import { handleRoleAssignmentRequest } from "@/lib/auth/server"

type Context = { params: Promise<{ path?: string[] }> }

async function handle(request: Request, context: Context) {
  const { path } = await context.params
  return handleRoleAssignmentRequest(request, path ?? [], "hrm")
}

export { handle as GET, handle as POST }
