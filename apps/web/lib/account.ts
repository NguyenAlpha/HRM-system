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
export type AccountPage = Schemas["PagedResultAccountResponse"]

export function listAccounts(portal: Portal, page: number): Promise<AccountPage> {
  return authorizedRequest<AccountPage>(portal, `/accounts?page=${page}&size=20&sort=id,desc`, { cache: "no-store" })
}

/** Resend an invitation (PENDING) or start a password reset (ACTIVE/LOCKED); both return a new one-time token. */
export function issueAccountInvitation(accountId: number, action: "invitations/resend" | "password-reset"): Promise<AccountInvitation> {
  return authorizedRequest<AccountInvitation>("hrm", `/accounts/${accountId}/${action}`, { method: "POST", cache: "no-store" })
}

export function changeAccountStatus(accountId: number, action: "suspend" | "activate"): Promise<Account> {
  return authorizedRequest<Account>("hrm", `/accounts/${accountId}/${action}`, { method: "POST", cache: "no-store" })
}
