"use client"

import Link from "next/link"
import { ArrowLeft } from "lucide-react"

import { usePortalSession } from "@/components/auth/portal-shell"
import { OrganizationReferenceManager } from "@/components/organization/organization-reference-manager"
import { Button } from "@/components/ui/button"

export function AdminOrganizationPage() {
  const { onSessionExpired } = usePortalSession()

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">ADMIN CONSOLE</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Danh mục tổ chức</h1>
          <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
            Đơn vị tổ chức, địa điểm làm việc, vị trí công việc và hình thức làm việc trong doanh nghiệp.
          </p>
        </div>
        <Button variant="outline" nativeButton={false} render={<Link href="/admin" />}>
          <ArrowLeft /> Về tổng quan
        </Button>
      </header>

      <div className="mt-6">
        <OrganizationReferenceManager portal="admin" onSessionExpired={onSessionExpired} />
      </div>
    </>
  )
}
