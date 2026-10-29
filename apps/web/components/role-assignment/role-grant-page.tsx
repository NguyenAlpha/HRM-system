"use client"

import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { DirectRoleGrantManager } from "@/components/role-assignment/direct-role-grant-manager"
import { DirectorAppointmentCard } from "@/components/role-assignment/director-appointment-card"
import { PermissionOverrideManager } from "@/components/role-assignment/permission-override-manager"

/** Direct role grants; the same page serves both portals. */
export function RoleGrantPage() {
  const { portal, session, onSessionExpired } = usePortalSession()
  const canManageOverrides = session.account.permissions.some(
    (permission) => permission.code === "account.permission.override.manage",
  )

  return (
    <>
      <PageHeader
        title="Cấp quyền trực tiếp"
        description="Gán role trực tiếp cho nhân sự và cấp mã kích hoạt tài khoản mà không cần chờ duyệt."
        backToOverview
      />

      <div className="mt-6 grid gap-6">
        <DirectRoleGrantManager portal={portal} onSessionExpired={onSessionExpired} />
        <DirectorAppointmentCard portal={portal} onSessionExpired={onSessionExpired} />
        {canManageOverrides && (
          <PermissionOverrideManager portal={portal} onSessionExpired={onSessionExpired} />
        )}
      </div>
    </>
  )
}
