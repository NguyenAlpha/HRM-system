"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { FormEvent, useState, type ReactNode } from "react"
import { ArrowUpRight, KeyRound, ShieldCheck, UserRoundCheck } from "lucide-react"

import { ACCOUNT_STATUS_LABELS } from "@/components/account/account-actions"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { apiErrorMessage } from "@/lib/api-helpers"
import { PORTAL_CONFIG } from "@/lib/auth/config"
import { changePassword, refreshSession } from "@/lib/auth/client"
import type { SessionData } from "@/lib/auth/types"
import { formatDateTime } from "@/lib/format-date"

export function roleScope(role: SessionData["account"]["roles"][number]): string {
  if (role.scopeType === "ORG_UNIT") return role.organizationUnitName ?? `Đơn vị #${role.organizationUnitId}`
  if (role.scopeType === "LOCATION") return role.workLocationName ?? `Địa điểm #${role.workLocationId}`
  if (role.scopeType === "SELF") return "Bản thân"
  return "Toàn công ty"
}

/** Admin Console overview: the signed-in admin's session, roles and permissions, and a password change. */
export function PortalDashboard() {
  const router = useRouter()
  const { portal, session, setSession } = usePortalSession()
  const config = PORTAL_CONFIG[portal]
  const [working, setWorking] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [currentPassword, setCurrentPassword] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")

  async function handleRefresh() {
    setWorking(true)
    setError(null)
    setMessage(null)
    try {
      setSession(await refreshSession(portal))
      setMessage("Access token và refresh token đã được xoay vòng thành công.")
    } catch (caughtError) {
      setError(apiErrorMessage(caughtError))
    } finally {
      setWorking(false)
    }
  }

  async function handleChangePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setMessage(null)
    if (newPassword !== confirmPassword) {
      setError("Mật khẩu xác nhận không khớp.")
      return
    }

    setWorking(true)
    try {
      await changePassword(portal, currentPassword, newPassword)
      router.replace(config.loginPath)
    } catch (caughtError) {
      setError(apiErrorMessage(caughtError))
    } finally {
      setWorking(false)
    }
  }

  const { account } = session

  return (
    <>
      <PageHeader title="Tổng quan quản trị" description="Kiểm tra phiên quản trị và quyền RBAC hiện tại." />

      <div className="mt-8 grid gap-6">
        {(message || error) && (
          <Alert variant={error ? "destructive" : "default"} role="status">
            <AlertDescription>{error ?? message}</AlertDescription>
          </Alert>
        )}

        <section aria-label="Tóm tắt tài khoản" className="grid gap-4 sm:grid-cols-3">
          <Figure icon={<UserRoundCheck />} label="Trạng thái" value={ACCOUNT_STATUS_LABELS[account.status] ?? account.status}
            hint="Tài khoản đang được phép xác thực" />
          <Figure icon={<ShieldCheck />} label="Vai trò" value={String(account.roles.length)} hint="Role đang có hiệu lực" />
          <Figure icon={<KeyRound />} label="Quyền hạn" value={String(account.permissions.length)} hint="Hợp nhất từ các role" />
        </section>

        <div className="grid gap-6 lg:grid-cols-3">
          <Card>
            <CardHeader>
              <CardTitle>Thông tin đăng nhập</CardTitle>
              <CardAction><Badge variant="outline">ID #{account.accountId}</Badge></CardAction>
            </CardHeader>
            <CardContent className="grid gap-4">
              <dl className="grid gap-2.5 text-sm">
                <Line label="Username" value={account.username} />
                <Line label="Email" value={account.email} />
                <Line label="Nhân viên" value={account.employee ? `${account.employee.fullName} (${account.employee.employeeCode})` : "Chưa liên kết"} />
                <Line label="Access token hết hạn" value={formatDateTime(session.expiresAt)} />
              </dl>
              <Button type="button" variant="outline" size="sm" className="justify-self-start" onClick={handleRefresh} disabled={working}>
                {working ? "Đang xử lý..." : "Xoay vòng refresh token"}
              </Button>
            </CardContent>
          </Card>

          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle>Vai trò và quyền</CardTitle>
              <CardAction>
                <Link href="/admin/rbac" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
                  Quản lý vai trò & quyền <ArrowUpRight className="size-3.5" />
                </Link>
              </CardAction>
            </CardHeader>
            <CardContent className="grid gap-5">
              <div className="grid gap-2">
                <p className="text-xs text-muted-foreground">Vai trò</p>
                <div className="flex flex-wrap gap-1.5">
                  {account.roles.map((role) => (
                    <Badge variant="secondary" key={`${role.code}:${role.scopeType}:${role.organizationUnitId ?? role.workLocationId ?? "company"}`}
                      title={`${role.code} · ${role.scopeType}`}>
                      {role.name} · {roleScope(role)}
                    </Badge>
                  ))}
                </div>
              </div>
              <div className="grid gap-2">
                <p className="text-xs text-muted-foreground">Quyền</p>
                <div className="flex flex-wrap gap-1.5">
                  {account.permissions.length > 0
                    ? account.permissions.map((permission) => (
                      <Badge variant="outline" key={permission.code} title={`${permission.code} · ${permission.module}`}>
                        {permission.name}
                      </Badge>
                    ))
                    : <span className="text-sm text-muted-foreground">Không có permission trực tiếp.</span>}
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Đổi mật khẩu</CardTitle>
            <CardDescription>Đổi mật khẩu sẽ thu hồi toàn bộ refresh token và đưa bạn về trang đăng nhập.</CardDescription>
          </CardHeader>
          <CardContent>
            <form className="grid items-end gap-4 md:grid-cols-[1fr_1fr_1fr_auto]" onSubmit={handleChangePassword}>
              <div className="grid gap-2">
                <Label htmlFor="admin-current-password">Mật khẩu hiện tại</Label>
                <Input id="admin-current-password" type="password" autoComplete="current-password" value={currentPassword}
                  onChange={(event) => setCurrentPassword(event.target.value)} required />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="admin-new-password">Mật khẩu mới</Label>
                <Input id="admin-new-password" type="password" autoComplete="new-password" minLength={8} maxLength={100} value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)} required />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="admin-confirm-password">Xác nhận mật khẩu mới</Label>
                <Input id="admin-confirm-password" type="password" autoComplete="new-password" minLength={8} maxLength={100} value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)} required />
              </div>
              <Button type="submit" variant="destructive" disabled={working}>Đổi mật khẩu và đăng xuất</Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </>
  )
}

/** A headline figure in the dashboard card style: label left, round icon right. */
function Figure({ icon, label, value, hint }: { icon: ReactNode; label: string; value: string; hint: string }) {
  return (
    <div className="rounded-xl border bg-card p-5">
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm text-muted-foreground">{label}</p>
        <span className="grid size-9 shrink-0 place-items-center rounded-full bg-muted text-muted-foreground [&_svg]:size-4">{icon}</span>
      </div>
      <p className="mt-1 truncate text-2xl font-semibold tracking-tight">{value}</p>
      <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
    </div>
  )
}

/** One label/value row of a definition list, value aligned right. */
function Line({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="truncate font-medium">{value}</dd>
    </div>
  )
}
