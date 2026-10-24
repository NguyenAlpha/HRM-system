"use client"

import { useEffect, useState } from "react"

import {
  TIMELINE_RANGES,
  TimelineChart,
  TimelineRangeTabs,
  TimelineStartNote,
  timelineStartSlot,
  type TimelineRange,
} from "@/components/common/timeline-chart"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { getApiTrafficTimeline, type ApiTrafficTimeline, type TrafficPoint } from "@/lib/api-traffic"

const count = (value: number) => value.toLocaleString("vi-VN")
const millis = (value: number) => `${value.toLocaleString("vi-VN", { maximumFractionDigits: value < 10 ? 1 : 0 })} ms`

type ChartPoint = TrafficPoint & { errors: number }

/**
 * Requests, errors and mean processing time of the whole API over the last hour, day or week:
 * three charts on one shared time axis, since the three measures differ in scale and unit.
 * Reloads whenever `revision` changes.
 */
export function ApiTrafficTimelineCard({ revision, onSessionExpired }: {
  revision: number
  onSessionExpired: () => void
}) {
  const [range, setRange] = useState<TimelineRange>("HOUR")
  const [timeline, setTimeline] = useState<ApiTrafficTimeline | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    getApiTrafficTimeline(range)
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
  }, [range, revision, onSessionExpired])

  // A shown timeline for another range is stale; show the skeleton instead.
  const current = timeline?.range === range ? timeline : null
  const points: ChartPoint[] = current?.points.map((point) => ({ ...point, errors: point.clientErrors + point.serverErrors })) ?? []
  const totalRequests = points.reduce((sum, point) => sum + point.requests, 0)
  const totalErrors = points.reduce((sum, point) => sum + point.errors, 0)
  const totalMillis = points.reduce((sum, point) => sum + (point.meanMillis ?? 0) * point.requests, 0)
  const span = TIMELINE_RANGES[range].span
  const startSlot = current ? timelineStartSlot(current.points, current.startedAt) : undefined

  return (
    <Card>
      <CardHeader className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <CardTitle>Theo thời gian</CardTitle>
          <CardDescription>
            Mỗi điểm là {TIMELINE_RANGES[range].slot}, gồm mọi request backend trả lời, kể cả request bị từ chối.
          </CardDescription>
        </div>
        <TimelineRangeTabs value={range} onChange={setRange} />
      </CardHeader>
      <CardContent className="grid gap-5">
        {error && <p className="rounded-lg bg-muted/50 px-4 py-8 text-center text-sm text-muted-foreground">{error}</p>}
        {!error && !current && (
          <>
            <Skeleton className="h-44 w-full" />
            <Skeleton className="h-36 w-full" />
            <Skeleton className="h-36 w-full" />
          </>
        )}
        {!error && current && (
          <>
            <TimelineChart
              title="Request"
              total={`${count(totalRequests)} trong ${span}`}
              data={points}
              dataKey="requests"
              range={range}
              slotSeconds={current.slotSeconds}
              startSlot={startSlot}
              kind="area"
            />
            <TimelineChart
              title="Lỗi (4xx + 5xx)"
              total={`${count(totalErrors)} trong ${span}`}
              data={points}
              dataKey="errors"
              range={range}
              slotSeconds={current.slotSeconds}
              startSlot={startSlot}
              kind="bar"
              tooltipRows={(point) => (
                <>
                  <TooltipRow label="4xx" value={count(point.clientErrors)} />
                  <TooltipRow label="5xx" value={count(point.serverErrors)} />
                </>
              )}
            />
            <TimelineChart
              title="Thời gian xử lý TB"
              total={totalRequests === 0 ? "—" : `${millis(totalMillis / totalRequests)} trung bình ${span}`}
              data={points}
              dataKey="meanMillis"
              range={range}
              slotSeconds={current.slotSeconds}
              startSlot={startSlot}
              kind="bar"
              formatValue={millis}
              tooltipRows={(point) => <TooltipRow label="Request" value={count(point.requests)} />}
            />
            {startSlot && <TimelineStartNote startedAt={current.startedAt} />}
          </>
        )}
      </CardContent>
    </Card>
  )
}

function TooltipRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 text-muted-foreground">
      <span>{label}</span>
      <span className="tabular-nums">{value}</span>
    </div>
  )
}
