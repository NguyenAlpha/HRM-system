"use client"

import Link from "next/link"
import { ArrowLeft } from "lucide-react"

import { OrganizationReferenceManager } from "@/components/organization/organization-reference-manager"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Button } from "@/components/ui/button"
import { PORTAL_CONFIG } from "@/lib/auth/config"

/** Organization reference data; the same page serves both portals. */
export function OrganizationPage() {
  const { portal, onSessionExpired } = usePortalSession()

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">{portal === "admin" ? "ADMIN CONSOLE" : "HRM WORKSPACE"}</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Danh mục tổ chức</h1>
          <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
            Đơn vị tổ chức, địa điểm làm việc, vị trí công việc và hình thức làm việc trong doanh nghiệp.
          </p>
        </div>
        <Button variant="outline" nativeButton={false} render={<Link href={PORTAL_CONFIG[portal].homePath} />}>
          <ArrowLeft /> Về tổng quan
        </Button>
      </header>

      <div className="mt-6">
        <OrganizationReferenceManager portal={portal} onSessionExpired={onSessionExpired} />
      </div>
    </>
  )
}
