"use client"

import Link from "next/link"
import { useEffect, useState } from "react"

import { ErrorAlert } from "@/components/common/list-controls"
import { ALL_OPTION, FilterSelect, ListToolbar, useDebouncedValue } from "@/components/common/list-filters"
import { formatDate } from "@/lib/format-date"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { ExportButton, MonthPicker, StatTile, downloadCsv } from "@/components/report/report-shared"
import { Badge } from "@/components/ui/badge"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { EmptyRow, today, workforceApi } from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import type { components } from "@/lib/api-schema"
import { EMPLOYMENT_STATUS_LABELS } from "@/lib/employee"

type MonthlyReport = components["schemas"]["MonthlyHrStatusReportResponse"]
type Row = MonthlyReport["rows"][number]

const STATUS_LABELS: Record<string, string> = { ACTIVE: "Đang làm", NEW_HIRE: "Vào làm", TERMINATED: "Nghỉ việc" }
const FILTERS: Record<string, { label: string; match: (row: Row) => boolean }> = {
  NEW_HIRE: { label: "Vào làm trong tháng", match: (row) => row.status === "NEW_HIRE" },
  TERMINATED: { label: "Nghỉ việc trong tháng", match: (row) => row.status === "TERMINATED" },
  ON_LEAVE: { label: "Có nghỉ phép", match: (row) => Number(row.leaveDays) > 0 },
  ABSENT: { label: "Có vắng không phép", match: (row) => row.absentDays > 0 },
}
const LEAVE_COLUMNS: [string, (row: Row) => number][] = [
  ["Phép năm", (row) => Number(row.annualLeaveDays)],
  ["Ốm", (row) => Number(row.sickLeaveDays)],
  ["Thai sản", (row) => Number(row.maternityLeaveDays)],
  ["Không lương", (row) => Number(row.unpaidLeaveDays)],
  ["Khác", (row) => Number(row.otherLeaveDays)],
]

const days = (value: number) => value ? value.toLocaleString("vi-VN", { maximumFractionDigits: 2 }) : "—"

