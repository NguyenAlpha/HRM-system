"use client"

import { type FormEvent, useEffect, useRef, useState } from "react"

import { RbacFeedback } from "@/components/admin/rbac-controls"
import { getPermissionOptions, isSessionExpired, rbacErrorMessage, rbacMutation, rbacRequest, type Permission, type Role } from "@/lib/rbac"

export function RolePermissions({ role, onClose, onSessionExpired }: {
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
  const [message, setMessage] = useState<string | null>(null)
  const panelRef = useRef<HTMLElement>(null)
  const disabled = busy || loading
  const available = catalog.filter((permission) => permission.assignmentPolicy === "DELEGABLE"
    && !assigned.some((item) => item.id === permission.id))

  useEffect(() => { panelRef.current?.scrollIntoView({ block: "start" }) }, [role.id])

  useEffect(() => {
    let active = true
    Promise.all([getPermissionOptions(), rbacRequest<Permission[]>(`/roles/${role.id}/permissions`)])
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
  }, [role.id, revision, onSessionExpired])

  function reload() {
    setLoading(true)
    setError(null)
    setPermissionId("")
    setRevision((value) => value + 1)
  }

  async function changePermission(permission: Permission, remove: boolean) {
    if (remove && !window.confirm(`Gỡ quyền ${permission.code} khỏi ${role.code}?`)) return
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      if (remove) {
        await rbacMutation(`/roles/${role.id}/permissions/${permission.id}`, "DELETE")
      } else {
        await rbacMutation(`/roles/${role.id}/permissions`, "POST", { permissionId: permission.id })
      }
      setMessage(`${remove ? "Đã gỡ" : "Đã gán"} quyền ${permission.code}.`)
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
    <section className="dashboard-card rbac-assignment" ref={panelRef} aria-labelledby="assignment-title" aria-busy={loading}>
      <div className="card-heading">
        <div><h2 id="assignment-title">Quyền của {role.code}</h2><p>{role.name}</p></div>
        <div className="portal-actions">
          <button type="button" className="secondary-button" onClick={reload} disabled={disabled}>Tải lại quyền đã gán</button>
          <button type="button" className="secondary-button" onClick={onClose} disabled={busy}>Đóng</button>
        </div>
      </div>
      <RbacFeedback error={error} message={message} />
      {!role.isSystem && <form className="rbac-form" onSubmit={grant}>
          <fieldset disabled={disabled}>
            <label><span id="available-permission-label">Quyền có thể ủy quyền chưa được gán</span>
              <select aria-labelledby="available-permission-label" value={permissionId} onChange={(event) => setPermissionId(event.target.value)} required>
                <option value="">{available.length ? "Chọn quyền..." : "Không còn quyền để gán"}</option>
                {available.map((permission) => <option key={permission.id} value={permission.id}>
                  {permission.name} ({permission.code}){permission.isActive ? "" : " (tạm tắt)"}
                </option>)}
              </select>
            </label>
            <button className="rbac-primary" type="submit" disabled={!permissionId}>Gán quyền</button>
          </fieldset>
        </form>}
      <p className="rbac-note">{role.isSystem
        ? "System role và bộ quyền do ứng dụng định nghĩa, chỉ được phép xem."
        : "Chỉ permission DELEGABLE có thể gán; thay đổi có hiệu lực trong phiên đăng nhập hoặc lần làm mới token tiếp theo."}</p>
      {loading && <p role="status">Đang tải quyền...</p>}
      <ul className="rbac-assigned-list">
        {assigned.map((permission) => <li key={permission.id}>
          <div><strong>{permission.name}</strong><small><code>{permission.code}</code> · {permission.description} · {permission.isActive ? "Hoạt động" : "Tạm tắt"}</small></div>
          {!role.isSystem && <button type="button" className="danger-button" disabled={disabled} onClick={() => changePermission(permission, true)}>Gỡ quyền</button>}
        </li>)}
      </ul>
      {!loading && !error && assigned.length === 0 && <p>Vai trò này chưa được gán quyền.</p>}
    </section>
  )
}
