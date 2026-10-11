import type { Metadata } from "next"

import { RbacPage } from "@/components/rbac/rbac-page"

export const metadata: Metadata = { title: "Vai trò và quyền" }

export default function Page() {
  return <RbacPage />
}
