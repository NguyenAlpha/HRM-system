import type { Metadata } from "next"

import { OrganizationReferencePage } from "@/components/organization/organization-reference-page"

export const metadata: Metadata = { title: "Danh mục tổ chức" }

export default function Page() {
  return <OrganizationReferencePage />
}
