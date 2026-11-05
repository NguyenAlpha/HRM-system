import type { AuthPermission, AuthRole, Portal } from "@/lib/auth/types"

interface PortalConfig {
  accessCookie: string
  refreshCookie: string
  apiPrefix: string
  loginPath: string
  homePath: string
  /** Prefix of the portal's own pages, e.g. `${pagePrefix}/employees`. */
  pagePrefix: string
}

export const PORTAL_CONFIG: Record<Portal, PortalConfig> = {
  hrm: {
    accessCookie: "hrm_access_token",
    refreshCookie: "hrm_refresh_token",
    apiPrefix: "/api/session",
    loginPath: "/login",
    homePath: "/dashboard",
    pagePrefix: "",
  },
  admin: {
    accessCookie: "hrm_admin_access_token",
    refreshCookie: "hrm_admin_refresh_token",
    apiPrefix: "/api/admin-session",
    loginPath: "/admin/login",
    homePath: "/admin",
    pagePrefix: "/admin",
  },
}

export function isSystemAdmin(roles: AuthRole[]): boolean {
  return roles.some((role) => role.code === "SYSTEM_ADMIN")
}

/**
 * Codes of the permissions held on a COMPANY-scope role assignment. The API runs operations that cover the whole
 * company (payroll periods, preparing the month's attendance, allowance policies) only for these, so a holder
 * scoped to one unit or location must not be offered them on the permission code alone.
 */
export function companyScopePermissions(permissions: AuthPermission[]): Set<string> {
  return new Set(permissions.filter((permission) => permission.companyScope).map((permission) => permission.code))
}
