"use client"

import type { components } from "@/lib/api-schema"
import { authorizedRequest } from "@/lib/auth/client"
import type { Portal } from "@/lib/auth/types"
import type { Page } from "@/lib/pagination"

// DTO types come from the API's OpenAPI spec; run `npm run gen:api` after changing them.
type Schemas = components["schemas"]

export type Role = Schemas["RoleResponse"]
export type Permission = Schemas["PermissionResponse"]
export type RoleWithPermissions = Schemas["RoleWithPermissionsResponse"]
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
