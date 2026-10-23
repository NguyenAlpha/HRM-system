"use client"

import type { components } from "@/lib/api-schema"
import { authorizedRequest } from "@/lib/auth/client"

// DTO types come from the API's OpenAPI spec; run `npm run gen:api` after changing them.
type Schemas = components["schemas"]

export type ApiTraffic = Schemas["ApiTrafficResponse"]
export type EndpointTraffic = Schemas["EndpointTraffic"]

export function getApiTraffic(): Promise<ApiTraffic> {
  return authorizedRequest<ApiTraffic>("admin", "/traffic", { cache: "no-store" })
}
