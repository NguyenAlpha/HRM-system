"use client"

import { usePortalSession } from "@/components/auth/portal-shell"
import { DirectRoleGrantManager } from "@/components/role-assignment/direct-role-grant-manager"
import { PermissionOverrideManager } from "@/components/role-assignment/permission-override-manager"

export function DirectRoleGrantPage() {
  const { session, onSessionExpired } = usePortalSession()
  const canManageOverrides = session.account.permissions.some(
    (permission) => permission.code === "account.permission.override.manage",
  )

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">HRM WORKSPACE</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Cấp quyền trực tiếp</h1>
          <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
            Gán role trực tiếp cho nhân sự và cấp mã kích hoạt tài khoản mà không cần chờ duyệt.
          </p>
        </div>
      </header>

      <div className="mt-6 grid gap-6">
        <DirectRoleGrantManager portal="hrm" onSessionExpired={onSessionExpired} />
        {canManageOverrides && (
          <PermissionOverrideManager portal="hrm" onSessionExpired={onSessionExpired} />
        )}
      </div>
    </>
  )
}
