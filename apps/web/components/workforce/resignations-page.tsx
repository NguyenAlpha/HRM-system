"use client"

import { useEffect, useState, type ReactNode } from "react"
import { Ban, Check, FileX2, Loader2, RefreshCw, X } from "lucide-react"

import { ErrorAlert, Pagination } from "@/components/common/list-controls"
import { ALL_OPTION, FilterSelect, ListToolbar, useDebouncedValue } from "@/components/common/list-filters"
import { RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import { formatDate } from "@/components/employee/employee-detail-page"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { EmptyRow, dateTime, today, useWorkforceAction, workforceApi } from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import type { Page } from "@/lib/pagination"
import { getOrganizationUnitOptions } from "@/lib/reference"
import {
  RESIGNATION_STATUS_LABELS,
  type ResignationRequest,
  type ResignationStatus,
  type WorkforceBody,
} from "@/lib/workforce"

const STATUS_TONE: Record<ResignationStatus, "default" | "secondary" | "outline" | "destructive"> = {
  PENDING: "secondary",
  APPROVED: "default",
  REJECTED: "destructive",
  CANCELLED: "outline",
}
const STATUSES = Object.keys(RESIGNATION_STATUS_LABELS) as ResignationStatus[]

/** Open: waiting for a decision, or approved with the last working day still ahead. */
const isOpen = (request: ResignationRequest) =>
  request.status === "PENDING" || (request.status === "APPROVED" && !request.completedAt)

type Tab = "mine" | "approvals" | "all"
type Review = { request: ResignationRequest; approve: boolean }
type Run = (success: string, action: () => Promise<unknown>) => Promise<boolean>
type Actions = { busy: boolean; canManage: boolean; selfId: number | undefined; run: Run; onReview: (review: Review) => void }

export function ResignationsPage({ initialTab }: { initialTab?: Tab }) {
  const { session } = usePortalSession()
  const self = session.account.employee
  const permissions = new Set(session.account.permissions.map((permission) => permission.code))
  const canReadOwn = !!self && permissions.has("request.self.read")
  const canReadAll = permissions.has("request.read")
  const canManage = permissions.has("request.manage")
  const canCreate = !!self && permissions.has("request.self.create")

  const [revision, setRevision] = useState(0)
  const { busy, run } = useWorkforceAction(() => setRevision((value) => value + 1))
  const [pending, setPending] = useState<ResignationRequest[] | null>(null)
  const [mine, setMine] = useState<ResignationRequest[] | null>(null)
  const [selectedTab, setTab] = useState<Tab>(initialTab ?? (canReadOwn ? "mine" : "all"))
  const [creating, setCreating] = useState(false)
  const [review, setReview] = useState<Review | null>(null)

  // Approval follows the reporting line, not a permission: only the approver fixed on submit decides.
  useEffect(() => {
    let active = true
    workforceApi<Page<ResignationRequest>>("resignation-requests/pending?size=100")
      .then((page) => { if (active) setPending(page.content) })
      .catch(() => { if (active) setPending([]) })
    return () => { active = false }
  }, [revision])

  const selfId = self?.id
  useEffect(() => {
    if (!canReadOwn || selfId === undefined) return
    let active = true
    workforceApi<Page<ResignationRequest>>(`resignation-requests/employees/${selfId}?size=100`)
      .then((page) => { if (active) setMine(page.content) })
      .catch(() => { if (active) setMine([]) })
    return () => { active = false }
  }, [canReadOwn, selfId, revision])

  const actions: Actions = { busy, canManage, selfId, run, onReview: setReview }
  const showApprovals = (pending?.length ?? 0) > 0
  // Once the last pending request is decided its tab disappears; fall back to the default tab.
  const tab = selectedTab === "approvals" && !showApprovals ? (canReadOwn ? "mine" : "all") : selectedTab
  const open = mine?.find(isOpen)

  return (
    <>
      <PageHeader
        title="Đơn nghỉ việc"
        description="Nộp đơn xin nghỉ việc và duyệt đơn của nhân viên mình quản lý. Hợp đồng kết thúc sau ngày làm việc cuối đã duyệt."
        actions={canCreate && (
          <Button type="button" disabled={mine === null || !!open} onClick={() => setCreating(true)}
            title={open ? "Bạn đang có một đơn nghỉ việc chưa hoàn tất" : undefined}>
            <FileX2 /> Nộp đơn nghỉ việc
          </Button>
        )}
      />

      <div className="mt-6 grid gap-4">
        {!canReadOwn && !canReadAll && !showApprovals && <p role="alert">Tài khoản chưa có quyền xem đơn nghỉ việc.</p>}

        {(canReadOwn || canReadAll || showApprovals) && (
          <Tabs value={tab} onValueChange={(value) => setTab(value as Tab)}>
            <TabsList>
              {canReadOwn && <TabsTrigger value="mine">Đơn của tôi</TabsTrigger>}
              {showApprovals && (
                <TabsTrigger value="approvals">Chờ tôi duyệt <Badge className="ml-1">{pending?.length}</Badge></TabsTrigger>
              )}
              {canReadAll && <TabsTrigger value="all">Tất cả đơn</TabsTrigger>}
            </TabsList>
          </Tabs>
        )}

        {tab === "mine" && canReadOwn && (
          <Card>
            <CardHeader>
              <CardTitle>Đơn nghỉ việc của tôi</CardTitle>
              <CardDescription>
                {open?.status === "APPROVED"
                  ? `Đơn đã được duyệt: bạn làm việc đến hết ngày ${formatDate(open.lastWorkingDate)}, sau đó tài khoản sẽ bị khóa.`
                  : "Đơn đang chờ duyệt có thể rút lại. Mỗi lần chỉ có một đơn chưa hoàn tất."}
              </CardDescription>
            </CardHeader>
            <CardContent>
              {mine === null
                ? <Skeleton className="h-32 w-full" />
                : <ResignationTable requests={mine} emptyText="Bạn chưa nộp đơn nghỉ việc nào." actions={actions} />}
            </CardContent>
          </Card>
        )}
        {tab === "approvals" && showApprovals && pending && (
          <Card>
            <CardHeader>
              <CardTitle>Đơn chờ bạn duyệt</CardTitle>
              <CardDescription>Đơn của nhân viên mà bạn là người duyệt (quản lý trực tiếp, hoặc cấp trên khi chưa có quản lý).</CardDescription>
            </CardHeader>
            <CardContent>
              <ResignationTable requests={pending} showEmployee emptyText="Không có đơn chờ duyệt." actions={actions} />
            </CardContent>
          </Card>
        )}
        {tab === "all" && canReadAll && <AllResignations revision={revision} actions={actions} />}
      </div>

      {creating && self && (
        <CreateDialog employeeId={self.id} busy={busy} run={run} onClose={() => setCreating(false)} />
      )}
      {review && <ReviewDialog review={review} busy={busy} run={run} onClose={() => setReview(null)} />}
    </>
  )
}

type Filters = { keyword: string; status: string; organizationUnitId: string }
const EMPTY_FILTERS: Filters = { keyword: "", status: "", organizationUnitId: "" }

function AllResignations({ revision, actions }: { revision: number; actions: Actions }) {
  const { portal } = usePortalSession()
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS)
  const [page, setPage] = useState(0)
  const [reloads, setReloads] = useState(0)
  const [data, setData] = useState<{ key: string; page: Page<ResignationRequest> } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [unitOptions, setUnitOptions] = useState<{ value: string; label: string }[]>([])
  const keyword = useDebouncedValue(filters.keyword)

  // The unit filter needs organization.read; without it the filter is not shown.
  useEffect(() => {
    let active = true
    getOrganizationUnitOptions(portal)
      .then((units) => { if (active) setUnitOptions(units.map((unit) => ({ value: String(unit.id), label: unit.name }))) })
      .catch(() => undefined)
    return () => { active = false }
  }, [portal])

  const params = new URLSearchParams({ page: String(page), size: "10", sort: "createdAt,desc" })
  if (keyword.trim()) params.set("keyword", keyword.trim())
  if (filters.status) params.set("status", filters.status)
  if (filters.organizationUnitId) params.set("organizationUnitId", filters.organizationUnitId)
  const key = `${params}#${revision}#${reloads}`
  const loading = data?.key !== key || keyword !== filters.keyword

  useEffect(() => {
    let active = true
    workforceApi<Page<ResignationRequest>>(`resignation-requests?${params}`)
      .then((result) => { if (active) { setData({ key, page: result }); setError(null) } })
      .catch((caught: unknown) => {
        if (!active) return
        setError(apiErrorMessage(caught))
        setData({ key, page: { content: [], number: 0, totalElements: 0, totalPages: 0 } })
      })
    return () => { active = false }
    // `key` is built from `params` and the reload counters.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  function change(next: Partial<Filters>) {
    setPage(0)
    setFilters((current) => ({ ...current, ...next }))
  }

  const result = data?.page

  return (
    <Card>
      <CardHeader>
        <CardTitle>Tất cả đơn nghỉ việc</CardTitle>
        <CardDescription>Đơn của nhân viên trong phạm vi bạn được giao. HR theo dõi để chuẩn bị bàn giao và tuyển thay thế.</CardDescription>
        <CardAction>
          <Button type="button" variant="outline" size="sm" disabled={loading} onClick={() => setReloads((value) => value + 1)}>
            <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="grid gap-4">
        <ErrorAlert error={error} />
        <ListToolbar
          keyword={filters.keyword}
          onKeywordChange={(value) => change({ keyword: value })}
          placeholder="Tìm theo họ tên hoặc mã nhân viên"
          activeFilters={[filters.status, filters.organizationUnitId].filter(Boolean).length}
          onReset={() => change(EMPTY_FILTERS)}
        >
          <FilterSelect id="resignation-status-filter" label="Trạng thái" value={filters.status || ALL_OPTION}
            onChange={(value) => change({ status: value === ALL_OPTION ? "" : value })}
            options={STATUSES.map((status) => ({ value: status, label: RESIGNATION_STATUS_LABELS[status] }))} />
          {unitOptions.length > 0 && (
            <FilterSelect id="resignation-unit-filter" label="Đơn vị (phân công hiện tại)" value={filters.organizationUnitId || ALL_OPTION}
              onChange={(value) => change({ organizationUnitId: value === ALL_OPTION ? "" : value })} options={unitOptions} />
          )}
        </ListToolbar>

        {loading && !result
          ? <Skeleton className="h-40 w-full" />
          : <ResignationTable requests={result?.content ?? []} showEmployee emptyText="Không có đơn nào khớp bộ lọc." actions={actions} />}
        <Pagination page={page} totalPages={result?.totalPages ?? 0} totalElements={result?.totalElements ?? 0}
          disabled={loading} onChange={setPage} />
      </CardContent>
    </Card>
  )
}

function ResignationTable({ requests, showEmployee = false, emptyText, actions }: {
  requests: ResignationRequest[]
  showEmployee?: boolean
  emptyText: string
  actions: Actions
}) {
  const { busy, canManage, selfId, run, onReview } = actions
  const columns = showEmployee ? 7 : 6
  const menu = (request: ResignationRequest): ReactNode[] => {
    const pending = request.status === "PENDING"
    const decides = pending && request.approverEmployeeId === selfId
    return [
      decides && <RowActionItem key="approve" icon={Check} disabled={busy} onClick={() => onReview({ request, approve: true })}>Duyệt</RowActionItem>,
      decides && <RowActionItem key="reject" icon={X} destructive disabled={busy} onClick={() => onReview({ request, approve: false })}>Từ chối</RowActionItem>,
      pending && (request.employeeId === selfId || canManage) && (
        <RowActionItem key="cancel" icon={Ban} destructive disabled={busy}
          onClick={() => void run("Đã rút đơn", () => workforceApi(`resignation-requests/${request.id}/cancel`, "POST"))}>
          Rút đơn
        </RowActionItem>
      ),
    ]
  }

  return (
    <div className="rounded-xl border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Đơn</TableHead>
            {showEmployee && <TableHead>Nhân viên</TableHead>}
            <TableHead>Ngày làm việc cuối</TableHead>
            <TableHead>Trạng thái</TableHead>
            <TableHead>Người duyệt</TableHead>
            <TableHead>Ngày nộp</TableHead>
            <TableHead className="text-right">Thao tác</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {requests.map((request) => (
            <TableRow key={request.id}>
              <TableCell>
                <span className="font-medium">#{request.id}</span>
                <span className="block max-w-64 truncate text-xs text-muted-foreground" title={request.reason}>{request.reason}</span>
              </TableCell>
              {showEmployee && (
                <TableCell>
                  <span className="font-medium">{request.employeeName}</span>
                  <span className="block text-xs text-muted-foreground">{request.employeeCode}</span>
                </TableCell>
              )}
              <TableCell className="tabular-nums">{formatDate(request.lastWorkingDate)}</TableCell>
              <TableCell>
                <Badge variant={STATUS_TONE[request.status]}>
                  {request.completedAt ? "Đã nghỉ việc" : RESIGNATION_STATUS_LABELS[request.status]}
                </Badge>
                {request.reviewComment && <span className="block max-w-56 text-xs text-muted-foreground">{request.reviewComment}</span>}
              </TableCell>
              <TableCell className="text-sm">{request.approverEmployeeName}</TableCell>
              <TableCell className="text-sm">{dateTime(request.createdAt)}</TableCell>
              <RowActionsCell label={`đơn #${request.id}`}>{menu(request)}</RowActionsCell>
            </TableRow>
          ))}
          {requests.length === 0 && <EmptyRow colSpan={columns}>{emptyText}</EmptyRow>}
        </TableBody>
      </Table>
    </div>
  )
}

function CreateDialog({ employeeId, busy, run, onClose }: { employeeId: number; busy: boolean; run: Run; onClose: () => void }) {
  const [lastWorkingDate, setLastWorkingDate] = useState("")
  const [reason, setReason] = useState("")

  function submit() {
    const body = {
      employeeId, requestedLastWorkingDate: lastWorkingDate, reason: reason.trim(),
    } satisfies WorkforceBody["CreateResignationRequestRequest"]
    void run("Đã nộp đơn nghỉ việc", () => workforceApi("resignation-requests", "POST", body))
      .then((ok) => { if (ok) onClose() })
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={(event) => { event.preventDefault(); submit() }}>
          <DialogHeader>
            <DialogTitle>Nộp đơn nghỉ việc</DialogTitle>
            <DialogDescription>
              Đơn đi tới quản lý trực tiếp, người có thể đổi ngày làm việc cuối khi duyệt. Theo Bộ luật Lao động 2019
              (Điều 35), cần báo trước ít nhất 45 ngày với hợp đồng không xác định thời hạn, 30 ngày với hợp đồng
              12–36 tháng, 3 ngày với hợp đồng dưới 12 tháng.
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="resign-date">Ngày làm việc cuối</Label>
              <Input id="resign-date" required type="date" min={today()} value={lastWorkingDate}
                onChange={(event) => setLastWorkingDate(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="resign-reason">Lý do</Label>
              <Textarea id="resign-reason" required rows={3} value={reason} onChange={(event) => setReason(event.target.value)} />
            </div>
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Hủy</Button>
            <Button type="submit" disabled={busy || !lastWorkingDate || !reason.trim()}>
              {busy && <Loader2 className="animate-spin" />} Nộp đơn
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/** Approving may move the last working day; a day already past must be moved. Rejecting needs a reason. */
function ReviewDialog({ review, busy, run, onClose }: { review: Review; busy: boolean; run: Run; onClose: () => void }) {
  const { request, approve } = review
  const asked = request.lastWorkingDate
  const [lastWorkingDate, setLastWorkingDate] = useState(asked < today() ? "" : asked)
  const [comment, setComment] = useState("")

  function submit() {
    const action = approve
      ? () => workforceApi(`resignation-requests/${request.id}/approve`, "POST", {
        comment: comment.trim() || null,
        lastWorkingDate: lastWorkingDate === asked ? null : lastWorkingDate,
      } satisfies WorkforceBody["ApproveResignationBody"])
      : () => workforceApi(`resignation-requests/${request.id}/reject`, "POST",
        { comment: comment.trim() } satisfies WorkforceBody["RejectResignationBody"])
    void run(approve ? "Đã duyệt đơn nghỉ việc" : "Đã từ chối đơn nghỉ việc", action).then((ok) => { if (ok) onClose() })
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={(event) => { event.preventDefault(); submit() }}>
          <DialogHeader>
            <DialogTitle>{approve ? "Duyệt" : "Từ chối"} đơn nghỉ việc #{request.id}</DialogTitle>
            <DialogDescription>
              {request.employeeCode} · {request.employeeName} · xin nghỉ sau ngày {formatDate(asked)}. {request.reason}
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-4">
            {approve && (
              <div className="grid gap-1.5">
                <Label htmlFor="review-date">Ngày làm việc cuối</Label>
                <Input id="review-date" required type="date" min={today()} value={lastWorkingDate}
                  onChange={(event) => setLastWorkingDate(event.target.value)} />
                <p className="text-xs text-muted-foreground">
                  {asked < today()
                    ? "Ngày nhân viên xin đã qua, hãy chọn lại ngày làm việc cuối."
                    : "Có thể đổi để đủ thời gian báo trước và bàn giao. Hợp đồng kết thúc vào ngày hôm sau."}
                </p>
              </div>
            )}
            <div className="grid gap-1.5">
              <Label htmlFor="review-comment">{approve ? "Ghi chú (không bắt buộc)" : "Lý do từ chối"}</Label>
              <Textarea id="review-comment" rows={3} required={!approve} value={comment}
                onChange={(event) => setComment(event.target.value)} />
            </div>
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Đóng</Button>
            <Button type="submit" variant={approve ? "default" : "destructive"}
              disabled={busy || (approve ? !lastWorkingDate : !comment.trim())}>
              {busy && <Loader2 className="animate-spin" />}{approve ? "Duyệt đơn" : "Từ chối đơn"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
