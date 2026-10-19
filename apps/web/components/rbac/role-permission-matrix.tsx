"use client"

import { Fragment, useEffect, useMemo, useState } from "react"
import { Check, RefreshCw } from "lucide-react"

import { ErrorAlert } from "@/components/common/list-controls"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import type { Portal } from "@/lib/auth/types"
import {
  getPermissionOptions,
  isSessionExpired,
  PERMISSION_MODULES,
  rbacErrorMessage,
  rbacRequest,
  type Permission,
  type PermissionModule,
  type RoleWithPermissions,
} from "@/lib/rbac"

/** Every role (columns) against every permission (rows), grouped by module. */
export function RolePermissionMatrix({ portal, onSessionExpired }: { portal: Portal; onSessionExpired: () => void }) {
  const [roles, setRoles] = useState<RoleWithPermissions[]>([])
  const [permissions, setPermissions] = useState<Permission[]>([])
  const [hiddenModules, setHiddenModules] = useState<Set<PermissionModule>>(new Set())
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    Promise.all([
      rbacRequest<RoleWithPermissions[]>(portal, "/roles/with-permissions"),
      getPermissionOptions(portal),
    ])
      .then(([roleList, catalog]) => {
        if (!active) return
        setRoles(roleList)
        setPermissions(catalog)
        setLoading(false)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isSessionExpired(caught)) onSessionExpired()
        setError(rbacErrorMessage(caught))
        setLoading(false)
      })
    return () => { active = false }
  }, [portal, revision, onSessionExpired])

  const grantedByRole = useMemo(
    () => new Map(roles.map((role) => [role.id, new Set(role.permissions.map((permission) => permission.id))])),
    [roles],
  )
  const groups = useMemo(
    () => PERMISSION_MODULES
      .map((module) => ({ module, permissions: permissions.filter((permission) => permission.module === module) }))
      .filter((group) => group.permissions.length > 0),
    [permissions],
  )
  const visibleGroups = groups.filter((group) => !hiddenModules.has(group.module))

  function reload() {
    setLoading(true)
    setError(null)
    setRevision((value) => value + 1)
  }

  function toggleModule(module: PermissionModule) {
    setHiddenModules((current) => {
      const next = new Set(current)
      if (next.has(module)) next.delete(module)
      else next.add(module)
      return next
    })
  }

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
        <div>
          <CardTitle>Ma trận vai trò – quyền</CardTitle>
          <CardDescription>
            Mỗi cột là một vai trò, mỗi dòng là một quyền; dấu tích cho biết vai trò đang có quyền đó.
          </CardDescription>
        </div>
        <Button type="button" variant="outline" size="sm" onClick={reload} disabled={loading}>
          <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
        </Button>
      </CardHeader>
      <CardContent className="grid gap-4">
        <ErrorAlert error={error} />

        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm text-muted-foreground">Nhóm quyền:</span>
          {groups.map((group) => {
            const shown = !hiddenModules.has(group.module)
            return (
              <Button
                key={group.module}
                type="button"
                size="sm"
                variant={shown ? "default" : "outline"}
                aria-pressed={shown}
                onClick={() => toggleModule(group.module)}
              >
                {group.module}
                <span className="opacity-70">{group.permissions.length}</span>
              </Button>
            )
          })}
          {groups.length > 0 && (
            <Button
              type="button"
              size="sm"
              variant="ghost"
              onClick={() => setHiddenModules(hiddenModules.size > 0
                ? new Set()
                : new Set(groups.map((group) => group.module)))}
            >
              {hiddenModules.size > 0 ? "Hiện tất cả" : "Ẩn tất cả"}
            </Button>
          )}
        </div>

        {loading && <Skeleton className="h-64 w-full" />}

        {!loading && roles.length > 0 && visibleGroups.length > 0 && (
          <div className="max-h-[70vh] overflow-auto rounded-xl border">
            <table className="w-full border-separate border-spacing-0 text-sm">
              <thead>
                <tr>
                  <th className="sticky top-0 left-0 z-30 min-w-64 border-b bg-background px-3 py-2 text-left font-medium">
                    Quyền
                  </th>
                  {roles.map((role) => (
                    <th
                      key={role.id}
                      title={role.description ?? role.name}
                      className="sticky top-0 z-20 min-w-28 border-b border-l bg-background px-2 py-2 text-center align-bottom font-medium"
                    >
                      <code className="block text-xs">{role.code}</code>
                      <span className="block text-xs font-normal text-muted-foreground">{role.name}</span>
                      {!role.isSystem && <Badge variant="outline" className="mt-1">tùy chỉnh</Badge>}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {visibleGroups.map((group) => (
                  <Fragment key={group.module}>
                    <tr>
                      <td className="sticky left-0 z-10 border-b bg-muted px-3 py-1.5">
                        <Badge>{group.module}</Badge>
                      </td>
                      <td colSpan={roles.length} className="border-b bg-muted" />
                    </tr>
                    {group.permissions.map((permission) => (
                      <tr key={permission.id} className="hover:bg-muted/30">
                        <td className="sticky left-0 z-10 border-b bg-background px-3 py-2">
                          <span className="flex flex-wrap items-center gap-1.5 font-medium">
                            {permission.name}
                            {!permission.isActive && <Badge variant="outline">tạm tắt</Badge>}
                          </span>
                          <code className="block text-xs text-muted-foreground">{permission.code}</code>
                        </td>
                        {roles.map((role) => (
                          <td key={role.id} className="border-b border-l text-center">
                            {grantedByRole.get(role.id)?.has(permission.id) && (
                              <Check className="mx-auto size-4 text-primary" aria-label="Có quyền" />
                            )}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {!loading && !error && (roles.length === 0 || visibleGroups.length === 0) && (
          <p className="rounded-lg border border-dashed px-3 py-8 text-center text-sm text-muted-foreground">
            {roles.length === 0 ? "Chưa có vai trò nào." : "Đang ẩn tất cả nhóm quyền."}
          </p>
        )}
      </CardContent>
    </Card>
  )
}
