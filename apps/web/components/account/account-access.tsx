"use client"

import { useMemo } from "react"

import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import type { AccountPermissionGrant } from "@/lib/account"
import { ROLE_SCOPE_LABELS, type AccountRoleAssignment } from "@/lib/role-assignment"
import { formatDate } from "@/lib/format-date"

export type AssignmentState = "active" | "scheduled" | "expired" | "revoked"

export const ASSIGNMENT_STATES: Record<AssignmentState, { label: string; variant: "default" | "secondary" | "outline" }> = {
  active: { label: "Đang hiệu lực", variant: "secondary" },
  scheduled: { label: "Chưa hiệu lực", variant: "secondary" },
  expired: { label: "Đã hết hạn", variant: "outline" },
  revoked: { label: "Đã thu hồi", variant: "outline" },
}

// The API refuses to revoke these: EMPLOYEE is automatic, the other two have their own workflows.
const NON_REVOCABLE_ROLES = ["EMPLOYEE", "COMPANY_OWNER", "SYSTEM_ADMIN"]

export function todayIso() {
  const date = new Date()
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, "0")
  const day = String(date.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

export function assignmentState(assignment: AccountRoleAssignment, today: string): AssignmentState {
  if (assignment.revokedAt) return "revoked"
  if (assignment.effectiveTo !== null && assignment.effectiveTo < today) return "expired"
  if (assignment.effectiveFrom > today) return "scheduled"
  return "active"
}

/** A current or scheduled assignment of a role the API lets an admin take back. */
export function isRevocable(assignment: AccountRoleAssignment, today: string) {
  const state = assignmentState(assignment, today)
  return (state === "active" || state === "scheduled") && !NON_REVOCABLE_ROLES.includes(assignment.roleCode)
}

/** True when some held role could be revoked; the list endpoint only gives role codes, not assignments. */
export function holdsRevocableRole(roleCodes: string[]) {
  return roleCodes.some((code) => !NON_REVOCABLE_ROLES.includes(code))
}

export function scopeLabel(assignment: AccountRoleAssignment) {
  const label = ROLE_SCOPE_LABELS[assignment.scopeType]
  if (assignment.scopeType === "ORG_UNIT") return `${label} #${assignment.organizationUnitId}`
  if (assignment.scopeType === "LOCATION") return `${label} #${assignment.workLocationId}`
  return label
}

export function validityLabel(assignment: AccountRoleAssignment) {
  return `từ ${formatDate(assignment.effectiveFrom)}${assignment.effectiveTo ? ` đến ${formatDate(assignment.effectiveTo)}` : ", không thời hạn"}`
}

/** Today's permissions grouped by module, each tagged with the roles (or overrides) that give it. */
export function AccountPermissionList({ permissions, loading }: {
  permissions: AccountPermissionGrant[] | null
  loading: boolean
}) {
  const modules = useMemo(() => {
    const groups = new Map<string, AccountPermissionGrant[]>()
    for (const permission of permissions ?? []) {
      groups.set(permission.module, [...(groups.get(permission.module) ?? []), permission])
    }
    return [...groups.entries()]
  }, [permissions])

  return (
    <>
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
                    <Badge key={source.assignmentId} variant={source.viaOverride ? "secondary" : "outline"}>
                      {source.roleName}{source.viaOverride && " · ngoại lệ"}
                    </Badge>
                  ))}
                </span>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </>
  )
}
