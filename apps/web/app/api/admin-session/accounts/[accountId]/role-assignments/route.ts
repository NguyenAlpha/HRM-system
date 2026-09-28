import { handleAccountRoleAssignmentRequest } from "@/lib/auth/server"

type Context = { params: Promise<{ accountId: string }> }

async function handle(request: Request, context: Context) {
  const { accountId } = await context.params
  return handleAccountRoleAssignmentRequest(request, accountId, "admin")
}

export { handle as GET, handle as POST }
