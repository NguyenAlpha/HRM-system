"use client"

import Link from "next/link"
import { ArrowLeft } from "lucide-react"

import { usePortalSession } from "@/components/portal/portal-shell"
import { WorkforceReportDashboard } from "@/components/report/workforce-report-dashboard"
import { Button } from "@/components/ui/button"

export function WorkforceReportPage() {
  const { session, onSessionExpired } = usePortalSession()
  const permissionCodes = new Set(session.account.permissions.map((permission) => permission.code))
  const canReadHr = permissionCodes.has("report.hr.read")
  const canReadPayroll = permissionCodes.has("report.payroll.read")
  const canReadOrganization = permissionCodes.has("organization.read")

  if (!canReadHr && !canReadPayroll) return <AlertAccessDenied />

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">HRM WORKSPACE</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Báo cáo nhân sự</h1>
          <p className="mt-1.5 max-w-2xl text-[var(--muted-ink)]">
            Thống kê trình độ học vấn, mức lương cơ bản và thâm niên theo phạm vi được phân quyền.
          </p>
        </div>
        <Button variant="outline" nativeButton={false} render={<Link href="/dashboard" />}>
          <ArrowLeft /> Về tổng quan
        </Button>
      </header>

      <div className="mt-6">
        <WorkforceReportDashboard
          canReadHr={canReadHr}
          canReadPayroll={canReadPayroll}
          canReadOrganization={canReadOrganization}
          onSessionExpired={onSessionExpired}
        />
      </div>
    </>
  )
}

function AlertAccessDenied() {
  return (
    <div className="dashboard-card">
      <p className="eyebrow">ACCESS DENIED</p>
      <h1 className="mt-2 text-2xl font-semibold">Bạn chưa có quyền xem báo cáo</h1>
      <p className="mt-2 text-sm text-muted-foreground">
        Cần quyền xem báo cáo nhân sự hoặc báo cáo tiền lương để sử dụng trang này.
      </p>
      <Button className="mt-4" variant="outline" nativeButton={false} render={<Link href="/dashboard" />}>
        Về trang tổng quan
      </Button>
    </div>
  )
}
