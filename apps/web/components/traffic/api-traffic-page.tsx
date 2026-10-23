"use client"

import { useEffect, useState } from "react"
import { RefreshCw } from "lucide-react"
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts"

import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { StatTile } from "@/components/report/report-shared"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { getApiTraffic, type ApiTraffic, type EndpointTraffic } from "@/lib/api-traffic"

const count = (value: number) => value.toLocaleString("vi-VN")
const millis = (value: number) => `${value.toLocaleString("vi-VN", { maximumFractionDigits: value < 10 ? 1 : 0 })} ms`
const percent = (part: number, total: number) => total === 0 ? "0%"
  : `${(part / total * 100).toLocaleString("vi-VN", { maximumFractionDigits: 1 })}%`

function formatTime(value: string): string {
  return new Intl.DateTimeFormat("vi-VN", {
    hour: "2-digit", minute: "2-digit", day: "2-digit", month: "2-digit", year: "numeric",
  }).format(new Date(value))
}

/** Requests answered before a controller was chosen have no route template. */
const routeLabel = (endpoint: EndpointTraffic) => endpoint.uri === "UNKNOWN"
  ? "Chưa tới controller (401, 404, 429…)"
  : endpoint.uri

type Sort = "requests" | "slowest" | "errors"

const SORTS: Record<Sort, { label: string; compare: (a: EndpointTraffic, b: EndpointTraffic) => number }> = {
  requests: { label: "Gọi nhiều nhất", compare: (a, b) => b.requests - a.requests },
  slowest: { label: "Chậm nhất", compare: (a, b) => b.meanMillis - a.meanMillis },
  errors: {
    label: "Lỗi nhiều nhất",
    compare: (a, b) => (b.clientErrors + b.serverErrors) - (a.clientErrors + a.serverErrors) || b.requests - a.requests,
  },
}

/**
 * Admin Console view of the API traffic: request counts, errors and mean duration per route,
 * from the timers Spring Boot keeps in memory since the API's last start.
 */
