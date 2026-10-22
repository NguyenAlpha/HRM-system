"use client"

import Link from "next/link"

import { PageHeader } from "@/components/portal/page-header"
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
      <PageHeader
        title="Cơ cấu nhân sự"
        description="Thống kê trình độ học vấn, mức lương cơ bản và thâm niên theo phạm vi được phân quyền."
        backToOverview
      />

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
    <div className="rounded-xl border bg-card p-6">
      <h1 className="text-2xl font-semibold tracking-tight">Bạn chưa có quyền xem báo cáo</h1>
      <p className="mt-2 text-sm text-muted-foreground">
        Cần quyền xem báo cáo nhân sự hoặc báo cáo tiền lương để sử dụng trang này.
      </p>
      <Button className="mt-4" variant="outline" nativeButton={false} render={<Link href="/dashboard" />}>
        Về trang tổng quan
      </Button>
    </div>
  )
}
