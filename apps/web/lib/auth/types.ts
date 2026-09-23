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
  id: number
  employeeId: number | null
  username: string
  email: string
  status: string
  roles: string[]
  permissions: string[]
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
