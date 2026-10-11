import type { Metadata } from "next"

import { OrganizationPage } from "@/components/organization/organization-page"

export const metadata: Metadata = { title: "Danh mục tổ chức" }

export default function Page() {
  return <OrganizationPage />
}
