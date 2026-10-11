import type { Metadata } from "next"

import { AdminOrganizationPage } from "@/components/admin/organization-page"

export const metadata: Metadata = { title: "Danh mục tổ chức" }

export default function Page() {
  return <AdminOrganizationPage />
}
