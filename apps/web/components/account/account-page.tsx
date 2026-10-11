"use client"

import { AccountManager } from "@/components/account/account-manager"
import { usePortalSession } from "@/components/auth/portal-shell"

export function AccountPage() {
  const { session, onSessionExpired } = usePortalSession()
  const has = (code: string) => session.account.permissions.some((permission) => permission.code === code)

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">HRM WORKSPACE</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Tài khoản</h1>
          <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
            Quản lý vòng đời tài khoản đăng nhập. Tài khoản mới được cấp từ hồ sơ nhân viên.
          </p>
        </div>
      </header>

      <div className="mt-6 grid gap-6">
        {has("account.read") ? (
          <AccountManager
            currentAccountId={session.account.accountId}
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
