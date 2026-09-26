import type { AuthRole, Portal } from "@/lib/auth/types"

interface PortalConfig {
  accessCookie: string
  refreshCookie: string
  apiPrefix: string
  loginPath: string
  homePath: string
}

export const PORTAL_CONFIG: Record<Portal, PortalConfig> = {
  hrm: {
    accessCookie: "hrm_access_token",
    refreshCookie: "hrm_refresh_token",
    apiPrefix: "/api/session",
    loginPath: "/login",
    homePath: "/dashboard",
  },
  admin: {
    accessCookie: "hrm_admin_access_token",
    refreshCookie: "hrm_admin_refresh_token",
    apiPrefix: "/api/admin-session",
    loginPath: "/admin/login",
    homePath: "/admin",
  },
}

export function isSystemAdmin(roles: AuthRole[]): boolean {
  return roles.some((role) => role.code === "SYSTEM_ADMIN")
}
