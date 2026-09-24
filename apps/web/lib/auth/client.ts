"use client"

import { PORTAL_CONFIG } from "@/lib/auth/config"
import {
  AuthApiError,
  type ApiEnvelope,
  type Portal,
  type SessionData,
} from "@/lib/auth/types"

const refreshRequests = new Map<Portal, Promise<SessionData>>()

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

    await refreshSession(portal)
    return request<T>(portal, path, init)
  }
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
