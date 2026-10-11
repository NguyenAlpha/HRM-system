import type { Metadata } from "next"

import { CompanyOwnerPage } from "@/components/company-owner/company-owner-page"

export const metadata: Metadata = { title: "Khởi tạo Company Owner" }

export default function Page() {
  return <CompanyOwnerPage />
}
