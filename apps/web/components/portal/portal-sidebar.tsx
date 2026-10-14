"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"

import { PORTAL_CONFIG } from "@/lib/auth/config"
import type { AccountSummary, Portal } from "@/lib/auth/types"

/** A null href is a menu entry that is not available yet. */
type NavLink = { label: string; href: string | null }

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
    ["request.self.read", "request.read", "request.self.create"].includes(permission.code))
  const canReadPayrollPeriods = account.permissions.some((permission) => permission.code === "report.payroll.read")
  const canReadOwnPayslips = !!account.employee
    && account.permissions.some((permission) => permission.code === "payroll.self.read")
  const canReadCompensation = account.permissions.some((permission) => permission.code === "compensation.read")

  const home = PORTAL_CONFIG[portal].homePath
  // A link is listed only when its condition holds.
  const visible = (links: [boolean, string, string | null][]): NavLink[] =>
    links.filter(([show]) => show).map(([, label, href]) => ({ label, href }))
  const links = admin
    ? visible([
      [true, "Tổng quan", home],
      [canReadEmployees, "Nhân sự", "/admin/employees"],
      [canReadAccounts, "Tài khoản", "/admin/accounts"],
      [true, "Vai trò & quyền", "/admin/rbac"],
      [canAssignRoleDirect, "Cấp quyền trực tiếp", "/admin/role-grant"],
      [canReadOrganization, "Danh mục tổ chức", "/admin/organization"],
      [canBootstrapCompanyOwner, "Company Owner", "/admin/company-owner"],
    ])
    : visible([
      [true, "Tổng quan", home],
      [true, "Hồ sơ", null],
      [canReadEmployees, "Nhân sự", "/employees"],
      [canReadAccounts, "Tài khoản", "/accounts"],
      [canUseRoleRequests, "Đề xuất role", "/role-requests"],
      [canAssignRoleDirect, "Cấp quyền trực tiếp", "/role-grant"],
      [canManageRbac, "Vai trò & quyền", "/rbac"],
      [canReadOrganization, "Danh mục tổ chức", "/organization"],
      [canReadReports, "Báo cáo nhân sự", "/reports/workforce"],
      [canUseAttendance, "Chấm công", "/attendance"],
      [canUseLeave, "Đơn từ", "/leave-requests"],
      [canReadPayrollPeriods, "Kỳ lương", "/payroll"],
      [canReadOwnPayslips, "Phiếu lương", "/payslips"],
      [canReadCompensation, "Chính sách phụ cấp", "/allowances"],
    ])

  return (
    <aside className="portal-sidebar">
      <div className="brand sidebar-brand">
        <span className="brand-mark">H</span>
        <span><strong>HRM</strong><small>{admin ? "Admin Console" : "People Workspace"}</small></span>
      </div>
      <nav aria-label="Điều hướng chính">
        {links.map((link) => link.href === null
          ? <span key={link.label} className="nav-item disabled" aria-disabled="true">{link.label}</span>
          : <Link key={link.href} className={navClass(link.href)} href={link.href}>{link.label}</Link>)}
      </nav>
      <div className="sidebar-account">
        <span className="avatar">{(account.employee?.fullName ?? account.username).slice(0, 1).toUpperCase()}</span>
        <span><strong>{account.employee?.fullName ?? account.username}</strong><small>{account.email}</small></span>
      </div>
    </aside>
  )
}
