"use client"

import { useEffect, useState } from "react"
import { Clock, Loader2, LogIn, LogOut } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { SectionError, time, today, useWorkforceAction, workforceApi } from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import { authorizedRequest } from "@/lib/auth/client"
import type { Portal } from "@/lib/auth/types"
import type { Page } from "@/lib/pagination"
import type { AttendanceRecord, WebCheckInSetting } from "@/lib/workforce"

/** The setting's path on each portal's BFF. */
const SETTING_PATH: Record<Portal, string> = {
  hrm: "/workforce/attendance/web-check-in",
  admin: "/attendance/web-check-in",
}

/**
 * Switches web check-in on or off for the whole company (attendance.web_check_in.manage): on, employees
 * check in and out themselves; off, attendance comes from the time clock and HR's corrections.
 */
export function WebCheckInSettingCard({ portal }: { portal: Portal }) {
  const [enabled, setEnabled] = useState<boolean | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const { busy, run } = useWorkforceAction(() => setRevision((value) => value + 1))

  useEffect(() => {
    let active = true
    authorizedRequest<WebCheckInSetting>(portal, SETTING_PATH[portal])
      .then((setting) => { if (active) { setEnabled(setting.enabled); setError(null) } })
      .catch((caught: unknown) => { if (active) setError(apiErrorMessage(caught)) })
    return () => { active = false }
  }, [portal, revision])

  const toggle = () => run(enabled ? "Đã tắt chấm công trên web" : "Đã bật chấm công trên web",
    () => authorizedRequest(portal, SETTING_PATH[portal], {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled: !enabled }),
    }))

  return (
    <Card>
      <CardHeader>
        <CardTitle>Chấm công trên web</CardTitle>
        <CardDescription>
          Khi bật, nhân viên tự bấm Vào ca / Ra ca trên trang Chấm công, theo giờ của máy chủ. Khi tắt, công lấy từ máy
          chấm công và phần HR chỉnh sửa.
        </CardDescription>
        {enabled !== null && (
          <CardAction>
            <Badge variant={enabled ? "secondary" : "outline"}>{enabled ? "Đang bật" : "Đang tắt"}</Badge>
          </CardAction>
        )}
      </CardHeader>
      <CardContent className="grid gap-3">
        <SectionError error={error} />
        {enabled !== null && (
          <div>
            <Button type="button" variant={enabled ? "outline" : "default"} disabled={busy} onClick={() => void toggle()}>
              {busy && <Loader2 className="animate-spin" />} {enabled ? "Tắt chấm công trên web" : "Bật chấm công trên web"}
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

/** The current time, ticking every second. */
function useNow() {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(timer)
  }, [])
  return now
}

const LIVE_CLOCK = new Intl.DateTimeFormat("vi-VN", {
  timeZone: "Asia/Ho_Chi_Minh", hour: "2-digit", minute: "2-digit", second: "2-digit",
})

/** YYYY-MM-DD of the day before. */
function previousDay(date: string) {
  const day = new Date(`${date}T00:00:00Z`)
  day.setUTCDate(day.getUTCDate() - 1)
  return day.toISOString().slice(0, 10)
}

/**
 * The signed-in employee's check-in for today while web check-in is on: Vào ca records the server's time
 * as the check-in, Ra ca the check-out (pressed again, it moves the check-out). Shows nothing while off.
 * `onPunched` runs after each punch so the month's timesheet below reloads.
 */
export function WebCheckInCard({ employeeId, onPunched }: { employeeId: number; onPunched: () => void }) {
  const now = useNow()
  const [enabled, setEnabled] = useState(false)
  const [record, setRecord] = useState<AttendanceRecord | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const { busy, run } = useWorkforceAction(() => {
    setRevision((value) => value + 1)
    onPunched()
  })

  useEffect(() => {
    let active = true
    const day = today()
    workforceApi<WebCheckInSetting>("attendance/web-check-in")
      .then(async (setting) => {
        if (!active) return
        setEnabled(setting.enabled)
        if (!setting.enabled) return
        // Yesterday too: a night shift checks out the morning after.
        const page = await workforceApi<Page<AttendanceRecord>>(
          `attendance/employees/${employeeId}?from=${previousDay(day)}&to=${day}&size=2`)
        if (!active) return
        const todays = page.content.find((row) => row.workDate === day)
        const openNight = page.content.find((row) => row.workDate !== day && row.checkInAt && !row.checkOutAt)
        setRecord(todays?.checkInAt ? todays : openNight ?? todays ?? null)
        setError(null)
      })
      .catch((caught: unknown) => { if (active) setError(apiErrorMessage(caught)) })
    return () => { active = false }
  }, [employeeId, revision])

  if (!enabled && !error) return null

  const checkedIn = !!record?.checkInAt
  const checkedOut = !!record?.checkOutAt
  const punch = (action: "check-in" | "check-out") => run(action === "check-in" ? "Đã vào ca" : "Đã ra ca",
    () => workforceApi(`attendance/me/${action}`, "POST"))

  return (
    <Card className="border-primary/40">
      <CardHeader>
        <CardTitle>Chấm công hôm nay</CardTitle>
        <CardDescription>
          Giờ vào/ra lấy theo giờ máy chủ khi bạn bấm. Quên ra ca thì ngày đó thiếu dữ liệu và cần phòng Nhân sự sửa.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4 sm:grid-cols-[1fr_auto] sm:items-center">
        <SectionError error={error} />
        <div className="flex flex-wrap items-center gap-x-8 gap-y-3">
          <div>
            <p className="text-xs text-muted-foreground">Bây giờ</p>
            <p className="flex items-center gap-1.5 text-2xl font-semibold tabular-nums">
              <Clock className="size-5 text-muted-foreground" /> {LIVE_CLOCK.format(now)}
            </p>
          </div>
          {record && (
            <div>
              <p className="text-xs text-muted-foreground">Ca hôm nay</p>
              <p className="font-medium tabular-nums">{time(record.scheduledStartAt)}–{time(record.scheduledEndAt)}</p>
            </div>
          )}
          <div>
            <p className="text-xs text-muted-foreground">Vào ca</p>
            <p className="font-medium tabular-nums">
              {time(record?.checkInAt ?? null)}
              {record && record.lateMinutes > 0 && <span className="ml-1 text-sm text-destructive">(muộn {record.lateMinutes} phút)</span>}
            </p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Ra ca</p>
            <p className="font-medium tabular-nums">{time(record?.checkOutAt ?? null)}</p>
          </div>
        </div>
        <div className="flex gap-2">
          {!checkedIn && (
            <Button type="button" size="lg" disabled={busy} onClick={() => void punch("check-in")}>
              {busy ? <Loader2 className="animate-spin" /> : <LogIn />} Vào ca
            </Button>
          )}
          {checkedIn && (
            <Button type="button" size="lg" variant={checkedOut ? "outline" : "default"} disabled={busy}
              onClick={() => void punch("check-out")}>
              {busy ? <Loader2 className="animate-spin" /> : <LogOut />} {checkedOut ? "Cập nhật giờ ra" : "Ra ca"}
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
