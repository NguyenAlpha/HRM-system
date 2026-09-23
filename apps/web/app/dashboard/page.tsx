import type { Metadata } from "next"

import { PortalDashboard } from "@/components/auth/portal-dashboard"

export const metadata: Metadata = { title: "Tổng quan" }

export default function DashboardPage() {
  return <PortalDashboard portal="hrm" />
}
