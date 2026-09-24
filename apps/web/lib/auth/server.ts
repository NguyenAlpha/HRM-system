import { cookies } from "next/headers"
import { NextResponse } from "next/server"

import { isSystemAdmin, PORTAL_CONFIG } from "@/lib/auth/config"
import type {
  AccountSummary,
  ApiEnvelope,
  BackendAuthData,
  Portal,
  SessionData,
} from "@/lib/auth/types"

const apiBaseUrl = process.env.API_URL ?? process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"
const refreshCookieMaxAge = Number(process.env.AUTH_REFRESH_COOKIE_MAX_AGE_SECONDS ?? 2_592_000)
const secureCookies = process.env.AUTH_COOKIE_SECURE === undefined
  ? process.env.NODE_ENV === "production"
  : process.env.AUTH_COOKIE_SECURE === "true"

const cookieOptions = {
  httpOnly: true,
  sameSite: "lax" as const,
  secure: secureCookies,
  path: "/",
}

function success<T>(data: T): ApiEnvelope<T> {
  return { success: true, data, error: null }
}

function failure(code: string, message: string, field: string | null = null): ApiEnvelope<never> {
  return { success: false, data: null, error: { code, message, field } }
}

function gatewayFailure(): NextResponse {
  return NextResponse.json(
    failure("API_UNAVAILABLE", "Không thể kết nối tới HRM API"),
    { status: 502 },
  )
}

async function callApi<T>(path: string, init?: RequestInit): Promise<{
  response: Response
  payload: ApiEnvelope<T>
} | null> {
  try {
    const response = await fetch(`${apiBaseUrl}${path}`, {
      ...init,
      cache: "no-store",
      headers: {
        Accept: "application/json",
        ...init?.headers,
      },
    })
    const payload = (await response.json().catch(() =>
      failure("INVALID_API_RESPONSE", "HRM API trả về dữ liệu không hợp lệ"))) as ApiEnvelope<T>
    return { response, payload }
  } catch {
    return null
  }
}

function sessionData(auth: BackendAuthData): SessionData {
  return {
    account: auth.account,
    expiresAt: new Date(Date.now() + auth.expiresIn * 1000).toISOString(),
  }
}

function jwtExpiresAt(token: string): string {
  try {
    const payload = JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString("utf8")) as { exp?: number }
    if (typeof payload.exp === "number") {
      return new Date(payload.exp * 1000).toISOString()
    }
  } catch {
    // The API has already validated the token. Missing display metadata is non-fatal.
  }
  return new Date().toISOString()
}

function portalMatches(portal: Portal, account: AccountSummary): boolean {
  const admin = isSystemAdmin(account.roles)
  return portal === "admin" ? admin : !admin
}

function setSessionCookies(response: NextResponse, portal: Portal, auth: BackendAuthData): void {
  const config = PORTAL_CONFIG[portal]
  response.cookies.set(config.accessCookie, auth.accessToken, {
    ...cookieOptions,
    maxAge: auth.expiresIn,
  })
  response.cookies.set(config.refreshCookie, auth.refreshToken, {
    ...cookieOptions,
    maxAge: refreshCookieMaxAge,
  })
}

function clearSessionCookies(response: NextResponse, portal: Portal): void {
  const config = PORTAL_CONFIG[portal]
  response.cookies.set(config.accessCookie, "", { ...cookieOptions, maxAge: 0 })
  response.cookies.set(config.refreshCookie, "", { ...cookieOptions, maxAge: 0 })
}

async function revokeAuthData(auth: BackendAuthData): Promise<void> {
  await callApi<null>("/api/auth/logout", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${auth.accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ refreshToken: auth.refreshToken }),
  })
}

function portalMismatch(portal: Portal): NextResponse {
  if (portal === "admin") {
    return NextResponse.json(
      failure("ADMIN_ACCESS_REQUIRED", "Tài khoản không có quyền truy cập cổng quản trị"),
      { status: 403 },
    )
  }
  return NextResponse.json(
    failure("ADMIN_PORTAL_REQUIRED", "Tài khoản quản trị phải đăng nhập tại cổng Admin"),
    { status: 403 },
  )
}

export async function handleLogin(request: Request, portal: Portal): Promise<NextResponse> {
  let body: unknown
  try {
    body = await request.json()
  } catch {
    return NextResponse.json(failure("VALIDATION_ERROR", "Dữ liệu đăng nhập không hợp lệ"), { status: 400 })
  }

  const result = await callApi<BackendAuthData>("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  })
  if (!result) {
    return gatewayFailure()
  }
  if (!result.response.ok || !result.payload.success || !result.payload.data) {
    return NextResponse.json(result.payload, { status: result.response.status })
  }

  const auth = result.payload.data
  if (!portalMatches(portal, auth.account)) {
    await revokeAuthData(auth)
    return portalMismatch(portal)
  }

  const response = NextResponse.json(success(sessionData(auth)))
  setSessionCookies(response, portal, auth)
  return response
}

export async function handleRefresh(portal: Portal): Promise<NextResponse> {
  const cookieStore = await cookies()
  const refreshToken = cookieStore.get(PORTAL_CONFIG[portal].refreshCookie)?.value
  if (!refreshToken) {
    const response = NextResponse.json(failure("UNAUTHORIZED", "Phiên đăng nhập không tồn tại"), { status: 401 })
    clearSessionCookies(response, portal)
    return response
  }

  const result = await callApi<BackendAuthData>("/api/auth/refresh", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  })
  if (!result) {
    return gatewayFailure()
  }
  if (!result.response.ok || !result.payload.success || !result.payload.data) {
    const response = NextResponse.json(result.payload, { status: result.response.status })
    clearSessionCookies(response, portal)
    return response
  }

  const auth = result.payload.data
  if (!portalMatches(portal, auth.account)) {
    await revokeAuthData(auth)
    const response = portalMismatch(portal)
    clearSessionCookies(response, portal)
    return response
  }

  const response = NextResponse.json(success(sessionData(auth)))
  setSessionCookies(response, portal, auth)
  return response
}

