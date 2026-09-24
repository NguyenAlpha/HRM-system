export function RbacFeedback({ error, message }: { error: string | null; message: string | null }) {
  return (
    <>
      {error && <div className="dashboard-alert error" role="alert">{error}</div>}
      {message && <div className="dashboard-alert success" role="status">{message}</div>}
    </>
  )
}

export function Pagination({ page, totalPages, totalElements, disabled, onChange }: {
  page: number
  totalPages: number
  totalElements: number
  disabled: boolean
  onChange: (page: number) => void
}) {
  return (
    <div className="rbac-pagination">
      <span>{totalElements} bản ghi · Trang {page + 1}/{Math.max(1, totalPages)}</span>
      <div className="portal-actions">
        <button type="button" className="secondary-button" disabled={disabled || page === 0} onClick={() => onChange(page - 1)}>Trước</button>
        <button type="button" className="secondary-button" disabled={disabled || page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Sau</button>
      </div>
    </div>
  )
}
