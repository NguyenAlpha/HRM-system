import type { Metadata } from "next"

import { EmployeePage } from "@/components/employee/employee-page"

export const metadata: Metadata = { title: "Nhân sự" }

export default function Page() {
  return <EmployeePage />
}
