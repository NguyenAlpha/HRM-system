"use client"

import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { useState } from "react"
import { LogOut } from "lucide-react"

import { logout } from "@/lib/auth/client"
import { PORTAL_CONFIG } from "@/lib/auth/config"
import type { AccountSummary, Portal } from "@/lib/auth/types"

type NavLink = { label: string; href: string }
/** A titled block of the menu; a null title is the untitled block at the top. */
type NavGroup = { title: string | null; links: NavLink[] }

export function PortalSidebar({ portal, account }: { portal: Portal; account: AccountSummary }) {
  const admin = portal === "admin"
  const pathname = usePathname()
  const router = useRouter()
  const [signingOut, setSigningOut] = useState(false)
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
  const canReadHrReports = account.permissions.some((permission) => permission.code === "report.hr.read")
  const canReadPayrollReports = account.permissions.some((permission) => permission.code === "report.payroll.read")
  const canReadAttendance = account.permissions.some((permission) => permission.code === "attendance.read")
  const canUseAttendance = account.permissions.some((permission) =>
    ["attendance.self.read", "attendance.read", "attendance.manage"].includes(permission.code))
  const canUseLeave = account.permissions.some((permission) =>
    ["request.self.read", "request.read", "request.self.create"].includes(permission.code))
  const canReadPayrollPeriods = account.permissions.some((permission) => permission.code === "report.payroll.read")
  const canReadOwnPayslips = !!account.employee
    && account.permissions.some((permission) => permission.code === "payroll.self.read")
  const canReadCompensation = account.permissions.some((permission) => permission.code === "compensation.read")
  const canManageCompany = account.permissions.some((permission) => permission.code === "company.manage")
  const canReadOwnProfile = !!account.employee
    && account.permissions.some((permission) => permission.code === "profile.self.read")

  async function handleLogout() {
    setSigningOut(true)
    try {
      await logout(portal)
    } finally {
      router.replace(PORTAL_CONFIG[portal].loginPath)
      router.refresh()
    }
  }

  const home = PORTAL_CONFIG[portal].homePath
  // A link is listed only when its condition holds; a group with no visible link is dropped.
  const group = (title: string | null, links: [boolean, string, string][]): NavGroup => ({
    title,
    links: links.filter(([show]) => show).map(([, label, href]) => ({ label, href })),
  })
  const groups = (admin
    ? [group(null, [
      [true, "Tổng quan", home],
      [canReadEmployees, "Nhân sự", "/admin/employees"],
      [canReadAccounts, "Tài khoản", "/admin/accounts"],
      [true, "Vai trò & quyền", "/admin/rbac"],
      [canAssignRoleDirect, "Cấp quyền trực tiếp", "/admin/role-grant"],
      [canReadOrganization, "Danh mục tổ chức", "/admin/organization"],
      [canBootstrapCompanyOwner, "Company Owner", "/admin/company-owner"],
    ])]
    : [
      group(null, [
        [true, "Tổng quan", home],
        [canReadOwnProfile, "Hồ sơ của tôi", "/profile"],
      ]),
      group("Nhân sự", [
        [canReadEmployees, "Danh sách nhân sự", "/employees"],
        [canReadOrganization, "Danh mục tổ chức", "/organization"],
      ]),
      group("Chấm công & nghỉ", [
        [canUseAttendance, "Chấm công", "/attendance"],
        [canUseLeave, "Đơn từ", "/leave-requests"],
        [canUseLeave, "Đơn nghỉ việc", "/resignations"],
      ]),
      group("Lương", [
        [canReadPayrollPeriods, "Kỳ lương", "/payroll"],
        [canReadOwnPayslips, "Phiếu lương của tôi", "/payslips"],
        [canReadCompensation, "Chính sách phụ cấp", "/allowances"],
      ]),
      group("Báo cáo", [
        [canReadReports, "Cơ cấu nhân sự", "/reports/workforce"],
        [canReadHrReports, "Biến động nhân sự", "/reports/turnover"],
        [canReadHrReports, "Tình hình nhân sự tháng", "/reports/monthly"],
        [canReadAttendance, "Tổng hợp chấm công", "/reports/attendance"],
        [canReadHrReports, "Phép năm", "/reports/leave"],
        [canReadPayrollReports, "Quỹ lương & thuế", "/reports/payroll"],
      ]),
      group("Hệ thống", [
        [canManageCompany, "Thông tin doanh nghiệp", "/company"],
        [canReadAccounts, "Tài khoản", "/accounts"],
        [canUseRoleRequests, "Đề xuất role", "/role-requests"],
        [canAssignRoleDirect, "Cấp quyền trực tiếp", "/role-grant"],
        [canManageRbac, "Vai trò & quyền", "/rbac"],
      ]),
    ]).filter((navGroup) => navGroup.links.length > 0)

  return (
    <aside className="portal-sidebar">
      <div className="brand sidebar-brand">
        <span className="brand-mark">H</span>
        <span><strong>HRM</strong><small>{admin ? "Admin Console" : "People Workspace"}</small></span>
      </div>
      <nav aria-label="Điều hướng chính">
        {groups.map((navGroup) => (
          <div key={navGroup.title ?? "main"} className="nav-group">
            {navGroup.title && <p className="nav-group-title">{navGroup.title}</p>}
            {navGroup.links.map((link) => (
              <Link key={link.href} className={navClass(link.href)} href={link.href}>{link.label}</Link>
            ))}
          </div>
        ))}
      </nav>
      <div className="sidebar-account">
        <span className="avatar">{(account.employee?.fullName ?? account.username).slice(0, 1).toUpperCase()}</span>
        <span><strong>{account.employee?.fullName ?? account.username}</strong><small>{account.email}</small></span>
        <button type="button" className="sidebar-logout" onClick={handleLogout} disabled={signingOut}
          aria-label="Đăng xuất" title="Đăng xuất"><LogOut /></button>
      </div>
    </aside>
  )
}
