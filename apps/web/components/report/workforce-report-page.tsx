"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useCallback, useEffect, useState } from "react"
import { ArrowLeft } from "lucide-react"

import { PortalSidebar } from "@/components/auth/portal-sidebar"
import { WorkforceReportDashboard } from "@/components/report/workforce-report-dashboard"
import { Button } from "@/components/ui/button"
import { apiErrorMessage } from "@/lib/api-helpers"
import { getCurrentSession } from "@/lib/auth/client"
import { AuthApiError, type SessionData } from "@/lib/auth/types"

export function WorkforceReportPage() {
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

  const permissionCodes = new Set(session.account.permissions.map((permission) => permission.code))
  const canReadHr = permissionCodes.has("report.hr.read")
  const canReadPayroll = permissionCodes.has("report.payroll.read")
  const canReadOrganization = permissionCodes.has("organization.read")

  if (!canReadHr && !canReadPayroll) {
    return (
      <main className="portal-shell hrm-shell">
        <PortalSidebar portal="hrm" account={session.account} />
        <section className="portal-content">
          <AlertAccessDenied />
        </section>
      </main>
    )
  }

  return (
    <main className="portal-shell hrm-shell">
      <PortalSidebar portal="hrm" account={session.account} active="reports" />
      <section className="portal-content">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="eyebrow">HRM WORKSPACE</p>
            <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Báo cáo nhân sự</h1>
            <p className="mt-1.5 max-w-2xl text-[var(--muted-ink)]">
              Thống kê trình độ học vấn, mức lương cơ bản và thâm niên theo phạm vi được phân quyền.
            </p>
          </div>
          <Button variant="outline" nativeButton={false} render={<Link href="/dashboard" />}>
            <ArrowLeft /> Về tổng quan
          </Button>
        </header>

        <div className="mt-6">
          <WorkforceReportDashboard
            canReadHr={canReadHr}
            canReadPayroll={canReadPayroll}
            canReadOrganization={canReadOrganization}
            onSessionExpired={onSessionExpired}
          />
        </div>
      </section>
    </main>
  )
}

function AlertAccessDenied() {
  return (
    <div className="dashboard-card">
      <p className="eyebrow">ACCESS DENIED</p>
      <h1 className="mt-2 text-2xl font-semibold">Bạn chưa có quyền xem báo cáo</h1>
      <p className="mt-2 text-sm text-muted-foreground">
        Cần quyền xem báo cáo nhân sự hoặc báo cáo tiền lương để sử dụng trang này.
      </p>
      <Button className="mt-4" variant="outline" nativeButton={false} render={<Link href="/dashboard" />}>
        Về trang tổng quan
      </Button>
    </div>
  )
}
