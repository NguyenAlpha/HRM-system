"use client"

import Link from "next/link"
import { useEffect, useState } from "react"

import { ErrorAlert } from "@/components/common/list-controls"
import { formatDate } from "@/lib/format-date"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { ExportButton, MonthlyColumnChart, StatTile, YearPicker, downloadCsv } from "@/components/report/report-shared"
import { Badge } from "@/components/ui/badge"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { EmptyRow, today, workforceApi } from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import type { components } from "@/lib/api-schema"
import { EMPLOYMENT_STATUS_LABELS } from "@/lib/employee"

type TurnoverReport = components["schemas"]["TurnoverReportResponse"]

const percent = (value: number) => `${value.toLocaleString("vi-VN", { maximumFractionDigits: 2 })}%`

export function TurnoverReportPage() {
  const { session } = usePortalSession()
  const canRead = session.account.permissions.some((permission) => permission.code === "report.hr.read")
  const currentYear = Number(today().slice(0, 4))
  const [year, setYear] = useState(currentYear)
  const [report, setReport] = useState<{ year: number; data: TurnoverReport | null; error?: string } | null>(null)

  useEffect(() => {
    if (!canRead) return
    let active = true
    workforceApi<TurnoverReport>(`reports/hr/turnover?year=${year}`)
      .then((data) => { if (active) setReport({ year, data }) })
      .catch((caught: unknown) => { if (active) setReport({ year, data: null, error: apiErrorMessage(caught) }) })
    return () => { active = false }
  }, [canRead, year])

  const data = report?.year === year ? report.data : null
  const loading = report?.year !== year

  function exportMonths() {
    if (!data) return
    downloadCsv(`bien-dong-nhan-su-${year}.csv`, ["Tháng", "Đầu kỳ", "Vào làm", "Nghỉ việc", "Cuối kỳ", "Tỷ lệ nghỉ việc (%)"],
      data.months.map((month) => [`${month.month}/${year}`, month.headcountStart, month.hires, month.terminations,
        month.headcountEnd, Number(month.turnoverRate)]))
  }

  function exportEvents() {
    if (!data) return
    downloadCsv(`vao-nghi-${year}.csv`, ["Ngày", "Loại", "Mã nhân viên", "Họ tên", "Trạng thái kết thúc", "Lý do"],
      data.events.map((event) => [event.date, event.type === "HIRE" ? "Vào làm" : "Nghỉ việc", event.employeeCode,
        event.fullName, event.endStatus ? EMPLOYMENT_STATUS_LABELS[event.endStatus] : "", event.reason ?? ""]))
  }

  return (
    <>
      <PageHeader title="Biến động nhân sự" alignActions="end"
        description="Quân số, số người vào làm, nghỉ việc và tỷ lệ nghỉ việc theo tháng trong phạm vi được giao."
        actions={canRead && <YearPicker year={year} maxYear={currentYear} onChange={setYear} />} />

      <div className="mt-6 grid gap-4">
        {!canRead && <p role="alert">Cần quyền xem báo cáo nhân sự để dùng trang này.</p>}
        <ErrorAlert error={report?.year === year ? report.error ?? null : null} />
        {canRead && loading && <Skeleton className="h-64 w-full rounded-xl" />}

        {data && (
          <>
            <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5" aria-label="Tổng hợp năm">
              <StatTile label="Quân số đầu năm" value={data.headcountStart.toLocaleString("vi-VN")} hint={`Cuối ngày 31/12/${year - 1}`} />
              <StatTile label="Vào làm" value={data.hires.toLocaleString("vi-VN")} />
              <StatTile label="Nghỉ việc" value={data.terminations.toLocaleString("vi-VN")} />
              <StatTile label="Quân số cuối kỳ" value={data.headcountEnd.toLocaleString("vi-VN")}
                hint={`Cuối tháng ${data.months.length}/${year}`} />
              <StatTile label="Tỷ lệ nghỉ việc" value={percent(Number(data.turnoverRate))} hint="Nghỉ việc / quân số bình quân" />
            </section>

            <div className="grid gap-4 lg:grid-cols-5">
              <Card className="lg:col-span-2">
                <CardHeader>
                  <CardTitle>Quân số cuối tháng</CardTitle>
                  <CardDescription>Số người đang làm việc vào ngày cuối mỗi tháng.</CardDescription>
                </CardHeader>
                <CardContent>
                  <MonthlyColumnChart label={`Quân số cuối tháng năm ${year}`}
                    points={Array.from({ length: 12 }, (_, index) => {
                      const month = data.months.find((item) => item.month === index + 1)
                      return { month: index + 1, value: month ? month.headcountEnd : null }
                    })}
                    format={(value) => Math.round(value).toLocaleString("vi-VN")} />
                </CardContent>
              </Card>

              <Card className="lg:col-span-3">
                <CardHeader>
                  <CardTitle>Theo tháng</CardTitle>
                  <CardDescription>Đầu kỳ + vào làm − nghỉ việc = cuối kỳ. Người nghỉ ngày nào thì không tính vào cuối ngày đó.</CardDescription>
                  <CardAction><ExportButton onClick={exportMonths} /></CardAction>
                </CardHeader>
                <CardContent>
                  <div className="rounded-xl border">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Tháng</TableHead>
                          <TableHead className="text-right">Đầu kỳ</TableHead>
                          <TableHead className="text-right">Vào làm</TableHead>
                          <TableHead className="text-right">Nghỉ việc</TableHead>
                          <TableHead className="text-right">Cuối kỳ</TableHead>
                          <TableHead className="text-right">Tỷ lệ nghỉ việc</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {data.months.map((month) => (
                          <TableRow key={month.month}>
                            <TableCell>{String(month.month).padStart(2, "0")}/{year}</TableCell>
                            <TableCell className="text-right tabular-nums">{month.headcountStart}</TableCell>
                            <TableCell className="text-right tabular-nums">{month.hires || "—"}</TableCell>
                            <TableCell className="text-right tabular-nums">{month.terminations || "—"}</TableCell>
                            <TableCell className="text-right tabular-nums">{month.headcountEnd}</TableCell>
                            <TableCell className="text-right tabular-nums">{percent(Number(month.turnoverRate))}</TableCell>
                          </TableRow>
                        ))}
                        <TableRow className="bg-muted/40 font-semibold hover:bg-muted/40">
                          <TableCell>Cả kỳ</TableCell>
                          <TableCell className="text-right tabular-nums">{data.headcountStart}</TableCell>
                          <TableCell className="text-right tabular-nums">{data.hires}</TableCell>
                          <TableCell className="text-right tabular-nums">{data.terminations}</TableCell>
                          <TableCell className="text-right tabular-nums">{data.headcountEnd}</TableCell>
                          <TableCell className="text-right tabular-nums">{percent(Number(data.turnoverRate))}</TableCell>
                        </TableRow>
                      </TableBody>
                    </Table>
                  </div>
                </CardContent>
              </Card>
            </div>

            <Card>
              <CardHeader>
                <CardTitle>Danh sách vào làm và nghỉ việc</CardTitle>
                <CardDescription>Theo thứ tự ngày trong năm {year}.</CardDescription>
                <CardAction><ExportButton onClick={exportEvents} disabled={data.events.length === 0} /></CardAction>
              </CardHeader>
              <CardContent>
                <div className="rounded-xl border">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Ngày</TableHead>
                        <TableHead>Biến động</TableHead>
                        <TableHead>Nhân viên</TableHead>
                        <TableHead>Trạng thái kết thúc</TableHead>
                        <TableHead>Lý do</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {data.events.map((event) => (
                        <TableRow key={`${event.type}-${event.employeeId}`}>
                          <TableCell className="tabular-nums">{formatDate(event.date)}</TableCell>
                          <TableCell>
                            <Badge variant={event.type === "HIRE" ? "secondary" : "outline"}>
                              {event.type === "HIRE" ? "Vào làm" : "Nghỉ việc"}
                            </Badge>
                          </TableCell>
                          <TableCell>
                            <Link href={`/employees/${event.employeeId}`} className="font-medium hover:underline">{event.fullName}</Link>
                            <span className="block text-xs text-muted-foreground">{event.employeeCode}</span>
                          </TableCell>
                          <TableCell>{event.endStatus ? EMPLOYMENT_STATUS_LABELS[event.endStatus] : "—"}</TableCell>
                          <TableCell className="max-w-72 truncate" title={event.reason ?? undefined}>{event.reason ?? "—"}</TableCell>
                        </TableRow>
                      ))}
                      {data.events.length === 0 && <EmptyRow colSpan={5}>Không có ai vào làm hay nghỉ việc trong năm {year}.</EmptyRow>}
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
