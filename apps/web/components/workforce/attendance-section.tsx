"use client"

import { useEffect, useState } from "react"
import { CalendarPlus, Clock, Loader2, LogIn, LogOut, Pencil, Trash2, Zap } from "lucide-react"

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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Textarea } from "@/components/ui/textarea"
import { apiErrorMessage } from "@/lib/api-helpers"
import type { Page } from "@/lib/pagination"
import {
  ATTENDANCE_STATUS_LABELS,
  type AttendanceRecord,
  type AttendanceStatus,
  type CompanyHoliday,
  type WorkforceBody,
} from "@/lib/workforce"
import {
  EmptyRow,
  SectionError,
  monthRange,
  time,
  today,
  useWorkforceAction,
  workforceApi,
  type WorkforceContext,
} from "@/components/workforce/workforce-shared"

const STATUS_TONE: Record<AttendanceStatus, "default" | "secondary" | "outline" | "destructive"> = {
  PRESENT: "default",
  PAID_LEAVE: "secondary",
  UNPAID_LEAVE: "outline",
  MATERNITY_LEAVE: "secondary",
  SICK_LEAVE: "secondary",
  UNAUTHORIZED_ABSENCE: "destructive",
  HOLIDAY: "outline",
  MISSING_PUNCH: "destructive",
}

/** HR may only resolve a day to one of these; leave statuses come from approved requests. */
const ADJUSTABLE_STATUSES = ["PRESENT", "UNAUTHORIZED_ABSENCE"] as const satisfies readonly AttendanceStatus[]

