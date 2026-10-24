"use client"

import { useEffect, useState } from "react"
import { Area, AreaChart, Bar, BarChart, CartesianGrid, ReferenceLine, XAxis, YAxis } from "recharts"

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import {
  getRateLimitTimeline,
  POLICY_LABELS,
  type RateLimitTimeline,
  type RateLimitTimelineRange,
} from "@/lib/rate-limit-stats"

const RANGES: Record<RateLimitTimelineRange, { label: string; slot: string; span: string }> = {
  HOUR: { label: "60 phút", slot: "1 phút", span: "60 phút qua" },
  DAY: { label: "1 ngày", slot: "15 phút", span: "24 giờ qua" },
  WEEK: { label: "7 ngày", slot: "1 giờ", span: "7 ngày qua" },
}

const ALL = "all"
const POLICY_ITEMS: Record<string, string> = { [ALL]: "Tất cả các mức", ...POLICY_LABELS }

const count = (value: number) => value.toLocaleString("vi-VN")

const time = new Intl.DateTimeFormat("vi-VN", { hour: "2-digit", minute: "2-digit" })
// "24/10": the vi-VN locale writes a bare day and month as "24-10".
const dayMonth = { format: (date: Date) => `${String(date.getDate()).padStart(2, "0")}/${String(date.getMonth() + 1).padStart(2, "0")}` }

/** Axis tick: the time of day, or the date for the week view. */
function tickLabel(start: string, range: RateLimitTimelineRange): string {
  const date = new Date(start)
  return range === "WEEK" ? dayMonth.format(date) : time.format(date)
}

/** The week view gets one tick per day, at local midnight, so no date repeats. */
function weekTicks(timeline: RateLimitTimeline): string[] {
  return timeline.points.map((point) => point.start).filter((start) => new Date(start).getHours() === 0)
}

/** Tooltip title: the slot as a span, e.g. "10:30–10:45 24/10". */
function slotLabel(start: string, slotSeconds: number): string {
  const from = new Date(start)
  const to = new Date(from.getTime() + slotSeconds * 1000)
  return `${time.format(from)}–${time.format(to)} ${dayMonth.format(from)}`
}

/**
 * Requests and blocks over the last hour, day or week, as two charts on one shared time axis:
 * blocks are usually a tiny fraction of requests, so one scale would flatten them, and two
 * y-scales on one plot would mislead. Reloads whenever `revision` changes.
 */
