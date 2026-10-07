"use client"

import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Loader2 } from "lucide-react"
import { toast } from "sonner"

import { RbacFeedback } from "@/components/admin/rbac-controls"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import {
  completeEmployeeResignation,
  employeeErrorMessage,
  isSessionExpired,
  type EmployeeDetail,
} from "@/lib/employee"

const resignationSchema = z.object({
  terminationDate: z.string().min(1, "Bắt buộc"),
  terminationReason: z.string().trim().min(1, "Bắt buộc"),
})

type ResignationValues = z.infer<typeof resignationSchema>

export function EmployeeResignationDialog({ employee, onClose, onCompleted, onSessionExpired }: {
  employee: EmployeeDetail
  onClose: () => void
  onCompleted: () => void
  onSessionExpired: () => void
}) {
  const [dialogError, setDialogError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ResignationValues>({
    resolver: zodResolver(resignationSchema),
    defaultValues: { terminationDate: "", terminationReason: "" },
  })

  async function onSubmit(values: ResignationValues) {
    setDialogError(null)
    try {
      await completeEmployeeResignation(
        employee.id,
        values.terminationDate,
        values.terminationReason.trim(),
      )
      toast.success(`Đã ghi nhận nghỉ việc cho ${employee.fullName}`, {
        description: "Phân công hiện tại đã đóng và tài khoản đăng nhập đã bị vô hiệu hóa.",
      })
      onClose()
      onCompleted()
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      setDialogError(employeeErrorMessage(caught))
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent>
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogHeader>
            <DialogTitle>Hoàn tất nghỉ việc</DialogTitle>
            <DialogDescription>
              Ghi nhận nghỉ việc cho {employee.fullName} ({employee.employeeCode}).
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <p className="rounded-lg border border-dashed p-3 text-sm text-muted-foreground">
              Thao tác này đóng phân công đang hiệu lực tại ngày nghỉ, chuyển tài khoản đăng nhập sang
              trạng thái vô hiệu hóa và thu hồi toàn bộ phiên đăng nhập của nhân sự. Dữ liệu chấm công và
              phiếu lương đã có được giữ nguyên.
            </p>

            <div className="grid gap-2">
              <Label htmlFor="terminationDate">Ngày nghỉ việc</Label>
              <Input
                id="terminationDate"
                type="date"
                min={employee.hireDate}
                {...register("terminationDate")}
              />
              {errors.terminationDate && (
                <p className="text-sm text-destructive">{errors.terminationDate.message}</p>
              )}
              <p className="text-xs text-muted-foreground">
                Không được trước ngày vào làm {employee.hireDate}.
              </p>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="terminationReason">Lý do</Label>
              <Textarea id="terminationReason" rows={3} {...register("terminationReason")} />
              {errors.terminationReason && (
                <p className="text-sm text-destructive">{errors.terminationReason.message}</p>
              )}
            </div>
          </div>

          {dialogError && <RbacFeedback error={dialogError} />}

          <DialogFooter>
            <DialogClose render={<Button type="button" variant="outline" />}>Hủy</DialogClose>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <Loader2 className="animate-spin" />}
              Ghi nhận nghỉ việc
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
