"use client"

import { useEffect, useMemo, useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Loader2, Plus, RefreshCw, ShieldMinus } from "lucide-react"
import { toast } from "sonner"

import { RbacFeedback } from "@/components/admin/rbac-controls"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Textarea } from "@/components/ui/textarea"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import type { Portal } from "@/lib/auth/types"
import { employeeRequest, type EmployeeSummary, type Page as EmployeePage } from "@/lib/employee"
import {
  createPermissionOverride,
  listAccountRoleAssignments,
  listPermissionOverrideOptions,
  listPermissionOverrides,
  PERMISSION_OVERRIDE_EFFECT_LABELS,
  PERMISSION_OVERRIDE_STATUS_LABELS,
  revokePermissionOverride,
  ROLE_SCOPE_LABELS,
  type AccountPermissionOverride,
  type AccountRoleAssignment,
  type PermissionOverrideOption,
} from "@/lib/role-assignment"

const overrideSchema = z.object({
  permissionId: z.string().min(1, "Bắt buộc"),
  effectiveFrom: z.string().min(1, "Bắt buộc"),
  effectiveTo: z.string().optional(),
  reason: z.string().trim().min(1, "Bắt buộc").max(500, "Tối đa 500 ký tự"),
}).refine((data) => !data.effectiveTo || data.effectiveTo >= data.effectiveFrom, {
  message: "Ngày kết thúc không được trước ngày bắt đầu",
  path: ["effectiveTo"],
})

type OverrideValues = z.infer<typeof overrideSchema>