export function ApiTrafficPage() {
  const { session, onSessionExpired } = usePortalSession()
  const canRead = session.account.permissions.some((permission) => permission.code === "system.traffic.read")
  const [traffic, setTraffic] = useState<ApiTraffic | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true)
  const [sort, setSort] = useState<Sort>("requests")

  useEffect(() => {
    if (!canRead) return
    let active = true
    getApiTraffic()
      .then((loaded) => {
        if (!active) return
        setTraffic(loaded)
        setError(null)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setError(apiErrorMessage(caught))
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [canRead, revision, onSessionExpired])

  function reload() {
    setLoading(true)
    setRevision((value) => value + 1)
  }

  const displayedError = canRead ? error : "Tài khoản của bạn không có quyền xem lưu lượng API."
  const endpoints = traffic ? [...traffic.endpoints].sort(SORTS[sort].compare) : null

  return (
    <>
      <PageHeader
        title="Lưu lượng API"
        description="Số request, thời gian xử lý và số lỗi của từng API. Số liệu nằm trong bộ nhớ, tính từ lần khởi động backend gần nhất."
        actions={canRead && (
          <Button type="button" variant="outline" size="sm" onClick={reload} disabled={loading}>
            <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
          </Button>
        )}
      />

      {displayedError && (
        <Alert variant="destructive" className="mt-6"><AlertDescription>{displayedError}</AlertDescription></Alert>
      )}

      {canRead && !displayedError && (
        <div className="mt-6 grid gap-6">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {traffic ? (
              <>
                <StatTile label="Tổng request" value={count(traffic.totalRequests)} hint={`${count(traffic.endpoints.length)} API đã được gọi`} />
                <StatTile label="Lỗi phía client (4xx)" value={count(traffic.clientErrors)}
                  hint={`${percent(traffic.clientErrors, traffic.totalRequests)} tổng request`} />
                <StatTile label="Lỗi máy chủ (5xx)" value={count(traffic.serverErrors)}
                  hint={`${percent(traffic.serverErrors, traffic.totalRequests)} tổng request`} />
                <StatTile label="Đếm từ" value={formatTime(traffic.startedAt)} hint="Lần khởi động backend gần nhất" />
              </>
            ) : Array.from({ length: 4 }, (_, index) => <Skeleton key={index} className="h-[106px] rounded-xl" />)}
          </div>

          <Card>
            <CardHeader>
              <CardTitle>10 API được gọi nhiều nhất</CardTitle>
              <CardDescription>Số request theo method và đường dẫn, gồm cả request bị lỗi.</CardDescription>
            </CardHeader>
            <CardContent>
              {traffic ? <TopRequestsChart endpoints={traffic.endpoints} /> : <Skeleton className="h-72 w-full" />}
            </CardContent>
          </Card>

          <section className="grid gap-3">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold">Theo từng API</h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  Thời gian là trung bình của mọi request, tính bằng mili giây.
                </p>
              </div>
              <Tabs value={sort} onValueChange={(value) => setSort(value as Sort)}>
                <TabsList>
                  {(Object.keys(SORTS) as Sort[]).map((key) => (
                    <TabsTrigger key={key} value={key}>{SORTS[key].label}</TabsTrigger>
                  ))}
                </TabsList>
              </Tabs>
            </div>
            <div className="rounded-xl border bg-card">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>API</TableHead>
                    <TableHead className="text-right">Request</TableHead>
                    <TableHead className="text-right">Thời gian TB</TableHead>
                    <TableHead className="text-right">Lỗi 4xx</TableHead>
                    <TableHead className="text-right">Lỗi 5xx</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {!endpoints && Array.from({ length: 5 }, (_, row) => (
                    <TableRow key={row}>
                      {Array.from({ length: 5 }, (_, cell) => (
                        <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
                      ))}
                    </TableRow>
                  ))}
                  {endpoints?.map((endpoint) => (
                    <TableRow key={`${endpoint.method} ${endpoint.uri}`}>
                      <TableCell className="max-w-[32rem]">
                        <span className="flex min-w-0 items-center gap-2">
                          <Badge variant="outline" className="shrink-0 font-mono">{endpoint.method}</Badge>
                          <span className="truncate font-mono text-sm" title={endpoint.uri}>{routeLabel(endpoint)}</span>
                        </span>
                      </TableCell>
                      <TableCell className="text-right text-sm font-medium tabular-nums">{count(endpoint.requests)}</TableCell>
                      <TableCell className="text-right text-sm tabular-nums">{millis(endpoint.meanMillis)}</TableCell>
                      <TableCell className="text-right text-sm tabular-nums">{count(endpoint.clientErrors)}</TableCell>
                      <TableCell className="text-right text-sm tabular-nums">{count(endpoint.serverErrors)}</TableCell>
                    </TableRow>
                  ))}
                  {endpoints?.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} className="h-24 text-center text-muted-foreground">
                        Chưa có request nào kể từ lần khởi động.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </div>
          </section>
        </div>
      )}
    </>
  )
}

/**
 * The ten most requested routes as horizontal bars, one series in the single data colour: bars
 * capped at 16px with a 4px rounded data end, hairline vertical gridlines, a tooltip per bar.
 */
function TopRequestsChart({ endpoints }: { endpoints: EndpointTraffic[] }) {
  if (endpoints.length === 0) {
    return <p className="rounded-lg bg-muted/50 px-4 py-8 text-center text-sm text-muted-foreground">Chưa có request nào.</p>
  }
  const label = "Request"
  const data = endpoints.slice(0, 10).map((endpoint) => ({
    name: `${endpoint.method} ${endpoint.uri === "UNKNOWN" ? "(chưa tới controller)" : endpoint.uri}`,
    value: endpoint.requests,
  }))
  const config = { value: { label, color: "var(--chart-1)" } } satisfies ChartConfig

  return (
    <figure aria-label="10 API được gọi nhiều nhất">
      <ChartContainer config={config} className="aspect-auto h-80 w-full">
        <BarChart data={data} layout="vertical" margin={{ top: 0, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid horizontal={false} />
          <XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} tickFormatter={(value) => count(Number(value))} />
          <YAxis type="category" dataKey="name" tickLine={false} axisLine={false} width={300} interval={0}
            tickFormatter={(name: string) => name.length > 44 ? `${name.slice(0, 43)}…` : name} />
          <ChartTooltip
            cursor={{ fill: "var(--muted)", opacity: 0.6 }}
            content={(
              <ChartTooltipContent
                formatter={(value) => (
                  <div className="flex w-full items-center justify-between gap-4">
                    <span className="text-muted-foreground">{label}</span>
                    <span className="font-medium tabular-nums">{count(Number(value))}</span>
                  </div>
                )}
              />
            )}
          />
          <Bar dataKey="value" fill="var(--color-value)" radius={[0, 4, 4, 0]} maxBarSize={16} />
        </BarChart>
      </ChartContainer>
    </figure>
  )
}
