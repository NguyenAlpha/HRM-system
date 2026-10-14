"use client"

import { useEffect, useMemo, useState } from "react"
import { ArrowLeft, ChevronLeft, ChevronRight } from "lucide-react"

import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { AttendanceSection, OvertimeApprovals } from "@/components/workforce/attendance-section"
import { AttendanceTimesheet, type TimesheetEmployee } from "@/components/workforce/attendance-timesheet"
import { today, workforceApi } from "@/components/workforce/workforce-shared"
import type { CompanyHoliday, OvertimeApprovalItem } from "@/lib/workforce"

/** YYYY-MM shifted by a number of months. */
function shiftMonth(month: string, by: number): string {
  const date = new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)) - 1 + by, 1))
  return date.toISOString().slice(0, 7)
}

type Tab = "mine" | "sheet" | "overtime"

/**
 * Attendance: the signed-in employee's own month; for attendance.read the company timesheet, from
 * which one employee's month opens for review and corrections; and for whoever approves someone's
 * overtime, the days waiting for that decision.
 */
export function AttendancePage({ initialTab }: { initialTab?: Tab }) {
  const { session } = usePortalSession()
  const self = session.account.employee
  const permissions = useMemo(
    () => new Set(session.account.permissions.map((permission) => permission.code)),
    [session],
  )
  const canReadOwn = !!self && permissions.has("attendance.self.read")
  const canReadAll = permissions.has("attendance.read")
  const [month, setMonth] = useState(today().slice(0, 7))
  const defaultTab: Tab = canReadAll ? "sheet" : "mine"
  const [selectedTab, setTab] = useState<Tab>(initialTab ?? defaultTab)
  const [viewing, setViewing] = useState<TimesheetEmployee | null>(null)
  const [overtime, setOvertime] = useState<{ items: OvertimeApprovalItem[]; holidays: CompanyHoliday[] } | null>(null)
  const [overtimeRevision, setOvertimeRevision] = useState(0)
  const canReadHolidays = permissions.has("holiday.read")

  // Approval follows the reporting line, so anyone may have overtime to decide, whatever their permissions.
  useEffect(() => {
    let active = true
    workforceApi<OvertimeApprovalItem[]>("attendance/overtime/pending")
      .then(async (items) => {
        // The queue spans any month; its holidays set the minimum multiplier of each day.
        const dates = items.map((item) => item.record.workDate).sort()
        const holidays = canReadHolidays && dates.length > 0
          ? await workforceApi<CompanyHoliday[]>(`company-holidays?from=${dates[0]}&to=${dates[dates.length - 1]}`)
          : []
        if (active) setOvertime({ items, holidays })
      })
      .catch(() => { if (active) setOvertime({ items: [], holidays: [] }) })
    return () => { active = false }
  }, [canReadHolidays, overtimeRevision])

  const showOvertime = (overtime?.items.length ?? 0) > 0
  // The overtime tab disappears once its last day is decided (or when a link asked for it and nothing waits).
  const tab = selectedTab === "overtime" && overtime && !showOvertime ? defaultTab : selectedTab
  const tabCount = [canReadAll, canReadOwn, showOvertime].filter(Boolean).length

  return (
    <>
      <PageHeader
        title="Chấm công"
        description="Dữ liệu từ máy chấm công, bảng công tháng và tăng ca."
        alignActions="end"
        actions={(canReadOwn || canReadAll) && tab !== "overtime" && (
          <div className="grid gap-1.5">
            <Label htmlFor="attendance-month">Tháng</Label>
            <div className="flex items-center gap-1">
              <Button type="button" variant="outline" size="icon" aria-label="Tháng trước"
                onClick={() => setMonth(shiftMonth(month, -1))}><ChevronLeft /></Button>
              <Input id="attendance-month" type="month" className="w-44" value={month}
                onChange={(event) => { if (event.target.value) setMonth(event.target.value) }} />
              <Button type="button" variant="outline" size="icon" aria-label="Tháng sau"
                onClick={() => setMonth(shiftMonth(month, 1))}><ChevronRight /></Button>
            </div>
          </div>
        )}
      />

      <div className="mt-6 grid gap-4">
        {!canReadOwn && !canReadAll && !showOvertime && <p role="alert">Tài khoản chưa có quyền truy cập mục này.</p>}

        {tabCount > 1 && (
          <Tabs value={tab} onValueChange={(value) => setTab(value as Tab)}>
            <TabsList>
              {canReadAll && <TabsTrigger value="sheet">Bảng công tổng hợp</TabsTrigger>}
              {canReadOwn && <TabsTrigger value="mine">Bảng công của tôi</TabsTrigger>}
              {showOvertime && (
                <TabsTrigger value="overtime">Tăng ca chờ duyệt <Badge className="ml-1">{overtime?.items.length}</Badge></TabsTrigger>
              )}
            </TabsList>
          </Tabs>
        )}

        {(tab === "overtime" || (!canReadOwn && !canReadAll)) && showOvertime && overtime && (
          <OvertimeApprovals items={overtime.items} holidays={overtime.holidays}
            onDecided={() => setOvertimeRevision((value) => value + 1)} />
        )}

        {tab === "mine" && canReadOwn && self && (
          <AttendanceSection employeeId={self.id} employeeLabel={`${self.employeeCode} · ${self.fullName}`}
            permissions={permissions} month={month} />
        )}

        {tab === "sheet" && canReadAll && !viewing && <AttendanceTimesheet month={month} onOpen={setViewing} />}
        {tab === "sheet" && canReadAll && viewing && (
          <>
            <div className="flex flex-wrap items-center gap-3">
              <Button type="button" variant="outline" size="sm" onClick={() => setViewing(null)}>
                <ArrowLeft /> Bảng công tổng hợp
              </Button>
              <span className="font-medium">{viewing.employeeCode} · {viewing.fullName}</span>
            </div>
            <AttendanceSection employeeId={viewing.id} employeeLabel={`${viewing.employeeCode} · ${viewing.fullName}`}
              permissions={permissions} month={month} />
          </>
        )}
      </div>
    </>
  )
}
