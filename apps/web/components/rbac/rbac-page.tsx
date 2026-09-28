"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useCallback, useEffect, useState } from "react"
import { KeyRound, Users } from "lucide-react"

import { PermissionManager } from "@/components/admin/permission-manager"
import { RoleManager } from "@/components/admin/role-manager"
import { PortalSidebar } from "@/components/auth/portal-sidebar"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { getCurrentSession } from "@/lib/auth/client"
import { AuthApiError, type SessionData } from "@/lib/auth/types"
import { rbacErrorMessage } from "@/lib/rbac"

export function RbacWorkspacePage() {
  const router = useRouter()
  const [session, setSession] = useState<SessionData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [tab, setTab] = useState<"roles" | "permissions">("roles")
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
        setError(rbacErrorMessage(caught))
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

  return (
    <main className="portal-shell hrm-shell">
      <PortalSidebar portal="hrm" account={session.account} active="rbac" />
      <section className="portal-content">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="eyebrow">HRM WORKSPACE</p>
            <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Vai trò & quyền</h1>
            <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
              Quản lý vai trò tùy chỉnh và xem danh mục quyền do hệ thống định nghĩa.
            </p>
          </div>
        </header>

        <Tabs
          className="mt-6"
          value={tab}
          onValueChange={(value) => setTab(value as "roles" | "permissions")}
        >
          <TabsList>
            <TabsTrigger value="roles"><Users /> Vai trò</TabsTrigger>
            <TabsTrigger value="permissions"><KeyRound /> Quyền</TabsTrigger>
          </TabsList>
          <TabsContent value="roles" className="mt-4">
            <RoleManager portal="hrm" onSessionExpired={onSessionExpired} />
          </TabsContent>
          <TabsContent value="permissions" className="mt-4">
            <PermissionManager portal="hrm" onSessionExpired={onSessionExpired} />
          </TabsContent>
        </Tabs>
      </section>
    </main>
  )
}
