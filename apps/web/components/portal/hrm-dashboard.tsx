"use client"

import Link from "next/link"
import { useEffect, useState, type ReactNode } from "react"
import {
  CalendarCheck,
  CalendarClock,
  ChevronRight,
  ClipboardList,
  Clock,
  FilePlus2,
  UserPlus,
  UsersRound,
  Wallet,
} from "lucide-react"

import { formatDate } from "@/components/employee/employee-detail-page"
import { PeriodStatusBadge, periodLabel } from "@/components/payroll/payroll-shared"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
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

  const tasks = buildTasks({ pendingLeaves, pendingOvertime, pendingRoles, periods, has })
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

      <div className="mt-6 grid gap-6">
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

        <div className="grid gap-4 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>Việc cần xử lý</CardTitle>
              <CardDescription>Các mục đang chờ bạn quyết định.</CardDescription>
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
                            <Badge>{task.count}</Badge>
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
                <CardDescription>
                  {latestPeriod ? `Tháng ${periodLabel(latestPeriod.year, latestPeriod.month)} · trả ngày ${formatDate(latestPeriod.taxPaymentDate)}` : "Chưa có kỳ lương nào."}
                </CardDescription>
                {latestPeriod && <CardAction><PeriodStatusBadge status={latestPeriod.status} /></CardAction>}
              </CardHeader>
              <CardContent>
                {periods === undefined
                  ? <Skeleton className="h-24 w-full" />
                  : latestPeriod
                    ? (
                      <div className="grid gap-4">
                        <dl className="grid grid-cols-3 gap-4">
                          <Figure label="Số nhân viên" value={String(latestPeriod.payslipCount)} />
                          <Figure label="Tổng thu nhập" value={money(latestPeriod.totalGrossPay)} />
                          <Figure label="Tổng thực nhận" value={money(latestPeriod.totalNetPay)} />
                        </dl>
                        <Button variant="outline" className="justify-self-start" nativeButton={false}
                          render={<Link href={`/payroll/${latestPeriod.id}`} />}>Mở bảng lương</Button>
                      </div>
                    )
                    : <Button variant="outline" nativeButton={false} render={<Link href="/payroll" />}>Đến trang kỳ lương</Button>}
              </CardContent>
            </Card>
          )}
        </div>

        {canReadEmployees && (
          <>
            <section aria-label="Nhân sự" className="grid gap-4 sm:grid-cols-3">
              <Tile icon={<UsersRound />} label="Nhân sự chính thức" href="/employees"
                value={activeCount === undefined ? undefined : String(activeCount?.totalElements ?? "—")} hint="Đang làm việc" />
              <Tile icon={<CalendarClock />} label="Đang thử việc" href="/employees"
                value={probation === undefined ? undefined : String(probation?.totalElements ?? "—")} hint="Cần xác nhận khi hết thử việc" />
              <Tile icon={<UserPlus />} label="Vào làm tháng này" href="/employees"
                value={hiredThisMonth === undefined ? undefined : String(hiredThisMonth)} hint={`Tháng ${month.slice(5)}/${month.slice(0, 4)}`} />
            </section>

            <div className="grid gap-4 lg:grid-cols-2">
              <PeopleCard title="Nhân viên mới" description="5 người vào làm gần nhất." people={newest} emptyText="Chưa có nhân viên." />
              <PeopleCard title="Đang thử việc" description="Xếp theo ngày vào làm, người vào sớm nhất lên trước."
                people={probation} emptyText="Không có ai đang thử việc." />
            </div>
          </>
        )}
      </div>
    </>
  )
}

type Task = { label: string; count: number; href: string }

function buildTasks({ pendingLeaves, pendingOvertime, pendingRoles, periods, has }: {
  pendingLeaves: Page<LeaveRequest> | null | undefined
  pendingOvertime: OvertimeApprovalItem[] | null | undefined
  pendingRoles: Page<unknown> | null | undefined
  periods: Page<PayrollPeriod> | null | undefined
  has: (code: string) => boolean
}): Task[] | undefined {
  if ([pendingLeaves, pendingOvertime, pendingRoles, periods].includes(undefined)) return undefined
  const periodTask = (status: PayrollPeriod["status"], permission: string, verb: string): Task[] => {
    const waiting = has(permission) ? periods?.content.filter((period) => period.status === status) ?? [] : []
    return waiting.map((period) => ({
      label: `${verb} kỳ lương tháng ${periodLabel(period.year, period.month)}`, count: 1, href: `/payroll/${period.id}`,
    }))
  }
  return [
    { label: "Đơn nghỉ chờ duyệt", count: pendingLeaves?.totalElements ?? 0, href: "/leave-requests?tab=approvals" },
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
    <Link href={href} className="group rounded-xl border bg-card p-4 transition-colors hover:border-primary/50">
      <p className="flex items-center gap-2 text-sm text-muted-foreground [&_svg]:size-4">{icon} {label}</p>
      {value === undefined
        ? <Skeleton className="mt-2 h-7 w-24" />
        : <p className="mt-1.5 text-2xl font-semibold tabular-nums group-hover:text-primary">{value}</p>}
      <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
    </Link>
  )
}

function Figure({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 truncate font-semibold tabular-nums">{value}</dd>
    </div>
  )
}

function PeopleCard({ title, description, people, emptyText }: {
  title: string
  description: string
  people: Page<EmployeeSummary> | null | undefined
  emptyText: string
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>
        {people === undefined
          ? <Skeleton className="h-24 w-full" />
          : !people || people.content.length === 0
            ? <p className="py-6 text-center text-sm text-muted-foreground">{emptyText}</p>
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
                      <span className="text-xs text-muted-foreground">Vào làm {formatDate(employee.hireDate)}</span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
      </CardContent>
    </Card>
  )
}
