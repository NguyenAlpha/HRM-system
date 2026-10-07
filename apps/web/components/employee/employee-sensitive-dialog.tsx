"use client"

import { useEffect, useState } from "react"
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
  employeeErrorMessage,
  getEmployeeSensitiveData,
  isSessionExpired,
  updateEmployeeSensitiveData,
  type EmployeeDetail,
  type EmployeeSensitiveData,
} from "@/lib/employee"

const sensitiveSchema = z.object({
  nationalId: z.string().max(30, "Tối đa 30 ký tự"),
  personalEmail: z.string().max(100, "Tối đa 100 ký tự"),
  address: z.string(),
  taxCode: z.string().max(30, "Tối đa 30 ký tự"),
  bankName: z.string().max(150, "Tối đa 150 ký tự"),
  bankAccountNumber: z.string().max(50, "Tối đa 50 ký tự"),
  bankAccountHolder: z.string().max(200, "Tối đa 200 ký tự"),
}).refine((data) => !data.personalEmail || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.personalEmail), {
  message: "Email cá nhân không hợp lệ",
  path: ["personalEmail"],
})

type SensitiveValues = z.infer<typeof sensitiveSchema>

const EMPTY_VALUES: SensitiveValues = {
  nationalId: "",
  personalEmail: "",
  address: "",
  taxCode: "",
  bankName: "",
  bankAccountNumber: "",
  bankAccountHolder: "",
}

function toFormValues(data: EmployeeSensitiveData): SensitiveValues {
  return {
    nationalId: data.nationalId ?? "",
    personalEmail: data.personalEmail ?? "",
    address: data.address ?? "",
    taxCode: data.taxCode ?? "",
    bankName: data.bankName ?? "",
    bankAccountNumber: data.bankAccountNumber ?? "",
    bankAccountHolder: data.bankAccountHolder ?? "",
  }
}

function trimmedOrNull(value: string): string | null {
  const trimmed = value.trim()
  return trimmed === "" ? null : trimmed
}

export function EmployeeSensitiveDialog({ employee, onClose, onSaved, onSessionExpired }: {
  employee: EmployeeDetail
  onClose: () => void
  onSaved: (data: EmployeeSensitiveData) => void
  onSessionExpired: () => void
}) {
  const [dialogError, setDialogError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<SensitiveValues>({ resolver: zodResolver(sensitiveSchema), defaultValues: EMPTY_VALUES })

  useEffect(() => {
    let active = true
    getEmployeeSensitiveData(employee.id)
      .then((data) => {
        if (active) reset(toFormValues(data))
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isSessionExpired(caught)) onSessionExpired()
        setDialogError(employeeErrorMessage(caught))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [employee.id, onSessionExpired, reset])

  async function onSubmit(values: SensitiveValues) {
    setDialogError(null)
    try {
      const saved = await updateEmployeeSensitiveData(employee.id, {
        nationalId: trimmedOrNull(values.nationalId),
        personalEmail: trimmedOrNull(values.personalEmail),
        address: trimmedOrNull(values.address),
        taxCode: trimmedOrNull(values.taxCode),
        bankName: trimmedOrNull(values.bankName),
        bankAccountNumber: trimmedOrNull(values.bankAccountNumber),
        bankAccountHolder: trimmedOrNull(values.bankAccountHolder),
      })
      toast.success(`Đã cập nhật dữ liệu nhạy cảm của ${employee.fullName}`)
      onSaved(saved)
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      setDialogError(employeeErrorMessage(caught))
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-2xl">
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogHeader>
            <DialogTitle>Dữ liệu nhạy cảm</DialogTitle>
            <DialogDescription>
              Cập nhật cho {employee.fullName} ({employee.employeeCode}). Ô để trống sẽ xóa giá trị đang lưu.
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4 sm:grid-cols-2">
            <div className="grid gap-2">
              <Label htmlFor="nationalId">Số CCCD</Label>
              <Input id="nationalId" {...register("nationalId")} disabled={loading} />
              {errors.nationalId && <p className="text-sm text-destructive">{errors.nationalId.message}</p>}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="taxCode">Mã số thuế</Label>
              <Input id="taxCode" {...register("taxCode")} disabled={loading} />
              {errors.taxCode && <p className="text-sm text-destructive">{errors.taxCode.message}</p>}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="personalEmail">Email cá nhân</Label>
              <Input id="personalEmail" type="email" {...register("personalEmail")} disabled={loading} />
              {errors.personalEmail && <p className="text-sm text-destructive">{errors.personalEmail.message}</p>}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="bankName">Ngân hàng</Label>
              <Input id="bankName" {...register("bankName")} disabled={loading} />
              {errors.bankName && <p className="text-sm text-destructive">{errors.bankName.message}</p>}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="bankAccountNumber">Số tài khoản</Label>
              <Input id="bankAccountNumber" {...register("bankAccountNumber")} disabled={loading} />
              {errors.bankAccountNumber && (
                <p className="text-sm text-destructive">{errors.bankAccountNumber.message}</p>
              )}
            </div>

            <div className="grid gap-2">
              <Label htmlFor="bankAccountHolder">Chủ tài khoản</Label>
              <Input id="bankAccountHolder" {...register("bankAccountHolder")} disabled={loading} />
              {errors.bankAccountHolder && (
                <p className="text-sm text-destructive">{errors.bankAccountHolder.message}</p>
              )}
            </div>

            <div className="grid gap-2 sm:col-span-2">
              <Label htmlFor="address">Địa chỉ</Label>
              <Textarea id="address" rows={2} {...register("address")} disabled={loading} />
            </div>
          </div>

          {dialogError && <RbacFeedback error={dialogError} />}

          <DialogFooter>
            <DialogClose render={<Button type="button" variant="outline" />}>Hủy</DialogClose>
            <Button type="submit" disabled={isSubmitting || loading}>
              {(isSubmitting || loading) && <Loader2 className="animate-spin" />}
              Lưu thay đổi
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
