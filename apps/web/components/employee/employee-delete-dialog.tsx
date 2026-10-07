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
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import {
  employeeErrorMessage,
  isSessionExpired,
  softDeleteEmployee,
  type EmployeeDetail,
} from "@/lib/employee"

const deleteSchema = z.object({
  deletionReason: z.string().trim().min(1, "Bắt buộc"),
})

type DeleteValues = z.infer<typeof deleteSchema>

export function EmployeeDeleteDialog({ employee, onClose, onDeleted, onSessionExpired }: {
  employee: EmployeeDetail
  onClose: () => void
  onDeleted: () => void
  onSessionExpired: () => void
}) {
  const [dialogError, setDialogError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<DeleteValues>({
    resolver: zodResolver(deleteSchema),
    defaultValues: { deletionReason: "" },
  })

  async function onSubmit(values: DeleteValues) {
    setDialogError(null)
    try {
      await softDeleteEmployee(employee.id, values.deletionReason.trim())
      toast.success(`Đã xóa hồ sơ ${employee.fullName} (${employee.employeeCode})`)
      onClose()
      onDeleted()
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
            <DialogTitle>Xóa hồ sơ tạo nhầm</DialogTitle>
            <DialogDescription>
              Xóa mềm hồ sơ {employee.fullName} ({employee.employeeCode}).
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <p className="rounded-lg border border-dashed p-3 text-sm text-muted-foreground">
              Chỉ dùng cho hồ sơ nhập nhầm. Nếu nhân sự đã có tài khoản, lương, đơn nghỉ, chấm công,
              phiếu lương hoặc đã từng được điều chuyển thì hệ thống sẽ từ chối; trường hợp đó hãy
              dùng chức năng nghỉ việc.
            </p>

            <div className="grid gap-2">
              <Label htmlFor="deletionReason">Lý do xóa</Label>
              <Textarea id="deletionReason" rows={3} {...register("deletionReason")} />
              {errors.deletionReason && (
                <p className="text-sm text-destructive">{errors.deletionReason.message}</p>
              )}
            </div>
          </div>

          {dialogError && <RbacFeedback error={dialogError} />}

          <DialogFooter>
            <DialogClose render={<Button type="button" variant="outline" />}>Hủy</DialogClose>
            <Button type="submit" variant="destructive" disabled={isSubmitting}>
              {isSubmitting && <Loader2 className="animate-spin" />}
              Xóa hồ sơ
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
