import { handleAccountLifecycleRequest } from "@/lib/auth/server"

type Context = { params: Promise<{ accountId: string }> }

async function handle(request: Request, context: Context) {
  const { accountId } = await context.params
  return handleAccountLifecycleRequest(request, [accountId], "hrm")
}

export { handle as GET }
