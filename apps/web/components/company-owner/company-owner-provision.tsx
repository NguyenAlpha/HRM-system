"use client"

import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Loader2, ShieldCheck } from "lucide-react"
import { toast } from "sonner"

import { ActivationInvitation } from "@/components/account/activation-invitation"
import { PhoneInput } from "@/components/common/phone-input"
import { ErrorAlert } from "@/components/common/list-controls"
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
import { phoneSchema } from "@/lib/phone"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { usernameSchema } from "@/lib/account"
import { bootstrapCompanyOwner, type CompanyOwnerProvisioningResult } from "@/lib/company-owner"

const bootstrapSchema = z
  .object({
    companyName: z.string().trim().min(1, "Bắt buộc").max(40, "Tối đa 40 ký tự"),
    companyCode: z.string().trim().min(1, "Bắt buộc").max(20, "Tối đa 20 ký tự"),
    companyTaxCode: z.string().trim().max(30, "Tối đa 30 ký tự").optional(),
    companyPhone: phoneSchema,
    companyEmail: z
      .string()
      .trim()
      .max(30, "Tối đa 30 ký tự")
      .regex(/^$|^[^\s@]+@[^\s@]+\.[^\s@]+$/, "Email không hợp lệ")
      .optional(),
    companyAddress: z.string().trim().optional(),
    fullName: z.string().trim().min(1, "Bắt buộc").max(30, "Tối đa 30 ký tự"),
    workEmail: z
      .string()
      .trim()
      .min(1, "Bắt buộc")
      .max(30, "Tối đa 30 ký tự")
      .regex(/^[^\s@]+@[^\s@]+\.[^\s@]+$/, "Email không hợp lệ"),
    phone: phoneSchema,
    hireDate: z.string().min(1, "Bắt buộc"),
    username: usernameSchema,
    effectiveFrom: z.string().min(1, "Bắt buộc"),
    ownershipReason: z.string().trim().min(1, "Bắt buộc").max(100, "Tối đa 100 ký tự"),
    directorAppointmentReason: z.string().trim().min(1, "Bắt buộc").max(100, "Tối đa 100 ký tự"),
  })
  .refine((data) => data.effectiveFrom >= data.hireDate, {
    message: "Ngày hiệu lực không được trước ngày vào làm",
    path: ["effectiveFrom"],
  })

type BootstrapValues = z.infer<typeof bootstrapSchema>

export function CompanyOwnerProvision({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<CompanyOwnerProvisioningResult | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<BootstrapValues>({ resolver: zodResolver(bootstrapSchema) })

  async function onSubmit(values: BootstrapValues) {
    setError(null)
    try {
      const response = await bootstrapCompanyOwner({
        company: {
          code: values.companyCode.trim(),
          name: values.companyName.trim(),
          taxCode: values.companyTaxCode?.trim() || null,
          phone: values.companyPhone?.trim() || null,
          email: values.companyEmail?.trim() || null,
          address: values.companyAddress?.trim() || null,
        },
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
          <ActivationInvitation invitation={result.accountProvisioning.invitation} recipient="Company Owner"
            note="Hãy chuyển mã này cho Company Owner qua kênh an toàn để họ tự đặt mật khẩu." />

          <Separator />

          <div>
            <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Role đã cấp</p>
            <div className="mt-2 flex flex-wrap gap-2">
              <Badge variant="secondary"><ShieldCheck /> {result.companyOwnerRoleAssignment.roleName}</Badge>
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
          Nhập thông tin doanh nghiệp, rồi tạo hồ sơ, tài khoản và cấp đồng thời role Company Owner + Director cho người sở hữu doanh nghiệp. Sau bước này chỉ Company Owner sửa được thông tin doanh nghiệp. Chỉ thực hiện được một lần — nếu đã có Company Owner đang hoạt động, hệ thống sẽ từ chối.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form className="grid gap-4" onSubmit={handleSubmit(onSubmit)}>
          <ErrorAlert error={error} />

          <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Doanh nghiệp</p>
          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="company-name">Tên doanh nghiệp</Label>
              <Input id="company-name" aria-invalid={!!errors.companyName} {...register("companyName")} />
              {errors.companyName && <p className="text-xs font-medium text-destructive">{errors.companyName.message}</p>}
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="company-code">Mã doanh nghiệp</Label>
              <Input id="company-code" placeholder="VD: ACME" aria-invalid={!!errors.companyCode} {...register("companyCode")} />
              {errors.companyCode && <p className="text-xs font-medium text-destructive">{errors.companyCode.message}</p>}
            </div>
          </div>

          <div className="grid grid-cols-3 gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="company-tax-code">Mã số thuế</Label>
              <Input id="company-tax-code" aria-invalid={!!errors.companyTaxCode} {...register("companyTaxCode")} />
              {errors.companyTaxCode && <p className="text-xs font-medium text-destructive">{errors.companyTaxCode.message}</p>}
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="company-phone">Điện thoại</Label>
              <PhoneInput id="company-phone" aria-invalid={!!errors.companyPhone} {...register("companyPhone")} />
              {errors.companyPhone && <p className="text-xs font-medium text-destructive">{errors.companyPhone.message}</p>}
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="company-email">Email</Label>
              <Input id="company-email" type="email" aria-invalid={!!errors.companyEmail} {...register("companyEmail")} />
              {errors.companyEmail && <p className="text-xs font-medium text-destructive">{errors.companyEmail.message}</p>}
            </div>
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="company-address">Địa chỉ</Label>
            <Textarea id="company-address" rows={2} {...register("companyAddress")} />
          </div>

          <Separator />
          <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Company Owner</p>

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
              <PhoneInput id="owner-phone" aria-invalid={!!errors.phone} {...register("phone")} />
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
