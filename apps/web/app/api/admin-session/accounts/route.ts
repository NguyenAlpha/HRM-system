import { handleAccountProvisionRequest } from "@/lib/auth/server"

async function handle(request: Request) {
  return handleAccountProvisionRequest(request, "admin")
}

export { handle as POST }
