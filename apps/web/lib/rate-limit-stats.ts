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
