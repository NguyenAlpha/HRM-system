import type { Metadata } from "next"

import { WorkforceReportPage } from "@/components/report/workforce-report-page"

export const metadata: Metadata = { title: "Cơ cấu nhân sự" }

export default function Page() {
  return <WorkforceReportPage />
}
