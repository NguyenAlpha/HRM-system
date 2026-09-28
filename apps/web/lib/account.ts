"use client"

import { authorizedRequest } from "@/lib/auth/client"
import type { Portal } from "@/lib/auth/types"

export interface ProvisionedAccount {
  id: number
  employeeId: number
  username: string
  email: string
  status: string
  createdAt: string
}

export interface AccountInvitation {
  accountId: number
  activationToken: string
  expiresAt: string
}

export interface AccountProvisioningResponse {
  account: ProvisionedAccount
  invitation: AccountInvitation
}

export function provisionAccount(portal: Portal, employeeId: number, username: string): Promise<AccountProvisioningResponse> {
  return authorizedRequest<AccountProvisioningResponse>(portal, "/accounts", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ employeeId, username }),
    cache: "no-store",
  })
}
