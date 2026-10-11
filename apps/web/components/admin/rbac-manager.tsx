"use client"

import Link from "next/link"
import { useState } from "react"
import { ArrowLeft, KeyRound, Users } from "lucide-react"

import { PermissionManager } from "@/components/admin/permission-manager"
import { RoleManager } from "@/components/admin/role-manager"
import { usePortalSession } from "@/components/auth/portal-shell"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"

export function RbacManager() {
  const { onSessionExpired } = usePortalSession()
  const [tab, setTab] = useState<"roles" | "permissions">("roles")

  return (
    <>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="eyebrow">ADMIN CONSOLE</p>
          <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Vai trò & quyền</h1>
          <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">
            Quản lý vai trò tùy chỉnh và xem danh mục quyền do hệ thống định nghĩa.
          </p>
        </div>
        <Button variant="outline" nativeButton={false} render={<Link href="/admin" />}>
          <ArrowLeft /> Về tổng quan
        </Button>
      </header>

      <Tabs
        className="mt-6"
        value={tab}
        onValueChange={(value) => setTab(value as "roles" | "permissions")}
      >
        <TabsList>
          <TabsTrigger value="roles"><Users /> Vai trò</TabsTrigger>
          <TabsTrigger value="permissions"><KeyRound /> Quyền</TabsTrigger>
        </TabsList>
        <TabsContent value="roles" className="mt-4">
          <RoleManager portal="admin" onSessionExpired={onSessionExpired} />
        </TabsContent>
        <TabsContent value="permissions" className="mt-4">
          <PermissionManager portal="admin" onSessionExpired={onSessionExpired} />
        </TabsContent>
      </Tabs>
    </>
  )
}
