"use client"

import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Check, Copy, KeyRound, Link2, Loader2, ShieldCheck } from "lucide-react"
import { toast } from "sonner"

import { RbacFeedback } from "@/components/admin/rbac-controls"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Separator } from "@/components/ui/separator"
import { Textarea } from "@/components/ui/textarea"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { bootstrapCompanyOwner, type CompanyOwnerProvisioningResult } from "@/lib/company-owner"

const bootstrapSchema = z
  .object({
    fullName: z.string().trim().min(1, "Bắt buộc").max(200, "Tối đa 200 ký tự"),
    workEmail: z
      .string()
      .trim()
      .min(1, "Bắt buộc")
      .max(100, "Tối đa 100 ký tự")
      .regex(/^[^\s@]+@[^\s@]+\.[^\s@]+$/, "Email không hợp lệ"),
    phone: z.string().max(20, "Tối đa 20 ký tự").optional(),
    hireDate: z.string().min(1, "Bắt buộc"),
    username: z
      .string()
      .trim()
      .min(3, "Tối thiểu 3 ký tự")
      .max(50, "Tối đa 50 ký tự")
      .regex(/^[A-Za-z0-9._-]+$/, "Chỉ gồm chữ, số, dấu chấm, gạch dưới, gạch ngang"),
    effectiveFrom: z.string().min(1, "Bắt buộc"),
    ownershipReason: z.string().trim().min(1, "Bắt buộc").max(500, "Tối đa 500 ký tự"),
    directorAppointmentReason: z.string().trim().min(1, "Bắt buộc").max(500, "Tối đa 500 ký tự"),
  })
  .refine((data) => data.effectiveFrom >= data.hireDate, {
    message: "Ngày hiệu lực không được trước ngày vào làm",
    path: ["effectiveFrom"],
  })

type BootstrapValues = z.infer<typeof bootstrapSchema>

