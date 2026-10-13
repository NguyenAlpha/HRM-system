"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"

import { PORTAL_CONFIG } from "@/lib/auth/config"
import type { AccountSummary, Portal } from "@/lib/auth/types"

/** A null href is a menu entry that is not available yet. */
type NavSection = { title: string | null; links: { label: string; href: string | null }[] }

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

  const home = PORTAL_CONFIG[portal].homePath
  // Grouped menu; a link is listed only when its condition holds, and an empty group is hidden.
  const group = (title: string | null, links: [boolean, string, string | null][]): NavSection => ({
    title,
    links: links.filter(([visible]) => visible).map(([, label, href]) => ({ label, href })),
  })
  const sections: NavSection[] = admin
    ? [
      group(null, [[true, "Tổng quan", home]]),
      group("Quản trị truy cập", [
        [canReadAccounts, "Tài khoản", "/admin/accounts"],
        [true, "Vai trò & quyền", "/admin/rbac"],
        [canAssignRoleDirect, "Cấp quyền trực tiếp", "/admin/role-grant"],
        [canBootstrapCompanyOwner, "Company Owner", "/admin/company-owner"],
      ]),
      group("Dữ liệu tổ chức", [
        [canReadEmployees, "Nhân sự", "/admin/employees"],
        [canReadOrganization, "Danh mục tổ chức", "/admin/organization"],
      ]),
    ]
    : [
      group(null, [[true, "Tổng quan", home], [true, "Hồ sơ", null]]),
      group("Chấm công & lương", [
        [canUseAttendance, "Chấm công", "/attendance"],
        [canUseLeave, "Đơn từ", "/leave-requests"],
        [canUsePayroll, "Phiếu lương", "/payslips"],
      ]),
      group("Nhân sự", [
        [canReadEmployees, "Nhân sự", "/employees"],
        [canReadOrganization, "Danh mục tổ chức", "/organization"],
        [canReadReports, "Báo cáo nhân sự", "/reports/workforce"],
      ]),
      group("Quản trị truy cập", [
        [canReadAccounts, "Tài khoản", "/accounts"],
        [canUseRoleRequests, "Đề xuất role", "/role-requests"],
        [canAssignRoleDirect, "Cấp quyền trực tiếp", "/role-grant"],
        [canManageRbac, "Vai trò & quyền", "/rbac"],
      ]),
    ]

  return (
    <aside className="portal-sidebar">
      <div className="brand sidebar-brand">
        <span className="brand-mark">H</span>
        <span><strong>HRM</strong><small>{admin ? "Admin Console" : "People Workspace"}</small></span>
      </div>
      <nav aria-label="Điều hướng chính">
        {sections.map((section) => section.links.length > 0 && (
          <div key={section.title ?? "home"} className="nav-section">
            {section.title && <span className="nav-caption">{section.title}</span>}
            {section.links.map((link) => link.href === null
              ? <span key={link.label} className="nav-item disabled" aria-disabled="true">{link.label}</span>
              : <Link key={link.href} className={navClass(link.href)} href={link.href}>{link.label}</Link>)}
          </div>
        ))}
      </nav>
      <div className="sidebar-account">
        <span className="avatar">{(account.employee?.fullName ?? account.username).slice(0, 1).toUpperCase()}</span>
        <span><strong>{account.employee?.fullName ?? account.username}</strong><small>{account.email}</small></span>
      </div>
    </aside>
  )
}
