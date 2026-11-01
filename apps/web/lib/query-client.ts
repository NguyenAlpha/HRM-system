"use client"

import { QueryClient } from "@tanstack/react-query"

let browserClient: QueryClient | null = null

const createClient = () => new QueryClient({ defaultOptions: { queries: { retry: false } } })

/** Cache key prefix of the dashboard widgets (see docs/CACHING.md). */
export const DASHBOARD_KEY = ["dashboard"] as const

/**
 * The browser's one cache of API data (see docs/CACHING.md). PortalShell provides it to React and
 * clears it when the signed-in layout goes away; plain helpers in lib/ reach it here. On the server
 * every call gets its own client, so nothing is shared between users.
 */
export function getQueryClient(): QueryClient {
  if (typeof window === "undefined") return createClient()
  browserClient ??= createClient()
  return browserClient
}
