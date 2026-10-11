"use client"

import Link from "next/link"
import { ArrowLeft } from "lucide-react"

import { usePortalSession } from "@/components/portal/portal-shell"
import { DirectRoleGrantManager } from "@/components/role-assignment/direct-role-grant-manager"
import { PermissionOverrideManager } from "@/components/role-assignment/permission-override-manager"
import { Button } from "@/components/ui/button"
import { PORTAL_CONFIG } from "@/lib/auth/config"

/** Direct role grants; the same page serves both portals. */
export function RoleGrantPage() {
  const { portal, session, onSessionExpired } = usePortalSession()
  // SYSTEM_ADMIN is not seeded with this permission, so the override section stays HRM-only in practice.
  const canManageOverrides = session.account.permissions.some(
    (permission) => permission.code === "account.permission.override.manage",
  )

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">{portal === "admin" ? "ADMIN CONSOLE" : "HRM WORKSPACE"}</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Cấp quyền trực tiếp</h1>
          <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
            Gán role trực tiếp cho nhân sự và cấp mã kích hoạt tài khoản mà không cần chờ duyệt.
          </p>
        </div>
        <Button variant="outline" nativeButton={false} render={<Link href={PORTAL_CONFIG[portal].homePath} />}>
          <ArrowLeft /> Về tổng quan
        </Button>
      </header>

      <div className="mt-6 grid gap-6">
        <DirectRoleGrantManager portal={portal} onSessionExpired={onSessionExpired} />
        {canManageOverrides && (
          <PermissionOverrideManager portal={portal} onSessionExpired={onSessionExpired} />
        )}
      </div>
    </>
  )
}
