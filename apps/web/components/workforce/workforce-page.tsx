"use client"

import { useMemo, useState } from "react"
import { ChevronLeft, ChevronRight } from "lucide-react"

import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { AttendanceSection } from "@/components/workforce/attendance-section"
import { LeaveSection } from "@/components/workforce/leave-section"
import { PayrollSection } from "@/components/workforce/payroll-section"
import { today, type WorkforceContext } from "@/components/workforce/workforce-shared"

type Section = "attendance" | "leave" | "payroll"

const SECTION_TEXT: Record<Section, { title: string; description: string }> = {
  attendance: { title: "Chấm công", description: "Lịch sử chấm công từ máy chấm công, bảng công tháng, tăng ca và ngày lễ công ty." },
  leave: { title: "Đơn nghỉ phép", description: "Số dư phép năm, tạo và duyệt đơn nghỉ." },
  payroll: { title: "Phiếu lương", description: "Kỳ lương, phiếu lương, lương cơ bản, bảo hiểm và người phụ thuộc." },
}

/** Permissions that let the user look at someone other than themselves in each section. */
const OTHERS_PERMISSIONS: Record<Section, string[]> = {
  attendance: ["attendance.read", "attendance.manage"],
  leave: ["request.read", "request.manage"],
  payroll: ["report.payroll.read", "compensation.read", "compensation.manage"],
}

/** YYYY-MM shifted by a number of months. */
function shiftMonth(month: string, by: number): string {
  const date = new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)) - 1 + by, 1))
  return date.toISOString().slice(0, 7)
}

export function WorkforcePage({ section }: { section: Section }) {
  const { session } = usePortalSession()
  // Employee pages link here with ?employeeId=; otherwise start with the signed-in employee.
  // PortalShell only renders this page in the browser, after the session loads, so window is available.
  const [employeeInput, setEmployeeInput] = useState(() => {
    const requested = new URLSearchParams(window.location.search).get("employeeId")
    if (requested && /^[1-9]\d*$/.test(requested)) return requested
    return session.account.employee ? String(session.account.employee.id) : ""
  })
  const [month, setMonth] = useState(today().slice(0, 7))

  const permissions = useMemo(
    () => new Set(session.account.permissions.map((permission) => permission.code)),
    [session],
  )

  const canUseSection = section === "attendance"
    ? ["attendance.self.read", "attendance.read", "attendance.manage"].some((code) => permissions.has(code))
    : section === "leave"
      ? ["request.self.read", "request.read", "request.self.create"].some((code) => permissions.has(code))
      : ["payroll.self.read", "report.payroll.read", "payroll.calculate", "compensation.read", "compensation.manage"]
        .some((code) => permissions.has(code))
  const canViewOthers = OTHERS_PERMISSIONS[section].some((code) => permissions.has(code))
  const employeeId = /^[1-9]\d*$/.test(employeeInput) ? Number(employeeInput) : 0
  const context: WorkforceContext = { employeeId, permissions, month }
  const { title, description } = SECTION_TEXT[section]

  return (
    <>
      <PageHeader title={title} description={description} alignActions="end" actions={canUseSection && (
        <div className="flex flex-wrap items-end gap-3">
          <div className="grid gap-1.5">
            <Label htmlFor="workforce-employee">Mã nhân viên (ID)</Label>
            <Input id="workforce-employee" type="number" min="1" className="w-36" value={employeeInput}
              disabled={!canViewOthers} onChange={(event) => setEmployeeInput(event.target.value)} />
          </div>
          {section !== "payroll" && (
            <div className="grid gap-1.5">
              <Label htmlFor="workforce-month">{section === "leave" ? "Năm phép (theo tháng)" : "Tháng"}</Label>
              <div className="flex items-center gap-1">
                <Button type="button" variant="outline" size="icon" aria-label="Tháng trước"
                  onClick={() => setMonth(shiftMonth(month, -1))}><ChevronLeft /></Button>
                <Input id="workforce-month" type="month" className="w-44" value={month}
                  onChange={(event) => { if (event.target.value) setMonth(event.target.value) }} />
                <Button type="button" variant="outline" size="icon" aria-label="Tháng sau"
                  onClick={() => setMonth(shiftMonth(month, 1))}><ChevronRight /></Button>
              </div>
            </div>
          )}
        </div>
      )} />

      <div className="mt-6">
        {!canUseSection && <p role="alert">Tài khoản chưa có quyền truy cập mục này.</p>}
        {canUseSection && section === "attendance" && <AttendanceSection {...context} />}
        {canUseSection && section === "leave" && <LeaveSection {...context} />}
        {canUseSection && section === "payroll" && <PayrollSection {...context} />}
      </div>
    </>
  )
}
