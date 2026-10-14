import type { Metadata } from "next"

import { AttendanceReportPage } from "@/components/report/attendance-report-page"

export const metadata: Metadata = { title: "Tổng hợp chấm công" }

export default function Page() {
  return <AttendanceReportPage />
}
