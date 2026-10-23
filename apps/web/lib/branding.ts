import type { components } from "@/lib/api-schema"

/** The company name and logo shown in the sidebar, on the sign-in page and in the tab title. */
export interface Branding {
  name: string
  logoUrl: string | null
}

export const LOGO_TYPES = ["image/png", "image/jpeg", "image/webp"]
/** Same limit as CompanyProfileService.MAX_LOGO_BYTES. */
export const MAX_LOGO_BYTES = 512 * 1024

/** Shown when the API cannot be reached. */
export const FALLBACK_BRANDING: Branding = { name: "HRM", logoUrl: null }

/** The public logo route; the timestamp changes the URL whenever the logo does, so caches never serve a stale one. */
export const logoUrl = (logoUpdatedAt: string | null | undefined) =>
  logoUpdatedAt ? `/api/branding/logo?v=${Date.parse(logoUpdatedAt)}` : null

export function toBranding(response: components["schemas"]["BrandingResponse"]): Branding {
  return { name: response.name, logoUrl: logoUrl(response.logoUpdatedAt) }
}
