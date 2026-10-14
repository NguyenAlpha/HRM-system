import type { Metadata } from "next"

import { HrmDashboard } from "@/components/portal/hrm-dashboard"

export const metadata: Metadata = { title: "Tổng quan" }

export default function DashboardPage() {
  return <HrmDashboard />
}
