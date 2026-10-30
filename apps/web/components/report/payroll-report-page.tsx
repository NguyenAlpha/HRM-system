"use client"

import Link from "next/link"
import { useEffect, useState } from "react"

import { ErrorAlert } from "@/components/common/list-controls"
import { PeriodStatusBadge } from "@/components/payroll/payroll-shared"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { ExportButton, MonthlyColumnChart, StatTile, YearPicker, downloadCsv, shortMoney } from "@/components/report/report-shared"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { EmptyRow, money, today, workforceApi } from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import type { components } from "@/lib/api-schema"
import { PAYROLL_PERIOD_STATUS_LABELS } from "@/lib/workforce"

type Schemas = components["schemas"]
type PayrollSummary = Schemas["PayrollSummaryReportResponse"]
type Amounts = Schemas["PayrollAmounts"]

/** Table amounts carry no currency sign; the caption states the unit once. */
const amount = (value: number) => Math.round(value).toLocaleString("vi-VN")
const insurance = (amounts: Amounts) => amounts.socialInsurance + amounts.healthInsurance + amounts.unemploymentInsurance

const COLUMNS: [string, (amounts: Amounts) => number][] = [
  ["Tổng thu nhập", (amounts) => amounts.grossPay],
  ["BHXH", (amounts) => amounts.socialInsurance],
  ["BHYT", (amounts) => amounts.healthInsurance],
  ["BHTN", (amounts) => amounts.unemploymentInsurance],
  ["Thuế TNCN", (amounts) => amounts.personalIncomeTax],
  ["Khấu trừ khác", (amounts) => amounts.otherDeductions],
  ["Thực nhận", (amounts) => amounts.netPay],
]

/** Payroll cost, employee insurance and personal income tax withheld, month by month and by unit. */
export function PayrollReportPage() {
  const { session } = usePortalSession()
  const canRead = session.account.permissions.some((permission) => permission.code === "report.payroll.read")
  const currentYear = Number(today().slice(0, 4))
  const [year, setYear] = useState(currentYear)
  const [report, setReport] = useState<{ year: number; data: PayrollSummary | null; error?: string } | null>(null)

  useEffect(() => {
    if (!canRead) return
    let active = true
    workforceApi<PayrollSummary>(`reports/payroll/summary?year=${year}`)
      .then((data) => { if (active) setReport({ year, data }) })
      .catch((caught: unknown) => { if (active) setReport({ year, data: null, error: apiErrorMessage(caught) }) })
    return () => { active = false }
  }, [canRead, year])

  const data = report?.year === year ? report.data : null
  const loading = canRead && report?.year !== year

  function exportMonths() {
    if (!data) return
    downloadCsv(`quy-luong-${year}.csv`, ["Tháng", "Trạng thái", "Số phiếu", ...COLUMNS.map(([label]) => label)],
      data.months.map((month) => [`${month.month}/${year}`, month.status ? PAYROLL_PERIOD_STATUS_LABELS[month.status] : "Chưa có kỳ",
        month.amounts.payslipCount, ...COLUMNS.map(([, pick]) => pick(month.amounts))]))
  }

  function exportUnits() {
    if (!data) return
    downloadCsv(`quy-luong-don-vi-${year}.csv`, ["Đơn vị", "Số phiếu", ...COLUMNS.map(([label]) => label)],
      data.units.map((unit) => [unit.organizationUnit, unit.amounts.payslipCount, ...COLUMNS.map(([, pick]) => pick(unit.amounts))]))
  }

  return (
    <>
      <PageHeader title="Quỹ lương, bảo hiểm & thuế" alignActions="end"
        description="Cộng từ phiếu lương của các kỳ đã duyệt. Bảo hiểm là phần người lao động đóng, khấu trừ vào lương."
        actions={canRead && <YearPicker year={year} maxYear={currentYear} onChange={setYear} />} />

      <div className="mt-6 grid gap-4">
        {!canRead && <p role="alert">Cần quyền xem báo cáo tiền lương để dùng trang này.</p>}
        <ErrorAlert error={report?.year === year ? report.error ?? null : null} />
        {loading && <Skeleton className="h-64 w-full rounded-xl" />}

        {data && (
          <>
            <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label={`Tổng hợp năm ${year}`}>
              <StatTile label="Quỹ lương (tổng thu nhập)" value={money(data.total.grossPay)}
                hint={`${data.total.payslipCount} phiếu lương`} />
              <StatTile label="Bảo hiểm người lao động đóng" value={money(insurance(data.total))} hint="BHXH + BHYT + BHTN" />
              <StatTile label="Thuế TNCN đã khấu trừ" value={money(data.total.personalIncomeTax)} />
              <StatTile label="Thực nhận" value={money(data.total.netPay)} />
            </section>

            <Card>
              <CardHeader>
                <CardTitle>Quỹ lương theo tháng</CardTitle>
                <CardDescription>Tổng thu nhập trên phiếu lương của từng tháng; tháng chưa duyệt lương để trống.</CardDescription>
              </CardHeader>
              <CardContent>
                <MonthlyColumnChart label={`Tổng thu nhập theo tháng năm ${year}`} format={shortMoney}
                  points={data.months.map((month) => ({
                    month: month.month,
                    value: month.amounts.payslipCount > 0 ? month.amounts.grossPay : null,
                  }))} />
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Chi tiết theo tháng</CardTitle>
                <CardDescription>Đơn vị tính: đồng. Bấm tháng để mở bảng lương của kỳ.</CardDescription>
                <CardAction><ExportButton onClick={exportMonths} /></CardAction>
              </CardHeader>
              <CardContent>
                <AmountsTable
                  firstColumn="Tháng"
                  rows={data.months.map((month) => ({
                    key: String(month.month),
                    label: month.periodId
                      ? <Link href={`/payroll/${month.periodId}`} className="font-medium hover:underline">{String(month.month).padStart(2, "0")}/{year}</Link>
                      : <span className="text-muted-foreground">{String(month.month).padStart(2, "0")}/{year}</span>,
                    hint: month.status ? <PeriodStatusBadge status={month.status} /> : <span className="text-xs text-muted-foreground">Chưa có kỳ</span>,
                    amounts: month.amounts,
                  }))}
                  total={data.total}
                  emptyText=""
                />
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Theo đơn vị</CardTitle>
                <CardDescription>Đơn vị ghi trên phiếu lương lúc tính; đơn vị có quỹ lương lớn nhất trước.</CardDescription>
                <CardAction><ExportButton onClick={exportUnits} disabled={data.units.length === 0} /></CardAction>
              </CardHeader>
              <CardContent>
                <AmountsTable
                  firstColumn="Đơn vị"
                  rows={data.units.map((unit) => ({
                    key: unit.organizationUnit,
                    label: <span className="font-medium">{unit.organizationUnit}</span>,
                    hint: <span className="text-xs text-muted-foreground">
                      {data.total.grossPay > 0 ? `${((unit.amounts.grossPay / data.total.grossPay) * 100).toLocaleString("vi-VN", { maximumFractionDigits: 1 })}% quỹ lương` : ""}
                    </span>,
                    amounts: unit.amounts,
                  }))}
                  total={data.units.length > 0 ? data.total : null}
                  emptyText={`Chưa có phiếu lương đã duyệt nào trong năm ${year}.`}
                />
              </CardContent>
            </Card>
          </>
        )}
      </div>
    </>
  )
}

