import { cookies } from "next/headers"
import { NextResponse } from "next/server"

import { PORTAL_CONFIG } from "@/lib/auth/config"
import type { ApiEnvelope } from "@/lib/auth/types"

const apiBaseUrl = process.env.API_URL ?? process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"

function failure(code: string, message: string): ApiEnvelope<never> {
  return { success: false, data: null, error: { code, message, field: null } }
}

export async function handleReportRequest(
  request: Request,
  report: "hr" | "payroll",
): Promise<NextResponse> {
  const accessToken = (await cookies()).get(PORTAL_CONFIG.hrm.accessCookie)?.value
  if (!accessToken) {
    return NextResponse.json(failure("UNAUTHORIZED", "Phiên đăng nhập đã hết hạn"), { status: 401 })
  }

  const query = new URLSearchParams()
  new URL(request.url).searchParams.forEach((value, key) => {
    if (["asOfDate", "organizationUnitId", "workLocationId", "employmentStatus"].includes(key)) {
      query.append(key, value)
    }
  })
  const endpoint = report === "hr"
    ? "/api/reports/hr/workforce-distribution"
    : "/api/reports/payroll/salary-distribution"

  try {
    const response = await fetch(`${apiBaseUrl}${endpoint}?${query}`, {
      cache: "no-store",
      headers: { Accept: "application/json", Authorization: `Bearer ${accessToken}` },
    })
    const payload = (await response.json().catch(() =>
      failure("INVALID_API_RESPONSE", "HRM API trả về dữ liệu không hợp lệ"))) as ApiEnvelope<unknown>
    return NextResponse.json(payload, {
      status: response.status,
      headers: { "Cache-Control": "no-store" },
    })
  } catch {
    return NextResponse.json(failure("API_UNAVAILABLE", "Không thể kết nối tới HRM API"), { status: 502 })
  }
}
