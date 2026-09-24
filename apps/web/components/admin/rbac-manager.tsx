"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useCallback, useEffect, useState } from "react"

import { PermissionManager } from "@/components/admin/permission-manager"
import { RoleManager } from "@/components/admin/role-manager"
import { PortalSidebar } from "@/components/auth/portal-sidebar"
import { getCurrentSession } from "@/lib/auth/client"
import { AuthApiError, type SessionData } from "@/lib/auth/types"
import { rbacErrorMessage } from "@/lib/rbac"

export function RbacManager() {
  const router = useRouter()
  const [session, setSession] = useState<SessionData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [tab, setTab] = useState<"roles" | "permissions">("roles")
  const onSessionExpired = useCallback(() => {
    router.replace("/admin/login")
    router.refresh()
  }, [router])

  useEffect(() => {
    let active = true
    getCurrentSession("admin")
      .then((result) => { if (active) setSession(result) })
      .catch((caught: unknown) => {
        if (!active) return
        if (caught instanceof AuthApiError && (caught.status === 401 || caught.status === 403)) {
          onSessionExpired()
        }
        setError(rbacErrorMessage(caught))
      })
    return () => { active = false }
  }, [onSessionExpired])

  if (!session) {
    return (
      <main className="dashboard-loading">
        <p role={error ? "alert" : "status"}>{error ?? "Đang xác minh phiên quản trị..."}</p>
        {error && <Link href="/admin">Về trang quản trị</Link>}
      </main>
    )
  }

  return (
    <main className="portal-shell admin-shell">
      <PortalSidebar portal="admin" account={session.account} active="rbac" />
      <section className="portal-content rbac-content">
        <header className="portal-header">
          <div>
            <p className="eyebrow">ADMIN CONSOLE</p>
            <h1>Vai trò & quyền</h1>
            <p>Quản lý vai trò, danh mục quyền và quyền của từng vai trò.</p>
          </div>
          <Link className="ghost-button" href="/admin">Về tổng quan</Link>
        </header>
        <nav className="rbac-tabs" aria-label="Danh mục phân quyền">
          <button type="button" aria-pressed={tab === "roles"} onClick={() => setTab("roles")}>Vai trò</button>
          <button type="button" aria-pressed={tab === "permissions"} onClick={() => setTab("permissions")}>Quyền</button>
        </nav>
        {tab === "roles"
          ? <RoleManager onSessionExpired={onSessionExpired} />
          : <PermissionManager onSessionExpired={onSessionExpired} />}
      </section>
    </main>
  )
}
