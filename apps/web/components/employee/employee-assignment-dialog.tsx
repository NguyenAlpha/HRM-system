"use client"

import { useEffect, useMemo, useState } from "react"
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
  employeeErrorMessage,
  employeeMutation,
  employeeRequest,
  EMPLOYMENT_TYPE_LABELS,
  EMPLOYMENT_TYPES,
  isSessionExpired,
  type EmployeeAssignment,
  type EmployeeDetail,
  type EmployeeSummary,
  type EmploymentType,
  type Page,
} from "@/lib/employee"
import {
  getJobPositionOptions,
  getOrganizationUnitOptions,
  getWorkLocationOptions,
  getWorkShiftOptions,
  type JobPositionOption,
  type OrganizationUnitOption,
  type WorkLocationOption,
  type WorkShiftOption,
} from "@/lib/reference"

const NONE = "NONE"

const assignmentSchema = z.object({
  organizationUnitId: z.string().min(1, "Bắt buộc"),
  workLocationId: z.string().min(1, "Bắt buộc"),
  positionId: z.string().min(1, "Bắt buộc"),
  shiftId: z.string(),
  managerEmployeeId: z.string(),
  employmentType: z.string().min(1, "Bắt buộc"),
  effectiveFrom: z.string().min(1, "Bắt buộc"),
  reason: z.string().max(500, "Tối đa 500 ký tự").optional(),
})

type AssignmentValues = z.infer<typeof assignmentSchema>

function localIsoDate(date = new Date()) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

function nextEffectiveDate(detail: EmployeeDetail) {
  const today = localIsoDate()
  const currentStart = detail.currentAssignment?.effectiveFrom
  if (!currentStart) return today < detail.hireDate ? detail.hireDate : today

  const [year, month, day] = currentStart.split("-").map(Number)
  const nextDate = new Date(year, month - 1, day)
  nextDate.setDate(nextDate.getDate() + 1)
  const dayAfterCurrentStart = localIsoDate(nextDate)
  return today < dayAfterCurrentStart ? dayAfterCurrentStart : today
}

