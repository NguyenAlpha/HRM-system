"use client"

import { useMemo, useState } from "react"
import { ArrowLeft, ChevronLeft, ChevronRight } from "lucide-react"

import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { AttendanceSection } from "@/components/workforce/attendance-section"
import { AttendanceTimesheet, type TimesheetEmployee } from "@/components/workforce/attendance-timesheet"
import { today } from "@/components/workforce/workforce-shared"

/** YYYY-MM shifted by a number of months. */
function shiftMonth(month: string, by: number): string {
  const date = new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)) - 1 + by, 1))
  return date.toISOString().slice(0, 7)
}

type Tab = "mine" | "sheet"

/**
 * Attendance: the signed-in employee's own month, and for attendance.read the company timesheet,
 * from which one employee's month opens for review and corrections.
 */
export function AttendancePage() {
  const { session } = usePortalSession()
  const self = session.account.employee
  const permissions = useMemo(
    () => new Set(session.account.permissions.map((permission) => permission.code)),
    [session],
  )
  const canReadOwn = !!self && permissions.has("attendance.self.read")
  const canReadAll = permissions.has("attendance.read")
  const [month, setMonth] = useState(today().slice(0, 7))
  const [tab, setTab] = useState<Tab>(canReadAll ? "sheet" : "mine")
  const [viewing, setViewing] = useState<TimesheetEmployee | null>(null)

  return (
    <>
      <PageHeader
        title="Chấm công"
        description="Dữ liệu từ máy chấm công, bảng công tháng và tăng ca."
        alignActions="end"
        actions={(canReadOwn || canReadAll) && (
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
        {!canReadOwn && !canReadAll && <p role="alert">Tài khoản chưa có quyền truy cập mục này.</p>}

        {canReadOwn && canReadAll && (
          <Tabs value={tab} onValueChange={(value) => setTab(value as Tab)}>
            <TabsList>
              <TabsTrigger value="sheet">Bảng công tổng hợp</TabsTrigger>
              <TabsTrigger value="mine">Bảng công của tôi</TabsTrigger>
            </TabsList>
          </Tabs>
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
