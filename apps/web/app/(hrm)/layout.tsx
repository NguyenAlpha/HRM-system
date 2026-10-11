import { PortalShell } from "@/components/auth/portal-shell"

export default function HrmLayout({ children }: { children: React.ReactNode }) {
  return <PortalShell portal="hrm">{children}</PortalShell>
}