export function EmployeeAssignmentDialog({ employee, onClose, onAssigned, onSessionExpired }: {
  employee: EmployeeSummary
  onClose: () => void
  onAssigned: () => void
  onSessionExpired: () => void
}) {
  const [dialogError, setDialogError] = useState<string | null>(null)
  const [detail, setDetail] = useState<EmployeeDetail | null>(null)
  const [orgUnits, setOrgUnits] = useState<OrganizationUnitOption[]>([])
  const [workLocations, setWorkLocations] = useState<WorkLocationOption[]>([])
  const [positions, setPositions] = useState<JobPositionOption[]>([])
  const [shifts, setShifts] = useState<WorkShiftOption[]>([])
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
  const positionItems = useMemo(
    () => Object.fromEntries(positions.map((position) => [String(position.id), position.title])),
    [positions],
  )
  const shiftItems = useMemo(
    () => ({ [NONE]: "Không xếp ca", ...Object.fromEntries(shifts.map((shift) => [String(shift.id), shift.name])) }),
    [shifts],
  )
  const managerItems = useMemo(
    () => ({
      [NONE]: "Không có",
      ...Object.fromEntries(managers.map((manager) => [String(manager.id), `${manager.fullName} (${manager.employeeCode})`])),
    }),
    [managers],
  )
  const employmentTypeItems = useMemo(() => ({ ...EMPLOYMENT_TYPE_LABELS }), [])

  const {
    register,
    handleSubmit,
    control,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<AssignmentValues>({
    resolver: zodResolver(assignmentSchema),
    defaultValues: {
      organizationUnitId: "",
      workLocationId: "",
      positionId: "",
      shiftId: NONE,
      managerEmployeeId: NONE,
      employmentType: "",
      effectiveFrom: "",
      reason: "",
    },
  })

  useEffect(() => {
    let active = true

    Promise.all([
      employeeRequest<EmployeeDetail>("hrm", `/${employee.id}`),
      getOrganizationUnitOptions("hrm"),
      getWorkLocationOptions("hrm"),
      getJobPositionOptions("hrm"),
      getWorkShiftOptions("hrm"),
      employeeRequest<Page<EmployeeSummary>>("hrm", "?page=0&size=100"),
    ])
      .then(([employeeDetail, units, locations, jobPositions, workShifts, employeePage]) => {
        if (!active) return
        const current = employeeDetail.currentAssignment
        setDetail(employeeDetail)
        setOrgUnits(units)
        setWorkLocations(locations)
        setPositions(jobPositions)
        setShifts(workShifts)
        setManagers(employeePage.content.filter((candidate) =>
          candidate.id !== employee.id
          && (candidate.employmentStatus === "ACTIVE" || candidate.employmentStatus === "PROBATION")))
        reset({
          organizationUnitId: current ? String(current.organizationUnitId) : "",
          workLocationId: current ? String(current.workLocationId) : "",
          positionId: current ? String(current.positionId) : "",
          shiftId: current?.shiftId ? String(current.shiftId) : NONE,
          managerEmployeeId: current?.managerEmployeeId ? String(current.managerEmployeeId) : NONE,
          employmentType: current?.employmentType ?? "",
          effectiveFrom: nextEffectiveDate(employeeDetail),
          reason: "",
        })
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isSessionExpired(caught)) onSessionExpired()
        setDialogError(employeeErrorMessage(caught))
      })
      .finally(() => {
        if (active) setLoadingOptions(false)
      })

    return () => { active = false }
  }, [employee.id, onSessionExpired, reset])

  async function onSubmit(values: AssignmentValues) {
    setDialogError(null)
    try {
      const assignment = await employeeMutation<EmployeeAssignment>(
        "hrm",
        `/${employee.id}/assignments`,
        "POST",
        {
          organizationUnitId: Number(values.organizationUnitId),
          workLocationId: Number(values.workLocationId),
          positionId: Number(values.positionId),
          shiftId: values.shiftId === NONE ? null : Number(values.shiftId),
          managerEmployeeId: values.managerEmployeeId === NONE ? null : Number(values.managerEmployeeId),
          employmentType: values.employmentType as EmploymentType,
          effectiveFrom: values.effectiveFrom,
          reason: values.reason?.trim() || null,
        },
      )
      toast.success(`Đã cập nhật phân công cho ${employee.fullName}`, {
        description: `Hiệu lực từ ${assignment.effectiveFrom}.`,
      })
      onClose()
      onAssigned()
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
            <DialogTitle>Thay đổi phân công</DialogTitle>
            <DialogDescription>
              Điều chuyển, bổ nhiệm hoặc cập nhật phân công cho {employee.fullName} ({employee.employeeCode}).
            </DialogDescription>
          </DialogHeader>

          <div className="mt-4 grid max-h-[65vh] gap-4 overflow-y-auto pr-1">
            <RbacFeedback error={dialogError} />

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="grid gap-1.5">
                <Label htmlFor="assignment-org-unit">Đơn vị tổ chức</Label>
                <Controller
                  control={control}
                  name="organizationUnitId"
                  render={({ field }) => (
                    <Select value={field.value} items={orgUnitItems} onValueChange={field.onChange} disabled={loadingOptions}>
                      <SelectTrigger id="assignment-org-unit" className="w-full" aria-invalid={!!errors.organizationUnitId}>
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
                <Label htmlFor="assignment-location">Địa điểm làm việc</Label>
                <Controller
                  control={control}
                  name="workLocationId"
                  render={({ field }) => (
                    <Select value={field.value} items={workLocationItems} onValueChange={field.onChange} disabled={loadingOptions}>
                      <SelectTrigger id="assignment-location" className="w-full" aria-invalid={!!errors.workLocationId}>
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

              <div className="grid gap-1.5">
                <Label htmlFor="assignment-position">Vị trí công việc</Label>
                <Controller
                  control={control}
                  name="positionId"
                  render={({ field }) => (
                    <Select value={field.value} items={positionItems} onValueChange={field.onChange} disabled={loadingOptions}>
                      <SelectTrigger id="assignment-position" className="w-full" aria-invalid={!!errors.positionId}>
                        <SelectValue placeholder={loadingOptions ? "Đang tải..." : "Chọn vị trí..."} />
                      </SelectTrigger>
                      <SelectContent>
                        {positions.map((position) => <SelectItem key={position.id} value={String(position.id)}>{position.title}</SelectItem>)}
                      </SelectContent>
                    </Select>
                  )}
                />
                {errors.positionId && <p className="text-xs font-medium text-destructive">{errors.positionId.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="assignment-employment-type">Hình thức làm việc</Label>
                <Controller
                  control={control}
                  name="employmentType"
                  render={({ field }) => (
                    <Select value={field.value} items={employmentTypeItems} onValueChange={field.onChange}>
                      <SelectTrigger id="assignment-employment-type" className="w-full" aria-invalid={!!errors.employmentType}>
                        <SelectValue placeholder="Chọn hình thức..." />
                      </SelectTrigger>
                      <SelectContent>
                        {EMPLOYMENT_TYPES.map((type) => <SelectItem key={type} value={type}>{EMPLOYMENT_TYPE_LABELS[type]}</SelectItem>)}
                      </SelectContent>
                    </Select>
                  )}
                />
                {errors.employmentType && <p className="text-xs font-medium text-destructive">{errors.employmentType.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="assignment-shift">Ca làm việc</Label>
                <Controller
                  control={control}
                  name="shiftId"
                  render={({ field }) => (
                    <Select value={field.value} items={shiftItems} onValueChange={field.onChange} disabled={loadingOptions}>
                      <SelectTrigger id="assignment-shift" className="w-full">
                        <SelectValue placeholder={loadingOptions ? "Đang tải..." : "Không xếp ca"} />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NONE}>Không xếp ca</SelectItem>
                        {shifts.map((shift) => <SelectItem key={shift.id} value={String(shift.id)}>{shift.name}</SelectItem>)}
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="assignment-manager">Quản lý trực tiếp</Label>
                <Controller
                  control={control}
                  name="managerEmployeeId"
                  render={({ field }) => (
                    <Select value={field.value} items={managerItems} onValueChange={field.onChange} disabled={loadingOptions}>
                      <SelectTrigger id="assignment-manager" className="w-full">
                        <SelectValue placeholder={loadingOptions ? "Đang tải..." : "Không có"} />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NONE}>Không có</SelectItem>
                        {managers.map((manager) => (
                          <SelectItem key={manager.id} value={String(manager.id)}>{manager.fullName} ({manager.employeeCode})</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </div>

              <div className="grid gap-1.5 sm:col-span-2">
                <Label htmlFor="assignment-effective-from">Hiệu lực từ</Label>
                <Input id="assignment-effective-from" type="date" className="sm:max-w-xs" aria-invalid={!!errors.effectiveFrom} {...register("effectiveFrom")} />
                {errors.effectiveFrom && <p className="text-xs font-medium text-destructive">{errors.effectiveFrom.message}</p>}
              </div>
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="assignment-reason">Lý do thay đổi</Label>
              <Textarea
                id="assignment-reason"
                rows={3}
                placeholder="VD: Điều chuyển sang bộ phận vận hành"
                aria-invalid={!!errors.reason}
                {...register("reason")}
              />
              {errors.reason && <p className="text-xs font-medium text-destructive">{errors.reason.message}</p>}
            </div>

            {detail?.currentAssignment && (
              <p className="text-xs text-muted-foreground">
                Phân công hiện tại sẽ kết thúc vào ngày liền trước ngày hiệu lực mới.
              </p>
            )}
          </div>

          <DialogFooter>
            <DialogClose render={<Button type="button" variant="outline" />}>Hủy</DialogClose>
            <Button type="submit" disabled={isSubmitting || loadingOptions || !detail}>
              {isSubmitting && <Loader2 className="animate-spin" />}
              Lưu phân công
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
