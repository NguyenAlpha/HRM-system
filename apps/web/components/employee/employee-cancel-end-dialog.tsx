"use client"

import { useState } from "react"
import { Loader2 } from "lucide-react"
import { toast } from "sonner"

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
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { cancelScheduledEmploymentEnd, type EmployeeSummary } from "@/lib/employee"
import { formatDate } from "@/lib/format-date"

/** Confirms dropping a scheduled employment end; the employee then keeps working with no last day. */
export function EmployeeCancelEndDialog({ employee, onClose, onCancelled, onSessionExpired }: {
  employee: Pick<EmployeeSummary, "id" | "fullName" | "employeeCode" | "terminationDate">
  onClose: () => void
  onCancelled: () => void
  onSessionExpired: () => void
}) {
  const [busy, setBusy] = useState(false)

  async function cancelEnd() {
    setBusy(true)
    try {
      await cancelScheduledEmploymentEnd(employee.id)
      toast.success(`Đã hủy lịch kết thúc làm việc của ${employee.fullName}`)
      onClose()
      onCancelled()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      toast.error("Không thể hủy lịch kết thúc", { description: apiErrorMessage(caught) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <AlertDialog open onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Hủy lịch kết thúc làm việc?</AlertDialogTitle>
          <AlertDialogDescription>
            {employee.fullName} ({employee.employeeCode}) sẽ tiếp tục làm việc, không còn ngày nghỉ việc
            {employee.terminationDate ? ` ${formatDate(employee.terminationDate)}` : ""}. Đơn xin nghỉ việc đã duyệt
            (nếu có) cũng bị hủy.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={busy}>Đóng</AlertDialogCancel>
          <AlertDialogAction disabled={busy} onClick={(event) => { event.preventDefault(); void cancelEnd() }}>
            {busy && <Loader2 className="animate-spin" />}
            Hủy lịch
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
