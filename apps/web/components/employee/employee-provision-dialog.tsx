"use client"

import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Check, Copy, KeyRound, Loader2 } from "lucide-react"
import { toast } from "sonner"

import { RbacFeedback } from "@/components/admin/rbac-controls"
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
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { provisionAccount, type AccountProvisioningResponse } from "@/lib/account"

const provisionSchema = z.object({
  username: z
    .string()
    .trim()
    .min(3, "Tối thiểu 3 ký tự")
    .max(50, "Tối đa 50 ký tự")
    .regex(/^[A-Za-z0-9._-]+$/, "Chỉ gồm chữ, số, dấu chấm, gạch dưới, gạch ngang"),
})

type ProvisionValues = z.infer<typeof provisionSchema>

export function EmployeeProvisionDialog({ employeeId, employeeName, onClose, onProvisioned, onSessionExpired }: {
  employeeId: number
  employeeName: string
  onClose: () => void
  onProvisioned: () => void
  onSessionExpired: () => void
}) {
  const [dialogError, setDialogError] = useState<string | null>(null)
  const [result, setResult] = useState<AccountProvisioningResponse | null>(null)
  const [copied, setCopied] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ProvisionValues>({ resolver: zodResolver(provisionSchema), defaultValues: { username: "" } })

  async function onSubmit(values: ProvisionValues) {
    setDialogError(null)
    try {
      const response = await provisionAccount(employeeId, values.username.trim())
      setResult(response)
      onProvisioned()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setDialogError(apiErrorMessage(caught))
    }
  }

  async function copyToken() {
    if (!result) return
    try {
      await navigator.clipboard.writeText(result.invitation.activationToken)
      setCopied(true)
      toast.success("Đã sao chép mã kích hoạt")
      setTimeout(() => setCopied(false), 1500)
    } catch {
      toast.error("Không thể sao chép, hãy tự copy thủ công")
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-md">
        {!result ? (
          <form onSubmit={handleSubmit(onSubmit)}>
            <DialogHeader>
              <DialogTitle>Cấp tài khoản cho {employeeName}</DialogTitle>
              <DialogDescription>
                Tạo tài khoản đăng nhập ở trạng thái chờ kích hoạt và gán sẵn role nền EMPLOYEE.
              </DialogDescription>
            </DialogHeader>

            <div className="mt-4 grid gap-4">
              <RbacFeedback error={dialogError} />
              <div className="grid gap-1.5">
                <Label htmlFor="prov-username">Tên đăng nhập</Label>
                <Input id="prov-username" placeholder="VD: an.nguyen" aria-invalid={!!errors.username} {...register("username")} />
                {errors.username && <p className="text-xs font-medium text-destructive">{errors.username.message}</p>}
                <p className="text-xs text-muted-foreground">Email đăng nhập sẽ lấy từ email công việc đã có trong hồ sơ.</p>
              </div>
            </div>

            <DialogFooter>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="animate-spin" />}
                Cấp tài khoản
              </Button>
            </DialogFooter>
          </form>
        ) : (
          <>
            <DialogHeader>
              <DialogTitle>Đã cấp tài khoản {result.account.username}</DialogTitle>
              <DialogDescription>
                Gửi mã kích hoạt bên dưới cho nhân sự để họ tự đặt mật khẩu. Mã chỉ hiển thị một lần duy nhất.
              </DialogDescription>
            </DialogHeader>

            <div className="mt-4 grid gap-3">
              <div className="flex items-start gap-2 rounded-lg bg-muted px-3 py-2.5 text-sm">
                <KeyRound className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                <div className="min-w-0 flex-1">
                  <p className="text-xs text-muted-foreground">Mã kích hoạt (dùng một lần)</p>
                  <code className="mt-0.5 block truncate font-semibold">{result.invitation.activationToken}</code>
                </div>
                <Button type="button" variant="ghost" size="icon-sm" onClick={copyToken} aria-label="Sao chép mã kích hoạt">
                  {copied ? <Check /> : <Copy />}
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">
                Hết hạn lúc {new Date(result.invitation.expiresAt).toLocaleString("vi-VN")}. Nếu hết hạn, hãy gửi lại lời mời từ trang quản trị tài khoản.
              </p>
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose}>Đóng</Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
