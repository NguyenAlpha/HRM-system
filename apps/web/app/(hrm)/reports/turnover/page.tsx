import type { Metadata } from "next"

import { TurnoverReportPage } from "@/components/report/turnover-report-page"

export const metadata: Metadata = { title: "Biến động nhân sự" }

export default function Page() {
  return <TurnoverReportPage />
}
