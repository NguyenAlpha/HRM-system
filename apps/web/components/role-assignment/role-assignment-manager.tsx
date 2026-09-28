"use client"

import { useEffect, useMemo, useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Check, Loader2, Plus, RefreshCw, X } from "lucide-react"
import { toast } from "sonner"

import { Pagination, RbacFeedback } from "@/components/admin/rbac-controls"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
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
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Textarea } from "@/components/ui/textarea"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { employeeRequest, type EmployeeSummary, type Page as EmployeePage } from "@/lib/employee"
import {
  approveRoleAssignmentRequest,
  cancelRoleAssignmentRequest,
  createRoleAssignmentRequest,
  getAvailableRoles,
  listRoleAssignmentRequests,
  rejectRoleAssignmentRequest,
  ROLE_ASSIGNMENT_STATUS_LABELS,
  ROLE_ASSIGNMENT_STATUSES,
  ROLE_SCOPE_LABELS,
  type Page,
  type RoleAssignmentOption,
  type RoleAssignmentRequest,
  type RoleAssignmentRequestStatus,
  type RoleScopeType,
} from "@/lib/role-assignment"
import {
  getOrganizationUnitOptions,
  getWorkLocationOptions,
  type OrganizationUnitOption,
  type WorkLocationOption,
} from "@/lib/reference"

const ALL_STATUSES = "ALL"

