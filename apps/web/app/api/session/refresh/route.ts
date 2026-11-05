import { handleRefresh } from "@/lib/auth/server"

export async function POST(request: Request) {
  return handleRefresh(request, "hrm")
}
