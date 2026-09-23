import { handleLogout } from "@/lib/auth/server"

export async function POST() {
  return handleLogout("hrm")
}
