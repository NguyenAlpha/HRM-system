import { handleRefresh } from "@/lib/auth/server"

export async function POST() {
  return handleRefresh("hrm")
}
