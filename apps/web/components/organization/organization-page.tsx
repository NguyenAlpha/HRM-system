"use client"

import { OrganizationReferenceManager } from "@/components/organization/organization-reference-manager"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"

/** Organization reference data; the same page serves both portals. */
export function OrganizationPage() {
  const { portal, onSessionExpired } = usePortalSession()

  return (
    <>
      <PageHeader
        title="Danh mục tổ chức"
        description="Đơn vị tổ chức, địa điểm làm việc, vị trí công việc, ca làm việc, ngày lễ và hình thức làm việc trong doanh nghiệp."
      />

      <div className="mt-6">
        <OrganizationReferenceManager portal={portal} onSessionExpired={onSessionExpired} />
      </div>
    </>
  )
}
