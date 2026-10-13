"use client"

import { useEffect, useState } from "react"
import { CalendarDays, CalendarPlus, Clock, List, Loader2, LogIn, Trash2, UserX, Zap, type LucideIcon } from "lucide-react"

import { RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { apiErrorMessage } from "@/lib/api-helpers"
import type { Page } from "@/lib/pagination"
import {
  ATTENDANCE_STATUS_LABELS,
  type AttendanceRecord,
  type AttendanceStatus,
  type CompanyHoliday,
  type WorkforceBody,
} from "@/lib/workforce"
import {
  EmptyRow,
  SectionError,
  monthRange,
  time,
  today,
  useWorkforceAction,
  workforceApi,
  type WorkforceContext,
} from "@/components/workforce/workforce-shared"

const STATUS_TONE: Record<AttendanceStatus, "default" | "secondary" | "outline" | "destructive"> = {
  PRESENT: "default",
  PAID_LEAVE: "secondary",
  UNPAID_LEAVE: "outline",
  MATERNITY_LEAVE: "secondary",
  SICK_LEAVE: "secondary",
  UNAUTHORIZED_ABSENCE: "destructive",
  HOLIDAY: "outline",
  MISSING_PUNCH: "destructive",
}

/** Set by an approved leave covering the whole day; HR changes these only through the leave workflow. */
const FULL_DAY_LEAVE: ReadonlySet<AttendanceStatus> = new Set(["PAID_LEAVE", "UNPAID_LEAVE", "MATERNITY_LEAVE", "SICK_LEAVE"])

/** Colour group of a calendar day; the legend lists them in this order. */
type DayTone = "present" | "late" | "leave" | "absent" | "missing" | "holiday"

const DAY_TONES: Record<DayTone, { label: string; cell: string; dot: string }> = {
  present: { label: "Đủ công", cell: "border-emerald-200 bg-emerald-50 dark:border-emerald-900 dark:bg-emerald-950/40", dot: "bg-emerald-500" },
  late: { label: "Muộn / về sớm", cell: "border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950/40", dot: "bg-amber-500" },
  leave: { label: "Nghỉ theo đơn", cell: "border-sky-200 bg-sky-50 dark:border-sky-900 dark:bg-sky-950/40", dot: "bg-sky-500" },
  absent: { label: "Vắng không phép", cell: "border-red-200 bg-red-50 dark:border-red-900 dark:bg-red-950/40", dot: "bg-red-500" },
  missing: { label: "Thiếu dữ liệu", cell: "border-dashed border-red-300 dark:border-red-800", dot: "border border-dashed border-red-500" },
  holiday: { label: "Ngày lễ", cell: "border-violet-200 bg-violet-50 dark:border-violet-900 dark:bg-violet-950/40", dot: "bg-violet-500" },
}

const WEEKDAYS = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"] as const

const BUSINESS_TIME = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Asia/Ho_Chi_Minh", hour: "2-digit", minute: "2-digit", hourCycle: "h23",
})

/** HH:mm of an instant in the business time zone, for a time input. */
const businessTime = (instant: string) => BUSINESS_TIME.format(new Date(instant))

/** Instant of a date + HH:mm in the business time zone (UTC+7, no daylight saving). */
const businessInstant = (date: string, hhmm: string) => new Date(`${date}T${hhmm}:00+07:00`)

/** 0 = Sunday … 6 = Saturday for a YYYY-MM-DD date. */
const weekday = (date: string) => new Date(`${date}T00:00:00Z`).getUTCDay()

/** Minutes as hours, e.g. 460 → "7h40", 480 → "8h", 25 → "25 phút". */
const hours = (minutes: number) => {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0 && m > 0) return `${m} phút`
  return m === 0 ? `${h}h` : `${h}h${String(m).padStart(2, "0")}`
}

/** One calendar day of the month with whatever is known about it. */
type Day = { date: string; record?: AttendanceRecord; holiday?: CompanyHoliday }

/** A Sunday or holiday: work then is overtime, so late and early-leave minutes mean nothing. */
const isOffDay = (day: Day) => weekday(day.date) === 0 || day.holiday !== undefined

