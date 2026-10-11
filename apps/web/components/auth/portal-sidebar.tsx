"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"

import { PORTAL_CONFIG } from "@/lib/auth/config"
import type { AccountSummary, Portal } from "@/lib/auth/types"

export function PortalSidebar({ portal, account }: { portal: Portal; account: AccountSummary }) {
  const admin = portal === "admin"
  const pathname = usePathname()
  // The home link only matches itself; every other link also covers its sub-pages.
  const navClass = (href: string) => {
    const active = href === PORTAL_CONFIG[portal].homePath
      ? pathname === href
      : pathname === href || pathname.startsWith(`${href}/`)
    return `nav-item ${active ? "active" : ""}`
  }
  const canBootstrapCompanyOwner = account.permissions.some(
    (permission) => permission.code === "organization.company_owner.bootstrap",
  )
  const canReadEmployees = account.permissions.some((permission) => permission.code === "employee.list.read")
  const canReadAccounts = account.permissions.some((permission) => permission.code === "account.read")
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
        <Link className={navClass(PORTAL_CONFIG[portal].homePath)} href={PORTAL_CONFIG[portal].homePath}>
          <span>◫</span> Tổng quan
        </Link>
        {admin ? (
          <>
            <span className="nav-item disabled"><span>◎</span> Tài khoản</span>
            <Link className={navClass("/admin/rbac")} href="/admin/rbac">
              <span>◇</span> Vai trò & quyền
            </Link>
            {canAssignRoleDirect && (
              <Link className={navClass("/admin/role-grant")} href="/admin/role-grant">
                <span>▥</span> Cấp quyền trực tiếp
              </Link>
            )}
            {canReadOrganization && (
              <Link className={navClass("/admin/organization")} href="/admin/organization">
                <span>▧</span> Danh mục tổ chức
              </Link>
            )}
            {canBootstrapCompanyOwner && (
              <Link className={navClass("/admin/company-owner")} href="/admin/company-owner">
                <span>◆</span> Company Owner
              </Link>
            )}
          </>
        ) : (
          <>
            <span className="nav-item disabled"><span>◎</span> Hồ sơ</span>
            {canReadEmployees && (
              <Link className={navClass("/employees")} href="/employees">
                <span>▤</span> Nhân sự
              </Link>
            )}
            {canReadAccounts && (
              <Link className={navClass("/accounts")} href="/accounts">
                <span>◎</span> Tài khoản
              </Link>
            )}
            {canUseRoleRequests && (
              <Link className={navClass("/role-requests")} href="/role-requests">
                <span>◧</span> Đề xuất role
              </Link>
            )}
            {canAssignRoleDirect && (
              <Link className={navClass("/role-grant")} href="/role-grant">
                <span>▥</span> Cấp quyền trực tiếp
              </Link>
            )}
            {canManageRbac && (
              <Link className={navClass("/rbac")} href="/rbac">
                <span>◇</span> Vai trò & quyền
              </Link>
            )}
            {canReadOrganization && (
              <Link className={navClass("/organization")} href="/organization">
                <span>▧</span> Danh mục tổ chức
              </Link>
            )}
            {canReadReports && (
              <Link className={navClass("/reports/workforce")} href="/reports/workforce">
                <span>▥</span> Báo cáo nhân sự
              </Link>
            )}
            {canUseAttendance && <Link className={navClass("/attendance")} href="/attendance"><span>◷</span> Chấm công</Link>}
            {canUseLeave && <Link className={navClass("/leave-requests")} href="/leave-requests"><span>▱</span> Đơn từ</Link>}
            {canUsePayroll && <Link className={navClass("/payslips")} href="/payslips"><span>◈</span> Phiếu lương</Link>}
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
