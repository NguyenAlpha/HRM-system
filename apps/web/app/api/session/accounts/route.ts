import { handleAccountProvisionRequest } from "@/lib/auth/server"

async function handle(request: Request) {
  return handleAccountProvisionRequest(request, "hrm")
}

export { handle as POST }
