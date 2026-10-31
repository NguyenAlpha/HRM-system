"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react"

import { PortalSidebar } from "@/components/portal/portal-sidebar"
import { apiErrorMessage } from "@/lib/api-helpers"
import { getCurrentSession } from "@/lib/auth/client"
import { PORTAL_CONFIG } from "@/lib/auth/config"
import { AuthApiError, type Portal, type SessionData } from "@/lib/auth/types"
import type { Branding } from "@/lib/branding"

interface PortalSessionValue {
  portal: Portal
  session: SessionData
  /** Replaces the session after a refresh so the sidebar and pages see the new account data. */
  setSession: (session: SessionData) => void
  /** Sends the user back to this portal's login page. */
  onSessionExpired: () => void
}

/** Remembers, per browser, whether the sidebar was left collapsed. */
const SIDEBAR_COLLAPSED_KEY = "hrm.sidebar-collapsed"

const PortalSessionContext = createContext<PortalSessionValue | null>(null)

/** Session of the portal layout the calling page is rendered in. */
export function usePortalSession(): PortalSessionValue {
  const value = useContext(PortalSessionContext)
  if (!value) throw new Error("usePortalSession must be used inside PortalShell")
  return value
}

/** Shared frame of every signed-in page: loads the session once, then renders the sidebar and the page. */
export function PortalShell({ portal, branding, children }: { portal: Portal; branding: Branding; children: ReactNode }) {
  const router = useRouter()
  const { loginPath } = PORTAL_CONFIG[portal]
  const [session, setSession] = useState<SessionData | null>(null)
  const [error, setError] = useState<string | null>(null)
  // The sidebar only renders once the session has loaded in the browser, so reading storage here never
  // disagrees with the server render.
  const [sidebarCollapsed, setSidebarCollapsed] = useState(
    () => typeof window !== "undefined" && localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === "true",
  )
  const onSessionExpired = useCallback(() => {
    router.replace(loginPath)
  }, [router, loginPath])

  function toggleSidebar() {
    const next = !sidebarCollapsed
    setSidebarCollapsed(next)
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(next))
  }

  useEffect(() => {
    let active = true
    getCurrentSession(portal)
      .then((result) => { if (active) setSession(result) })
      .catch((caught: unknown) => {
        if (!active) return
        if (caught instanceof AuthApiError && (caught.status === 401 || caught.status === 403)) {
          onSessionExpired()
        }
        setError(apiErrorMessage(caught))
      })
    return () => { active = false }
  }, [portal, onSessionExpired])

  if (!session) {
    return (
      <main className="dashboard-loading">
        <p role={error ? "alert" : "status"}>
          {error ?? (portal === "admin" ? "Đang xác minh phiên quản trị..." : "Đang xác minh phiên đăng nhập...")}
        </p>
        {error && <Link href={loginPath}>Về trang đăng nhập</Link>}
      </main>
    )
  }

  return (
    <PortalSessionContext.Provider value={{ portal, session, setSession, onSessionExpired }}>
      <main className={`portal-shell ${portal === "admin" ? "admin-shell" : "hrm-shell"} ${sidebarCollapsed ? "sidebar-collapsed" : ""}`}>
        <PortalSidebar portal={portal} account={session.account} branding={branding}
          collapsed={sidebarCollapsed} onToggle={toggleSidebar} />
        <section className="portal-content">{children}</section>
      </main>
    </PortalSessionContext.Provider>
  )
}
