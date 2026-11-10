"use client"

import { useMemo, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Calculator, CalendarCheck, Eye, FileSpreadsheet, Printer } from "lucide-react"

import { ActionsMenu, RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import {
  CompanyPrintHeader,
  MY_PAYSLIPS_KEY,
  PayslipDialog,
  hours,
  payslipDeductions,
  payslipInsurance,
  periodLabel,
} from "@/components/payroll/payroll-shared"
import { PayslipAttendanceDialog } from "@/components/payroll/payslip-attendance-dialog"
import { PayslipCalculationDialog } from "@/components/payroll/payslip-calculation-dialog"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { YearPicker } from "@/components/report/report-shared"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { EmptyRow, SectionError, money, useWorkforceAction, workforceApi } from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import { authorizedDownload } from "@/lib/auth/client"
import type { Page } from "@/lib/pagination"
import type { Payslip } from "@/lib/workforce"

/** The signed-in employee's own payslips, released once a period is approved. */
export function MyPayslipsPage() {
  const { session } = usePortalSession()
  const self = session.account.employee
  const permissions = useMemo(
    () => new Set(session.account.permissions.map((permission) => permission.code)),
    [session],
  )
  const canRead = !!self && permissions.has("payroll.self.read")
  const canReadAttendance = !!self && permissions.has("attendance.self.read")
  const employeeId = self?.id
  const [pickedYear, setPickedYear] = useState<number | null>(null)
  const [selected, setSelected] = useState<Payslip | null>(null)
  const [explaining, setExplaining] = useState<Payslip | null>(null)
  const [attendanceOf, setAttendanceOf] = useState<Payslip | null>(null)
  const path = `payroll/employees/${employeeId}/payslips`

  // The newest payslip heads the page and picks the year shown first (null: none released yet).
  const latestQuery = useQuery({
    queryKey: [...MY_PAYSLIPS_KEY, employeeId, "latest"],
    queryFn: () => workforceApi<Page<Payslip>>(`${path}?size=1&sort=payrollPeriod.periodStart,desc`)
      .then((page) => page.content[0] ?? null),
    enabled: canRead,
    staleTime: 5 * 60_000,
  })
  const latest = latestQuery.data
  const year = pickedYear ?? latest?.periodYear
  // One year at a time, each cached under its own key: stepping back to a year already seen
  // shows it from memory without calling the API.
  const yearQuery = useQuery({
    queryKey: [...MY_PAYSLIPS_KEY, employeeId, year],
    queryFn: () => workforceApi<Page<Payslip>>(`${path}?year=${year}&size=100&sort=payrollPeriod.periodStart,desc`)
      .then((page) => page.content),
    enabled: canRead && year !== undefined,
    staleTime: 5 * 60_000,
  })
  const payslips = latest === null ? [] : yearQuery.data
  const failure = latestQuery.error ?? yearQuery.error
  const error = failure ? apiErrorMessage(failure) : null

  return (
    <>
      <PageHeader
        title="Phiếu lương của tôi"
        description="Phiếu lương hiện ra khi kỳ lương của tháng đã được duyệt."
      />

      <div className="mt-6 grid gap-6">
        {!canRead && <p role="alert">Tài khoản chưa gắn với hồ sơ nhân viên hoặc chưa có quyền xem phiếu lương.</p>}
        <SectionError error={error} />

        {canRead && latestQuery.isPending && <Skeleton className="h-40 w-full rounded-xl" />}

        {latest && (
          <Card className="border-primary/40">
            <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
              <div>
                <CardTitle>Lương tháng {periodLabel(latest.periodYear, latest.periodMonth)}</CardTitle>
                <CardDescription>
                  {latest.positionSnapshot} · {latest.organizationUnitSnapshot} · Công {hours(latest.payableWorkMinutes)}
                </CardDescription>
              </div>
              <div className="flex flex-wrap gap-2">
                <Button type="button" variant="outline" onClick={() => setSelected(latest)}><Eye /> Xem phiếu lương</Button>
                <Button type="button" variant="outline" onClick={() => setExplaining(latest)}><Calculator /> Xem cách tính lương</Button>
              </div>
            </CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-3">
              <div>
                <p className="text-xs text-muted-foreground">Tổng thu nhập</p>
                <p className="mt-1 text-xl font-semibold tabular-nums">{money(latest.grossPay)}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Khấu trừ (bảo hiểm, thuế, khác)</p>
                <p className="mt-1 text-xl font-semibold tabular-nums">−{money(payslipDeductions(latest))}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Thực nhận</p>
                <p className="mt-1 text-2xl font-semibold tabular-nums text-primary">{money(latest.netPay)}</p>
              </div>
            </CardContent>
          </Card>
        )}

        {latest && year !== undefined && (
          <YearlyPayrollCard employeeId={latest.employeeId} year={year} maxYear={latest.periodYear} payslips={payslips}
            onYearChange={setPickedYear} printable={!selected} />
        )}

        {canRead && latest !== undefined && (
          <Card>
            <CardHeader>
              <CardTitle>Lịch sử phiếu lương{latest ? ` năm ${year}` : ""}</CardTitle>
              <CardDescription>Chọn một tháng để xem chi tiết thu nhập và khấu trừ.</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="rounded-xl border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Tháng</TableHead>
                      <TableHead className="text-right">Công</TableHead>
                      <TableHead className="text-right">Tổng thu nhập</TableHead>
                      <TableHead className="text-right">Khấu trừ</TableHead>
                      <TableHead className="text-right">Thực nhận</TableHead>
                      <TableHead className="text-right">Thao tác</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {payslips?.map((slip) => (
                      <TableRow key={slip.id} className="cursor-pointer" onClick={() => setSelected(slip)}>
                        <TableCell className="font-medium">{periodLabel(slip.periodYear, slip.periodMonth)}</TableCell>
                        <TableCell className="text-right">{hours(slip.payableWorkMinutes)}</TableCell>
                        <TableCell className="text-right tabular-nums">{money(slip.grossPay)}</TableCell>
                        <TableCell className="text-right tabular-nums">−{money(payslipDeductions(slip))}</TableCell>
                        <TableCell className="text-right font-semibold tabular-nums">{money(slip.netPay)}</TableCell>
                        <RowActionsCell label={periodLabel(slip.periodYear, slip.periodMonth)}>
                          <RowActionItem icon={Eye} onClick={() => setSelected(slip)}>Xem phiếu lương</RowActionItem>
                          <RowActionItem icon={Calculator} onClick={() => setExplaining(slip)}>Xem cách tính lương</RowActionItem>
                          {canReadAttendance && (
                            <RowActionItem icon={CalendarCheck} onClick={() => setAttendanceOf(slip)}>Xem chấm công</RowActionItem>
                          )}
                        </RowActionsCell>
                      </TableRow>
                    ))}
                    {payslips?.length === 0 && (
                      <EmptyRow colSpan={6}>
                        {latest ? `Không có phiếu lương năm ${year}.` : "Chưa có phiếu lương nào được phát hành."}
                      </EmptyRow>
                    )}
                  </TableBody>
                </Table>
              </div>
            </CardContent>
          </Card>
        )}
      </div>

      {selected && <PayslipDialog payslip={selected} onClose={() => setSelected(null)} />}
      {explaining && <PayslipCalculationDialog payslip={explaining} onClose={() => setExplaining(null)} />}
      {attendanceOf && (
        <PayslipAttendanceDialog payslip={attendanceOf} permissions={permissions} onClose={() => setAttendanceOf(null)} />
      )}
    </>
  )
}

const YEAR_COLUMNS: [string, (slip: Payslip) => number][] = [
  ["Lương theo công", (slip) => slip.basicSalaryPay],
  ["Phụ cấp", (slip) => slip.allowancePay],
  ["Tăng ca", (slip) => slip.overtimePay],
  ["Thu nhập khác", (slip) => slip.otherEarningsPay],
  ["Tổng thu nhập", (slip) => slip.grossPay],
  ["Bảo hiểm", payslipInsurance],
  ["Thuế TNCN", (slip) => slip.personalIncomeTax],
  ["Khấu trừ khác", (slip) => slip.otherDeductions],
  ["Thực nhận", (slip) => slip.netPay],
]

/**
 * A year of released payslips as one sheet: a line per month, the year's total under it, printable or
 * downloadable as XLSX. `payslips` are that year's, undefined while they load.
 * `printable` is off while a payslip is open so printing then gives just that slip.
 */
function YearlyPayrollCard({ employeeId, year, maxYear, payslips, onYearChange, printable }: {
  employeeId: number
  year: number
  maxYear: number
  payslips: Payslip[] | undefined
  onYearChange: (year: number) => void
  printable: boolean
}) {
  const slips = payslips ?? []
  const months = Array.from({ length: 12 }, (_, index) => ({
    month: index + 1,
    slip: slips.find((slip) => slip.periodMonth === index + 1),
  }))
  const first = slips[0]
  const { busy, run } = useWorkforceAction(() => undefined)

  return (
    <Card className={printable ? "print-area" : undefined}>
      <div className="px-6"><CompanyPrintHeader /></div>
      <CardHeader>
        <CardTitle>Bảng lương năm {year}</CardTitle>
        <CardDescription>
          {first ? `${first.employeeNameSnapshot} · ${first.employeeCodeSnapshot} · ` : ""}
          Tổng hợp các phiếu lương đã phát hành trong năm.
        </CardDescription>
        <CardAction className="flex items-center gap-2 print:hidden">
          <YearPicker year={year} maxYear={maxYear} onChange={onYearChange} />
          <ActionsMenu label={`bảng lương năm ${year}`} trigger={<><Printer /> In bảng lương năm</>}
            disabled={slips.length === 0 || busy}>
            <RowActionItem icon={Printer} onClick={() => window.print()}>In</RowActionItem>
            <RowActionItem icon={FileSpreadsheet} onClick={() => void run("Đã tải bảng lương năm",
              () => authorizedDownload("hrm", `/workforce/payroll/employees/${employeeId}/payslips/export?year=${year}`))}>
              Xuất XLSX
            </RowActionItem>
          </ActionsMenu>
        </CardAction>
      </CardHeader>
      <CardContent>
        <div className="rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Tháng</TableHead>
                <TableHead className="text-right">Công</TableHead>
                {YEAR_COLUMNS.map(([label]) => <TableHead key={label} className="text-right">{label}</TableHead>)}
              </TableRow>
            </TableHeader>
            <TableBody>
              {months.map(({ month, slip }) => (
                <TableRow key={month}>
                  <TableCell className="font-medium">{periodLabel(year, month)}</TableCell>
                  <TableCell className="text-right">{slip ? hours(slip.payableWorkMinutes) : "—"}</TableCell>
                  {YEAR_COLUMNS.map(([label, pick]) => (
                    <TableCell key={label} className={`text-right tabular-nums ${label === "Thực nhận" ? "font-semibold" : ""}`}>
                      {slip ? money(pick(slip)) : "—"}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
              <TableRow className="bg-muted/40 font-semibold hover:bg-muted/40">
                <TableCell>Cả năm</TableCell>
                <TableCell className="text-right">{hours(slips.reduce((sum, slip) => sum + slip.payableWorkMinutes, 0))}</TableCell>
                {YEAR_COLUMNS.map(([label, pick]) => (
                  <TableCell key={label} className="text-right tabular-nums">
                    {money(slips.reduce((sum, slip) => sum + pick(slip), 0))}
                  </TableCell>
                ))}
              </TableRow>
            </TableBody>
          </Table>
        </div>
      </CardContent>
    </Card>
  )
}
