import type { Metadata } from "next"

import { LeaveReportPage } from "@/components/report/leave-report-page"

export const metadata: Metadata = { title: "Phép năm" }

export default function Page() {
  return <LeaveReportPage />
}
