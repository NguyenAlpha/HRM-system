"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useCallback, useEffect, useState } from "react"
import { ArrowLeft } from "lucide-react"

import { EmployeeManager } from "@/components/employee/employee-manager"
import { PortalSidebar } from "@/components/auth/portal-sidebar"
import { Button } from "@/components/ui/button"
import { getCurrentSession } from "@/lib/auth/client"
import { AuthApiError, type SessionData } from "@/lib/auth/types"
import { employeeErrorMessage } from "@/lib/employee"

export function EmployeePage() {
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
        setError(employeeErrorMessage(caught))
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
  const capabilities = {
    canReadDetail: permissionCodes.has("employee.read"),
    canCreate: permissionCodes.has("employee.create"),
    canUpdate: permissionCodes.has("employee.update"),
    canAssign: permissionCodes.has("employee.assignment.manage"),
    canConfirmProbation: permissionCodes.has("employee.probation.confirm"),
    canProvisionAccount: permissionCodes.has("account.provision"),
  }

  return (
    <main className="portal-shell hrm-shell">
      <PortalSidebar portal="hrm" account={session.account} active="employees" />
      <section className="portal-content">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="eyebrow">HRM WORKSPACE</p>
            <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Nhân sự</h1>
            <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
              Xem và cập nhật hồ sơ nhân sự trong phạm vi quản lý được phân công.
            </p>
          </div>
          <Button variant="outline" nativeButton={false} render={<Link href="/dashboard" />}>
            <ArrowLeft /> Về tổng quan
          </Button>
        </header>

        <div className="mt-6">
          <EmployeeManager capabilities={capabilities} onSessionExpired={onSessionExpired} />
        </div>
      </section>
    </main>
  )
}
