import { PortalShell } from "@/components/portal/portal-shell"
import { getBranding } from "@/lib/auth/server"

export default async function HrmLayout({ children }: { children: React.ReactNode }) {
  return <PortalShell portal="hrm" branding={await getBranding()}>{children}</PortalShell>
}
