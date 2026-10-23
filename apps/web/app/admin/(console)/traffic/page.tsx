import type { Metadata } from "next"

import { ApiTrafficPage } from "@/components/traffic/api-traffic-page"

export const metadata: Metadata = { title: "Lưu lượng API" }

export default function Page() {
  return <ApiTrafficPage />
}
