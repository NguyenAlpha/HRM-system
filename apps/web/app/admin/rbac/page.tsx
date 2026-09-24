import type { Metadata } from "next"

import { RbacManager } from "@/components/admin/rbac-manager"

export const metadata: Metadata = { title: "Quản lý vai trò và quyền" }

export default function RbacPage() {
  return <RbacManager />
}
