import type { Metadata } from "next"

import { RoleGrantPage } from "@/components/role-assignment/role-grant-page"

export const metadata: Metadata = { title: "Cấp quyền trực tiếp" }

export default function Page() {
  return <RoleGrantPage />
}
