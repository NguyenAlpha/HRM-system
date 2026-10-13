"use client"

import { useEffect, useMemo, useState } from "react"

import { ErrorAlert } from "@/components/common/list-controls"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { getAccountPermissions, type Account, type AccountPermissionGrant } from "@/lib/account"
import type { Portal } from "@/lib/auth/types"
import { listAccountRoleAssignments, ROLE_SCOPE_LABELS, type AccountRoleAssignment } from "@/lib/role-assignment"

type AssignmentState = "active" | "scheduled" | "expired" | "revoked"

const ASSIGNMENT_STATES: Record<AssignmentState, { label: string; variant: "default" | "secondary" | "outline" }> = {
  active: { label: "Đang hiệu lực", variant: "default" },
  scheduled: { label: "Chưa hiệu lực", variant: "secondary" },
  expired: { label: "Đã hết hạn", variant: "outline" },
  revoked: { label: "Đã thu hồi", variant: "outline" },
}

function todayIso() {
  const date = new Date()
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

function assignmentState(assignment: AccountRoleAssignment, today: string): AssignmentState {
  if (assignment.revokedAt) return "revoked"
  if (assignment.effectiveTo !== null && assignment.effectiveTo < today) return "expired"
  if (assignment.effectiveFrom > today) return "scheduled"
  return "active"
}

function scopeLabel(assignment: AccountRoleAssignment) {
  const label = ROLE_SCOPE_LABELS[assignment.scopeType]
  if (assignment.scopeType === "ORG_UNIT") return `${label} #${assignment.organizationUnitId}`
  if (assignment.scopeType === "LOCATION") return `${label} #${assignment.workLocationId}`
  return label
}

/** Read-only view of what an account can do: its role assignments and today's permissions. */
export function AccountDetailSheet({ portal, account, onClose, onSessionExpired }: {
  portal: Portal
  account: Account
  onClose: () => void
  onSessionExpired: () => void
}) {
  const [assignments, setAssignments] = useState<AccountRoleAssignment[] | null>(null)
  const [permissions, setPermissions] = useState<AccountPermissionGrant[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [showPast, setShowPast] = useState(false)
  const today = todayIso()

  useEffect(() => {
    let active = true
    Promise.all([listAccountRoleAssignments(portal, account.id), getAccountPermissions(portal, account.id)])
      .then(([loadedAssignments, loadedPermissions]) => {
        if (!active) return
        setAssignments(loadedAssignments)
        setPermissions(loadedPermissions)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setError(apiErrorMessage(caught))
      })
    return () => { active = false }
  }, [portal, account.id, onSessionExpired])

  const current = assignments?.filter((item) => ["active", "scheduled"].includes(assignmentState(item, today))) ?? []
  const past = assignments?.filter((item) => ["expired", "revoked"].includes(assignmentState(item, today))) ?? []
  const modules = useMemo(() => {
    const groups = new Map<string, AccountPermissionGrant[]>()
    for (const permission of permissions ?? []) {
      groups.set(permission.module, [...(groups.get(permission.module) ?? []), permission])
    }
    return [...groups.entries()]
  }, [permissions])
  const loading = !error && (assignments === null || permissions === null)

  return (
    <Sheet open onOpenChange={(open) => { if (!open) onClose() }}>
      <SheetContent className="w-full overflow-y-auto data-[side=right]:sm:max-w-xl">
        <SheetHeader className="pr-12">
          <SheetTitle>{account.username}</SheetTitle>
          <SheetDescription>
            {account.email}
            {account.employeeFullName && <> · {account.employeeFullName} ({account.employeeCode})</>}
          </SheetDescription>
        </SheetHeader>

        <div className="grid gap-6 px-4 pb-6">
          <ErrorAlert error={error} />

          <section className="grid gap-3">
            <h3 className="font-medium">Role đang giữ</h3>
            {loading && <Skeleton className="h-16 w-full" />}
            {!loading && assignments && current.length === 0 && (
              <p className="text-muted-foreground">Account không có role nào đang hiệu lực.</p>
            )}
            {[...current, ...(showPast ? past : [])].map((assignment) => {
              const state = ASSIGNMENT_STATES[assignmentState(assignment, today)]
              return (
                <div key={assignment.id} className="rounded-lg border px-3 py-2">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-medium">{assignment.roleName}</span>
                    <Badge variant={state.variant}>{state.label}</Badge>
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {scopeLabel(assignment)} · từ {assignment.effectiveFrom}
                    {assignment.effectiveTo ? ` đến ${assignment.effectiveTo}` : ", không thời hạn"}
                  </p>
                  {assignment.revokedAt && assignment.revocationReason && (
                    <p className="text-xs text-muted-foreground">Lý do thu hồi: {assignment.revocationReason}</p>
                  )}
                </div>
              )
            })}
            {past.length > 0 && (
              <Button type="button" variant="ghost" size="sm" className="justify-self-start" onClick={() => setShowPast((value) => !value)}>
                {showPast ? "Ẩn role đã hết hạn hoặc thu hồi" : `Hiện role đã hết hạn hoặc thu hồi (${past.length})`}
              </Button>
            )}
          </section>

          <section className="grid gap-3">
            <h3 className="font-medium">Permission hiệu lực{permissions && ` (${permissions.length})`}</h3>
            <p className="text-xs text-muted-foreground">
              Quyền account có hôm nay: quyền của các role đang hiệu lực, cộng và trừ ngoại lệ quyền.
            </p>
            {loading && <Skeleton className="h-32 w-full" />}
            {!loading && permissions?.length === 0 && (
              <p className="text-muted-foreground">Account không có permission nào.</p>
            )}
            {modules.map(([module, grants]) => (
              <div key={module} className="rounded-lg border">
                <div className="border-b px-3 py-2"><Badge variant="secondary">{module}</Badge></div>
                <ul className="divide-y">
                  {grants.map((permission) => (
                    <li key={permission.code} className="flex flex-wrap items-start justify-between gap-2 px-3 py-2">
                      <span>
                        {permission.name}
                        <code className="block text-xs text-muted-foreground">{permission.code}</code>
                      </span>
                      <span className="flex flex-wrap justify-end gap-1">
                        {permission.grantedBy.map((source) => (
                          <Badge key={source.assignmentId} variant={source.viaOverride ? "default" : "outline"}>
                            {source.roleName}{source.viaOverride && " · ngoại lệ"}
                          </Badge>
                        ))}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </section>
        </div>
      </SheetContent>
    </Sheet>
  )
}