/** A day that has not ended yet; missing punches there are not a problem yet. */
const isUpcoming = (day: Day) => day.date >= today()

/** Late or early-leave minutes that count; none on an off day. */
const lateOrEarly = (day: Day) =>
  !day.record || isOffDay(day) ? 0 : day.record.lateMinutes + day.record.earlyLeaveMinutes

const decimal = (value: number) => value.toLocaleString("vi-VN", { maximumFractionDigits: 2 })

function dayTone(day: Day): DayTone | null {
  const { record, holiday } = day
  if (!record) return holiday ? "holiday" : null
  if (record.status === "MISSING_PUNCH") return isUpcoming(day) ? null : "missing"
  if (record.status === "UNAUTHORIZED_ABSENCE") return "absent"
  if (FULL_DAY_LEAVE.has(record.status)) return "leave"
  if (record.status === "HOLIDAY") return "holiday"
  return lateOrEarly(day) > 0 ? "late" : "present"
}

/** Status text of a record, with a not-yet-due day shown as such instead of missing. */
const statusLabel = (day: Day, record: AttendanceRecord) =>
  record.status === "MISSING_PUNCH" && isUpcoming(day) ? "Chưa tới" : ATTENDANCE_STATUS_LABELS[record.status]

export function AttendanceSection({ employeeId, permissions, month }: WorkforceContext) {
  const canReadAttendance = permissions.has("attendance.self.read") || permissions.has("attendance.read")
  const canManage = permissions.has("attendance.manage")
  const canApproveOvertime = permissions.has("attendance.overtime.approve")
  const canReadHolidays = permissions.has("holiday.read")
  const canManageHolidays = permissions.has("organization.manage")

  const [rows, setRows] = useState<AttendanceRecord[]>([])
  const [holidays, setHolidays] = useState<CompanyHoliday[]>([])
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  // Employees look at their month as a calendar; HR works through the list, which carries the actions.
  const [view, setView] = useState<"calendar" | "list">(canManage ? "list" : "calendar")
  const [selected, setSelected] = useState<Day | null>(null)
  const [absent, setAbsent] = useState<AttendanceRecord | null>(null)
  // A record to fix, or {} to enter a day that has no record yet (a Sunday or holiday worked).
  const [punching, setPunching] = useState<{ record?: AttendanceRecord } | null>(null)
  const [approving, setApproving] = useState<AttendanceRecord | null>(null)
  const { busy, run } = useWorkforceAction(() => setRevision((value) => value + 1))
  const { first, last } = monthRange(month)

  useEffect(() => {
    let active = true
    Promise.all([
      employeeId > 0 && canReadAttendance
        ? workforceApi<Page<AttendanceRecord>>(`attendance/employees/${employeeId}?from=${first}&to=${last}&size=100`)
          .then((page) => page.content)
        : Promise.resolve([]),
      canReadHolidays ? workforceApi<CompanyHoliday[]>(`company-holidays?from=${first}&to=${last}`) : Promise.resolve([]),
    ])
      .then(([records, loadedHolidays]) => {
        if (!active) return
        setRows(records)
        setHolidays(loadedHolidays)
        setError(null)
      })
      .catch((caught: unknown) => { if (active) setError(apiErrorMessage(caught)) })
    return () => { active = false }
  }, [employeeId, first, last, canReadAttendance, canReadHolidays, revision])

  const days: Day[] = Array.from({ length: Number(last.slice(8, 10)) }, (_, index) => {
    const date = `${month}-${String(index + 1).padStart(2, "0")}`
    return {
      date,
      record: rows.find((row) => row.workDate === date),
      holiday: holidays.find((holiday) => holiday.holidayDate === date),
    }
  })
  const actions: DayActions = {
    busy,
    onPunch: canManage ? (record) => setPunching({ record }) : undefined,
    onAbsent: canManage ? setAbsent : undefined,
    onOvertime: canApproveOvertime ? setApproving : undefined,
  }

  return (
    <div className="grid gap-6">
      <Card>
        <CardHeader>
          <CardTitle>Bảng công tháng {month.slice(5, 7)}/{month.slice(0, 4)}</CardTitle>
          <CardDescription>
            Công tính lương không gồm giờ nghỉ giữa ca; ngày nghỉ phép được duyệt tự gắn vào bảng công.
            Bảng công được tự chuẩn bị mỗi đêm.
          </CardDescription>
          <CardAction className="flex flex-wrap justify-end gap-2">
            <Tabs value={view} onValueChange={(value) => setView(value as "calendar" | "list")}>
              <TabsList>
                <TabsTrigger value="calendar"><CalendarDays /> Lịch</TabsTrigger>
                <TabsTrigger value="list"><List /> Danh sách</TabsTrigger>
              </TabsList>
            </Tabs>
            {canManage && employeeId > 0 && (
              <Button type="button" size="sm" variant="outline" disabled={busy} onClick={() => setPunching({})}>
                <LogIn /> Nhập giờ ngày khác
              </Button>
            )}
            {canManage && (
              <Button type="button" size="sm" variant="outline" disabled={busy}
                onClick={() => void run("Đã chuẩn bị bảng công",
                  () => workforceApi(`attendance/prepare?year=${month.slice(0, 4)}&month=${Number(month.slice(5, 7))}`, "POST"))}>
                <CalendarPlus /> Chuẩn bị ngay
              </Button>
            )}
          </CardAction>
        </CardHeader>
        <CardContent className="grid gap-4">
          <SectionError error={error} />
          {employeeId > 0 && <MonthSummary days={days} />}
          {view === "calendar"
            ? <CalendarView days={days} onSelect={setSelected} />
            : <ListView days={days} actions={actions}
                emptyText={employeeId ? "Chưa có dữ liệu công trong tháng." : "Nhập mã nhân viên để xem bảng công."} />}
          <Legend />
        </CardContent>
      </Card>

      {canReadHolidays && (
        <HolidayCard holidays={holidays} canManage={canManageHolidays} busy={busy} run={run} />
      )}

      {selected && (
        <DayDialog day={selected} actions={actions} onClose={() => setSelected(null)} />
      )}
      {punching && (
        <PunchDialog employeeId={employeeId} record={punching.record} month={month} busy={busy} onClose={() => setPunching(null)}
          onSubmit={(body) => run("Đã ghi giờ vào/ra", () => workforceApi("attendance/punches", "POST", body))} />
      )}
      {absent && (
        <AbsenceDialog record={absent} busy={busy} onClose={() => setAbsent(null)}
          onSubmit={(body) => run("Đã ghi vắng không phép", () => workforceApi(`attendance/${absent.id}`, "PUT", body))} />
      )}
      {approving && (
        <OvertimeDialog record={approving} holidays={holidays} busy={busy} onClose={() => setApproving(null)}
          onSubmit={(body) => run("Đã duyệt tăng ca", () => workforceApi(`attendance/${approving.id}/overtime-approval`, "POST", body))} />
      )}
    </div>
  )
}

