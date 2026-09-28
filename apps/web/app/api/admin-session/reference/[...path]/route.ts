import { handleReferenceRequest } from "@/lib/auth/server"

type Context = { params: Promise<{ path: string[] }> }

async function handle(request: Request, context: Context) {
  const { path } = await context.params
  return handleReferenceRequest(request, path, "admin")
}

export { handle as GET, handle as POST, handle as PUT, handle as DELETE }
