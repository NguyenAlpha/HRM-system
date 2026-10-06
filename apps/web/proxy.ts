import { NextRequest, NextResponse } from "next/server"

import { PORTAL_CONFIG } from "@/lib/auth/config"

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
  if ((pathname.startsWith("/dashboard") || pathname.startsWith("/reports")
    || pathname.startsWith("/attendance") || pathname.startsWith("/leave-requests")
    || pathname.startsWith("/payslips")) && !hasSession(request, "hrm")) {
    return NextResponse.redirect(new URL("/login", request.url))
  }
  if (pathname.startsWith("/admin") && pathname !== "/admin/login" && !hasSession(request, "admin")) {
    return NextResponse.redirect(new URL("/admin/login", request.url))
  }

  return NextResponse.next()
}

export const config = {
  matcher: ["/login", "/dashboard/:path*", "/reports/:path*", "/attendance/:path*", "/leave-requests/:path*", "/payslips/:path*", "/admin/:path*"],
}
