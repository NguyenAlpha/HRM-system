import type { Metadata } from "next"

import { AttendancePage } from "@/components/workforce/attendance-page"

export const metadata: Metadata = { title: "Chấm công" }

// The dashboard links here with ?tab=overtime to open the overtime waiting for approval.
export default async function Page({ searchParams }: { searchParams: Promise<{ tab?: string }> }) {
  const { tab } = await searchParams
  return <AttendancePage initialTab={tab === "overtime" ? "overtime" : undefined} />
}
