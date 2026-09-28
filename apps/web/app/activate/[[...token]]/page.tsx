import type { Metadata } from "next"

import { ActivateAccountPage } from "@/components/activation/activate-account-page"

export const metadata: Metadata = { title: "Kích hoạt tài khoản" }

export default async function Page({ params }: { params: Promise<{ token?: string[] }> }) {
  const { token } = await params
  return <ActivateAccountPage initialToken={token?.[0] ?? ""} />
}
