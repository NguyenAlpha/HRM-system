const apiBaseUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"

export class ApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message)
    this.name = "ApiError"
  }
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers: { Accept: "application/json", ...init?.headers },
  })

  if (!response.ok) throw new ApiError(`API request failed (${response.status})`, response.status)
  return response.json() as Promise<T>
}
