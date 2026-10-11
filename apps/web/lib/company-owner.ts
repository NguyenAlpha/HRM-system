"use client"

import type { components } from "@/lib/api-schema"
import { authorizedRequest } from "@/lib/auth/client"

// DTO types come from the API's OpenAPI spec; run `npm run gen:api` after changing them.
type Schemas = components["schemas"]

export type CompanyOwnerProvisioningResult = Schemas["OrganizationCompanyOwnerProvisioningResponse"]
export type BootstrapCompanyOwnerInput = Schemas["CreateOrganizationCompanyOwnerRequest"]

export function bootstrapCompanyOwner(input: BootstrapCompanyOwnerInput): Promise<CompanyOwnerProvisioningResult> {
  return authorizedRequest<CompanyOwnerProvisioningResult>("admin", "/company-owner", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
    cache: "no-store",
  })
}
