import { handleLogout } from "@/lib/auth/server"

export async function POST(request: Request) {
  return handleLogout(request, "admin")
}
