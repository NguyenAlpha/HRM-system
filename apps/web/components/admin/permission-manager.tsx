"use client"

import { useEffect, useState } from "react"

import { Pagination, RbacFeedback } from "@/components/admin/rbac-controls"
import {
  isSessionExpired,
  PERMISSION_MODULES,
  rbacErrorMessage,
  rbacRequest,
  type Page,
  type Permission,
} from "@/lib/rbac"

export function PermissionManager({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [data, setData] = useState<Page<Permission> | null>(null)
  const [page, setPage] = useState(0)
  const [moduleFilter, setModuleFilter] = useState("")
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

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

  return (
    <div className="rbac-stack">
      <RbacFeedback error={error} message={null} />
      <section className="dashboard-card" aria-labelledby="permission-list-title" aria-busy={loading}>
        <div className="card-heading">
          <div>
            <h2 id="permission-list-title">Danh mục quyền hệ thống</h2>
            <p>Permission được định nghĩa trong code; người dùng chỉ có thể xem và chọn quyền được phép ủy quyền.</p>
          </div>
          <button type="button" className="secondary-button" onClick={reload} disabled={loading}>Tải lại quyền</button>
        </div>
        <label className="rbac-filter"><span id="module-filter-label">Lọc theo module</span>
          <select aria-labelledby="module-filter-label" value={moduleFilter} disabled={loading} onChange={(event) => {
            setModuleFilter(event.target.value); setPage(0); setLoading(true); setError(null)
          }}>
            <option value="">Tất cả module</option>
            {PERMISSION_MODULES.map((module) => <option key={module} value={module}>{module}</option>)}
          </select>
        </label>
        {loading && <p role="status">Đang tải quyền...</p>}
        <div className="rbac-table-wrap">
          <table className="rbac-table">
            <thead><tr><th scope="col">Mã / ID</th><th scope="col">Tên</th><th scope="col">Module</th><th scope="col">Chính sách gán</th><th scope="col">Mô tả</th><th scope="col">Trạng thái</th></tr></thead>
            <tbody>
              {data?.content.map((permission) => <tr key={permission.id}>
                <td><code>{permission.code}</code><small>#{permission.id}</small></td>
                <td>{permission.name}</td>
                <td>{permission.module}</td>
                <td>{permission.assignmentPolicy === "DELEGABLE" ? "Có thể ủy quyền" : "Chỉ system role"}</td>
                <td>{permission.description}</td>
                <td>{permission.isActive ? "Hoạt động" : "Tạm tắt"}</td>
              </tr>)}
              {!loading && data?.content.length === 0 && <tr><td colSpan={6}>Chưa có quyền trong danh sách này.</td></tr>}
            </tbody>
          </table>
        </div>
        <Pagination page={page} totalPages={data?.totalPages ?? 0} totalElements={data?.totalElements ?? 0} disabled={loading}
          onChange={(next) => { setLoading(true); setError(null); setPage(next) }} />
      </section>
    </div>
  )
}
