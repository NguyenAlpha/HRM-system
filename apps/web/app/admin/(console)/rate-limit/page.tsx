import type { Metadata } from "next"

import { RateLimitPage } from "@/components/rate-limit/rate-limit-page"

export const metadata: Metadata = { title: "Giới hạn truy cập" }

export default function Page() {
  return <RateLimitPage />
}
