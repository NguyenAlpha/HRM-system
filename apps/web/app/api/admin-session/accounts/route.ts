import { handleAccountLifecycleRequest, handleAccountProvisionRequest } from "@/lib/auth/server"

async function handle(request: Request) {
  return handleAccountProvisionRequest(request, "admin")
}

async function list(request: Request) {
  return handleAccountLifecycleRequest(request, [], "admin")
}

export { handle as POST, list as GET }