/** Row actions; each is present only when the user may perform it. */
type DayActions = {
  busy: boolean
  onPunch?: (record: AttendanceRecord) => void
  onAbsent?: (record: AttendanceRecord) => void
  onOvertime?: (record: AttendanceRecord) => void
}

function MonthSummary({ days }: { days: Day[] }) {
  const rows = days.flatMap((day) => day.record ? [day.record] : [])
  // A Sunday or holiday worked has a record but is not part of the standard month.
  const standard = days.filter((day) => day.record && !isOffDay(day))
  const payableDays = rows.reduce((sum, row) => sum + (row.scheduledMinutes > 0 ? row.payableMinutes / row.scheduledMinutes : 0), 0)
  const lateDays = days.filter((day) => lateOrEarly(day) > 0)
  const lateMinutes = lateDays.reduce((sum, day) => sum + lateOrEarly(day), 0)
  const leaveDays = rows.filter((row) => FULL_DAY_LEAVE.has(row.status)).length
  const overtime = rows.reduce((sum, row) => sum + row.overtimeMinutes, 0)
  const missing = days.filter((day) => day.record?.status === "MISSING_PUNCH" && !isUpcoming(day)).length
  const stats: { label: string; value: string; hint?: string; alert?: boolean }[] = [
    { label: "Công tính lương", value: decimal(payableDays), hint: `/ ${standard.length} ngày` },
    { label: "Muộn / về sớm", value: `${lateDays.length} lần`, hint: lateMinutes > 0 ? hours(lateMinutes) : undefined },
    { label: "Nghỉ theo đơn", value: `${leaveDays} ngày` },
    { label: "Tăng ca đã duyệt", value: hours(overtime) },
    { label: "Cần xử lý", value: `${missing} ngày`, alert: missing > 0 },
  ]

  return (
    <dl className="grid gap-3 sm:grid-cols-3 lg:grid-cols-5">
      {stats.map((stat) => (
        <div key={stat.label} className={`rounded-xl border px-4 py-3 ${stat.alert ? "border-destructive/40 bg-destructive/5" : ""}`}>
          <dt className="text-xs text-muted-foreground">{stat.label}</dt>
          <dd className={`text-2xl font-semibold ${stat.alert ? "text-destructive" : ""}`}>
            {stat.value} {stat.hint && <span className="text-sm font-normal text-muted-foreground">{stat.hint}</span>}
          </dd>
        </div>
      ))}
    </dl>
  )
}

