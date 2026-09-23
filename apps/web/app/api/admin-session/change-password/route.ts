import { handleChangePassword } from "@/lib/auth/server"

export async function POST(request: Request) {
  return handleChangePassword(request, "admin")
}
