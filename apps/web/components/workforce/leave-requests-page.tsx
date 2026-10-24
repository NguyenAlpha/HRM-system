"use client"

import { useEffect, useState, type ReactNode } from "react"
import { Ban, Check, FilePlus2, Loader2, RefreshCw, Send, X } from "lucide-react"

import { ErrorAlert, Pagination } from "@/components/common/list-controls"
import { ALL_OPTION, FilterSelect, ListToolbar, useDebouncedValue } from "@/components/common/list-filters"
import { RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import { hours } from "@/components/payroll/payroll-shared"
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import {
  EmptyRow,
  dateTime,
  monthRange,
  toDays,
  today,
  useWorkforceAction,
  workforceApi,
} from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import type { Page } from "@/lib/pagination"
import { getOrganizationUnitOptions } from "@/lib/reference"
import {
  DERIVED_SALARY_TREATMENT,
  LEAVE_SALARY_TREATMENTS,
  LEAVE_SALARY_TREATMENT_LABELS,
  LEAVE_STATUS_LABELS,
  LEAVE_TYPES,
  LEAVE_TYPE_LABELS,
  type LeaveBalance,
  type LeaveRequest,
  type LeaveSalaryTreatment,
  type LeaveStatus,
  type LeaveType,
  type WorkforceBody,
} from "@/lib/workforce"

const STATUS_TONE: Record<LeaveStatus, "default" | "secondary" | "outline" | "destructive"> = {
  DRAFT: "outline",
  PENDING: "secondary",
  APPROVED: "secondary",
  REJECTED: "destructive",
  CANCELLED: "outline",
}

/**
 * Part-of-day presets follow the default office shift (08:00–17:00, break 12:00–13:00). Other
 * shifts use the custom option. A half day is half the standard day either way.
 */
const PARTS = ["FULL_DAY", "MORNING", "AFTERNOON", "CUSTOM"] as const
type Part = (typeof PARTS)[number]
const PART_LABELS: Record<Part, string> = {
  FULL_DAY: "Cả ngày (08:00–17:00)",
  MORNING: "Buổi sáng (08:00–12:00)",
  AFTERNOON: "Buổi chiều (13:00–17:00)",
  CUSTOM: "Nhiều ngày hoặc giờ khác",
}
const PART_HOURS: Record<Exclude<Part, "CUSTOM">, [string, string]> = {
  FULL_DAY: ["08:00", "17:00"],
  MORNING: ["08:00", "12:00"],
  AFTERNOON: ["13:00", "17:00"],
}

const vietnamInstant = (date: string, hhmm: string) => new Date(`${date}T${hhmm}:00+07:00`).toISOString()

/** Drafts are never listed company-wide; the filter offers the statuses a reviewer can see. */
const LISTED_STATUSES = ["PENDING", "APPROVED", "REJECTED", "CANCELLED"] as const satisfies readonly LeaveStatus[]

type Tab = "mine" | "approvals" | "all"
type Review = { leave: LeaveRequest; approve: boolean }
type Run = (success: string, action: () => Promise<unknown>) => Promise<boolean>

export function LeaveRequestsPage({ openCreate = false, initialTab, initialRequestId }: {
  openCreate?: boolean; initialTab?: Tab; initialRequestId?: number
}) {
  const { session } = usePortalSession()
  const self = session.account.employee
  const permissions = new Set(session.account.permissions.map((permission) => permission.code))
  const canReadOwn = !!self && permissions.has("request.self.read")
  const canReadAll = permissions.has("request.read")
  const canManage = permissions.has("request.manage")
  const canCreate = !!self && permissions.has("request.self.create")

  const [revision, setRevision] = useState(0)
  const { busy, run } = useWorkforceAction(() => setRevision((value) => value + 1))
  const [pending, setPending] = useState<LeaveRequest[] | null>(null)
  const [selectedTab, setTab] = useState<Tab>(initialTab ?? (canReadOwn ? "mine" : "all"))
  const [creating, setCreating] = useState(canCreate && openCreate)
  const [review, setReview] = useState<Review | null>(null)
  const [highlight, setHighlight] = useState<{ id: number; leave: LeaveRequest | null; error: string | null } | null>(null)
  const highlighted = highlight && highlight.id === initialRequestId ? highlight.leave : null
  const highlightError = highlight && highlight.id === initialRequestId ? highlight.error : null

  useEffect(() => {
    if (!initialRequestId) return
    let active = true
    workforceApi<LeaveRequest>(`leave-requests/${initialRequestId}`)
      .then((leave) => { if (active) setHighlight({ id: initialRequestId, leave, error: null }) })
      .catch((caught: unknown) => {
        if (active) setHighlight({ id: initialRequestId, leave: null, error: apiErrorMessage(caught) })
      })
    return () => { active = false }
  }, [initialRequestId, revision])

  // Approval follows the reporting line, not a permission: only the approver fixed on submit reviews.
  useEffect(() => {
    let active = true
    workforceApi<Page<LeaveRequest>>("leave-requests/pending?size=100")
      .then((page) => { if (active) setPending(page.content) })
      .catch(() => { if (active) setPending([]) })
    return () => { active = false }
  }, [revision])

  const isApprover = (leave: LeaveRequest) => !!self && leave.approverEmployeeId === self.id
  const actions: LeaveActions = {
    busy,
    isApprover,
    selfId: self?.id,
    run,
    onReview: setReview,
  }
  const showApprovals = (pending?.length ?? 0) > 0
  // Once the last pending request is decided its tab disappears; fall back to the default tab.
  const tab = selectedTab === "approvals" && !showApprovals ? (canReadOwn ? "mine" : "all") : selectedTab

  return (
    <>
      <PageHeader
        title="Đơn nghỉ phép"
        description="Tạo đơn nghỉ, theo dõi phép năm và duyệt đơn của nhân viên mình quản lý."
        actions={canCreate && <Button type="button" onClick={() => setCreating(true)}><FilePlus2 /> Tạo đơn nghỉ</Button>}
      />

      <div className="mt-6 grid gap-4">
        {highlightError && <ErrorAlert error={highlightError} />}
        {highlighted && (
          <Card>
            <CardHeader><CardTitle>Đơn #{highlighted.id} từ thông báo</CardTitle></CardHeader>
            <CardContent><LeaveTable leaves={[highlighted]} showEmployee emptyText="Không tìm thấy đơn." actions={actions} /></CardContent>
          </Card>
        )}
        {!canReadOwn && !canReadAll && !showApprovals && <p role="alert">Tài khoản chưa có quyền xem đơn nghỉ.</p>}

        {(canReadOwn || canReadAll || showApprovals) && (
          <Tabs value={tab} onValueChange={(value) => setTab(value as Tab)}>
            <TabsList>
              {canReadOwn && <TabsTrigger value="mine">Đơn của tôi</TabsTrigger>}
              {showApprovals && (
                <TabsTrigger value="approvals">Chờ tôi duyệt <Badge variant="secondary" className="ml-1">{pending?.length}</Badge></TabsTrigger>
              )}
              {canReadAll && <TabsTrigger value="all">Tất cả đơn</TabsTrigger>}
            </TabsList>
          </Tabs>
        )}

        {tab === "mine" && canReadOwn && self && <MyLeaves employeeId={self.id} revision={revision} actions={actions} />}
        {tab === "approvals" && showApprovals && pending && (
          <Card>
            <CardHeader>
              <CardTitle>Đơn chờ bạn duyệt</CardTitle>
              <CardDescription>Đơn của nhân viên mà bạn là người duyệt (quản lý trực tiếp, hoặc cấp trên khi chưa có quản lý).</CardDescription>
            </CardHeader>
            <CardContent>
              <LeaveTable leaves={pending} showEmployee emptyText="Không có đơn chờ duyệt." actions={actions} />
            </CardContent>
          </Card>
        )}
        {tab === "all" && canReadAll && <AllLeaves revision={revision} actions={actions} />}
      </div>

      {creating && (
        <CreateLeaveDialog canManage={canManage} self={self ?? null} run={run} busy={busy}
          onClose={() => setCreating(false)} />
      )}
      {review && (
        <ReviewDialog review={review} busy={busy} onClose={() => setReview(null)}
          onSubmit={(comment) => run(review.approve ? "Đã duyệt đơn" : "Đã từ chối đơn", () => workforceApi(
            `leave-requests/${review.leave.id}/${review.approve ? "approve" : "reject"}`, "POST",
            { comment } satisfies WorkforceBody["ReviewBody"]))} />
      )}
    </>
  )
}

/** What a row menu may offer; each tab passes the same set. */
type LeaveActions = {
  busy: boolean
  isApprover: (leave: LeaveRequest) => boolean
  selfId: number | undefined
  run: Run
  onReview: (review: Review) => void
}

function MyLeaves({ employeeId, revision, actions }: { employeeId: number; revision: number; actions: LeaveActions }) {
  const [leaves, setLeaves] = useState<LeaveRequest[] | null>(null)
  const [balance, setBalance] = useState<LeaveBalance | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    Promise.all([
      workforceApi<Page<LeaveRequest>>(`leave-requests/employees/${employeeId}?size=100`),
      workforceApi<LeaveBalance>(`leave-entitlements/employees/${employeeId}`),
    ])
      .then(([page, loadedBalance]) => {
        if (!active) return
        setLeaves(page.content)
        setBalance(loadedBalance)
        setError(null)
      })
      .catch((caught: unknown) => { if (active) { setLeaves([]); setError(apiErrorMessage(caught)) } })
    return () => { active = false }
  }, [employeeId, revision])

  return (
    <>
      <ErrorAlert error={error} />
      {balance && <BalanceCard balance={balance} />}
      <Card>
        <CardHeader>
          <CardTitle>Đơn nghỉ của tôi</CardTitle>
          <CardDescription>Đơn nháp cần gửi duyệt; đơn nháp và đơn chờ duyệt có thể hủy.</CardDescription>
        </CardHeader>
        <CardContent>
          {leaves === null
            ? <Skeleton className="h-32 w-full" />
            : <LeaveTable leaves={leaves} emptyText="Bạn chưa có đơn nghỉ nào." actions={actions} />}
        </CardContent>
      </Card>
    </>
  )
}

