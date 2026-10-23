import type { Metadata } from "next"

import { LoginForm } from "@/components/auth/login-form"
import { getBranding } from "@/lib/auth/server"

export const metadata: Metadata = { title: "Đăng nhập Admin" }

export default async function AdminLoginPage() {
  return <LoginForm portal="admin" branding={await getBranding()} />
}
