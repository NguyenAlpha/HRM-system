"use client"

import { isPortalSessionExpired } from "@/lib/api-helpers"
import { PORTAL_CONFIG } from "@/lib/auth/config"
import {
  AuthApiError,
  type ApiEnvelope,
  type Portal,
  type SessionData,
} from "@/lib/auth/types"

const refreshRequests = new Map<Portal, Promise<SessionData>>()
const sessionGoneHandlers = new Map<Portal, () => void>()

/**
 * What to do when a request finds the portal's session gone for good (the refresh is refused); PortalShell
 * sends the user back to sign in. Pass null to remove it.
 */
export function setSessionGoneHandler(portal: Portal, handler: (() => void) | null): void {
  if (handler) sessionGoneHandlers.set(portal, handler)
  else sessionGoneHandlers.delete(portal)
}

/** Refreshes once for a request that hit an expired access token; a refused refresh ends the session. */
async function refreshForRetry(portal: Portal): Promise<void> {
  try {
    await refreshSession(portal)
  } catch (error) {
    if (isPortalSessionExpired(error)) sessionGoneHandlers.get(portal)?.()
    throw error
  }
}

async function request<T>(portal: Portal, path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${PORTAL_CONFIG[portal].apiPrefix}${path}`, {
      ...init,
      headers: {
        Accept: "application/json",
        ...init?.headers,
      },
    })
  } catch {
    throw new AuthApiError("Không thể kết nối tới máy chủ", 0, "NETWORK_ERROR")
  }

  const payload = (await response.json().catch(() => null)) as ApiEnvelope<T> | null
  if (!response.ok || !payload?.success) {
    throw new AuthApiError(
      payload?.error?.message ?? "Yêu cầu không thể hoàn tất",
      response.status,
      payload?.error?.code ?? "REQUEST_FAILED",
      payload?.error?.field ?? null,
    )
  }

  return payload.data as T
}

export function login(
  portal: Portal,
  usernameOrEmail: string,
  password: string,
): Promise<SessionData> {
  return request<SessionData>(portal, "/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ usernameOrEmail, password }),
  })
}

export function refreshSession(portal: Portal): Promise<SessionData> {
  const pendingRequest = refreshRequests.get(portal)
  if (pendingRequest) {
    return pendingRequest
  }

  const refreshRequest = request<SessionData>(portal, "/refresh", { method: "POST" })
    .finally(() => refreshRequests.delete(portal))
  refreshRequests.set(portal, refreshRequest)
  return refreshRequest
}

export async function authorizedRequest<T>(
  portal: Portal,
  path: string,
  init?: RequestInit,
): Promise<T> {
  try {
    return await request<T>(portal, path, init)
  } catch (error) {
    if (!(error instanceof AuthApiError) || error.code !== "UNAUTHORIZED") {
      throw error
    }

    await refreshForRetry(portal)
    return request<T>(portal, path, init)
  }
}

/** Downloads a file through the BFF, refreshing the session once like `authorizedRequest`. */
export async function authorizedDownload(portal: Portal, path: string): Promise<void> {
  const fetchFile = () => fetch(`${PORTAL_CONFIG[portal].apiPrefix}${path}`).catch(() => {
    throw new AuthApiError("Không thể kết nối tới máy chủ", 0, "NETWORK_ERROR")
  })
  let response = await fetchFile()
  if (response.status === 401) {
    await refreshForRetry(portal)
    response = await fetchFile()
  }
  if (!response.ok) {
    // Failures still come back as the JSON envelope.
    const payload = (await response.json().catch(() => null)) as ApiEnvelope<never> | null
    throw new AuthApiError(
      payload?.error?.message ?? "Không thể tải file",
      response.status,
      payload?.error?.code ?? "REQUEST_FAILED",
      payload?.error?.field ?? null,
    )
  }
  const disposition = response.headers.get("Content-Disposition") ?? ""
  const fileName = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? "download"
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement("a")
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

export function getCurrentSession(portal: Portal): Promise<SessionData> {
  return authorizedRequest<SessionData>(portal, "/me")
}

export function logout(portal: Portal): Promise<null> {
  return request<null>(portal, "/logout", { method: "POST" })
}

export function changePassword(
  portal: Portal,
  currentPassword: string,
  newPassword: string,
): Promise<null> {
  return authorizedRequest<null>(portal, "/change-password", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ currentPassword, newPassword }),
  })
}
