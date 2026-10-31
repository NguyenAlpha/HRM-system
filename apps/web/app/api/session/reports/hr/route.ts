import { handleReportRequest } from "@/lib/auth/server"

export function GET(request: Request) {
  return handleReportRequest(request, "hr")
}
