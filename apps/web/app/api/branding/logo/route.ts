import { handleBrandingLogoRequest } from "@/lib/auth/server"

export async function GET(request: Request) {
  return handleBrandingLogoRequest(request)
}
