import type { Metadata } from "next"

import { RoleAssignmentPage } from "@/components/role-assignment/role-assignment-page"

export const metadata: Metadata = { title: "Đề xuất cấp role" }

export default function Page() {
  return <RoleAssignmentPage />
}