function CalendarView({ days, onSelect }: { days: Day[]; onSelect: (day: Day) => void }) {
  // Weeks start on Monday; blank cells pad the first week.
  const leading = (weekday(days[0].date) + 6) % 7

  return (
    <div className="grid grid-cols-7 gap-1.5 text-sm">
      {[...WEEKDAYS.slice(1), WEEKDAYS[0]].map((label) => (
        <div key={label} className="px-1 pb-1 text-center text-xs font-medium text-muted-foreground">{label}</div>
      ))}
      {Array.from({ length: leading }, (_, index) => <div key={`blank-${index}`} />)}
      {days.map((day) => {
        const tone = dayTone(day)
        const { record, holiday } = day
        const sunday = weekday(day.date) === 0
        return (
          <button key={day.date} type="button" disabled={!record && !holiday} onClick={() => onSelect(day)}
            aria-label={`Ngày ${day.date}`}
            className={`flex min-h-20 flex-col items-start gap-0.5 rounded-lg border p-1.5 text-left transition-colors enabled:hover:ring-2 enabled:hover:ring-ring/40 ${
              tone ? DAY_TONES[tone].cell : sunday ? "bg-muted/50" : ""}`}>
            <span className={`text-xs font-medium ${sunday ? "text-muted-foreground" : ""}`}>{Number(day.date.slice(8, 10))}</span>
            {record?.checkInAt && (
              <span className="text-xs tabular-nums">{time(record.checkInAt)}–{time(record.checkOutAt)}</span>
            )}
            {record && !record.checkInAt && (
              <span className={`text-xs ${tone ? "" : "text-muted-foreground"}`}>{statusLabel(day, record)}</span>
            )}
            {!record && holiday && <span className="line-clamp-2 text-xs">{holiday.name}</span>}
            {record && record.overtimeMinutes > 0 && (
              <span className="text-xs text-muted-foreground">+{hours(record.overtimeMinutes)} tăng ca</span>
            )}
          </button>
        )
      })}
    </div>
  )
}

