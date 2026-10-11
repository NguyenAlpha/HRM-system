"use client"

import Link from "next/link"
import { ArrowLeft } from "lucide-react"

import { usePortalSession } from "@/components/portal/portal-shell"
import { EmployeeManager } from "@/components/employee/employee-manager"
import { Button } from "@/components/ui/button"

export function EmployeePage() {
  const { session, onSessionExpired } = usePortalSession()
  const permissionCodes = new Set(session.account.permissions.map((permission) => permission.code))
  const capabilities = {
    canReadDetail: permissionCodes.has("employee.read"),
    canCreate: permissionCodes.has("employee.create"),
    canUpdate: permissionCodes.has("employee.update"),
    canAssign: permissionCodes.has("employee.assignment.manage"),
    canConfirmProbation: permissionCodes.has("employee.probation.confirm"),
    canProvisionAccount: permissionCodes.has("account.provision"),
  }

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">HRM WORKSPACE</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Nhân sự</h1>
          <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
            Xem và cập nhật hồ sơ nhân sự trong phạm vi quản lý được phân công.
          </p>
        </div>
        <Button variant="outline" nativeButton={false} render={<Link href="/dashboard" />}>
          <ArrowLeft /> Về tổng quan
        </Button>
      </header>

      <div className="mt-6">
        <EmployeeManager capabilities={capabilities} onSessionExpired={onSessionExpired} />
      </div>
    </>
  )
}
