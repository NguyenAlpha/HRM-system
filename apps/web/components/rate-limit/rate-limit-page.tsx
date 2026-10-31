"use client"

import { useEffect, useState } from "react"
import { Pencil, Power, PowerOff, RefreshCw, RotateCcw, Timer, TimerOff } from "lucide-react"
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts"

import { RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import {
  RateLimitPolicyConfirmDialog,
  RateLimitPolicyEditDialog,
  type RateLimitPolicyAction,
} from "@/components/rate-limit/rate-limit-policy-dialogs"
import { RateLimitTimelineCard } from "@/components/rate-limit/rate-limit-timeline"
import { StatTile } from "@/components/report/report-shared"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import {
  formatQuota,
  getRateLimitStats,
  POLICY_LABELS,
  SCOPE_LABELS,
  type RateLimitPolicyStats,
  type RateLimitStats,
} from "@/lib/rate-limit-stats"
import { formatDateTime } from "@/lib/format-date"

const count = (value: number) => value.toLocaleString("vi-VN")

const AUTO_REFRESH_MS = 15_000

const policyLabel = (policy: string) => POLICY_LABELS[policy] ?? policy

/**
 * Admin Console view of the API rate limits: every quota with its allowed/blocked counters and
 * the keys refused most often. The API keeps the counters in memory since its last start.
 * Holders of system.rate_limit.manage also edit, switch off and reset each quota from its row.
 * The page reloads itself every 15 seconds while it is visible, unless switched off.
 */
export function RateLimitPage() {
  const { session, onSessionExpired } = usePortalSession()
  const canRead = session.account.permissions.some((permission) => permission.code === "system.rate_limit.read")
  const canManage = session.account.permissions.some((permission) => permission.code === "system.rate_limit.manage")
  const [stats, setStats] = useState<RateLimitStats | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true)
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null)
  const [editing, setEditing] = useState<RateLimitPolicyStats | null>(null)
  const [confirming, setConfirming] = useState<{ policy: RateLimitPolicyStats; action: RateLimitPolicyAction } | null>(null)

  useEffect(() => {
    if (!canRead) return
    let active = true
    getRateLimitStats()
      .then((loaded) => {
        if (!active) return
        setStats(loaded)
        setError(null)
        setUpdatedAt(new Date())
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setError(apiErrorMessage(caught))
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [canRead, revision, onSessionExpired])

  // Silent: the figures stay on screen while the next ones load. A hidden tab skips its turn.
  useEffect(() => {
    if (!canRead || !autoRefresh) return
    const timer = setInterval(() => {
      if (document.visibilityState === "visible") setRevision((value) => value + 1)
    }, AUTO_REFRESH_MS)
    return () => clearInterval(timer)
  }, [canRead, autoRefresh])

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
          <div className="flex flex-wrap items-center justify-end gap-2">
            {updatedAt && (
              <span className="text-xs text-muted-foreground tabular-nums">
                Cập nhật lúc {updatedAt.toLocaleTimeString("vi-VN", { timeZone: "Asia/Ho_Chi_Minh" })}
              </span>
            )}
            <Button type="button" variant="outline" size="sm" aria-pressed={autoRefresh}
              title="Tải lại mỗi 15 giây khi đang mở trang"
              onClick={() => setAutoRefresh((value) => !value)}>
              {autoRefresh ? <Timer /> : <TimerOff />}
              Tự động làm mới: {autoRefresh ? "Bật" : "Tắt"}
            </Button>
            <Button type="button" variant="outline" size="sm" onClick={reload} disabled={loading}>
              <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
            </Button>
          </div>
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
                <StatTile label="Đếm từ" value={formatDateTime(stats.startedAt)} hint="Lần khởi động backend gần nhất" />
                <StatTile label="Request bị chặn" value={count(totalBlocked)} hint="Cộng tất cả các mức" />
                <StatTile label="Bị chặn nhiều nhất" value={top ? top.key : "—"}
                  hint={top ? `${count(top.blocked)} lần · ${policyLabel(top.policy)}` : "Chưa có request nào bị chặn"} />
              </>
            ) : Array.from({ length: 4 }, (_, index) => <Skeleton key={index} className="h-[106px] rounded-xl" />)}
          </div>

          <RateLimitTimelineCard revision={revision} onSessionExpired={onSessionExpired} />

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
            <div>
              <h2 className="text-lg font-semibold">Hạn mức đang áp dụng</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                {canManage
                  ? "Thay đổi được lưu vào cơ sở dữ liệu và có hiệu lực ngay. Mức sai mật khẩu chỉ chỉnh được hạn mức, không tắt được."
                  : "Mặc định lấy từ application.properties; quản trị viên có quyền chỉnh có thể thay đổi."}
              </p>
            </div>
            <div className="rounded-xl border bg-card">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Mức</TableHead>
                    <TableHead>Đếm theo</TableHead>
                    <TableHead>Hạn mức</TableHead>
                    <TableHead>Trạng thái</TableHead>
                    <TableHead className="text-right">Được cho qua</TableHead>
                    <TableHead className="text-right">Bị chặn</TableHead>
                    {canManage && <TableHead className="text-right">Thao tác</TableHead>}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {!stats && <SkeletonRows columns={canManage ? 7 : 6} />}
                  {stats?.policies.map((policy) => (
                    <TableRow key={policy.policy}>
                      <TableCell>
                        <span className="font-medium">{policyLabel(policy.policy)}</span>
                        <span className="mt-1 block font-mono text-xs text-muted-foreground">{policy.policy}</span>
                      </TableCell>
                      <TableCell className="text-sm">{SCOPE_LABELS[policy.scope] ?? policy.scope}</TableCell>
                      <TableCell className="text-sm tabular-nums">
                        {formatQuota(policy.maxRequests, policy.windowSeconds)}
                        {(policy.maxRequests !== policy.defaultMaxRequests || policy.windowSeconds !== policy.defaultWindowSeconds) && (
                          <span className="mt-1 block text-xs text-muted-foreground">
                            Đã chỉnh · mặc định {formatQuota(policy.defaultMaxRequests, policy.defaultWindowSeconds)}
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        <Badge variant={policy.enabled ? "secondary" : "outline"}>{policy.enabled ? "Đang bật" : "Đã tắt"}</Badge>
                      </TableCell>
                      <TableCell className="text-right text-sm tabular-nums">{count(policy.allowed)}</TableCell>
                      <TableCell className="text-right text-sm font-medium tabular-nums">{count(policy.blocked)}</TableCell>
                      {canManage && (
                        <RowActionsCell label={policyLabel(policy.policy)}>
                          <RowActionItem icon={Pencil} onClick={() => setEditing(policy)}>Sửa hạn mức</RowActionItem>
                          {policy.enabled && policy.canDisable && (
                            <RowActionItem icon={PowerOff} destructive onClick={() => setConfirming({ policy, action: "disable" })}>
                              Tắt mức này
                            </RowActionItem>
                          )}
                          {!policy.enabled && (
                            <RowActionItem icon={Power} onClick={() => setConfirming({ policy, action: "enable" })}>Bật lại</RowActionItem>
                          )}
                          {policy.customized && (
                            <RowActionItem icon={RotateCcw} onClick={() => setConfirming({ policy, action: "restore" })}>
                              Khôi phục mặc định
                            </RowActionItem>
                          )}
                        </RowActionsCell>
                      )}
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

      {editing && (
        <RateLimitPolicyEditDialog policy={editing} onClose={() => setEditing(null)} onSaved={setStats}
          onSessionExpired={onSessionExpired} />
      )}
      {confirming && (
        <RateLimitPolicyConfirmDialog policy={confirming.policy} action={confirming.action}
          onClose={() => setConfirming(null)} onSaved={setStats} onSessionExpired={onSessionExpired} />
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
