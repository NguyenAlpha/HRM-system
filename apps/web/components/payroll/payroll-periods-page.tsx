"use client"

import { useMemo, useState } from "react"
import { useRouter } from "next/navigation"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { Loader2, Plus, Table2 } from "lucide-react"

import { RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import { PAYROLL_PERIODS_KEY, PeriodStatusBadge, periodLabel } from "@/components/payroll/payroll-shared"
import { PageHeader } from "@/components/portal/page-header"
import { YearPicker } from "@/components/report/report-shared"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Button } from "@/components/ui/button"
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
import { companyScopePermissions } from "@/lib/auth/config"
import type { Page } from "@/lib/pagination"
import type { PayrollPeriod, WorkforceBody } from "@/lib/workforce"
import { formatDate, formatDateRange } from "@/lib/format-date"

/** Payroll runs, one per month and listed a year at a time; each opens its payroll sheet. */
export function PayrollPeriodsPage() {
  const router = useRouter()
  const { session } = usePortalSession()
  const permissions = useMemo(
    () => new Set(session.account.permissions.map((permission) => permission.code)),
    [session],
  )
  const canRead = companyScopePermissions(session.account.permissions).has("report.payroll.read")
  const canCalculate = permissions.has("payroll.calculate")
  const currentYear = Number(today().slice(0, 4))
  const [year, setYear] = useState(currentYear)
  const [creating, setCreating] = useState(false)
  const queryClient = useQueryClient()
  // Each year is cached under its own key: stepping back to a year already seen shows it from memory
  // without calling the API. The payroll sheet page marks the list stale whenever it changes a period.
  const { data: periods = [], error, isPending } = useQuery({
    queryKey: [...PAYROLL_PERIODS_KEY, year],
    // A year holds at most one live period per month, so one request covers it.
    queryFn: () => workforceApi<Page<PayrollPeriod>>(`payroll/periods?year=${year}&size=100&sort=periodStart,desc`)
      .then((page) => page.content),
    enabled: canRead,
    staleTime: 5 * 60_000,
  })
  const loading = canRead && isPending

  const open = (period: PayrollPeriod) => router.push(`/payroll/${period.id}`)

  return (
    <>
      <PageHeader
        title="Kỳ lương"
        description="Mỗi tháng một kỳ: kế toán tính lương, người duyệt kiểm tra bảng lương rồi duyệt, xác nhận đã trả và khóa kỳ."
      />

      <div className="mt-6">
        {!canRead && <p role="alert">Tài khoản chưa có quyền truy cập mục này.</p>}
        {canRead && (
          // No card around the list: the page header already names it.
          <div className="grid gap-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-wrap items-center gap-3">
                <YearPicker year={year} maxYear={currentYear} onChange={setYear} />
                <p className="text-sm text-muted-foreground">Chọn một kỳ để xem bảng lương của toàn bộ nhân viên.</p>
              </div>
              <div className="flex gap-2">
                {canCalculate && (
                  <Button type="button" size="sm" onClick={() => setCreating(true)}><Plus /> Tạo kỳ lương</Button>
                )}
              </div>
            </div>
              <SectionError error={error ? apiErrorMessage(error) : null} />
              <div className="rounded-xl border bg-card">
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
                          <span className="block text-xs text-muted-foreground">{formatDateRange(period.periodStart, period.periodEnd)}</span>
                        </TableCell>
                        <TableCell>{formatDate(period.taxPaymentDate)}</TableCell>
                        <TableCell className="text-right">{period.payslipCount || "—"}</TableCell>
                        <TableCell className="text-right">{period.payslipCount ? money(period.totalGrossPay) : "—"}</TableCell>
                        <TableCell className="text-right font-medium">{period.payslipCount ? money(period.totalNetPay) : "—"}</TableCell>
                        <TableCell><PeriodStatusBadge status={period.status} /></TableCell>
                        <RowActionsCell label={periodLabel(period.year, period.month)}>
                          <RowActionItem icon={Table2} onClick={() => open(period)}>Mở bảng lương</RowActionItem>
                        </RowActionsCell>
                      </TableRow>
                    ))}

                    {!loading && periods.length === 0 && <EmptyRow colSpan={7}>Chưa có kỳ lương năm {year}.</EmptyRow>}
                  </TableBody>
                </Table>
              </div>
          </div>
        )}
      </div>

      {creating && (
        <CreatePeriodDialog
          onClose={() => setCreating(false)}
          onCreated={(period) => {
            void queryClient.invalidateQueries({ queryKey: PAYROLL_PERIODS_KEY })
            router.push(`/payroll/${period.id}`)
          }}
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
              <Input id="new-period-payment" type="date" required min={month ? `${month}-01` : undefined} value={taxPaymentDate}
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
