"use client"

import { AccountManager } from "@/components/account/account-manager"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"

export function AccountPage() {
  const { portal, session, onSessionExpired } = usePortalSession()
  const has = (code: string) => session.account.permissions.some((permission) => permission.code === code)

  return (
    <>
      <PageHeader
        title="Tài khoản"
        description="Quản lý vòng đời tài khoản đăng nhập. Tài khoản mới được cấp từ hồ sơ nhân viên."
      />

      <div className="mt-6 grid gap-6">
        {has("account.read") ? (
          <AccountManager
            portal={portal}
            currentAccountId={session.account.accountId}
            currentRoleCodes={session.account.roles.map((role) => role.code)}
            canManage={has("account.manage")}
            canManageActivation={has("account.activation.manage")}
            onSessionExpired={onSessionExpired}
          />
        ) : (
          <p role="alert" className="text-[var(--muted-ink)]">Tài khoản của bạn không có quyền xem danh sách tài khoản.</p>
        )}
      </div>
    </>
  )
}
