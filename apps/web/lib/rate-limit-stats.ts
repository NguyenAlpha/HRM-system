"use client"

import type { components } from "@/lib/api-schema"
import { authorizedRequest } from "@/lib/auth/client"

// DTO types come from the API's OpenAPI spec; run `npm run gen:api` after changing them.
type Schemas = components["schemas"]

export type RateLimitStats = Schemas["RateLimitStatsResponse"]
export type RateLimitPolicyStats = Schemas["PolicyStats"]
export type RateLimitBlockedKey = Schemas["BlockedKeyStats"]

export function getRateLimitStats(): Promise<RateLimitStats> {
  return authorizedRequest<RateLimitStats>("admin", "/rate-limit", { cache: "no-store" })
}

export type RateLimitPolicyInput = Schemas["RateLimitPolicyRequest"]

/** Replaces a policy's quota and on/off state; answers the statistics with the change in force. */
export function updateRateLimitPolicy(policy: string, input: RateLimitPolicyInput): Promise<RateLimitStats> {
  return authorizedRequest<RateLimitStats>("admin", `/rate-limit/policies/${encodeURIComponent(policy)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
    cache: "no-store",
  })
}

/** Back to the application.properties quota, switched on. */
export function restoreRateLimitPolicy(policy: string): Promise<RateLimitStats> {
  return authorizedRequest<RateLimitStats>("admin", `/rate-limit/policies/${encodeURIComponent(policy)}`, {
    method: "DELETE",
    cache: "no-store",
  })
}

/** What each quota covers, as administrators know it. */
export const POLICY_LABELS: Record<string, string> = {
  login: "Đăng nhập",
  refresh: "Làm mới phiên",
  activation: "Kích hoạt tài khoản",
  "ip-ceiling": "Trần chung theo IP",
  "login-failure": "Sai mật khẩu",
  api: "API mặc định",
  password: "Đổi / đặt lại mật khẩu",
  export: "Xuất Excel bảng lương",
  report: "Báo cáo",
  "heavy-write": "Thao tác nặng",
}

export const SCOPE_LABELS: Record<string, string> = {
  IP: "Theo IP",
  USER: "Theo người dùng",
  ACCOUNT: "Theo tài khoản",
}

/** 60 → "1 phút", 900 → "15 phút", 30 → "30 giây". */
export function formatWindow(seconds: number): string {
  if (seconds % 3600 === 0) return `${seconds / 3600} giờ`
  if (seconds % 60 === 0) return `${seconds / 60} phút`
  return `${seconds} giây`
}

/** "300 request / 1 phút". */
export function formatQuota(maxRequests: number, windowSeconds: number): string {
  return `${maxRequests.toLocaleString("vi-VN")} request / ${formatWindow(windowSeconds)}`
}

export type RateLimitTimeline = Schemas["RateLimitTimelineResponse"]
export type RateLimitTimelinePoint = Schemas["Point"]
export type RateLimitTimelineRange = "HOUR" | "DAY" | "WEEK"

/** Requests and blocks over the range, for one policy or all of them. */
export function getRateLimitTimeline(range: RateLimitTimelineRange, policy: string | null): Promise<RateLimitTimeline> {
  const query = new URLSearchParams({ range })
  if (policy) query.set("policy", policy)
  return authorizedRequest<RateLimitTimeline>("admin", `/rate-limit/timeline?${query}`, { cache: "no-store" })
}
