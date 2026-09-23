import { handleLogin } from "@/lib/auth/server"

export async function POST(request: Request) {
  return handleLogin(request, "admin")
}
