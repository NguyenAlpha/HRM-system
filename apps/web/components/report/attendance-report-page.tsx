"use client"

import Link from "next/link"
import { useEffect, useState } from "react"

import { ErrorAlert } from "@/components/common/list-controls"
import { ALL_OPTION, FilterSelect, ListToolbar, useDebouncedValue } from "@/components/common/list-filters"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { ExportButton, MonthPicker, StatTile, downloadCsv } from "@/components/report/report-shared"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { decimal, hours, monthTotals, type Day } from "@/components/workforce/attendance-section"
import { EmptyRow, monthRange, today, workforceApi } from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import { getOrganizationUnitOptions } from "@/lib/reference"
import type { CompanyHoliday, TimesheetRow } from "@/lib/workforce"

type Totals = ReturnType<typeof monthTotals>
type Line = { row: TimesheetRow; totals: Totals }

const ORDERS: Record<string, { label: string; compare: (a: Line, b: Line) => number }> = {
  code: { label: "Mã nhân viên", compare: (a, b) => a.row.employeeCode.localeCompare(b.row.employeeCode) },
  late: { label: "Đi muộn / về sớm nhiều nhất", compare: (a, b) => b.totals.lateMinutes - a.totals.lateMinutes },
  absent: { label: "Vắng không phép nhiều nhất", compare: (a, b) => b.totals.absentDays - a.totals.absentDays },
  overtime: { label: "Tăng ca nhiều nhất", compare: (a, b) => b.totals.overtimeMinutes - a.totals.overtimeMinutes },
  missing: { label: "Thiếu chấm công nhiều nhất", compare: (a, b) => b.totals.missingDays - a.totals.missingDays },
}

