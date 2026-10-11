"use client"

import Link from "next/link"
import { ArrowLeft } from "lucide-react"

import { usePortalSession } from "@/components/auth/portal-shell"
import { DirectRoleGrantManager } from "@/components/role-assignment/direct-role-grant-manager"
import { Button } from "@/components/ui/button"

export function AdminRoleGrantPage() {
  const { onSessionExpired } = usePortalSession()

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">ADMIN CONSOLE</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Cấp quyền trực tiếp</h1>
          <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
            Gán role trực tiếp cho nhân sự và cấp mã kích hoạt tài khoản mà không cần chờ duyệt.
          </p>
        </div>
        <Button variant="outline" nativeButton={false} render={<Link href="/admin" />}>
          <ArrowLeft /> Về tổng quan
        </Button>
      </header>

      <div className="mt-6">
        <DirectRoleGrantManager portal="admin" onSessionExpired={onSessionExpired} />
      </div>
    </>
  )
}
