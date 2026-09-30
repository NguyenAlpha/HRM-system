import { handleAccountRoleAssignmentRequest } from "@/lib/auth/server"

type Context = { params: Promise<{ accountId: string; path: string[] }> }

async function handle(request: Request, context: Context) {
  const { accountId, path } = await context.params
  return handleAccountRoleAssignmentRequest(request, accountId, "hrm", path)
}

export { handle as GET, handle as POST }
