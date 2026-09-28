import Link from "next/link"

import { PORTAL_CONFIG } from "@/lib/auth/config"
import type { AccountSummary, Portal } from "@/lib/auth/types"

export function PortalSidebar({ portal, account, active = "overview" }: {
  portal: Portal
  account: AccountSummary
  active?: "overview" | "rbac" | "employees" | "role-requests" | "company-owner" | "role-grant" | "organization"
}) {
  const admin = portal === "admin"
  const canBootstrapCompanyOwner = account.permissions.some(
    (permission) => permission.code === "organization.company_owner.bootstrap",
  )
  const canReadEmployees = account.permissions.some((permission) => permission.code === "employee.read")
  const canUseRoleRequests = account.permissions.some(
    (permission) => permission.code === "role.assignment.request" || permission.code === "role.assignment.approve",
  )
  const canManageRbac = account.permissions.some((permission) => permission.code === "rbac.manage")
  const canAssignRoleDirect = account.permissions.some((permission) => permission.code === "account.role.assign")
  const canReadOrganization = account.permissions.some((permission) => permission.code === "organization.read")

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
            <span className="nav-item disabled"><span>◷</span> Chấm công</span>
            <span className="nav-item disabled"><span>▱</span> Đơn từ</span>
            <span className="nav-item disabled"><span>◈</span> Phiếu lương</span>
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
