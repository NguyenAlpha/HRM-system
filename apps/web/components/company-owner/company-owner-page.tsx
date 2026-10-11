"use client"

import Link from "next/link"
import { ArrowLeft } from "lucide-react"

import { CompanyOwnerProvision } from "@/components/company-owner/company-owner-provision"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Button } from "@/components/ui/button"

export function CompanyOwnerPage() {
  const { onSessionExpired } = usePortalSession()

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">ADMIN CONSOLE</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Khởi tạo Company Owner</h1>
          <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
            Cầu nối từ vận hành nền tảng sang quản trị nội bộ doanh nghiệp — chỉ dùng một lần cho Company Owner đầu tiên.
          </p>
        </div>
        <Button variant="outline" nativeButton={false} render={<Link href="/admin" />}>
          <ArrowLeft /> Về tổng quan
        </Button>
      </header>

      <div className="mt-6 max-w-2xl">
        <CompanyOwnerProvision onSessionExpired={onSessionExpired} />
      </div>
    </>
  )
}
