import { handleReportRequest } from "@/lib/report-server"

export function GET(request: Request) {
  return handleReportRequest(request, "payroll")
}
