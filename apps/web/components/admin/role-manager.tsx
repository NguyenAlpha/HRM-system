"use client"

import { type FormEvent, useEffect, useRef, useState } from "react"

import { Pagination, RbacFeedback } from "@/components/admin/rbac-controls"
import { RolePermissions } from "@/components/admin/role-permissions"
import { isSessionExpired, rbacErrorMessage, rbacMutation, rbacRequest, type Page, type Role } from "@/lib/rbac"

const EMPTY_FORM = { code: "", name: "", description: "", isActive: true }

export function RoleManager({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [data, setData] = useState<Page<Role> | null>(null)
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [editing, setEditing] = useState<Role | null>(null)
  const [selected, setSelected] = useState<Role | null>(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const formRef = useRef<HTMLFormElement>(null)
  const disabled = busy || loading

  useEffect(() => {
    let active = true
    rbacRequest<Page<Role>>(`/roles?page=${page}&size=10&sort=id,desc`)
      .then((result) => {
        if (!active) return
        if (page > 0 && page >= result.totalPages) {
          setPage(Math.max(0, result.totalPages - 1))
          return
        }
        setData(result)
        setLoading(false)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isSessionExpired(caught)) onSessionExpired()
        setError(rbacErrorMessage(caught))
        setLoading(false)
      })
    return () => { active = false }
  }, [page, revision, onSessionExpired])

  function reload() {
    setLoading(true)
    setError(null)
    setRevision((value) => value + 1)
  }

  function resetForm() {
    setEditing(null)
    setForm(EMPTY_FORM)
  }

  function edit(role: Role) {
    setEditing(role)
    setForm({ code: role.code, name: role.name, description: role.description ?? "", isActive: role.isActive })
    setError(null)
    setMessage(null)
    formRef.current?.scrollIntoView({ block: "start" })
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      const body = { name: form.name.trim(), description: form.description.trim() || null }
      const saved = editing
        ? await rbacMutation<Role>(`/roles/${editing.id}`, "PUT", { ...body, isActive: form.isActive })
        : await rbacMutation<Role>("/roles", "POST", { ...body, code: form.code.trim() })
      if (selected?.id === saved.id) setSelected(saved)
      setMessage(editing ? `Đã cập nhật vai trò ${saved.code}.` : `Đã tạo vai trò ${saved.code}.`)
      if (!editing) setPage(0)
      resetForm()
      reload()
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      setError(rbacErrorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  async function remove(role: Role) {
    if (!window.confirm(`Xóa vai trò ${role.code}? Vai trò sẽ ngừng hoạt động; lịch sử được giữ lại.`)) return
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await rbacMutation(`/roles/${role.id}`, "DELETE")
      if (editing?.id === role.id) resetForm()
      if (selected?.id === role.id) setSelected(null)
      setMessage(`Đã xóa vai trò ${role.code}.`)
      reload()
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      setError(rbacErrorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="rbac-stack">
      <RbacFeedback error={error} message={message} />
      <section className="dashboard-card" aria-labelledby="role-form-title">
        <h2 id="role-form-title">{editing ? `Sửa vai trò #${editing.id}` : "Thêm vai trò"}</h2>
        <form className="rbac-form" onSubmit={save} ref={formRef}>
          <fieldset disabled={disabled}>
            <div className="rbac-form-grid">
              <label>Mã vai trò
                <input value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })}
                  required maxLength={50} pattern="(?!ROLE_)[A-Z][A-Z0-9_]*" placeholder="VD: RBAC_MANAGER"
                  title="Chữ hoa, số, dấu gạch dưới; không bắt đầu bằng ROLE_" disabled={Boolean(editing)} />
              </label>
              <label>Tên vai trò
                <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required maxLength={150} />
              </label>
              <label className="rbac-full"><span id="role-description-label">Mô tả vai trò</span>
                <textarea aria-labelledby="role-description-label" rows={2} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} />
              </label>
            </div>
            {editing && (
              <label className="rbac-checkbox">
                <input type="checkbox" checked={form.isActive} disabled={editing.isSystem}
                  onChange={(event) => setForm({ ...form, isActive: event.target.checked })} />
                Đang hoạt động {editing.isSystem && "(vai trò hệ thống luôn hoạt động)"}
              </label>
            )}
            <div className="portal-actions">
              <button type="submit" className="rbac-primary">{busy ? "Đang xử lý..." : editing ? "Lưu vai trò" : "Tạo vai trò"}</button>
              {editing && <button type="button" className="secondary-button" onClick={resetForm}>Hủy sửa</button>}
            </div>
          </fieldset>
        </form>
      </section>

      {selected && <RolePermissions key={selected.id} role={selected} onClose={() => setSelected(null)} onSessionExpired={onSessionExpired} />}

      <section className="dashboard-card" aria-labelledby="role-list-title" aria-busy={loading}>
        <div className="card-heading">
          <h2 id="role-list-title">Danh sách vai trò</h2>
          <button type="button" className="secondary-button" onClick={reload} disabled={disabled}>Tải lại vai trò</button>
        </div>
        {loading && <p role="status">Đang tải vai trò...</p>}
        <div className="rbac-table-wrap">
          <table className="rbac-table">
            <thead><tr><th scope="col">Mã / ID</th><th scope="col">Tên / mô tả</th><th scope="col">Loại</th><th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead>
            <tbody>
              {data?.content.map((role) => (
                <tr key={role.id}>
                  <td><code>{role.code}</code><small>#{role.id}</small></td>
                  <td>{role.name}<small>{role.description}</small></td>
                  <td>{role.isSystem ? "Hệ thống" : "Tùy chỉnh"}</td>
                  <td>{role.isActive ? "Hoạt động" : "Tạm tắt"}</td>
                  <td><div className="portal-actions">
                    <button type="button" className="secondary-button" onClick={() => edit(role)} disabled={disabled || role.isSystem}
                      title={role.isSystem ? "System role do ứng dụng định nghĩa" : `Sửa ${role.code}`}>Sửa</button>
                    <button type="button" className="secondary-button" onClick={() => setSelected(role)} disabled={disabled}>
                      {role.isSystem ? "Xem quyền" : "Phân quyền"}
                    </button>
                    <button type="button" className="danger-button" onClick={() => remove(role)} disabled={disabled || role.isSystem}
                      title={role.isSystem ? "Không thể xóa vai trò hệ thống" : `Xóa ${role.code}`}>Xóa</button>
                  </div></td>
                </tr>
              ))}
              {!loading && data?.content.length === 0 && <tr><td colSpan={5}>Chưa có vai trò.</td></tr>}
            </tbody>
          </table>
        </div>
        <Pagination page={page} totalPages={data?.totalPages ?? 0} totalElements={data?.totalElements ?? 0} disabled={disabled}
          onChange={(next) => { setLoading(true); setError(null); setPage(next) }} />
      </section>
    </div>
  )
}