export function RateLimitTimelineCard({ revision, onSessionExpired }: {
  revision: number
  onSessionExpired: () => void
}) {
  const [range, setRange] = useState<RateLimitTimelineRange>("HOUR")
  const [policy, setPolicy] = useState(ALL)
  const [timeline, setTimeline] = useState<RateLimitTimeline | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    getRateLimitTimeline(range, policy === ALL ? null : policy)
      .then((loaded) => {
        if (!active) return
        setTimeline(loaded)
        setError(null)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setError(apiErrorMessage(caught))
      })
    return () => { active = false }
  }, [range, policy, revision, onSessionExpired])

  // A shown timeline for another range or policy is stale; show the skeleton instead.
  const current = timeline && timeline.range === range && (timeline.policy ?? ALL) === policy ? timeline : null
  const totalRequests = current?.points.reduce((sum, point) => sum + point.requests, 0) ?? 0
  const totalBlocked = current?.points.reduce((sum, point) => sum + point.blocked, 0) ?? 0
  const startedInRange = current && new Date(current.startedAt) > new Date(current.points[0].start)
  // The slot the backend started in, marked on both charts when it falls inside the range.
  const startSlot = startedInRange
    ? current.points.findLast((point) => new Date(point.start) <= new Date(current.startedAt))?.start
    : undefined

  return (
    <Card>
      <CardHeader className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <CardTitle>Theo thời gian</CardTitle>
          <CardDescription>
            Mỗi điểm là {RANGES[range].slot}.{" "}
            {policy === ALL
              ? "Request đếm một lần ở lớp IP; bị chặn cộng mọi mức."
              : "Request thuộc mức này, gồm cả request bị chặn."}
          </CardDescription>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Select value={policy} items={POLICY_ITEMS} onValueChange={(value) => setPolicy(String(value ?? ALL))}>
            <SelectTrigger className="w-52" aria-label="Mức giới hạn">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(POLICY_ITEMS).map(([key, label]) => (
                <SelectItem key={key} value={key}>{label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Tabs value={range} onValueChange={(value) => setRange(value as RateLimitTimelineRange)}>
            <TabsList>
              {(Object.keys(RANGES) as RateLimitTimelineRange[]).map((key) => (
                <TabsTrigger key={key} value={key}>{RANGES[key].label}</TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        </div>
      </CardHeader>
      <CardContent className="grid gap-5">
        {error && <p className="rounded-lg bg-muted/50 px-4 py-8 text-center text-sm text-muted-foreground">{error}</p>}
        {!error && !current && (
          <>
            <Skeleton className="h-44 w-full" />
            <Skeleton className="h-36 w-full" />
          </>
        )}
        {!error && current && (
          <>
            <TimelineChart
              title="Request"
              total={`${count(totalRequests)} trong ${RANGES[range].span}`}
              timeline={current}
              dataKey="requests"
              startSlot={startSlot}
              kind="area"
            />
            <TimelineChart
              title="Bị chặn"
              total={`${count(totalBlocked)} trong ${RANGES[range].span}`}
              timeline={current}
              dataKey="blocked"
              startSlot={startSlot}
              kind="bar"
            />
            {startedInRange && (
              <p className="text-xs text-muted-foreground">
                Backend khởi động lúc {time.format(new Date(current.startedAt))} {dayMonth.format(new Date(current.startedAt))}{" "}
                (vạch đứt); số liệu nằm trong bộ nhớ nên trước đó không có dữ liệu.
              </p>
            )}
          </>
        )}
      </CardContent>
    </Card>
  )
}

/**
 * One series in the single data colour over the shared time axis: requests as an area with a
 * 2px line, blocks as bars with a 4px rounded top. Hairline horizontal gridlines, a crosshair
 * tooltip per slot, and a dashed marker at the backend start.
 */
function TimelineChart({ title, total, timeline, dataKey, startSlot, kind }: {
  title: string
  total: string
  timeline: RateLimitTimeline
  dataKey: "requests" | "blocked"
  startSlot: string | undefined
  kind: "area" | "bar"
}) {
  const range = timeline.range as RateLimitTimelineRange
  const config = { [dataKey]: { label: title, color: "var(--chart-1)" } } satisfies ChartConfig
  const axis = (
    <>
      <CartesianGrid vertical={false} />
      <XAxis dataKey="start" tickLine={false} axisLine={false} minTickGap={32}
        ticks={range === "WEEK" ? weekTicks(timeline) : undefined}
        tickFormatter={(start: string) => tickLabel(start, range)} />
      <YAxis width={44} allowDecimals={false} tickLine={false} axisLine={false} interval={0}
        tickFormatter={(value) => count(Number(value))} />
      <ChartTooltip
        cursor={kind === "bar" ? { fill: "var(--muted)", opacity: 0.6 } : true}
        content={(
          <ChartTooltipContent
            labelFormatter={(_, payload) => {
              const start = payload?.[0]?.payload?.start as string | undefined
              return start ? slotLabel(start, timeline.slotSeconds) : ""
            }}
            formatter={(value) => (
              <div className="flex w-full items-center justify-between gap-4">
                <span className="text-muted-foreground">{title}</span>
                <span className="font-medium tabular-nums">{count(Number(value))}</span>
              </div>
            )}
          />
        )}
      />
      {startSlot && <ReferenceLine x={startSlot} stroke="var(--muted-foreground)" strokeDasharray="4 4" />}
    </>
  )

  return (
    <figure aria-label={`${title} theo thời gian`} className="grid gap-2">
      <figcaption className="flex items-baseline justify-between gap-3 text-sm">
        <span className="font-medium">{title}</span>
        <span className="text-muted-foreground tabular-nums">{total}</span>
      </figcaption>
      <ChartContainer config={config} className={kind === "area" ? "aspect-auto h-44 w-full" : "aspect-auto h-36 w-full"}>
        {kind === "area" ? (
          <AreaChart data={timeline.points} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
            {axis}
            <Area dataKey={dataKey} type="monotone" stroke={`var(--color-${dataKey})`} strokeWidth={2}
              fill={`var(--color-${dataKey})`} fillOpacity={0.15} isAnimationActive={false} />
          </AreaChart>
        ) : (
          <BarChart data={timeline.points} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
            {axis}
            <Bar dataKey={dataKey} fill={`var(--color-${dataKey})`} radius={[4, 4, 0, 0]} maxBarSize={16}
              isAnimationActive={false} />
          </BarChart>
        )}
      </ChartContainer>
    </figure>
  )
}
