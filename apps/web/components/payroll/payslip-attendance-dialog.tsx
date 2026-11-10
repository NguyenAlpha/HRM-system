"use client"

import { periodLabel } from "@/components/payroll/payroll-shared"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { AttendanceSection } from "@/components/workforce/attendance-section"
import type { Payslip } from "@/lib/workforce"

/** The attendance month a payslip was paid on, opened from the payslip list. */
export function PayslipAttendanceDialog({ payslip, permissions, onClose }: {
  payslip: Payslip
  permissions: ReadonlySet<string>
  onClose: () => void
}) {
  const label = `${payslip.employeeCodeSnapshot} · ${payslip.employeeNameSnapshot}`
  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-6xl">
        <DialogHeader>
          <DialogTitle>Chấm công tháng {periodLabel(payslip.periodYear, payslip.periodMonth)}</DialogTitle>
          <DialogDescription>{label}. Bảng công mà phiếu lương tháng này dựa vào.</DialogDescription>
        </DialogHeader>
        <AttendanceSection employeeId={payslip.employeeId} permissions={permissions} employeeLabel={label}
          month={`${payslip.periodYear}-${String(payslip.periodMonth).padStart(2, "0")}`} />
      </DialogContent>
    </Dialog>
  )
}
