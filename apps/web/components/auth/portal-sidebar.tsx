import Link from "next/link"

import { PORTAL_CONFIG } from "@/lib/auth/config"
import type { AccountSummary, Portal } from "@/lib/auth/types"

export function PortalSidebar({ portal, account, active = "overview" }: {
  portal: Portal
  account: AccountSummary
  active?: "overview" | "rbac"
}) {
  const admin = portal === "admin"

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
          </>
        ) : (
          <>
            <span className="nav-item disabled"><span>◎</span> Hồ sơ</span>
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
