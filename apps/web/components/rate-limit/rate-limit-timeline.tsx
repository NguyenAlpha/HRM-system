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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { getRateLimitTimeline, POLICY_LABELS, type RateLimitTimeline } from "@/lib/rate-limit-stats"

const ALL = "all"
const POLICY_ITEMS: Record<string, string> = { [ALL]: "Tất cả các mức", ...POLICY_LABELS }

const count = (value: number) => value.toLocaleString("vi-VN")

/**
 * Requests and blocks over the last hour, day or week, as two charts on one shared time axis:
 * blocks are usually a tiny fraction of requests, so one scale would flatten them, and two
 * y-scales on one plot would mislead. Reloads whenever `revision` changes.
 */
export function RateLimitTimelineCard({ revision, onSessionExpired }: {
  revision: number
  onSessionExpired: () => void
}) {
  const [range, setRange] = useState<TimelineRange>("HOUR")
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
  const startSlot = current ? timelineStartSlot(current.points, current.startedAt) : undefined

  return (
    <Card>
      <CardHeader className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <CardTitle>Theo thời gian</CardTitle>
          <CardDescription>
            Mỗi điểm là {TIMELINE_RANGES[range].slot}.{" "}
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
          <TimelineRangeTabs value={range} onChange={setRange} />
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
              total={`${count(totalRequests)} trong ${TIMELINE_RANGES[range].span}`}
              data={current.points}
              dataKey="requests"
              range={range}
              slotSeconds={current.slotSeconds}
              startSlot={startSlot}
              kind="area"
            />
            <TimelineChart
              title="Bị chặn"
              total={`${count(totalBlocked)} trong ${TIMELINE_RANGES[range].span}`}
              data={current.points}
              dataKey="blocked"
              range={range}
              slotSeconds={current.slotSeconds}
              startSlot={startSlot}
              kind="bar"
            />
            {startSlot && <TimelineStartNote startedAt={current.startedAt} />}
          </>
        )}
      </CardContent>
    </Card>
  )
}
