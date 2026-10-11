"use client"

import { useState } from "react"
import { Controller, useForm } from "react-hook-form"
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import {
  EMPLOYMENT_END_LABELS,
  EMPLOYMENT_END_STATUSES,
  endEmployeeEmployment,
  employeeErrorMessage,
  isSessionExpired,
  type EmployeeDetail,
} from "@/lib/employee"

const employmentEndSchema = z.object({
  endStatus: z.enum(EMPLOYMENT_END_STATUSES),
  terminationDate: z.string().min(1, "Bắt buộc"),
  terminationReason: z.string().trim().min(1, "Bắt buộc"),
})

type EmploymentEndValues = z.infer<typeof employmentEndSchema>

const END_STATUS_ITEMS = { ...EMPLOYMENT_END_LABELS }

export function EmployeeEmploymentEndDialog({ employee, onClose, onCompleted, onSessionExpired }: {
  employee: EmployeeDetail
  onClose: () => void
  onCompleted: () => void
  onSessionExpired: () => void
}) {
  const [dialogError, setDialogError] = useState<string | null>(null)

  const {
    control,
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<EmploymentEndValues>({
    resolver: zodResolver(employmentEndSchema),
    defaultValues: { endStatus: "RESIGNED", terminationDate: "", terminationReason: "" },
  })

  async function onSubmit(values: EmploymentEndValues) {
    setDialogError(null)
    try {
      await endEmployeeEmployment(
        employee.id,
        values.endStatus,
        values.terminationDate,
        values.terminationReason.trim(),
      )
      toast.success(`Đã ghi nhận ${EMPLOYMENT_END_LABELS[values.endStatus].toLowerCase()} cho ${employee.fullName}`, {
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
            <DialogTitle>Kết thúc làm việc</DialogTitle>
            <DialogDescription>
              Ghi nhận kết thúc làm việc cho {employee.fullName} ({employee.employeeCode}).
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <p className="rounded-lg border border-dashed p-3 text-sm text-muted-foreground">
              Thao tác này đóng phân công đang hiệu lực tại ngày kết thúc, chuyển tài khoản đăng nhập sang
              trạng thái vô hiệu hóa và thu hồi toàn bộ phiên đăng nhập của nhân sự. Dữ liệu chấm công và
              phiếu lương đã có được giữ nguyên.
            </p>

            <div className="grid gap-2">
              <Label htmlFor="endStatus">Hình thức</Label>
              <Controller
                control={control}
                name="endStatus"
                render={({ field }) => (
                  <Select value={field.value} items={END_STATUS_ITEMS} onValueChange={field.onChange}>
                    <SelectTrigger id="endStatus" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {EMPLOYMENT_END_STATUSES.map((status) => (
                        <SelectItem key={status} value={status}>{EMPLOYMENT_END_LABELS[status]}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="terminationDate">Ngày kết thúc</Label>
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
              Xác nhận
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
