"use client"

import { useEffect, useMemo, useState } from "react"
import { ChevronLeft, ChevronRight } from "lucide-react"

import { useDebouncedValue } from "@/components/common/list-filters"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { AttendanceSection } from "@/components/workforce/attendance-section"
import { today, type WorkforceContext } from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import { findEmployeeByCode } from "@/lib/employee"

type Section = "attendance"

const SECTION_TEXT: Record<Section, { title: string; description: string }> = {
  attendance: { title: "Chấm công", description: "Lịch sử chấm công từ máy chấm công, bảng công tháng, tăng ca và ngày lễ công ty." },
}

/** Permissions that let the user look at someone other than themselves in each section. */
const OTHERS_PERMISSIONS: Record<Section, string[]> = {
  attendance: ["attendance.read", "attendance.manage"],
}

/** YYYY-MM shifted by a number of months. */
function shiftMonth(month: string, by: number): string {
  const date = new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)) - 1 + by, 1))
  return date.toISOString().slice(0, 7)
}

/** The employee a section shows, resolved from the code typed in the header. */
type SelectedEmployee = { id: number; employeeCode: string; fullName: string }

export function WorkforcePage({ section }: { section: Section }) {
  const { session } = usePortalSession()
  const self = session.account.employee
  // Employee pages link here with ?employeeCode=; otherwise start with the signed-in employee.
  // PortalShell only renders this page in the browser, after the session loads, so window is available.
  const [codeInput, setCodeInput] = useState(() =>
    new URLSearchParams(window.location.search).get("employeeCode")?.trim() || self?.employeeCode || "")
  const code = useDebouncedValue(codeInput.trim())
  // The last lookup: the code it was for, and the employee found (null when none matched).
  const [lookup, setLookup] = useState<{ code: string; employee: SelectedEmployee | null; error?: string } | null>(null)
  const [month, setMonth] = useState(today().slice(0, 7))

  const permissions = useMemo(
    () => new Set(session.account.permissions.map((permission) => permission.code)),
    [session],
  )

  const canUseSection = ["attendance.self.read", "attendance.read", "attendance.manage"].some((code) => permissions.has(code))
  const canViewOthers = OTHERS_PERMISSIONS[section].some((code) => permissions.has(code))
  // The signed-in employee needs no lookup; anyone else is found by code in the employee list.
  const isSelf = !!self && code.toLowerCase() === self.employeeCode.toLowerCase()
  const needsLookup = !isSelf && code !== "" && canViewOthers

  useEffect(() => {
    if (!needsLookup) return
    let active = true
    findEmployeeByCode("hrm", code)
      .then((found) => { if (active) setLookup({ code, employee: found }) })
      .catch((caught: unknown) => { if (active) setLookup({ code, employee: null, error: apiErrorMessage(caught) }) })
    return () => { active = false }
  }, [code, needsLookup])

  const selected: SelectedEmployee | null = isSelf ? self
    : needsLookup && lookup?.code === code ? lookup.employee
    : null
  const lookupMessage = needsLookup && lookup?.code === code && !lookup.employee
    ? lookup.error ?? `Không tìm thấy nhân viên có mã ${code}.`
    : null
  const employeeId = selected?.id ?? 0
  const employeeLabel = selected ? `${selected.employeeCode} · ${selected.fullName}` : "—"
  const context: WorkforceContext = { employeeId, employeeLabel, permissions, month }
  const { title, description } = SECTION_TEXT[section]

  return (
    <>
      <PageHeader title={title} description={description} alignActions="end" actions={canUseSection && (
        <div className="flex flex-wrap items-end gap-3">
          <div className="grid gap-1.5">
            <Label htmlFor="workforce-employee">Mã nhân viên</Label>
            <Input id="workforce-employee" className="w-36" placeholder="NV0001" value={codeInput}
              disabled={!canViewOthers} onChange={(event) => setCodeInput(event.target.value)} />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="workforce-month">Tháng</Label>
            <div className="flex items-center gap-1">
              <Button type="button" variant="outline" size="icon" aria-label="Tháng trước"
                onClick={() => setMonth(shiftMonth(month, -1))}><ChevronLeft /></Button>
              <Input id="workforce-month" type="month" className="w-44" value={month}
                onChange={(event) => { if (event.target.value) setMonth(event.target.value) }} />
              <Button type="button" variant="outline" size="icon" aria-label="Tháng sau"
                onClick={() => setMonth(shiftMonth(month, 1))}><ChevronRight /></Button>
            </div>
          </div>
        </div>
      )} />

      <div className="mt-6">
        {!canUseSection && <p role="alert">Tài khoản chưa có quyền truy cập mục này.</p>}
        {canUseSection && lookupMessage && <p role="alert" className="mb-4 text-sm text-destructive">{lookupMessage}</p>}
        {canUseSection && <AttendanceSection {...context} />}
      </div>
    </>
  )
}
