import type { Metadata } from "next"

import { CompanyProfilePage } from "@/components/organization/company-profile-page"

export const metadata: Metadata = { title: "Thông tin doanh nghiệp" }

export default function Page() {
  return <CompanyProfilePage />
}
