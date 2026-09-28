import { handleCompanyOwnerBootstrapRequest } from "@/lib/auth/server"

async function handle(request: Request) {
  return handleCompanyOwnerBootstrapRequest(request)
}

export { handle as POST }
