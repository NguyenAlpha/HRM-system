"use client"

import { useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Loader2 } from "lucide-react"
import { toast } from "sonner"

import { EmployeeBirthDateSelect } from "@/components/employee/employee-birth-date-select"
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  EDUCATION_LEVEL_LABELS,
  EDUCATION_LEVELS,
  employeeErrorMessage,
  employeeMutation,
  GENDER_LABELS,
  GENDERS,
  isSessionExpired,
  type EducationLevel,
  type EmployeeDetail,
  type Gender,
  type UpdateEmployeeInput,
} from "@/lib/employee"
import type { Portal } from "@/lib/auth/types"
import { isEligibleEmployeeBirthDate } from "@/lib/employee-birth-date"

const GENDER_NONE = "NONE"
const EDU_NONE = "NONE"

const GENDER_ITEMS: Record<string, string> = { [GENDER_NONE]: "Không chọn", ...GENDER_LABELS }
const EDUCATION_ITEMS: Record<string, string> = { [EDU_NONE]: "Không chọn", ...EDUCATION_LEVEL_LABELS }

const employeeEditSchema = z
  .object({
    fullName: z.string().trim().min(1, "Bắt buộc").max(200, "Tối đa 200 ký tự"),
    dateOfBirth: z.string().refine((value) => !value || isEligibleEmployeeBirthDate(value),
      "Nhân sự phải đủ 17 tuổi"),
    gender: z.string(),
    highestEducationLevel: z.string(),
    major: z.string().max(200, "Tối đa 200 ký tự").optional(),
    institution: z.string().max(200, "Tối đa 200 ký tự").optional(),
    graduationYear: z.string().optional(),
    workEmail: z.string().max(100, "Tối đa 100 ký tự").optional(),
    phone: z.string().max(20, "Tối đa 20 ký tự").optional(),
  })
  .refine((data) => !data.workEmail || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.workEmail), {
    message: "Email không hợp lệ",
    path: ["workEmail"],
  })
  .refine((data) => !data.graduationYear || /^\d{4}$/.test(data.graduationYear), {
    message: "Năm phải gồm 4 chữ số",
    path: ["graduationYear"],
  })

type EmployeeEditValues = z.infer<typeof employeeEditSchema>

function toFormValues(detail: EmployeeDetail): EmployeeEditValues {
  return {
    fullName: detail.fullName,
    dateOfBirth: detail.dateOfBirth ?? "",
    gender: detail.gender ?? GENDER_NONE,
    highestEducationLevel: detail.highestEducationLevel ?? EDU_NONE,
    major: detail.major ?? "",
    institution: detail.institution ?? "",
    graduationYear: detail.graduationYear ? String(detail.graduationYear) : "",
    workEmail: detail.workEmail ?? "",
    phone: detail.phone ?? "",
  }
}