function ListView({ days, actions, emptyText }: { days: Day[]; actions: DayActions; emptyText: string }) {
  const shown = days.filter((day) => day.record || day.holiday)

  return (
    <div className="rounded-xl border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Ngày</TableHead>
            <TableHead>Ca</TableHead>
            <TableHead>Vào – Ra</TableHead>
            <TableHead>Trạng thái</TableHead>
            <TableHead className="text-right">Muộn / Sớm</TableHead>
            <TableHead className="text-right">Công</TableHead>
            <TableHead className="text-right">Tăng ca</TableHead>
            <TableHead className="text-right">Thao tác</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {shown.map((day) => {
            const { record, holiday } = day
            const upcoming = record?.status === "MISSING_PUNCH" && isUpcoming(day)
            return (
              <TableRow key={day.date} className={isOffDay(day) ? "bg-muted/40" : undefined}>
                <TableCell className="font-medium whitespace-nowrap">
                  <span className="inline-block w-7 text-muted-foreground">{WEEKDAYS[weekday(day.date)]}</span>
                  {day.date.slice(8, 10)}/{day.date.slice(5, 7)}
                </TableCell>
                {record ? <>
                  <TableCell className="text-muted-foreground tabular-nums">{time(record.scheduledStartAt)}–{time(record.scheduledEndAt)}</TableCell>
                  <TableCell className="tabular-nums">{time(record.checkInAt)} – {time(record.checkOutAt)}</TableCell>
                  <TableCell>
                    <Badge variant={upcoming ? "outline" : STATUS_TONE[record.status]}>{statusLabel(day, record)}</Badge>
                    {holiday && <span className="block text-xs text-muted-foreground">{holiday.name}</span>}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {lateOrEarly(day) > 0 ? `${record.lateMinutes} / ${record.earlyLeaveMinutes} phút` : "—"}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{hours(record.payableMinutes)} / {hours(record.scheduledMinutes)}</TableCell>
                  <TableCell className="text-right tabular-nums">
                    {record.overtimeMinutes > 0 ? `${hours(record.overtimeMinutes)} × ${record.overtimeMultiplier}` : "—"}
                    {record.overtimeMinutes > 0 && record.overtimeTaxExempt && <span className="block text-xs text-muted-foreground">miễn thuế</span>}
                  </TableCell>
                  <RowActionsCell label={`ngày ${record.workDate}`}>
                    {recordActions(day, record, actions).map(({ key, icon, label, destructive, onClick }) => (
                      <RowActionItem key={key} icon={icon} destructive={destructive} disabled={actions.busy} onClick={onClick}>{label}</RowActionItem>
                    ))}
                  </RowActionsCell>
                </> : (
                  <TableCell colSpan={7} className="text-muted-foreground">Ngày lễ · {holiday?.name}</TableCell>
                )}
              </TableRow>
            )
          })}
          {shown.length === 0 && <EmptyRow colSpan={8}>{emptyText}</EmptyRow>}
        </TableBody>
      </Table>
    </div>
  )
}

/** The actions a record allows; the list shows them in its row menu, the day dialog as buttons. */
function recordActions(day: Day, record: AttendanceRecord, actions: DayActions) {
  const { onPunch, onAbsent, onOvertime } = actions
  // A future day cannot have punches yet, and a full day of approved leave belongs to the leave workflow.
  const editable = !FULL_DAY_LEAVE.has(record.status) && day.date <= today()
  const items: { key: string; icon: LucideIcon; label: string; destructive?: boolean; onClick: () => void }[] = []
  if (onOvertime && record.checkOutAt) items.push({ key: "overtime", icon: Zap, label: "Duyệt tăng ca", onClick: () => onOvertime(record) })
  if (onPunch && editable) items.push({ key: "punch", icon: LogIn, label: "Nhập giờ vào/ra", onClick: () => onPunch(record) })
  if (onAbsent && editable) items.push({ key: "absent", icon: UserX, label: "Ghi vắng", destructive: true, onClick: () => onAbsent(record) })
  return items
}

function RecordActions({ day, record, actions }: { day: Day; record: AttendanceRecord; actions: DayActions }) {
  return recordActions(day, record, actions).map(({ key, icon: Icon, label, destructive, onClick }) => (
    <Button key={key} type="button" variant="outline" size="sm" disabled={actions.busy}
      className={destructive ? "text-muted-foreground hover:text-destructive" : undefined} onClick={onClick}>
      <Icon />{label}
    </Button>
  ))
}

