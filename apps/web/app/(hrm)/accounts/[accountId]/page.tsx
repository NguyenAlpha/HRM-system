import type { Metadata } from "next"

import { AccountDetailPage } from "@/components/account/account-detail-page"

export const metadata: Metadata = { title: "Chi tiết tài khoản" }

export default async function Page({
  params,
}: {
  params: Promise<{ accountId: string }>
}) {
  const { accountId } = await params
  return <AccountDetailPage accountId={accountId} />
}
