"use client"

import { AuthApiError, type ApiEnvelope } from "@/lib/auth/types"

export function completeAccountActivation(
  token: string,
  password: string,
  passwordConfirmation: string,
): Promise<null> {
  return request(token, { password, passwordConfirmation })
}

async function request(token: string, body: unknown): Promise<null> {
  let response: Response
  try {
    response = await fetch(`/api/account-activations/${encodeURIComponent(token)}/complete`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    })
  } catch {
    throw new AuthApiError("Không thể kết nối tới máy chủ", 0, "NETWORK_ERROR")
  }

  const payload = (await response.json().catch(() => null)) as ApiEnvelope<null> | null
  if (!response.ok || !payload?.success) {
    throw new AuthApiError(
      payload?.error?.message ?? "Yêu cầu không thể hoàn tất",
      response.status,
      payload?.error?.code ?? "REQUEST_FAILED",
      payload?.error?.field ?? null,
    )
  }

  return payload.data
}