type Filters = { keyword: string; status: string; leaveType: string; organizationUnitId: string; month: string }
const EMPTY_FILTERS: Filters = { keyword: "", status: "", leaveType: "", organizationUnitId: "", month: "" }

function AllLeaves({ revision, actions }: { revision: number; actions: LeaveActions }) {
  const { portal } = usePortalSession()
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS)
  const [page, setPage] = useState(0)
  const [reloads, setReloads] = useState(0)
  const [data, setData] = useState<{ key: string; page: Page<LeaveRequest> } | null>(null)
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

  const params = new URLSearchParams({ page: String(page), size: "10", sort: "startAt,desc" })
  if (keyword.trim()) params.set("keyword", keyword.trim())
  if (filters.status) params.set("status", filters.status)
  if (filters.leaveType) params.set("leaveType", filters.leaveType)
  if (filters.organizationUnitId) params.set("organizationUnitId", filters.organizationUnitId)
  if (filters.month) {
    const { first, last } = monthRange(filters.month)
    params.set("from", first)
    params.set("to", last)
  }
  const key = `${params}#${revision}#${reloads}`
  const loading = data?.key !== key || keyword !== filters.keyword

  useEffect(() => {
    let active = true
    workforceApi<Page<LeaveRequest>>(`leave-requests?${params}`)
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
        <CardTitle>Tất cả đơn nghỉ</CardTitle>
        <CardDescription>Đơn đã gửi của nhân viên trong phạm vi bạn được giao; đơn nháp chỉ người tạo thấy.</CardDescription>
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
          activeFilters={[filters.status, filters.leaveType, filters.organizationUnitId, filters.month].filter(Boolean).length}
          onReset={() => change(EMPTY_FILTERS)}
        >
          <FilterSelect id="leave-status-filter" label="Trạng thái" value={filters.status || ALL_OPTION}
            onChange={(value) => change({ status: value === ALL_OPTION ? "" : value })}
            options={LISTED_STATUSES.map((status) => ({ value: status, label: LEAVE_STATUS_LABELS[status] }))} />
          <FilterSelect id="leave-type-filter" label="Loại nghỉ" value={filters.leaveType || ALL_OPTION}
            onChange={(value) => change({ leaveType: value === ALL_OPTION ? "" : value })}
            options={LEAVE_TYPES.map((type) => ({ value: type, label: LEAVE_TYPE_LABELS[type] }))} />
          {unitOptions.length > 0 && (
            <FilterSelect id="leave-unit-filter" label="Đơn vị (phân công hiện tại)" value={filters.organizationUnitId || ALL_OPTION}
              onChange={(value) => change({ organizationUnitId: value === ALL_OPTION ? "" : value })} options={unitOptions} />
          )}
          <div className="grid gap-1.5">
            <Label htmlFor="leave-month-filter">Tháng nghỉ</Label>
            <Input id="leave-month-filter" type="month" value={filters.month} onChange={(event) => change({ month: event.target.value })} />
          </div>
        </ListToolbar>

        {loading && !result
          ? <Skeleton className="h-40 w-full" />
          : <LeaveTable leaves={result?.content ?? []} showEmployee emptyText="Không có đơn nào khớp bộ lọc." actions={actions} />}
        <Pagination page={page} totalPages={result?.totalPages ?? 0} totalElements={result?.totalElements ?? 0}
          disabled={loading} onChange={setPage} />
      </CardContent>
    </Card>
  )
}

