import { PortalShell } from "@/components/portal/portal-shell"
import { getBranding } from "@/lib/auth/server"

export default async function AdminConsoleLayout({ children }: { children: React.ReactNode }) {
  return <PortalShell portal="admin" branding={await getBranding()}>{children}</PortalShell>
}
