"use client"

import type { components } from "@/lib/api-schema"
import { authorizedRequest } from "@/lib/auth/client"
import type { Portal } from "@/lib/auth/types"

// DTO types come from the API's OpenAPI spec; run `npm run gen:api` after changing them.
type Schemas = components["schemas"]

export type RoleAssignmentOption = Schemas["RoleAssignmentOptionResponse"]
export type AccountRoleAssignment = Schemas["AccountRoleAssignmentResponse"]
export type PermissionOverrideOption = Schemas["PermissionOverrideOptionResponse"]
export type AccountPermissionOverride = Schemas["AccountPermissionOverrideResponse"]
export type RoleAssignmentRequest = Schemas["RoleAssignmentRequestResponse"]
export type CreatePermissionOverrideInput = Schemas["CreatePermissionOverrideRequest"]
export type CreateRoleAssignmentRequestInput = Schemas["CreateRoleAssignmentRequest"]
export type DirectRoleAssignmentInput = Schemas["AssignAccountRoleRequest"]

export type RoleScopeType = AccountRoleAssignment["scopeType"]
export type RoleGrantPolicy = RoleAssignmentRequest["grantPolicy"]
export type RoleAssignmentRequestStatus = RoleAssignmentRequest["status"]
export type PermissionOverrideEffect = AccountPermissionOverride["effect"]
export type PermissionOverrideStatus = AccountPermissionOverride["status"]

// The label records below must list every enum value, so a value added in the API fails typecheck.
export const ROLE_SCOPE_TYPES = ["SELF", "COMPANY", "ORG_UNIT", "LOCATION"] as const satisfies readonly RoleScopeType[]

export const ROLE_ASSIGNMENT_STATUSES = [
  "PENDING", "APPROVED", "REJECTED", "CANCELLED",
] as const satisfies readonly RoleAssignmentRequestStatus[]

export const ROLE_ASSIGNMENT_STATUS_LABELS: Record<RoleAssignmentRequestStatus, string> = {
  PENDING: "Chờ duyệt",
  APPROVED: "Đã duyệt",
  REJECTED: "Từ chối",
  CANCELLED: "Đã hủy",
}

export const ROLE_SCOPE_LABELS: Record<RoleScopeType, string> = {
  SELF: "Bản thân",
  COMPANY: "Toàn công ty",
  ORG_UNIT: "Đơn vị tổ chức",
  LOCATION: "Địa điểm làm việc",
}

export const PERMISSION_OVERRIDE_EFFECT_LABELS: Record<PermissionOverrideEffect, string> = {
  GRANT: "Cấp thêm",
  REVOKE: "Thu hồi",
}

export const PERMISSION_OVERRIDE_STATUS_LABELS: Record<PermissionOverrideStatus, string> = {
  SCHEDULED: "Đã lên lịch",
  ACTIVE: "Đang hiệu lực",
  EXPIRED: "Đã hết hạn",
  REVOKED: "Đã thu hồi",
}

export interface Page<T> {
  content: T[]
  number: number
  totalElements: number
  totalPages: number
}

function roleAssignmentRequestApi<T>(path: string, init?: RequestInit): Promise<T> {
  return authorizedRequest<T>("hrm", `/role-assignment-requests${path}`, { ...init, cache: "no-store" })
}

function roleAssignmentMutation<T>(path: string, body?: unknown): Promise<T> {
  return roleAssignmentRequestApi<T>(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export function getAvailableRoles(): Promise<RoleAssignmentOption[]> {
  return roleAssignmentRequestApi<RoleAssignmentOption[]>("/available-roles")
}

export function createRoleAssignmentRequest(input: CreateRoleAssignmentRequestInput): Promise<RoleAssignmentRequest> {
  return roleAssignmentMutation<RoleAssignmentRequest>("", input)
}

export function listRoleAssignmentRequests(page: number, status?: RoleAssignmentRequestStatus | ""): Promise<Page<RoleAssignmentRequest>> {
  const params = new URLSearchParams({ page: String(page), size: "10", sort: "requestedAt,desc" })
  if (status) params.set("status", status)
  return roleAssignmentRequestApi<Page<RoleAssignmentRequest>>(`?${params}`)
}

export function approveRoleAssignmentRequest(id: number, note?: string): Promise<RoleAssignmentRequest> {
  return roleAssignmentMutation<RoleAssignmentRequest>(`/${id}/approve`, { note: note?.trim() || null } satisfies Schemas["ApproveRoleAssignmentRequest"])
}

export function rejectRoleAssignmentRequest(id: number, note: string): Promise<RoleAssignmentRequest> {
  return roleAssignmentMutation<RoleAssignmentRequest>(`/${id}/reject`, { note } satisfies Schemas["RejectRoleAssignmentRequest"])
}

export function cancelRoleAssignmentRequest(id: number, reason: string): Promise<RoleAssignmentRequest> {
  return roleAssignmentMutation<RoleAssignmentRequest>(`/${id}/cancel`, { reason } satisfies Schemas["CancelRoleAssignmentRequest"])
}

function accountRoleAssignmentApi<T>(portal: Portal, accountId: number, path: string, init?: RequestInit): Promise<T> {
  return authorizedRequest<T>(portal, `/accounts/${accountId}/role-assignments${path}`, { ...init, cache: "no-store" })
}

export function listAccountRoleAssignments(portal: Portal, accountId: number): Promise<AccountRoleAssignment[]> {
  return accountRoleAssignmentApi<AccountRoleAssignment[]>(portal, accountId, "")
}

export function assignAccountRoleDirect(portal: Portal, accountId: number, input: DirectRoleAssignmentInput): Promise<AccountRoleAssignment> {
  return accountRoleAssignmentApi<AccountRoleAssignment>(portal, accountId, "", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  })
}

export function listPermissionOverrides(
  portal: Portal,
  accountId: number,
  assignmentId: number,
): Promise<AccountPermissionOverride[]> {
  return accountRoleAssignmentApi<AccountPermissionOverride[]>(
    portal,
    accountId,
    `/${assignmentId}/permission-overrides`,
  )
}

export function listPermissionOverrideOptions(
  portal: Portal,
  accountId: number,
  assignmentId: number,
): Promise<PermissionOverrideOption[]> {
  return accountRoleAssignmentApi<PermissionOverrideOption[]>(
    portal,
    accountId,
    `/${assignmentId}/permission-overrides/available-permissions`,
  )
}

export function createPermissionOverride(
  portal: Portal,
  accountId: number,
  assignmentId: number,
  input: CreatePermissionOverrideInput,
): Promise<AccountPermissionOverride> {
  return accountRoleAssignmentApi<AccountPermissionOverride>(
    portal,
    accountId,
    `/${assignmentId}/permission-overrides`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    },
  )
}

export function revokePermissionOverride(
  portal: Portal,
  accountId: number,
  assignmentId: number,
  overrideId: number,
  reason: string,
): Promise<AccountPermissionOverride> {
  return accountRoleAssignmentApi<AccountPermissionOverride>(
    portal,
    accountId,
    `/${assignmentId}/permission-overrides/${overrideId}/revoke`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ reason } satisfies Schemas["RevokePermissionOverrideRequest"]),
    },
  )
}
