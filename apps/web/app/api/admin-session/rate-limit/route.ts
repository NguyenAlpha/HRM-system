import { handleRateLimitStatsRequest } from "@/lib/auth/server"

export async function GET() {
  return handleRateLimitStatsRequest()
}
