"use client"

import Link from "next/link"
import { useEffect, useMemo, useState } from "react"
import {
  ArrowLeft,
  Ban,
  Calculator,
  CheckCheck,
  Eye,
  FileSpreadsheet,
  Loader2,
  Lock,
  RefreshCw,
  Search,
  Undo2,
  Wallet,
} from "lucide-react"

import { ActionsMenu, RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import {
  PayslipDialog,
  PeriodSteps,
  hours,
  payslipInsurance,
  periodLabel,
} from "@/components/payroll/payroll-shared"
import { usePortalSession } from "@/components/portal/portal-shell"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
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
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { PayrollAdjustmentsPanel } from "@/components/workforce/payroll-adjustments-panel"
import {
  EmptyRow,
  SectionError,
  money,
  today,
  useWorkforceAction,
  workforceApi,
} from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import { authorizedDownload } from "@/lib/auth/client"
import type { PayrollPeriod, Payslip, WorkforceBody } from "@/lib/workforce"
import { formatDate, formatDateRange, formatDateTime } from "@/lib/format-date"

type Confirm = "approve" | "lock" | "cancel"

const sum = (payslips: Payslip[], pick: (slip: Payslip) => number) =>
  payslips.reduce((total, slip) => total + pick(slip), 0)

/** Sheet amounts without the currency sign, which the caption states once. */
const amount = (value: number) => Math.round(value).toLocaleString("vi-VN")

const extraEarnings = (slip: Payslip) => slip.overtimePay + slip.otherEarningsPay

/** One payroll run: its workflow, totals and the payroll sheet of every employee. */
export function PayrollPeriodPage({ periodId }: { periodId: string }) {
  const { session } = usePortalSession()
  const permissions = useMemo(
    () => new Set(session.account.permissions.map((permission) => permission.code)),
    [session],
  )
  const [period, setPeriod] = useState<PayrollPeriod | null>(null)
  const [payslips, setPayslips] = useState<Payslip[]>([])
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [loadedRevision, setLoadedRevision] = useState(-1)
  const [search, setSearch] = useState("")
  const [selected, setSelected] = useState<Payslip | null>(null)
  const [confirm, setConfirm] = useState<Confirm | null>(null)
  const [markingPaid, setMarkingPaid] = useState(false)
  const [calculating, setCalculating] = useState(false)
  const [reopening, setReopening] = useState(false)
  const reload = () => setRevision((value) => value + 1)
  const { busy, run } = useWorkforceAction(reload)
  const loading = loadedRevision !== revision
  const validId = /^[1-9]\d*$/.test(periodId)

  useEffect(() => {
    if (!validId) return
    let active = true
    Promise.all([
      workforceApi<PayrollPeriod>(`payroll/periods/${periodId}`),
      workforceApi<Payslip[]>(`payroll/periods/${periodId}/payslips`),
    ])
      .then(([loadedPeriod, loadedPayslips]) => {
        if (!active) return
        setPeriod(loadedPeriod)
        setPayslips(loadedPayslips)
        setError(null)
      })
      .catch((caught: unknown) => { if (active) setError(apiErrorMessage(caught)) })
      .finally(() => { if (active) setLoadedRevision(revision) })
    return () => { active = false }
  }, [periodId, validId, revision])

  const hasOtherDeductions = payslips.some((slip) => slip.otherDeductions > 0)
  const columns = hasOtherDeductions ? 10 : 9
  const term = search.trim().toLowerCase()
  const shown = term
    ? payslips.filter((slip) => slip.employeeCodeSnapshot.toLowerCase().includes(term)
      || slip.employeeNameSnapshot.toLowerCase().includes(term))
    : payslips

  if (!validId) return <p role="alert">Mã kỳ lương không hợp lệ.</p>

  const title = period ? `Bảng lương tháng ${periodLabel(period.year, period.month)}` : "Bảng lương"
  const status = period?.status
  const editable = status === "DRAFT" || status === "CALCULATED"
  const canCalculate = permissions.has("payroll.calculate") && editable
  const canApprove = permissions.has("payroll.approve") && status === "CALCULATED"
  const canMarkPaid = permissions.has("payroll.mark_paid") && status === "APPROVED"
  const canReopen = permissions.has("payroll.approve") && status === "APPROVED"
  const canLock = permissions.has("payroll.lock") && status === "PAID"
  const canCancel = permissions.has("payroll.calculate") && editable
  const canExport = status !== undefined && status !== "DRAFT" && status !== "CANCELLED"
  const action = (path: string, success: string, body?: unknown) =>
    run(success, () => workforceApi(`payroll/periods/${periodId}/${path}`, "POST", body))

  return (
    <>
      <Link href="/payroll" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" /> Kỳ lương
      </Link>

      <header className="mt-4 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">{title}</h1>
          {period && (
            <p className="mt-1.5 text-[var(--muted-ink)]">
              {formatDateRange(period.periodStart, period.periodEnd)} · Ngày trả lương {period.taxPaymentDate ? formatDate(period.taxPaymentDate) : "chưa đặt"}
            </p>
          )}
          {period?.reopenedAt && editable && (
            <p className="mt-1 text-sm text-muted-foreground">
              Đã hủy duyệt lúc {formatDateTime(period.reopenedAt)}: {period.reopenReason}
            </p>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {canCalculate && (
            <Button type="button" disabled={busy} onClick={() => setCalculating(true)}>
              <Calculator /> {status === "DRAFT" ? "Tính lương" : "Tính lại"}
            </Button>
          )}
          {canApprove && <Button type="button" disabled={busy} onClick={() => setConfirm("approve")}><CheckCheck /> Duyệt bảng lương</Button>}
          {canMarkPaid && <Button type="button" disabled={busy} onClick={() => setMarkingPaid(true)}><Wallet /> Xác nhận đã trả</Button>}
          {canLock && <Button type="button" disabled={busy} onClick={() => setConfirm("lock")}><Lock /> Khóa kỳ</Button>}
          {canExport && (
            <Button type="button" variant="outline" disabled={busy}
              onClick={() => void run("Đã tải bảng lương", () => authorizedDownload("hrm", `/workforce/payroll/periods/${periodId}/export`))}>
              <FileSpreadsheet /> Xuất Excel
            </Button>
          )}
          <ActionsMenu label={title} outline>
            <RowActionItem icon={RefreshCw} onClick={reload} disabled={loading}>Tải lại</RowActionItem>
            {canReopen && <RowActionItem icon={Undo2} disabled={busy} onClick={() => setReopening(true)}>Hủy duyệt</RowActionItem>}
            {canCancel && <RowActionItem icon={Ban} destructive disabled={busy} onClick={() => setConfirm("cancel")}>Hủy kỳ</RowActionItem>}
          </ActionsMenu>
        </div>
      </header>

      <div className="mt-8 grid gap-6">
        <SectionError error={error} />

        {period && (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border bg-card px-4 py-3">
            <PeriodSteps status={period.status} />
            {period.status === "CALCULATED" && (
              <p className="text-sm text-muted-foreground">Người tính lương và người duyệt phải là hai tài khoản khác nhau.</p>
            )}
          </div>
        )}

        {/* The five totals as one card of label/value pairs rather than five separate boxes. */}
        <Card>
          <CardContent>
            <dl className="grid gap-4 sm:grid-cols-3 lg:grid-cols-5">
          {[
            ["Số nhân viên", String(payslips.length)],
            ["Tổng thu nhập", money(sum(payslips, (slip) => slip.grossPay))],
            ["Bảo hiểm nhân viên đóng", money(sum(payslips, payslipInsurance))],
            ["Thuế TNCN", money(sum(payslips, (slip) => slip.personalIncomeTax))],
            ["Tổng thực nhận", money(sum(payslips, (slip) => slip.netPay))],
          ].map(([label, value]) => (
            <div key={label} className="min-w-0">
              <dt className="text-xs text-muted-foreground">{label}</dt>
              {loading && !period
                ? <Skeleton className="mt-2 h-6 w-24" />
                : <dd className="mt-1 truncate text-xl font-semibold tabular-nums">{value}</dd>}
            </div>
          ))}
            </dl>
          </CardContent>
        </Card>

        <Tabs defaultValue="sheet">
          <TabsList>
            <TabsTrigger value="sheet">Bảng lương</TabsTrigger>
            <TabsTrigger value="adjustments">Khoản cộng/trừ</TabsTrigger>
          </TabsList>

          <TabsContent value="sheet" className="mt-4 grid gap-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="relative w-full max-w-sm">
                <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input className="pl-8" placeholder="Tìm theo mã hoặc tên nhân viên" value={search}
                  onChange={(event) => setSearch(event.target.value)} />
              </div>
              <p className="text-xs text-muted-foreground">Đơn vị tính: đồng · Bấm một dòng để xem phiếu lương</p>
            </div>
            <div className="overflow-x-auto rounded-xl border bg-card">
              <Table>
                <TableHeader>
                  {/* Headings wrap so each money column is only as wide as its numbers. */}
                  <TableRow className="[&>th]:h-auto [&>th]:py-2 [&>th]:align-bottom [&>th]:whitespace-normal">
                    <TableHead>Nhân viên</TableHead>
                    <TableHead className="text-right">Công</TableHead>
                    <TableHead className="text-right">Lương theo công</TableHead>
                    <TableHead className="text-right">Phụ cấp</TableHead>
                    <TableHead className="text-right">Tăng ca, thưởng</TableHead>
                    <TableHead className="text-right">Tổng thu nhập</TableHead>
                    <TableHead className="text-right">Bảo hiểm</TableHead>
                    <TableHead className="text-right">Thuế TNCN</TableHead>
                    {hasOtherDeductions && <TableHead className="text-right">Khấu trừ khác</TableHead>}
                    <TableHead className="text-right">Thực nhận</TableHead>
                    <TableHead className="text-right">Thao tác</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {loading && payslips.length === 0 && Array.from({ length: 3 }).map((_, index) => (
                    <TableRow key={index}>
                      {Array.from({ length: columns }).map((__, cell) => (
                        <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
                      ))}
                    </TableRow>
                  ))}

                  {shown.map((slip) => (
                    <TableRow key={slip.id} className="cursor-pointer whitespace-nowrap" onClick={() => setSelected(slip)}>
                      <TableCell>
                        <span className="font-medium">{slip.employeeNameSnapshot}</span>
                        <span className="block text-xs text-muted-foreground">
                          <code>{slip.employeeCodeSnapshot}</code> · {slip.positionSnapshot}
                        </span>
                      </TableCell>
                      <TableCell className="text-right text-sm">
                        {hours(slip.payableWorkMinutes)}
                        <span className="block text-xs text-muted-foreground">/ {hours(slip.scheduledWorkMinutes)}</span>
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{amount(slip.basicSalaryPay)}</TableCell>
                      <TableCell className="text-right tabular-nums">{amount(slip.allowancePay)}</TableCell>
                      <TableCell className="text-right tabular-nums">{amount(extraEarnings(slip))}</TableCell>
                      <TableCell className="text-right font-medium tabular-nums">{amount(slip.grossPay)}</TableCell>
                      <TableCell className="text-right tabular-nums">{amount(payslipInsurance(slip))}</TableCell>
                      <TableCell className="text-right tabular-nums">{amount(slip.personalIncomeTax)}</TableCell>
                      {hasOtherDeductions && <TableCell className="text-right tabular-nums">{amount(slip.otherDeductions)}</TableCell>}
                      <TableCell className="text-right font-semibold tabular-nums text-primary">{amount(slip.netPay)}</TableCell>
                      <RowActionsCell label={slip.employeeCodeSnapshot}>
                        <RowActionItem icon={Eye} onClick={() => setSelected(slip)}>Xem phiếu lương</RowActionItem>
                      </RowActionsCell>
                    </TableRow>
                  ))}

                  {!loading && shown.length === 0 && (
                    <EmptyRow colSpan={columns}>
                      {payslips.length > 0 ? "Không có nhân viên khớp từ khóa."
                        : status === "DRAFT" ? "Kỳ chưa được tính. Bấm \"Tính lương\" để tạo bảng lương cho toàn bộ nhân viên."
                          : "Kỳ không có phiếu lương."}
                    </EmptyRow>
                  )}
                </TableBody>
                {shown.length > 1 && (
                  <TableFooter>
                    <TableRow className="whitespace-nowrap font-semibold">
                      <TableCell colSpan={2}>Tổng {shown.length} nhân viên</TableCell>
                      <TableCell className="text-right tabular-nums">{amount(sum(shown, (slip) => slip.basicSalaryPay))}</TableCell>
                      <TableCell className="text-right tabular-nums">{amount(sum(shown, (slip) => slip.allowancePay))}</TableCell>
                      <TableCell className="text-right tabular-nums">{amount(sum(shown, extraEarnings))}</TableCell>
                      <TableCell className="text-right tabular-nums">{amount(sum(shown, (slip) => slip.grossPay))}</TableCell>
                      <TableCell className="text-right tabular-nums">{amount(sum(shown, payslipInsurance))}</TableCell>
                      <TableCell className="text-right tabular-nums">{amount(sum(shown, (slip) => slip.personalIncomeTax))}</TableCell>
                      {hasOtherDeductions && <TableCell className="text-right tabular-nums">{amount(sum(shown, (slip) => slip.otherDeductions))}</TableCell>}
                      <TableCell className="text-right tabular-nums text-primary">{amount(sum(shown, (slip) => slip.netPay))}</TableCell>
                      <TableCell />
                    </TableRow>
                  </TableFooter>
                )}
              </Table>
            </div>
          </TabsContent>

          <TabsContent value="adjustments" className="mt-4">
            <Card>
              <CardContent>
                <p className="mb-3 text-sm text-muted-foreground">
                  Thưởng, phụ cấp khác và khấu trừ (tạm ứng, phạt) của kỳ. Thêm hoặc xóa khoản sẽ đưa kỳ đã tính về Nháp, cần tính lại.
                </p>
                <PayrollAdjustmentsPanel periodId={Number(periodId)} editable={editable && permissions.has("payroll.calculate")}
                  onChanged={reload} />
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>

      {selected && <PayslipDialog payslip={selected} onClose={() => setSelected(null)} />}

      {calculating && period && (
        <CalculateDialog period={period} busy={busy} onClose={() => setCalculating(false)}
          onSubmit={(taxPaymentDate) => action("calculate", "Đã tính lương",
            { taxPaymentDate } satisfies WorkforceBody["PayrollActionRequest"])} />
      )}

      {reopening && (
        <ReopenDialog title={title} busy={busy} onClose={() => setReopening(false)}
          onSubmit={(reason) => action("reopen", "Đã hủy duyệt bảng lương",
            { reason } satisfies WorkforceBody["ReopenPayrollPeriodRequest"])} />
      )}

      {markingPaid && period && (
        <MarkPaidDialog period={period} busy={busy} onClose={() => setMarkingPaid(false)}
          onSubmit={(paidOn) => action("mark-paid", "Đã ghi nhận trả lương",
            { paidOn } satisfies WorkforceBody["MarkPayrollPaidRequest"])} />
      )}

      <AlertDialog open={confirm !== null} onOpenChange={(open) => { if (!open && !busy) setConfirm(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {confirm === "approve" ? `Duyệt ${title.toLowerCase()}?`
                : confirm === "lock" ? `Khóa ${title.toLowerCase()}?` : `Hủy ${title.toLowerCase()}?`}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {confirm === "approve"
                ? `${payslips.length} nhân viên, tổng thực nhận ${money(sum(payslips, (slip) => slip.netPay))}. Sau khi duyệt, nhân viên xem được phiếu lương và dữ liệu đầu vào của kỳ không sửa được nữa.`
                : confirm === "lock"
                  ? "Kỳ đã khóa là bản lưu cuối cùng, không thể thay đổi."
                  : "Kỳ chuyển sang Đã hủy và không khôi phục được; sau đó có thể tạo kỳ mới cho cùng tháng."}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={busy}>Không</AlertDialogCancel>
            <AlertDialogAction
              className={confirm === "cancel" ? "bg-destructive text-white hover:bg-destructive/90" : undefined}
              disabled={busy}
              onClick={(event) => {
                event.preventDefault()
                const done = () => setConfirm(null)
                if (confirm === "approve") void action("approve", "Đã duyệt bảng lương").then(done)
                if (confirm === "lock") void action("lock", "Đã khóa kỳ lương").then(done)
                if (confirm === "cancel") void action("cancel", "Đã hủy kỳ lương").then(done)
              }}
            >
              {busy && <Loader2 className="animate-spin" />}
              {confirm === "approve" ? "Duyệt" : confirm === "lock" ? "Khóa kỳ" : "Hủy kỳ"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}

/** The payment date decides the tax rules and month, so it is confirmed, or changed, before each calculation. */
function CalculateDialog({ period, busy, onClose, onSubmit }: {
  period: PayrollPeriod
  busy: boolean
  onClose: () => void
  onSubmit: (taxPaymentDate: string) => Promise<boolean>
}) {
  const [taxPaymentDate, setTaxPaymentDate] = useState(period.taxPaymentDate ?? today())
  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="sm:max-w-sm">
        <form onSubmit={(event) => {
          event.preventDefault()
          void onSubmit(taxPaymentDate).then((ok) => { if (ok) onClose() })
        }}>
          <DialogHeader>
            <DialogTitle>{period.status === "DRAFT" ? "Tính lương" : "Tính lại lương"}</DialogTitle>
            <DialogDescription>
              Thuế TNCN tính theo tháng của ngày trả lương; khi trả thực tế, ngày trả phải nằm trong cùng tháng này.
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-1.5">
            <Label htmlFor="tax-payment-date">Ngày trả lương dự kiến</Label>
            <Input id="tax-payment-date" type="date" required min={period.periodStart} value={taxPaymentDate}
              onChange={(event) => setTaxPaymentDate(event.target.value)} />
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Hủy</Button>
            <Button type="submit" disabled={busy}>{busy && <Loader2 className="animate-spin" />} Tính lương</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/** Sends an approved, unpaid period back to draft, e.g. when salaries are paid in another month than planned. */
function ReopenDialog({ title, busy, onClose, onSubmit }: {
  title: string
  busy: boolean
  onClose: () => void
  onSubmit: (reason: string) => Promise<boolean>
}) {
  const [reason, setReason] = useState("")
  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={(event) => {
          event.preventDefault()
          void onSubmit(reason.trim()).then((ok) => { if (ok) onClose() })
        }}>
          <DialogHeader>
            <DialogTitle>Hủy duyệt {title.toLowerCase()}?</DialogTitle>
            <DialogDescription>
              Phiếu lương của kỳ bị xóa và nhân viên không còn thấy phiếu cho tới khi kỳ được tính và duyệt lại.
              Dùng khi chưa trả lương, ví dụ trả lương lệch sang tháng khác nên phải tính lại thuế.
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-1.5">
            <Label htmlFor="reopen-reason">Lý do</Label>
            <Textarea id="reopen-reason" rows={2} required maxLength={500} value={reason}
              onChange={(event) => setReason(event.target.value)} />
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Không</Button>
            <Button type="submit" variant="destructive" disabled={busy || !reason.trim()}>
              {busy && <Loader2 className="animate-spin" />} Hủy duyệt
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/** The actual payment date; it must stay in the planned payment month, since tax was withheld for that month. */
function MarkPaidDialog({ period, busy, onClose, onSubmit }: {
  period: PayrollPeriod
  busy: boolean
  onClose: () => void
  onSubmit: (paidOn: string) => Promise<boolean>
}) {
  const planned = period.taxPaymentDate ?? today()
  const [paidOn, setPaidOn] = useState(today().slice(0, 7) === planned.slice(0, 7) ? today() : planned)
  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="sm:max-w-sm">
        <form onSubmit={(event) => {
          event.preventDefault()
          void onSubmit(paidOn).then((ok) => { if (ok) onClose() })
        }}>
          <DialogHeader>
            <DialogTitle>Xác nhận đã trả lương</DialogTitle>
            <DialogDescription>
              Ngày trả thực tế phải trong tháng {planned.slice(5, 7)}/{planned.slice(0, 4)} như đã dự kiến, vì thuế TNCN
              đã tính theo tháng đó.
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-1.5">
            <Label htmlFor="paid-on">Ngày trả thực tế</Label>
            <Input id="paid-on" type="date" required max={today()} value={paidOn} onChange={(event) => setPaidOn(event.target.value)} />
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Hủy</Button>
            <Button type="submit" disabled={busy}>{busy && <Loader2 className="animate-spin" />} Xác nhận</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
