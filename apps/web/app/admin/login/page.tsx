import type { Metadata } from "next"

import { LoginForm } from "@/components/auth/login-form"

export const metadata: Metadata = { title: "Đăng nhập Admin" }

export default function AdminLoginPage() {
  return <LoginForm portal="admin" />
}