function AmountsTable({ firstColumn, rows, total, emptyText }: {
  firstColumn: string
  rows: { key: string; label: React.ReactNode; hint: React.ReactNode; amounts: Amounts }[]
  total: Amounts | null
  emptyText: string
}) {
  return (
    <div className="overflow-x-auto rounded-xl border">
      <Table className="[&_th]:whitespace-normal">
        <TableHeader>
          <TableRow>
            <TableHead>{firstColumn}</TableHead>
            <TableHead className="text-right">Số phiếu</TableHead>
            {COLUMNS.map(([label]) => <TableHead key={label} className="text-right">{label}</TableHead>)}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.key}>
              <TableCell>
                {row.label}
                <span className="mt-0.5 block">{row.hint}</span>
              </TableCell>
              <TableCell className="text-right tabular-nums">{row.amounts.payslipCount || "—"}</TableCell>
              {COLUMNS.map(([label, pick]) => (
                <TableCell key={label} className={`text-right tabular-nums ${label === "Thực nhận" ? "font-semibold" : ""}`}>
                  {row.amounts.payslipCount ? amount(pick(row.amounts)) : "—"}
                </TableCell>
              ))}
            </TableRow>
          ))}
          {rows.length === 0 && <EmptyRow colSpan={COLUMNS.length + 2}>{emptyText}</EmptyRow>}
          {total && (
            <TableRow className="bg-muted/40 font-semibold hover:bg-muted/40">
              <TableCell>Cả năm</TableCell>
              <TableCell className="text-right tabular-nums">{total.payslipCount}</TableCell>
              {COLUMNS.map(([label, pick]) => <TableCell key={label} className="text-right tabular-nums">{amount(pick(total))}</TableCell>)}
            </TableRow>
          )}
        </TableBody>
      </Table>
    </div>
  )
}
