"use client"

import { useEffect, useMemo, useState } from "react"
import { Controller, useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Loader2 } from "lucide-react"
import { toast } from "sonner"

import { RbacFeedback } from "@/components/admin/rbac-controls"
import { EmployeeBirthDateSelect } from "@/components/employee/employee-birth-date-select"
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
import { Separator } from "@/components/ui/separator"
import { Textarea } from "@/components/ui/textarea"
import {
  apiErrorMessage,
  isPortalSessionExpired,
} from "@/lib/api-helpers"
import {
  createEmployee,
  EDUCATION_LEVEL_LABELS,
  EDUCATION_LEVELS,
  EMPLOYMENT_TYPE_LABELS,
  EMPLOYMENT_TYPES,
  employeeRequest,
  GENDER_LABELS,
  GENDERS,
  type EducationLevel,
  type EmployeeSummary,
  type EmploymentType,
  type Gender,
  type Page,
} from "@/lib/employee"
import { positionsForOrganizationUnit } from "@/lib/organization-position-filter"
import { isEligibleEmployeeBirthDate } from "@/lib/employee-birth-date"
import {
  getJobPositionOptions,
  getOrganizationUnitOptions,
  getWorkLocationOptions,
  type JobPositionOption,
  type OrganizationUnitOption,
  type WorkLocationOption,
} from "@/lib/reference"

const GENDER_NONE = "NONE"
const EDU_NONE = "NONE"
const MANAGER_NONE = "NONE"

const GENDER_ITEMS: Record<string, string> = { [GENDER_NONE]: "Không chọn", ...GENDER_LABELS }
const EDUCATION_ITEMS: Record<string, string> = { [EDU_NONE]: "Không chọn", ...EDUCATION_LEVEL_LABELS }
const EMPLOYMENT_TYPE_ITEMS: Record<string, string> = { ...EMPLOYMENT_TYPE_LABELS }

const createEmployeeSchema = z
  .object({
    fullName: z.string().trim().min(1, "Bắt buộc").max(200, "Tối đa 200 ký tự"),
    hireDate: z.string().min(1, "Bắt buộc"),
    dateOfBirth: z.string().min(1, "Bắt buộc chọn ngày sinh")
      .refine((value) => !value || isEligibleEmployeeBirthDate(value), "Nhân sự phải đủ 17 tuổi"),
    gender: z.string(),
    highestEducationLevel: z.string(),
    major: z.string().max(200, "Tối đa 200 ký tự").optional(),
    institution: z.string().max(200, "Tối đa 200 ký tự").optional(),
    graduationYear: z.string().optional(),
    workEmail: z.string().max(100, "Tối đa 100 ký tự").optional(),
    phone: z.string().max(20, "Tối đa 20 ký tự").optional(),
    organizationUnitId: z.string().min(1, "Bắt buộc"),
    workLocationId: z.string().min(1, "Bắt buộc"),
    positionId: z.string().min(1, "Bắt buộc"),
    managerEmployeeId: z.string(),
    employmentType: z.string().min(1, "Bắt buộc"),
    effectiveFrom: z.string().min(1, "Bắt buộc"),
    reason: z.string().max(500, "Tối đa 500 ký tự").optional(),
  })
  .refine((data) => !data.workEmail || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.workEmail), {
    message: "Email không hợp lệ",
    path: ["workEmail"],
  })
  .refine((data) => !data.graduationYear || /^\d{4}$/.test(data.graduationYear), {
    message: "Năm phải gồm 4 chữ số",
    path: ["graduationYear"],
  })
  .refine((data) => data.effectiveFrom >= data.hireDate, {
    message: "Ngày hiệu lực phân công không được trước ngày vào làm",
    path: ["effectiveFrom"],
  })

type CreateEmployeeValues = z.infer<typeof createEmployeeSchema>

const EMPTY_VALUES: CreateEmployeeValues = {
  fullName: "",
  hireDate: "",
  dateOfBirth: "",
  gender: GENDER_NONE,
  highestEducationLevel: EDU_NONE,
  major: "",
  institution: "",
  graduationYear: "",
  workEmail: "",
  phone: "",
  organizationUnitId: "",
  workLocationId: "",
  positionId: "",
  managerEmployeeId: MANAGER_NONE,
  employmentType: "",
  effectiveFrom: "",
  reason: "",
}

