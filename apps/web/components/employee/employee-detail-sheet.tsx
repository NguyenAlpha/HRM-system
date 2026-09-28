"use client"

import { useEffect, useState } from "react"
import { KeyRound, Pencil } from "lucide-react"

import { RbacFeedback } from "@/components/admin/rbac-controls"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import {
  EDUCATION_LEVEL_LABELS,
  employeeErrorMessage,
  employeeRequest,
  EMPLOYMENT_STATUS_LABELS,
  GENDER_LABELS,
  isSessionExpired,
  type EmployeeDetail,
} from "@/lib/employee"

function Field({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 truncate text-sm font-medium">{value || "—"}</dd>
    </div>
  )
}

export function EmployeeDetailSheet({ employeeId, canManage, onClose, onEdit, onProvision, onSessionExpired }: {
  employeeId: number
  canManage: boolean
  onClose: () => void
  onEdit: (detail: EmployeeDetail) => void
  onProvision: (detail: EmployeeDetail) => void
  onSessionExpired: () => void
}) {
  const [detail, setDetail] = useState<EmployeeDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    employeeRequest<EmployeeDetail>(`/${employeeId}`)
      .then((result) => {
        if (active) setDetail(result)
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
  }, [employeeId, onSessionExpired])

  return (
    <Sheet open onOpenChange={(open) => { if (!open) onClose() }}>
      <SheetContent className="flex flex-col gap-0 overflow-y-auto p-0 sm:max-w-md">
        <SheetHeader className="border-b">
          <SheetTitle>{detail?.fullName ?? "Hồ sơ nhân sự"}</SheetTitle>
          <SheetDescription>
            {detail ? `${detail.employeeCode} · ${EMPLOYMENT_STATUS_LABELS[detail.employmentStatus]}` : "Đang tải..."}
          </SheetDescription>
        </SheetHeader>

        <div className="flex-1 space-y-5 p-4">
          <RbacFeedback error={error} />

          {loading && (
            <div className="grid gap-3">
              {Array.from({ length: 6 }).map((_, index) => <Skeleton key={index} className="h-10 w-full" />)}
            </div>
          )}

          {!loading && detail && (
            <>
              <section>
                <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Liên hệ</p>
                <dl className="mt-2 grid grid-cols-2 gap-3">
                  <Field label="Email công việc" value={detail.workEmail} />
                  <Field label="Điện thoại" value={detail.phone} />
                </dl>
              </section>

              <Separator />

              <section>
                <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Cá nhân</p>
                <dl className="mt-2 grid grid-cols-2 gap-3">
                  <Field label="Ngày sinh" value={detail.dateOfBirth} />
                  <Field label="Giới tính" value={detail.gender ? GENDER_LABELS[detail.gender] : null} />
                </dl>
              </section>

              <Separator />

              <section>
                <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Học vấn</p>
                <dl className="mt-2 grid grid-cols-2 gap-3">
                  <Field label="Trình độ" value={detail.highestEducationLevel ? EDUCATION_LEVEL_LABELS[detail.highestEducationLevel] : null} />
                  <Field label="Năm tốt nghiệp" value={detail.graduationYear ? String(detail.graduationYear) : null} />
                  <Field label="Chuyên ngành" value={detail.major} />
                  <Field label="Trường / đơn vị đào tạo" value={detail.institution} />
                </dl>
              </section>

              <Separator />

              <section>
                <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Công việc</p>
                <dl className="mt-2 grid grid-cols-2 gap-3">
                  <Field label="Ngày vào làm" value={detail.hireDate} />
                  <Field label="Ngày nghỉ việc" value={detail.terminationDate} />
                </dl>
                {detail.currentAssignment ? (
                  <dl className="mt-3 grid grid-cols-2 gap-3">
                    <Field label="Đơn vị tổ chức" value={`#${detail.currentAssignment.organizationUnitId}`} />
                    <Field label="Địa điểm làm việc" value={`#${detail.currentAssignment.workLocationId}`} />
                    <Field label="Vị trí công việc" value={`#${detail.currentAssignment.positionId}`} />
                    <Field label="Hiệu lực từ" value={detail.currentAssignment.effectiveFrom} />
                  </dl>
                ) : (
                  <p className="mt-2 text-sm text-muted-foreground">Chưa có phân công hiện tại.</p>
                )}
              </section>

              <Separator />

              <section>
                <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Tài khoản đăng nhập</p>
                {detail.account ? (
                  <dl className="mt-2 grid grid-cols-2 gap-3">
                    <Field label="Username" value={detail.account.username} />
                    <div className="min-w-0">
                      <dt className="text-xs text-muted-foreground">Trạng thái</dt>
                      <dd className="mt-1">
                        <Badge variant={detail.account.status === "ACTIVE" ? "default" : "outline"}>
                          {detail.account.status}
                        </Badge>
                      </dd>
                    </div>
                  </dl>
                ) : (
                  <p className="mt-2 text-sm text-muted-foreground">Chưa được cấp tài khoản đăng nhập.</p>
                )}
              </section>
            </>
          )}
        </div>

        {canManage && detail && (
          <SheetFooter className="flex-row border-t">
            <Button type="button" variant="outline" onClick={() => onEdit(detail)}>
              <Pencil /> Sửa hồ sơ
            </Button>
            {!detail.account && (
              <Button type="button" onClick={() => onProvision(detail)}>
                <KeyRound /> Cấp tài khoản
              </Button>
            )}
          </SheetFooter>
        )}
      </SheetContent>
    </Sheet>
  )
}
