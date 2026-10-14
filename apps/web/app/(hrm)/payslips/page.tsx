import type { Metadata } from "next"

import { MyPayslipsPage } from "@/components/payroll/my-payslips-page"

export const metadata: Metadata = { title: "Phiếu lương của tôi" }

export default function Page() {
  return <MyPayslipsPage />
}