export async function handleMe(portal: Portal): Promise<NextResponse> {
  const cookieStore = await cookies()
  const accessToken = cookieStore.get(PORTAL_CONFIG[portal].accessCookie)?.value
  if (!accessToken) {
    return NextResponse.json(failure("UNAUTHORIZED", "Access token không tồn tại"), { status: 401 })
  }

  const result = await callApi<AccountSummary>("/api/auth/me", {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  if (!result) {
    return gatewayFailure()
  }
  if (!result.response.ok || !result.payload.success || !result.payload.data) {
    return NextResponse.json(result.payload, { status: result.response.status })
  }

  if (!portalMatches(portal, result.payload.data)) {
    const response = portalMismatch(portal)
    clearSessionCookies(response, portal)
    return response
  }

  return NextResponse.json(success({
    account: result.payload.data,
    expiresAt: jwtExpiresAt(accessToken),
  }))
}

export async function handleLogout(portal: Portal): Promise<NextResponse> {
  const cookieStore = await cookies()
  const config = PORTAL_CONFIG[portal]
  const accessToken = cookieStore.get(config.accessCookie)?.value
  const refreshToken = cookieStore.get(config.refreshCookie)?.value

  if (accessToken && refreshToken) {
    await callApi<null>("/api/auth/logout", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ refreshToken }),
    })
  }

  const response = NextResponse.json(success(null))
  clearSessionCookies(response, portal)
  return response
}

export async function handleChangePassword(request: Request, portal: Portal): Promise<NextResponse> {
  const cookieStore = await cookies()
  const accessToken = cookieStore.get(PORTAL_CONFIG[portal].accessCookie)?.value
  if (!accessToken) {
    return NextResponse.json(failure("UNAUTHORIZED", "Access token không tồn tại"), { status: 401 })
  }

  let body: unknown
  try {
    body = await request.json()
  } catch {
    return NextResponse.json(failure("VALIDATION_ERROR", "Dữ liệu đổi mật khẩu không hợp lệ"), { status: 400 })
  }

  const result = await callApi<null>("/api/auth/change-password", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  })
  if (!result) {
    return gatewayFailure()
  }

  const response = NextResponse.json(result.payload, { status: result.response.status })
  if (result.response.ok && result.payload.success) {
    clearSessionCookies(response, portal)
  }
  return response
}

// Only expose the role/permission endpoints, never an arbitrary upstream URL.
export async function handleRbacRequest(request: Request, segments: string[]): Promise<NextResponse> {
  const path = segments.join("/")
  const routes = [
    { pattern: /^(roles|permissions)$/, methods: ["GET", "POST"] },
    { pattern: /^(roles|permissions)\/[1-9]\d*$/, methods: ["GET", "PUT", "DELETE"] },
    { pattern: /^roles\/[1-9]\d*\/permissions$/, methods: ["GET", "POST"] },
    { pattern: /^roles\/[1-9]\d*\/permissions\/[1-9]\d*$/, methods: ["DELETE"] },
  ]
  const route = routes.find((candidate) => candidate.pattern.test(path))
  if (!route) {
    return NextResponse.json(failure("RESOURCE_NOT_FOUND", "API không tồn tại"), { status: 404 })
  }
  if (!route.methods.includes(request.method)) {
    return NextResponse.json(failure("METHOD_NOT_ALLOWED", "Phương thức không được hỗ trợ"), { status: 405 })
  }

  const origin = request.headers.get("origin")
  if (request.method !== "GET" && origin && origin !== new URL(request.url).origin) {
    return NextResponse.json(failure("FORBIDDEN", "Nguồn yêu cầu không hợp lệ"), { status: 403 })
  }

  const cookieStore = await cookies()
  const accessToken = cookieStore.get(PORTAL_CONFIG.admin.accessCookie)?.value
  if (!accessToken) {
    return NextResponse.json(failure("UNAUTHORIZED", "Phiên quản trị đã hết hạn"), { status: 401 })
  }

  let body: string | undefined
  if (request.method === "POST" || request.method === "PUT") {
    try {
      body = JSON.stringify(await request.json())
    } catch {
      return NextResponse.json(failure("VALIDATION_ERROR", "Dữ liệu JSON không hợp lệ"), { status: 400 })
    }
  }

  const query = new URLSearchParams()
  new URL(request.url).searchParams.forEach((value, key) => {
    if (["page", "size", "sort", "module"].includes(key)) query.append(key, value)
  })
  const result = await callApi<unknown>(`/api/${path}?${query}`, {
    method: request.method,
    headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
    body,
  })
  if (!result) return gatewayFailure()

  return NextResponse.json(result.payload, {
    status: result.response.status,
    headers: { "Cache-Control": "no-store" },
  })
}
