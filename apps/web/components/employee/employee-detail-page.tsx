"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useEffect, useState } from "react"
import { ArrowLeft, KeyRound, LogOut, Pencil, RefreshCw, Trash2 } from "lucide-react"

import { ErrorAlert } from "@/components/common/list-controls"
import { ActionsMenu, RowActionItem, RowActionLink } from "@/components/common/row-actions"
import { usePortalSession } from "@/components/portal/portal-shell"
import { EmployeeCompensationCard } from "@/components/employee/employee-compensation-card"
import { EmployeeDeleteDialog } from "@/components/employee/employee-delete-dialog"
import { EmployeeEditDialog } from "@/components/employee/employee-edit-dialog"
import { EmployeeEmploymentEndDialog } from "@/components/employee/employee-employment-end-dialog"
import { EmployeeSensitiveCard } from "@/components/employee/employee-sensitive-card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
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
import {
  EDUCATION_LEVEL_LABELS,
  employeeErrorMessage,
  employeeRequest,
  EMPLOYMENT_STATUS_LABELS,
  EMPLOYMENT_TYPE_LABELS,
  GENDER_LABELS,
  isEmploymentEnded,
  isSessionExpired,
  type EmployeeAssignment,
  type EmployeeDetail,
} from "@/lib/employee"
import { PORTAL_CONFIG } from "@/lib/auth/config"

const ACCOUNT_STATUS_LABELS: Record<string, string> = {
  PENDING: "Chờ kích hoạt",
  ACTIVE: "Đang hoạt động",
  LOCKED: "Đang khóa",
  DISABLED: "Đã vô hiệu hóa",
}

