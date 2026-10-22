"use client"

import Link from "next/link"
import { useEffect, useState, type ReactNode } from "react"
import {
  ArrowUpRight,
  CalendarCheck,
  ChevronRight,
  ClipboardList,
  Clock,
  FilePlus2,
  Wallet,
} from "lucide-react"

import { formatDate } from "@/components/employee/employee-detail-page"
import { PeriodStatusBadge, periodLabel } from "@/components/payroll/payroll-shared"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { money, time, today, workforceApi } from "@/components/workforce/workforce-shared"
import { employeeListQuery, employeeRequest, type EmployeeSummary } from "@/lib/employee"
import type { Page } from "@/lib/pagination"
import { listRoleAssignmentRequests } from "@/lib/role-assignment"
import {
  ATTENDANCE_STATUS_LABELS,
  type AttendanceRecord,
  type LeaveBalance,
  type LeaveRequest,
  type OvertimeApprovalItem,
  type PayrollPeriod,
  type Payslip,
  type ResignationRequest,
} from "@/lib/workforce"

/**
 * Loads one dashboard widget. `undefined` while loading, `null` when the call failed or the widget
 * is not allowed; a failing widget never blocks the others.
 */
function useWidget<T>(enabled: boolean, load: () => Promise<T>, key: string): T | null | undefined {
  const [state, setState] = useState<{ key: string; value: T | null } | null>(null)
  useEffect(() => {
    if (!enabled) return
    let active = true
    load()
      .then((value) => { if (active) setState({ key, value }) })
      .catch(() => { if (active) setState({ key, value: null }) })
    return () => { active = false }
    // `key` identifies the request; `load` is a fresh closure on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, key])
  if (!enabled) return null
  return state?.key === key ? state.value : undefined
}

const ALL = { keyword: "", organizationUnitId: "", positionId: "" }

export function HrmDashboard() {
  const { session } = usePortalSession()
  const { account } = session
  const self = account.employee
  const permissions = new Set(account.permissions.map((permission) => permission.code))
  const has = (code: string) => permissions.has(code)
  const day = today()
  const month = day.slice(0, 7)

  // Personal widgets.
  const attendance = useWidget<AttendanceRecord | false>(!!self && has("attendance.self.read"),
    () => workforceApi<Page<AttendanceRecord>>(`attendance/employees/${self?.id}?from=${day}&to=${day}`)
      .then((page) => page.content[0] ?? false), `attendance:${day}`)
  const balance = useWidget(!!self && has("request.self.read"),
    () => workforceApi<LeaveBalance>(`leave-entitlements/employees/${self?.id}`), "balance")
  const myLeaves = useWidget(!!self && has("request.self.read"),
    () => workforceApi<Page<LeaveRequest>>(`leave-requests/employees/${self?.id}?size=100`), "my-leaves")
  const payslip = useWidget<Payslip | false>(!!self && has("payroll.self.read"),
    () => workforceApi<Page<Payslip>>(`payroll/employees/${self?.id}/payslips?size=1&sort=payrollPeriod.periodStart,desc`)
      .then((page) => page.content[0] ?? false), "payslip")

  // Work waiting for the signed-in user. The pending lists only return items this user decides.
  const pendingLeaves = useWidget(true, () => workforceApi<Page<LeaveRequest>>("leave-requests/pending?size=1"), "pending-leaves")
  const pendingResignations = useWidget(true,
    () => workforceApi<Page<ResignationRequest>>("resignation-requests/pending?size=1"), "pending-resignations")
  const pendingOvertime = useWidget(true, () => workforceApi<OvertimeApprovalItem[]>("attendance/overtime/pending"), "pending-ot")
  const pendingRoles = useWidget(has("role.assignment.approve"), () => listRoleAssignmentRequests(0, "PENDING"), "pending-roles")
  const periods = useWidget(has("report.payroll.read"),
    () => workforceApi<Page<PayrollPeriod>>("payroll/periods?size=12&sort=periodStart,desc"), "periods")

  // Company-wide people figures.
  const canReadEmployees = has("employee.list.read")
  const activeCount = useWidget(canReadEmployees, () => employeeRequest<Page<EmployeeSummary>>("hrm",
    employeeListQuery(0, 1, { ...ALL, employmentStatus: "ACTIVE" }, "id,asc")), "active")
  const probation = useWidget(canReadEmployees, () => employeeRequest<Page<EmployeeSummary>>("hrm",
    employeeListQuery(0, 50, { ...ALL, employmentStatus: "PROBATION" }, "hireDate,asc")), "probation")
  const newest = useWidget(canReadEmployees, () => employeeRequest<Page<EmployeeSummary>>("hrm",
    employeeListQuery(0, 5, { ...ALL, employmentStatus: "" }, "hireDate,desc")), "newest")

  const tasks = buildTasks({ pendingLeaves, pendingResignations, pendingOvertime, pendingRoles, periods, has })
  const latestPeriod = periods?.content.find((period) => period.status !== "CANCELLED")
  const myPending = myLeaves?.content.filter((leave) => leave.status === "PENDING" || leave.status === "DRAFT").length
  const hiredThisMonth = newest?.content.filter((employee) => employee.hireDate.startsWith(month)).length

  return (
    <>
      <PageHeader
        title={`Xin chào, ${self?.fullName ?? account.username}`}
        description={new Intl.DateTimeFormat("vi-VN", { dateStyle: "full", timeZone: "Asia/Ho_Chi_Minh" }).format(new Date())}
        actions={self && has("request.self.create") && (
          <Button nativeButton={false} render={<Link href="/leave-requests?new=1" />}><FilePlus2 /> Tạo đơn nghỉ</Button>
        )}
      />

      <div className="mt-8 grid gap-6">
        {self && (
          <section aria-label="Của tôi" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {attendance !== null && (
              <Tile icon={<Clock />} label="Chấm công hôm nay" href="/attendance" value={attendance === undefined ? undefined
                : attendance === false ? "Chưa có dữ liệu"
                : attendance.checkInAt ? `${time(attendance.checkInAt)} – ${time(attendance.checkOutAt)}`
                : ATTENDANCE_STATUS_LABELS[attendance.status]}
                hint={attendance && attendance.lateMinutes > 0 ? `Đi muộn ${attendance.lateMinutes} phút`
                  : attendance ? `Ca ${time(attendance.scheduledStartAt)} – ${time(attendance.scheduledEndAt)}` : "Ca hôm nay"} />
            )}
            {balance !== null && (
              <Tile icon={<CalendarCheck />} label={`Phép năm ${balance?.year ?? ""} còn lại`} href="/leave-requests"
                value={balance === undefined ? undefined : `${days(balance.remainingMinutes, balance.standardDayMinutes)} ngày`}
                hint={balance ? `Đã dùng ${days(balance.usedMinutes, balance.standardDayMinutes)} / ${days(balance.grantedMinutes, balance.standardDayMinutes)} ngày` : ""} />
            )}
            {myLeaves !== null && (
              <Tile icon={<ClipboardList />} label="Đơn của tôi đang mở" href="/leave-requests"
                value={myPending === undefined ? undefined : String(myPending)} hint="Nháp hoặc chờ duyệt" />
            )}
            {payslip !== null && (
              <Tile icon={<Wallet />} label="Lương thực nhận gần nhất" href="/payslips"
                value={payslip === undefined ? undefined : payslip === false ? "Chưa có" : money(payslip.netPay)}
                hint={payslip ? `Tháng ${periodLabel(payslip.periodYear, payslip.periodMonth)}` : "Phiếu lương hiện khi kỳ được duyệt"} />
            )}
          </section>
        )}

        <div className="grid gap-6 lg:grid-cols-3">
          <Card className={periods !== null ? "lg:col-span-2" : "lg:col-span-3"}>
            <CardHeader>
              <CardTitle>Việc cần xử lý</CardTitle>
            </CardHeader>
            <CardContent>
              {tasks === undefined
                ? <Skeleton className="h-24 w-full" />
                : tasks.length === 0
                  ? <p className="py-6 text-center text-sm text-muted-foreground">Không có việc nào đang chờ bạn.</p>
                  : (
                    <ul className="divide-y">
                      {tasks.map((task) => (
                        <li key={task.label}>
                          <Link href={task.href} className="flex items-center gap-3 py-3 text-sm hover:text-primary">
                            <span className="flex-1">{task.label}</span>
                            <Badge variant="secondary" className="tabular-nums">{task.count}</Badge>
                            <ChevronRight className="size-4 text-muted-foreground" />
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
            </CardContent>
          </Card>

          {periods !== null && (
            <Card>
              <CardHeader>
                <CardTitle>Kỳ lương gần nhất</CardTitle>
                {latestPeriod && <CardAction><PeriodStatusBadge status={latestPeriod.status} /></CardAction>}
              </CardHeader>
              <CardContent>
                {periods === undefined
                  ? <Skeleton className="h-24 w-full" />
                  : latestPeriod
                    ? (
                      <div className="grid gap-4">
                        <dl className="grid gap-2.5 text-sm">
                          <Line label="Kỳ lương" value={`Tháng ${periodLabel(latestPeriod.year, latestPeriod.month)}`} />
                          <Line label="Ngày trả" value={formatDate(latestPeriod.taxPaymentDate)} />
                          <Line label="Số nhân viên" value={String(latestPeriod.payslipCount)} />
                          <Line label="Tổng thu nhập" value={money(latestPeriod.totalGrossPay)} />
                          <Line label="Tổng thực nhận" value={money(latestPeriod.totalNetPay)} />
                        </dl>
                        <MoreLink href={`/payroll/${latestPeriod.id}`}>Mở bảng lương</MoreLink>
                      </div>
                    )
                    : (
                      <div className="grid gap-4">
                        <p className="text-sm text-muted-foreground">Chưa có kỳ lương nào.</p>
                        <MoreLink href="/payroll">Đến trang kỳ lương</MoreLink>
                      </div>
                    )}
              </CardContent>
            </Card>
          )}
        </div>

        {canReadEmployees && (
          <Card>
            <CardHeader>
              <CardTitle>Nhân sự</CardTitle>
              <CardAction><MoreLink href="/employees">Xem tất cả</MoreLink></CardAction>
            </CardHeader>
            <CardContent className="grid gap-6">
              <dl className="grid grid-cols-3 gap-4 border-b pb-5">
                <Figure label="Chính thức" value={activeCount === undefined ? "…" : String(activeCount?.totalElements ?? "—")} />
                <Figure label="Đang thử việc" value={probation === undefined ? "…" : String(probation?.totalElements ?? "—")} />
                <Figure label={`Vào làm tháng ${month.slice(5)}`} value={hiredThisMonth === undefined ? "…" : String(hiredThisMonth)} />
              </dl>
              <div className="grid gap-8 lg:grid-cols-2">
                <PeopleList title="Mới vào làm" people={newest} emptyText="Chưa có nhân viên." />
                <PeopleList title="Đang thử việc" people={probation} emptyText="Không có ai đang thử việc." />
              </div>
            </CardContent>
          </Card>
        )}
      </div>
    </>
  )
}

type Task = { label: string; count: number; href: string }

function buildTasks({ pendingLeaves, pendingResignations, pendingOvertime, pendingRoles, periods, has }: {
  pendingLeaves: Page<LeaveRequest> | null | undefined
  pendingResignations: Page<ResignationRequest> | null | undefined
  pendingOvertime: OvertimeApprovalItem[] | null | undefined
  pendingRoles: Page<unknown> | null | undefined
  periods: Page<PayrollPeriod> | null | undefined
  has: (code: string) => boolean
}): Task[] | undefined {
  if ([pendingLeaves, pendingResignations, pendingOvertime, pendingRoles, periods].includes(undefined)) return undefined
  const periodTask = (status: PayrollPeriod["status"], permission: string, verb: string): Task[] => {
    const waiting = has(permission) ? periods?.content.filter((period) => period.status === status) ?? [] : []
    return waiting.map((period) => ({
      label: `${verb} kỳ lương tháng ${periodLabel(period.year, period.month)}`, count: 1, href: `/payroll/${period.id}`,
    }))
  }
  return [
    { label: "Đơn nghỉ chờ duyệt", count: pendingLeaves?.totalElements ?? 0, href: "/leave-requests?tab=approvals" },
    { label: "Đơn nghỉ việc chờ duyệt", count: pendingResignations?.totalElements ?? 0, href: "/resignations?tab=approvals" },
    { label: "Tăng ca chờ duyệt", count: pendingOvertime?.length ?? 0, href: "/attendance?tab=overtime" },
    { label: "Đề xuất cấp/thu hồi role chờ duyệt", count: pendingRoles?.totalElements ?? 0, href: "/role-requests" },
    ...periodTask("DRAFT", "payroll.calculate", "Tính lương"),
    ...periodTask("CALCULATED", "payroll.approve", "Duyệt"),
    ...periodTask("APPROVED", "payroll.mark_paid", "Xác nhận đã trả"),
  ].filter((task) => task.count > 0)
}

const days = (minutes: number, dayMinutes: number) =>
  dayMinutes > 0 ? Number((minutes / dayMinutes).toFixed(1)).toLocaleString("vi-VN") : "0"

function Tile({ icon, label, value, hint, href }: {
  icon: ReactNode
  label: string
  /** undefined while loading. */
  value: string | undefined
  hint: string
  href: string
}) {
  return (
    <Link href={href} className="group rounded-xl border bg-card p-5 transition-colors hover:border-foreground/20">
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm text-muted-foreground">{label}</p>
        <span className="grid size-9 shrink-0 place-items-center rounded-full bg-muted text-muted-foreground [&_svg]:size-4">{icon}</span>
      </div>
      {value === undefined
        ? <Skeleton className="mt-2 h-8 w-28" />
        : <p className="mt-1 truncate text-2xl font-semibold tracking-tight tabular-nums">{value}</p>}
      <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
    </Link>
  )
}

function Figure({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 truncate text-xl font-semibold tabular-nums">{value}</dd>
    </div>
  )
}

/** One label/value row of a definition list, value aligned right. */
function Line({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="truncate font-medium tabular-nums">{value}</dd>
    </div>
  )
}

/** Quiet text link with an arrow, used instead of an extra button. */
function MoreLink({ href, children }: { href: string; children: ReactNode }) {
  return (
    <Link href={href} className="inline-flex items-center gap-1 justify-self-start text-sm text-muted-foreground hover:text-foreground">
      {children} <ArrowUpRight className="size-3.5" />
    </Link>
  )
}

function PeopleList({ title, people, emptyText }: {
  title: string
  people: Page<EmployeeSummary> | null | undefined
  emptyText: string
}) {
  return (
    <section className="grid content-start gap-2">
      <h3 className="text-sm font-medium">{title}</h3>
      {people === undefined
        ? <Skeleton className="h-24 w-full" />
        : !people || people.content.length === 0
          ? <p className="py-4 text-sm text-muted-foreground">{emptyText}</p>
          : (
            <ul className="divide-y">
              {people.content.slice(0, 5).map((employee) => (
                <li key={employee.id}>
                  <Link href={`/employees/${employee.id}`} className="flex items-center gap-3 py-2.5 text-sm hover:text-primary">
                    <span className="grid size-8 place-items-center rounded-full bg-muted text-xs font-semibold">
                      {employee.fullName.split(" ").pop()?.slice(0, 1)}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-medium">{employee.fullName}</span>
                      <span className="block truncate text-xs text-muted-foreground">
                        {employee.employeeCode} · {employee.currentAssignment?.positionTitle ?? "Chưa phân công"}
                      </span>
                    </span>
                    <span className="text-xs text-muted-foreground">{formatDate(employee.hireDate)}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
    </section>
  )
}
