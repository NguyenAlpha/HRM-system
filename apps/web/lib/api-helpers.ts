"use client"

import { AuthApiError } from "@/lib/auth/types"

export function apiErrorMessage(error: unknown): string {
  if (error instanceof AuthApiError) {
    return `${error.code}: ${error.message}${error.field ? ` (${error.field})` : ""}`
  }
  return error instanceof Error ? error.message : "Không thể hoàn tất thao tác. Vui lòng thử lại."
}

export function isPortalSessionExpired(error: unknown): boolean {
  return error instanceof AuthApiError
    && (error.status === 401 || error.code === "ADMIN_PORTAL_REQUIRED" || error.code === "ADMIN_ACCESS_REQUIRED")
}
