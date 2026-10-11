"use client"

import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { RoleAssignmentManager } from "@/components/role-assignment/role-assignment-manager"

export function RoleAssignmentPage() {
  const { session, onSessionExpired } = usePortalSession()
  const canRequest = session.account.permissions.some((permission) => permission.code === "role.assignment.request")
  const canApprove = session.account.permissions.some((permission) => permission.code === "role.assignment.approve")

  return (
    <>
      <PageHeader
        title="Đề xuất cấp role"
        description="Đề xuất, theo dõi và phê duyệt việc cấp role nghiệp vụ cho tài khoản nhân sự."
        backToOverview
      />

      <div className="mt-6">
        <RoleAssignmentManager
          currentAccountId={session.account.accountId}
          canRequest={canRequest}
          canApprove={canApprove}
          onSessionExpired={onSessionExpired}
        />
      </div>
    </>
  )
}
