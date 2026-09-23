import { handleMe } from "@/lib/auth/server"

export async function GET() {
  return handleMe("admin")
}