/** One month of the workforce, person by person: who joined, who left, who was on leave or absent. */
export function MonthlyStatusReportPage() {
  const { session } = usePortalSession()
  const canRead = session.account.permissions.some((permission) => permission.code === "report.hr.read")
  const currentMonth = today().slice(0, 7)
  const [month, setMonth] = useState(currentMonth)
  const [keywordInput, setKeywordInput] = useState("")
  const keyword = useDebouncedValue(keywordInput.trim().toLowerCase())
  const [filter, setFilter] = useState(ALL_OPTION)
  const [report, setReport] = useState<{ month: string; data: MonthlyReport | null; error?: string } | null>(null)

  useEffect(() => {
    if (!canRead) return
    let active = true
    workforceApi<MonthlyReport>(`reports/hr/monthly-status?year=${month.slice(0, 4)}&month=${Number(month.slice(5, 7))}`)
      .then((data) => { if (active) setReport({ month, data }) })
      .catch((caught: unknown) => { if (active) setReport({ month, data: null, error: apiErrorMessage(caught) }) })
    return () => { active = false }
  }, [canRead, month])

  const data = report?.month === month ? report.data : null
  const loading = report?.month !== month
  const label = `${month.slice(5, 7)}/${month.slice(0, 4)}`
  const rows = data?.rows.filter((row) => (filter === ALL_OPTION || FILTERS[filter].match(row))
    && (!keyword || row.fullName.toLowerCase().includes(keyword) || row.employeeCode.toLowerCase().includes(keyword))) ?? []

  function exportRows() {
    if (!data) return
    downloadCsv(`tinh-hinh-nhan-su-${month}.csv`,
      ["Mã nhân viên", "Họ tên", "Đơn vị", "Chức danh", "Trạng thái", "Ngày vào làm", "Ngày nghỉ việc", "Trạng thái kết thúc",
        ...LEAVE_COLUMNS.map(([column]) => `${column} (ngày)`), "Tổng nghỉ (ngày)", "Vắng không phép (ngày)"],
      data.rows.map((row) => [row.employeeCode, row.fullName, row.organizationUnitName ?? "", row.positionTitle ?? "",
        STATUS_LABELS[row.status] ?? row.status, row.hireDate, row.terminationDate ?? "",
        row.endStatus ? EMPLOYMENT_STATUS_LABELS[row.endStatus] : "",
        ...LEAVE_COLUMNS.map(([, pick]) => pick(row)), Number(row.leaveDays), row.absentDays]))
  }

  return (
    <>
      <PageHeader title="Tình hình nhân sự theo tháng" alignActions="end"
        description="Từng người làm việc trong tháng: vào làm, nghỉ việc, số ngày nghỉ phép theo loại và vắng không phép, trong phạm vi được giao."
        actions={canRead && <MonthPicker month={month} onChange={(next) => setMonth(next > currentMonth ? currentMonth : next)} />} />

      <div className="mt-6 grid gap-4">
        {!canRead && <p role="alert">Cần quyền xem báo cáo nhân sự để dùng trang này.</p>}
        <ErrorAlert error={report?.month === month ? report.error ?? null : null} />
        {canRead && loading && <Skeleton className="h-64 w-full rounded-xl" />}

        {data && (
          <>
            <section className="grid gap-4 sm:grid-cols-3 lg:grid-cols-6" aria-label={`Tổng hợp tháng ${label}`}>
              <StatTile label="Quân số đầu tháng" value={data.headcountStart.toLocaleString("vi-VN")} />
              <StatTile label="Vào làm" value={data.hires.toLocaleString("vi-VN")} />
              <StatTile label="Nghỉ việc" value={data.terminations.toLocaleString("vi-VN")} />
              <StatTile label="Quân số cuối tháng" value={data.headcountEnd.toLocaleString("vi-VN")} />
              <StatTile label="Có nghỉ phép" value={`${data.employeesOnLeave} người`} hint={`${days(Number(data.leaveDays))} ngày`} />
              <StatTile label="Vắng không phép" value={`${data.absentDays} ngày`} />
            </section>

            <Card>
              <CardHeader>
                <CardTitle>Theo nhân viên — tháng {label}</CardTitle>
                <CardDescription>
                  Ngày nghỉ theo đơn đã duyệt, nửa ngày tính 0,5; tháng hiện tại gồm cả ngày nghỉ đã duyệt sắp tới.
                </CardDescription>
                <CardAction><ExportButton onClick={exportRows} disabled={data.rows.length === 0} /></CardAction>
              </CardHeader>
              <CardContent className="grid gap-4">
                <ListToolbar keyword={keywordInput} onKeywordChange={setKeywordInput} placeholder="Tìm theo họ tên hoặc mã nhân viên"
                  activeFilters={filter === ALL_OPTION ? 0 : 1} onReset={() => { setKeywordInput(""); setFilter(ALL_OPTION) }}>
                  <FilterSelect id="monthly-status-filter" label="Tình hình" value={filter} onChange={setFilter}
                    options={Object.entries(FILTERS).map(([value, { label: option }]) => ({ value, label: option }))} />
                </ListToolbar>

                <div className="rounded-xl border">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Nhân viên</TableHead>
                        <TableHead>Đơn vị / chức danh</TableHead>
                        <TableHead>Tình hình</TableHead>
                        {LEAVE_COLUMNS.map(([column]) => <TableHead key={column} className="text-right">{column}</TableHead>)}
                        <TableHead className="text-right">Vắng</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {rows.map((row) => (
                        <TableRow key={row.employeeId}>
                          <TableCell>
                            <Link href={`/employees/${row.employeeId}`} className="font-medium hover:underline">{row.fullName}</Link>
                            <span className="block text-xs text-muted-foreground">{row.employeeCode}</span>
                          </TableCell>
                          <TableCell>
                            {row.organizationUnitName ?? "—"}
                            {row.positionTitle && <span className="block text-xs text-muted-foreground">{row.positionTitle}</span>}
                          </TableCell>
                          <TableCell>
                            <Badge variant={row.status === "ACTIVE" ? "secondary" : "outline"}>{STATUS_LABELS[row.status] ?? row.status}</Badge>
                            {row.status === "NEW_HIRE" && (
                              <span className="block text-xs text-muted-foreground">từ {formatDate(row.hireDate)}</span>
                            )}
                            {row.status === "TERMINATED" && row.terminationDate && (
                              <span className="block text-xs text-muted-foreground">
                                {formatDate(row.terminationDate)}{row.endStatus && ` · ${EMPLOYMENT_STATUS_LABELS[row.endStatus]}`}
                              </span>
                            )}
                          </TableCell>
                          {LEAVE_COLUMNS.map(([column, pick]) => (
                            <TableCell key={column} className="text-right tabular-nums">{days(pick(row))}</TableCell>
                          ))}
                          <TableCell className="text-right tabular-nums">{row.absentDays || "—"}</TableCell>
                        </TableRow>
                      ))}
                      {rows.length === 0 && (
                        <EmptyRow colSpan={4 + LEAVE_COLUMNS.length}>Không có nhân viên phù hợp trong tháng {label}.</EmptyRow>
                      )}
                    </TableBody>
                  </Table>
                </div>
              </CardContent>
            </Card>
          </>
        )}
      </div>
    </>
  )
}
