"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { FormEvent, useEffect, useState } from "react"

import { PORTAL_CONFIG } from "@/lib/auth/config"
import {
  changePassword,
  getCurrentSession,
  logout,
  refreshSession,
} from "@/lib/auth/client"
import { AuthApiError, type Portal, type SessionData } from "@/lib/auth/types"
import { PortalSidebar } from "@/components/auth/portal-sidebar"

interface PortalDashboardProps {
  portal: Portal
}

function formatExpiry(value: string): string {
  return new Intl.DateTimeFormat("vi-VN", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(value))
}

function roleScope(role: SessionData["account"]["roles"][number]): string {
  if (role.scopeType === "ORG_UNIT") return role.organizationUnitName ?? `Đơn vị #${role.organizationUnitId}`
  if (role.scopeType === "LOCATION") return role.workLocationName ?? `Địa điểm #${role.workLocationId}`
  if (role.scopeType === "SELF") return "Bản thân"
  return "Toàn công ty"
}

export function PortalDashboard({ portal }: PortalDashboardProps) {
  const router = useRouter()
  const config = PORTAL_CONFIG[portal]
  const [session, setSession] = useState<SessionData | null>(null)
  const [loading, setLoading] = useState(true)
  const [working, setWorking] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [currentPassword, setCurrentPassword] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")

  useEffect(() => {
    let active = true

    getCurrentSession(portal)
      .then((currentSession) => {
        if (active) setSession(currentSession)
      })
      .catch((caughtError: unknown) => {
        if (!active) return
        if (caughtError instanceof AuthApiError && (caughtError.status === 401 || caughtError.status === 403)) {
          router.replace(config.loginPath)
          return
        }
        setError(caughtError instanceof Error ? caughtError.message : "Không thể tải phiên đăng nhập")
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [config.loginPath, portal, router])

  async function handleRefresh() {
    setWorking(true)
    setError(null)
    setMessage(null)
    try {
      setSession(await refreshSession(portal))
      setMessage("Access token và refresh token đã được xoay vòng thành công.")
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : "Không thể refresh phiên")
    } finally {
      setWorking(false)
    }
  }

  async function handleLogout() {
    setWorking(true)
    try {
      await logout(portal)
    } finally {
      router.replace(config.loginPath)
      router.refresh()
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
      router.refresh()
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : "Không thể đổi mật khẩu")
    } finally {
      setWorking(false)
    }
  }

  if (loading) {
    return (
      <main className="dashboard-loading">
        <span className="loading-mark">H</span>
        <p>Đang xác minh phiên đăng nhập...</p>
      </main>
    )
  }

  if (!session) {
    return (
      <main className="dashboard-loading">
        <p>{error ?? "Phiên đăng nhập không khả dụng."}</p>
        <button type="button" onClick={() => router.replace(config.loginPath)}>Về trang đăng nhập</button>
      </main>
    )
  }

  const { account } = session
  const admin = portal === "admin"

  return (
    <main className={`portal-shell ${admin ? "admin-shell" : "hrm-shell"}`}>
      <PortalSidebar portal={portal} account={account} />

      <section className="portal-content">
        <header className="portal-header">
          <div>
            <p className="eyebrow">{admin ? "SYSTEM ADMINISTRATION" : "HRM WORKSPACE"}</p>
            <h1>{admin ? "Tổng quan quản trị" : `Xin chào, ${account.employee?.fullName ?? account.username}`}</h1>
            <p>{admin ? "Kiểm tra phiên quản trị và quyền RBAC hiện tại." : "Phiên đăng nhập HRM của bạn đang hoạt động."}</p>
          </div>
          <div className="portal-actions">
            {admin && <Link className="ghost-button" href="/admin/rbac">Quản lý vai trò & quyền</Link>}
            <button className="ghost-button" type="button" onClick={handleLogout} disabled={working}>
              Đăng xuất
            </button>
          </div>
        </header>

        {(message || error) && (
          <div className={`dashboard-alert ${error ? "error" : "success"}`} role="status">
            {error ?? message}
          </div>
        )}

        <section className="stat-grid" aria-label="Tóm tắt tài khoản">
          <article>
            <span className="stat-label">Trạng thái</span>
            <strong className="status-value"><i /> {account.status}</strong>
            <small>Account đang được phép xác thực</small>
          </article>
          <article>
            <span className="stat-label">Vai trò</span>
            <strong>{account.roles.length}</strong>
            <small>Role đang có hiệu lực</small>
          </article>
          <article>
            <span className="stat-label">Quyền hạn</span>
            <strong>{account.permissions.length}</strong>
            <small>Authority hợp nhất từ RBAC</small>
          </article>
        </section>

        <div className="dashboard-grid">
          <section className="dashboard-card account-card">
            <div className="card-heading">
              <div>
                <span className="card-kicker">Account</span>
                <h2>Thông tin đăng nhập</h2>
              </div>
              <span className="account-id">ID #{account.accountId}</span>
            </div>
            <dl className="account-details">
              <div><dt>Username</dt><dd>{account.username}</dd></div>
              <div><dt>Email</dt><dd>{account.email}</dd></div>
              <div><dt>Nhân viên</dt><dd>{account.employee ? `${account.employee.fullName} (${account.employee.employeeCode})` : "Chưa liên kết"}</dd></div>
              <div><dt>Access token hết hạn</dt><dd>{formatExpiry(session.expiresAt)}</dd></div>
            </dl>
            <button className="secondary-button" type="button" onClick={handleRefresh} disabled={working}>
              {working ? "Đang xử lý..." : "Xoay vòng refresh token"}
            </button>
          </section>

          <section className="dashboard-card">
            <div className="card-heading">
              <div>
                <span className="card-kicker">Authorization</span>
                <h2>Vai trò và quyền</h2>
              </div>
            </div>
            <div className="tag-section">
              <span>Roles</span>
              <div className="tag-list">
                {account.roles.map((role) => (
                  <strong className="role-tag" key={`${role.code}:${role.scopeType}:${role.organizationUnitId ?? role.workLocationId ?? "company"}`}
                    title={`${role.code} · ${role.scopeType}`}>
                    {role.name} · {roleScope(role)}
                  </strong>
                ))}
              </div>
            </div>
            <div className="tag-section">
              <span>Permissions</span>
              <div className="tag-list">
                {account.permissions.length > 0
                  ? account.permissions.map((permission) => (
                    <span className="permission-tag" key={permission.code} title={`${permission.code} · ${permission.module}`}>
                      {permission.name}
                    </span>
                  ))
                  : <em>Không có permission trực tiếp.</em>}
              </div>
            </div>
          </section>

          <section className="dashboard-card password-card">
            <div className="card-heading">
              <div>
                <span className="card-kicker">Security</span>
                <h2>Đổi mật khẩu</h2>
              </div>
            </div>
            <p>Đổi mật khẩu sẽ thu hồi toàn bộ refresh token và đưa bạn về trang đăng nhập.</p>
            <form onSubmit={handleChangePassword}>
              <label>
                Mật khẩu hiện tại
                <input type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} required />
              </label>
              <label>
                Mật khẩu mới
                <input type="password" autoComplete="new-password" minLength={8} maxLength={100} value={newPassword} onChange={(event) => setNewPassword(event.target.value)} required />
              </label>
              <label>
                Xác nhận mật khẩu mới
                <input type="password" autoComplete="new-password" minLength={8} maxLength={100} value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} required />
              </label>
              <button className="danger-button" type="submit" disabled={working}>Đổi mật khẩu và đăng xuất</button>
            </form>
          </section>
        </div>
      </section>
    </main>
  )
}
