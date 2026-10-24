import { handleSystemStatsRequest } from "@/lib/auth/server"

// Only the known range parameter is passed on, never an arbitrary query.
export async function GET(request: Request) {
  const range = new URL(request.url).searchParams.get("range")
  const query = new URLSearchParams()
  if (range && /^(HOUR|DAY|WEEK)$/.test(range)) query.set("range", range)
  return handleSystemStatsRequest("/api/system/traffic/timeline", query)
}
