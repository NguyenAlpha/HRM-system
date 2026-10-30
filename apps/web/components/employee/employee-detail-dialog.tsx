"use client"

import Link from "next/link"
import { useEffect, useState } from "react"
import { ExternalLink, KeyRound, Pencil } from "lucide-react"

import { ErrorAlert } from "@/components/common/list-controls"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { apiErrorMessage } from "@/lib/api-helpers"
import {
  EDUCATION_LEVEL_LABELS,
  employeeRequest,
  EMPLOYMENT_STATUS_LABELS,
  GENDER_LABELS,
  isSessionExpired,
  type EmployeeDetail,
} from "@/lib/employee"
import { PORTAL_CONFIG } from "@/lib/auth/config"
import type { Portal } from "@/lib/auth/types"
import { formatDate } from "@/lib/format-date"

function Field({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 truncate text-sm font-medium">{value || "—"}</dd>
    </div>
  )
}

export function EmployeeDetailDialog({ portal, employeeId, canUpdate, canProvisionAccount, onClose, onEdit, onProvision, onSessionExpired }: {
  portal: Portal
  employeeId: number
  canUpdate: boolean
  canProvisionAccount: boolean
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
    employeeRequest<EmployeeDetail>(portal, `/${employeeId}`)
      .then((result) => {
        if (active) setDetail(result)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isSessionExpired(caught)) onSessionExpired()
        setError(apiErrorMessage(caught))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [portal, employeeId, onSessionExpired])

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader className="flex-row items-center gap-3 space-y-0 pr-6">
          <span className="avatar shrink-0">{(detail?.fullName ?? "?").slice(0, 1).toUpperCase()}</span>
          <div className="min-w-0">
            <DialogTitle className="truncate">{detail?.fullName ?? "Hồ sơ nhân sự"}</DialogTitle>
            <DialogDescription>
              {detail ? `${detail.employeeCode} · ${EMPLOYMENT_STATUS_LABELS[detail.employmentStatus]}` : "Đang tải..."}
            </DialogDescription>
          </div>
        </DialogHeader>

        <div className="max-h-[65vh] space-y-5 overflow-y-auto py-1">
          <ErrorAlert error={error} />

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
                  <Field label="Ngày sinh" value={formatDate(detail.dateOfBirth)} />
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
                  <Field label="Ngày vào làm" value={formatDate(detail.hireDate)} />
                  <Field label="Ngày nghỉ việc" value={formatDate(detail.terminationDate)} />
                </dl>
                {detail.currentAssignment ? (
                  <dl className="mt-3 grid grid-cols-2 gap-3">
                    <Field label="Đơn vị tổ chức" value={detail.currentAssignment.organizationUnitName} />
                    <Field label="Địa điểm làm việc" value={detail.currentAssignment.workLocationName} />
                    <Field label="Vị trí công việc" value={detail.currentAssignment.positionTitle} />
                    <Field label="Hiệu lực từ" value={formatDate(detail.currentAssignment.effectiveFrom)} />
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
                        <Badge variant={detail.account.status === "ACTIVE" ? "secondary" : "outline"}>
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

        <DialogFooter>
          <DialogClose render={<Button type="button" variant="outline" />}>Đóng</DialogClose>
          {detail && (
            <>
              <Button variant="outline" nativeButton={false}
                render={<Link href={`${PORTAL_CONFIG[portal].pagePrefix}/employees/${detail.id}`} />}>
                <ExternalLink /> Xem chi tiết
              </Button>
              {canUpdate && (
                <Button type="button" variant="outline" onClick={() => onEdit(detail)}>
                  <Pencil /> Sửa hồ sơ
                </Button>
              )}
              {canProvisionAccount && !detail.account && (
                <Button type="button" onClick={() => onProvision(detail)}>
                  <KeyRound /> Cấp tài khoản
                </Button>
              )}
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
