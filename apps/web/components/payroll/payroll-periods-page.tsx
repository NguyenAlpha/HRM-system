"use client"

import { useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { Loader2, Plus, RefreshCw, Table2 } from "lucide-react"

import { RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import { PeriodStatusBadge, periodLabel } from "@/components/payroll/payroll-shared"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
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
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import {
  EmptyRow,
  SectionError,
  money,
  today,
  useWorkforceAction,
  workforceApi,
} from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import type { Page } from "@/lib/pagination"
import type { PayrollPeriod, WorkforceBody } from "@/lib/workforce"

/** Payroll runs, one per month; each opens its payroll sheet. */
export function PayrollPeriodsPage() {
  const router = useRouter()
  const { session } = usePortalSession()
  const permissions = useMemo(
    () => new Set(session.account.permissions.map((permission) => permission.code)),
    [session],
  )
  const canRead = permissions.has("report.payroll.read")
  const canCalculate = permissions.has("payroll.calculate")
  const [periods, setPeriods] = useState<PayrollPeriod[]>([])
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [loadedRevision, setLoadedRevision] = useState(-1)
  const [creating, setCreating] = useState(false)
  const loading = canRead && loadedRevision !== revision
  const reload = () => setRevision((value) => value + 1)

  useEffect(() => {
    if (!canRead) return
    let active = true
    workforceApi<Page<PayrollPeriod>>("payroll/periods?size=100&sort=periodStart,desc")
      .then((page) => { if (active) { setPeriods(page.content); setError(null) } })
      .catch((caught: unknown) => { if (active) setError(apiErrorMessage(caught)) })
      .finally(() => { if (active) setLoadedRevision(revision) })
    return () => { active = false }
  }, [canRead, revision])

  const open = (period: PayrollPeriod) => router.push(`/payroll/${period.id}`)

  return (
    <>
      <PageHeader
        title="Kỳ lương"
        description="Mỗi tháng một kỳ: kế toán tính lương, người duyệt kiểm tra bảng lương rồi duyệt, xác nhận đã trả và khóa kỳ."
        backToOverview
      />

      <div className="mt-6">
        {!canRead && <p role="alert">Tài khoản chưa có quyền truy cập mục này.</p>}
        {canRead && (
          <Card>
            <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
              <div>
                <CardTitle>Danh sách kỳ lương</CardTitle>
                <CardDescription>Chọn một kỳ để xem bảng lương của toàn bộ nhân viên.</CardDescription>
              </div>
              <div className="flex gap-2">
                <Button type="button" variant="outline" size="sm" onClick={reload} disabled={loading}>
                  <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
                </Button>
                {canCalculate && (
                  <Button type="button" size="sm" onClick={() => setCreating(true)}><Plus /> Tạo kỳ lương</Button>
                )}
              </div>
            </CardHeader>
            <CardContent className="grid gap-4">
              <SectionError error={error} />
              <div className="rounded-xl border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Kỳ</TableHead>
                      <TableHead>Ngày trả lương</TableHead>
                      <TableHead className="text-right">Số nhân viên</TableHead>
                      <TableHead className="text-right">Tổng thu nhập</TableHead>
                      <TableHead className="text-right">Tổng thực nhận</TableHead>
                      <TableHead>Trạng thái</TableHead>
                      <TableHead className="text-right">Thao tác</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {loading && Array.from({ length: 3 }).map((_, index) => (
                      <TableRow key={index}>
                        {Array.from({ length: 7 }).map((__, cell) => (
                          <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
                        ))}
                      </TableRow>
                    ))}

                    {!loading && periods.map((period) => (
                      <TableRow key={period.id} className="cursor-pointer" onClick={() => open(period)}>
                        <TableCell>
                          <span className="font-medium">Tháng {periodLabel(period.year, period.month)}</span>
                          <span className="block text-xs text-muted-foreground">{period.periodStart} → {period.periodEnd}</span>
                        </TableCell>
                        <TableCell>{period.taxPaymentDate ?? "—"}</TableCell>
                        <TableCell className="text-right">{period.payslipCount || "—"}</TableCell>
                        <TableCell className="text-right">{period.payslipCount ? money(period.totalGrossPay) : "—"}</TableCell>
                        <TableCell className="text-right font-medium">{period.payslipCount ? money(period.totalNetPay) : "—"}</TableCell>
                        <TableCell><PeriodStatusBadge status={period.status} /></TableCell>
                        <RowActionsCell label={periodLabel(period.year, period.month)}>
                          <RowActionItem icon={Table2} onClick={() => open(period)}>Mở bảng lương</RowActionItem>
                        </RowActionsCell>
                      </TableRow>
                    ))}

                    {!loading && periods.length === 0 && <EmptyRow colSpan={7}>Chưa có kỳ lương.</EmptyRow>}
                  </TableBody>
                </Table>
              </div>
            </CardContent>
          </Card>
        )}
      </div>

      {creating && (
        <CreatePeriodDialog
          onClose={() => setCreating(false)}
          onCreated={(period) => router.push(`/payroll/${period.id}`)}
        />
      )}
    </>
  )
}

/** The month whose salaries are paid, and when they will be paid (it sets the tax rules used). */
function CreatePeriodDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (period: PayrollPeriod) => void }) {
  // Default to last month, paid on the 5th of this month.
  const [month, setMonth] = useState(() => {
    const now = new Date(`${today()}T00:00:00`)
    return new Date(now.getFullYear(), now.getMonth() - 1, 15).toISOString().slice(0, 7)
  })
  const [taxPaymentDate, setTaxPaymentDate] = useState(() => `${today().slice(0, 7)}-05`)
  const { busy, run } = useWorkforceAction(() => {})

  async function submit() {
    const result: { period?: PayrollPeriod } = {}
    const ok = await run("Đã tạo kỳ lương", async () => {
      result.period = await workforceApi<PayrollPeriod>("payroll/periods", "POST", {
        year: Number(month.slice(0, 4)), month: Number(month.slice(5, 7)), taxPaymentDate,
      } satisfies WorkforceBody["CreatePayrollPeriodRequest"])
    })
    if (ok && result.period) onCreated(result.period)
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={(event) => { event.preventDefault(); void submit() }}>
          <DialogHeader>
            <DialogTitle>Tạo kỳ lương</DialogTitle>
            <DialogDescription>
              Kỳ tính lương cho mọi nhân viên có làm việc trong tháng. Thuế TNCN tính theo quy định tại ngày trả lương.
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="new-period-month">Tháng lương</Label>
              <Input id="new-period-month" type="month" required value={month} onChange={(event) => setMonth(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="new-period-payment">Ngày dự kiến trả lương</Label>
              <Input id="new-period-payment" type="date" required value={taxPaymentDate}
                onChange={(event) => setTaxPaymentDate(event.target.value)} />
            </div>
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Hủy</Button>
            <Button type="submit" disabled={busy}>{busy && <Loader2 className="animate-spin" />} Tạo kỳ</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
