"use client"

import { useState } from "react"
import { Grid3x3, KeyRound, Users } from "lucide-react"

import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { PermissionManager } from "@/components/rbac/permission-manager"
import { RoleManager } from "@/components/rbac/role-manager"
import { RolePermissionMatrix } from "@/components/rbac/role-permission-matrix"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"

/** Role and permission management; the same page serves both portals. */
export function RbacPage() {
  const { portal, onSessionExpired } = usePortalSession()
  const [tab, setTab] = useState<"roles" | "permissions" | "matrix">("roles")

  return (
    <>
      <PageHeader
        title="Vai trò & quyền"
        description="Quản lý vai trò tùy chỉnh và xem danh mục quyền do hệ thống định nghĩa."
      />

      <Tabs
        className="mt-6"
        value={tab}
        onValueChange={(value) => setTab(value as "roles" | "permissions" | "matrix")}
      >
        <TabsList>
          <TabsTrigger value="roles"><Users /> Vai trò</TabsTrigger>
          <TabsTrigger value="permissions"><KeyRound /> Quyền</TabsTrigger>
          <TabsTrigger value="matrix"><Grid3x3 /> Ma trận</TabsTrigger>
        </TabsList>
        <TabsContent value="roles" className="mt-4">
          <RoleManager portal={portal} onSessionExpired={onSessionExpired} />
        </TabsContent>
        <TabsContent value="permissions" className="mt-4">
          <PermissionManager portal={portal} onSessionExpired={onSessionExpired} />
        </TabsContent>
        <TabsContent value="matrix" className="mt-4">
          <RolePermissionMatrix portal={portal} onSessionExpired={onSessionExpired} />
        </TabsContent>
      </Tabs>
    </>
  )
}
