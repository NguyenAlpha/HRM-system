"use client"

import Link from "next/link"
import { useEffect, useState } from "react"
import { ArrowLeft, KeyRound, RefreshCw, ShieldCheck, ShieldMinus, UsersRound } from "lucide-react"

import {
  AccountPermissionList,
  ASSIGNMENT_STATES,
  assignmentState,
  isRevocable,
  scopeLabel,
  todayIso,
  validityLabel,
} from "@/components/account/account-access"
import {
  ACCOUNT_ACTIONS,
  ACCOUNT_STATUS_LABELS,
  ACCOUNT_STATUS_VARIANTS,
  AccountActionDialog,
  accountViewer,
  availableAccountActions,
  formatDateTime,
  isGuardedAccount,
  type AccountActionKind,
} from "@/components/account/account-actions"
import { RevokeRoleDialog } from "@/components/account/revoke-role-dialog"
import { ErrorAlert } from "@/components/common/list-controls"
import { RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { getAccount, getAccountPermissions, type Account, type AccountPermissionGrant } from "@/lib/account"
import { PORTAL_CONFIG } from "@/lib/auth/config"
import { listAccountRoleAssignments, type AccountRoleAssignment } from "@/lib/role-assignment"

function InfoField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-words text-sm font-medium">{children || "—"}</dd>
    </div>
  )
}

