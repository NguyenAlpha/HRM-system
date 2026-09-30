"use client"

import { authorizedRequest } from "@/lib/auth/client"
import type { Portal } from "@/lib/auth/types"

export const ROLE_SCOPE_TYPES = ["SELF", "COMPANY", "ORG_UNIT", "LOCATION"] as const
export type RoleScopeType = typeof ROLE_SCOPE_TYPES[number]

export type RoleGrantPolicy = "HR_ASSIGNABLE" | "OWNER_APPROVAL" | "AUTO" | "SYSTEM_ONLY"

export const ROLE_ASSIGNMENT_STATUSES = ["PENDING", "APPROVED", "REJECTED", "CANCELLED"] as const
export type RoleAssignmentRequestStatus = typeof ROLE_ASSIGNMENT_STATUSES[number]

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

export interface RoleAssignmentOption {
  roleId: number
  roleCode: string
  roleName: string
  roleDescription: string | null
  grantPolicy: RoleGrantPolicy
  requiresApproval: boolean
  allowedScopeTypes: RoleScopeType[]
}

export interface AccountRoleAssignment {
  id: number
  accountId: number
  roleId: number
  roleCode: string
  roleName: string
  scopeType: RoleScopeType
  organizationUnitId: number | null
  workLocationId: number | null
  effectiveFrom: string
  effectiveTo: string | null
  grantedByAccountId: number
  reason: string
  createdAt: string
  revokedByAccountId: number | null
  revokedAt: string | null
  revocationReason: string | null
}

export type PermissionOverrideEffect = "GRANT" | "REVOKE"
export type PermissionOverrideStatus = "SCHEDULED" | "ACTIVE" | "EXPIRED" | "REVOKED"

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

export interface PermissionOverrideOption {
  permissionId: number
  permissionCode: string
  permissionName: string
  module: string
  description: string
  effect: PermissionOverrideEffect
}

export interface AccountPermissionOverride {
  id: number
  accountRoleAssignmentId: number
  accountId: number
  permissionId: number
  permissionCode: string
  permissionName: string
  module: string
  effect: PermissionOverrideEffect
  effectiveFrom: string
  effectiveTo: string | null
  reason: string
  grantedByAccountId: number
  grantedByUsername: string
  createdAt: string
  revokedByAccountId: number | null
  revokedByUsername: string | null
  revokedAt: string | null
  revocationReason: string | null
  status: PermissionOverrideStatus
}

export interface CreatePermissionOverrideInput {
  permissionId: number
  effect: PermissionOverrideEffect
  effectiveFrom: string
  effectiveTo: string | null
  reason: string
}

export interface RoleAssignmentRequest {
  id: number
  accountId: number
  employeeId: number
  employeeCode: string
  employeeName: string
  roleId: number
  roleCode: string
  roleName: string
  grantPolicy: RoleGrantPolicy
  scopeType: RoleScopeType
  organizationUnitId: number | null
  organizationUnitName: string | null
  workLocationId: number | null
  workLocationName: string | null
  effectiveFrom: string
  effectiveTo: string | null
  reason: string
  status: RoleAssignmentRequestStatus
  requestedByAccountId: number
  requestedByUsername: string
  requestedAt: string
  reviewedByAccountId: number | null
  reviewedByUsername: string | null
  reviewedAt: string | null
  reviewNote: string | null
  cancelledByAccountId: number | null
  cancelledByUsername: string | null
  cancelledAt: string | null
  cancellationReason: string | null
  updatedAt: string
  assignment: AccountRoleAssignment | null
}

export interface Page<T> {
  content: T[]
  number: number
  totalElements: number
  totalPages: number
}

export interface CreateRoleAssignmentRequestInput {
  accountId: number
  roleCode: string
  scopeType: RoleScopeType
  organizationUnitId: number | null
  workLocationId: number | null
  effectiveFrom: string
  effectiveTo: string | null
  reason: string
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
  return roleAssignmentMutation<RoleAssignmentRequest>(`/${id}/approve`, { note: note?.trim() || null })
}

export function rejectRoleAssignmentRequest(id: number, note: string): Promise<RoleAssignmentRequest> {
  return roleAssignmentMutation<RoleAssignmentRequest>(`/${id}/reject`, { note })
}

export function cancelRoleAssignmentRequest(id: number, reason: string): Promise<RoleAssignmentRequest> {
  return roleAssignmentMutation<RoleAssignmentRequest>(`/${id}/cancel`, { reason })
}

export interface DirectRoleAssignmentInput {
  roleCode: string
  scopeType: RoleScopeType
  organizationUnitId: number | null
  workLocationId: number | null
  effectiveFrom: string
  effectiveTo: string | null
  reason: string
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
      body: JSON.stringify({ reason }),
    },
  )
}
