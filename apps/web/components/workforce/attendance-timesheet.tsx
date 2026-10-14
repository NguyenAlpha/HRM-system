"use client"

import { useEffect, useState } from "react"
import { Eye, RefreshCw } from "lucide-react"

import { ErrorAlert } from "@/components/common/list-controls"
import { ALL_OPTION, FilterSelect, ListToolbar, useDebouncedValue } from "@/components/common/list-filters"
import { RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import {
  DAY_TONES,
  WEEKDAYS,
  dayTone,
  decimal,
  hours,
  lateOrEarly,
  monthTotals,
  weekday,
  type Day,
} from "@/components/workforce/attendance-section"
import { EmptyRow, monthRange, workforceApi } from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import { getOrganizationUnitOptions } from "@/lib/reference"
import { ATTENDANCE_STATUS_LABELS, type AttendanceStatus, type CompanyHoliday, type TimesheetRow } from "@/lib/workforce"

/** Timesheet symbol of a full-day leave, by the leave's attendance status. */
const LEAVE_CODES: Partial<Record<AttendanceStatus, string>> = {
  PAID_LEAVE: "P",
  UNPAID_LEAVE: "KL",
  SICK_LEAVE: "Ô",
  MATERNITY_LEAVE: "TS",
}

const LEGEND: [string, string][] = [
  ["X", "Đủ công"], ["M", "Muộn / về sớm"], ["P", "Nghỉ có lương"], ["KL", "Nghỉ không lương"], ["Ô", "Nghỉ ốm"],
  ["TS", "Thai sản"], ["V", "Vắng không phép"], ["?", "Thiếu chấm công"], ["L", "Ngày lễ"], ["+", "Có tăng ca"],
]

/** The symbol shown in a day cell; blank for a day with nothing to report yet. */
function dayCode(day: Day): string {
  const tone = dayTone(day)
  if (tone === "present") return "X"
  if (tone === "late") return "M"
  if (tone === "absent") return "V"
  if (tone === "missing") return "?"
  if (tone === "holiday") return "L"
  if (tone === "leave" && day.record) return LEAVE_CODES[day.record.status] ?? "P"
  return ""
}

function dayTitle(day: Day): string {
  const parts = [`${day.date.slice(8, 10)}/${day.date.slice(5, 7)}`]
  if (day.holiday) parts.push(day.holiday.name)
  if (day.record) parts.push(ATTENDANCE_STATUS_LABELS[day.record.status])
  if (lateOrEarly(day) > 0) parts.push(`muộn/sớm ${lateOrEarly(day)} phút`)
  if (day.record && day.record.overtimeMinutes > 0) parts.push(`tăng ca ${hours(day.record.overtimeMinutes)}`)
  return parts.join(" · ")
}

export type TimesheetEmployee = { id: number; employeeCode: string; fullName: string }

/**
 * Every employee in the reader's scope for one month, one line each: a symbol per day and the
 * month's totals, computed the same way as the single-employee calendar.
 */
export function AttendanceTimesheet({ month, onOpen }: { month: string; onOpen: (employee: TimesheetEmployee) => void }) {
  const { portal, session } = usePortalSession()
  const canReadHolidays = session.account.permissions.some((permission) => permission.code === "holiday.read")
  const [keywordInput, setKeywordInput] = useState("")
  const keyword = useDebouncedValue(keywordInput.trim())
  const [unitId, setUnitId] = useState("")
  const [unitOptions, setUnitOptions] = useState<{ value: string; label: string }[]>([])
  const [reloads, setReloads] = useState(0)
  const [data, setData] = useState<{ key: string; rows: TimesheetRow[]; holidays: CompanyHoliday[] } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const { first, last } = monthRange(month)

  useEffect(() => {
    let active = true
    getOrganizationUnitOptions(portal)
      .then((units) => { if (active) setUnitOptions(units.map((unit) => ({ value: String(unit.id), label: unit.name }))) })
      .catch(() => undefined)
    return () => { active = false }
  }, [portal])

  const params = new URLSearchParams({ year: month.slice(0, 4), month: String(Number(month.slice(5, 7))) })
  if (keyword) params.set("keyword", keyword)
  if (unitId) params.set("organizationUnitId", unitId)
  const key = `${params}#${reloads}`
  const loading = data?.key !== key || keyword !== keywordInput.trim()

  useEffect(() => {
    let active = true
    Promise.all([
      workforceApi<TimesheetRow[]>(`attendance/timesheet?${params}`),
      canReadHolidays ? workforceApi<CompanyHoliday[]>(`company-holidays?from=${first}&to=${last}`) : Promise.resolve([]),
    ])
      .then(([rows, holidays]) => { if (active) { setData({ key, rows, holidays }); setError(null) } })
      .catch((caught: unknown) => { if (active) { setData({ key, rows: [], holidays: [] }); setError(apiErrorMessage(caught)) } })
    return () => { active = false }
    // `key` is built from `params` and the reload counter; the holidays follow the same month.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  const dates = Array.from({ length: Number(last.slice(8, 10)) }, (_, index) => `${month}-${String(index + 1).padStart(2, "0")}`)
  const holidays = data?.holidays ?? []
  const holidayOn = (date: string) => holidays.find((holiday) => holiday.holidayDate === date)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Bảng công tổng hợp tháng {month.slice(5, 7)}/{month.slice(0, 4)}</CardTitle>
        <CardDescription>
          Nhân viên trong phạm vi bạn được giao có làm việc trong tháng. Bấm một dòng để xem và chỉnh công chi tiết của người đó.
        </CardDescription>
        <CardAction>
          <Button type="button" variant="outline" size="sm" disabled={loading} onClick={() => setReloads((value) => value + 1)}>
            <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="grid gap-4">
        <ErrorAlert error={error} />
        <ListToolbar keyword={keywordInput} onKeywordChange={setKeywordInput} placeholder="Tìm theo họ tên hoặc mã nhân viên"
          activeFilters={unitId ? 1 : 0} onReset={() => { setKeywordInput(""); setUnitId("") }}>
          {unitOptions.length > 0 && (
            <FilterSelect id="timesheet-unit-filter" label="Đơn vị (phân công hiện tại)" value={unitId || ALL_OPTION}
              onChange={(value) => setUnitId(value === ALL_OPTION ? "" : value)} options={unitOptions} />
          )}
        </ListToolbar>

        {loading && !data
          ? <Skeleton className="h-48 w-full" />
          : (
            <div className="overflow-x-auto rounded-xl border">
              <Table className="text-xs">
                <TableHeader>
                  <TableRow>
                    <TableHead className="sticky left-0 z-10 min-w-48 bg-background">Nhân viên</TableHead>
                    <TableHead className="text-right" title="Công tính lương / ngày công chuẩn">Công</TableHead>
                    <TableHead className="text-right" title="Số lần đi muộn hoặc về sớm">Muộn</TableHead>
                    <TableHead className="text-right" title="Ngày nghỉ theo đơn">Nghỉ</TableHead>
                    <TableHead className="text-right" title="Vắng không phép">Vắng</TableHead>
                    <TableHead className="text-right" title="Tăng ca đã duyệt">Tăng ca</TableHead>
                    <TableHead className="text-right" title="Ngày thiếu chấm công đã qua">Cần xử lý</TableHead>
                    {dates.map((date) => {
                      const off = weekday(date) === 0 || holidayOn(date)
                      return (
                        <TableHead key={date} title={holidayOn(date)?.name}
                          className={`w-8 min-w-8 px-0 text-center leading-tight ${off ? "bg-muted/60" : ""}`}>
                          <span className="block font-semibold">{Number(date.slice(8, 10))}</span>
                          <span className="block font-normal text-muted-foreground">{WEEKDAYS[weekday(date)]}</span>
                        </TableHead>
                      )
                    })}
                    <TableHead className="text-right">Thao tác</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data?.rows.map((row) => {
                    const days: Day[] = dates.map((date) => ({
                      date,
                      record: row.records.find((record) => record.workDate === date),
                      holiday: holidayOn(date),
                    }))
                    const totals = monthTotals(days)
                    const employee = { id: row.employeeId, employeeCode: row.employeeCode, fullName: row.employeeName }
                    return (
                      <TableRow key={row.employeeId} className="cursor-pointer" onClick={() => onOpen(employee)}>
                        <TableCell className="sticky left-0 z-10 bg-background">
                          <span className="block font-medium">{row.employeeName}</span>
                          <span className="block text-muted-foreground">{row.employeeCode}</span>
                        </TableCell>
                        <TableCell className="text-right tabular-nums whitespace-nowrap">
                          <span className="font-semibold">{decimal(totals.payableDays)}</span>
                          <span className="text-muted-foreground">/{totals.standardDays}</span>
                        </TableCell>
                        <TableCell className="text-right tabular-nums">{totals.lateCount || "—"}</TableCell>
                        <TableCell className="text-right tabular-nums">{totals.leaveDays || "—"}</TableCell>
                        <TableCell className={`text-right tabular-nums ${totals.absentDays ? "font-semibold text-destructive" : ""}`}>
                          {totals.absentDays || "—"}
                        </TableCell>
                        <TableCell className="text-right tabular-nums whitespace-nowrap">
                          {totals.overtimeMinutes ? hours(totals.overtimeMinutes) : "—"}
                        </TableCell>
                        <TableCell className={`text-right tabular-nums ${totals.missingDays ? "font-semibold text-destructive" : ""}`}>
                          {totals.missingDays || "—"}
                        </TableCell>
                        {days.map((day) => {
                          const tone = dayTone(day)
                          const off = weekday(day.date) === 0 || day.holiday
                          return (
                            <TableCell key={day.date} title={dayTitle(day)} className="p-0.5 text-center">
                              <span className={`relative flex h-7 items-center justify-center rounded border font-semibold ${
                                tone ? DAY_TONES[tone].cell : off ? "border-transparent bg-muted/60" : "border-transparent"}`}>
                                {dayCode(day)}
                                {day.record && day.record.overtimeMinutes > 0 && (
                                  <span className="absolute -top-1 right-0 text-[10px] text-primary">+</span>
                                )}
                              </span>
                            </TableCell>
                          )
                        })}
                        <RowActionsCell label={row.employeeName}>
                          <RowActionItem icon={Eye} onClick={() => onOpen(employee)}>Xem chi tiết công</RowActionItem>
                        </RowActionsCell>
                      </TableRow>
                    )
                  })}
                  {data?.rows.length === 0 && <EmptyRow colSpan={dates.length + 8}>Không có nhân viên nào khớp bộ lọc.</EmptyRow>}
                </TableBody>
              </Table>
            </div>
          )}

        <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
          {LEGEND.map(([code, label]) => (
            <li key={code}><span className="font-semibold text-foreground">{code}</span> {label}</li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}
