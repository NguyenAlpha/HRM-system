"use client"

import { type FormEvent, useEffect, useMemo, useState } from "react"
import { Loader2, ShieldOff, X } from "lucide-react"
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import type { Portal } from "@/lib/auth/types"
import {
  getPermissionOptions,
  isSessionExpired,
  rbacErrorMessage,
  rbacMutation,
  rbacRequest,
  type Permission,
  type Role,
} from "@/lib/rbac"

export function RolePermissions({ portal, role, onClose, onSessionExpired }: {
  portal: Portal
  role: Role
  onClose: () => void
  onSessionExpired: () => void
}) {
  const [catalog, setCatalog] = useState<Permission[]>([])
  const [assigned, setAssigned] = useState<Permission[]>([])
  const [permissionId, setPermissionId] = useState("")
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [revision, setRevision] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const disabled = busy || loading
  const available = catalog.filter((permission) => permission.assignmentPolicy === "DELEGABLE"
    && !assigned.some((item) => item.id === permission.id))
  const permissionItems = useMemo(
    () => Object.fromEntries(available.map((permission) => [
      String(permission.id),
      `${permission.name} (${permission.code})${permission.isActive ? "" : " · tạm tắt"}`,
    ])),
    [available],
  )

  useEffect(() => {
    let active = true
    Promise.all([getPermissionOptions(portal), rbacRequest<Permission[]>(portal, `/roles/${role.id}/permissions`)])
      .then(([permissions, rolePermissions]) => {
        if (!active) return
        setCatalog(permissions)
        setAssigned(rolePermissions)
        setLoading(false)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isSessionExpired(caught)) onSessionExpired()
        setError(rbacErrorMessage(caught))
        setLoading(false)
      })
    return () => { active = false }
  }, [portal, role.id, revision, onSessionExpired])

  function reload() {
    setLoading(true)
    setError(null)
    setPermissionId("")
    setRevision((value) => value + 1)
  }

  async function changePermission(permission: Permission, remove: boolean) {
    setBusy(true)
    setError(null)
    try {
      if (remove) {
        await rbacMutation(portal, `/roles/${role.id}/permissions/${permission.id}`, "DELETE")
      } else {
        await rbacMutation(portal, `/roles/${role.id}/permissions`, "POST", { permissionId: permission.id })
      }
      toast.success(`${remove ? "Đã gỡ" : "Đã gán"} quyền ${permission.code}`)
      reload()
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      setError(rbacErrorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  function grant(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const permission = available.find((item) => item.id === Number(permissionId))
    if (permission) void changePermission(permission, false)
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Quyền của {role.code}</DialogTitle>
          <DialogDescription>{role.name}</DialogDescription>
        </DialogHeader>

        <div className="max-h-[65vh] overflow-y-auto py-1">
          <RbacFeedback error={error} />

          {role.isSystem ? (
            <p className="mt-3 flex items-start gap-2 rounded-lg bg-muted px-3 py-2.5 text-sm text-muted-foreground">
              <ShieldOff className="mt-0.5 size-4 shrink-0" />
              System role và bộ quyền do ứng dụng định nghĩa, chỉ được phép xem.
            </p>
          ) : (
            <form className="mt-3 flex items-end gap-2" onSubmit={grant}>
              <div className="grid flex-1 gap-1.5">
                <Select value={permissionId} items={permissionItems} onValueChange={(value) => setPermissionId(value ?? "")} disabled={disabled || available.length === 0}>
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder={available.length ? "Chọn quyền để gán..." : "Không còn quyền để gán"} />
                  </SelectTrigger>
                  <SelectContent>
                    {available.map((permission) => (
                      <SelectItem key={permission.id} value={String(permission.id)}>
                        {permission.name} ({permission.code}){permission.isActive ? "" : " · tạm tắt"}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <Button type="submit" disabled={disabled || !permissionId}>
                {busy ? <Loader2 className="animate-spin" /> : "Gán"}
              </Button>
            </form>
          )}

          <p className="mt-4 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
            Đã gán · {assigned.length}
          </p>

          <ul className="mt-2 grid gap-2">
            {loading &&
              Array.from({ length: 3 }).map((_, index) => (
                <li key={index}><Skeleton className="h-14 w-full" /></li>
              ))}

            {!loading && assigned.map((permission) => (
              <li
                key={permission.id}
                className="flex items-start justify-between gap-3 rounded-lg border px-3 py-2.5"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium">{permission.name}</p>
                  <p className="mt-0.5 truncate text-xs text-muted-foreground">
                    <code>{permission.code}</code>
                    {!permission.isActive && (
                      <Badge variant="outline" className="ml-1.5 align-middle">tạm tắt</Badge>
                    )}
                  </p>
                </div>
                {!role.isSystem && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    className="shrink-0 text-muted-foreground hover:text-destructive"
                    disabled={disabled}
                    onClick={() => void changePermission(permission, true)}
                    aria-label={`Gỡ quyền ${permission.code}`}
                  >
                    <X />
                  </Button>
                )}
              </li>
            ))}

            {!loading && !error && assigned.length === 0 && (
              <li className="rounded-lg border border-dashed px-3 py-6 text-center text-sm text-muted-foreground">
                Vai trò này chưa được gán quyền.
              </li>
            )}
          </ul>
        </div>

        <DialogFooter>
          <DialogClose render={<Button type="button" variant="outline" />}>Đóng</DialogClose>
          <Button type="button" variant="outline" onClick={reload} disabled={disabled}>
            Tải lại
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
