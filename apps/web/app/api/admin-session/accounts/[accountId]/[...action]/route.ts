import { handleAccountLifecycleRequest } from "@/lib/auth/server"

type Context = { params: Promise<{ accountId: string; action: string[] }> }

async function handle(request: Request, context: Context) {
  const { accountId, action } = await context.params
  return handleAccountLifecycleRequest(request, [accountId, ...action], "admin")
}

export { handle as GET, handle as POST }
