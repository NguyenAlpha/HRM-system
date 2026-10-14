import type { Metadata } from "next"

import { PayrollReportPage } from "@/components/report/payroll-report-page"

export const metadata: Metadata = { title: "Quỹ lương, bảo hiểm & thuế" }

export default function Page() {
  return <PayrollReportPage />
}
