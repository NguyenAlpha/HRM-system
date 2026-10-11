"use client"

import Link from "next/link"
import { ArrowLeft } from "lucide-react"

import { usePortalSession } from "@/components/portal/portal-shell"
import { RoleAssignmentManager } from "@/components/role-assignment/role-assignment-manager"
import { Button } from "@/components/ui/button"

export function RoleAssignmentPage() {
  const { session, onSessionExpired } = usePortalSession()
  const canRequest = session.account.permissions.some((permission) => permission.code === "role.assignment.request")
  const canApprove = session.account.permissions.some((permission) => permission.code === "role.assignment.approve")

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">HRM WORKSPACE</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Đề xuất cấp role</h1>
          <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
            Đề xuất, theo dõi và phê duyệt việc cấp role nghiệp vụ cho tài khoản nhân sự.
          </p>
        </div>
        <Button variant="outline" nativeButton={false} render={<Link href="/dashboard" />}>
          <ArrowLeft /> Về tổng quan
        </Button>
      </header>

      <div className="mt-6">
        <RoleAssignmentManager
          currentAccountId={session.account.accountId}
          canRequest={canRequest}
          canApprove={canApprove}
          onSessionExpired={onSessionExpired}
        />
      </div>
    </>
  )
}
