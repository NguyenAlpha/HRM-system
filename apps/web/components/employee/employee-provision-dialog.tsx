"use client"

import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Loader2 } from "lucide-react"

import { ActivationInvitation } from "@/components/account/activation-invitation"
import { ErrorAlert } from "@/components/common/list-controls"
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
import type { Portal } from "@/lib/auth/types"
import { provisionAccount, usernameSchema, type AccountProvisioningResponse } from "@/lib/account"

const provisionSchema = z.object({ username: usernameSchema })

type ProvisionValues = z.infer<typeof provisionSchema>

export function EmployeeProvisionDialog({ portal, employeeId, employeeName, onClose, onProvisioned, onSessionExpired }: {
  portal: Portal
  employeeId: number
  employeeName: string
  onClose: () => void
  onProvisioned: () => void
  onSessionExpired: () => void
}) {
  const [dialogError, setDialogError] = useState<string | null>(null)
  const [result, setResult] = useState<AccountProvisioningResponse | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ProvisionValues>({ resolver: zodResolver(provisionSchema), defaultValues: { username: "" } })

  async function onSubmit(values: ProvisionValues) {
    setDialogError(null)
    try {
      const response = await provisionAccount(portal, employeeId, values.username.trim())
      setResult(response)
      onProvisioned()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setDialogError(apiErrorMessage(caught))
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
              <ErrorAlert error={dialogError} />
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

            <ActivationInvitation className="mt-4" invitation={result.invitation} recipient="nhân sự"
              note="Nếu hết hạn, hãy gửi lại lời mời từ trang quản trị tài khoản." />

            <DialogFooter>
              <Button type="button" variant="outline" onClick={onClose}>Đóng</Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
