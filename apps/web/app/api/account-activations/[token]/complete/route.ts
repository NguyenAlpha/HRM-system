import { handleAccountActivationRequest } from "@/lib/auth/server"

type Context = { params: Promise<{ token: string }> }

async function handle(request: Request, context: Context) {
  const { token } = await context.params
  return handleAccountActivationRequest(request, token)
}

export { handle as POST }
