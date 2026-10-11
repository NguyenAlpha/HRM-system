"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useCallback, useEffect, useState } from "react"

import { AccountManager } from "@/components/account/account-manager"
import { PortalSidebar } from "@/components/auth/portal-sidebar"
import { apiErrorMessage } from "@/lib/api-helpers"
import { getCurrentSession } from "@/lib/auth/client"
import { AuthApiError, type SessionData } from "@/lib/auth/types"

export function AccountPage() {
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

  const has = (code: string) => session.account.permissions.some((permission) => permission.code === code)

  return (
    <main className="portal-shell hrm-shell">
      <PortalSidebar portal="hrm" account={session.account} active="accounts" />
      <section className="portal-content">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="eyebrow">HRM WORKSPACE</p>
            <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Tài khoản</h1>
            <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
              Quản lý vòng đời tài khoản đăng nhập. Tài khoản mới được cấp từ hồ sơ nhân viên.
            </p>
          </div>
        </header>

        <div className="mt-6 grid gap-6">
          {has("account.read") ? (
            <AccountManager
              currentAccountId={session.account.accountId}
              canManage={has("account.manage")}
              canManageActivation={has("account.activation.manage")}
              onSessionExpired={onSessionExpired}
            />
          ) : (
            <p role="alert" className="text-[var(--muted-ink)]">Tài khoản của bạn không có quyền xem danh sách tài khoản.</p>
          )}
        </div>
      </section>
    </main>
  )
}
