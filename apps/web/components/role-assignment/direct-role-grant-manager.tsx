"use client"

import { useEffect, useMemo, useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Check, Copy, KeyRound, Link2, Loader2, ShieldCheck, UserPlus } from "lucide-react"
import { toast } from "sonner"

import { RbacFeedback } from "@/components/admin/rbac-controls"
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { provisionAccount, type AccountProvisioningResponse } from "@/lib/account"
import type { Portal } from "@/lib/auth/types"
import { employeeRequest, type EmployeeSummary, type Page as EmployeePage } from "@/lib/employee"
import {
  getOrganizationUnitOptions,
  getWorkLocationOptions,
  type OrganizationUnitOption,
  type WorkLocationOption,
} from "@/lib/reference"
import { rbacRequest, type Page as RbacPage, type Role } from "@/lib/rbac"
import {
  assignAccountRoleDirect,
  ROLE_SCOPE_LABELS,
  ROLE_SCOPE_TYPES,
  type AccountRoleAssignment,
  type RoleScopeType,
} from "@/lib/role-assignment"

const grantSchema = z
  .object({
    employeeId: z.string().min(1, "Bắt buộc"),
    username: z.string().trim().max(50, "Tối đa 50 ký tự").optional(),
    roleCode: z.string().min(1, "Bắt buộc"),
    scopeType: z.string().min(1, "Bắt buộc"),
    organizationUnitId: z.string().optional(),
    workLocationId: z.string().optional(),
    effectiveFrom: z.string().min(1, "Bắt buộc"),
    effectiveTo: z.string().optional(),
    reason: z.string().trim().min(1, "Bắt buộc").max(500, "Tối đa 500 ký tự"),
  })
  .refine((data) => data.scopeType !== "ORG_UNIT" || !!data.organizationUnitId, {
    message: "Bắt buộc chọn đơn vị tổ chức",
    path: ["organizationUnitId"],
  })
  .refine((data) => data.scopeType !== "LOCATION" || !!data.workLocationId, {
    message: "Bắt buộc chọn địa điểm",
    path: ["workLocationId"],
  })
  .refine((data) => !data.effectiveTo || data.effectiveTo >= data.effectiveFrom, {
    message: "Ngày kết thúc không được trước ngày bắt đầu",
    path: ["effectiveTo"],
  })

type GrantValues = z.infer<typeof grantSchema>

const EMPTY_VALUES: GrantValues = {
  employeeId: "",
  username: "",
  roleCode: "",
  scopeType: "",
  organizationUnitId: "",
  workLocationId: "",
  effectiveFrom: "",
  effectiveTo: "",
  reason: "",
}

interface GrantResult {
  employee: EmployeeSummary
  provisioning: AccountProvisioningResponse | null
  assignment: AccountRoleAssignment | null
}