export function formatDate(value: string | null | undefined) {
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

export function InfoField({ label, value }: { label: string; value: string | null | undefined }) {
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
  const { portal, session, onSessionExpired } = usePortalSession()
  const [detail, setDetail] = useState<EmployeeDetail | null>(null)
  const [assignments, setAssignments] = useState<EmployeeAssignment[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [endingEmployment, setEndingEmployment] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [editing, setEditing] = useState(false)

  const numericEmployeeId = Number(employeeId)
  const hasValidEmployeeId = /^\d+$/.test(employeeId)
    && Number.isSafeInteger(numericEmployeeId)
    && numericEmployeeId > 0

  useEffect(() => {
    if (!hasValidEmployeeId) return

    let active = true
    const canReadAssignments = session.account.permissions.some(
      (permission) => permission.code === "employee.assignment.read",
    )
    Promise.all([
      employeeRequest<EmployeeDetail>(portal, `/${numericEmployeeId}`),
      canReadAssignments
        ? employeeRequest<EmployeeAssignment[]>(portal, `/${numericEmployeeId}/assignments`)
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
  }, [hasValidEmployeeId, numericEmployeeId, onSessionExpired, portal, revision, session])

  function reload() {
    setLoading(true)
    setError(null)
    setRevision((value) => value + 1)
  }

  const currentAssignment = detail?.currentAssignment ?? null
  const canReadAssignments = session.account.permissions.some(
    (permission) => permission.code === "employee.assignment.read",
  )
  const canReadEmployeeList = session.account.permissions.some(
    (permission) => permission.code === "employee.list.read",
  )
  const backHref = canReadEmployeeList ? `${PORTAL_CONFIG[portal].pagePrefix}/employees` : PORTAL_CONFIG[portal].homePath
  // The account detail page needs account.read; without it the shortcut is hidden.
  const canReadAccount = session.account.permissions.some((permission) => permission.code === "account.read")
  const permissions = new Set(session.account.permissions.map((permission) => permission.code))
  // Salary data goes through the HRM workforce proxy, so the Admin Console never shows it.
  const canReadCompensation = portal === "hrm" && permissions.has("compensation.read")
  const canReadSensitive = session.account.permissions.some(
    (permission) => permission.code === "employee.sensitive.read",
  )
  const canManageSensitive = session.account.permissions.some(
    (permission) => permission.code === "employee.sensitive.manage",
  )
  const canManageLifecycle = session.account.permissions.some(
    (permission) => permission.code === "employee.lifecycle.manage",
  )
  const canUpdateEmployee = session.account.permissions.some((permission) => permission.code === "employee.update")
  const canDeleteEmployee = session.account.permissions.some(
    (permission) => permission.code === "employee.delete",
  )
  const displayedError = hasValidEmployeeId ? error : "Mã nhân sự trên đường dẫn không hợp lệ."
  const displayedLoading = hasValidEmployeeId && loading

  return (
    <>
      <Link href={backHref} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" /> {canReadEmployeeList ? "Danh sách nhân sự" : "Tổng quan"}
      </Link>

      <header className="mt-4 flex flex-wrap items-start justify-between gap-4">
        <div className="flex min-w-0 items-center gap-3">
          <span className="avatar shrink-0 text-lg">
            {(detail?.fullName ?? "?").slice(0, 1).toUpperCase()}
          </span>
          <div className="min-w-0">
            <h1 className="truncate text-3xl font-semibold tracking-tight">
              {detail?.fullName ?? "Chi tiết nhân sự"}
            </h1>
            <div className="mt-2 flex flex-wrap items-center gap-2">
              {detail && <code className="text-sm text-muted-foreground">{detail.employeeCode}</code>}
              {detail && (
                <Badge variant={detail.employmentStatus === "ACTIVE" ? "secondary" : "outline"}>
                  {EMPLOYMENT_STATUS_LABELS[detail.employmentStatus]}
                </Badge>
              )}
            </div>
          </div>
        </div>
        {/* One visible action; the rarer ones sit in the "…" menu. */}
        <div className="flex flex-wrap items-center gap-2">
          {detail && canUpdateEmployee && (
            <Button type="button" variant="outline" onClick={() => setEditing(true)}>
              <Pencil /> Sửa hồ sơ
            </Button>
          )}
          <ActionsMenu label={detail?.fullName ?? "hồ sơ"} outline>
            {detail?.account && canReadAccount && (
              <RowActionLink icon={KeyRound} href={`${PORTAL_CONFIG[portal].pagePrefix}/accounts/${detail.account.id}`}>
                Xem tài khoản
              </RowActionLink>
            )}
            <RowActionItem icon={RefreshCw} onClick={reload} disabled={displayedLoading}>Tải lại</RowActionItem>
            {detail && canManageLifecycle && !isEmploymentEnded(detail.employmentStatus) && (
              <RowActionItem icon={LogOut} onClick={() => setEndingEmployment(true)}>Kết thúc làm việc</RowActionItem>
            )}
            {detail && canDeleteEmployee && (
              <RowActionItem icon={Trash2} destructive onClick={() => setDeleting(true)}>Xóa hồ sơ</RowActionItem>
            )}
          </ActionsMenu>
        </div>
      </header>

      <div className="mt-8">
        {displayedLoading && <DetailSkeleton />}

        {!displayedLoading && displayedError && (
          <Card>
            <CardHeader>
              <CardTitle>Không thể tải hồ sơ</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <ErrorAlert error={displayedError} />
              <Button type="button" onClick={reload} disabled={!hasValidEmployeeId}>
                <RefreshCw /> Thử lại
              </Button>
            </CardContent>
          </Card>
        )}

        {!displayedLoading && !displayedError && detail && (
          <div className="grid gap-6 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <CardHeader>
                <CardTitle>Thông tin hồ sơ</CardTitle>
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
                  <p className="text-sm font-medium">Học vấn</p>
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

            <div className="grid content-start gap-6">
              <Card>
                <CardHeader>
                  <CardTitle>Tài khoản</CardTitle>
                </CardHeader>
                <CardContent>
                  {detail.account ? (
                    <dl className="grid gap-4">
                      <InfoField label="Tên đăng nhập" value={detail.account.username} />
                      <InfoField label="Email đăng nhập" value={detail.account.email} />
                      <div>
                        <dt className="text-xs text-muted-foreground">Trạng thái</dt>
                        <dd className="mt-1">
                          <Badge variant={detail.account.status === "ACTIVE" ? "secondary" : "outline"}>
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
                  <CardTitle>Phân công hiện tại</CardTitle>
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

            {canReadCompensation && (
              <div className="lg:col-span-3">
                <EmployeeCompensationCard employeeId={detail.id} hireDate={detail.hireDate} permissions={permissions} />
              </div>
            )}

            {canReadSensitive && (
              <div className="lg:col-span-3">
                <EmployeeSensitiveCard
                  employee={detail}
                  canManage={canManageSensitive}
                  onSessionExpired={onSessionExpired}
                />
              </div>
            )}

            {canReadAssignments && <Card className="lg:col-span-3">
              <CardHeader>
                <CardTitle>Lịch sử phân công</CardTitle>
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
                            <span className="text-sm">{formatDate(assignment.effectiveFrom)}</span>
                            <span className="mt-1 block text-xs text-muted-foreground">
                              đến {formatDate(assignment.effectiveTo)}
                            </span>
                          </TableCell>
                          <TableCell className="min-w-52">
                            <span className="font-medium">{assignment.organizationUnitName}</span>
                            <span className="mt-1 block text-xs text-muted-foreground">
                              {assignment.positionTitle}
                            </span>
                          </TableCell>
                          <TableCell className="min-w-48">
                            <span className="text-sm">{assignment.workLocationName}</span>
                            <span className="mt-1 block text-xs text-muted-foreground">
                              {assignment.shiftName || "Chưa xếp ca"}
                            </span>
                          </TableCell>
                          <TableCell className="min-w-40">
                            <span className="text-sm">{assignment.managerEmployeeName || "Chưa chỉ định"}</span>
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

      {endingEmployment && detail && (
        <EmployeeEmploymentEndDialog
          employee={detail}
          onClose={() => setEndingEmployment(false)}
          onCompleted={reload}
          onSessionExpired={onSessionExpired}
        />
      )}

      {editing && detail && (
        <EmployeeEditDialog
          portal={portal}
          detail={detail}
          onClose={() => setEditing(false)}
          onSaved={reload}
          onSessionExpired={onSessionExpired}
        />
      )}

      {deleting && detail && (
        <EmployeeDeleteDialog
          employee={detail}
          onClose={() => setDeleting(false)}
          onDeleted={() => router.replace(backHref)}
          onSessionExpired={onSessionExpired}
        />
      )}
    </>
  )
}
