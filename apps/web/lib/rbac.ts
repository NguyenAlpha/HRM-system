"use client"

import { authorizedRequest } from "@/lib/auth/client"
import { AuthApiError, type Portal } from "@/lib/auth/types"
import type { RoleGrantPolicy } from "@/lib/role-assignment"

export const PERMISSION_MODULES = ["EMPLOYEE", "ACCOUNT", "ORGANIZATION", "REQUEST", "ATTENDANCE", "PAYROLL", "RBAC", "REPORT"] as const
export type PermissionModule = typeof PERMISSION_MODULES[number]
export type PermissionAssignmentPolicy = "DELEGABLE" | "SYSTEM_ONLY"

export interface Role {
  id: number
  code: string
  name: string
  description: string | null
  isSystem: boolean
  grantPolicy: RoleGrantPolicy
}

export interface Permission {
  id: number
  code: string
  name: string
  module: PermissionModule
  description: string
  assignmentPolicy: PermissionAssignmentPolicy
  isActive: boolean
}

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
