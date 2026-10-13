"use client"

import Link from "next/link"
import { useEffect, useState } from "react"
import { ExternalLink } from "lucide-react"

import {
  AccountPermissionList,
  ASSIGNMENT_STATES,
  assignmentState,
  scopeLabel,
  todayIso,
  validityLabel,
} from "@/components/account/account-access"
import { ErrorAlert } from "@/components/common/list-controls"
import { Badge } from "@/components/ui/badge"
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
import { Skeleton } from "@/components/ui/skeleton"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { getAccountPermissions, type Account, type AccountPermissionGrant } from "@/lib/account"
import { PORTAL_CONFIG } from "@/lib/auth/config"
import type { Portal } from "@/lib/auth/types"
import { listAccountRoleAssignments, type AccountRoleAssignment } from "@/lib/role-assignment"

/** Read-only view of what an account can do: its role assignments and today's permissions. */
export function AccountDetailDialog({ portal, account, onClose, onSessionExpired }: {
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
  const loading = !error && (assignments === null || permissions === null)

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader className="pr-6">
          <DialogTitle>{account.username}</DialogTitle>
          <DialogDescription>
            {account.email}
            {account.employeeFullName && <> · {account.employeeFullName} ({account.employeeCode})</>}
          </DialogDescription>
        </DialogHeader>

        <div className="grid max-h-[65vh] gap-6 overflow-y-auto pr-1">
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
                    {scopeLabel(assignment)} · {validityLabel(assignment)}
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
            <AccountPermissionList permissions={permissions} loading={loading} />
          </section>
        </div>

        <DialogFooter>
          <DialogClose render={<Button type="button" variant="outline" />}>Đóng</DialogClose>
          <Button nativeButton={false} render={<Link href={`${PORTAL_CONFIG[portal].pagePrefix}/accounts/${account.id}`} />}>
            <ExternalLink /> Xem chi tiết tài khoản
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
