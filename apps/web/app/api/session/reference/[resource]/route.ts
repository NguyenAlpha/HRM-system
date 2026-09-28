import { handleReferenceRequest } from "@/lib/auth/server"

type Context = { params: Promise<{ resource: string }> }

async function handle(request: Request, context: Context) {
  const { resource } = await context.params
  return handleReferenceRequest(request, resource, "hrm")
}

export { handle as GET }