function todayIso() {
  const date = new Date()
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

function statusVariant(status: AccountPermissionOverride["status"]) {
  if (status === "ACTIVE") return "default" as const
  if (status === "REVOKED") return "destructive" as const
  return "outline" as const
}

export function PermissionOverrideManager({ portal, onSessionExpired }: {
  portal: Portal
  onSessionExpired: () => void
}) {
  const [employees, setEmployees] = useState<EmployeeSummary[]>([])
  const [assignments, setAssignments] = useState<AccountRoleAssignment[]>([])
  const [overrides, setOverrides] = useState<AccountPermissionOverride[]>([])
  const [permissionOptions, setPermissionOptions] = useState<PermissionOverrideOption[]>([])
  const [accountId, setAccountId] = useState("")
  const [assignmentId, setAssignmentId] = useState("")
  const [loadingEmployees, setLoadingEmployees] = useState(true)
  const [loadingAssignments, setLoadingAssignments] = useState(false)
  const [loadingOverrides, setLoadingOverrides] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [overrideToRevoke, setOverrideToRevoke] = useState<AccountPermissionOverride | null>(null)
  const [revocationReason, setRevocationReason] = useState("")
  const [revoking, setRevoking] = useState(false)

  const {
    register,
    handleSubmit,
    control,
    reset,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<OverrideValues>({
    resolver: zodResolver(overrideSchema),
    defaultValues: { permissionId: "", effectiveFrom: todayIso(), effectiveTo: "", reason: "" },
  })

  const selectedPermissionId = watch("permissionId")
  const selectedEffectiveFrom = watch("effectiveFrom")
  const selectedPermission = permissionOptions.find((option) => String(option.permissionId) === selectedPermissionId) ?? null
  const selectedAssignment = assignments.find((assignment) => String(assignment.id) === assignmentId) ?? null
  const canCreateOverride = selectedAssignment !== null
    && selectedAssignment.revokedAt === null
    && (selectedAssignment.effectiveTo === null || selectedAssignment.effectiveTo >= todayIso())
  const minimumEffectiveFrom = selectedAssignment?.effectiveFrom && selectedAssignment.effectiveFrom > todayIso()
    ? selectedAssignment.effectiveFrom
    : todayIso()

  const employeeItems = useMemo(
    () => Object.fromEntries(employees
      .filter((employee) => employee.account)
      .map((employee) => [String(employee.account!.id), `${employee.fullName} (${employee.account!.username})`])),
    [employees],
  )
  const assignmentItems = useMemo(
    () => Object.fromEntries(assignments.map((assignment) => [
      String(assignment.id),
      `${assignment.roleName} · ${ROLE_SCOPE_LABELS[assignment.scopeType]}${assignment.revokedAt ? " · đã thu hồi" : ""}`,
    ])),
    [assignments],
  )
  const permissionItems = useMemo(
    () => Object.fromEntries(permissionOptions.map((permission) => [
      String(permission.permissionId),
      `${permission.permissionName} · ${PERMISSION_OVERRIDE_EFFECT_LABELS[permission.effect]}`,
    ])),
    [permissionOptions],
  )

  useEffect(() => {
    let active = true
    employeeRequest<EmployeePage<EmployeeSummary>>(portal, "?page=0&size=100")
      .then((page) => {
        if (active) setEmployees(page.content.filter((employee) => employee.account))
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setError(apiErrorMessage(caught))
      })
      .finally(() => { if (active) setLoadingEmployees(false) })
    return () => { active = false }
  }, [portal, onSessionExpired])

  async function selectAccount(nextAccountId: string | null) {
    const value = nextAccountId ?? ""
    setAccountId(value)
    setAssignmentId("")
    setAssignments([])
    setOverrides([])
    setPermissionOptions([])
    setError(null)
    if (!value) return

    setLoadingAssignments(true)
    try {
      setAssignments(await listAccountRoleAssignments(portal, Number(value)))
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setError(apiErrorMessage(caught))
    } finally {
      setLoadingAssignments(false)
    }
  }

  async function selectAssignment(nextAssignmentId: string | null) {
    const value = nextAssignmentId ?? ""
    setAssignmentId(value)
    setOverrides([])
    setPermissionOptions([])
    setError(null)
    if (!value || !accountId) return

    const assignment = assignments.find((candidate) => String(candidate.id) === value)
    reset({
      permissionId: "",
      effectiveFrom: assignment && assignment.effectiveFrom > todayIso() ? assignment.effectiveFrom : todayIso(),
      effectiveTo: assignment?.effectiveTo ?? "",
      reason: "",
    })
    setLoadingOverrides(true)
    try {
      const [history, options] = await Promise.all([
        listPermissionOverrides(portal, Number(accountId), Number(value)),
        listPermissionOverrideOptions(portal, Number(accountId), Number(value)),
      ])
      setOverrides(history)
      setPermissionOptions(options)
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setError(apiErrorMessage(caught))
    } finally {
      setLoadingOverrides(false)
    }
  }

  async function refreshOverrides() {
    if (!accountId || !assignmentId) return
    setLoadingOverrides(true)
    setError(null)
    try {
      const [history, options] = await Promise.all([
        listPermissionOverrides(portal, Number(accountId), Number(assignmentId)),
        listPermissionOverrideOptions(portal, Number(accountId), Number(assignmentId)),
      ])
      setOverrides(history)
      setPermissionOptions(options)
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setError(apiErrorMessage(caught))
    } finally {
      setLoadingOverrides(false)
    }
  }

  async function onSubmit(values: OverrideValues) {
    if (!accountId || !assignmentId || !selectedPermission) return
    setError(null)
    try {
      await createPermissionOverride(portal, Number(accountId), Number(assignmentId), {
        permissionId: Number(values.permissionId),
        effect: selectedPermission.effect,
        effectiveFrom: values.effectiveFrom,
        effectiveTo: values.effectiveTo || null,
        reason: values.reason.trim(),
      })
      toast.success(`Đã tạo ngoại lệ ${PERMISSION_OVERRIDE_EFFECT_LABELS[selectedPermission.effect].toLowerCase()}`)
      reset({
        permissionId: "",
        effectiveFrom: selectedAssignment && selectedAssignment.effectiveFrom > todayIso()
          ? selectedAssignment.effectiveFrom
          : todayIso(),
        effectiveTo: selectedAssignment?.effectiveTo ?? "",
        reason: "",
      })
      await refreshOverrides()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setError(apiErrorMessage(caught))
    }
  }

  async function confirmRevoke() {
    if (!overrideToRevoke || !accountId || !assignmentId || !revocationReason.trim()) return
    setRevoking(true)
    try {
      await revokePermissionOverride(
        portal,
        Number(accountId),
        Number(assignmentId),
        overrideToRevoke.id,
        revocationReason.trim(),
      )
      toast.success("Đã thu hồi ngoại lệ quyền")
      setOverrideToRevoke(null)
      setRevocationReason("")
      await refreshOverrides()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setError(apiErrorMessage(caught))
    } finally {
      setRevoking(false)
    }
  }

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle>Ngoại lệ quyền tài khoản</CardTitle>
          <CardDescription>
            Cấp thêm hoặc thu hồi một permission trên đúng role assignment và phạm vi được chọn.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-5">
          <RbacFeedback error={error} />

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="grid gap-1.5">
              <Label htmlFor="override-account">Nhân sự / tài khoản</Label>
              <Select value={accountId} items={employeeItems} onValueChange={selectAccount} disabled={loadingEmployees}>
                <SelectTrigger id="override-account" className="w-full">
                  <SelectValue placeholder={loadingEmployees ? "Đang tải..." : "Chọn tài khoản..."} />
                </SelectTrigger>
                <SelectContent>
                  {employees.map((employee) => employee.account && (
                    <SelectItem key={employee.account.id} value={String(employee.account.id)}>
                      {employee.fullName} ({employee.account.username})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="override-assignment">Role assignment</Label>
              <Select value={assignmentId} items={assignmentItems} onValueChange={selectAssignment} disabled={!accountId || loadingAssignments}>
                <SelectTrigger id="override-assignment" className="w-full">
                  <SelectValue placeholder={loadingAssignments ? "Đang tải..." : "Chọn role assignment..."} />
                </SelectTrigger>
                <SelectContent>
                  {assignments.map((assignment) => (
                    <SelectItem key={assignment.id} value={String(assignment.id)}>
                      {assignment.roleName} · {ROLE_SCOPE_LABELS[assignment.scopeType]}{assignment.revokedAt ? " · đã thu hồi" : ""}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {selectedAssignment && (
            <p className="rounded-lg bg-muted px-3 py-2.5 text-xs text-muted-foreground">
              Ngoại lệ chỉ áp dụng cho role <strong>{selectedAssignment.roleName}</strong> trong phạm vi
              {" "}<strong>{ROLE_SCOPE_LABELS[selectedAssignment.scopeType]}</strong>. Permission vẫn có thể được cấp từ role assignment khác.
            </p>
          )}

          {canCreateOverride && (
            <form className="grid gap-4 rounded-xl border p-4" onSubmit={handleSubmit(onSubmit)}>
              <div className="flex items-center gap-2 font-medium"><Plus className="size-4" /> Thêm ngoại lệ</div>
              <div className="grid gap-1.5">
                <Label htmlFor="override-permission">Permission</Label>
                <Controller
                  control={control}
                  name="permissionId"
                  render={({ field }) => (
                    <Select value={field.value} items={permissionItems} onValueChange={field.onChange} disabled={loadingOverrides}>
                      <SelectTrigger id="override-permission" className="w-full" aria-invalid={!!errors.permissionId}>
                        <SelectValue placeholder={loadingOverrides ? "Đang tải..." : "Chọn permission..."} />
                      </SelectTrigger>
                      <SelectContent>
                        {permissionOptions.map((permission) => (
                          <SelectItem key={permission.permissionId} value={String(permission.permissionId)}>
                            {permission.permissionName} · {PERMISSION_OVERRIDE_EFFECT_LABELS[permission.effect]}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
                {errors.permissionId && <p className="text-xs font-medium text-destructive">{errors.permissionId.message}</p>}
                {selectedPermission && (
                  <p className="text-xs text-muted-foreground">
                    <code>{selectedPermission.permissionCode}</code> · thao tác: {PERMISSION_OVERRIDE_EFFECT_LABELS[selectedPermission.effect]}
                  </p>
                )}
              </div>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div className="grid gap-1.5">
                  <Label htmlFor="override-from">Hiệu lực từ</Label>
                  <Input
                    id="override-from"
                    type="date"
                    min={minimumEffectiveFrom}
                    max={selectedAssignment?.effectiveTo ?? undefined}
                    aria-invalid={!!errors.effectiveFrom}
                    {...register("effectiveFrom")}
                  />
                  {errors.effectiveFrom && <p className="text-xs font-medium text-destructive">{errors.effectiveFrom.message}</p>}
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="override-to">Hiệu lực đến</Label>
                  <Input
                    id="override-to"
                    type="date"
                    min={selectedEffectiveFrom || minimumEffectiveFrom}
                    max={selectedAssignment?.effectiveTo ?? undefined}
                    aria-invalid={!!errors.effectiveTo}
                    {...register("effectiveTo")}
                  />
                  {errors.effectiveTo && <p className="text-xs font-medium text-destructive">{errors.effectiveTo.message}</p>}
                </div>
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="override-reason">Lý do</Label>
                <Textarea id="override-reason" rows={2} aria-invalid={!!errors.reason} {...register("reason")} />
                {errors.reason && <p className="text-xs font-medium text-destructive">{errors.reason.message}</p>}
              </div>

              <div>
                <Button type="submit" disabled={isSubmitting || loadingOverrides || !selectedPermission}>
                  {isSubmitting && <Loader2 className="animate-spin" />}
                  Tạo ngoại lệ
                </Button>
              </div>
            </form>
          )}

          {assignmentId && (
            <div className="grid gap-3">
              <div className="flex items-center justify-between gap-3">
                <h3 className="font-medium">Lịch sử ngoại lệ</h3>
                <Button type="button" variant="outline" size="sm" onClick={() => { void refreshOverrides() }} disabled={loadingOverrides}>
                  <RefreshCw className={loadingOverrides ? "animate-spin" : ""} /> Tải lại
                </Button>
              </div>
              <div className="rounded-xl border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Permission</TableHead>
                      <TableHead>Hiệu ứng</TableHead>
                      <TableHead>Hiệu lực</TableHead>
                      <TableHead>Trạng thái</TableHead>
                      <TableHead>Audit</TableHead>
                      <TableHead className="text-right">Thao tác</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {overrides.map((permissionOverride) => (
                      <TableRow key={permissionOverride.id}>
                        <TableCell>
                          <span className="font-medium">{permissionOverride.permissionName}</span>
                          <code className="block text-xs text-muted-foreground">{permissionOverride.permissionCode}</code>
                        </TableCell>
                        <TableCell>
                          <Badge variant={permissionOverride.effect === "GRANT" ? "default" : "outline"}>
                            {PERMISSION_OVERRIDE_EFFECT_LABELS[permissionOverride.effect]}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-sm">
                          {permissionOverride.effectiveFrom} → {permissionOverride.effectiveTo ?? "Không thời hạn"}
                        </TableCell>
                        <TableCell>
                          <Badge variant={statusVariant(permissionOverride.status)}>
                            {PERMISSION_OVERRIDE_STATUS_LABELS[permissionOverride.status]}
                          </Badge>
                        </TableCell>
                        <TableCell className="max-w-72 text-xs">
                          <p><span className="text-muted-foreground">Tạo bởi:</span> {permissionOverride.grantedByUsername}</p>
                          <p className="break-words text-muted-foreground">{permissionOverride.reason}</p>
                          {permissionOverride.revokedByUsername && (
                            <>
                              <p className="mt-1"><span className="text-muted-foreground">Thu hồi bởi:</span> {permissionOverride.revokedByUsername}</p>
                              <p className="break-words text-muted-foreground">{permissionOverride.revocationReason}</p>
                            </>
                          )}
                        </TableCell>
                        <TableCell className="text-right">
                          {(permissionOverride.status === "ACTIVE" || permissionOverride.status === "SCHEDULED") && (
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon-sm"
                              aria-label={`Thu hồi ${permissionOverride.permissionCode}`}
                              onClick={() => { setOverrideToRevoke(permissionOverride); setRevocationReason("") }}
                            >
                              <ShieldMinus />
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                    {!loadingOverrides && overrides.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={6} className="h-20 text-center text-muted-foreground">Chưa có ngoại lệ quyền.</TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <AlertDialog open={overrideToRevoke !== null} onOpenChange={(open) => { if (!open && !revoking) setOverrideToRevoke(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Thu hồi ngoại lệ quyền?</AlertDialogTitle>
            <AlertDialogDescription>
              Ngoại lệ của permission {overrideToRevoke?.permissionCode} sẽ ngừng hiệu lực và vẫn được giữ trong lịch sử.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="grid gap-1.5">
            <Label htmlFor="override-revocation-reason">Lý do thu hồi</Label>
            <Textarea
              id="override-revocation-reason"
              rows={3}
              value={revocationReason}
              onChange={(event) => setRevocationReason(event.target.value)}
            />
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={revoking}>Hủy</AlertDialogCancel>
            <AlertDialogAction
              disabled={revoking || !revocationReason.trim()}
              onClick={(event) => { event.preventDefault(); void confirmRevoke() }}
            >
              {revoking && <Loader2 className="animate-spin" />}
              Thu hồi
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
