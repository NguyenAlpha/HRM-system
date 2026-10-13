"use client"

import { useEffect, useState } from "react"
import { Ban, Check, Loader2, Send, X } from "lucide-react"

import { RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Textarea } from "@/components/ui/textarea"
import {
  EmptyRow,
  SectionError,
  dateTime,
  toDays,
  today,
  useWorkforceAction,
  workforceApi,
  type WorkforceContext,
} from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import type { Page } from "@/lib/pagination"
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
  APPROVED: "default",
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

type Review = { leave: LeaveRequest; approve: boolean }

export function LeaveSection({ employeeId, permissions, month }: WorkforceContext) {
  const canReadLeave = permissions.has("request.self.read") || permissions.has("request.read")
  const canCreate = permissions.has("request.self.create")
  const canApprove = permissions.has("request.approve")
  const canManage = permissions.has("request.manage")
  const year = Number(month.slice(0, 4))

  const [leaves, setLeaves] = useState<LeaveRequest[]>([])
  const [pending, setPending] = useState<LeaveRequest[]>([])
  const [balance, setBalance] = useState<LeaveBalance | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [review, setReview] = useState<Review | null>(null)
  const { busy, run } = useWorkforceAction(() => setRevision((value) => value + 1))

  useEffect(() => {
    let active = true
    const showEmployee = employeeId > 0 && canReadLeave
    Promise.all([
      showEmployee
        ? workforceApi<Page<LeaveRequest>>(`leave-requests/employees/${employeeId}?size=100`).then((page) => page.content)
        : Promise.resolve([]),
      showEmployee ? workforceApi<LeaveBalance>(`leave-entitlements/employees/${employeeId}?year=${year}`) : Promise.resolve(null),
      canApprove
        ? workforceApi<Page<LeaveRequest>>("leave-requests/pending?size=100").then((page) => page.content)
        : Promise.resolve([]),
    ])
      .then(([employeeLeaves, loadedBalance, pendingLeaves]) => {
        if (!active) return
        setLeaves(employeeLeaves)
        setBalance(loadedBalance)
        setPending(pendingLeaves)
        setError(null)
      })
      .catch((caught: unknown) => { if (active) setError(apiErrorMessage(caught)) })
    return () => { active = false }
  }, [employeeId, year, canReadLeave, canApprove, revision])

  const reviewActions = (leave: LeaveRequest) => [
    <RowActionItem key="approve" icon={Check} disabled={busy} onClick={() => setReview({ leave, approve: true })}>Duyệt</RowActionItem>,
    <RowActionItem key="reject" icon={X} destructive disabled={busy} onClick={() => setReview({ leave, approve: false })}>Từ chối</RowActionItem>,
  ]

  return (
    <div className="grid gap-6">
      <SectionError error={error} />

      {balance && <BalanceCard balance={balance} />}

      {canApprove && (
        <Card>
          <CardHeader>
            <CardTitle>Đơn chờ duyệt</CardTitle>
            <CardDescription>Các đơn trong phạm vi bạn được giao duyệt.</CardDescription>
          </CardHeader>
          <CardContent>
            <LeaveTable leaves={pending} showEmployee emptyText="Không có đơn chờ duyệt." actions={reviewActions} />
          </CardContent>
        </Card>
      )}

      {canCreate && employeeId > 0 && (
        <CreateLeaveCard employeeId={employeeId} canManage={canManage}
          standardDayMinutes={balance?.standardDayMinutes ?? 480} busy={busy} run={run} />
      )}

      {canReadLeave && (
        <Card>
          <CardHeader>
            <CardTitle>Đơn nghỉ của nhân viên #{employeeId || "—"}</CardTitle>
            <CardDescription>Đơn nháp cần gửi duyệt; đơn nháp và đơn chờ duyệt có thể hủy.</CardDescription>
          </CardHeader>
          <CardContent>
            <LeaveTable leaves={leaves} emptyText={employeeId ? "Chưa có đơn nghỉ." : "Nhập mã nhân viên để xem đơn nghỉ."}
              actions={(leave) => [
                leave.status === "DRAFT" && (
                  <RowActionItem key="submit" icon={Send} disabled={busy}
                    onClick={() => void run("Đã gửi đơn", () => workforceApi(`leave-requests/${leave.id}/submit`, "POST"))}>
                    Gửi duyệt
                  </RowActionItem>
                ),
                ...(canApprove && leave.status === "PENDING" ? reviewActions(leave) : []),
                (leave.status === "DRAFT" || leave.status === "PENDING") && (
                  <RowActionItem key="cancel" icon={Ban} destructive disabled={busy}
                    onClick={() => void run("Đã hủy đơn", () => workforceApi(`leave-requests/${leave.id}/cancel`, "POST"))}>
                    Hủy đơn
                  </RowActionItem>
                ),
              ]} />
          </CardContent>
        </Card>
      )}

      {review && (
        <ReviewDialog review={review} busy={busy} onClose={() => setReview(null)}
          onSubmit={(comment) => run(review.approve ? "Đã duyệt đơn" : "Đã từ chối đơn", () => workforceApi(
            `leave-requests/${review.leave.id}/${review.approve ? "approve" : "reject"}`, "POST",
            { comment } satisfies WorkforceBody["ReviewBody"]))} />
      )}
    </div>
  )
}

