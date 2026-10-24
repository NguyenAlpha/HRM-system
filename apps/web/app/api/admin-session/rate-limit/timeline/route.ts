import { handleSystemStatsRequest } from "@/lib/auth/server"

// Only the two known parameters are passed on, never an arbitrary query.
export async function GET(request: Request) {
  const params = new URL(request.url).searchParams
  const query = new URLSearchParams()
  const range = params.get("range")
  const policy = params.get("policy")
  if (range && /^(HOUR|DAY|WEEK)$/.test(range)) query.set("range", range)
  if (policy && /^[a-z-]{1,30}$/.test(policy)) query.set("policy", policy)
  return handleSystemStatsRequest("/api/system/rate-limit/timeline", query)
}