const createRequestSchema = z
  .object({
    accountId: z.string().min(1, "Bắt buộc"),
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

type CreateRequestValues = z.infer<typeof createRequestSchema>

const EMPTY_CREATE_VALUES: CreateRequestValues = {
  accountId: "",
  roleCode: "",
  scopeType: "",
  organizationUnitId: "",
  workLocationId: "",
  effectiveFrom: "",
  effectiveTo: "",
  reason: "",
}

type ActionKind = "approve" | "reject" | "cancel"

function statusBadgeVariant(status: RoleAssignmentRequestStatus): "default" | "secondary" | "outline" | "destructive" {
  if (status === "APPROVED") return "default"
  if (status === "PENDING") return "secondary"
  if (status === "REJECTED") return "destructive"
  return "outline"
}

export function RoleAssignmentManager({ currentAccountId, canRequest, canApprove, onSessionExpired }: {
  currentAccountId: number
  canRequest: boolean
  canApprove: boolean
  onSessionExpired: () => void
}) {
  const [data, setData] = useState<Page<RoleAssignmentRequest> | null>(null)
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<RoleAssignmentRequestStatus | "">("")
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [createOpen, setCreateOpen] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
  const [loadingOptions, setLoadingOptions] = useState(true)
  const [roleOptions, setRoleOptions] = useState<RoleAssignmentOption[]>([])
  const [accountOptions, setAccountOptions] = useState<EmployeeSummary[]>([])
  const [orgUnits, setOrgUnits] = useState<OrganizationUnitOption[]>([])
  const [workLocations, setWorkLocations] = useState<WorkLocationOption[]>([])

  const [actionTarget, setActionTarget] = useState<{ request: RoleAssignmentRequest; action: ActionKind } | null>(null)
  const [actionNote, setActionNote] = useState("")
  const [actionBusy, setActionBusy] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    watch,
    control,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<CreateRequestValues>({ resolver: zodResolver(createRequestSchema), defaultValues: EMPTY_CREATE_VALUES })

  const selectedRoleCode = watch("roleCode")
  const selectedScopeType = watch("scopeType")
  const selectedRole = useMemo(
    () => roleOptions.find((role) => role.roleCode === selectedRoleCode) ?? null,
    [roleOptions, selectedRoleCode],
  )

  const accountItems = useMemo(
    () => Object.fromEntries(accountOptions.map((employee) => [String(employee.account!.id), `${employee.fullName} (${employee.account!.username})`])),
    [accountOptions],
  )
  const roleItems = useMemo(
    () => Object.fromEntries(roleOptions.map((role) => [role.roleCode, `${role.roleName} ${role.requiresApproval ? "· cần duyệt" : "· tự động"}`])),
    [roleOptions],
  )
  const scopeItems = useMemo(
    () => Object.fromEntries((selectedRole?.allowedScopeTypes ?? []).map((scope) => [scope, ROLE_SCOPE_LABELS[scope]])),
    [selectedRole],
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
    listRoleAssignmentRequests(page, statusFilter || undefined)
      .then((result) => {
        if (!active) return
        if (page > 0 && page >= result.totalPages) {
          setPage(Math.max(0, result.totalPages - 1))
          return
        }
        setData(result)
        setLoading(false)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setError(apiErrorMessage(caught))
        setLoading(false)
      })
    return () => { active = false }
  }, [page, statusFilter, revision, onSessionExpired])

  function reload() {
    setLoading(true)
    setError(null)
    setRevision((value) => value + 1)
  }

  function openCreate() {
    setCreateError(null)
    reset(EMPTY_CREATE_VALUES)
    setCreateOpen(true)
    setLoadingOptions(true)

    Promise.all([
      getAvailableRoles(),
      employeeRequest<EmployeePage<EmployeeSummary>>("?page=0&size=100"),
      getOrganizationUnitOptions(),
      getWorkLocationOptions(),
    ])
      .then(([roles, employees, units, locations]) => {
        setRoleOptions(roles)
        setAccountOptions(employees.content.filter((employee) => employee.account !== null))
        setOrgUnits(units)
        setWorkLocations(locations)
      })
      .catch((caught: unknown) => {
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setCreateError(apiErrorMessage(caught))
      })
      .finally(() => setLoadingOptions(false))
  }

  async function onSubmit(values: CreateRequestValues) {
    setCreateError(null)
    try {
      const created = await createRoleAssignmentRequest({
        accountId: Number(values.accountId),
        roleCode: values.roleCode,
        scopeType: values.scopeType as RoleScopeType,
        organizationUnitId: values.scopeType === "ORG_UNIT" ? Number(values.organizationUnitId) : null,
        workLocationId: values.scopeType === "LOCATION" ? Number(values.workLocationId) : null,
        effectiveFrom: values.effectiveFrom,
        effectiveTo: values.effectiveTo || null,
        reason: values.reason.trim(),
      })
      toast.success(
        created.status === "PENDING"
          ? `Đã gửi đề xuất cấp role ${created.roleName}, đang chờ Company Owner duyệt`
          : `Đã cấp role ${created.roleName} cho ${created.employeeName}`,
      )
      setCreateOpen(false)
      setPage(0)
      reload()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setCreateError(apiErrorMessage(caught))
    }
  }

  function openAction(request: RoleAssignmentRequest, action: ActionKind) {
    setActionTarget({ request, action })
    setActionNote("")
    setActionError(null)
  }

  async function submitAction() {
    if (!actionTarget) return
    const { request, action } = actionTarget
    if (action !== "approve" && !actionNote.trim()) {
      setActionError("Bắt buộc nhập lý do")
      return
    }

    setActionBusy(true)
    setActionError(null)
    try {
      if (action === "approve") await approveRoleAssignmentRequest(request.id, actionNote)
      if (action === "reject") await rejectRoleAssignmentRequest(request.id, actionNote)
      if (action === "cancel") await cancelRoleAssignmentRequest(request.id, actionNote)
      toast.success(
        action === "approve" ? "Đã duyệt yêu cầu" : action === "reject" ? "Đã từ chối yêu cầu" : "Đã hủy yêu cầu",
      )
      setActionTarget(null)
      reload()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setActionError(apiErrorMessage(caught))
    } finally {
      setActionBusy(false)
    }
  }

  return (
    <>
      <Card>
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle>Đề xuất cấp role</CardTitle>
            <CardDescription>
              {canApprove
                ? "Xem và duyệt toàn bộ đề xuất cấp role trong doanh nghiệp."
                : "Đề xuất cấp role cho nhân sự đã có tài khoản; role cao cấp cần Company Owner duyệt."}
            </CardDescription>
          </div>
          <div className="flex items-center gap-2">
            <Button type="button" variant="outline" size="sm" onClick={reload} disabled={loading}>
              <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
            </Button>
            {canRequest && (
              <Button type="button" size="sm" onClick={openCreate}>
                <Plus /> Tạo đề xuất
              </Button>
            )}
          </div>
        </CardHeader>
        <CardContent className="grid gap-4">
          <RbacFeedback error={error} />

          <Select
            value={statusFilter || ALL_STATUSES}
            items={{ [ALL_STATUSES]: "Tất cả trạng thái", ...ROLE_ASSIGNMENT_STATUS_LABELS }}
            disabled={loading}
            onValueChange={(value) => {
              setStatusFilter(value === ALL_STATUSES ? "" : (value as RoleAssignmentRequestStatus))
              setPage(0)
              setLoading(true)
              setError(null)
            }}
          >
            <SelectTrigger className="w-52">
              <SelectValue placeholder="Tất cả trạng thái" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_STATUSES}>Tất cả trạng thái</SelectItem>
              {ROLE_ASSIGNMENT_STATUSES.map((status) => (
                <SelectItem key={status} value={status}>{ROLE_ASSIGNMENT_STATUS_LABELS[status]}</SelectItem>
              ))}
            </SelectContent>
          </Select>

          <div className="rounded-xl border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Nhân sự</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead>Phạm vi</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead>Người gửi</TableHead>
                  <TableHead className="text-right">Thao tác</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loading &&
                  Array.from({ length: 5 }).map((_, index) => (
                    <TableRow key={index}>
                      {Array.from({ length: 6 }).map((__, cell) => (
                        <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
                      ))}
                    </TableRow>
                  ))}

                {!loading && data?.content.map((request) => {
                  const isOwnRequest = request.requestedByAccountId === currentAccountId
                  return (
                    <TableRow key={request.id}>
                      <TableCell>
                        <span className="font-medium">{request.employeeName}</span>
                        <span className="block text-xs text-muted-foreground"><code>{request.employeeCode}</code></span>
                      </TableCell>
                      <TableCell>
                        <span className="font-medium">{request.roleName}</span>
                        <span className="block text-xs text-muted-foreground">{request.roleCode}</span>
                      </TableCell>
                      <TableCell className="text-sm">
                        {ROLE_SCOPE_LABELS[request.scopeType]}
                        {request.organizationUnitName && <span className="block text-xs text-muted-foreground">{request.organizationUnitName}</span>}
                        {request.workLocationName && <span className="block text-xs text-muted-foreground">{request.workLocationName}</span>}
                      </TableCell>
                      <TableCell>
                        <Badge variant={statusBadgeVariant(request.status)}>{ROLE_ASSIGNMENT_STATUS_LABELS[request.status]}</Badge>
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">{request.requestedByUsername}</TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-1.5">
                          {canApprove && request.status === "PENDING" && (
                            <>
                              <Button type="button" size="sm" onClick={() => openAction(request, "approve")}>
                                <Check /> Duyệt
                              </Button>
                              <Button type="button" variant="outline" size="sm" onClick={() => openAction(request, "reject")}>
                                <X /> Từ chối
                              </Button>
                            </>
                          )}
                          {!canApprove && isOwnRequest && request.status === "PENDING" && (
                            <Button type="button" variant="outline" size="sm" onClick={() => openAction(request, "cancel")}>
                              Hủy đề xuất
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}

                {!loading && data?.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} className="h-24 text-center text-muted-foreground">
                      Chưa có đề xuất nào.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>

          <Pagination page={page} totalPages={data?.totalPages ?? 0} totalElements={data?.totalElements ?? 0} disabled={loading}
            onChange={(next) => { setLoading(true); setError(null); setPage(next) }} />
        </CardContent>
      </Card>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="sm:max-w-lg">
          <form onSubmit={handleSubmit(onSubmit)}>
            <DialogHeader>
              <DialogTitle>Tạo đề xuất cấp role</DialogTitle>
              <DialogDescription>
                Role có chính sách &ldquo;Chờ duyệt&rdquo; sẽ tạo yêu cầu PENDING chờ Company Owner phê duyệt; role tự động duyệt sẽ có hiệu lực ngay.
              </DialogDescription>
            </DialogHeader>

            <div className="mt-4 grid max-h-[65vh] gap-4 overflow-y-auto pr-1">
              <RbacFeedback error={createError} />

              <div className="grid gap-1.5">
                <Label htmlFor="req-account">Nhân sự (đã có tài khoản)</Label>
                <Controller
                  control={control}
                  name="accountId"
                  render={({ field }) => (
                    <Select value={field.value} items={accountItems} onValueChange={field.onChange} disabled={loadingOptions}>
                      <SelectTrigger id="req-account" className="w-full" aria-invalid={!!errors.accountId}>
                        <SelectValue placeholder={loadingOptions ? "Đang tải..." : "Chọn nhân sự..."} />
                      </SelectTrigger>
                      <SelectContent>
                        {accountOptions.map((employee) => (
                          <SelectItem key={employee.account!.id} value={String(employee.account!.id)}>
                            {employee.fullName} ({employee.account!.username})
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
                {errors.accountId && <p className="text-xs font-medium text-destructive">{errors.accountId.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="req-role">Role đề xuất</Label>
                <Controller
                  control={control}
                  name="roleCode"
                  render={({ field }) => (
                    <Select
                      value={field.value}
                      items={roleItems}
                      onValueChange={(value) => {
                        field.onChange(value)
                        const role = roleOptions.find((item) => item.roleCode === value)
                        setValue("scopeType", role?.allowedScopeTypes[0] ?? "")
                      }}
                      disabled={loadingOptions}
                    >
                      <SelectTrigger id="req-role" className="w-full" aria-invalid={!!errors.roleCode}>
                        <SelectValue placeholder={loadingOptions ? "Đang tải..." : "Chọn role..."} />
                      </SelectTrigger>
                      <SelectContent>
                        {roleOptions.map((role) => (
                          <SelectItem key={role.roleCode} value={role.roleCode}>
                            {role.roleName} {role.requiresApproval ? "· cần duyệt" : "· tự động"}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
                {errors.roleCode && <p className="text-xs font-medium text-destructive">{errors.roleCode.message}</p>}
                {selectedRole?.roleDescription && (
                  <p className="text-xs text-muted-foreground">{selectedRole.roleDescription}</p>
                )}
              </div>

              {selectedRole && (
                <div className="grid gap-1.5">
                  <Label htmlFor="req-scope">Phạm vi</Label>
                  <Controller
                    control={control}
                    name="scopeType"
                    render={({ field }) => (
                      <Select value={field.value} items={scopeItems} onValueChange={field.onChange}>
                        <SelectTrigger id="req-scope" className="w-full" aria-invalid={!!errors.scopeType}>
                          <SelectValue placeholder="Chọn phạm vi..." />
                        </SelectTrigger>
                        <SelectContent>
                          {selectedRole.allowedScopeTypes.map((scope) => (
                            <SelectItem key={scope} value={scope}>{ROLE_SCOPE_LABELS[scope]}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  />
                  {errors.scopeType && <p className="text-xs font-medium text-destructive">{errors.scopeType.message}</p>}
                </div>
              )}

              {selectedScopeType === "ORG_UNIT" && (
                <div className="grid gap-1.5">
                  <Label htmlFor="req-org-unit">Đơn vị tổ chức</Label>
                  <Controller
                    control={control}
                    name="organizationUnitId"
                    render={({ field }) => (
                      <Select value={field.value} items={orgUnitItems} onValueChange={field.onChange}>
                        <SelectTrigger id="req-org-unit" className="w-full" aria-invalid={!!errors.organizationUnitId}>
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
                  <Label htmlFor="req-location">Địa điểm làm việc</Label>
                  <Controller
                    control={control}
                    name="workLocationId"
                    render={({ field }) => (
                      <Select value={field.value} items={workLocationItems} onValueChange={field.onChange}>
                        <SelectTrigger id="req-location" className="w-full" aria-invalid={!!errors.workLocationId}>
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
                  <Label htmlFor="req-effective-from">Hiệu lực từ</Label>
                  <Input id="req-effective-from" type="date" aria-invalid={!!errors.effectiveFrom} {...register("effectiveFrom")} />
                  {errors.effectiveFrom && <p className="text-xs font-medium text-destructive">{errors.effectiveFrom.message}</p>}
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="req-effective-to">Hiệu lực đến (tùy chọn)</Label>
                  <Input id="req-effective-to" type="date" aria-invalid={!!errors.effectiveTo} {...register("effectiveTo")} />
                  {errors.effectiveTo && <p className="text-xs font-medium text-destructive">{errors.effectiveTo.message}</p>}
                </div>
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="req-reason">Lý do đề xuất</Label>
                <Textarea id="req-reason" rows={2} aria-invalid={!!errors.reason} {...register("reason")} />
                {errors.reason && <p className="text-xs font-medium text-destructive">{errors.reason.message}</p>}
              </div>
            </div>

            <DialogFooter>
              <Button type="submit" disabled={isSubmitting || loadingOptions}>
                {isSubmitting && <Loader2 className="animate-spin" />}
                Gửi đề xuất
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={actionTarget !== null} onOpenChange={(open) => { if (!open) setActionTarget(null) }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>
              {actionTarget?.action === "approve" && `Duyệt đề xuất role ${actionTarget.request.roleName}?`}
              {actionTarget?.action === "reject" && `Từ chối đề xuất role ${actionTarget.request.roleName}?`}
              {actionTarget?.action === "cancel" && `Hủy đề xuất role ${actionTarget.request.roleName}?`}
            </DialogTitle>
            <DialogDescription>
              Cho {actionTarget?.request.employeeName} ({actionTarget?.request.employeeCode})
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-3">
            <RbacFeedback error={actionError} />
            <div className="grid gap-1.5">
              <Label htmlFor="action-note">
                {actionTarget?.action === "approve" ? "Ghi chú (tùy chọn)" : "Lý do (bắt buộc)"}
              </Label>
              <Textarea
                id="action-note"
                rows={3}
                value={actionNote}
                onChange={(event) => setActionNote(event.target.value)}
              />
            </div>
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant={actionTarget?.action === "approve" ? "default" : "destructive"}
              disabled={actionBusy}
              onClick={submitAction}
            >
              {actionBusy && <Loader2 className="animate-spin" />}
              {actionTarget?.action === "approve" && "Duyệt"}
              {actionTarget?.action === "reject" && "Từ chối"}
              {actionTarget?.action === "cancel" && "Hủy đề xuất"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
