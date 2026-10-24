import type { ApiEnvelope } from "@/lib/auth/types"

export const RATE_LIMIT_FALLBACK_MESSAGE = "Bạn thao tác quá nhanh. Vui lòng thử lại sau ít phút."

/** "Thử lại sau N giây/phút/giờ", from the API's `Retry-After` (seconds). */
export function rateLimitMessage(retryAfter: string | null): string {
  const seconds = Number(retryAfter)
  if (!Number.isFinite(seconds) || seconds <= 0) return RATE_LIMIT_FALLBACK_MESSAGE
  // Quotas edited by an administrator can have windows of up to 24 hours.
  const wait = seconds < 60 ? `${Math.ceil(seconds)} giây`
    : seconds < 3600 ? `${Math.ceil(seconds / 60)} phút`
    : `${Math.ceil(seconds / 3600)} giờ`
  return `Bạn thao tác quá nhanh. Vui lòng thử lại sau ${wait}.`
}

/**
 * Rewrites a 429 from the API into a Vietnamese message with the wait time, which the browser
 * shows as it is; the `Retry-After` header itself does not survive the BFF.
 */
export function withRateLimitMessage<T>(response: Response, payload: ApiEnvelope<T>): ApiEnvelope<T> {
  if (response.status !== 429 || !payload.error) return payload
  return { ...payload, error: { ...payload.error, message: rateLimitMessage(response.headers.get("Retry-After")) } }
}