export function CompanyOwnerProvision({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<CompanyOwnerProvisioningResult | null>(null)
  const [copied, setCopied] = useState(false)
  const [copiedLink, setCopiedLink] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<BootstrapValues>({ resolver: zodResolver(bootstrapSchema) })

  async function onSubmit(values: BootstrapValues) {
    setError(null)
    try {
      const response = await bootstrapCompanyOwner({
        employee: {
          fullName: values.fullName.trim(),
          workEmail: values.workEmail.trim(),
          phone: values.phone?.trim() || null,
          hireDate: values.hireDate,
        },
        account: { username: values.username.trim() },
        effectiveFrom: values.effectiveFrom,
        ownershipReason: values.ownershipReason.trim(),
        directorAppointmentReason: values.directorAppointmentReason.trim(),
      })
      setResult(response)
      toast.success(`Đã khởi tạo Company Owner ${response.fullName}`)
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setError(apiErrorMessage(caught))
    }
  }

  async function copyToken() {
    if (!result) return
    try {
      await navigator.clipboard.writeText(result.accountProvisioning.invitation.activationToken)
      setCopied(true)
      toast.success("Đã sao chép mã kích hoạt")
      setTimeout(() => setCopied(false), 1500)
    } catch {
      toast.error("Không thể sao chép, hãy tự copy thủ công")
    }
  }

  async function copyLink() {
    if (!result) return
    try {
      await navigator.clipboard.writeText(`${window.location.origin}/activate/${result.accountProvisioning.invitation.activationToken}`)
      setCopiedLink(true)
      toast.success("Đã sao chép link kích hoạt")
      setTimeout(() => setCopiedLink(false), 1500)
    } catch {
      toast.error("Không thể sao chép, hãy tự copy thủ công")
    }
  }

  if (result) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Đã khởi tạo Company Owner</CardTitle>
          <CardDescription>
            {result.fullName} ({result.employeeCode}) · tài khoản {result.accountProvisioning.account.username}
          </CardDescription>
        </CardHeader>
        <CardContent className="grid min-w-0 gap-4">
          <div className="flex min-w-0 items-start gap-2 rounded-lg bg-muted px-3 py-2.5 text-sm">
            <Link2 className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
            <div className="min-w-0 flex-1">
              <p className="text-xs text-muted-foreground">Link kích hoạt — gửi trực tiếp cho Company Owner</p>
              <code className="mt-0.5 block truncate font-semibold">/activate/{result.accountProvisioning.invitation.activationToken}</code>
            </div>
            <Button type="button" variant="ghost" size="icon-sm" onClick={copyLink} aria-label="Sao chép link kích hoạt">
              {copiedLink ? <Check /> : <Copy />}
            </Button>
          </div>
          <div className="flex min-w-0 items-start gap-2 rounded-lg bg-muted px-3 py-2.5 text-sm">
            <KeyRound className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
            <div className="min-w-0 flex-1">
              <p className="text-xs text-muted-foreground">Mã kích hoạt (dùng một lần)</p>
              <code className="mt-0.5 block truncate font-semibold">{result.accountProvisioning.invitation.activationToken}</code>
            </div>
            <Button type="button" variant="ghost" size="icon-sm" onClick={copyToken} aria-label="Sao chép mã kích hoạt">
              {copied ? <Check /> : <Copy />}
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            Hết hạn lúc {new Date(result.accountProvisioning.invitation.expiresAt).toLocaleString("vi-VN")}. Hãy chuyển mã này cho Company Owner qua kênh an toàn để họ tự đặt mật khẩu.
          </p>

          <Separator />

          <div>
            <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Role đã cấp</p>
            <div className="mt-2 flex flex-wrap gap-2">
              <Badge><ShieldCheck /> {result.companyOwnerRoleAssignment.roleName}</Badge>
              <Badge variant="secondary"><ShieldCheck /> {result.directorRoleAssignment.roleName}</Badge>
            </div>
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Khởi tạo Company Owner đầu tiên</CardTitle>
        <CardDescription>
          Tạo hồ sơ, tài khoản và cấp đồng thời role Company Owner + Director cho người sở hữu doanh nghiệp. Chỉ thực hiện được một lần — nếu đã có Company Owner đang hoạt động, hệ thống sẽ từ chối.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form className="grid gap-4" onSubmit={handleSubmit(onSubmit)}>
          <RbacFeedback error={error} />

          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-1.5">
              <Label>Mã nhân viên</Label>
              <p className="flex min-h-9 items-center rounded-md border bg-muted/50 px-3 text-sm text-muted-foreground">
                Tự tạo mã GD sau khi lưu
              </p>
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="owner-name">Họ tên</Label>
              <Input id="owner-name" aria-invalid={!!errors.fullName} {...register("fullName")} />
              {errors.fullName && <p className="text-xs font-medium text-destructive">{errors.fullName.message}</p>}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="owner-email">Email công việc</Label>
              <Input id="owner-email" type="email" aria-invalid={!!errors.workEmail} {...register("workEmail")} />
              {errors.workEmail && <p className="text-xs font-medium text-destructive">{errors.workEmail.message}</p>}
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="owner-phone">Điện thoại</Label>
              <Input id="owner-phone" aria-invalid={!!errors.phone} {...register("phone")} />
              {errors.phone && <p className="text-xs font-medium text-destructive">{errors.phone.message}</p>}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="owner-hire-date">Ngày vào làm</Label>
              <Input id="owner-hire-date" type="date" aria-invalid={!!errors.hireDate} {...register("hireDate")} />
              {errors.hireDate && <p className="text-xs font-medium text-destructive">{errors.hireDate.message}</p>}
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="owner-effective-from">Hiệu lực role từ</Label>
              <Input id="owner-effective-from" type="date" aria-invalid={!!errors.effectiveFrom} {...register("effectiveFrom")} />
              {errors.effectiveFrom && <p className="text-xs font-medium text-destructive">{errors.effectiveFrom.message}</p>}
            </div>
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="owner-username">Tên đăng nhập</Label>
            <Input id="owner-username" placeholder="VD: company.owner" aria-invalid={!!errors.username} {...register("username")} />
            {errors.username && <p className="text-xs font-medium text-destructive">{errors.username.message}</p>}
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="owner-reason">Lý do cấp quyền sở hữu</Label>
            <Textarea id="owner-reason" rows={2} aria-invalid={!!errors.ownershipReason} {...register("ownershipReason")} />
            {errors.ownershipReason && <p className="text-xs font-medium text-destructive">{errors.ownershipReason.message}</p>}
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="owner-director-reason">Lý do bổ nhiệm Director</Label>
            <Textarea id="owner-director-reason" rows={2} aria-invalid={!!errors.directorAppointmentReason} {...register("directorAppointmentReason")} />
            {errors.directorAppointmentReason && <p className="text-xs font-medium text-destructive">{errors.directorAppointmentReason.message}</p>}
          </div>

          <Button type="submit" className="justify-self-start" disabled={isSubmitting}>
            {isSubmitting && <Loader2 className="animate-spin" />}
            Khởi tạo Company Owner
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
