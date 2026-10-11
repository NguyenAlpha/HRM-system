import { PortalShell } from "@/components/portal/portal-shell"

export default function AdminConsoleLayout({ children }: { children: React.ReactNode }) {
  return <PortalShell portal="admin">{children}</PortalShell>
}
