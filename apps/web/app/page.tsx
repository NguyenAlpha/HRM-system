import type { Metadata } from "next"

import { LandingPage } from "@/components/landing/landing-page"

export const metadata: Metadata = { title: { absolute: "HRM — Nền tảng quản trị nguồn nhân lực" } }

export default function HomePage() {
  return <LandingPage />
}
