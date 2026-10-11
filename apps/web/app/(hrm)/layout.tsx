import { PortalShell } from "@/components/portal/portal-shell"

export default function HrmLayout({ children }: { children: React.ReactNode }) {
  return <PortalShell portal="hrm">{children}</PortalShell>
}
