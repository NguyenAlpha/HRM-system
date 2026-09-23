import type { Metadata } from "next"

import { PortalDashboard } from "@/components/auth/portal-dashboard"

export const metadata: Metadata = { title: "Quản trị hệ thống" }

export default function AdminPage() {
  return <PortalDashboard portal="admin" />
}
