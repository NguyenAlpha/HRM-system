import type { Metadata } from "next"

import { MonthlyStatusReportPage } from "@/components/report/monthly-status-report-page"

export const metadata: Metadata = { title: "Tình hình nhân sự theo tháng" }

export default function Page() {
  return <MonthlyStatusReportPage />
}
