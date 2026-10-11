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
