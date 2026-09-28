"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useCallback, useEffect, useState } from "react"
import { ArrowLeft } from "lucide-react"

import { RoleAssignmentManager } from "@/components/role-assignment/role-assignment-manager"
import { PortalSidebar } from "@/components/auth/portal-sidebar"
import { Button } from "@/components/ui/button"
import { apiErrorMessage } from "@/lib/api-helpers"
import { getCurrentSession } from "@/lib/auth/client"
import { AuthApiError, type SessionData } from "@/lib/auth/types"

export function RoleAssignmentPage() {
  const router = useRouter()
  const [session, setSession] = useState<SessionData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const onSessionExpired = useCallback(() => {
    router.replace("/login")
    router.refresh()
  }, [router])

  useEffect(() => {
    let active = true
    getCurrentSession("hrm")
      .then((result) => { if (active) setSession(result) })
      .catch((caught: unknown) => {
        if (!active) return
        if (caught instanceof AuthApiError && (caught.status === 401 || caught.status === 403)) {
          onSessionExpired()
        }
        setError(apiErrorMessage(caught))
      })
    return () => { active = false }
  }, [onSessionExpired])

  if (!session) {
    return (
      <main className="dashboard-loading">
        <p role={error ? "alert" : "status"}>{error ?? "Đang xác minh phiên đăng nhập..."}</p>
        {error && <Link href="/dashboard">Về trang tổng quan</Link>}
      </main>
    )
  }

  const canRequest = session.account.permissions.some((permission) => permission.code === "role.assignment.request")
  const canApprove = session.account.permissions.some((permission) => permission.code === "role.assignment.approve")

  return (
    <main className="portal-shell hrm-shell">
      <PortalSidebar portal="hrm" account={session.account} active="role-requests" />
      <section className="portal-content">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="eyebrow">HRM WORKSPACE</p>
            <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Đề xuất cấp role</h1>
            <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
              Đề xuất, theo dõi và phê duyệt việc cấp role nghiệp vụ cho tài khoản nhân sự.
            </p>
          </div>
          <Button variant="outline" nativeButton={false} render={<Link href="/dashboard" />}>
            <ArrowLeft /> Về tổng quan
          </Button>
        </header>

        <div className="mt-6">
          <RoleAssignmentManager
            currentAccountId={session.account.accountId}
            canRequest={canRequest}
            canApprove={canApprove}
            onSessionExpired={onSessionExpired}
          />
        </div>
      </section>
    </main>
  )
}
