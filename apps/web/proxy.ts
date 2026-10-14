import { NextRequest, NextResponse } from "next/server"

import { PORTAL_CONFIG } from "@/lib/auth/config"

// HRM Workspace pages; keep in sync with config.matcher below.
const HRM_PAGES = [
  "/dashboard", "/employees", "/accounts", "/organization", "/attendance", "/leave-requests",
  "/payslips", "/payroll", "/allowances", "/profile", "/reports", "/rbac", "/role-grant", "/role-requests",
]

function hasSession(request: NextRequest, portal: "hrm" | "admin"): boolean {
  const config = PORTAL_CONFIG[portal]
  return Boolean(
    request.cookies.get(config.accessCookie)?.value
    || request.cookies.get(config.refreshCookie)?.value,
  )
}

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl

  if (pathname === "/login" && hasSession(request, "hrm")) {
    return NextResponse.redirect(new URL("/dashboard", request.url))
  }
  if (pathname === "/admin/login" && hasSession(request, "admin")) {
    return NextResponse.redirect(new URL("/admin", request.url))
  }
  const hrmPage = HRM_PAGES.some((page) => pathname === page || pathname.startsWith(`${page}/`))
  if (hrmPage && !hasSession(request, "hrm")) {
    return NextResponse.redirect(new URL("/login", request.url))
  }
  if (pathname.startsWith("/admin") && pathname !== "/admin/login" && !hasSession(request, "admin")) {
    return NextResponse.redirect(new URL("/admin/login", request.url))
  }

  return NextResponse.next()
}

export const config = {
  matcher: [
    "/login", "/dashboard/:path*", "/employees/:path*", "/accounts/:path*", "/organization/:path*",
    "/attendance/:path*", "/leave-requests/:path*", "/payslips/:path*", "/payroll/:path*",
    "/allowances/:path*", "/profile/:path*", "/reports/:path*", "/rbac/:path*",
    "/role-grant/:path*", "/role-requests/:path*", "/admin/:path*",
  ],
}
