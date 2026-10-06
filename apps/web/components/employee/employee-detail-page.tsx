"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useCallback, useEffect, useState } from "react"
import {
  ArrowLeft,
  BriefcaseBusiness,
  Building2,
  CalendarDays,
  Clock3,
  GraduationCap,
  KeyRound,
  MapPin,
  RefreshCw,
  UserRound,
  UsersRound,
} from "lucide-react"

import { RbacFeedback } from "@/components/admin/rbac-controls"
import { PortalSidebar } from "@/components/auth/portal-sidebar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { getCurrentSession } from "@/lib/auth/client"
import { AuthApiError, type SessionData } from "@/lib/auth/types"
import {
  EDUCATION_LEVEL_LABELS,
  employeeErrorMessage,
  employeeRequest,
  EMPLOYMENT_STATUS_LABELS,
  EMPLOYMENT_TYPE_LABELS,
  GENDER_LABELS,
  isSessionExpired,
  type EmployeeAssignment,
  type EmployeeDetail,
} from "@/lib/employee"

const ACCOUNT_STATUS_LABELS: Record<string, string> = {
  PENDING: "Chờ kích hoạt",
  ACTIVE: "Đang hoạt động",
  LOCKED: "Đang khóa",
  DISABLED: "Đã vô hiệu hóa",
}

function formatDate(value: string | null | undefined) {
  if (!value) return "—"
  const [year, month, day] = value.split("-")
  return year && month && day ? `${day}/${month}/${year}` : value
}

