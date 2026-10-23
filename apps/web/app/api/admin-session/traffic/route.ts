import { handleSystemStatsRequest } from "@/lib/auth/server"

export async function GET() {
  return handleSystemStatsRequest("/api/system/traffic/stats")
}
