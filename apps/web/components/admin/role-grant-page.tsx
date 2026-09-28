"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useCallback, useEffect, useState } from "react"
import { ArrowLeft } from "lucide-react"

import { DirectRoleGrantManager } from "@/components/role-assignment/direct-role-grant-manager"
import { PortalSidebar } from "@/components/auth/portal-sidebar"
import { Button } from "@/components/ui/button"
import { getCurrentSession } from "@/lib/auth/client"
import { AuthApiError, type SessionData } from "@/lib/auth/types"
import { rbacErrorMessage } from "@/lib/rbac"

export function AdminRoleGrantPage() {
  const router = useRouter()
  const [session, setSession] = useState<SessionData | null>(null)
  const [error, setError] = useState<string | null>(null)
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
      <PortalSidebar portal="admin" account={session.account} active="role-grant" />
      <section className="portal-content">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="eyebrow">ADMIN CONSOLE</p>
            <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Cấp quyền trực tiếp</h1>
            <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
              Gán role trực tiếp cho nhân sự và cấp mã kích hoạt tài khoản mà không cần chờ duyệt.
            </p>
          </div>
          <Button variant="outline" nativeButton={false} render={<Link href="/admin" />}>
            <ArrowLeft /> Về tổng quan
          </Button>
        </header>

        <div className="mt-6">
          <DirectRoleGrantManager portal="admin" onSessionExpired={onSessionExpired} />
        </div>
      </section>
    </main>
  )
}
