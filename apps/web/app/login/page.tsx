import type { Metadata } from "next"

import { LoginForm } from "@/components/auth/login-form"
import { getBranding } from "@/lib/auth/server"

export const metadata: Metadata = { title: "Đăng nhập" }

export default async function LoginPage() {
  return <LoginForm portal="hrm" branding={await getBranding()} />
}
