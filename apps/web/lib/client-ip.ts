import { headers } from "next/headers"

/**
 * Passes the browser's address on to the API, which rate-limits by it and trusts this header only
 * from the web server. Next fills `x-forwarded-for` with the socket address when the request has
 * none; only its last entry is kept, the one written by the nearest hop.
 */
export async function clientIpHeaders(): Promise<Record<string, string>> {
  try {
    const forwarded = (await headers()).get("x-forwarded-for")
    const client = forwarded?.split(",").map((entry) => entry.trim()).filter(Boolean).at(-1)
    return client ? { "X-Forwarded-For": client } : {}
  } catch {
    // Outside a request (e.g. while prerendering) there is no browser to forward.
    return {}
  }
}