function LeaveTable({ leaves, showEmployee = false, emptyText, actions }: {
  leaves: LeaveRequest[]
  showEmployee?: boolean
  emptyText: string
  actions: LeaveActions
}) {
  const { busy, isApprover, selfId, run, onReview } = actions
  const columns = showEmployee ? 8 : 7
  const menu = (leave: LeaveRequest): ReactNode[] => {
    const own = leave.employeeId === selfId
    const open = leave.status === "DRAFT" || leave.status === "PENDING"
    return [
      leave.status === "DRAFT" && own && (
        <RowActionItem key="submit" icon={Send} disabled={busy}
          onClick={() => void run("Đã gửi đơn", () => workforceApi(`leave-requests/${leave.id}/submit`, "POST"))}>
          Gửi duyệt
        </RowActionItem>
      ),
      isApprover(leave) && leave.status === "PENDING" && (
        <RowActionItem key="approve" icon={Check} disabled={busy} onClick={() => onReview({ leave, approve: true })}>Duyệt</RowActionItem>
      ),
      isApprover(leave) && leave.status === "PENDING" && (
        <RowActionItem key="reject" icon={X} destructive disabled={busy} onClick={() => onReview({ leave, approve: false })}>Từ chối</RowActionItem>
      ),
      open && own && (
        <RowActionItem key="cancel" icon={Ban} destructive disabled={busy}
          onClick={() => void run("Đã hủy đơn", () => workforceApi(`leave-requests/${leave.id}/cancel`, "POST"))}>
          Hủy đơn
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
            <TableHead>Loại</TableHead>
            <TableHead>Thời gian</TableHead>
            <TableHead className="text-right">Thời lượng</TableHead>
            <TableHead>Trạng thái</TableHead>
            <TableHead>Người duyệt</TableHead>
            <TableHead className="text-right">Thao tác</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {leaves.map((leave) => (
            <TableRow key={leave.id}>
              <TableCell>
                <span className="font-medium">#{leave.id}</span>
                <span className="block max-w-56 truncate text-xs text-muted-foreground" title={leave.reason}>{leave.reason}</span>
              </TableCell>
              {showEmployee && (
                <TableCell>
                  <span className="font-medium">{leave.employeeName}</span>
                  <span className="block text-xs text-muted-foreground">{leave.employeeCode}</span>
                </TableCell>
              )}
              <TableCell>
                {LEAVE_TYPE_LABELS[leave.leaveType]}
                <span className="block text-xs text-muted-foreground">{LEAVE_SALARY_TREATMENT_LABELS[leave.salaryTreatment]}</span>
              </TableCell>
              <TableCell className="text-sm">{dateTime(leave.startAt)} → {dateTime(leave.endAt)}</TableCell>
              <TableCell className="text-right tabular-nums">{hours(leave.requestedMinutes)}</TableCell>
              <TableCell>
                <Badge variant={STATUS_TONE[leave.status]}>{LEAVE_STATUS_LABELS[leave.status]}</Badge>
                {leave.reviewComment && <span className="block text-xs text-muted-foreground">{leave.reviewComment}</span>}
              </TableCell>
              <TableCell className="text-sm">{leave.approverEmployeeName ?? "—"}</TableCell>
              <RowActionsCell label={`đơn #${leave.id}`}>{menu(leave)}</RowActionsCell>
            </TableRow>
          ))}
          {leaves.length === 0 && <EmptyRow colSpan={columns}>{emptyText}</EmptyRow>}
        </TableBody>
      </Table>
    </div>
  )
}

function BalanceCard({ balance }: { balance: LeaveBalance }) {
  const day = balance.standardDayMinutes
  const stats = [
    ["Được cấp", balance.grantedMinutes],
    ["Đã dùng", balance.usedMinutes],
    ["Đang chờ duyệt", balance.pendingMinutes],
    ["Còn lại", balance.remainingMinutes],
  ] as const

  return (
    <Card>
      <CardHeader>
        <CardTitle>Phép năm {balance.year}</CardTitle>
        <CardDescription>
          12 ngày mỗi năm, vào làm giữa năm thì tính theo số tháng làm việc. Phép không dùng hết không chuyển sang năm sau.
          Một ngày = {day} phút theo ca làm việc.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <dl className="grid gap-3 sm:grid-cols-4">
          {stats.map(([label, minutes]) => (
            <div key={label}>
              <dt className="text-xs text-muted-foreground">{label}</dt>
              <dd className="mt-1 text-xl font-semibold tabular-nums">{toDays(minutes, day)} <span className="text-sm font-normal text-muted-foreground">ngày</span></dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
  )
}

type Applicant = { id: number }

/**
 * A new leave request always belongs to the signed-in employee. "Gửi duyệt" saves and submits at once.
 */
function CreateLeaveDialog({ canManage, self, busy, run, onClose }: {
  canManage: boolean
  self: Applicant | null
  busy: boolean
  run: Run
  onClose: () => void
}) {
  const [balance, setBalance] = useState<{ employeeId: number; value: LeaveBalance | null } | null>(null)
  const [leaveType, setLeaveType] = useState<LeaveType>("ANNUAL")
  const [treatment, setTreatment] = useState<LeaveSalaryTreatment>("EMPLOYER_PAID")
  const [part, setPart] = useState<Part>("FULL_DAY")
  const [date, setDate] = useState(today())
  const [customStart, setCustomStart] = useState("")
  const [customEnd, setCustomEnd] = useState("")
  const [reason, setReason] = useState("")

  const applicant = self

  useEffect(() => {
    if (!applicant) return
    let active = true
    workforceApi<LeaveBalance>(`leave-entitlements/employees/${applicant.id}`)
      .then((value) => { if (active) setBalance({ employeeId: applicant.id, value }) })
      .catch(() => { if (active) setBalance({ employeeId: applicant.id, value: null }) })
    return () => { active = false }
  }, [applicant?.id]) // eslint-disable-line react-hooks/exhaustive-deps

  const applicantBalance = applicant && balance?.employeeId === applicant.id ? balance.value : null
  const standardDayMinutes = applicantBalance?.standardDayMinutes ?? 480
  const presetMinutes = part === "FULL_DAY" ? standardDayMinutes : Math.round(standardDayMinutes / 2)
  const typeItems = { ...LEAVE_TYPE_LABELS }
  const treatmentItems = { ...LEAVE_SALARY_TREATMENT_LABELS }
  const partItems = { ...PART_LABELS }

  function save(submit: boolean) {
    if (!applicant) return
    const span = part === "CUSTOM"
      ? { startAt: new Date(customStart).toISOString(), endAt: new Date(customEnd).toISOString() }
      : { startAt: vietnamInstant(date, PART_HOURS[part][0]), endAt: vietnamInstant(date, PART_HOURS[part][1]) }
    const body = {
      employeeId: applicant.id, leaveType, ...(canManage ? { salaryTreatment: treatment } : {}), ...span, reason: reason.trim(),
    } satisfies WorkforceBody["CreateLeaveCommand"]
    void run(submit ? "Đã gửi đơn nghỉ" : "Đã lưu đơn nháp", async () => {
      const draft = await workforceApi<LeaveRequest>("leave-requests", "POST", body)
      if (submit) await workforceApi(`leave-requests/${draft.id}/submit`, "POST")
    }).then((ok) => { if (ok) onClose() })
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={(event) => { event.preventDefault(); save(true) }}>
          <DialogHeader>
            <DialogTitle>Tạo đơn nghỉ</DialogTitle>
            <DialogDescription>
              Nghỉ nửa ngày rồi đi làm nửa còn lại vẫn được tính đủ công. Đơn gửi duyệt đi tới quản lý trực tiếp.
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            {applicantBalance && (
              <p className="rounded-lg border bg-muted/40 px-3 py-2 text-sm sm:col-span-2">
                Bạn còn <strong>{toDays(applicantBalance.remainingMinutes, applicantBalance.standardDayMinutes)} ngày</strong> phép năm {applicantBalance.year}.
              </p>
            )}
            <div className="grid gap-1.5">
              <Label htmlFor="leave-type">Loại nghỉ</Label>
              <Select value={leaveType} items={typeItems} onValueChange={(value) => {
                const type = value as LeaveType
                setLeaveType(type)
                setTreatment(DERIVED_SALARY_TREATMENT[type])
              }}>
                <SelectTrigger id="leave-type" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>{LEAVE_TYPES.map((type) => <SelectItem key={type} value={type}>{LEAVE_TYPE_LABELS[type]}</SelectItem>)}</SelectContent>
              </Select>
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="leave-treatment">Chế độ lương</Label>
              {canManage ? (
                <Select value={treatment} items={treatmentItems} onValueChange={(value) => setTreatment(value as LeaveSalaryTreatment)}>
                  <SelectTrigger id="leave-treatment" className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {LEAVE_SALARY_TREATMENTS.map((value) => <SelectItem key={value} value={value}>{LEAVE_SALARY_TREATMENT_LABELS[value]}</SelectItem>)}
                  </SelectContent>
                </Select>
              ) : (
                <p id="leave-treatment" className="flex h-8 items-center rounded-lg border bg-muted px-2.5 text-sm">
                  {LEAVE_SALARY_TREATMENT_LABELS[DERIVED_SALARY_TREATMENT[leaveType]]}
                </p>
              )}
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="leave-part">Thời gian nghỉ</Label>
              <Select value={part} items={partItems} onValueChange={(value) => setPart(value as Part)}>
                <SelectTrigger id="leave-part" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>{PARTS.map((value) => <SelectItem key={value} value={value}>{PART_LABELS[value]}</SelectItem>)}</SelectContent>
              </Select>
            </div>
            {part === "CUSTOM" ? (
              // The API works the minutes out from the shifts, so there is nothing to type here.
              <p className="self-end text-xs text-muted-foreground">
                Số công nghỉ được tính theo ca làm việc trong khoảng đã chọn, không gồm giờ nghỉ trưa, Chủ nhật và ngày lễ.
              </p>
            ) : (
              <div className="grid gap-1.5">
                <Label htmlFor="leave-date">Ngày nghỉ</Label>
                <Input id="leave-date" required type="date" value={date} onChange={(event) => setDate(event.target.value)} />
                <p className="text-xs text-muted-foreground">{presetMinutes} phút công ({toDays(presetMinutes, standardDayMinutes)} ngày).</p>
              </div>
            )}
            {part === "CUSTOM" && <>
              <div className="grid gap-1.5">
                <Label htmlFor="leave-start">Bắt đầu</Label>
                <Input id="leave-start" required type="datetime-local" value={customStart} onChange={(event) => setCustomStart(event.target.value)} />
              </div>
              <div className="grid gap-1.5">
                <Label htmlFor="leave-end">Kết thúc</Label>
                <Input id="leave-end" required type="datetime-local" value={customEnd} onChange={(event) => setCustomEnd(event.target.value)} />
              </div>
            </>}
            <div className="grid gap-1.5 sm:col-span-2">
              <Label htmlFor="leave-reason">Lý do</Label>
              <Textarea id="leave-reason" required rows={2} value={reason} onChange={(event) => setReason(event.target.value)} />
            </div>
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Hủy</Button>
            <Button type="button" variant="outline" disabled={busy || !applicant || !reason.trim()} onClick={() => save(false)}>
              Lưu nháp
            </Button>
            <Button type="submit" disabled={busy || !applicant || !reason.trim()}>
              {busy && <Loader2 className="animate-spin" />} Gửi duyệt
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function ReviewDialog({ review, busy, onClose, onSubmit }: {
  review: Review
  busy: boolean
  onClose: () => void
  onSubmit: (comment: string | null) => Promise<boolean>
}) {
  const [comment, setComment] = useState("")
  const { leave, approve } = review
  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={(event) => {
          event.preventDefault()
          void onSubmit(comment.trim() || null).then((ok) => { if (ok) onClose() })
        }}>
          <DialogHeader>
            <DialogTitle>{approve ? "Duyệt" : "Từ chối"} đơn #{leave.id}</DialogTitle>
            <DialogDescription>
              {leave.employeeCode} · {leave.employeeName} · {LEAVE_TYPE_LABELS[leave.leaveType]} · {dateTime(leave.startAt)} → {dateTime(leave.endAt)}
              {" "}· {leave.requestedMinutes} phút. {leave.reason}
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-1.5">
            <Label htmlFor="review-comment">Ghi chú {approve ? "(không bắt buộc)" : "cho nhân viên"}</Label>
            <Textarea id="review-comment" rows={3} value={comment} onChange={(event) => setComment(event.target.value)} />
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose}>Đóng</Button>
            <Button type="submit" variant={approve ? "default" : "destructive"} disabled={busy}>
              {busy && <Loader2 className="animate-spin" />}{approve ? "Duyệt đơn" : "Từ chối đơn"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
