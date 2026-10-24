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
import {
  employeeErrorMessage,
  employeeMutation,
  isSessionExpired,
  type EmployeeDetail,
  type EmployeeSummary,
} from "@/lib/employee"
import type { Portal } from "@/lib/auth/types"

/** Confirms a probation employee as permanent. Opened from the employee list and from the employee detail page. */
export function EmployeeConfirmDialog({ portal, employee, onClose, onConfirmed, onSessionExpired }: {
  portal: Portal
  employee: Pick<EmployeeSummary, "id" | "fullName" | "employeeCode">
  onClose: () => void
  onConfirmed: () => void
  onSessionExpired: () => void
}) {
  const [confirming, setConfirming] = useState(false)

  async function confirm() {
    setConfirming(true)
    try {
      const saved = await employeeMutation<EmployeeDetail>(portal, `/${employee.id}/confirm`, "POST")
      toast.success(`Đã xác nhận ${saved.fullName} là nhân sự chính thức`)
      onClose()
      onConfirmed()
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      toast.error("Không thể xác nhận nhân sự", { description: employeeErrorMessage(caught) })
    } finally {
      setConfirming(false)
    }
  }

  return (
    <AlertDialog open onOpenChange={(open) => { if (!open && !confirming) onClose() }}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Xác nhận nhân sự chính thức?</AlertDialogTitle>
          <AlertDialogDescription>
            Chuyển {employee.fullName} ({employee.employeeCode}) từ “Thử việc” sang “Đang làm việc”.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={confirming}>Hủy</AlertDialogCancel>
          <AlertDialogAction
            disabled={confirming}
            onClick={(event) => { event.preventDefault(); void confirm() }}
          >
            {confirming && <Loader2 className="animate-spin" />}
            Xác nhận chính thức
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
