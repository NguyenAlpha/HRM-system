"use client"

import { type FormEvent, useEffect, useRef, useState } from "react"

import { Pagination, RbacFeedback } from "@/components/admin/rbac-controls"
import { isSessionExpired, PERMISSION_MODULES, rbacErrorMessage, rbacMutation, rbacRequest, type Page, type Permission, type PermissionModule } from "@/lib/rbac"

const EMPTY_FORM = { code: "", module: "EMPLOYEE" as PermissionModule, description: "", isActive: true }

export function PermissionManager({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [data, setData] = useState<Page<Permission> | null>(null)
  const [page, setPage] = useState(0)
  const [moduleFilter, setModuleFilter] = useState("")
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [editing, setEditing] = useState<Permission | null>(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const formRef = useRef<HTMLFormElement>(null)
  const disabled = busy || loading

  useEffect(() => {
    let active = true
    rbacRequest<Page<Permission>>(`/permissions?page=${page}&size=10&sort=id,desc${moduleFilter ? `&module=${moduleFilter}` : ""}`)
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
  }, [page, moduleFilter, revision, onSessionExpired])

  function reload() {
    setLoading(true)
    setError(null)
    setRevision((value) => value + 1)
  }

  function resetForm() {
    setEditing(null)
    setForm(EMPTY_FORM)
  }

  function edit(permission: Permission) {
    setEditing(permission)
    setForm({ code: permission.code, module: permission.module, description: permission.description, isActive: permission.isActive })
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
      const saved = editing
        ? await rbacMutation<Permission>(`/permissions/${editing.id}`, "PUT", { description: form.description.trim(), isActive: form.isActive })
        : await rbacMutation<Permission>("/permissions", "POST", { code: form.code.trim(), module: form.module, description: form.description.trim() })
      setMessage(editing ? `Đã cập nhật quyền ${saved.code}.` : `Đã tạo quyền ${saved.code}.`)
      if (!editing) { setPage(0); setModuleFilter("") }
      resetForm()
      reload()
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      setError(rbacErrorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  async function remove(permission: Permission) {
    if (!window.confirm(`Xóa quyền ${permission.code}? Quyền đang được sử dụng sẽ không thể xóa.`)) return
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      await rbacMutation(`/permissions/${permission.id}`, "DELETE")
      if (editing?.id === permission.id) resetForm()
      setMessage(`Đã xóa quyền ${permission.code}.`)
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
      <section className="dashboard-card" aria-labelledby="permission-form-title">
        <h2 id="permission-form-title">{editing ? `Sửa quyền #${editing.id}` : "Thêm quyền"}</h2>
        <form className="rbac-form" onSubmit={save} ref={formRef}>
          <fieldset disabled={disabled}>
            <div className="rbac-form-grid">
              <label>Mã quyền
                <input value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value })}
                  required maxLength={100} pattern={"[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"} placeholder="VD: employee.export"
                  title="Các phần chữ thường ngăn bởi dấu chấm, ví dụ employee.export" disabled={Boolean(editing)} />
              </label>
              <label><span id="permission-module-label">Module của quyền</span>
                <select aria-labelledby="permission-module-label" value={form.module} onChange={(event) => setForm({ ...form, module: event.target.value as PermissionModule })} disabled={Boolean(editing)}>
                  {PERMISSION_MODULES.map((module) => <option key={module} value={module}>{module}</option>)}
                </select>
              </label>
              <label className="rbac-full"><span id="permission-description-label">Mô tả quyền</span>
                <textarea aria-labelledby="permission-description-label" rows={2} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} required />
              </label>
            </div>
            {editing && <label className="rbac-checkbox">
              <input type="checkbox" checked={form.isActive} disabled={editing.code === "rbac.manage"}
                onChange={(event) => setForm({ ...form, isActive: event.target.checked })} />
              Đang hoạt động {editing.code === "rbac.manage" && "(quyền quản trị luôn hoạt động)"}
            </label>}
            <div className="portal-actions">
              <button type="submit" className="rbac-primary">{busy ? "Đang xử lý..." : editing ? "Lưu quyền" : "Tạo quyền"}</button>
              {editing && <button type="button" className="secondary-button" onClick={resetForm}>Hủy sửa</button>}
            </div>
          </fieldset>
        </form>
      </section>

      <section className="dashboard-card" aria-labelledby="permission-list-title" aria-busy={loading}>
        <div className="card-heading"><h2 id="permission-list-title">Danh sách quyền</h2>
          <button type="button" className="secondary-button" onClick={reload} disabled={disabled}>Tải lại quyền</button>
        </div>
        <label className="rbac-filter"><span id="module-filter-label">Lọc theo module</span>
          <select aria-labelledby="module-filter-label" value={moduleFilter} disabled={disabled} onChange={(event) => {
            setModuleFilter(event.target.value); setPage(0); setLoading(true); setError(null)
          }}>
            <option value="">Tất cả module</option>
            {PERMISSION_MODULES.map((module) => <option key={module} value={module}>{module}</option>)}
          </select>
        </label>
        {loading && <p role="status">Đang tải quyền...</p>}
        <div className="rbac-table-wrap">
          <table className="rbac-table">
            <thead><tr><th scope="col">Mã / ID</th><th scope="col">Module</th><th scope="col">Mô tả</th><th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead>
            <tbody>
              {data?.content.map((permission) => <tr key={permission.id}>
                <td><code>{permission.code}</code><small>#{permission.id}</small></td>
                <td>{permission.module}</td><td>{permission.description}</td>
                <td>{permission.isActive ? "Hoạt động" : "Tạm tắt"}</td>
                <td><div className="portal-actions">
                  <button type="button" className="secondary-button" onClick={() => edit(permission)} disabled={disabled}>Sửa</button>
                  <button type="button" className="danger-button" onClick={() => remove(permission)} disabled={disabled || permission.code === "rbac.manage"}
                    title={permission.code === "rbac.manage" ? "Không thể xóa quyền quản trị" : `Xóa ${permission.code}`}>Xóa</button>
                </div></td>
              </tr>)}
              {!loading && data?.content.length === 0 && <tr><td colSpan={5}>Chưa có quyền trong danh sách này.</td></tr>}
            </tbody>
          </table>
        </div>
        <Pagination page={page} totalPages={data?.totalPages ?? 0} totalElements={data?.totalElements ?? 0} disabled={disabled}
          onChange={(next) => { setLoading(true); setError(null); setPage(next) }} />
      </section>
    </div>
  )
}