/** One month of attendance in figures: company totals and each employee's line, from the timesheet. */
export function AttendanceReportPage() {
  const { portal, session } = usePortalSession()
  const permissions = new Set(session.account.permissions.map((permission) => permission.code))
  const canRead = permissions.has("attendance.read")
  const [month, setMonth] = useState(today().slice(0, 7))
  const [keywordInput, setKeywordInput] = useState("")
  const keyword = useDebouncedValue(keywordInput.trim())
  const [unitId, setUnitId] = useState("")
  const [order, setOrder] = useState("code")
  const [unitOptions, setUnitOptions] = useState<{ value: string; label: string }[]>([])
  const [data, setData] = useState<{ key: string; rows: TimesheetRow[]; holidays: CompanyHoliday[]; error?: string } | null>(null)
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
  const key = params.toString()
  const canReadHolidays = permissions.has("holiday.read")

  useEffect(() => {
    if (!canRead) return
    let active = true
    Promise.all([
      workforceApi<TimesheetRow[]>(`attendance/timesheet?${key}`),
      canReadHolidays ? workforceApi<CompanyHoliday[]>(`company-holidays?from=${first}&to=${last}`) : Promise.resolve([]),
    ])
      .then(([rows, holidays]) => { if (active) setData({ key, rows, holidays }) })
      .catch((caught: unknown) => { if (active) setData({ key, rows: [], holidays: [], error: apiErrorMessage(caught) }) })
    return () => { active = false }
  }, [canRead, canReadHolidays, key, first, last])

  const loaded = data?.key === key ? data : null
  const dates = Array.from({ length: Number(last.slice(8, 10)) }, (_, index) => `${month}-${String(index + 1).padStart(2, "0")}`)
  const lines: Line[] | null = loaded && loaded.rows.map((row) => {
    const days: Day[] = dates.map((date) => ({
      date,
      record: row.records.find((record) => record.workDate === date),
      holiday: loaded.holidays.find((holiday) => holiday.holidayDate === date),
    }))
    return { row, totals: monthTotals(days) }
  }).sort(ORDERS[order].compare)
  const total = (pick: (totals: Totals) => number) => (lines ?? []).reduce((sum, line) => sum + pick(line.totals), 0)
  const payable = total((totals) => totals.payableDays)
  const standard = total((totals) => totals.standardDays)

  function exportLines() {
    if (!lines) return
    downloadCsv(`cham-cong-${month}.csv`,
      ["Mã nhân viên", "Họ tên", "Công tính lương", "Ngày công chuẩn", "Số lần muộn/sớm", "Phút muộn/sớm", "Nghỉ theo đơn (ngày)",
        "Vắng không phép (ngày)", "Tăng ca (phút)", "Thiếu chấm công (ngày)"],
      lines.map(({ row, totals }) => [row.employeeCode, row.employeeName, Number(totals.payableDays.toFixed(2)), totals.standardDays,
        totals.lateCount, totals.lateMinutes, totals.leaveDays, totals.absentDays, totals.overtimeMinutes, totals.missingDays]))
  }

  return (
    <>
      <PageHeader title="Tổng hợp chấm công" alignActions="end"
        description="Công tính lương, đi muộn, nghỉ, vắng và tăng ca của tháng, theo từng nhân viên trong phạm vi được giao."
        actions={canRead && <MonthPicker month={month} onChange={setMonth} />} />

      <div className="mt-6 grid gap-4">
        {!canRead && <p role="alert">Cần quyền xem chấm công để dùng trang này.</p>}
        <ErrorAlert error={loaded?.error ?? null} />

        {canRead && (
          <>
            <section className="grid gap-4 sm:grid-cols-3 lg:grid-cols-6" aria-label={`Tổng hợp tháng ${month}`}>
              <StatTile label="Nhân viên" value={String(lines?.length ?? "—")} />
              <StatTile label="Công tính lương" value={decimal(payable)}
                hint={`trên ${standard} ngày công chuẩn cả tháng`} />
              <StatTile label="Lần đi muộn / về sớm" value={String(total((totals) => totals.lateCount))}
                hint={hours(total((totals) => totals.lateMinutes))} />
              <StatTile label="Nghỉ theo đơn" value={`${total((totals) => totals.leaveDays)} ngày`} />
              <StatTile label="Vắng không phép" value={`${total((totals) => totals.absentDays)} ngày`} />
              <StatTile label="Tăng ca đã duyệt" value={hours(total((totals) => totals.overtimeMinutes))}
                hint={`${total((totals) => totals.missingDays)} ngày thiếu chấm công`} />
            </section>

            <Card>
              <CardHeader>
                <CardTitle>Theo nhân viên — tháng {month.slice(5, 7)}/{month.slice(0, 4)}</CardTitle>
                <CardDescription>Ngày chưa tới không tính là thiếu chấm công. Sửa công ở trang Chấm công.</CardDescription>
                <CardAction className="flex gap-2">
                  <Button variant="outline" size="sm" nativeButton={false} render={<Link href="/attendance" />}>Mở bảng công</Button>
                  <ExportButton onClick={exportLines} disabled={!lines || lines.length === 0} />
                </CardAction>
              </CardHeader>
              <CardContent className="grid gap-4">
                <ListToolbar keyword={keywordInput} onKeywordChange={setKeywordInput} placeholder="Tìm theo họ tên hoặc mã nhân viên"
                  activeFilters={(unitId ? 1 : 0) + (order !== "code" ? 1 : 0)}
                  onReset={() => { setKeywordInput(""); setUnitId(""); setOrder("code") }}>
                  {unitOptions.length > 0 && (
                    <FilterSelect id="attendance-report-unit" label="Đơn vị (phân công hiện tại)" value={unitId || ALL_OPTION}
                      onChange={(value) => setUnitId(value === ALL_OPTION ? "" : value)} options={unitOptions} />
                  )}
                  <FilterSelect id="attendance-report-order" label="Sắp xếp theo" value={order === "code" ? ALL_OPTION : order}
                    onChange={(value) => setOrder(value === ALL_OPTION ? "code" : value)} allLabel={ORDERS.code.label}
                    options={Object.entries(ORDERS).filter(([value]) => value !== "code").map(([value, { label }]) => ({ value, label }))} />
                </ListToolbar>

                {lines === null
                  ? <Skeleton className="h-40 w-full" />
                  : (
                    <div className="rounded-xl border">
                      <Table>
                        <TableHeader>
                          <TableRow>
                            <TableHead>Nhân viên</TableHead>
                            <TableHead className="text-right">Công / chuẩn</TableHead>
                            <TableHead className="text-right">Muộn / sớm</TableHead>
                            <TableHead className="text-right">Nghỉ theo đơn</TableHead>
                            <TableHead className="text-right">Vắng không phép</TableHead>
                            <TableHead className="text-right">Tăng ca</TableHead>
                            <TableHead className="text-right">Thiếu chấm công</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {lines.map(({ row, totals }) => (
                            <TableRow key={row.employeeId}>
                              <TableCell>
                                <Link href={`/employees/${row.employeeId}`} className="font-medium hover:underline">{row.employeeName}</Link>
                                <span className="block text-xs text-muted-foreground">{row.employeeCode}</span>
                              </TableCell>
                              <TableCell className="text-right tabular-nums">
                                <span className="font-semibold">{decimal(totals.payableDays)}</span>
                                <span className="text-muted-foreground"> / {totals.standardDays}</span>
                              </TableCell>
                              <TableCell className="text-right tabular-nums">
                                {totals.lateCount ? `${totals.lateCount} lần · ${hours(totals.lateMinutes)}` : "—"}
                              </TableCell>
                              <TableCell className="text-right tabular-nums">{totals.leaveDays ? `${totals.leaveDays} ngày` : "—"}</TableCell>
                              <TableCell className={`text-right tabular-nums ${totals.absentDays ? "font-semibold text-destructive" : ""}`}>
                                {totals.absentDays ? `${totals.absentDays} ngày` : "—"}
                              </TableCell>
                              <TableCell className="text-right tabular-nums">{totals.overtimeMinutes ? hours(totals.overtimeMinutes) : "—"}</TableCell>
                              <TableCell className={`text-right tabular-nums ${totals.missingDays ? "font-semibold text-destructive" : ""}`}>
                                {totals.missingDays ? `${totals.missingDays} ngày` : "—"}
                              </TableCell>
                            </TableRow>
                          ))}
                          {lines.length === 0 && <EmptyRow colSpan={7}>Không có nhân viên nào khớp bộ lọc.</EmptyRow>}
                        </TableBody>
                      </Table>
                    </div>
                  )}
              </CardContent>
            </Card>
          </>
        )}
      </div>
    </>
  )
}
