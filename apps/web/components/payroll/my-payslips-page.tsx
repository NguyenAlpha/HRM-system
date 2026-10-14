"use client"

import { useEffect, useState } from "react"
import { Eye } from "lucide-react"

import { RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import {
  PayslipDialog,
  hours,
  payslipDeductions,
  periodLabel,
} from "@/components/payroll/payroll-shared"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { EmptyRow, SectionError, money, workforceApi } from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import type { Page } from "@/lib/pagination"
import type { Payslip } from "@/lib/workforce"

/** The signed-in employee's own payslips, released once a period is approved. */
export function MyPayslipsPage() {
  const { session } = usePortalSession()
  const self = session.account.employee
  const canRead = !!self && session.account.permissions.some((permission) => permission.code === "payroll.self.read")
  const [payslips, setPayslips] = useState<Payslip[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<Payslip | null>(null)

  useEffect(() => {
    if (!canRead || !self) return
    let active = true
    workforceApi<Page<Payslip>>(`payroll/employees/${self.id}/payslips?size=100&sort=payrollPeriod.periodStart,desc`)
      .then((page) => { if (active) setPayslips(page.content) })
      .catch((caught: unknown) => { if (active) { setError(apiErrorMessage(caught)); setPayslips([]) } })
    return () => { active = false }
  }, [canRead, self])

  const latest = payslips?.[0]

  return (
    <>
      <PageHeader
        title="Phiếu lương của tôi"
        description="Phiếu lương hiện ra khi kỳ lương của tháng đã được duyệt."
        backToOverview
      />

      <div className="mt-6 grid gap-6">
        {!canRead && <p role="alert">Tài khoản chưa gắn với hồ sơ nhân viên hoặc chưa có quyền xem phiếu lương.</p>}
        <SectionError error={error} />

        {canRead && payslips === null && <Skeleton className="h-40 w-full rounded-xl" />}

        {latest && (
          <Card className="border-primary/40">
            <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
              <div>
                <CardTitle>Lương tháng {periodLabel(latest.periodYear, latest.periodMonth)}</CardTitle>
                <CardDescription>
                  {latest.positionSnapshot} · {latest.organizationUnitSnapshot} · Công {hours(latest.payableWorkMinutes)}
                </CardDescription>
              </div>
              <Button type="button" variant="outline" onClick={() => setSelected(latest)}><Eye /> Xem phiếu lương</Button>
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

        {canRead && payslips !== null && (
          <Card>
            <CardHeader>
              <CardTitle>Lịch sử phiếu lương</CardTitle>
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
                    {payslips.map((slip) => (
                      <TableRow key={slip.id} className="cursor-pointer" onClick={() => setSelected(slip)}>
                        <TableCell className="font-medium">{periodLabel(slip.periodYear, slip.periodMonth)}</TableCell>
                        <TableCell className="text-right">{hours(slip.payableWorkMinutes)}</TableCell>
                        <TableCell className="text-right tabular-nums">{money(slip.grossPay)}</TableCell>
                        <TableCell className="text-right tabular-nums">−{money(payslipDeductions(slip))}</TableCell>
                        <TableCell className="text-right font-semibold tabular-nums">{money(slip.netPay)}</TableCell>
                        <RowActionsCell label={periodLabel(slip.periodYear, slip.periodMonth)}>
                          <RowActionItem icon={Eye} onClick={() => setSelected(slip)}>Xem phiếu lương</RowActionItem>
                        </RowActionsCell>
                      </TableRow>
                    ))}
                    {payslips.length === 0 && <EmptyRow colSpan={6}>Chưa có phiếu lương nào được phát hành.</EmptyRow>}
                  </TableBody>
                </Table>
              </div>
            </CardContent>
          </Card>
        )}
      </div>

      {selected && <PayslipDialog payslip={selected} onClose={() => setSelected(null)} />}
    </>
  )
}
