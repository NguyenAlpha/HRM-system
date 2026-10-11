import type { components } from "@/lib/api-schema"

export type Portal = "hrm" | "admin"

// DTO types come from the API's OpenAPI spec; run `npm run gen:api` after changing them.
type Schemas = components["schemas"]

export type ApiErrorDetail = Schemas["ErrorDetail"]
export type AccountSummary = Schemas["AuthAccountResponse"]
export type AuthEmployee = Schemas["AuthEmployeeResponse"]
export type AuthRole = Schemas["AuthRoleResponse"]
export type AuthPermission = Schemas["AuthPermissionResponse"]
export type BackendAuthData = Schemas["AuthResponse"]

export interface ApiEnvelope<T> {
  success: boolean
  data: T | null
  error: ApiErrorDetail | null
}

export interface SessionData {
  account: AccountSummary
  expiresAt: string
}

export class AuthApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code: string,
    public readonly field: string | null = null,
  ) {
    super(message)
    this.name = "AuthApiError"
  }
}
