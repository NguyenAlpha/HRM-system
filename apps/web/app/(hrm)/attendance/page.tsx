import type { Metadata } from "next"

import { AttendancePage } from "@/components/workforce/attendance-page"

export const metadata: Metadata = { title: "Chấm công" }

// The dashboard links here with ?tab=overtime to open the overtime waiting for approval, and the
// employee detail page with ?employeeId= to open that employee's month.
export default async function Page({ searchParams }: { searchParams: Promise<{ tab?: string; employeeId?: string }> }) {
  const { tab, employeeId } = await searchParams
  const initialEmployeeId = employeeId && /^\d+$/.test(employeeId) ? Number(employeeId) : undefined
  return <AttendancePage initialTab={tab === "overtime" ? "overtime" : undefined} initialEmployeeId={initialEmployeeId} />
}