/** One account on its own page: profile, lifecycle actions, role assignments (with revoke) and permissions. */
export function AccountDetailPage({ accountId: rawAccountId }: { accountId: string }) {
  const { portal, session, onSessionExpired } = usePortalSession()
  const viewer = accountViewer(session.account)
  const canRead = session.account.permissions.some((permission) => permission.code === "account.read")
  const accountId = /^[1-9]\d*$/.test(rawAccountId) ? Number(rawAccountId) : null
  const listHref = `${PORTAL_CONFIG[portal].pagePrefix}/accounts`

  const [account, setAccount] = useState<Account | null>(null)
  const [assignments, setAssignments] = useState<AccountRoleAssignment[] | null>(null)
  const [permissions, setPermissions] = useState<AccountPermissionGrant[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [pending, setPending] = useState<AccountActionKind | null>(null)
  const [revoking, setRevoking] = useState<AccountRoleAssignment | null>(null)
  const [showPast, setShowPast] = useState(false)
  const today = todayIso()

  useEffect(() => {
    if (accountId === null || !canRead) return
    let active = true
    Promise.all([
      getAccount(portal, accountId),
      listAccountRoleAssignments(portal, accountId),
      getAccountPermissions(portal, accountId),
    ])
      .then(([loadedAccount, loadedAssignments, loadedPermissions]) => {
        if (!active) return
        setAccount(loadedAccount)
        setAssignments(loadedAssignments)
        setPermissions(loadedPermissions)
        setError(null)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setError(apiErrorMessage(caught))
      })
    return () => { active = false }
  }, [portal, accountId, canRead, revision, onSessionExpired])

  function reload() {
    setError(null)
    setAccount(null)
    setAssignments(null)
    setPermissions(null)
    setRevision((value) => value + 1)
  }

  const displayedError = !canRead
    ? "Tài khoản của bạn không có quyền xem tài khoản."
    : accountId === null ? "Mã tài khoản trên đường dẫn không hợp lệ." : error
  const loading = !displayedError && account === null
  const actions = account ? availableAccountActions(account, viewer) : []
  const canRevoke = account !== null && viewer.canAssignRole && !isGuardedAccount(account, viewer)
  const current = assignments?.filter((item) => ["active", "scheduled"].includes(assignmentState(item, today))) ?? []
  const past = assignments?.filter((item) => ["expired", "revoked"].includes(assignmentState(item, today))) ?? []
  const shownAssignments = [...current, ...(showPast ? past : [])]

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex min-w-0 items-center gap-3">
          <span className="avatar shrink-0 text-lg">{(account?.username ?? "?").slice(0, 1).toUpperCase()}</span>
          <div className="min-w-0">
            <p className="eyebrow">TÀI KHOẢN</p>
            <h1 className="mt-1 truncate text-3xl font-semibold tracking-tight sm:text-4xl">
              {account?.username ?? "Chi tiết tài khoản"}
            </h1>
            {account && (
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <span className="text-sm text-muted-foreground">{account.email}</span>
                <Badge variant={ACCOUNT_STATUS_VARIANTS[account.status]}>{ACCOUNT_STATUS_LABELS[account.status]}</Badge>
                {account.id === viewer.accountId && <span className="text-xs text-muted-foreground">(bạn)</span>}
              </div>
            )}
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {actions.map((kind) => {
            const Icon = ACCOUNT_ACTIONS[kind].icon
            return (
              <Button key={kind} type="button" variant={ACCOUNT_ACTIONS[kind].destructive ? "destructive" : "outline"}
                onClick={() => setPending(kind)}>
                <Icon /> {ACCOUNT_ACTIONS[kind].label}
              </Button>
            )
          })}
          <Button type="button" variant="outline" onClick={reload} disabled={loading}>
            <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
          </Button>
          <Button variant="outline" nativeButton={false} render={<Link href={listHref} />}>
            <ArrowLeft /> Về danh sách
          </Button>
        </div>
      </header>

      <div className="mt-6">
        {loading && (
          <div className="grid gap-4 lg:grid-cols-3">
            <Skeleton className="h-56 w-full" />
            <Skeleton className="h-56 w-full lg:col-span-2" />
          </div>
        )}

        {displayedError && (
          <Card>
            <CardHeader>
              <CardTitle>Không thể tải tài khoản</CardTitle>
              <CardDescription>Kiểm tra quyền truy cập hoặc thử tải lại dữ liệu.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <ErrorAlert error={displayedError} />
              <Button type="button" onClick={reload} disabled={accountId === null || !canRead}>
                <RefreshCw /> Thử lại
              </Button>
            </CardContent>
          </Card>
        )}

        {!displayedError && account && (
          <div className="grid gap-4 lg:grid-cols-3">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2"><KeyRound /> Thông tin tài khoản</CardTitle>
              </CardHeader>
              <CardContent>
                <dl className="grid gap-4">
                  <InfoField label="Tên đăng nhập">{account.username}</InfoField>
                  <InfoField label="Email đăng nhập">{account.email}</InfoField>
                  <InfoField label="Nhân viên">
                    {account.employeeId !== null && (
                      <Link className="underline-offset-4 hover:underline"
                        href={`${PORTAL_CONFIG[portal].pagePrefix}/employees/${account.employeeId}`}>
                        {account.employeeFullName} <span className="text-muted-foreground">({account.employeeCode})</span>
                      </Link>
                    )}
                  </InfoField>
                  <InfoField label="Đăng nhập gần nhất">{formatDateTime(account.lastLoginAt)}</InfoField>
                  <InfoField label="Đăng nhập sai liên tiếp">{String(account.failedLoginCount)}</InfoField>
                  {account.lockedUntil && <InfoField label="Khóa đến">{formatDateTime(account.lockedUntil)}</InfoField>}
                  <InfoField label="Ngày tạo">{formatDateTime(account.createdAt)}</InfoField>
                  <InfoField label="Cập nhật gần nhất">{formatDateTime(account.updatedAt)}</InfoField>
                </dl>
              </CardContent>
            </Card>

            <Card className="lg:col-span-2">
              <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
                <div>
                  <CardTitle className="flex items-center gap-2"><UsersRound /> Role</CardTitle>
                  <CardDescription>Các lần gán role của tài khoản, kèm phạm vi và thời gian hiệu lực.</CardDescription>
                </div>
                {past.length > 0 && (
                  <Button type="button" variant="ghost" size="sm" onClick={() => setShowPast((value) => !value)}>
                    {showPast ? "Ẩn role đã hết hạn / thu hồi" : `Hiện role đã hết hạn / thu hồi (${past.length})`}
                  </Button>
                )}
              </CardHeader>
              <CardContent>
                <div className="rounded-xl border">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Role</TableHead>
                        <TableHead>Phạm vi</TableHead>
                        <TableHead>Hiệu lực</TableHead>
                        <TableHead>Trạng thái</TableHead>
                        <TableHead className="text-right">Thao tác</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {shownAssignments.map((assignment) => {
                        const state = ASSIGNMENT_STATES[assignmentState(assignment, today)]
                        return (
                          <TableRow key={assignment.id}>
                            <TableCell>
                              <span className="font-medium">{assignment.roleName}</span>
                              <code className="block text-xs text-muted-foreground">{assignment.roleCode}</code>
                            </TableCell>
                            <TableCell className="text-sm">{scopeLabel(assignment)}</TableCell>
                            <TableCell className="text-sm">{validityLabel(assignment)}</TableCell>
                            <TableCell>
                              <Badge variant={state.variant}>{state.label}</Badge>
                              {assignment.revokedAt && assignment.revocationReason && (
                                <span className="mt-1 block max-w-56 text-xs text-wrap text-muted-foreground">
                                  {assignment.revocationReason}
                                </span>
                              )}
                            </TableCell>
                            <RowActionsCell label={assignment.roleName}>
                              {canRevoke && isRevocable(assignment, today) && (
                                <RowActionItem icon={ShieldMinus} destructive onClick={() => setRevoking(assignment)}>
                                  Thu hồi role
                                </RowActionItem>
                              )}
                            </RowActionsCell>
                          </TableRow>
                        )
                      })}
                      {shownAssignments.length === 0 && (
                        <TableRow>
                          <TableCell colSpan={5} className="h-24 text-center text-muted-foreground">
                            Tài khoản không có role nào đang hiệu lực.
                          </TableCell>
                        </TableRow>
                      )}
                    </TableBody>
                  </Table>
                </div>
              </CardContent>
            </Card>

            <Card className="lg:col-span-3">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <ShieldCheck /> Permission hiệu lực{permissions && ` (${permissions.length})`}
                </CardTitle>
                <CardDescription>
                  Quyền tài khoản có hôm nay: quyền của các role đang hiệu lực, cộng và trừ ngoại lệ quyền.
                </CardDescription>
              </CardHeader>
              <CardContent className="grid gap-3 md:grid-cols-2">
                <AccountPermissionList permissions={permissions} loading={false} />
              </CardContent>
            </Card>
          </div>
        )}
      </div>

      {account && pending && (
        <AccountActionDialog
          portal={portal}
          account={account}
          kind={pending}
          onClose={() => setPending(null)}
          onDone={() => setRevision((value) => value + 1)}
          onSessionExpired={onSessionExpired}
        />
      )}

      {account && revoking && (
        <RevokeRoleDialog
          portal={portal}
          accountId={account.id}
          username={account.username}
          assignment={revoking}
          onClose={() => setRevoking(null)}
          onRevoked={() => setRevision((value) => value + 1)}
          onSessionExpired={onSessionExpired}
        />
      )}
    </>
  )
}
