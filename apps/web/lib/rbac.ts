"use client"

import type { components } from "@/lib/api-schema"
import { authorizedRequest } from "@/lib/auth/client"
import { AuthApiError, type Portal } from "@/lib/auth/types"

// DTO types come from the API's OpenAPI spec; run `npm run gen:api` after changing them.
type Schemas = components["schemas"]

export type Role = Schemas["RoleResponse"]
export type Permission = Schemas["PermissionResponse"]
export type CreateRoleInput = Schemas["CreateRoleRequest"]
export type UpdateRoleInput = Schemas["UpdateRoleRequest"]
export type ReplaceRolePermissionsInput = Schemas["ReplaceRolePermissionsRequest"]

export type PermissionModule = Permission["module"]
export type PermissionAssignmentPolicy = Permission["assignmentPolicy"]

// A Record must name every enum value, so a module added in the API fails typecheck here.
const PERMISSION_MODULE_SET: Record<PermissionModule, true> = {
  EMPLOYEE: true,
  ACCOUNT: true,
  ORGANIZATION: true,
  REQUEST: true,
  ATTENDANCE: true,
  PAYROLL: true,
  RBAC: true,
  REPORT: true,
}
export const PERMISSION_MODULES = Object.keys(PERMISSION_MODULE_SET) as PermissionModule[]

export interface Page<T> {
  content: T[]
  number: number
  totalElements: number
  totalPages: number
}

export function rbacRequest<T>(portal: Portal, path: string, init?: RequestInit): Promise<T> {
  return authorizedRequest<T>(portal, `/rbac${path}`, { ...init, cache: "no-store" })
}

export function rbacMutation<T = null>(portal: Portal, path: string, method: "POST" | "PUT" | "DELETE", body?: unknown): Promise<T> {
  return rbacRequest<T>(portal, path, {
    method,
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export async function getPermissionOptions(portal: Portal): Promise<Permission[]> {
  const permissions: Permission[] = []
  let page = 0
  let totalPages = 1
  while (page < totalPages) {
    const result = await rbacRequest<Page<Permission>>(portal, `/permissions?page=${page}&size=100&sort=code,asc`)
    permissions.push(...result.content)
    totalPages = result.totalPages
    page++
  }
  return permissions
}

export function rbacErrorMessage(error: unknown): string {
  if (error instanceof AuthApiError) {
    return `${error.code}: ${error.message}${error.field ? ` (${error.field})` : ""}`
  }
  return error instanceof Error ? error.message : "Không thể hoàn tất thao tác. Vui lòng thử lại."
}

export function isSessionExpired(error: unknown): boolean {
  return error instanceof AuthApiError
    && (error.status === 401 || error.code === "ADMIN_ACCESS_REQUIRED" || error.code === "ADMIN_PORTAL_REQUIRED")
}
