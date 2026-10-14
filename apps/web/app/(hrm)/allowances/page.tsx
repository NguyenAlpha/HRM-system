import type { Metadata } from "next"

import { AllowancePolicyPage } from "@/components/workforce/allowance-policy-page"

export const metadata: Metadata = { title: "Chính sách phụ cấp" }

export default function Page() {
  return <AllowancePolicyPage />
}