export function DirectRoleGrantManager({ portal, onSessionExpired }: { portal: Portal; onSessionExpired: () => void }) {
  const [loadingOptions, setLoadingOptions] = useState(true)
  const [optionsError, setOptionsError] = useState<string | null>(null)
  const [employees, setEmployees] = useState<EmployeeSummary[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [orgUnits, setOrgUnits] = useState<OrganizationUnitOption[]>([])
  const [workLocations, setWorkLocations] = useState<WorkLocationOption[]>([])
  const [revision, setRevision] = useState(0)

  const [formError, setFormError] = useState<string | null>(null)
  const [result, setResult] = useState<GrantResult | null>(null)
  const [copiedToken, setCopiedToken] = useState(false)
  const [copiedLink, setCopiedLink] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    watch,
    control,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<GrantValues>({ resolver: zodResolver(grantSchema), defaultValues: EMPTY_VALUES })

  const selectedEmployeeId = watch("employeeId")
  const selectedScopeType = watch("scopeType")
  const selectedEmployee = useMemo(
    () => employees.find((employee) => String(employee.id) === selectedEmployeeId) ?? null,
    [employees, selectedEmployeeId],
  )

  const employeeItems = useMemo(
    () => Object.fromEntries(employees.map((employee) => [
      String(employee.id),
      `${employee.fullName}${employee.account ? ` (${employee.account.username})` : " · chưa có tài khoản"}`,
    ])),
    [employees],
  )
  const roleItems = useMemo(
    () => Object.fromEntries(roles.map((role) => [role.code, role.name])),
    [roles],
  )
  const scopeItems = useMemo(
    () => Object.fromEntries(ROLE_SCOPE_TYPES.map((scope) => [scope, ROLE_SCOPE_LABELS[scope]])),
    [],
  )
  const orgUnitItems = useMemo(
    () => Object.fromEntries(orgUnits.map((unit) => [String(unit.id), unit.name])),
    [orgUnits],
  )
  const workLocationItems = useMemo(
    () => Object.fromEntries(workLocations.map((location) => [String(location.id), location.name])),
    [workLocations],
  )

  useEffect(() => {
    let active = true
    setLoadingOptions(true)
    setOptionsError(null)
    Promise.all([
      employeeRequest<EmployeePage<EmployeeSummary>>(portal, "?page=0&size=100"),
      rbacRequest<RbacPage<Role>>(portal, "/roles?page=0&size=100&sort=name,asc"),
      getOrganizationUnitOptions(portal),
      getWorkLocationOptions(portal),
    ])
      .then(([employeePage, rolePage, units, locations]) => {
        if (!active) return
        setEmployees(employeePage.content)
        setRoles(rolePage.content.filter((role) => role.grantPolicy === "HR_ASSIGNABLE" || role.grantPolicy === "OWNER_APPROVAL"))
        setOrgUnits(units)
        setWorkLocations(locations)
        setLoadingOptions(false)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setOptionsError(apiErrorMessage(caught))
        setLoadingOptions(false)
      })
    return () => { active = false }
  }, [portal, revision, onSessionExpired])

  function startOver() {
    setResult(null)
    setFormError(null)
    reset(EMPTY_VALUES)
    setRevision((value) => value + 1)
  }

  async function onSubmit(values: GrantValues) {
    setFormError(null)
    const employee = employees.find((item) => String(item.id) === values.employeeId)
    if (!employee) return

    let accountId: number
    let provisioning: AccountProvisioningResponse | null = null

    if (employee.account) {
      accountId = employee.account.id
    } else {
      if (!values.username?.trim()) {
        setError("username", { message: "Bắt buộc nhập tên đăng nhập" })
        return
      }
      try {
        provisioning = await provisionAccount(portal, employee.id, values.username.trim())
        accountId = provisioning.account.id
      } catch (caught) {
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setFormError(apiErrorMessage(caught))
        return
      }
    }

    try {
      const assignment = await assignAccountRoleDirect(portal, accountId, {
        roleCode: values.roleCode,
        scopeType: values.scopeType as RoleScopeType,
        organizationUnitId: values.scopeType === "ORG_UNIT" ? Number(values.organizationUnitId) : null,
        workLocationId: values.scopeType === "LOCATION" ? Number(values.workLocationId) : null,
        effectiveFrom: values.effectiveFrom,
        effectiveTo: values.effectiveTo || null,
        reason: values.reason.trim(),
      })
      setResult({ employee, provisioning, assignment })
      toast.success(`Đã cấp role ${assignment.roleName} cho ${employee.fullName}`)
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      if (provisioning) {
        setResult({ employee, provisioning, assignment: null })
        setFormError(
          `Đã tạo tài khoản ${provisioning.account.username} nhưng gán role thất bại: ${apiErrorMessage(caught)}. `
          + "Bạn có thể gán role sau trong trang Vai trò & quyền.",
        )
      } else {
        setFormError(apiErrorMessage(caught))
      }
    }
  }

  async function copyToken() {
    if (!result?.provisioning) return
    try {
      await navigator.clipboard.writeText(result.provisioning.invitation.activationToken)
      setCopiedToken(true)
      toast.success("Đã sao chép mã kích hoạt")
      setTimeout(() => setCopiedToken(false), 1500)
    } catch {
      toast.error("Không thể sao chép, hãy tự copy thủ công")
    }
  }

  async function copyLink() {
    if (!result?.provisioning) return
    try {
      await navigator.clipboard.writeText(`${window.location.origin}/activate/${result.provisioning.invitation.activationToken}`)
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
          <CardTitle>Đã cấp quyền cho {result.employee.fullName}</CardTitle>
          <CardDescription>
            {result.assignment
              ? `Role ${result.assignment.roleName} có hiệu lực từ ${result.assignment.effectiveFrom}.`
              : "Xem lỗi bên dưới để gán role lại sau."}
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <RbacFeedback error={formError} />

          {result.assignment && (
            <div className="flex items-start gap-2 rounded-lg bg-muted px-3 py-2.5 text-sm">
              <ShieldCheck className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
              <div className="min-w-0 flex-1">
                <p className="font-medium">{result.assignment.roleName}</p>
                <p className="text-xs text-muted-foreground">
                  {ROLE_SCOPE_LABELS[result.assignment.scopeType]} · hiệu lực từ {result.assignment.effectiveFrom}
                  {result.assignment.effectiveTo ? ` đến ${result.assignment.effectiveTo}` : ""}
                </p>
              </div>
            </div>
          )}

          {result.provisioning && (
            <>
              <div className="flex items-start gap-2 rounded-lg bg-muted px-3 py-2.5 text-sm">
                <Link2 className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                <div className="min-w-0 flex-1">
                  <p className="text-xs text-muted-foreground">Link kích hoạt — gửi trực tiếp cho nhân sự</p>
                  <code className="mt-0.5 block truncate font-semibold">/activate/{result.provisioning.invitation.activationToken}</code>
                </div>
                <Button type="button" variant="ghost" size="icon-sm" onClick={copyLink} aria-label="Sao chép link kích hoạt">
                  {copiedLink ? <Check /> : <Copy />}
                </Button>
              </div>
              <div className="flex items-start gap-2 rounded-lg bg-muted px-3 py-2.5 text-sm">
                <KeyRound className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                <div className="min-w-0 flex-1">
                  <p className="text-xs text-muted-foreground">Mã kích hoạt (dùng một lần)</p>
                  <code className="mt-0.5 block truncate font-semibold">{result.provisioning.invitation.activationToken}</code>
                </div>
                <Button type="button" variant="ghost" size="icon-sm" onClick={copyToken} aria-label="Sao chép mã kích hoạt">
                  {copiedToken ? <Check /> : <Copy />}
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">
                Hết hạn lúc {new Date(result.provisioning.invitation.expiresAt).toLocaleString("vi-VN")}.
              </p>
            </>
          )}

          <Button type="button" onClick={startOver}>
            <UserPlus /> Cấp quyền cho nhân sự khác
          </Button>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Cấp quyền trực tiếp</CardTitle>
        <CardDescription>
          Gán role cho nhân sự có sẵn ngay lập tức, không cần chờ duyệt. Nếu nhân sự chưa có tài khoản, hệ thống sẽ tạo tài khoản
          và cấp mã kích hoạt để họ tự đặt mật khẩu.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form className="grid gap-4" onSubmit={handleSubmit(onSubmit)}>
          <RbacFeedback error={formError ?? optionsError} />

          <div className="grid gap-1.5">
            <Label htmlFor="grant-employee">Nhân sự</Label>
            <Controller
              control={control}
              name="employeeId"
              render={({ field }) => (
                <Select value={field.value} items={employeeItems} onValueChange={field.onChange} disabled={loadingOptions}>
                  <SelectTrigger id="grant-employee" className="w-full" aria-invalid={!!errors.employeeId}>
                    <SelectValue placeholder={loadingOptions ? "Đang tải..." : "Chọn nhân sự..."} />
                  </SelectTrigger>
                  <SelectContent>
                    {employees.map((employee) => (
                      <SelectItem key={employee.id} value={String(employee.id)}>
                        {employee.fullName}{employee.account ? ` (${employee.account.username})` : " · chưa có tài khoản"}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            {errors.employeeId && <p className="text-xs font-medium text-destructive">{errors.employeeId.message}</p>}
          </div>

          {selectedEmployee && !selectedEmployee.account && (
            <div className="grid gap-1.5">
              <Label htmlFor="grant-username">Tên đăng nhập (tạo tài khoản mới)</Label>
              <Input id="grant-username" placeholder="VD: an.nguyen" aria-invalid={!!errors.username} {...register("username")} />
              {errors.username && <p className="text-xs font-medium text-destructive">{errors.username.message}</p>}
              <p className="text-xs text-muted-foreground">Email đăng nhập sẽ lấy từ email công việc đã có trong hồ sơ.</p>
            </div>
          )}

          {selectedEmployee?.account && (
            <p className="rounded-lg bg-muted px-3 py-2.5 text-xs text-muted-foreground">
              Nhân sự đã có tài khoản <strong>{selectedEmployee.account.username}</strong> ({selectedEmployee.account.status}); role mới sẽ được gán thêm vào tài khoản này.
            </p>
          )}

          <div className="grid gap-1.5">
            <Label htmlFor="grant-role">Role</Label>
            <Controller
              control={control}
              name="roleCode"
              render={({ field }) => (
                <Select value={field.value} items={roleItems} onValueChange={field.onChange} disabled={loadingOptions}>
                  <SelectTrigger id="grant-role" className="w-full" aria-invalid={!!errors.roleCode}>
                    <SelectValue placeholder={loadingOptions ? "Đang tải..." : "Chọn role..."} />
                  </SelectTrigger>
                  <SelectContent>
                    {roles.map((role) => (
                      <SelectItem key={role.code} value={role.code}>{role.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            {errors.roleCode && <p className="text-xs font-medium text-destructive">{errors.roleCode.message}</p>}
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="grant-scope">Phạm vi</Label>
            <Controller
              control={control}
              name="scopeType"
              render={({ field }) => (
                <Select value={field.value} items={scopeItems} onValueChange={field.onChange}>
                  <SelectTrigger id="grant-scope" className="w-full" aria-invalid={!!errors.scopeType}>
                    <SelectValue placeholder="Chọn phạm vi..." />
                  </SelectTrigger>
                  <SelectContent>
                    {ROLE_SCOPE_TYPES.map((scope) => (
                      <SelectItem key={scope} value={scope}>{ROLE_SCOPE_LABELS[scope]}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            {errors.scopeType && <p className="text-xs font-medium text-destructive">{errors.scopeType.message}</p>}
          </div>

          {selectedScopeType === "ORG_UNIT" && (
            <div className="grid gap-1.5">
              <Label htmlFor="grant-org-unit">Đơn vị tổ chức</Label>
              <Controller
                control={control}
                name="organizationUnitId"
                render={({ field }) => (
                  <Select value={field.value} items={orgUnitItems} onValueChange={field.onChange}>
                    <SelectTrigger id="grant-org-unit" className="w-full" aria-invalid={!!errors.organizationUnitId}>
                      <SelectValue placeholder="Chọn đơn vị..." />
                    </SelectTrigger>
                    <SelectContent>
                      {orgUnits.map((unit) => <SelectItem key={unit.id} value={String(unit.id)}>{unit.name}</SelectItem>)}
                    </SelectContent>
                  </Select>
                )}
              />
              {errors.organizationUnitId && <p className="text-xs font-medium text-destructive">{errors.organizationUnitId.message}</p>}
            </div>
          )}

          {selectedScopeType === "LOCATION" && (
            <div className="grid gap-1.5">
              <Label htmlFor="grant-location">Địa điểm làm việc</Label>
              <Controller
                control={control}
                name="workLocationId"
                render={({ field }) => (
                  <Select value={field.value} items={workLocationItems} onValueChange={field.onChange}>
                    <SelectTrigger id="grant-location" className="w-full" aria-invalid={!!errors.workLocationId}>
                      <SelectValue placeholder="Chọn địa điểm..." />
                    </SelectTrigger>
                    <SelectContent>
                      {workLocations.map((location) => <SelectItem key={location.id} value={String(location.id)}>{location.name}</SelectItem>)}
                    </SelectContent>
                  </Select>
                )}
              />
              {errors.workLocationId && <p className="text-xs font-medium text-destructive">{errors.workLocationId.message}</p>}
            </div>
          )}

          <div className="grid grid-cols-2 gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="grant-effective-from">Hiệu lực từ</Label>
              <Input id="grant-effective-from" type="date" aria-invalid={!!errors.effectiveFrom} {...register("effectiveFrom")} />
              {errors.effectiveFrom && <p className="text-xs font-medium text-destructive">{errors.effectiveFrom.message}</p>}
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="grant-effective-to">Hiệu lực đến (tùy chọn)</Label>
              <Input id="grant-effective-to" type="date" aria-invalid={!!errors.effectiveTo} {...register("effectiveTo")} />
              {errors.effectiveTo && <p className="text-xs font-medium text-destructive">{errors.effectiveTo.message}</p>}
            </div>
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="grant-reason">Lý do cấp quyền</Label>
            <Textarea id="grant-reason" rows={2} aria-invalid={!!errors.reason} {...register("reason")} />
            {errors.reason && <p className="text-xs font-medium text-destructive">{errors.reason.message}</p>}
          </div>

          <div>
            <Button type="submit" disabled={isSubmitting || loadingOptions}>
              {isSubmitting && <Loader2 className="animate-spin" />}
              Cấp quyền
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
