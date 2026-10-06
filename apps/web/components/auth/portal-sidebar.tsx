import Link from "next/link"

import { PORTAL_CONFIG } from "@/lib/auth/config"
import type { AccountSummary, Portal } from "@/lib/auth/types"

export function PortalSidebar({ portal, account, active = "overview" }: {
  portal: Portal
  account: AccountSummary
  active?: "overview" | "rbac" | "employees" | "role-requests" | "company-owner" | "role-grant" | "organization" | "reports" | "attendance" | "leave" | "payroll"
}) {
  const admin = portal === "admin"
  const canBootstrapCompanyOwner = account.permissions.some(
    (permission) => permission.code === "organization.company_owner.bootstrap",
  )
  const canReadEmployees = account.permissions.some((permission) => permission.code === "employee.list.read")
  const canUseRoleRequests = account.permissions.some(
    (permission) => permission.code === "role.assignment.request" || permission.code === "role.assignment.approve",
  )
  const canManageRbac = account.permissions.some((permission) => permission.code === "rbac.manage")
  const canAssignRoleDirect = account.permissions.some((permission) => permission.code === "account.role.assign")
  const canReadOrganization = account.permissions.some((permission) => permission.code === "organization.read")
  const canReadReports = account.permissions.some(
    (permission) => permission.code === "report.hr.read" || permission.code === "report.payroll.read",
  )
  const canUseAttendance = account.permissions.some((permission) =>
    ["attendance.self.read", "attendance.read", "attendance.manage"].includes(permission.code))
  const canUseLeave = account.permissions.some((permission) =>
    ["request.self.read", "request.read", "request.self.create", "request.approve"].includes(permission.code))
  const canUsePayroll = account.permissions.some((permission) =>
    ["payroll.self.read", "report.payroll.read", "payroll.calculate", "compensation.read", "compensation.manage"].includes(permission.code))

  return (
    <aside className="portal-sidebar">
      <div className="brand sidebar-brand">
        <span className="brand-mark">H</span>
        <span><strong>HRM</strong><small>{admin ? "Admin Console" : "People Workspace"}</small></span>
      </div>
      <nav aria-label="Điều hướng chính">
        <span className="nav-caption">Workspace</span>
        <Link className={`nav-item ${active === "overview" ? "active" : ""}`} href={PORTAL_CONFIG[portal].homePath}>
          <span>◫</span> Tổng quan
        </Link>
        {admin ? (
          <>
            <span className="nav-item disabled"><span>◎</span> Tài khoản</span>
            <Link className={`nav-item ${active === "rbac" ? "active" : ""}`} href="/admin/rbac">
              <span>◇</span> Vai trò & quyền
            </Link>
            {canAssignRoleDirect && (
              <Link className={`nav-item ${active === "role-grant" ? "active" : ""}`} href="/admin/role-grant">
                <span>▥</span> Cấp quyền trực tiếp
              </Link>
            )}
            {canReadOrganization && (
              <Link className={`nav-item ${active === "organization" ? "active" : ""}`} href="/admin/organization">
                <span>▧</span> Danh mục tổ chức
              </Link>
            )}
            {canBootstrapCompanyOwner && (
              <Link className={`nav-item ${active === "company-owner" ? "active" : ""}`} href="/admin/company-owner">
                <span>◆</span> Company Owner
              </Link>
            )}
          </>
        ) : (
          <>
            <span className="nav-item disabled"><span>◎</span> Hồ sơ</span>
            {canReadEmployees && (
              <Link className={`nav-item ${active === "employees" ? "active" : ""}`} href="/employees">
                <span>▤</span> Nhân sự
              </Link>
            )}
            {canUseRoleRequests && (
              <Link className={`nav-item ${active === "role-requests" ? "active" : ""}`} href="/role-requests">
                <span>◧</span> Đề xuất role
              </Link>
            )}
            {canAssignRoleDirect && (
              <Link className={`nav-item ${active === "role-grant" ? "active" : ""}`} href="/role-grant">
                <span>▥</span> Cấp quyền trực tiếp
              </Link>
            )}
            {canManageRbac && (
              <Link className={`nav-item ${active === "rbac" ? "active" : ""}`} href="/rbac">
                <span>◇</span> Vai trò & quyền
              </Link>
            )}
            {canReadOrganization && (
              <Link className={`nav-item ${active === "organization" ? "active" : ""}`} href="/organization">
                <span>▧</span> Danh mục tổ chức
              </Link>
            )}
            {canReadReports && (
              <Link className={`nav-item ${active === "reports" ? "active" : ""}`} href="/reports/workforce">
                <span>▥</span> Báo cáo nhân sự
              </Link>
            )}
            {canUseAttendance && <Link className={`nav-item ${active === "attendance" ? "active" : ""}`} href="/attendance"><span>◷</span> Chấm công</Link>}
            {canUseLeave && <Link className={`nav-item ${active === "leave" ? "active" : ""}`} href="/leave-requests"><span>▱</span> Đơn từ</Link>}
            {canUsePayroll && <Link className={`nav-item ${active === "payroll" ? "active" : ""}`} href="/payslips"><span>◈</span> Phiếu lương</Link>}
          </>
        )}
      </nav>
      <div className="sidebar-account">
        <span className="avatar">{(account.employee?.fullName ?? account.username).slice(0, 1).toUpperCase()}</span>
        <span><strong>{account.employee?.fullName ?? account.username}</strong><small>{account.email}</small></span>
      </div>
    </aside>
  )
}
