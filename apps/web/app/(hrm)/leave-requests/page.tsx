import type { Metadata } from "next"

import { LeaveRequestsPage } from "@/components/workforce/leave-requests-page"

export const metadata: Metadata = { title: "Đơn nghỉ phép" }

// The dashboard links here with ?new=1 to open the form straight away, or ?tab=approvals for the
// requests waiting for the signed-in approver.
export default async function Page({ searchParams }: { searchParams: Promise<{ new?: string; tab?: string; request?: string }> }) {
  const { new: openCreate, tab, request } = await searchParams
  const requestId = request && /^[1-9]\d*$/.test(request) ? Number(request) : undefined
  return <LeaveRequestsPage openCreate={openCreate !== undefined}
    initialTab={tab === "approvals" || tab === "all" ? tab : undefined} initialRequestId={requestId} />
}
