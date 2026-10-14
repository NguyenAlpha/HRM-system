import type { Metadata } from "next"

import { LeaveRequestsPage } from "@/components/workforce/leave-requests-page"

export const metadata: Metadata = { title: "Đơn nghỉ phép" }

// The dashboard's "Tạo đơn nghỉ" links here with ?new=1 to open the form straight away.
export default async function Page({ searchParams }: { searchParams: Promise<{ new?: string }> }) {
  const { new: openCreate } = await searchParams
  return <LeaveRequestsPage openCreate={openCreate !== undefined} />
}
