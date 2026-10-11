import { PortalShell } from "@/components/auth/portal-shell"

export default function AdminConsoleLayout({ children }: { children: React.ReactNode }) {
  return <PortalShell portal="admin">{children}</PortalShell>
}
