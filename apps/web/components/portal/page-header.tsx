"use client"

import type { ReactNode } from "react"

/**
 * Title block of a portal page. `backToOverview` is still accepted from the pages but no longer
 * rendered: the sidebar's "Tổng quan" entry already leads back.
 */
export function PageHeader({ title, description, actions, alignActions = "start" }: {
  title: ReactNode
  description: ReactNode
  backToOverview?: boolean
  /** Extra controls on the right, e.g. the employee/month filters of the workforce pages. */
  actions?: ReactNode
  alignActions?: "start" | "end"
}) {
  return (
    <header className={`flex flex-wrap ${alignActions === "end" ? "items-end" : "items-start"} justify-between gap-4`}>
      <div>
        <h1 className="text-3xl font-semibold tracking-tight">{title}</h1>
        <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">{description}</p>
      </div>
      {actions}
    </header>
  )
}