/** Edits the personal and education fields of one employee; mounted only while open. */
export function EmployeeEditDialog({ portal, detail, onClose, onSaved, onSessionExpired }: {
  portal: Portal
  detail: EmployeeDetail
  onClose: () => void
  onSaved: (saved: EmployeeDetail) => void
  onSessionExpired: () => void
}) {
  const [error, setError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = useForm<EmployeeEditValues>({ resolver: zodResolver(employeeEditSchema), defaultValues: toFormValues(detail) })

  async function onSubmit(values: EmployeeEditValues) {
    setError(null)
    try {
      const body: UpdateEmployeeInput = {
        fullName: values.fullName.trim(),
        dateOfBirth: values.dateOfBirth || null,
        gender: values.gender === GENDER_NONE ? null : (values.gender as Gender),
        highestEducationLevel: values.highestEducationLevel === EDU_NONE ? null : (values.highestEducationLevel as EducationLevel),
        major: values.major?.trim() || null,
        institution: values.institution?.trim() || null,
        graduationYear: values.graduationYear ? Number(values.graduationYear) : null,
        workEmail: values.workEmail?.trim() || null,
        phone: values.phone?.trim() || null,
      }
      const saved = await employeeMutation<EmployeeDetail>(portal, `/${detail.id}`, "PUT", body)
      toast.success(`Đã cập nhật hồ sơ ${saved.fullName}`)
      onSaved(saved)
      onClose()
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      setError(employeeErrorMessage(caught))
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !isSubmitting) onClose() }}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogHeader>
            <DialogTitle>Sửa hồ sơ {detail.fullName}</DialogTitle>
            <DialogDescription>Cập nhật thông tin cá nhân và học vấn của nhân sự.</DialogDescription>
          </DialogHeader>

          <div className="mt-4 grid max-h-[65vh] gap-4 overflow-y-auto pr-1">
            <ErrorAlert error={error} />

            <div className="grid gap-1.5">
              <Label htmlFor="emp-fullname">Họ tên</Label>
              <Input id="emp-fullname" aria-invalid={!!errors.fullName} {...register("fullName")} />
              {errors.fullName && <p className="text-xs font-medium text-destructive">{errors.fullName.message}</p>}
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-1.5">
                <Label htmlFor="emp-dob">Ngày sinh</Label>
                <Controller control={control} name="dateOfBirth" render={({ field }) => (
                  <EmployeeBirthDateSelect id="emp-dob" value={field.value}
                    onChange={field.onChange} invalid={!!errors.dateOfBirth} />
                )} />
                {errors.dateOfBirth && <p className="text-xs font-medium text-destructive">{errors.dateOfBirth.message}</p>}
              </div>
              <div className="grid gap-1.5">
                <Label htmlFor="emp-gender">Giới tính</Label>
                <Controller
                  control={control}
                  name="gender"
                  render={({ field }) => (
                    <Select value={field.value} items={GENDER_ITEMS} onValueChange={field.onChange}>
                      <SelectTrigger id="emp-gender" className="w-full">
                        <SelectValue placeholder="Chọn..." />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={GENDER_NONE}>Không chọn</SelectItem>
                        {GENDERS.map((g) => <SelectItem key={g} value={g}>{GENDER_LABELS[g]}</SelectItem>)}
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-1.5">
                <Label htmlFor="emp-education">Trình độ học vấn</Label>
                <Controller
                  control={control}
                  name="highestEducationLevel"
                  render={({ field }) => (
                    <Select value={field.value} items={EDUCATION_ITEMS} onValueChange={field.onChange}>
                      <SelectTrigger id="emp-education" className="w-full">
                        <SelectValue placeholder="Chọn..." />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={EDU_NONE}>Không chọn</SelectItem>
                        {EDUCATION_LEVELS.map((e) => <SelectItem key={e} value={e}>{EDUCATION_LEVEL_LABELS[e]}</SelectItem>)}
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>
              <div className="grid gap-1.5">
                <Label htmlFor="emp-grad-year">Năm tốt nghiệp</Label>
                <Input id="emp-grad-year" inputMode="numeric" placeholder="VD: 2020" aria-invalid={!!errors.graduationYear} {...register("graduationYear")} />
                {errors.graduationYear && <p className="text-xs font-medium text-destructive">{errors.graduationYear.message}</p>}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-1.5">
                <Label htmlFor="emp-major">Chuyên ngành</Label>
                <Input id="emp-major" {...register("major")} />
              </div>
              <div className="grid gap-1.5">
                <Label htmlFor="emp-institution">Trường / đơn vị đào tạo</Label>
                <Input id="emp-institution" {...register("institution")} />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-1.5">
                <Label htmlFor="emp-email">Email công việc</Label>
                <Input id="emp-email" type="email" aria-invalid={!!errors.workEmail} {...register("workEmail")} />
                {errors.workEmail && <p className="text-xs font-medium text-destructive">{errors.workEmail.message}</p>}
              </div>
              <div className="grid gap-1.5">
                <Label htmlFor="emp-phone">Điện thoại</Label>
                <Input id="emp-phone" aria-invalid={!!errors.phone} {...register("phone")} />
                {errors.phone && <p className="text-xs font-medium text-destructive">{errors.phone.message}</p>}
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting && <Loader2 className="animate-spin" />}
              Lưu thay đổi
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
