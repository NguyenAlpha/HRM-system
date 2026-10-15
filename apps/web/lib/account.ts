"use client"

import type { components } from "@/lib/api-schema"
import { authorizedRequest } from "@/lib/auth/client"
import type { Portal } from "@/lib/auth/types"

// DTO types come from the API's OpenAPI spec; run `npm run gen:api` after changing them.
type Schemas = components["schemas"]

export type ProvisionedAccount = Schemas["AccountResponse"]
export type AccountInvitation = Schemas["AccountInvitationResponse"]
export type AccountProvisioningResponse = Schemas["AccountProvisioningResponse"]
type CreateAccountInput = Schemas["CreateAccountRequest"]

export function provisionAccount(portal: Portal, employeeId: number, username: string): Promise<AccountProvisioningResponse> {
  return authorizedRequest<AccountProvisioningResponse>(portal, "/accounts", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ employeeId, username } satisfies CreateAccountInput),
    cache: "no-store",
  })
}

export type Account = ProvisionedAccount
export type AccountPermissionGrant = Schemas["AccountPermissionGrantResponse"]
export type AccountPage = Schemas["PagedResultAccountResponse"]

/** Filters of the account list; an empty string means "no filter". */
export interface AccountFilters {
  keyword: string
  status: "" | Account["status"]
  roleCode: string
  /** Shows the soft-deleted accounts instead of the others. */
  deleted: boolean
}

export function listAccounts(portal: Portal, page: number, filters: AccountFilters, sort: string): Promise<AccountPage> {
  const params = new URLSearchParams({ page: String(page), size: "20", sort })
  if (filters.keyword.trim()) params.set("keyword", filters.keyword.trim())
  if (filters.status) params.set("status", filters.status)
  if (filters.roleCode) params.set("roleCode", filters.roleCode)
  if (filters.deleted) params.set("deleted", "true")
  return authorizedRequest<AccountPage>(portal, `/accounts?${params}`, { cache: "no-store" })
}

/** Resend an invitation (PENDING) or start a password reset (ACTIVE/LOCKED); both return a new one-time token. */
export function issueAccountInvitation(
  portal: Portal,
  accountId: number,
  action: "invitations/resend" | "password-reset",
): Promise<AccountInvitation> {
  return authorizedRequest<AccountInvitation>(portal, `/accounts/${accountId}/${action}`, { method: "POST", cache: "no-store" })
}

export function changeAccountStatus(portal: Portal, accountId: number, action: "suspend" | "activate"): Promise<Account> {
  return authorizedRequest<Account>(portal, `/accounts/${accountId}/${action}`, { method: "POST", cache: "no-store" })
}

/** Soft-deletes an account for good; the reason is kept with it. */
export function deleteAccount(portal: Portal, accountId: number, reason: string): Promise<Account> {
  return authorizedRequest<Account>(portal, `/accounts/${accountId}/delete`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ reason }),
    cache: "no-store",
  })
}

/** Today's permissions of the account, each with the role assignments that give it. */
export function getAccountPermissions(portal: Portal, accountId: number): Promise<AccountPermissionGrant[]> {
  return authorizedRequest<AccountPermissionGrant[]>(portal, `/accounts/${accountId}/permissions`, { cache: "no-store" })
}

export function getAccount(portal: Portal, accountId: number): Promise<Account> {
  return authorizedRequest<Account>(portal, `/accounts/${accountId}`, { cache: "no-store" })
}
