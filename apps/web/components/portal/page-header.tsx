"use client"

import Link from "next/link"
import type { ReactNode } from "react"
import { ArrowLeft } from "lucide-react"

import { usePortalSession } from "@/components/portal/portal-shell"
import { Button } from "@/components/ui/button"
import { PORTAL_CONFIG } from "@/lib/auth/config"

/** Title block of a portal page; the eyebrow and the "back to overview" link follow the current portal. */
export function PageHeader({ title, description, backToOverview = false, actions, alignActions = "start" }: {
  title: ReactNode
  description: ReactNode
  backToOverview?: boolean
  /** Extra controls on the right, e.g. the employee/month filters of the workforce pages. */
  actions?: ReactNode
  alignActions?: "start" | "end"
}) {
  const { portal } = usePortalSession()

  return (
    <header className={`flex flex-wrap ${alignActions === "end" ? "items-end" : "items-start"} justify-between gap-4`}>
      <div>
        <p className="eyebrow">{portal === "admin" ? "ADMIN CONSOLE" : "HRM WORKSPACE"}</p>
        <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">{title}</h1>
        <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">{description}</p>
      </div>
      {actions}
      {backToOverview && (
        <Button variant="outline" nativeButton={false} render={<Link href={PORTAL_CONFIG[portal].homePath} />}>
          <ArrowLeft /> Về tổng quan
        </Button>
      )}
    </header>
  )
}
