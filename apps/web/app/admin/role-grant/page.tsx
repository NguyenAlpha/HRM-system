import type { Metadata } from "next"

import { AdminRoleGrantPage } from "@/components/admin/role-grant-page"

export const metadata: Metadata = { title: "Cấp quyền trực tiếp" }

export default function Page() {
  return <AdminRoleGrantPage />
}
