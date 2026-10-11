"use client"

import { CompanyOwnerProvision } from "@/components/company-owner/company-owner-provision"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"

export function CompanyOwnerPage() {
  const { onSessionExpired } = usePortalSession()

  return (
    <>
      <PageHeader
        title="Khởi tạo Company Owner"
        description="Cầu nối từ vận hành nền tảng sang quản trị nội bộ doanh nghiệp — chỉ dùng một lần cho Company Owner đầu tiên."
        backToOverview
      />

      <div className="mt-6 max-w-2xl">
        <CompanyOwnerProvision onSessionExpired={onSessionExpired} />
      </div>
    </>
  )
}
