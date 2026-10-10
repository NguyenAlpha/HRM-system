"use client"

import Link from "next/link"
import { useCallback, useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"

import { PortalSidebar } from "@/components/auth/portal-sidebar"
import { authorizedRequest, getCurrentSession } from "@/lib/auth/client"
import { AuthApiError, type SessionData } from "@/lib/auth/types"
import { apiErrorMessage } from "@/lib/api-helpers"

type Section = "attendance" | "leave" | "payroll"
type Paged<T> = { content: T[]; totalElements: number }
type Attendance = {
  id: number; workDate: string; scheduledMinutes: number; workedMinutes: number
  payableMinutes: number; overtimeMinutes: number; overtimeMultiplier: number; overtimeTaxExempt: boolean; status: string
  checkInAt: string | null; checkOutAt: string | null
}
type Leave = {
  id: number; employeeId: number; startAt: string; endAt: string; requestedMinutes: number
  leaveType: string; salaryTreatment: string; status: string; reason: string
}
type Period = { id: number; year: number; month: number; taxPaymentDate: string | null; status: string }
type Payslip = {
  id: number; payrollPeriodId: number; employeeNameSnapshot: string
  scheduledWorkMinutes: number; payableWorkMinutes: number; approvedOvertimeMinutes: number
  basicSalaryPay: number; allowancePay: number; overtimePay: number; taxExemptOvertimePay: number; grossPay: number
  insuranceSalaryBase: number; unemploymentInsuranceBase: number; employeeSocialInsurance: number; employeeHealthInsurance: number
  employeeUnemploymentInsurance: number; taxableIncome: number; personalIncomeTax: number; netPay: number
  items: { description: string; amount: number }[]
}
type Holiday = { id: number; holidayDate: string; name: string }
type SalaryHistory = { id: number; baseSalary: number; effectiveFrom: string; effectiveTo: string | null; reason: string | null }
type PayrollProfile = { id: number; effectiveFrom: string; effectiveTo: string | null; taxResident: boolean
  socialInsurance: boolean; healthInsurance: boolean; unemploymentInsurance: boolean
  insuranceSalary: number; wageRegion: number }
type TaxDependent = { id: number; fullName: string; effectiveFrom: string; effectiveTo: string | null }
type LeaveBalance = {
  year: number; grantedMinutes: number; carriedOverMinutes: number; adjustmentMinutes: number
  adjustmentReason: string | null; standardDayMinutes: number
  usedMinutes: number; pendingMinutes: number; remainingMinutes: number
}

// Mirrors LeaveType.defaultSalaryTreatment() on the server, which rejects any other value
// unless the caller holds request.manage.
const DERIVED_TREATMENT: Record<string, string> = {
  ANNUAL: "EMPLOYER_PAID", SICK: "SOCIAL_INSURANCE", MATERNITY: "SOCIAL_INSURANCE", UNPAID: "UNPAID", OTHER: "UNPAID",
}
const TREATMENT_LABELS: Record<string, string> = {
  EMPLOYER_PAID: "Công ty trả lương", SOCIAL_INSURANCE: "Bảo hiểm xã hội chi trả", UNPAID: "Không lương",
}
const toDays = (minutes: number, dayMinutes: number) => dayMinutes > 0 ? Number((minutes / dayMinutes).toFixed(2)) : 0

const today = () => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date())
const money = (amount: number) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount)
const api = <T,>(path: string, method = "GET", body?: unknown) => authorizedRequest<T>("hrm", `/workforce/${path}`, {
  method,
  ...(body === undefined ? {} : { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }),
})

