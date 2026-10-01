import type { Metadata } from "next"

import { WorkforceReportPage } from "@/components/report/workforce-report-page"

export const metadata: Metadata = { title: "Báo cáo nhân sự" }

export default function Page() {
  return <WorkforceReportPage />
}
