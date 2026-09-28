import type { Metadata } from "next"

import { DirectRoleGrantPage } from "@/components/role-assignment/direct-role-grant-page"

export const metadata: Metadata = { title: "Cấp quyền trực tiếp" }

export default function Page() {
  return <DirectRoleGrantPage />
}
