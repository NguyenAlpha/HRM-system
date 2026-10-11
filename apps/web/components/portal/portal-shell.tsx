"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react"

import { PortalSidebar } from "@/components/portal/portal-sidebar"
import { apiErrorMessage } from "@/lib/api-helpers"
import { getCurrentSession } from "@/lib/auth/client"
import { PORTAL_CONFIG } from "@/lib/auth/config"
import { AuthApiError, type Portal, type SessionData } from "@/lib/auth/types"

interface PortalSessionValue {
  portal: Portal
  session: SessionData
  /** Replaces the session after a refresh so the sidebar and pages see the new account data. */
  setSession: (session: SessionData) => void
  /** Sends the user back to this portal's login page. */
  onSessionExpired: () => void
}

const PortalSessionContext = createContext<PortalSessionValue | null>(null)

/** Session of the portal layout the calling page is rendered in. */
export function usePortalSession(): PortalSessionValue {
  const value = useContext(PortalSessionContext)
  if (!value) throw new Error("usePortalSession must be used inside PortalShell")
  return value
}

/** Shared frame of every signed-in page: loads the session once, then renders the sidebar and the page. */
export function PortalShell({ portal, children }: { portal: Portal; children: ReactNode }) {
  const router = useRouter()
  const { loginPath } = PORTAL_CONFIG[portal]
  const [session, setSession] = useState<SessionData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const onSessionExpired = useCallback(() => {
    router.replace(loginPath)
    router.refresh()
  }, [router, loginPath])

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
      <main className={`portal-shell ${portal === "admin" ? "admin-shell" : "hrm-shell"}`}>
        <PortalSidebar portal={portal} account={session.account} />
        <section className="portal-content">{children}</section>
      </main>
    </PortalSessionContext.Provider>
  )
}