/** Details of a calendar day, with the same actions as its list row. */
function DayDialog({ day, actions, onClose }: { day: Day; actions: DayActions; onClose: () => void }) {
  const { record, holiday } = day
  const facts: [string, string][] = record ? [
    ["Trạng thái", statusLabel(day, record)],
    ["Ca", `${time(record.scheduledStartAt)}–${time(record.scheduledEndAt)}`],
    ["Vào – Ra", `${time(record.checkInAt)} – ${time(record.checkOutAt)}`],
    ["Công tính lương", `${hours(record.payableMinutes)} / ${hours(record.scheduledMinutes)}`],
    ["Thực làm", hours(record.workedMinutes)],
    ["Đi muộn / về sớm", lateOrEarly(day) > 0 ? `${record.lateMinutes} / ${record.earlyLeaveMinutes} phút` : "—"],
    ["Tăng ca", record.overtimeMinutes > 0 ? `${hours(record.overtimeMinutes)} × ${record.overtimeMultiplier}` : "—"],
  ] : []
  // Close first so the action's own dialog is the only one open.
  const wrap = (action?: (record: AttendanceRecord) => void) =>
    action && ((target: AttendanceRecord) => { onClose(); action(target) })

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{WEEKDAYS[weekday(day.date)]}, {day.date.slice(8, 10)}/{day.date.slice(5, 7)}/{day.date.slice(0, 4)}</DialogTitle>
          {holiday && <DialogDescription>Ngày lễ · {holiday.name}</DialogDescription>}
        </DialogHeader>
        {record && (
          <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
            {facts.map(([label, value]) => <div key={label} className="contents">
              <dt className="text-muted-foreground">{label}</dt>
              <dd className="tabular-nums">{value}</dd>
            </div>)}
          </dl>
        )}
        {record && (
          <DialogFooter className="mt-4">
            <RecordActions day={day} record={record} actions={{
              ...actions, onPunch: wrap(actions.onPunch), onAbsent: wrap(actions.onAbsent), onOvertime: wrap(actions.onOvertime),
            }} />
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  )
}

function Legend() {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
      {Object.entries(DAY_TONES).map(([tone, { label, dot }]) => (
        <li key={tone} className="flex items-center gap-1.5"><span className={`size-2.5 rounded-full ${dot}`} />{label}</li>
      ))}
    </ul>
  )
}

function HolidayCard({ holidays, canManage, busy, run }: {
  holidays: CompanyHoliday[]
  canManage: boolean
  busy: boolean
  run: (success: string, action: () => Promise<unknown>) => Promise<boolean>
}) {
  const [date, setDate] = useState("")
  const [name, setName] = useState("")

  return (
    <Card>
      <CardHeader>
        <CardTitle>Ngày lễ công ty trong tháng</CardTitle>
        <CardDescription>Ngày lễ không tính là ngày làm việc; thêm hoặc xóa ngày lễ đưa kỳ lương đã tính về nháp.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        {holidays.length === 0 && <p className="text-sm text-muted-foreground">Không có ngày lễ trong tháng.</p>}
        <ul className="grid gap-2">
          {holidays.map((holiday) => (
            <li key={holiday.id} className="flex items-center justify-between rounded-lg border px-3 py-2 text-sm">
              <span><span className="font-medium">{holiday.holidayDate}</span> · {holiday.name}</span>
              {canManage && (
                <Button type="button" variant="ghost" size="icon-sm" aria-label={`Xóa ngày lễ ${holiday.name}`} disabled={busy}
                  className="text-muted-foreground hover:text-destructive"
                  onClick={() => void run("Đã xóa ngày lễ", () => workforceApi(`company-holidays/${holiday.id}`, "DELETE"))}>
                  <Trash2 />
                </Button>
              )}
            </li>
          ))}
        </ul>
        {canManage && (
          <form className="flex flex-wrap items-end gap-2" onSubmit={(event) => {
            event.preventDefault()
            void run("Đã thêm ngày lễ", () => workforceApi("company-holidays", "POST",
              { date, name: name.trim() } satisfies WorkforceBody["CompanyHolidayRequest"]))
              .then((ok) => { if (ok) { setDate(""); setName("") } })
          }}>
            <div className="grid gap-1.5">
              <Label htmlFor="holiday-date">Ngày</Label>
              <Input id="holiday-date" type="date" required value={date} onChange={(event) => setDate(event.target.value)} />
            </div>
            <div className="grid min-w-56 flex-1 gap-1.5">
              <Label htmlFor="holiday-name">Tên ngày lễ</Label>
              <Input id="holiday-name" required maxLength={150} value={name} onChange={(event) => setName(event.target.value)} />
            </div>
            <Button type="submit" disabled={busy || !date || !name.trim()}><CalendarPlus /> Thêm ngày lễ</Button>
          </form>
        )}
      </CardContent>
    </Card>
  )
}

/**
 * HR reconciles a day by entering the actual check-in and check-out; the server derives every
 * minute figure from them, as it does for the time clock. Overwrites earlier punches or an
 * earlier adjustment and clears approved overtime, which must be approved again.
 */
