import { cn } from "@/lib/utils"

/**
 * Fades its children in as they scroll into view. Pure CSS (see .landing-reveal in globals.css), so the
 * content stays visible without JavaScript or where scroll-driven animations are unsupported.
 */
export function Reveal({ children, className }: { children: React.ReactNode; className?: string }) {
  return <div className={cn("landing-reveal", className)}>{children}</div>
}
