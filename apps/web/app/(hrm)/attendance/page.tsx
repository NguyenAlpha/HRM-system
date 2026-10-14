import type { Metadata } from "next"

import { AttendancePage } from "@/components/workforce/attendance-page"

export const metadata: Metadata = { title: "Chấm công" }

export default function Page() { return <AttendancePage /> }