function PunchDialog({ employeeId, record, month, busy, onClose, onSubmit }: {
  employeeId: number
  record?: AttendanceRecord
  month: string
  busy: boolean
  onClose: () => void
  onSubmit: (body: WorkforceBody["RecordPunchesRequest"]) => Promise<boolean>
}) {
  const { first, last } = monthRange(month)
  const [date, setDate] = useState(record?.workDate ?? "")
  // Prefill with the recorded punches, else with the scheduled shift (the usual "forgot to punch" case).
  const [checkIn, setCheckIn] = useState(record ? businessTime(record.checkInAt ?? record.scheduledStartAt) : "08:00")
  const [checkOut, setCheckOut] = useState(record ? businessTime(record.checkOutAt ?? record.scheduledEndAt) : "17:00")
  const [note, setNote] = useState("")
  const [error, setError] = useState<string | null>(null)

  function submit(event: React.FormEvent) {
    event.preventDefault()
    const checkInAt = businessInstant(date, checkIn)
    const checkOutAt = businessInstant(date, checkOut)
    // A check-out earlier than the check-in is the next morning, as on a night shift.
    if (checkOutAt <= checkInAt) checkOutAt.setUTCDate(checkOutAt.getUTCDate() + 1)
    if (checkOutAt > new Date()) { setError("Giờ ra chưa tới, không ghi trước được"); return }
    void onSubmit({
      employeeId, workDate: date, checkInAt: checkInAt.toISOString(), checkOutAt: checkOutAt.toISOString(), note: note.trim(),
    }).then((ok) => { if (ok) onClose() })
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={submit}>
          <DialogHeader>
            <DialogTitle>{record ? `Nhập giờ vào/ra ngày ${record.workDate}` : "Nhập giờ vào/ra ngày khác"}</DialogTitle>
            <DialogDescription>
              {record
                ? `Hiện tại: ${ATTENDANCE_STATUS_LABELS[record.status]}, ${record.payableMinutes}/${record.scheduledMinutes} phút. `
                : "Dùng cho ngày chưa có trong bảng công, ví dụ đi làm Chủ nhật hoặc ngày lễ. "}
              Hệ thống tự tính phút muộn, sớm và công tính lương; tăng ca đã duyệt của ngày này phải duyệt lại.
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            {!record && (
              <div className="grid gap-1.5 sm:col-span-2">
                <Label htmlFor="punch-date">Ngày làm việc</Label>
                <Input id="punch-date" type="date" required min={first} max={last} value={date}
                  onChange={(event) => setDate(event.target.value)} />
              </div>
            )}
            <div className="grid gap-1.5">
              <Label htmlFor="punch-in">Giờ vào</Label>
              <Input id="punch-in" type="time" required value={checkIn} onChange={(event) => setCheckIn(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="punch-out">Giờ ra</Label>
              <Input id="punch-out" type="time" required value={checkOut} onChange={(event) => setCheckOut(event.target.value)} />
            </div>
            <p className="text-xs text-muted-foreground sm:col-span-2">Giờ ra nhỏ hơn giờ vào được hiểu là sáng hôm sau (ca đêm).</p>
            <div className="grid gap-1.5 sm:col-span-2">
              <Label htmlFor="punch-note">Lý do và căn cứ</Label>
              <Textarea id="punch-note" required rows={3} maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} />
            </div>
            {error && <p role="alert" className="text-sm text-destructive sm:col-span-2">{error}</p>}
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose}>Hủy</Button>
            <Button type="submit" disabled={busy || !date || !note.trim()}>{busy && <Loader2 className="animate-spin" />} Lưu giờ vào/ra</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function AbsenceDialog({ record, busy, onClose, onSubmit }: {
  record: AttendanceRecord
  busy: boolean
  onClose: () => void
  onSubmit: (body: WorkforceBody["AdjustAttendanceRequest"]) => Promise<boolean>
}) {
  const [note, setNote] = useState("")

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={(event) => {
          event.preventDefault()
          void onSubmit({
            workedMinutes: 0, payableMinutes: 0, lateMinutes: 0, earlyLeaveMinutes: 0,
            status: "UNAUTHORIZED_ABSENCE", note: note.trim(),
          }).then((ok) => { if (ok) onClose() })
        }}>
          <DialogHeader>
            <DialogTitle>Ghi vắng không phép ngày {record.workDate}</DialogTitle>
            <DialogDescription>
              Hiện tại: {ATTENDANCE_STATUS_LABELS[record.status]}, {record.payableMinutes}/{record.scheduledMinutes} phút.
              Ngày này sẽ không được trả lương. Nghỉ có lương phải đi qua quy trình duyệt đơn.
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-1.5">
            <Label htmlFor="absence-note">Lý do và căn cứ</Label>
            <Textarea id="absence-note" required rows={3} value={note} onChange={(event) => setNote(event.target.value)} />
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose}>Hủy</Button>
            <Button type="submit" variant="destructive" disabled={busy || !note.trim()}>
              {busy && <Loader2 className="animate-spin" />} Ghi vắng không phép
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function OvertimeDialog({ record, holidays, busy, onClose, onSubmit }: {
  record: AttendanceRecord
  holidays: CompanyHoliday[]
  busy: boolean
  onClose: () => void
  onSubmit: (body: WorkforceBody["ApproveOvertimeRequest"]) => Promise<boolean>
}) {
  // The server enforces the same minimums: 300% on company holidays, 200% on Sundays, 150% otherwise.
  const minimum = holidays.some((holiday) => holiday.holidayDate === record.workDate) ? 3
    : new Date(`${record.workDate}T12:00:00Z`).getUTCDay() === 0 ? 2 : 1.5
  const [minutes, setMinutes] = useState(String(record.overtimeMinutes))
  const [multiplier, setMultiplier] = useState(String(record.overtimeMinutes > 0 ? record.overtimeMultiplier : minimum))
  const [taxExempt, setTaxExempt] = useState(record.overtimeTaxExempt)
  const [error, setError] = useState<string | null>(null)

  function submit(event: React.FormEvent) {
    event.preventDefault()
    const overtimeMinutes = Number(minutes)
    const overtimeMultiplier = Number(multiplier)
    if (!Number.isInteger(overtimeMinutes) || overtimeMinutes < 0) { setError("Số phút không hợp lệ"); return }
    if (!Number.isFinite(overtimeMultiplier) || overtimeMultiplier < minimum) { setError(`Hệ số tối thiểu là ${minimum}`); return }
    void onSubmit({ overtimeMinutes, overtimeMultiplier, taxExempt: overtimeMinutes > 0 && taxExempt })
      .then((ok) => { if (ok) onClose() })
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={submit}>
          <DialogHeader>
            <DialogTitle>Duyệt tăng ca ngày {record.workDate}</DialogTitle>
            <DialogDescription>
              Đã làm {record.workedMinutes} phút, trong đó {record.payableMinutes} phút là công thường. Nhập 0 phút để bỏ duyệt.
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-4">
            <MinutesField id="overtime-minutes" label="Phút tăng ca được duyệt" value={minutes} onChange={setMinutes} />
            <div className="grid gap-1.5">
              <Label htmlFor="overtime-multiplier">Hệ số (tối thiểu {minimum})</Label>
              <Input id="overtime-multiplier" type="number" step="0.1" min={minimum} required value={multiplier}
                onChange={(event) => setMultiplier(event.target.value)} />
            </div>
            <label className="flex items-start gap-2 text-sm">
              <input type="checkbox" className="mt-1" checked={taxExempt} onChange={(event) => setTaxExempt(event.target.checked)} />
              <span>Đã đối soát, phần tăng ca này đủ điều kiện miễn thuế TNCN. Bỏ chọn nếu chưa chắc; khi đó tiền tăng ca chịu thuế.</span>
            </label>
            {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose}>Hủy</Button>
            <Button type="submit" disabled={busy}>{busy && <Loader2 className="animate-spin" />}<Clock /> Duyệt</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function MinutesField({ id, label, value, onChange }: { id: string; label: string; value: string; onChange: (value: string) => void }) {
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} type="number" min="0" step="1" required value={value} onChange={(event) => onChange(event.target.value)} />
    </div>
  )
}