function todayIsoDate() {
  const today = new Date()
  const year = today.getFullYear()
  const month = String(today.getMonth() + 1).padStart(2, "0")
  const day = String(today.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

function InfoField({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-words text-sm font-medium">{value || "—"}</dd>
    </div>
  )
}

function AssignmentBadge({ assignment, currentAssignmentId }: {
  assignment: EmployeeAssignment
  currentAssignmentId: number | null
}) {
  if (assignment.id === currentAssignmentId) {
    return <Badge>Hiện tại</Badge>
  }
  const today = todayIsoDate()
  if (assignment.effectiveFrom > today) {
    return <Badge variant="secondary">Sắp hiệu lực</Badge>
  }
  if (assignment.effectiveTo && assignment.effectiveTo < today) {
    return <Badge variant="outline">Đã kết thúc</Badge>
  }
  return <Badge variant="secondary">Đang hiệu lực</Badge>
}

function DetailSkeleton() {
  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <Skeleton className="h-64 lg:col-span-2" />
      <Skeleton className="h-64" />
      <Skeleton className="h-72 lg:col-span-3" />
    </div>
  )
}

export function EmployeeDetailPage({ employeeId }: { employeeId: string }) {
  const router = useRouter()
  const [session, setSession] = useState<SessionData | null>(null)
  const [sessionError, setSessionError] = useState<string | null>(null)
  const [detail, setDetail] = useState<EmployeeDetail | null>(null)
  const [assignments, setAssignments] = useState<EmployeeAssignment[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)

  const numericEmployeeId = Number(employeeId)
  const hasValidEmployeeId = /^\d+$/.test(employeeId)
    && Number.isSafeInteger(numericEmployeeId)
    && numericEmployeeId > 0

  const onSessionExpired = useCallback(() => {
    router.replace("/login")
    router.refresh()
  }, [router])

  useEffect(() => {
    let active = true
    getCurrentSession("hrm")
      .then((result) => {
        if (active) setSession(result)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (caught instanceof AuthApiError && (caught.status === 401 || caught.status === 403)) {
          onSessionExpired()
        }
        setSessionError(employeeErrorMessage(caught))
      })
    return () => { active = false }
  }, [onSessionExpired])

  useEffect(() => {
    if (!session || !hasValidEmployeeId) return

    let active = true
    const canReadAssignments = session.account.permissions.some(
      (permission) => permission.code === "employee.assignment.read",
    )
    Promise.all([
      employeeRequest<EmployeeDetail>("hrm", `/${numericEmployeeId}`),
      canReadAssignments
        ? employeeRequest<EmployeeAssignment[]>("hrm", `/${numericEmployeeId}/assignments`)
        : Promise.resolve([]),
    ])
      .then(([employeeDetail, assignmentHistory]) => {
        if (!active) return
        setDetail(employeeDetail)
        setAssignments(assignmentHistory)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isSessionExpired(caught)) onSessionExpired()
        setError(employeeErrorMessage(caught))
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => { active = false }
  }, [hasValidEmployeeId, numericEmployeeId, onSessionExpired, revision, session])

  function reload() {
    setLoading(true)
    setError(null)
    setRevision((value) => value + 1)
  }

  if (!session) {
    return (
      <main className="dashboard-loading">
        <p role={sessionError ? "alert" : "status"}>
          {sessionError ?? "Đang xác minh phiên đăng nhập..."}
        </p>
        {sessionError && <Link href="/employees">Về danh sách nhân sự</Link>}
      </main>
    )
  }

  const currentAssignment = detail?.currentAssignment ?? null
  const canReadAssignments = session.account.permissions.some(
    (permission) => permission.code === "employee.assignment.read",
  )
  const canReadEmployeeList = session.account.permissions.some(
    (permission) => permission.code === "employee.list.read",
  )
  const canManageCompensation = session.account.permissions.some(
    (permission) => permission.code === "compensation.manage",
  )
  const displayedError = hasValidEmployeeId ? error : "Mã nhân sự trên đường dẫn không hợp lệ."
  const displayedLoading = hasValidEmployeeId && loading

  return (
    <main className="portal-shell hrm-shell">
      <PortalSidebar portal="hrm" account={session.account} active="employees" />
      <section className="portal-content">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex min-w-0 items-center gap-3">
            <span className="avatar shrink-0 text-lg">
              {(detail?.fullName ?? "?").slice(0, 1).toUpperCase()}
            </span>
            <div className="min-w-0">
              <p className="eyebrow">HỒ SƠ NHÂN SỰ</p>
              <h1 className="mt-1 truncate text-3xl font-semibold tracking-tight sm:text-4xl">
                {detail?.fullName ?? "Chi tiết nhân sự"}
              </h1>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                {detail && <code className="text-sm text-muted-foreground">{detail.employeeCode}</code>}
                {detail && (
                  <Badge variant={detail.employmentStatus === "ACTIVE" ? "default" : "outline"}>
                    {EMPLOYMENT_STATUS_LABELS[detail.employmentStatus]}
                  </Badge>
                )}
              </div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={reload}
              disabled={displayedLoading}
            >
              <RefreshCw className={displayedLoading ? "animate-spin" : ""} /> Tải lại
            </Button>
            <Button
              variant="outline"
              nativeButton={false}
              render={<Link href={canReadEmployeeList ? "/employees" : "/dashboard"} />}
            >
              <ArrowLeft /> {canReadEmployeeList ? "Về danh sách" : "Về tổng quan"}
            </Button>
          </div>
        </header>

        <div className="mt-6">
          {displayedLoading && <DetailSkeleton />}

          {!displayedLoading && displayedError && (
            <Card>
              <CardHeader>
                <CardTitle>Không thể tải hồ sơ</CardTitle>
                <CardDescription>Kiểm tra quyền truy cập hoặc thử tải lại dữ liệu.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <RbacFeedback error={displayedError} />
                <Button type="button" onClick={reload} disabled={!hasValidEmployeeId}>
                  <RefreshCw /> Thử lại
                </Button>
              </CardContent>
            </Card>
          )}

          {!displayedLoading && !displayedError && detail && (
            <div className="grid gap-4 lg:grid-cols-3">
              <Card className="lg:col-span-2">
                <CardHeader>
                  <CardTitle className="flex items-center gap-2"><UserRound /> Thông tin hồ sơ</CardTitle>
                  <CardDescription>Thông tin cá nhân, liên hệ và quá trình làm việc cơ bản.</CardDescription>
                </CardHeader>
                <CardContent className="space-y-5">
                  <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                    <InfoField label="Mã nhân sự" value={detail.employeeCode} />
                    <InfoField label="Ngày sinh" value={formatDate(detail.dateOfBirth)} />
                    <InfoField label="Giới tính" value={detail.gender ? GENDER_LABELS[detail.gender] : null} />
                    <InfoField label="Email công việc" value={detail.workEmail} />
                    <InfoField label="Điện thoại" value={detail.phone} />
                    <InfoField label="Ngày vào làm" value={formatDate(detail.hireDate)} />
                    <InfoField label="Ngày nghỉ việc" value={formatDate(detail.terminationDate)} />
                  </dl>

                  <Separator />

                  <div>
                    <p className="flex items-center gap-2 text-sm font-medium"><GraduationCap /> Học vấn</p>
                    <dl className="mt-3 grid gap-4 sm:grid-cols-2">
                      <InfoField
                        label="Trình độ cao nhất"
                        value={detail.highestEducationLevel
                          ? EDUCATION_LEVEL_LABELS[detail.highestEducationLevel]
                          : null}
                      />
                      <InfoField
                        label="Năm tốt nghiệp"
                        value={detail.graduationYear ? String(detail.graduationYear) : null}
                      />
                      <InfoField label="Chuyên ngành" value={detail.major} />
                      <InfoField label="Trường / đơn vị đào tạo" value={detail.institution} />
                    </dl>
                  </div>
                </CardContent>
              </Card>

              <div className="grid content-start gap-4">
                {canManageCompensation && <Card>
                  <CardHeader>
                    <CardTitle>Lương nhân viên</CardTitle>
                    <CardDescription>Nhập mức lương ban đầu hoặc cập nhật lịch sử lương.</CardDescription>
                  </CardHeader>
                  <CardContent>
                    <Button nativeButton={false} render={<Link href={`/payslips?employeeId=${detail.id}&effectiveFrom=${detail.hireDate}`} />}>
                      Nhập và xem lương
                    </Button>
                  </CardContent>
                </Card>}
                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2"><KeyRound /> Tài khoản</CardTitle>
                  </CardHeader>
                  <CardContent>
                    {detail.account ? (
                      <dl className="grid gap-4">
                        <InfoField label="Tên đăng nhập" value={detail.account.username} />
                        <InfoField label="Email đăng nhập" value={detail.account.email} />
                        <div>
                          <dt className="text-xs text-muted-foreground">Trạng thái</dt>
                          <dd className="mt-1">
                            <Badge variant={detail.account.status === "ACTIVE" ? "default" : "outline"}>
                              {ACCOUNT_STATUS_LABELS[detail.account.status] ?? detail.account.status}
                            </Badge>
                          </dd>
                        </div>
                      </dl>
                    ) : (
                      <p className="text-sm text-muted-foreground">Nhân sự chưa được cấp tài khoản đăng nhập.</p>
                    )}
                  </CardContent>
                </Card>

                <Card>
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2"><BriefcaseBusiness /> Phân công hiện tại</CardTitle>
                  </CardHeader>
                  <CardContent>
                    {currentAssignment ? (
                      <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
                        <InfoField label="Đơn vị tổ chức" value={currentAssignment.organizationUnitName} />
                        <InfoField label="Chức danh" value={currentAssignment.positionTitle} />
                        <InfoField label="Địa điểm" value={currentAssignment.workLocationName} />
                        <InfoField label="Ca làm việc" value={currentAssignment.shiftName} />
                        <InfoField label="Quản lý trực tiếp" value={currentAssignment.managerEmployeeName} />
                        <InfoField
                          label="Loại hình"
                          value={EMPLOYMENT_TYPE_LABELS[currentAssignment.employmentType]}
                        />
                        <InfoField label="Hiệu lực từ" value={formatDate(currentAssignment.effectiveFrom)} />
                      </dl>
                    ) : (
                      <p className="text-sm text-muted-foreground">Chưa có phân công đang hiệu lực.</p>
                    )}
                  </CardContent>
                </Card>
              </div>

              {canReadAssignments && <Card className="lg:col-span-3">
                <CardHeader>
                  <CardTitle className="flex items-center gap-2"><Clock3 /> Lịch sử phân công</CardTitle>
                  <CardDescription>
                    Bao gồm phân công đã kết thúc, đang hiệu lực và được lên lịch trong tương lai.
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="overflow-x-auto rounded-xl border">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Thời gian</TableHead>
                          <TableHead>Đơn vị / chức danh</TableHead>
                          <TableHead>Địa điểm / ca</TableHead>
                          <TableHead>Quản lý</TableHead>
                          <TableHead>Loại hình</TableHead>
                          <TableHead>Trạng thái</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {assignments.map((assignment) => (
                          <TableRow key={assignment.id}>
                            <TableCell className="min-w-40">
                              <span className="flex items-center gap-1.5 text-sm">
                                <CalendarDays className="size-3.5 text-muted-foreground" />
                                {formatDate(assignment.effectiveFrom)}
                              </span>
                              <span className="mt-1 block text-xs text-muted-foreground">
                                đến {formatDate(assignment.effectiveTo)}
                              </span>
                            </TableCell>
                            <TableCell className="min-w-52">
                              <span className="flex items-center gap-1.5 font-medium">
                                <Building2 className="size-3.5 text-muted-foreground" />
                                {assignment.organizationUnitName}
                              </span>
                              <span className="mt-1 block text-xs text-muted-foreground">
                                {assignment.positionTitle}
                              </span>
                            </TableCell>
                            <TableCell className="min-w-48">
                              <span className="flex items-center gap-1.5 text-sm">
                                <MapPin className="size-3.5 text-muted-foreground" />
                                {assignment.workLocationName}
                              </span>
                              <span className="mt-1 block text-xs text-muted-foreground">
                                {assignment.shiftName || "Chưa xếp ca"}
                              </span>
                            </TableCell>
                            <TableCell className="min-w-40">
                              <span className="flex items-center gap-1.5 text-sm">
                                <UsersRound className="size-3.5 text-muted-foreground" />
                                {assignment.managerEmployeeName || "Chưa chỉ định"}
                              </span>
                            </TableCell>
                            <TableCell>
                              <Badge variant="outline">
                                {EMPLOYMENT_TYPE_LABELS[assignment.employmentType]}
                              </Badge>
                            </TableCell>
                            <TableCell>
                              <AssignmentBadge
                                assignment={assignment}
                                currentAssignmentId={currentAssignment?.id ?? null}
                              />
                            </TableCell>
                          </TableRow>
                        ))}

                        {assignments.length === 0 && (
                          <TableRow>
                            <TableCell colSpan={6} className="h-24 text-center text-muted-foreground">
                              Chưa có lịch sử phân công.
                            </TableCell>
                          </TableRow>
                        )}
                      </TableBody>
                    </Table>
                  </div>
                </CardContent>
              </Card>}
            </div>
          )}
        </div>
      </section>
    </main>
  )
}
