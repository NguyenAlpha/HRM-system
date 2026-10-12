import { handleAccountLifecycleRequest, handleAccountProvisionRequest } from "@/lib/auth/server"

async function handle(request: Request) {
  return handleAccountProvisionRequest(request, "hrm")
}

async function list(request: Request) {
  return handleAccountLifecycleRequest(request, [], "hrm")
}

export { handle as POST, list as GET }
