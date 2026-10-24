import { handleRateLimitPolicyRequest } from "@/lib/auth/server"

type Context = { params: Promise<{ policy: string }> }

async function handle(request: Request, context: Context) {
  const { policy } = await context.params
  return handleRateLimitPolicyRequest(request, policy)
}

export { handle as PUT, handle as DELETE }
