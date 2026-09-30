import type { Metadata } from "next"

import { EmployeeDetailPage } from "@/components/employee/employee-detail-page"

export const metadata: Metadata = { title: "Chi tiết nhân sự" }

export default async function Page({
  params,
}: {
  params: Promise<{ employeeId: string }>
}) {
  const { employeeId } = await params
  return <EmployeeDetailPage employeeId={employeeId} />
}
