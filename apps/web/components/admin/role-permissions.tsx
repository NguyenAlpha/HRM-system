"use client"

import { useEffect, useMemo, useState } from "react"
import { Loader2, Search, ShieldOff } from "lucide-react"
import { toast } from "sonner"

import { RbacFeedback } from "@/components/admin/rbac-controls"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import type { Portal } from "@/lib/auth/types"
import {
  getPermissionOptions,
  isSessionExpired,
  PERMISSION_MODULES,
  rbacErrorMessage,
  rbacMutation,
  rbacRequest,
  type Permission,
  type PermissionModule,
  type Role,
} from "@/lib/rbac"

function equalIds(left: Set<number>, right: Set<number>) {
  return left.size === right.size && Array.from(left).every((id) => right.has(id))
}

export function RolePermissions({ portal, role, onClose, onSessionExpired }: {
  portal: Portal
  role: Role
  onClose: () => void
  onSessionExpired: () => void
}) {
  const [catalog, setCatalog] = useState<Permission[]>([])
  const [assigned, setAssigned] = useState<Permission[]>([])
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())
  const [savedIds, setSavedIds] = useState<Set<number>>(new Set())
  const [query, setQuery] = useState("")
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [revision, setRevision] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const disabled = busy || loading
  const dirty = !equalIds(selectedIds, savedIds)

  const selectablePermissions = useMemo(
    () => catalog.filter((permission) => permission.assignmentPolicy === "DELEGABLE"),
    [catalog],
  )
  const displayedPermissions = useMemo(() => {
    const source = role.isSystem ? assigned : selectablePermissions
    const normalizedQuery = query.trim().toLowerCase()
    if (!normalizedQuery) return source
    return source.filter((permission) =>
      permission.code.toLowerCase().includes(normalizedQuery)
      || permission.name.toLowerCase().includes(normalizedQuery)
      || permission.description.toLowerCase().includes(normalizedQuery),
    )
  }, [assigned, query, role.isSystem, selectablePermissions])
  const groups = useMemo(
    () => PERMISSION_MODULES
      .map((module) => ({
        module,
        permissions: displayedPermissions.filter((permission) => permission.module === module),
      }))
      .filter((group) => group.permissions.length > 0),
    [displayedPermissions],
  )

  useEffect(() => {
    let active = true
    Promise.all([
      role.isSystem ? Promise.resolve([]) : getPermissionOptions(portal),
      rbacRequest<Permission[]>(portal, `/roles/${role.id}/permissions`),
    ])
      .then(([permissions, rolePermissions]) => {
        if (!active) return
        const editableIds = rolePermissions
          .filter((permission) => permission.assignmentPolicy === "DELEGABLE")
          .map((permission) => permission.id)
        setCatalog(permissions)
        setAssigned(rolePermissions)
        setSelectedIds(new Set(editableIds))
        setSavedIds(new Set(editableIds))
        setLoading(false)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isSessionExpired(caught)) onSessionExpired()
        setError(rbacErrorMessage(caught))
        setLoading(false)
      })
    return () => { active = false }
  }, [portal, role.id, role.isSystem, revision, onSessionExpired])

  function reload() {
    setLoading(true)
    setError(null)
    setRevision((value) => value + 1)
  }

  function togglePermission(permissionId: number) {
    setSelectedIds((current) => {
      const next = new Set(current)
      if (next.has(permissionId)) next.delete(permissionId)
      else next.add(permissionId)
      return next
    })
  }

  function toggleModule(module: PermissionModule) {
    const modulePermissions = selectablePermissions.filter((permission) => permission.module === module)
    const allSelected = modulePermissions.every((permission) => selectedIds.has(permission.id))
    setSelectedIds((current) => {
      const next = new Set(current)
      modulePermissions.forEach((permission) => {
        if (allSelected) next.delete(permission.id)
        else next.add(permission.id)
      })
      return next
    })
  }

  function toggleAll() {
    const allSelected = selectablePermissions.every((permission) => selectedIds.has(permission.id))
    setSelectedIds(allSelected
      ? new Set()
      : new Set(selectablePermissions.map((permission) => permission.id)))
  }

  async function save() {
    setBusy(true)
    setError(null)
    try {
      const updated = await rbacMutation<Permission[]>(
        portal,
        `/roles/${role.id}/permissions`,
        "PUT",
        { permissionIds: Array.from(selectedIds).sort((left, right) => left - right) },
      )
      const updatedIds = new Set(updated.map((permission) => permission.id))
      setAssigned(updated)
      setSelectedIds(updatedIds)
      setSavedIds(new Set(updatedIds))
      toast.success(`Đã cập nhật ${updated.length} quyền cho ${role.code}`)
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      setError(rbacErrorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-4xl">
        <DialogHeader>
          <DialogTitle>Quyền của {role.code}</DialogTitle>
          <DialogDescription>
            {role.name} · {role.isSystem ? `${assigned.length} quyền hệ thống` : `${selectedIds.size} quyền đã chọn`}
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-[70vh] overflow-y-auto py-1">
          <RbacFeedback error={error} />

          {role.isSystem && (
            <p className="mt-3 flex items-start gap-2 rounded-lg bg-muted px-3 py-2.5 text-sm text-muted-foreground">
              <ShieldOff className="mt-0.5 size-4 shrink-0" />
              System role và bộ quyền do ứng dụng định nghĩa, chỉ được phép xem.
            </p>
          )}

          <div className="mt-3 flex flex-wrap items-center gap-2">
            <div className="relative min-w-56 flex-1">
              <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                className="pl-9"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Tìm theo tên, mã hoặc mô tả..."
                disabled={loading}
              />
            </div>
            {!role.isSystem && (
              <>
                <Button type="button" variant="outline" onClick={toggleAll} disabled={disabled}>
                  {selectablePermissions.length > 0
                    && selectablePermissions.every((permission) => selectedIds.has(permission.id))
                    ? "Bỏ chọn tất cả"
                    : "Chọn tất cả"}
                </Button>
                <Badge variant="secondary">{selectedIds.size}/{selectablePermissions.length} quyền</Badge>
              </>
            )}
          </div>

          <div className="mt-4 grid gap-4">
            {loading && Array.from({ length: 3 }).map((_, index) => (
              <Skeleton key={index} className="h-32 w-full" />
            ))}

            {!loading && groups.map((group) => {
              const moduleCatalog = role.isSystem
                ? group.permissions
                : selectablePermissions.filter((permission) => permission.module === group.module)
              const selectedCount = moduleCatalog.filter((permission) => selectedIds.has(permission.id)).length
              const allSelected = moduleCatalog.length > 0 && selectedCount === moduleCatalog.length
              return (
                <section key={group.module} className="rounded-xl border">
                  <header className="flex flex-wrap items-center justify-between gap-2 border-b bg-muted/40 px-4 py-3">
                    <div className="flex items-center gap-2">
                      <Badge>{group.module}</Badge>
                      <span className="text-xs text-muted-foreground">
                        {role.isSystem ? `${group.permissions.length} quyền` : `${selectedCount}/${moduleCatalog.length} đã chọn`}
                      </span>
                    </div>
                    {!role.isSystem && (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        disabled={disabled}
                        onClick={() => toggleModule(group.module)}
                      >
                        {allSelected ? "Bỏ chọn module" : "Chọn cả module"}
                      </Button>
                    )}
                  </header>

                  <div className="grid gap-0 sm:grid-cols-2">
                    {group.permissions.map((permission) => (
                      <label
                        key={permission.id}
                        className={`flex gap-3 border-b px-4 py-3 last:border-b-0 sm:odd:border-r ${role.isSystem ? "cursor-default" : "cursor-pointer hover:bg-muted/30"}`}
                      >
                        <input
                          type="checkbox"
                          className="mt-1 size-4 shrink-0 accent-primary"
                          checked={role.isSystem || selectedIds.has(permission.id)}
                          disabled={role.isSystem || disabled}
                          onChange={() => togglePermission(permission.id)}
                        />
                        <span className="min-w-0">
                          <span className="flex flex-wrap items-center gap-1.5 text-sm font-medium">
                            {permission.name}
                            {!permission.isActive && <Badge variant="outline">tạm tắt</Badge>}
                          </span>
                          <code className="mt-0.5 block truncate text-xs text-muted-foreground">{permission.code}</code>
                          <span className="mt-1 block text-xs text-muted-foreground">{permission.description}</span>
                        </span>
                      </label>
                    ))}
                  </div>
                </section>
              )
            })}

            {!loading && groups.length === 0 && (
              <p className="rounded-lg border border-dashed px-3 py-8 text-center text-sm text-muted-foreground">
                {query ? "Không tìm thấy quyền phù hợp." : "Vai trò này chưa có quyền."}
              </p>
            )}
          </div>
        </div>

        <DialogFooter>
          <DialogClose render={<Button type="button" variant="outline" />}>Đóng</DialogClose>
          <Button type="button" variant="outline" onClick={reload} disabled={disabled}>Tải lại</Button>
          {!role.isSystem && (
            <>
              <Button
                type="button"
                variant="outline"
                disabled={disabled || !dirty}
                onClick={() => setSelectedIds(new Set(savedIds))}
              >
                Hoàn tác
              </Button>
              <Button type="button" disabled={disabled || !dirty} onClick={() => void save()}>
                {busy && <Loader2 className="animate-spin" />}
                Lưu quyền
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