function LeaveTable({ leaves, showEmployee = false, emptyText, actions }: {
  leaves: LeaveRequest[]
  showEmployee?: boolean
  emptyText: string
  /** The row's menu items; false entries are skipped. */
  actions: (leave: LeaveRequest) => React.ReactNode[]
}) {
  const columns = showEmployee ? 7 : 6
  return (
    <div className="rounded-xl border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Đơn</TableHead>
            {showEmployee && <TableHead>Nhân viên</TableHead>}
            <TableHead>Loại</TableHead>
            <TableHead>Thời gian</TableHead>
            <TableHead className="text-right">Số phút</TableHead>
            <TableHead>Trạng thái</TableHead>
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
              {showEmployee && <TableCell>#{leave.employeeId}</TableCell>}
              <TableCell>
                {LEAVE_TYPE_LABELS[leave.leaveType]}
                <span className="block text-xs text-muted-foreground">{LEAVE_SALARY_TREATMENT_LABELS[leave.salaryTreatment]}</span>
              </TableCell>
              <TableCell className="text-sm">{dateTime(leave.startAt)} → {dateTime(leave.endAt)}</TableCell>
              <TableCell className="text-right">{leave.requestedMinutes}</TableCell>
              <TableCell>
                <Badge variant={STATUS_TONE[leave.status]}>{LEAVE_STATUS_LABELS[leave.status]}</Badge>
                {leave.reviewComment && <span className="block text-xs text-muted-foreground">{leave.reviewComment}</span>}
              </TableCell>
              <RowActionsCell label={`đơn #${leave.id}`}>{actions(leave)}</RowActionsCell>
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
            <div key={label} className="rounded-xl border px-4 py-3">
              <dt className="text-xs text-muted-foreground">{label}</dt>
              <dd className="text-2xl font-semibold">{toDays(minutes, day)} <span className="text-sm font-normal text-muted-foreground">ngày</span></dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
  )
}

function CreateLeaveCard({ employeeId, canManage, standardDayMinutes, busy, run }: {
  employeeId: number
  canManage: boolean
  standardDayMinutes: number
  busy: boolean
  run: (success: string, action: () => Promise<unknown>) => Promise<boolean>
}) {
  const [leaveType, setLeaveType] = useState<LeaveType>("ANNUAL")
  const [treatment, setTreatment] = useState<LeaveSalaryTreatment>("EMPLOYER_PAID")
  const [part, setPart] = useState<Part>("FULL_DAY")
  const [date, setDate] = useState(today())
  const [customStart, setCustomStart] = useState("")
  const [customEnd, setCustomEnd] = useState("")
  const [customMinutes, setCustomMinutes] = useState(String(standardDayMinutes))
  const [reason, setReason] = useState("")
  const typeItems = { ...LEAVE_TYPE_LABELS }
  const treatmentItems = { ...LEAVE_SALARY_TREATMENT_LABELS }
  const partItems = { ...PART_LABELS }
  const presetMinutes = part === "FULL_DAY" ? standardDayMinutes : Math.round(standardDayMinutes / 2)

  function submit(event: React.FormEvent) {
    event.preventDefault()
    const span = part === "CUSTOM"
      ? { startAt: new Date(customStart).toISOString(), endAt: new Date(customEnd).toISOString(), requestedMinutes: Number(customMinutes) }
      : { startAt: vietnamInstant(date, PART_HOURS[part][0]), endAt: vietnamInstant(date, PART_HOURS[part][1]), requestedMinutes: presetMinutes }
    void run("Đã tạo đơn nháp, nhớ gửi duyệt", () => workforceApi("leave-requests", "POST", {
      employeeId, leaveType, ...(canManage ? { salaryTreatment: treatment } : {}), ...span, reason: reason.trim(),
    } satisfies WorkforceBody["CreateLeaveCommand"])).then((ok) => { if (ok) setReason("") })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Tạo đơn nghỉ</CardTitle>
        <CardDescription>
          Đơn được lưu nháp, sau đó gửi duyệt ở danh sách bên dưới. Nghỉ nửa ngày rồi đi làm nửa còn lại vẫn được tính đủ công.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form className="grid gap-4 sm:grid-cols-2" onSubmit={submit}>
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
            <div className="grid gap-1.5">
              <Label htmlFor="leave-minutes">Số phút công nghỉ</Label>
              <Input id="leave-minutes" required type="number" min="1" value={customMinutes} onChange={(event) => setCustomMinutes(event.target.value)} />
            </div>
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
          <div className="sm:col-span-2">
            <Button type="submit" disabled={busy || !reason.trim()}>{busy && <Loader2 className="animate-spin" />} Lưu đơn nháp</Button>
          </div>
        </form>
      </CardContent>
    </Card>
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
              Nhân viên #{leave.employeeId} · {LEAVE_TYPE_LABELS[leave.leaveType]} · {dateTime(leave.startAt)} → {dateTime(leave.endAt)}
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
