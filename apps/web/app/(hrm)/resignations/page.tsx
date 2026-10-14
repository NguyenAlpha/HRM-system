import type { Metadata } from "next"

import { ResignationsPage } from "@/components/workforce/resignations-page"

export const metadata: Metadata = { title: "Đơn nghỉ việc" }

// The dashboard links here with ?tab=approvals for the requests waiting for the signed-in approver.
export default async function Page({ searchParams }: { searchParams: Promise<{ tab?: string }> }) {
  const { tab } = await searchParams
  return <ResignationsPage initialTab={tab === "approvals" ? "approvals" : undefined} />
}
