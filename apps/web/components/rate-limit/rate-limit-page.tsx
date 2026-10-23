"use client"

import { useEffect, useState } from "react"
import { RefreshCw } from "lucide-react"
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts"

import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { StatTile } from "@/components/report/report-shared"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import {
  getRateLimitStats,
  POLICY_LABELS,
  SCOPE_LABELS,
  type RateLimitPolicyStats,
  type RateLimitStats,
} from "@/lib/rate-limit-stats"

const count = (value: number) => value.toLocaleString("vi-VN")

/** 60 → "1 phút", 900 → "15 phút", 30 → "30 giây". */
function formatWindow(seconds: number): string {
  if (seconds % 3600 === 0) return `${seconds / 3600} giờ`
  if (seconds % 60 === 0) return `${seconds / 60} phút`
  return `${seconds} giây`
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat("vi-VN", {
    hour: "2-digit", minute: "2-digit", day: "2-digit", month: "2-digit", year: "numeric",
  }).format(new Date(value))
}

const policyLabel = (policy: string) => POLICY_LABELS[policy] ?? policy

/**
 * Admin Console view of the API rate limits: every quota with its allowed/blocked counters and
 * the keys refused most often. The API keeps the counters in memory since its last start.
 */
export function RateLimitPage() {
  const { session, onSessionExpired } = usePortalSession()
  const canRead = session.account.permissions.some((permission) => permission.code === "system.rate_limit.read")
  const [stats, setStats] = useState<RateLimitStats | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!canRead) return
    let active = true
    getRateLimitStats()
      .then((loaded) => {
        if (!active) return
        setStats(loaded)
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

  const displayedError = canRead ? error : "Tài khoản của bạn không có quyền xem thống kê giới hạn truy cập."
  const totalBlocked = stats?.policies.reduce((sum, policy) => sum + policy.blocked, 0) ?? 0
  const top = stats?.topBlocked[0]

  return (
    <>
      <PageHeader
        title="Giới hạn truy cập"
        description="Hạn mức request của API và số lần bị chặn. Số liệu nằm trong bộ nhớ, tính từ lần khởi động backend gần nhất."
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
            {stats ? (
              <>
                <StatTile label="Trạng thái" value={stats.enabled ? "Đang bật" : "Đang tắt"} hint="Đổi bằng RATE_LIMIT_ENABLED" />
                <StatTile label="Đếm từ" value={formatTime(stats.startedAt)} hint="Lần khởi động backend gần nhất" />
                <StatTile label="Request bị chặn" value={count(totalBlocked)} hint="Cộng tất cả các mức" />
                <StatTile label="Bị chặn nhiều nhất" value={top ? top.key : "—"}
                  hint={top ? `${count(top.blocked)} lần · ${policyLabel(top.policy)}` : "Chưa có request nào bị chặn"} />
              </>
            ) : Array.from({ length: 4 }, (_, index) => <Skeleton key={index} className="h-[106px] rounded-xl" />)}
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Request bị chặn theo mức</CardTitle>
              <CardDescription>Mỗi request bị chặn được tính một lần, ở mức đã chặn nó.</CardDescription>
            </CardHeader>
            <CardContent>
              {stats ? <BlockedChart policies={stats.policies} /> : <Skeleton className="h-72 w-full" />}
            </CardContent>
          </Card>

          <section className="grid gap-3">
            <h2 className="text-lg font-semibold">Hạn mức đang áp dụng</h2>
            <div className="rounded-xl border bg-card">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Mức</TableHead>
                    <TableHead>Đếm theo</TableHead>
                    <TableHead>Hạn mức</TableHead>
                    <TableHead className="text-right">Được cho qua</TableHead>
                    <TableHead className="text-right">Bị chặn</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {!stats && <SkeletonRows columns={5} />}
                  {stats?.policies.map((policy) => (
                    <TableRow key={policy.policy}>
                      <TableCell>
                        <span className="font-medium">{policyLabel(policy.policy)}</span>
                        <span className="mt-1 block font-mono text-xs text-muted-foreground">{policy.policy}</span>
                      </TableCell>
                      <TableCell className="text-sm">{SCOPE_LABELS[policy.scope] ?? policy.scope}</TableCell>
                      <TableCell className="text-sm tabular-nums">
                        {count(policy.maxRequests)} request / {formatWindow(policy.windowSeconds)}
                      </TableCell>
                      <TableCell className="text-right text-sm tabular-nums">{count(policy.allowed)}</TableCell>
                      <TableCell className="text-right text-sm font-medium tabular-nums">{count(policy.blocked)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </section>

          <section className="grid gap-3">
            <div>
              <h2 className="text-lg font-semibold">Bị chặn nhiều nhất</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                20 khoá bị chặn nhiều nhất: địa chỉ IP, tên đăng nhập, hoặc tài khoản khi nhập sai mật khẩu.
              </p>
            </div>
            <div className="rounded-xl border bg-card">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Khoá</TableHead>
                    <TableHead>Đếm theo</TableHead>
                    <TableHead>Mức</TableHead>
                    <TableHead className="text-right">Số lần bị chặn</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {!stats && <SkeletonRows columns={4} />}
                  {stats?.topBlocked.map((item) => (
                    <TableRow key={`${item.policy}:${item.key}`}>
                      <TableCell className="max-w-72 truncate font-mono text-sm" title={item.key}>{item.key}</TableCell>
                      <TableCell className="text-sm">{SCOPE_LABELS[item.scope] ?? item.scope}</TableCell>
                      <TableCell className="text-sm">{policyLabel(item.policy)}</TableCell>
                      <TableCell className="text-right text-sm font-medium tabular-nums">{count(item.blocked)}</TableCell>
                    </TableRow>
                  ))}
                  {stats?.topBlocked.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={4} className="h-24 text-center text-muted-foreground">
                        Chưa có request nào bị chặn.
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

function SkeletonRows({ columns }: { columns: number }) {
  return Array.from({ length: 3 }, (_, row) => (
    <TableRow key={row}>
      {Array.from({ length: columns }, (_, cell) => (
        <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
      ))}
    </TableRow>
  ))
}

/**
 * Blocked requests per policy as horizontal bars, one series in the single data colour: bars
 * capped at 16px with a 4px rounded data end, hairline vertical gridlines, a tooltip per bar.
 * Every policy keeps its row, so the categories do not move between reloads.
 */
function BlockedChart({ policies }: { policies: RateLimitPolicyStats[] }) {
  if (policies.every((policy) => policy.blocked === 0)) {
    return <p className="rounded-lg bg-muted/50 px-4 py-8 text-center text-sm text-muted-foreground">Chưa có request nào bị chặn.</p>
  }
  const label = "Request bị chặn"
  const data = policies.map((policy) => ({ name: policyLabel(policy.policy), value: policy.blocked }))
  const config = { value: { label, color: "var(--chart-1)" } } satisfies ChartConfig

  return (
    <figure aria-label={label}>
      <ChartContainer config={config} className="aspect-auto h-72 w-full">
        <BarChart data={data} layout="vertical" margin={{ top: 0, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid horizontal={false} />
          <XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} tickFormatter={(value) => count(Number(value))} />
          <YAxis type="category" dataKey="name" tickLine={false} axisLine={false} width={180} />
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
