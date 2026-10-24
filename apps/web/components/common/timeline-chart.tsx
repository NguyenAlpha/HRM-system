"use client"

import type { ReactNode } from "react"
import { Area, AreaChart, Bar, BarChart, CartesianGrid, ReferenceLine, XAxis, YAxis } from "recharts"

import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"

// Shared by the statistics pages (rate limits, API traffic) that chart in-memory counters over time.

export type TimelineRange = "HOUR" | "DAY" | "WEEK"

export const TIMELINE_RANGES: Record<TimelineRange, { label: string; slot: string; span: string }> = {
  HOUR: { label: "60 phút", slot: "1 phút", span: "60 phút qua" },
  DAY: { label: "1 ngày", slot: "15 phút", span: "24 giờ qua" },
  WEEK: { label: "7 ngày", slot: "1 giờ", span: "7 ngày qua" },
}

const count = (value: number) => value.toLocaleString("vi-VN")

const time = new Intl.DateTimeFormat("vi-VN", { hour: "2-digit", minute: "2-digit" })
// "24/10": the vi-VN locale writes a bare day and month as "24-10".
const dayMonth = { format: (date: Date) => `${String(date.getDate()).padStart(2, "0")}/${String(date.getMonth() + 1).padStart(2, "0")}` }

/** Axis tick: the time of day, or the date for the week view. */
function tickLabel(start: string, range: TimelineRange): string {
  const date = new Date(start)
  return range === "WEEK" ? dayMonth.format(date) : time.format(date)
}

/** The week view gets one tick per day, at local midnight, so no date repeats. */
function weekTicks(points: { start: string }[]): string[] {
  return points.map((point) => point.start).filter((start) => new Date(start).getHours() === 0)
}

/** Tooltip title: the slot as a span, e.g. "10:30–10:45 24/10". */
function slotLabel(start: string, slotSeconds: number): string {
  const from = new Date(start)
  const to = new Date(from.getTime() + slotSeconds * 1000)
  return `${time.format(from)}–${time.format(to)} ${dayMonth.format(from)}`
}

/** The slot the backend started in, when that falls inside the range; the charts mark it with a dashed line. */
export function timelineStartSlot(points: { start: string }[], startedAt: string): string | undefined {
  const started = new Date(startedAt)
  if (points.length === 0 || started <= new Date(points[0].start)) return undefined
  return points.findLast((point) => new Date(point.start) <= started)?.start
}

export function TimelineRangeTabs({ value, onChange }: { value: TimelineRange; onChange: (range: TimelineRange) => void }) {
  return (
    <Tabs value={value} onValueChange={(next) => onChange(next as TimelineRange)}>
      <TabsList>
        {(Object.keys(TIMELINE_RANGES) as TimelineRange[]).map((key) => (
          <TabsTrigger key={key} value={key}>{TIMELINE_RANGES[key].label}</TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  )
}

/** Explains the dashed line: nothing before it, because the counters live in memory. */
export function TimelineStartNote({ startedAt }: { startedAt: string }) {
  const started = new Date(startedAt)
  return (
    <p className="text-xs text-muted-foreground">
      Backend khởi động lúc {time.format(started)} {dayMonth.format(started)} (vạch đứt); số liệu nằm trong bộ nhớ nên
      trước đó không có dữ liệu.
    </p>
  )
}

/**
 * One series in the single data colour over a shared time axis: an area with a 2px line, or bars
 * with a 4px rounded top. Hairline horizontal gridlines, a tooltip per slot (with optional extra
 * rows), and a dashed marker at the backend start. Stacking several of these keeps one y-scale per
 * chart when measures differ in size.
 */
export function TimelineChart<T extends { start: string }>({
  title, total, data, dataKey, range, slotSeconds, startSlot, kind, formatValue = count, tooltipRows,
}: {
  title: string
  total: string
  data: T[]
  dataKey: keyof T & string
  range: TimelineRange
  slotSeconds: number
  startSlot: string | undefined
  kind: "area" | "bar"
  formatValue?: (value: number) => string
  /** Extra tooltip rows for the hovered slot, e.g. a breakdown of the value. */
  tooltipRows?: (point: T) => ReactNode
}) {
  const config = { [dataKey]: { label: title, color: "var(--chart-1)" } } satisfies ChartConfig
  // Widened for Recharts, which would otherwise infer its own data type from the key.
  const key: string = dataKey
  const axis = (
    <>
      <CartesianGrid vertical={false} />
      <XAxis dataKey="start" tickLine={false} axisLine={false} minTickGap={32}
        ticks={range === "WEEK" ? weekTicks(data) : undefined}
        tickFormatter={(start: string) => tickLabel(start, range)} />
      <YAxis width={52} allowDecimals={false} tickLine={false} axisLine={false} interval={0}
        tickFormatter={(value) => formatValue(Number(value))} />
      <ChartTooltip
        cursor={kind === "bar" ? { fill: "var(--muted)", opacity: 0.6 } : true}
        content={(
          <ChartTooltipContent
            labelFormatter={(_, payload) => {
              const start = payload?.[0]?.payload?.start as string | undefined
              return start ? slotLabel(start, slotSeconds) : ""
            }}
            formatter={(value, _name, item) => (
              <div className="grid w-full gap-1">
                <div className="flex items-center justify-between gap-4">
                  <span className="text-muted-foreground">{title}</span>
                  <span className="font-medium tabular-nums">{value == null ? "—" : formatValue(Number(value))}</span>
                </div>
                {tooltipRows?.(item.payload as T)}
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
          <AreaChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
            {axis}
            <Area dataKey={key} type="monotone" stroke={`var(--color-${dataKey})`} strokeWidth={2}
              fill={`var(--color-${dataKey})`} fillOpacity={0.15} isAnimationActive={false} />
          </AreaChart>
        ) : (
          <BarChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
            {axis}
            <Bar dataKey={key} fill={`var(--color-${dataKey})`} radius={[4, 4, 0, 0]} maxBarSize={16}
              isAnimationActive={false} />
          </BarChart>
        )}
      </ChartContainer>
    </figure>
  )
}