export function WorkforcePage({ section }: { section: Section }) {
  const router = useRouter()
  const [session, setSession] = useState<SessionData | null>(null)
  const [employeeId, setEmployeeId] = useState("")
  const [month, setMonth] = useState(today().slice(0, 7))
  const [rows, setRows] = useState<Attendance[]>([])
  const [leaves, setLeaves] = useState<Leave[]>([])
  const [pendingLeaves, setPendingLeaves] = useState<Leave[]>([])
  const [periods, setPeriods] = useState<Period[]>([])
  const [payslips, setPayslips] = useState<Payslip[]>([])
  const [holidays, setHolidays] = useState<Holiday[]>([])
  const [salaryHistory, setSalaryHistory] = useState<SalaryHistory[]>([])
  const [salaryAmount, setSalaryAmount] = useState("")
  const [salaryEffectiveFrom, setSalaryEffectiveFrom] = useState(today())
  const [salaryReason, setSalaryReason] = useState("")
  const [profiles, setProfiles] = useState<PayrollProfile[]>([])
  const [dependents, setDependents] = useState<TaxDependent[]>([])
  const [profileDate, setProfileDate] = useState(today())
  const [insuranceSalary, setInsuranceSalary] = useState("")
  const [wageRegion, setWageRegion] = useState("1")
  const [socialInsurance, setSocialInsurance] = useState(true)
  const [healthInsurance, setHealthInsurance] = useState(true)
  const [unemploymentInsurance, setUnemploymentInsurance] = useState(true)
  const [dependentName, setDependentName] = useState("")
  const [dependentDate, setDependentDate] = useState(today())
  const [taxPaymentDate, setTaxPaymentDate] = useState(today())
  const [selectedPayslip, setSelectedPayslip] = useState<Payslip | null>(null)
  const [error, setError] = useState("")
  const [message, setMessage] = useState("")
  const [busy, setBusy] = useState(false)
  const [leaveStart, setLeaveStart] = useState("")
  const [leaveEnd, setLeaveEnd] = useState("")
  const [leaveMinutes, setLeaveMinutes] = useState("480")
  const [leaveReason, setLeaveReason] = useState("")
  const [leaveType, setLeaveType] = useState("ANNUAL")
  const [leaveTreatment, setLeaveTreatment] = useState("EMPLOYER_PAID")
  const [balance, setBalance] = useState<LeaveBalance | null>(null)
  const [adjustmentDays, setAdjustmentDays] = useState("0")
  const [adjustmentReason, setAdjustmentReason] = useState("")
  const [carriedOverDays, setCarriedOverDays] = useState("0")
  const [holidayDate, setHolidayDate] = useState("")
  const [holidayName, setHolidayName] = useState("")

  useEffect(() => {
    getCurrentSession("hrm").then((value) => {
      setSession(value)
      const params = new URLSearchParams(window.location.search)
      const selectedEmployeeId = params.get("employeeId")
      if (selectedEmployeeId && /^[1-9]\d*$/.test(selectedEmployeeId)) {
        setEmployeeId(selectedEmployeeId)
      } else if (value.account.employee) {
        setEmployeeId(String(value.account.employee.id))
      }
      const effectiveFrom = params.get("effectiveFrom")
      if (effectiveFrom && /^\d{4}-\d{2}-\d{2}$/.test(effectiveFrom)) {
        setSalaryEffectiveFrom(effectiveFrom)
      }
    }).catch((caught: unknown) => {
      if (caught instanceof AuthApiError && caught.status === 401) router.replace("/login")
      setError(apiErrorMessage(caught))
    })
  }, [router])

  const permissions = useMemo(() => new Set(session?.account.permissions.map((permission) => permission.code) ?? []), [session])
  const canManageAttendance = permissions.has("attendance.manage")
  const canApproveOvertime = permissions.has("attendance.overtime.approve")
  const canManageHoliday = permissions.has("organization.manage")
  const canApproveLeave = permissions.has("request.approve")
  const canCalculatePayroll = permissions.has("payroll.calculate")
  const canReadPeriods = permissions.has("report.payroll.read")
  const canReadCompensation = permissions.has("compensation.read")
  const canManageCompensation = permissions.has("compensation.manage")
  const canReadOrganization = permissions.has("organization.read")
  const canReadPayslip = permissions.has("payroll.self.read") || canReadPeriods
  const canReadAttendance = permissions.has("attendance.self.read") || permissions.has("attendance.read")
  const canReadLeave = permissions.has("request.self.read") || permissions.has("request.read")
  const canManageLeave = permissions.has("request.manage")
  const canUseSection = section === "attendance" ? canReadAttendance || canManageAttendance
    : section === "leave" ? canReadLeave || canApproveLeave || permissions.has("request.self.create")
      : canReadPayslip || canCalculatePayroll || canReadCompensation || canManageCompensation

  const load = useCallback(async () => {
    if (!session) return
    const id = Number(employeeId)
    const first = `${month}-01`
    const last = new Date(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0).getDate()
    const end = `${month}-${String(last).padStart(2, "0")}`
    try {
      if (section === "attendance") {
        if (id > 0 && canReadAttendance) {
          const result = await api<Paged<Attendance>>(`attendance/employees/${id}?from=${first}&to=${end}&size=100`)
          setRows(result.content)
        }
        if (canReadOrganization) {
          setHolidays(await api<Holiday[]>(`company-holidays?from=${first}&to=${end}`))
        }
      } else if (section === "leave") {
        if (id > 0 && canReadLeave) {
          const result = await api<Paged<Leave>>(`leave-requests/employees/${id}?size=100`)
          setLeaves(result.content)
          const loaded = await api<LeaveBalance>(`leave-entitlements/employees/${id}?year=${month.slice(0, 4)}`)
          setBalance(loaded)
          setAdjustmentDays(String(toDays(loaded.adjustmentMinutes, loaded.standardDayMinutes)))
          setCarriedOverDays(String(toDays(loaded.carriedOverMinutes, loaded.standardDayMinutes)))
        }
        if (canApproveLeave) {
          const result = await api<Paged<Leave>>("leave-requests/pending?size=100")
          setPendingLeaves(result.content)
        }
      } else {
        if (id > 0 && canReadCompensation) {
          setSalaryHistory(await api<SalaryHistory[]>(`compensation/employees/${id}/salary-history`))
          setProfiles(await api<PayrollProfile[]>(`compensation/employees/${id}/payroll-profiles`))
          setDependents(await api<TaxDependent[]>(`compensation/employees/${id}/tax-dependents`))
        }
        if (id > 0 && canReadPayslip) {
          const result = await api<Paged<Payslip>>(`payroll/employees/${id}/payslips?size=100`)
          setPayslips(result.content)
        }
        if (canReadPeriods) {
          const result = await api<Paged<Period>>("payroll/periods?size=100&sort=periodStart,desc")
          setPeriods(result.content)
        }
      }
      setError("")
    } catch (caught) { setError(apiErrorMessage(caught)) }
  }, [session, employeeId, month, section, canReadAttendance, canReadLeave, canReadPayslip, canReadPeriods, canReadCompensation, canReadOrganization, canApproveLeave])

  useEffect(() => {
    const timer = window.setTimeout(() => { void load() }, 0)
    return () => window.clearTimeout(timer)
  }, [load])

  async function act(label: string, action: () => Promise<unknown>) {
    setBusy(true); setError(""); setMessage("")
    try { await action(); setMessage(label); await load() }
    catch (caught) { setError(apiErrorMessage(caught)) }
    finally { setBusy(false) }
  }

  const id = Number(employeeId)
  const title = section === "attendance" ? "Chấm công" : section === "leave" ? "Đơn nghỉ phép" : "Phiếu lương"
  if (!session) return <main className="dashboard-loading"><p role="status">{error || "Đang tải phiên đăng nhập..."}</p></main>
  return <main className="portal-shell hrm-shell">
    <PortalSidebar portal="hrm" account={session.account} active={section} />
    <section className="portal-content space-y-6">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div><p className="eyebrow">HRM WORKSPACE</p><h1 className="text-3xl font-semibold">{title}</h1></div>
        <Link href="/dashboard" className="text-sm underline">Về tổng quan</Link>
      </header>
      {!canUseSection ? <p role="alert">Tài khoản chưa có quyền truy cập mục này.</p> : <>
        <div className="flex flex-wrap items-end gap-3">
          <label className="grid gap-1 text-sm">Mã nhân viên
            <input className="rounded border p-2" type="number" min="1" value={employeeId}
              disabled={!permissions.has("attendance.read") && !permissions.has("request.read") && !canReadPeriods && !canReadCompensation && !canManageCompensation}
              onChange={(event) => setEmployeeId(event.target.value)} />
          </label>
          {section === "attendance" && <label className="grid gap-1 text-sm">Tháng
            <input className="rounded border p-2" type="month" value={month} onChange={(event) => setMonth(event.target.value)} />
          </label>}
          <button className="rounded border px-4 py-2" onClick={() => void load()} disabled={busy}>Tải lại</button>
        </div>
        {error && <p role="alert" className="rounded bg-red-50 p-3 text-red-700">{error}</p>}
        {message && <p role="status" className="rounded bg-green-50 p-3 text-green-700">{message}</p>}
        {section === "attendance" && <>
          <div className="flex flex-wrap gap-2">
            {permissions.has("attendance.self.record") && <>
              <button className="rounded border px-4 py-2" disabled={busy || !id}
                onClick={() => void act("Đã ghi nhận vào ca", () => api("attendance/check-in", "POST", { employeeId: id }))}>Vào ca</button>
              <button className="rounded border px-4 py-2" disabled={busy || !id}
                onClick={() => {
                  const open = rows.find((row) => row.checkInAt && !row.checkOutAt)
                  const workDate = open?.workDate ?? today()
                  void act("Đã ghi nhận ra ca", () => api("attendance/check-out", "POST", { employeeId: id, workDate }))
                }}>Ra ca</button>
            </>}
            {canManageAttendance && <button className="rounded border px-4 py-2" disabled={busy}
              onClick={() => void act("Đã chuẩn bị bảng công", () => api(`attendance/prepare?year=${month.slice(0, 4)}&month=${Number(month.slice(5, 7))}`, "POST"))}>Chuẩn bị bảng công tháng</button>}
          </div>
          <div className="overflow-x-auto rounded border"><table className="w-full text-left text-sm"><thead><tr className="border-b"><th className="p-2">Ngày</th><th className="p-2">Trạng thái</th><th className="p-2">Phút tính lương</th><th className="p-2">Tăng ca</th><th className="p-2">Thao tác</th></tr></thead><tbody>
            {rows.map((row) => <tr key={row.id} className="border-b"><td className="p-2">{row.workDate}</td><td className="p-2">{row.status}</td><td className="p-2">{row.payableMinutes}/{row.scheduledMinutes}</td><td className="p-2">{row.overtimeMinutes}{row.overtimeMinutes > 0 && row.overtimeTaxExempt ? " (miễn thuế)" : ""}</td><td className="p-2 space-x-2">
              {canManageAttendance && <button className="underline" disabled={busy} onClick={() => {
                const status = window.prompt("Tình trạng sau đối soát: PRESENT hoặc UNAUTHORIZED_ABSENCE", row.status === "MISSING_PUNCH" ? "UNAUTHORIZED_ABSENCE" : row.status)
                if (status === null) return
                if (status !== "PRESENT" && status !== "UNAUTHORIZED_ABSENCE") { setError("Chọn PRESENT hoặc UNAUTHORIZED_ABSENCE"); return }
                const workedValue = window.prompt("Số phút thực tế đã làm", status === "PRESENT" ? String(row.workedMinutes) : "0")
                if (workedValue === null) return
                const payableValue = window.prompt("Số phút được tính lương", status === "PRESENT" ? String(row.payableMinutes) : "0")
                if (payableValue === null) return
                const worked = Number(workedValue)
                const payable = Number(payableValue)
                if (!Number.isInteger(worked) || worked < 0 || !Number.isInteger(payable) || payable < 0
                  || payable > row.scheduledMinutes || (status === "PRESENT" && worked === 0)
                  || (status === "UNAUTHORIZED_ABSENCE" && (worked !== 0 || payable !== 0))) {
                  setError("Phút làm việc và phút tính lương không phù hợp với tình trạng đã chọn"); return
                }
                const note = window.prompt("Lý do điều chỉnh và căn cứ đối soát")
                if (note === null) return
                if (!note.trim()) { setError("Cần nhập lý do điều chỉnh"); return }
                void act("Đã điều chỉnh công", () => api(`attendance/${row.id}`, "PUT", {
                  workedMinutes: worked, payableMinutes: payable, lateMinutes: 0, earlyLeaveMinutes: 0,
                  status, note: note.trim(),
                }))
              }}>Điều chỉnh</button>}
              {permissions.has("attendance.self.record") && row.checkInAt && !row.checkOutAt && <button className="underline" disabled={busy} onClick={() => void act("Đã ghi nhận ra ca", () => api("attendance/check-out", "POST", { employeeId: id, workDate: row.workDate }))}>Ra ca ngày này</button>}
              {canApproveOvertime && row.checkOutAt && <button className="underline" disabled={busy} onClick={() => {
                const value = window.prompt("Số phút tăng ca được duyệt", String(row.overtimeMinutes))
                if (value === null) return
                const minutes = Number(value)
                if (!Number.isInteger(minutes) || minutes < 0) { setError("Số phút không hợp lệ"); return }
                const minimum = holidays.some((holiday) => holiday.holidayDate === row.workDate) ? 3
                  : new Date(`${row.workDate}T12:00:00Z`).getUTCDay() === 0 ? 2 : 1.5
                const multiplierText = window.prompt("Hệ số tăng ca", String(row.overtimeMinutes ? row.overtimeMultiplier : minimum))
                if (multiplierText === null) return
                const multiplier = Number(multiplierText)
                if (!Number.isFinite(multiplier) || multiplier < minimum) { setError(`Hệ số tối thiểu là ${minimum}`); return }
                const taxExempt = minutes > 0 && window.confirm("Xác nhận phần tăng ca này đủ điều kiện miễn thuế TNCN theo quy định? Nếu chưa đối soát, chọn Hủy để tính vào thu nhập chịu thuế.")
                void act("Đã duyệt tăng ca", () => api(`attendance/${row.id}/overtime-approval`, "POST", { overtimeMinutes: minutes, overtimeMultiplier: multiplier, taxExempt }))
              }}>Duyệt tăng ca</button>}
            </td></tr>)}
          </tbody></table>{rows.length === 0 && <p className="p-3 text-sm">Chưa có dữ liệu công trong tháng.</p>}</div>
          {permissions.has("organization.read") && <div className="space-y-2"><h2 className="text-xl font-semibold">Ngày lễ công ty</h2>
            {holidays.map((holiday) => <p key={holiday.id} className="flex gap-3 text-sm">{holiday.holidayDate} — {holiday.name}
              {canManageHoliday && <button className="underline" disabled={busy} onClick={() => void act("Đã xóa ngày lễ", () => api(`company-holidays/${holiday.id}`, "DELETE"))}>Xóa</button>}</p>)}
            {canManageHoliday && <div className="flex flex-wrap gap-2"><input aria-label="Ngày lễ" type="date" className="rounded border p-2" value={holidayDate} onChange={(event) => setHolidayDate(event.target.value)} /><input aria-label="Tên ngày lễ" className="rounded border p-2" value={holidayName} onChange={(event) => setHolidayName(event.target.value)} /><button className="rounded border px-4 py-2" disabled={busy || !holidayDate || !holidayName.trim()} onClick={() => void act("Đã thêm ngày lễ", () => api("company-holidays", "POST", { date: holidayDate, name: holidayName }))}>Thêm ngày lễ</button></div>}
          </div>}
        </>}
        {section === "leave" && <>
          {balance && <div className="space-y-3 rounded border p-4">
            <h2 className="text-xl font-semibold">Phép năm {balance.year}</h2>
            <dl className="grid gap-3 text-sm sm:grid-cols-4">
              <div><dt className="text-muted-foreground">Được cấp</dt><dd className="text-lg font-semibold">{toDays(balance.grantedMinutes, balance.standardDayMinutes)} ngày</dd></div>
              <div><dt className="text-muted-foreground">Đã dùng</dt><dd className="text-lg font-semibold">{toDays(balance.usedMinutes, balance.standardDayMinutes)} ngày</dd></div>
              <div><dt className="text-muted-foreground">Đang chờ duyệt</dt><dd className="text-lg font-semibold">{toDays(balance.pendingMinutes, balance.standardDayMinutes)} ngày</dd></div>
              <div><dt className="text-muted-foreground">Còn lại</dt><dd className="text-lg font-semibold">{toDays(balance.remainingMinutes, balance.standardDayMinutes)} ngày</dd></div>
            </dl>
            <p className="text-xs text-muted-foreground">
              Một ngày = {balance.standardDayMinutes} phút theo ca làm việc. Đã gồm {toDays(balance.carriedOverMinutes, balance.standardDayMinutes)} ngày chuyển từ năm trước
              và điều chỉnh {toDays(balance.adjustmentMinutes, balance.standardDayMinutes)} ngày{balance.adjustmentReason ? ` (${balance.adjustmentReason})` : ""}.
            </p>
            {canManageLeave && <div className="grid gap-3 border-t pt-3 sm:grid-cols-2">
              <form className="grid gap-2" onSubmit={(event) => {
                event.preventDefault()
                void act("Đã cập nhật điều chỉnh phép năm", () => api(`leave-entitlements/employees/${id}/adjustment?year=${balance.year}`, "PUT", {
                  adjustmentMinutes: Math.round(Number(adjustmentDays) * balance.standardDayMinutes), reason: adjustmentReason,
                }))
              }}>
                <label className="grid gap-1 text-sm">Tổng điều chỉnh (ngày, có thể âm)<input required type="number" step="0.5" className="rounded border p-2" value={adjustmentDays} onChange={(event) => setAdjustmentDays(event.target.value)} /></label>
                <label className="grid gap-1 text-sm">Lý do<input required className="rounded border p-2" value={adjustmentReason} onChange={(event) => setAdjustmentReason(event.target.value)} /></label>
                <button className="rounded border px-4 py-2" disabled={busy || !id}>Lưu điều chỉnh</button>
              </form>
              <form className="grid content-start gap-2" onSubmit={(event) => {
                event.preventDefault()
                void act("Đã cập nhật phép chuyển từ năm trước", () => api(`leave-entitlements/employees/${id}/carried-over?year=${balance.year}`, "PUT", {
                  carriedOverMinutes: Math.round(Number(carriedOverDays) * balance.standardDayMinutes),
                }))
              }}>
                <label className="grid gap-1 text-sm">Chuyển từ năm trước (ngày)<input required type="number" min="0" step="0.5" className="rounded border p-2" value={carriedOverDays} onChange={(event) => setCarriedOverDays(event.target.value)} /></label>
                <button className="rounded border px-4 py-2" disabled={busy || !id}>Lưu phép chuyển năm</button>
              </form>
            </div>}
          </div>}
          {canApproveLeave && <div className="space-y-2"><h2 className="text-xl font-semibold">Đơn chờ duyệt</h2>
            {pendingLeaves.map((leave) => <div key={leave.id} className="rounded border p-3 text-sm">
              <strong>Đơn #{leave.id} · Nhân viên #{leave.employeeId}</strong>
              <p>{leave.leaveType} · {leave.requestedMinutes} phút · {new Date(leave.startAt).toLocaleString("vi-VN")} → {new Date(leave.endAt).toLocaleString("vi-VN")}</p>
              <p>{leave.reason}</p>
              <div className="mt-2 flex gap-3"><button className="underline" disabled={busy} onClick={() => void act("Đã duyệt đơn", () => api(`leave-requests/${leave.id}/approve`, "POST", { comment: "" }))}>Duyệt</button>
                <button className="underline" disabled={busy} onClick={() => void act("Đã từ chối đơn", () => api(`leave-requests/${leave.id}/reject`, "POST", { comment: "" }))}>Từ chối</button></div>
            </div>)}
            {pendingLeaves.length === 0 && <p>Không có đơn chờ duyệt trong phạm vi được giao.</p>}
          </div>}
          {permissions.has("request.self.create") && <form className="grid gap-3 rounded border p-4 sm:grid-cols-2" onSubmit={(event) => {
            event.preventDefault()
            void act("Đã tạo đơn nháp", () => api("leave-requests", "POST", {
              employeeId: id, leaveType, ...(canManageLeave ? { salaryTreatment: leaveTreatment } : {}),
              startAt: new Date(leaveStart).toISOString(), endAt: new Date(leaveEnd).toISOString(),
              requestedMinutes: Number(leaveMinutes), reason: leaveReason,
            }))
          }}>
            <h2 className="sm:col-span-2 text-xl font-semibold">Tạo đơn nghỉ</h2>
            <label className="grid gap-1 text-sm">Bắt đầu<input required type="datetime-local" className="rounded border p-2" value={leaveStart} onChange={(event) => setLeaveStart(event.target.value)} /></label>
            <label className="grid gap-1 text-sm">Kết thúc<input required type="datetime-local" className="rounded border p-2" value={leaveEnd} onChange={(event) => setLeaveEnd(event.target.value)} /></label>
            <label className="grid gap-1 text-sm">Loại nghỉ<select className="rounded border p-2" value={leaveType} onChange={(event) => { setLeaveType(event.target.value); setLeaveTreatment(DERIVED_TREATMENT[event.target.value]) }}><option value="ANNUAL">Phép năm</option><option value="SICK">Ốm</option><option value="MATERNITY">Thai sản</option><option value="UNPAID">Không lương</option><option value="OTHER">Khác</option></select></label>
            {canManageLeave
              ? <label className="grid gap-1 text-sm">Chế độ lương<select className="rounded border p-2" value={leaveTreatment} onChange={(event) => setLeaveTreatment(event.target.value)}><option value="EMPLOYER_PAID">Công ty trả lương</option><option value="UNPAID">Không lương</option><option value="SOCIAL_INSURANCE">Bảo hiểm xã hội</option></select></label>
              : <div className="grid gap-1 text-sm">Chế độ lương<span className="rounded border bg-muted p-2">{TREATMENT_LABELS[DERIVED_TREATMENT[leaveType]]}</span></div>}
            <label className="grid gap-1 text-sm">Số phút nghỉ<input required type="number" min="1" className="rounded border p-2" value={leaveMinutes} onChange={(event) => setLeaveMinutes(event.target.value)} /></label>
            <label className="grid gap-1 text-sm">Lý do<input required className="rounded border p-2" value={leaveReason} onChange={(event) => setLeaveReason(event.target.value)} /></label>
            <button className="rounded border px-4 py-2 sm:col-span-2" disabled={busy || !id}>Lưu đơn nháp</button>
          </form>}
          <div className="space-y-2">{leaves.map((leave) => <div key={leave.id} className="rounded border p-3 text-sm"><div className="font-medium">#{leave.id} · {leave.leaveType} · {leave.status}</div><div>{new Date(leave.startAt).toLocaleString("vi-VN")} → {new Date(leave.endAt).toLocaleString("vi-VN")} · {leave.requestedMinutes} phút</div><p>{leave.reason}</p><div className="mt-2 flex gap-3">
            {leave.status === "DRAFT" && <button className="underline" disabled={busy} onClick={() => void act("Đã gửi đơn", () => api(`leave-requests/${leave.id}/submit`, "POST"))}>Gửi duyệt</button>}
            {["DRAFT", "PENDING"].includes(leave.status) && <button className="underline" disabled={busy} onClick={() => void act("Đã hủy đơn", () => api(`leave-requests/${leave.id}/cancel`, "POST"))}>Hủy</button>}
            {canApproveLeave && leave.status === "PENDING" && <><button className="underline" disabled={busy} onClick={() => void act("Đã duyệt đơn", () => api(`leave-requests/${leave.id}/approve`, "POST", { comment: "" }))}>Duyệt</button><button className="underline" disabled={busy} onClick={() => void act("Đã từ chối đơn", () => api(`leave-requests/${leave.id}/reject`, "POST", { comment: "" }))}>Từ chối</button></>}
          </div></div>)}{leaves.length === 0 && <p>Chưa có đơn nghỉ.</p>}</div>
        </>}
        {section === "payroll" && <>
          {(canReadCompensation || canManageCompensation) && <div className="space-y-3 rounded border p-4">
            <h2 className="text-xl font-semibold">Lịch sử lương của nhân viên #{id || "—"}</h2>
            {canReadCompensation && (salaryHistory.length > 0
              ? salaryHistory.map((salary) => <p key={salary.id} className="text-sm">
                  {salary.effectiveFrom} → {salary.effectiveTo ?? "hiện tại"}: <strong>{money(salary.baseSalary)}</strong>
                  {salary.reason ? ` · ${salary.reason}` : ""}
                </p>)
              : <p className="text-sm">Chưa có mức lương hiệu lực. Cần nhập trước khi tính kỳ lương.</p>)}
            {canManageCompensation && <form className="flex flex-wrap items-end gap-3" onSubmit={(event) => {
              event.preventDefault()
              const amount = Number(salaryAmount)
              if (!Number.isFinite(amount) || amount <= 0 || !salaryEffectiveFrom || !id) {
                setError("Kiểm tra lại nhân viên, mức lương và ngày hiệu lực")
                return
              }
              void act("Đã lưu mức lương", () => api(`compensation/employees/${id}/salary-history`, "POST", {
                baseSalary: amount, effectiveFrom: salaryEffectiveFrom, reason: salaryReason.trim() || null, note: null,
              }))
            }}>
              <label className="grid gap-1 text-sm">Lương tháng (VND)
                <input required type="number" min="1" step="0.01" className="rounded border p-2" value={salaryAmount} onChange={(event) => setSalaryAmount(event.target.value)} />
              </label>
              <label className="grid gap-1 text-sm">Hiệu lực từ
                <input required type="date" className="rounded border p-2" value={salaryEffectiveFrom} onChange={(event) => setSalaryEffectiveFrom(event.target.value)} />
              </label>
              <label className="grid gap-1 text-sm">Lý do
                <input className="rounded border p-2" value={salaryReason} onChange={(event) => setSalaryReason(event.target.value)} />
              </label>
              <button className="rounded border px-4 py-2" disabled={busy || !id}>Lưu mức lương</button>
            </form>}
          </div>}
          {canReadCompensation && <div className="space-y-3 rounded border p-4"><h2 className="text-xl font-semibold">Hồ sơ bảo hiểm và thuế</h2>
            {profiles.map((profile) => <p key={profile.id} className="text-sm">{profile.effectiveFrom} → {profile.effectiveTo ?? "hiện tại"}: căn cứ {money(profile.insuranceSalary)}, vùng {profile.wageRegion}; BHXH {profile.socialInsurance ? "có" : "không"}, BHYT {profile.healthInsurance ? "có" : "không"}, BHTN {profile.unemploymentInsurance ? "có" : "không"}</p>)}
            {profiles.length === 0 && <p className="text-sm">Chưa có hồ sơ; chưa thể tính lương thực lĩnh.</p>}
            {canManageCompensation && <form className="flex flex-wrap items-end gap-2" onSubmit={(event) => { event.preventDefault(); void act("Đã lưu hồ sơ bảo hiểm", () => api(`compensation/employees/${id}/payroll-profiles`, "POST", { effectiveFrom: profileDate, taxResident: true, socialInsurance, healthInsurance, unemploymentInsurance, insuranceSalary: Number(insuranceSalary), wageRegion: Number(wageRegion) })) }}>
              <label className="grid gap-1 text-sm">Hiệu lực từ<input required type="date" className="rounded border p-2" value={profileDate} onChange={(event) => setProfileDate(event.target.value)} /></label>
              <label className="grid gap-1 text-sm">Lương đóng bảo hiểm<input required type="number" min="0" className="rounded border p-2" value={insuranceSalary} onChange={(event) => setInsuranceSalary(event.target.value)} /></label>
              <label className="grid gap-1 text-sm">Vùng<select className="rounded border p-2" value={wageRegion} onChange={(event) => setWageRegion(event.target.value)}>{[1, 2, 3, 4].map((region) => <option key={region} value={region}>{region}</option>)}</select></label>
              <label className="flex gap-1 text-sm"><input type="checkbox" checked={socialInsurance} onChange={(event) => setSocialInsurance(event.target.checked)} />BHXH</label>
              <label className="flex gap-1 text-sm"><input type="checkbox" checked={healthInsurance} onChange={(event) => setHealthInsurance(event.target.checked)} />BHYT</label>
              <label className="flex gap-1 text-sm"><input type="checkbox" checked={unemploymentInsurance} onChange={(event) => setUnemploymentInsurance(event.target.checked)} />BHTN</label>
              <button className="rounded border px-4 py-2" disabled={busy || !id}>Lưu hồ sơ</button>
            </form>}
            <h3 className="font-semibold">Người phụ thuộc</h3>
            {dependents.map((dependent) => <p key={dependent.id} className="text-sm">{dependent.fullName}: {dependent.effectiveFrom} → {dependent.effectiveTo ?? "hiện tại"} {canManageCompensation && !dependent.effectiveTo && <button className="underline" disabled={busy} onClick={() => { const end = window.prompt("Ngày cuối được giảm trừ (YYYY-MM-DD)", today()); if (end) void act("Đã kết thúc người phụ thuộc", () => api(`compensation/employees/${id}/tax-dependents/${dependent.id}/end`, "PUT", { effectiveTo: end })) }}>Kết thúc</button>}</p>)}
            {canManageCompensation && <form className="flex flex-wrap items-end gap-2" onSubmit={(event) => { event.preventDefault(); void act("Đã thêm người phụ thuộc", () => api(`compensation/employees/${id}/tax-dependents`, "POST", { fullName: dependentName, effectiveFrom: dependentDate })) }}>
              <label className="grid gap-1 text-sm">Họ tên<input required className="rounded border p-2" value={dependentName} onChange={(event) => setDependentName(event.target.value)} /></label>
              <label className="grid gap-1 text-sm">Hiệu lực từ<input required type="date" className="rounded border p-2" value={dependentDate} onChange={(event) => setDependentDate(event.target.value)} /></label>
              <button className="rounded border px-4 py-2" disabled={busy || !id}>Thêm người phụ thuộc</button>
            </form>}
          </div>}
          {canReadPeriods && <div className="space-y-3"><h2 className="text-xl font-semibold">Kỳ lương</h2>{canCalculatePayroll && <div className="flex flex-wrap gap-2"><input aria-label="Tháng kỳ lương" type="month" className="rounded border p-2" value={month} onChange={(event) => setMonth(event.target.value)} /><label className="grid gap-1 text-sm">Ngày dự kiến trả lương<input type="date" className="rounded border p-2" value={taxPaymentDate} onChange={(event) => setTaxPaymentDate(event.target.value)} /></label><button className="rounded border px-4 py-2" disabled={busy} onClick={() => void act("Đã tạo kỳ lương", () => api("payroll/periods", "POST", { year: Number(month.slice(0, 4)), month: Number(month.slice(5, 7)), taxPaymentDate }))}>Tạo kỳ</button></div>}
            {periods.map((period) => <div key={period.id} className="flex flex-wrap items-center gap-3 rounded border p-3 text-sm"><strong>{period.month}/{period.year}</strong><span>{period.status}</span>
              {canCalculatePayroll && (["DRAFT", "CALCULATED"].includes(period.status)) && <button className="underline" disabled={busy} onClick={() => void act("Đã tính kỳ lương", () => api(`payroll/periods/${period.id}/calculate`, "POST", { taxPaymentDate: period.taxPaymentDate ?? taxPaymentDate }))}>Tính lại</button>}
              {period.status === "CALCULATED" && permissions.has("payroll.approve") && <button className="underline" disabled={busy} onClick={() => void act("Đã duyệt kỳ lương", () => api(`payroll/periods/${period.id}/approve`, "POST"))}>Duyệt</button>}
              {period.status === "APPROVED" && permissions.has("payroll.mark_paid") && <button className="underline" disabled={busy} onClick={() => void act("Đã ghi nhận thanh toán", () => api(`payroll/periods/${period.id}/mark-paid`, "POST", { taxPaymentDate: today() }))}>Đã trả</button>}
              {period.status === "PAID" && permissions.has("payroll.lock") && <button className="underline" disabled={busy} onClick={() => void act("Đã khóa kỳ lương", () => api(`payroll/periods/${period.id}/lock`, "POST"))}>Khóa</button>}
            </div>)}
          </div>}
          <div className="space-y-3"><h2 className="text-xl font-semibold">Phiếu lương nhân viên</h2>{payslips.map((slip) => <button key={slip.id} className="block w-full rounded border p-3 text-left text-sm" onClick={() => setSelectedPayslip(slip)}><strong>Phiếu #{slip.id}</strong> · Kỳ #{slip.payrollPeriodId} · {slip.employeeNameSnapshot} · Thực lĩnh {money(slip.netPay)}</button>)}{payslips.length === 0 && <p>Chưa có phiếu lương.</p>}</div>
          {selectedPayslip && <div className="space-y-2 rounded border p-4 text-sm"><h2 className="text-xl font-semibold">Chi tiết phiếu #{selectedPayslip.id}</h2><p>Công: {selectedPayslip.payableWorkMinutes}/{selectedPayslip.scheduledWorkMinutes} phút · Tăng ca: {selectedPayslip.approvedOvertimeMinutes} phút</p>{selectedPayslip.items.map((item, index) => <p key={index}>{item.description}: {money(item.amount)}</p>)}<p>Tổng thu nhập: {money(selectedPayslip.grossPay)} · Tăng ca miễn thuế đã xác nhận: {money(selectedPayslip.taxExemptOvertimePay)}</p><p>Căn cứ BHXH/BHYT: {money(selectedPayslip.insuranceSalaryBase)} · BHTN: {money(selectedPayslip.unemploymentInsuranceBase)}</p><p>BHXH: {money(selectedPayslip.employeeSocialInsurance)} · BHYT: {money(selectedPayslip.employeeHealthInsurance)} · BHTN: {money(selectedPayslip.employeeUnemploymentInsurance)}</p><p>Thu nhập tính thuế: {money(selectedPayslip.taxableIncome)} · Thuế TNCN: {money(selectedPayslip.personalIncomeTax)}</p><strong>Thực lĩnh: {money(selectedPayslip.netPay)}</strong></div>}
        </>}
      </>}
    </section>
  </main>
}
