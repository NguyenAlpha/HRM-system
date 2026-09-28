"use client"

import { authorizedRequest } from "@/lib/auth/client"

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

export function provisionAccount(employeeId: number, username: string): Promise<AccountProvisioningResponse> {
  return authorizedRequest<AccountProvisioningResponse>("hrm", "/accounts", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ employeeId, username }),
    cache: "no-store",
  })
}
