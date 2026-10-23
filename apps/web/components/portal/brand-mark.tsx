import type { Branding } from "@/lib/branding"
import { cn } from "@/lib/utils"

/** The company logo, or the first letter of its name on a dark tile while no logo is uploaded. */
export function BrandMark({ branding, className }: { branding: Branding; className?: string }) {
  if (branding.logoUrl) {
    // A plain <img>: the logo route already caches by ETag and next/image would only re-encode a tiny file.
    // eslint-disable-next-line @next/next/no-img-element
    return <img src={branding.logoUrl} alt="" className={cn("brand-mark brand-logo", className)} />
  }
  return <span className={cn("brand-mark", className)}>{branding.name.trim().charAt(0).toUpperCase()}</span>
}
