"use client"

import { useCallback, useEffect, useState } from "react"
import { AlertTriangle, Banknote, CalendarDays, GraduationCap, RefreshCw, UsersRound } from "lucide-react"

import { DistributionChart } from "@/components/report/distribution-chart"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { EMPLOYMENT_STATUSES, EMPLOYMENT_STATUS_LABELS, type EmploymentStatus } from "@/lib/employee"
import {
  getOrganizationUnitOptions,
  getWorkLocationOptions,
  type OrganizationUnitOption,
  type WorkLocationOption,
} from "@/lib/reference"
import {
  getHrWorkforceReport,
  getPayrollSalaryReport,
  type HrWorkforceReport,
  type PayrollSalaryReport,
  type ReportFilters,
} from "@/lib/report"

function localToday(): string {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, "0")
  const day = String(now.getDate()).padStart(2, "0")
  return `${year}-${month}-${day}`
}

const INITIAL_FILTERS: ReportFilters = {
  asOfDate: localToday(),
  organizationUnitId: "",
  workLocationId: "",
  employmentStatus: "",
}

const money = new Intl.NumberFormat("vi-VN", {
  style: "currency",
  currency: "VND",
  maximumFractionDigits: 0,
})

export function WorkforceReportDashboard({
  canReadHr,
  canReadPayroll,
  canReadOrganization,
  onSessionExpired,
}: {
  canReadHr: boolean
  canReadPayroll: boolean
  canReadOrganization: boolean
  onSessionExpired: () => void
}) {
  const [filters, setFilters] = useState<ReportFilters>(INITIAL_FILTERS)
  const [hrReport, setHrReport] = useState<HrWorkforceReport | null>(null)
  const [salaryReport, setSalaryReport] = useState<PayrollSalaryReport | null>(null)
  const [units, setUnits] = useState<OrganizationUnitOption[]>([])
  const [locations, setLocations] = useState<WorkLocationOption[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadReports = useCallback(async (nextFilters: ReportFilters) => {
    setLoading(true)
    setError(null)
    const requests: Promise<void>[] = []
    if (canReadHr) {
      requests.push(getHrWorkforceReport(nextFilters).then(setHrReport))
    }
    if (canReadPayroll) {
      requests.push(getPayrollSalaryReport(nextFilters).then(setSalaryReport))
    }
    try {
      await Promise.all(requests)
    } catch (caught) {
      const message = apiErrorMessage(caught)
      setError(message)
      if (isPortalSessionExpired(caught)) onSessionExpired()
    } finally {
      setLoading(false)
    }
  }, [canReadHr, canReadPayroll, onSessionExpired])

  useEffect(() => {
    let active = true
    const requests: Promise<void>[] = []
    if (canReadHr) {
      requests.push(getHrWorkforceReport(INITIAL_FILTERS).then((report) => {
        if (active) setHrReport(report)
      }))
    }
    if (canReadPayroll) {
      requests.push(getPayrollSalaryReport(INITIAL_FILTERS).then((report) => {
        if (active) setSalaryReport(report)
      }))
    }
    Promise.all(requests)
      .catch((caught) => {
        if (!active) return
        const message = apiErrorMessage(caught)
        setError(message)
        if (isPortalSessionExpired(caught)) onSessionExpired()
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [canReadHr, canReadPayroll, onSessionExpired])

  useEffect(() => {
    if (!canReadOrganization) return
    Promise.all([
      getOrganizationUnitOptions("hrm"),
      getWorkLocationOptions("hrm"),
    ]).then(([nextUnits, nextLocations]) => {
      setUnits(nextUnits)
      setLocations(nextLocations)
    }).catch(() => {
      // Report remains usable with date and status filters.
    })
  }, [canReadOrganization])

  const totalEmployees = hrReport?.totalEmployees ?? salaryReport?.totalEmployees ?? 0

  return (
    <div className="grid gap-4">
      <Card>
        <CardHeader>
          <CardTitle>Bộ lọc báo cáo</CardTitle>
          <CardDescription>Số liệu được tính theo hồ sơ và phân công có hiệu lực tại ngày đã chọn.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="grid gap-4 md:grid-cols-2 xl:grid-cols-5"
            onSubmit={(event) => {
              event.preventDefault()
              void loadReports(filters)
            }}
          >
            <div className="grid gap-2">
              <Label htmlFor="report-date">Ngày thống kê</Label>
              <Input
                id="report-date"
                type="date"
                max={localToday()}
                value={filters.asOfDate}
                onChange={(event) => setFilters((current) => ({ ...current, asOfDate: event.target.value }))}
                required
              />
            </div>

            {canReadOrganization && (
              <>
                <div className="grid gap-2">
                  <Label>Đơn vị tổ chức</Label>
                  <Select
                    value={filters.organizationUnitId || "all"}
                    onValueChange={(value) => setFilters((current) => ({
                      ...current,
                      organizationUnitId: value === "all" ? "" : String(value),
                    }))}
                  >
                    <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">Tất cả đơn vị</SelectItem>
                      {units.map((unit) => <SelectItem key={unit.id} value={String(unit.id)}>{unit.name}</SelectItem>)}
                    </SelectContent>
                  </Select>
                </div>
                <div className="grid gap-2">
                  <Label>Địa điểm làm việc</Label>
                  <Select
                    value={filters.workLocationId || "all"}
                    onValueChange={(value) => setFilters((current) => ({
                      ...current,
                      workLocationId: value === "all" ? "" : String(value),
                    }))}
                  >
                    <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">Tất cả địa điểm</SelectItem>
                      {locations.map((location) => (
                        <SelectItem key={location.id} value={String(location.id)}>{location.name}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </>
            )}

            <div className="grid gap-2">
              <Label>Trạng thái hiện tại</Label>
              <Select
                value={filters.employmentStatus || "all"}
                onValueChange={(value) => setFilters((current) => ({
                  ...current,
                  employmentStatus: value === "all" ? "" : value as EmploymentStatus,
                }))}
              >
                <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">Tất cả trạng thái</SelectItem>
                  {EMPLOYMENT_STATUSES.map((status) => (
                    <SelectItem key={status} value={status}>{EMPLOYMENT_STATUS_LABELS[status]}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="flex items-end">
              <Button className="w-full" type="submit" disabled={loading}>
                <RefreshCw className={loading ? "animate-spin" : ""} />
                {loading ? "Đang tải" : "Áp dụng"}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {error && (
        <Alert variant="destructive">
          <AlertTriangle />
          <AlertTitle>Không thể tải đầy đủ báo cáo</AlertTitle>
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <SummaryCard icon={<UsersRound />} label="Tổng nhân viên" value={totalEmployees.toLocaleString("vi-VN")} />
        {canReadHr && (
          <SummaryCard
            icon={<CalendarDays />}
            label="Thâm niên trung bình"
            value={`${Number(hrReport?.averageSeniorityYears ?? 0).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} năm`}
          />
        )}
        {canReadPayroll && (
          <>
            <SummaryCard
              icon={<Banknote />}
              label="Lương cơ bản trung bình"
              value={salaryReport?.averageBasicSalary == null ? "—" : money.format(salaryReport.averageBasicSalary)}
            />
            <SummaryCard
              icon={<AlertTriangle />}
              label="Chưa cấu hình lương"
              value={(salaryReport?.missingBasicSalary ?? 0).toLocaleString("vi-VN")}
            />
          </>
        )}
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        {canReadHr && (
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2"><GraduationCap className="size-5" /> Trình độ học vấn</CardTitle>
              <CardDescription>Phân bố trình độ cao nhất được cập nhật trong hồ sơ nhân sự.</CardDescription>
            </CardHeader>
            <CardContent>
              <DistributionChart items={hrReport?.educationDistribution ?? []} />
            </CardContent>
          </Card>
        )}

        {canReadPayroll && (
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2"><Banknote className="size-5" /> Mức lương cơ bản</CardTitle>
              <CardDescription>Phân bố mức lương cơ bản đang có hiệu lực, đơn vị VND/tháng.</CardDescription>
            </CardHeader>
            <CardContent>
              <DistributionChart items={salaryReport?.salaryDistribution ?? []} />
            </CardContent>
          </Card>
        )}

        {canReadHr && (
          <Card className={canReadPayroll ? "xl:col-span-2" : ""}>
            <CardHeader>
              <CardTitle className="flex items-center gap-2"><CalendarDays className="size-5" /> Thâm niên</CardTitle>
              <CardDescription>Thời gian làm việc tính từ ngày bắt đầu thâm niên đến ngày thống kê.</CardDescription>
            </CardHeader>
            <CardContent>
              <DistributionChart items={hrReport?.seniorityDistribution ?? []} />
            </CardContent>
          </Card>
        )}
      </div>

      {((hrReport?.missingEducation ?? 0) > 0 || (salaryReport?.missingBasicSalary ?? 0) > 0) && (
        <Alert>
          <AlertTriangle />
          <AlertTitle>Chất lượng dữ liệu</AlertTitle>
          <AlertDescription>
            {hrReport && hrReport.missingEducation > 0 && `${hrReport.missingEducation} hồ sơ chưa cập nhật trình độ. `}
            {salaryReport && salaryReport.missingBasicSalary > 0 && `${salaryReport.missingBasicSalary} nhân viên chưa có lương cơ bản tại ngày thống kê.`}
          </AlertDescription>
        </Alert>
      )}
    </div>
  )
}

function SummaryCard({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <Card size="sm">
      <CardContent className="flex min-h-24 items-center gap-3">
        <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-[var(--green-soft)] text-[var(--green-dark)]">
          {icon}
        </span>
        <span className="grid min-w-0 gap-1">
          <small className="text-xs text-muted-foreground">{label}</small>
          <strong className="truncate text-xl tracking-tight">{value}</strong>
        </span>
      </CardContent>
    </Card>
  )
}
