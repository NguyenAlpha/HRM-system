"use client"

import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { EmployeeManager } from "@/components/employee/employee-manager"

export function EmployeePage() {
  const { portal, session, onSessionExpired } = usePortalSession()
  const permissionCodes = new Set(session.account.permissions.map((permission) => permission.code))
  const capabilities = {
    canReadDetail: permissionCodes.has("employee.read"),
    canCreate: permissionCodes.has("employee.create"),
    canUpdate: permissionCodes.has("employee.update"),
    canAssign: permissionCodes.has("employee.assignment.manage"),
    canConfirmProbation: permissionCodes.has("employee.probation.confirm"),
    canProvisionAccount: permissionCodes.has("account.provision"),
    canReadAccount: permissionCodes.has("account.read"),
    // The attendance page lives in the HRM portal only.
    canReadAttendance: portal === "hrm" && permissionCodes.has("attendance.read"),
    canManageLifecycle: permissionCodes.has("employee.lifecycle.manage"),
    canDelete: permissionCodes.has("employee.delete"),
  }

  return (
    <>
      <PageHeader
        title="Nhân sự"
        description="Xem và cập nhật hồ sơ nhân sự trong phạm vi quản lý được phân công."
      />

      <div className="mt-6">
        <EmployeeManager portal={portal} capabilities={capabilities} onSessionExpired={onSessionExpired} />
      </div>
    </>
  )
}
