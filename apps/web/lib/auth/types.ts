export type Portal = "hrm" | "admin"

export interface ApiErrorDetail {
  code: string
  message: string
  field: string | null
}

export interface ApiEnvelope<T> {
  success: boolean
  data: T | null
  error: ApiErrorDetail | null
}

export interface AccountSummary {
  accountId: number
  username: string
  email: string
  status: string
  employee: AuthEmployee | null
  roles: AuthRole[]
  permissions: AuthPermission[]
}

export interface AuthEmployee {
  id: number
  employeeCode: string
  fullName: string
}

export interface AuthRole {
  code: string
  name: string
  scopeType: "SELF" | "ORG_UNIT" | "LOCATION" | "COMPANY"
  organizationUnitId: number | null
  organizationUnitName: string | null
  workLocationId: number | null
  workLocationName: string | null
}

export interface AuthPermission {
  code: string
  name: string
  module: string
}

export interface BackendAuthData {
  accessToken: string
  refreshToken: string
  tokenType: "Bearer"
  expiresIn: number
  account: AccountSummary
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
