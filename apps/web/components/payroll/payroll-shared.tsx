"use client"

import { Check, Printer } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Separator } from "@/components/ui/separator"
import { money } from "@/components/workforce/workforce-shared"
import { PAYROLL_PERIOD_STATUS_LABELS, type PayrollPeriodStatus, type Payslip } from "@/lib/workforce"

export const PERIOD_TONE: Record<PayrollPeriodStatus, "default" | "secondary" | "outline" | "destructive"> = {
  DRAFT: "outline",
  CALCULATED: "secondary",
  APPROVED: "default",
  PAID: "default",
  LOCKED: "outline",
  CANCELLED: "destructive",
}

/** "09/2026" for a period or a payslip's period. */
export const periodLabel = (year: number, month: number) => `${String(month).padStart(2, "0")}/${year}`

export const payslipInsurance = (slip: Payslip) =>
  slip.employeeSocialInsurance + slip.employeeHealthInsurance + slip.employeeUnemploymentInsurance

/** Everything taken off gross pay: employee insurance, income tax and other deductions. */
export const payslipDeductions = (slip: Payslip) =>
  payslipInsurance(slip) + slip.personalIncomeTax + slip.otherDeductions

/** Worked hours for display; payslips store minutes. */
export const hours = (minutes: number) => `${Number((minutes / 60).toFixed(1)).toLocaleString("vi-VN")} giờ`

export function PeriodStatusBadge({ status }: { status: PayrollPeriodStatus }) {
  return <Badge variant={PERIOD_TONE[status]}>{PAYROLL_PERIOD_STATUS_LABELS[status]}</Badge>
}

const STEPS: PayrollPeriodStatus[] = ["DRAFT", "CALCULATED", "APPROVED", "PAID", "LOCKED"]

/** Where a period is in Draft → Calculated → Approved → Paid → Locked. */
export function PeriodSteps({ status }: { status: PayrollPeriodStatus }) {
  if (status === "CANCELLED") return <PeriodStatusBadge status={status} />
  const current = STEPS.indexOf(status)
  return (
    <ol className="flex flex-wrap items-center gap-2 text-sm">
      {STEPS.map((step, index) => (
        <li key={step} className="flex items-center gap-2">
          <span className={`flex size-6 items-center justify-center rounded-full border text-xs ${
            index < current ? "border-primary bg-primary text-primary-foreground"
              : index === current ? "border-primary font-semibold text-primary"
                : "text-muted-foreground"}`}>
            {index < current ? <Check className="size-3.5" /> : index + 1}
          </span>
          <span className={index === current ? "font-medium" : "text-muted-foreground"}>
            {PAYROLL_PERIOD_STATUS_LABELS[step]}
          </span>
          {index < STEPS.length - 1 && <span className="h-px w-6 bg-border" />}
        </li>
      ))}
    </ol>
  )
}

function Line({ label, amount, sign = "", strong = false }: { label: string; amount: number; sign?: string; strong?: boolean }) {
  return (
    <div className={`flex items-baseline justify-between gap-4 py-1.5 text-sm ${strong ? "font-medium" : ""}`}>
      <span className={strong ? "" : "text-muted-foreground"}>{label}</span>
      <span className="tabular-nums">{amount === 0 ? "" : sign}{money(amount)}</span>
    </div>
  )
}

/** One employee's payslip laid out like a printed slip: who, earnings, deductions, net pay; "In" prints just the slip. */
export function PayslipDialog({ payslip, onClose }: { payslip: Payslip; onClose: () => void }) {
  const earnings = payslip.items.filter((item) => item.componentType !== "DEDUCTION")
  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="print-area max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Phiếu lương tháng {periodLabel(payslip.periodYear, payslip.periodMonth)}</DialogTitle>
          <DialogDescription>Số liệu chốt tại thời điểm tính lương; hồ sơ thay đổi sau đó không làm đổi phiếu.</DialogDescription>
        </DialogHeader>
        <Button type="button" variant="outline" size="sm" className="justify-self-start print:hidden" onClick={() => window.print()}>
          <Printer /> In phiếu lương
        </Button>

        <dl className="grid gap-x-6 gap-y-2 rounded-xl border p-4 text-sm sm:grid-cols-2">
          <div><dt className="text-xs text-muted-foreground">Nhân viên</dt><dd className="font-medium">{payslip.employeeNameSnapshot}</dd></div>
          <div><dt className="text-xs text-muted-foreground">Mã nhân viên</dt><dd><code>{payslip.employeeCodeSnapshot}</code></dd></div>
          <div><dt className="text-xs text-muted-foreground">Chức danh</dt><dd>{payslip.positionSnapshot}</dd></div>
          <div><dt className="text-xs text-muted-foreground">Đơn vị · Địa điểm</dt><dd>{payslip.organizationUnitSnapshot} · {payslip.workLocationSnapshot}</dd></div>
          <div><dt className="text-xs text-muted-foreground">Lương hợp đồng</dt><dd>{money(payslip.contractualBasicSalary)}</dd></div>
          <div>
            <dt className="text-xs text-muted-foreground">Công được trả lương · Tăng ca</dt>
            <dd>{hours(payslip.payableWorkMinutes)} / {hours(payslip.scheduledWorkMinutes)} · {hours(payslip.approvedOvertimeMinutes)}</dd>
          </div>
        </dl>

        <div className="grid gap-4 sm:grid-cols-2">
          <section className="rounded-xl border p-4">
            <h3 className="mb-1 text-sm font-semibold">Thu nhập</h3>
            {earnings.map((item) => <Line key={item.id} label={item.description} amount={item.amount} />)}
            <Separator className="my-1.5" />
            <Line label="Tổng thu nhập" amount={payslip.grossPay} strong />
          </section>
          <section className="rounded-xl border p-4">
            <h3 className="mb-1 text-sm font-semibold">Khấu trừ</h3>
            <Line label="BHXH (8%)" amount={payslip.employeeSocialInsurance} sign="−" />
            <Line label="BHYT (1,5%)" amount={payslip.employeeHealthInsurance} sign="−" />
            <Line label="BHTN (1%)" amount={payslip.employeeUnemploymentInsurance} sign="−" />
            <Line label="Thuế TNCN" amount={payslip.personalIncomeTax} sign="−" />
            {payslip.otherDeductions > 0 && <Line label="Khấu trừ khác" amount={payslip.otherDeductions} sign="−" />}
            <Separator className="my-1.5" />
            <Line label="Tổng khấu trừ" amount={payslipDeductions(payslip)} sign="−" strong />
          </section>
        </div>

        <div className="flex items-center justify-between rounded-xl bg-primary/5 px-4 py-3">
          <span className="font-semibold">Thực nhận</span>
          <span className="text-2xl font-semibold tabular-nums text-primary">{money(payslip.netPay)}</span>
        </div>

        <dl className="grid gap-1 text-xs text-muted-foreground sm:grid-cols-2">
          <div>Căn cứ đóng BHXH/BHYT: <span className="text-foreground">{money(payslip.insuranceSalaryBase)}</span></div>
          <div>Căn cứ đóng BHTN: <span className="text-foreground">{money(payslip.unemploymentInsuranceBase)}</span></div>
          <div>Thu nhập tính thuế sau giảm trừ: <span className="text-foreground">{money(payslip.taxableIncome)}</span></div>
          <div>Phần miễn thuế (tăng ca, phụ cấp): <span className="text-foreground">{money(payslip.taxExemptOvertimePay + payslip.taxExemptOtherEarnings)}</span></div>
        </dl>
      </DialogContent>
    </Dialog>
  )
}
