"use client"

import { useState } from "react"
import { KeyRound, Users } from "lucide-react"

import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { PermissionManager } from "@/components/rbac/permission-manager"
import { RoleManager } from "@/components/rbac/role-manager"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"

/** Role and permission management; the same page serves both portals. */
export function RbacPage() {
  const { portal, onSessionExpired } = usePortalSession()
  const [tab, setTab] = useState<"roles" | "permissions">("roles")

  return (
    <>
      <PageHeader
        title="Vai trò & quyền"
        description="Quản lý vai trò tùy chỉnh và xem danh mục quyền do hệ thống định nghĩa."
        backToOverview
      />

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
          <RoleManager portal={portal} onSessionExpired={onSessionExpired} />
        </TabsContent>
        <TabsContent value="permissions" className="mt-4">
          <PermissionManager portal={portal} onSessionExpired={onSessionExpired} />
        </TabsContent>
      </Tabs>
    </>
  )
}
