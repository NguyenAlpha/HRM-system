"use client"

import { usePortalSession } from "@/components/auth/portal-shell"
import { OrganizationReferenceManager } from "@/components/organization/organization-reference-manager"

export function OrganizationReferencePage() {
  const { onSessionExpired } = usePortalSession()

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">HRM WORKSPACE</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Danh mục tổ chức</h1>
          <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
            Đơn vị tổ chức, địa điểm làm việc, vị trí công việc và hình thức làm việc trong doanh nghiệp.
          </p>
        </div>
      </header>

      <div className="mt-6">
        <OrganizationReferenceManager portal="hrm" onSessionExpired={onSessionExpired} />
      </div>
    </>
  )
}
