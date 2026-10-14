import type { Metadata } from "next"

import { PayrollPeriodPage } from "@/components/payroll/payroll-period-page"

export const metadata: Metadata = { title: "Bảng lương" }

export default async function Page({
  params,
}: {
  params: Promise<{ periodId: string }>
}) {
  const { periodId } = await params
  return <PayrollPeriodPage periodId={periodId} />
}