export function EmployeeCreateDialog({ onClose, onCreated, onSessionExpired }: {
  onClose: () => void
  onCreated: () => void
  onSessionExpired: () => void
}) {
  const [dialogError, setDialogError] = useState<string | null>(null)
  const [orgUnits, setOrgUnits] = useState<OrganizationUnitOption[]>([])
  const [workLocations, setWorkLocations] = useState<WorkLocationOption[]>([])
  const [positions, setPositions] = useState<JobPositionOption[]>([])
  const [managers, setManagers] = useState<EmployeeSummary[]>([])
  const [loadingOptions, setLoadingOptions] = useState(true)

  const orgUnitItems = useMemo(
    () => Object.fromEntries(orgUnits.map((unit) => [String(unit.id), unit.name])),
    [orgUnits],
  )
  const workLocationItems = useMemo(
    () => Object.fromEntries(workLocations.map((location) => [String(location.id), location.name])),
    [workLocations],
  )
  const managerItems = useMemo(
    () => ({
      [MANAGER_NONE]: "Không có",
      ...Object.fromEntries(managers.map((manager) => [String(manager.id), `${manager.fullName} (${manager.employeeCode})`])),
    }),
    [managers],
  )

  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<CreateEmployeeValues>({
    resolver: zodResolver(createEmployeeSchema),
    defaultValues: EMPTY_VALUES,
  })

  const selectedUnitId = useWatch({ control, name: "organizationUnitId" })
  const availablePositions = useMemo(() => positionsForOrganizationUnit(
    orgUnits.find((unit) => String(unit.id) === selectedUnitId),
    positions,
  ), [orgUnits, positions, selectedUnitId])
  const positionItems = useMemo(
    () => Object.fromEntries(availablePositions.map((position) => [String(position.id), position.title])),
    [availablePositions],
  )

  useEffect(() => {
    let active = true

    Promise.all([
      getOrganizationUnitOptions("hrm"),
      getWorkLocationOptions("hrm"),
      getJobPositionOptions("hrm"),
      employeeRequest<Page<EmployeeSummary>>("hrm", "?page=0&size=100"),
    ])
      .then(([units, locations, jobPositions, employeePage]) => {
        if (!active) return
        setOrgUnits(units)
        setWorkLocations(locations)
        setPositions(jobPositions)
        setManagers(employeePage.content)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setDialogError(apiErrorMessage(caught))
      })
      .finally(() => {
        if (active) setLoadingOptions(false)
      })

    return () => { active = false }
  }, [onSessionExpired])

  async function onSubmit(values: CreateEmployeeValues) {
    setDialogError(null)
    try {
      const result = await createEmployee({
        employee: {
          fullName: values.fullName.trim(),
          dateOfBirth: values.dateOfBirth,
          gender: values.gender === GENDER_NONE ? null : (values.gender as Gender),
          highestEducationLevel: values.highestEducationLevel === EDU_NONE ? null : (values.highestEducationLevel as EducationLevel),
          major: values.major?.trim() || null,
          institution: values.institution?.trim() || null,
          graduationYear: values.graduationYear ? Number(values.graduationYear) : null,
          workEmail: values.workEmail?.trim() || null,
          phone: values.phone?.trim() || null,
          hireDate: values.hireDate,
        },
        initialAssignment: {
          organizationUnitId: Number(values.organizationUnitId),
          workLocationId: Number(values.workLocationId),
          positionId: Number(values.positionId),
          shiftId: null,
          managerEmployeeId: values.managerEmployeeId === MANAGER_NONE ? null : Number(values.managerEmployeeId),
          employmentType: values.employmentType as EmploymentType,
          effectiveFrom: values.effectiveFrom,
          reason: values.reason?.trim() || null,
        },
      })
      toast.success(`Đã tạo ${result.employee.fullName} (${result.employee.employeeCode})`, {
        description: "Tiếp theo: cấp tài khoản đăng nhập cho nhân sự này từ danh sách.",
      })
      onClose()
      onCreated()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setDialogError(apiErrorMessage(caught))
    }
  }

  return (
    <Dialog open onOpenChange={(next) => { if (!next) onClose() }}>
      <DialogContent className="sm:max-w-2xl">
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogHeader>
            <DialogTitle>Thêm nhân sự</DialogTitle>
            <DialogDescription>
              Tạo hồ sơ nhân sự và phân công ban đầu. Chưa tạo tài khoản đăng nhập ở bước này.
            </DialogDescription>
          </DialogHeader>

          <div className="mt-4 grid max-h-[65vh] gap-5 overflow-y-auto pr-1">
            <RbacFeedback error={dialogError} />

            <section className="grid gap-4">
              <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Hồ sơ nhân sự</p>
              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-1.5">
                  <Label>Mã nhân viên</Label>
                  <p className="flex min-h-9 items-center rounded-md border bg-muted/50 px-3 text-sm text-muted-foreground">
                    Tự tạo theo vị trí sau khi lưu
                  </p>
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="new-emp-name">Họ tên</Label>
                  <Input id="new-emp-name" aria-invalid={!!errors.fullName} {...register("fullName")} />
                  {errors.fullName && <p className="text-xs font-medium text-destructive">{errors.fullName.message}</p>}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-1.5">
                  <Label htmlFor="new-emp-hire-date">Ngày vào làm</Label>
                  <Input id="new-emp-hire-date" type="date" aria-invalid={!!errors.hireDate} {...register("hireDate")} />
                  {errors.hireDate && <p className="text-xs font-medium text-destructive">{errors.hireDate.message}</p>}
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="new-emp-dob">Ngày sinh</Label>
                  <Controller control={control} name="dateOfBirth" render={({ field }) => (
                    <EmployeeBirthDateSelect id="new-emp-dob" value={field.value}
                      onChange={field.onChange} invalid={!!errors.dateOfBirth} />
                  )} />
                  {errors.dateOfBirth && <p className="text-xs font-medium text-destructive">{errors.dateOfBirth.message}</p>}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-1.5">
                  <Label htmlFor="new-emp-email">Email công việc</Label>
                  <Input id="new-emp-email" type="email" aria-invalid={!!errors.workEmail} {...register("workEmail")} />
                  {errors.workEmail && <p className="text-xs font-medium text-destructive">{errors.workEmail.message}</p>}
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="new-emp-phone">Điện thoại</Label>
                  <Input id="new-emp-phone" aria-invalid={!!errors.phone} {...register("phone")} />
                  {errors.phone && <p className="text-xs font-medium text-destructive">{errors.phone.message}</p>}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-1.5">
                  <Label htmlFor="new-emp-gender">Giới tính</Label>
                  <Controller
                    control={control}
                    name="gender"
                    render={({ field }) => (
                      <Select value={field.value} items={GENDER_ITEMS} onValueChange={field.onChange}>
                        <SelectTrigger id="new-emp-gender" className="w-full">
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
                <div className="grid gap-1.5">
                  <Label htmlFor="new-emp-education">Trình độ học vấn</Label>
                  <Controller
                    control={control}
                    name="highestEducationLevel"
                    render={({ field }) => (
                      <Select value={field.value} items={EDUCATION_ITEMS} onValueChange={field.onChange}>
                        <SelectTrigger id="new-emp-education" className="w-full">
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
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-1.5">
                  <Label htmlFor="new-emp-major">Chuyên ngành</Label>
                  <Input id="new-emp-major" {...register("major")} />
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="new-emp-institution">Trường / đơn vị đào tạo</Label>
                  <Input id="new-emp-institution" {...register("institution")} />
                </div>
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="new-emp-grad-year">Năm tốt nghiệp</Label>
                <Input id="new-emp-grad-year" className="max-w-40" inputMode="numeric" placeholder="VD: 2020" aria-invalid={!!errors.graduationYear} {...register("graduationYear")} />
                {errors.graduationYear && <p className="text-xs font-medium text-destructive">{errors.graduationYear.message}</p>}
              </div>
            </section>

            <Separator />

            <section className="grid gap-4">
              <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Phân công ban đầu</p>

              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-1.5">
                  <Label htmlFor="new-emp-org-unit">Đơn vị tổ chức</Label>
                  <Controller
                    control={control}
                    name="organizationUnitId"
                    render={({ field }) => (
                      <Select value={field.value} items={orgUnitItems} onValueChange={(value) => {
                        field.onChange(value)
                        setValue("positionId", "")
                      }} disabled={loadingOptions}>
                        <SelectTrigger id="new-emp-org-unit" className="w-full" aria-invalid={!!errors.organizationUnitId}>
                          <SelectValue placeholder={loadingOptions ? "Đang tải..." : "Chọn đơn vị..."} />
                        </SelectTrigger>
                        <SelectContent>
                          {orgUnits.map((unit) => <SelectItem key={unit.id} value={String(unit.id)}>{unit.name}</SelectItem>)}
                        </SelectContent>
                      </Select>
                    )}
                  />
                  {errors.organizationUnitId && <p className="text-xs font-medium text-destructive">{errors.organizationUnitId.message}</p>}
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="new-emp-location">Địa điểm làm việc</Label>
                  <Controller
                    control={control}
                    name="workLocationId"
                    render={({ field }) => (
                      <Select value={field.value} items={workLocationItems} onValueChange={field.onChange} disabled={loadingOptions}>
                        <SelectTrigger id="new-emp-location" className="w-full" aria-invalid={!!errors.workLocationId}>
                          <SelectValue placeholder={loadingOptions ? "Đang tải..." : "Chọn địa điểm..."} />
                        </SelectTrigger>
                        <SelectContent>
                          {workLocations.map((location) => <SelectItem key={location.id} value={String(location.id)}>{location.name}</SelectItem>)}
                        </SelectContent>
                      </Select>
                    )}
                  />
                  {errors.workLocationId && <p className="text-xs font-medium text-destructive">{errors.workLocationId.message}</p>}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-1.5">
                  <Label htmlFor="new-emp-position">Vị trí công việc</Label>
                  <Controller
                    control={control}
                    name="positionId"
                    render={({ field }) => (
                      <Select value={field.value} items={positionItems} onValueChange={field.onChange} disabled={loadingOptions || !selectedUnitId || availablePositions.length === 0}>
                        <SelectTrigger id="new-emp-position" className="w-full" aria-invalid={!!errors.positionId}>
                          <SelectValue placeholder={loadingOptions ? "Đang tải..." : !selectedUnitId ? "Chọn đơn vị trước..." : availablePositions.length === 0 ? "Chưa có vị trí phù hợp" : "Chọn vị trí..."} />
                        </SelectTrigger>
                        <SelectContent>
                          {availablePositions.map((position) => <SelectItem key={position.id} value={String(position.id)}>{position.title}</SelectItem>)}
                        </SelectContent>
                      </Select>
                    )}
                  />
                  {errors.positionId && <p className="text-xs font-medium text-destructive">{errors.positionId.message}</p>}
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="new-emp-employment-type">Hình thức làm việc</Label>
                  <Controller
                    control={control}
                    name="employmentType"
                    render={({ field }) => (
                      <Select value={field.value} items={EMPLOYMENT_TYPE_ITEMS} onValueChange={field.onChange}>
                        <SelectTrigger id="new-emp-employment-type" className="w-full" aria-invalid={!!errors.employmentType}>
                          <SelectValue placeholder="Chọn..." />
                        </SelectTrigger>
                        <SelectContent>
                          {EMPLOYMENT_TYPES.map((type) => <SelectItem key={type} value={type}>{EMPLOYMENT_TYPE_LABELS[type]}</SelectItem>)}
                        </SelectContent>
                      </Select>
                    )}
                  />
                  {errors.employmentType && <p className="text-xs font-medium text-destructive">{errors.employmentType.message}</p>}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-1.5">
                  <Label htmlFor="new-emp-manager">Quản lý trực tiếp</Label>
                  <Controller
                    control={control}
                    name="managerEmployeeId"
                    render={({ field }) => (
                      <Select value={field.value} items={managerItems} onValueChange={field.onChange} disabled={loadingOptions}>
                        <SelectTrigger id="new-emp-manager" className="w-full">
                          <SelectValue placeholder={loadingOptions ? "Đang tải..." : "Không có"} />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value={MANAGER_NONE}>Không có</SelectItem>
                          {managers.map((manager) => (
                            <SelectItem key={manager.id} value={String(manager.id)}>{manager.fullName} ({manager.employeeCode})</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  />
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="new-emp-effective-from">Hiệu lực phân công từ</Label>
                  <Input id="new-emp-effective-from" type="date" aria-invalid={!!errors.effectiveFrom} {...register("effectiveFrom")} />
                  {errors.effectiveFrom && <p className="text-xs font-medium text-destructive">{errors.effectiveFrom.message}</p>}
                </div>
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="new-emp-reason">Lý do phân công</Label>
                <Textarea id="new-emp-reason" rows={2} {...register("reason")} />
              </div>
            </section>
          </div>

          <DialogFooter>
            <Button type="submit" disabled={isSubmitting || loadingOptions}>
              {isSubmitting && <Loader2 className="animate-spin" />}
              Tạo hồ sơ
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