export function AttendanceSection({ employeeId, permissions, month }: WorkforceContext) {
  const canRecordSelf = permissions.has("attendance.self.record")
  const canReadAttendance = permissions.has("attendance.self.read") || permissions.has("attendance.read")
  const canManage = permissions.has("attendance.manage")
  const canApproveOvertime = permissions.has("attendance.overtime.approve")
  const canReadHolidays = permissions.has("organization.read")
  const canManageHolidays = permissions.has("organization.manage")

  const [rows, setRows] = useState<AttendanceRecord[]>([])
  const [holidays, setHolidays] = useState<CompanyHoliday[]>([])
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [adjusting, setAdjusting] = useState<AttendanceRecord | null>(null)
  const [approving, setApproving] = useState<AttendanceRecord | null>(null)
  const { busy, run } = useWorkforceAction(() => setRevision((value) => value + 1))
  const { first, last } = monthRange(month)

  useEffect(() => {
    let active = true
    Promise.all([
      employeeId > 0 && canReadAttendance
        ? workforceApi<Page<AttendanceRecord>>(`attendance/employees/${employeeId}?from=${first}&to=${last}&size=100`)
          .then((page) => page.content)
        : Promise.resolve([]),
      canReadHolidays ? workforceApi<CompanyHoliday[]>(`company-holidays?from=${first}&to=${last}`) : Promise.resolve([]),
    ])
      .then(([records, loadedHolidays]) => {
        if (!active) return
        setRows(records)
        setHolidays(loadedHolidays)
        setError(null)
      })
      .catch((caught: unknown) => { if (active) setError(apiErrorMessage(caught)) })
    return () => { active = false }
  }, [employeeId, first, last, canReadAttendance, canReadHolidays, revision])

  const openRecord = rows.find((row) => row.checkInAt && !row.checkOutAt)

  return (
    <div className="grid gap-6">
      <Card>
        <CardHeader>
          <CardTitle>Bảng công tháng {month.slice(5, 7)}/{month.slice(0, 4)}</CardTitle>
          <CardDescription>
            Phút công tính lương không gồm giờ nghỉ giữa ca; ngày nghỉ phép được duyệt tự gắn vào bảng công.
          </CardDescription>
          <CardAction className="flex flex-wrap gap-2">
            {canRecordSelf && <>
              <Button type="button" size="sm" disabled={busy || !employeeId}
                onClick={() => void run("Đã ghi nhận vào ca", () => workforceApi("attendance/check-in", "POST",
                  { employeeId } satisfies WorkforceBody["CheckInRequest"]))}>
                <LogIn /> Vào ca
              </Button>
              <Button type="button" size="sm" variant="outline" disabled={busy || !employeeId}
                onClick={() => void run("Đã ghi nhận ra ca", () => workforceApi("attendance/check-out", "POST",
                  { employeeId, workDate: openRecord?.workDate ?? today() } satisfies WorkforceBody["CheckOutRequest"]))}>
                <LogOut /> Ra ca
              </Button>
            </>}
            {canManage && (
              <Button type="button" size="sm" variant="outline" disabled={busy}
                onClick={() => void run("Đã chuẩn bị bảng công",
                  () => workforceApi(`attendance/prepare?year=${month.slice(0, 4)}&month=${Number(month.slice(5, 7))}`, "POST"))}>
                <CalendarPlus /> Chuẩn bị bảng công tháng
              </Button>
            )}
          </CardAction>
        </CardHeader>
        <CardContent className="grid gap-3">
          <SectionError error={error} />
          <div className="rounded-xl border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Ngày</TableHead>
                  <TableHead>Ca dự kiến</TableHead>
                  <TableHead>Vào / Ra</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead className="text-right">Muộn / Sớm</TableHead>
                  <TableHead className="text-right">Công tính lương</TableHead>
                  <TableHead className="text-right">Tăng ca</TableHead>
                  <TableHead className="text-right">Thao tác</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell className="font-medium">{row.workDate}</TableCell>
                    <TableCell className="text-muted-foreground">{time(row.scheduledStartAt)}–{time(row.scheduledEndAt)}</TableCell>
                    <TableCell>{time(row.checkInAt)} / {time(row.checkOutAt)}</TableCell>
                    <TableCell><Badge variant={STATUS_TONE[row.status]}>{ATTENDANCE_STATUS_LABELS[row.status]}</Badge></TableCell>
                    <TableCell className="text-right">{row.lateMinutes} / {row.earlyLeaveMinutes} phút</TableCell>
                    <TableCell className="text-right">{row.payableMinutes}/{row.scheduledMinutes} phút</TableCell>
                    <TableCell className="text-right">
                      {row.overtimeMinutes > 0 ? `${row.overtimeMinutes} phút × ${row.overtimeMultiplier}` : "—"}
                      {row.overtimeMinutes > 0 && row.overtimeTaxExempt && <span className="block text-xs text-muted-foreground">miễn thuế</span>}
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1">
                        {canRecordSelf && row.checkInAt && !row.checkOutAt && (
                          <Button type="button" variant="ghost" size="icon-sm" aria-label={`Ra ca ngày ${row.workDate}`} disabled={busy}
                            onClick={() => void run("Đã ghi nhận ra ca", () => workforceApi("attendance/check-out", "POST",
                              { employeeId, workDate: row.workDate } satisfies WorkforceBody["CheckOutRequest"]))}>
                            <LogOut />
                          </Button>
                        )}
                        {canApproveOvertime && row.checkOutAt && (
                          <Button type="button" variant="ghost" size="icon-sm" aria-label={`Duyệt tăng ca ngày ${row.workDate}`}
                            disabled={busy} onClick={() => setApproving(row)}>
                            <Zap />
                          </Button>
                        )}
                        {canManage && (
                          <Button type="button" variant="ghost" size="icon-sm" aria-label={`Điều chỉnh công ngày ${row.workDate}`}
                            disabled={busy} onClick={() => setAdjusting(row)}>
                            <Pencil />
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
                {rows.length === 0 && (
                  <EmptyRow colSpan={8}>{employeeId ? "Chưa có dữ liệu công trong tháng." : "Nhập mã nhân viên để xem bảng công."}</EmptyRow>
                )}
              </TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>

      {canReadHolidays && (
        <HolidayCard holidays={holidays} canManage={canManageHolidays} busy={busy} run={run} />
      )}

      {adjusting && (
        <AdjustAttendanceDialog record={adjusting} busy={busy} onClose={() => setAdjusting(null)}
          onSubmit={(body) => run("Đã điều chỉnh công", () => workforceApi(`attendance/${adjusting.id}`, "PUT", body))} />
      )}
      {approving && (
        <OvertimeDialog record={approving} holidays={holidays} busy={busy} onClose={() => setApproving(null)}
          onSubmit={(body) => run("Đã duyệt tăng ca", () => workforceApi(`attendance/${approving.id}/overtime-approval`, "POST", body))} />
      )}
    </div>
  )
}

function HolidayCard({ holidays, canManage, busy, run }: {
  holidays: CompanyHoliday[]
  canManage: boolean
  busy: boolean
  run: (success: string, action: () => Promise<unknown>) => Promise<boolean>
}) {
  const [date, setDate] = useState("")
  const [name, setName] = useState("")

  return (
    <Card>
      <CardHeader>
        <CardTitle>Ngày lễ công ty trong tháng</CardTitle>
        <CardDescription>Ngày lễ không tính là ngày làm việc; thêm hoặc xóa ngày lễ đưa kỳ lương đã tính về nháp.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        {holidays.length === 0 && <p className="text-sm text-muted-foreground">Không có ngày lễ trong tháng.</p>}
        <ul className="grid gap-2">
          {holidays.map((holiday) => (
            <li key={holiday.id} className="flex items-center justify-between rounded-lg border px-3 py-2 text-sm">
              <span><span className="font-medium">{holiday.holidayDate}</span> · {holiday.name}</span>
              {canManage && (
                <Button type="button" variant="ghost" size="icon-sm" aria-label={`Xóa ngày lễ ${holiday.name}`} disabled={busy}
                  className="text-muted-foreground hover:text-destructive"
                  onClick={() => void run("Đã xóa ngày lễ", () => workforceApi(`company-holidays/${holiday.id}`, "DELETE"))}>
                  <Trash2 />
                </Button>
              )}
            </li>
          ))}
        </ul>
        {canManage && (
          <form className="flex flex-wrap items-end gap-2" onSubmit={(event) => {
            event.preventDefault()
            void run("Đã thêm ngày lễ", () => workforceApi("company-holidays", "POST",
              { date, name: name.trim() } satisfies WorkforceBody["CompanyHolidayRequest"]))
              .then((ok) => { if (ok) { setDate(""); setName("") } })
          }}>
            <div className="grid gap-1.5">
              <Label htmlFor="holiday-date">Ngày</Label>
              <Input id="holiday-date" type="date" required value={date} onChange={(event) => setDate(event.target.value)} />
            </div>
            <div className="grid min-w-56 flex-1 gap-1.5">
              <Label htmlFor="holiday-name">Tên ngày lễ</Label>
              <Input id="holiday-name" required maxLength={150} value={name} onChange={(event) => setName(event.target.value)} />
            </div>
            <Button type="submit" disabled={busy || !date || !name.trim()}><CalendarPlus /> Thêm ngày lễ</Button>
          </form>
        )}
      </CardContent>
    </Card>
  )
}

function AdjustAttendanceDialog({ record, busy, onClose, onSubmit }: {
  record: AttendanceRecord
  busy: boolean
  onClose: () => void
  onSubmit: (body: WorkforceBody["AdjustAttendanceRequest"]) => Promise<boolean>
}) {
  const initialStatus = record.status === "MISSING_PUNCH" ? "UNAUTHORIZED_ABSENCE" : record.status
  const [status, setStatus] = useState<AttendanceStatus>(initialStatus)
  const present = status === "PRESENT"
  const [worked, setWorked] = useState(String(record.workedMinutes))
  const [payable, setPayable] = useState(String(record.payableMinutes))
  const [late, setLate] = useState(String(record.lateMinutes))
  const [early, setEarly] = useState(String(record.earlyLeaveMinutes))
  const [note, setNote] = useState("")
  const [error, setError] = useState<string | null>(null)
  const statusItems = Object.fromEntries(ADJUSTABLE_STATUSES.map((value) => [value, ATTENDANCE_STATUS_LABELS[value]]))

  function submit(event: React.FormEvent) {
    event.preventDefault()
    const values = present
      ? { workedMinutes: Number(worked), payableMinutes: Number(payable), lateMinutes: Number(late), earlyLeaveMinutes: Number(early) }
      : { workedMinutes: 0, payableMinutes: 0, lateMinutes: 0, earlyLeaveMinutes: 0 }
    if (Object.values(values).some((value) => !Number.isInteger(value) || value < 0)) {
      setError("Số phút phải là số nguyên không âm"); return
    }
    if (values.payableMinutes > record.scheduledMinutes) {
      setError(`Phút tính lương không vượt ${record.scheduledMinutes} phút công chuẩn`); return
    }
    if (present && values.workedMinutes === 0) {
      setError("Có mặt thì phút thực làm phải lớn hơn 0"); return
    }
    void onSubmit({ ...values, status, note: note.trim() }).then((ok) => { if (ok) onClose() })
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={submit}>
          <DialogHeader>
            <DialogTitle>Điều chỉnh công ngày {record.workDate}</DialogTitle>
            <DialogDescription>
              Hiện tại: {ATTENDANCE_STATUS_LABELS[record.status]}, {record.payableMinutes}/{record.scheduledMinutes} phút.
              Nghỉ có lương phải đi qua quy trình duyệt đơn, không nhập ở đây.
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <div className="grid gap-1.5 sm:col-span-2">
              <Label htmlFor="adjust-status">Tình trạng sau đối soát</Label>
              <Select value={status} items={statusItems} onValueChange={(value) => setStatus(value as AttendanceStatus)}>
                <SelectTrigger id="adjust-status" className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {ADJUSTABLE_STATUSES.map((value) => <SelectItem key={value} value={value}>{ATTENDANCE_STATUS_LABELS[value]}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>
            {present && <>
              <MinutesField id="adjust-worked" label="Phút thực làm" value={worked} onChange={setWorked} />
              <MinutesField id="adjust-payable" label={`Phút tính lương (tối đa ${record.scheduledMinutes})`} value={payable} onChange={setPayable} />
              <MinutesField id="adjust-late" label="Phút đi muộn" value={late} onChange={setLate} />
              <MinutesField id="adjust-early" label="Phút về sớm" value={early} onChange={setEarly} />
            </>}
            <div className="grid gap-1.5 sm:col-span-2">
              <Label htmlFor="adjust-note">Lý do và căn cứ đối soát</Label>
              <Textarea id="adjust-note" required rows={3} value={note} onChange={(event) => setNote(event.target.value)} />
            </div>
            {error && <p role="alert" className="text-sm text-destructive sm:col-span-2">{error}</p>}
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose}>Hủy</Button>
            <Button type="submit" disabled={busy || !note.trim()}>{busy && <Loader2 className="animate-spin" />} Lưu điều chỉnh</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function OvertimeDialog({ record, holidays, busy, onClose, onSubmit }: {
  record: AttendanceRecord
  holidays: CompanyHoliday[]
  busy: boolean
  onClose: () => void
  onSubmit: (body: WorkforceBody["ApproveOvertimeRequest"]) => Promise<boolean>
}) {
  // The server enforces the same minimums: 300% on company holidays, 200% on Sundays, 150% otherwise.
  const minimum = holidays.some((holiday) => holiday.holidayDate === record.workDate) ? 3
    : new Date(`${record.workDate}T12:00:00Z`).getUTCDay() === 0 ? 2 : 1.5
  const [minutes, setMinutes] = useState(String(record.overtimeMinutes))
  const [multiplier, setMultiplier] = useState(String(record.overtimeMinutes > 0 ? record.overtimeMultiplier : minimum))
  const [taxExempt, setTaxExempt] = useState(record.overtimeTaxExempt)
  const [error, setError] = useState<string | null>(null)

  function submit(event: React.FormEvent) {
    event.preventDefault()
    const overtimeMinutes = Number(minutes)
    const overtimeMultiplier = Number(multiplier)
    if (!Number.isInteger(overtimeMinutes) || overtimeMinutes < 0) { setError("Số phút không hợp lệ"); return }
    if (!Number.isFinite(overtimeMultiplier) || overtimeMultiplier < minimum) { setError(`Hệ số tối thiểu là ${minimum}`); return }
    void onSubmit({ overtimeMinutes, overtimeMultiplier, taxExempt: overtimeMinutes > 0 && taxExempt })
      .then((ok) => { if (ok) onClose() })
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={submit}>
          <DialogHeader>
            <DialogTitle>Duyệt tăng ca ngày {record.workDate}</DialogTitle>
            <DialogDescription>
              Đã làm {record.workedMinutes} phút, trong đó {record.payableMinutes} phút là công thường. Nhập 0 phút để bỏ duyệt.
            </DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-4">
            <MinutesField id="overtime-minutes" label="Phút tăng ca được duyệt" value={minutes} onChange={setMinutes} />
            <div className="grid gap-1.5">
              <Label htmlFor="overtime-multiplier">Hệ số (tối thiểu {minimum})</Label>
              <Input id="overtime-multiplier" type="number" step="0.1" min={minimum} required value={multiplier}
                onChange={(event) => setMultiplier(event.target.value)} />
            </div>
            <label className="flex items-start gap-2 text-sm">
              <input type="checkbox" className="mt-1" checked={taxExempt} onChange={(event) => setTaxExempt(event.target.checked)} />
              <span>Đã đối soát, phần tăng ca này đủ điều kiện miễn thuế TNCN. Bỏ chọn nếu chưa chắc; khi đó tiền tăng ca chịu thuế.</span>
            </label>
            {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose}>Hủy</Button>
            <Button type="submit" disabled={busy}>{busy && <Loader2 className="animate-spin" />}<Clock /> Duyệt</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function MinutesField({ id, label, value, onChange }: { id: string; label: string; value: string; onChange: (value: string) => void }) {
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} type="number" min="0" step="1" required value={value} onChange={(event) => onChange(event.target.value)} />
    </div>
  )
}
