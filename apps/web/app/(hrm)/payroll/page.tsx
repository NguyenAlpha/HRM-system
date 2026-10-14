import type { Metadata } from "next"

import { PayrollPeriodsPage } from "@/components/payroll/payroll-periods-page"

export const metadata: Metadata = { title: "Kỳ lương" }

export default function Page() {
  return <PayrollPeriodsPage />
}
