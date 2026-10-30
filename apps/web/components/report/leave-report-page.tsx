"use client"

import Link from "next/link"
import { useEffect, useState } from "react"

import { ErrorAlert } from "@/components/common/list-controls"
import { ALL_OPTION, FilterSelect, ListToolbar, useDebouncedValue } from "@/components/common/list-filters"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
import { ExportButton, StatTile, YearPicker, downloadCsv } from "@/components/report/report-shared"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { EmptyRow, toDays, today, workforceApi } from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import type { components } from "@/lib/api-schema"
import { getOrganizationUnitOptions } from "@/lib/reference"

type BalanceRow = components["schemas"]["EmployeeBalanceView"]

const days = (value: number) => value.toLocaleString("vi-VN", { maximumFractionDigits: 2 })

/** Annual leave of every employee in scope: granted, used, waiting for approval and left. */
export function LeaveReportPage() {
  const { portal, session } = usePortalSession()
  const canRead = session.account.permissions.some((permission) => permission.code === "report.hr.read")
  const currentYear = Number(today().slice(0, 4))
  const [year, setYear] = useState(currentYear)
  const [keywordInput, setKeywordInput] = useState("")
  const keyword = useDebouncedValue(keywordInput.trim())
  const [unitId, setUnitId] = useState("")
  const [unitOptions, setUnitOptions] = useState<{ value: string; label: string }[]>([])
  const [data, setData] = useState<{ key: string; rows: BalanceRow[]; error?: string } | null>(null)

  useEffect(() => {
    let active = true
    getOrganizationUnitOptions(portal)
      .then((units) => { if (active) setUnitOptions(units.map((unit) => ({ value: String(unit.id), label: unit.name }))) })
      .catch(() => undefined)
    return () => { active = false }
  }, [portal])

  const params = new URLSearchParams({ year: String(year) })
  if (keyword) params.set("keyword", keyword)
  if (unitId) params.set("organizationUnitId", unitId)
  const key = params.toString()

  useEffect(() => {
    if (!canRead) return
    let active = true
    workforceApi<BalanceRow[]>(`reports/hr/leave-balances?${key}`)
      .then((rows) => { if (active) setData({ key, rows }) })
      .catch((caught: unknown) => { if (active) setData({ key, rows: [], error: apiErrorMessage(caught) }) })
    return () => { active = false }
  }, [canRead, key])

  const rows = data?.key === key ? data.rows : null
  // Days per employee follow their own shift; the totals add those days up.
  const inDays = (row: BalanceRow, minutes: number) => toDays(minutes, row.balance.standardDayMinutes)
  const sum = (pick: (row: BalanceRow) => number) => (rows ?? []).reduce((total, row) => total + inDays(row, pick(row)), 0)
  const granted = sum((row) => row.balance.grantedMinutes)
  const used = sum((row) => row.balance.usedMinutes)
  const pending = sum((row) => row.balance.pendingMinutes)
  const remaining = sum((row) => row.balance.remainingMinutes)

  function exportRows() {
    if (!rows) return
    downloadCsv(`phep-nam-${year}.csv`, ["Mã nhân viên", "Họ tên", "Được cấp (ngày)", "Đã dùng (ngày)", "Chờ duyệt (ngày)", "Còn lại (ngày)"],
      rows.map((row) => [row.employeeCode, row.employeeName, inDays(row, row.balance.grantedMinutes),
        inDays(row, row.balance.usedMinutes), inDays(row, row.balance.pendingMinutes), inDays(row, row.balance.remainingMinutes)]))
  }

  return (
    <>
      <PageHeader title="Phép năm" alignActions="end"
        description="Số ngày phép được cấp, đã dùng, đang chờ duyệt và còn lại của từng nhân viên."
        actions={canRead && <YearPicker year={year} maxYear={currentYear + 1} onChange={setYear} />} />

      <div className="mt-6 grid gap-4">
        {!canRead && <p role="alert">Cần quyền xem báo cáo nhân sự để dùng trang này.</p>}
        <ErrorAlert error={data?.key === key ? data.error ?? null : null} />

        {canRead && (
          <>
            <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="Tổng hợp phép năm">
              <StatTile label="Tổng phép được cấp" value={`${days(granted)} ngày`} hint={`${rows?.length ?? 0} nhân viên`} />
              <StatTile label="Đã dùng" value={`${days(used)} ngày`}
                hint={granted > 0 ? `${days((used / granted) * 100)}% số được cấp` : undefined} />
              <StatTile label="Đang chờ duyệt" value={`${days(pending)} ngày`} />
              <StatTile label="Còn lại" value={`${days(remaining)} ngày`} hint="Không chuyển sang năm sau" />
            </section>

            <Card>
              <CardHeader>
                <CardTitle>Phép năm {year} theo nhân viên</CardTitle>
                <CardDescription>
                  12 ngày mỗi năm, vào làm hoặc nghỉ việc giữa năm thì tính theo số tháng. Nhân viên có làm việc trong năm, kể cả người đã nghỉ.
                </CardDescription>
                <CardAction><ExportButton onClick={exportRows} disabled={!rows || rows.length === 0} /></CardAction>
              </CardHeader>
              <CardContent className="grid gap-4">
                <ListToolbar keyword={keywordInput} onKeywordChange={setKeywordInput} placeholder="Tìm theo họ tên hoặc mã nhân viên"
                  activeFilters={unitId ? 1 : 0} onReset={() => { setKeywordInput(""); setUnitId("") }}>
                  {unitOptions.length > 0 && (
                    <FilterSelect id="leave-report-unit" label="Đơn vị (phân công hiện tại)" value={unitId || ALL_OPTION}
                      onChange={(value) => setUnitId(value === ALL_OPTION ? "" : value)} options={unitOptions} />
                  )}
                </ListToolbar>

                {rows === null
                  ? <Skeleton className="h-40 w-full" />
                  : (
                    <div className="rounded-xl border">
                      <Table>
                        <TableHeader>
                          <TableRow>
                            <TableHead>Nhân viên</TableHead>
                            <TableHead className="text-right">Được cấp</TableHead>
                            <TableHead className="text-right">Đã dùng</TableHead>
                            <TableHead className="text-right">Chờ duyệt</TableHead>
                            <TableHead className="text-right">Còn lại</TableHead>
                            <TableHead className="w-48">Mức sử dụng</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {rows.map((row) => {
                            const share = row.balance.grantedMinutes > 0
                              ? Math.min(100, (row.balance.usedMinutes / row.balance.grantedMinutes) * 100) : 0
                            return (
                              <TableRow key={row.balance.employeeId}>
                                <TableCell>
                                  <Link href={`/employees/${row.balance.employeeId}`} className="font-medium hover:underline">{row.employeeName}</Link>
                                  <span className="block text-xs text-muted-foreground">{row.employeeCode}</span>
                                </TableCell>
                                <TableCell className="text-right tabular-nums">{days(inDays(row, row.balance.grantedMinutes))}</TableCell>
                                <TableCell className="text-right tabular-nums">{days(inDays(row, row.balance.usedMinutes))}</TableCell>
                                <TableCell className="text-right tabular-nums">{row.balance.pendingMinutes ? days(inDays(row, row.balance.pendingMinutes)) : "—"}</TableCell>
                                <TableCell className="text-right font-semibold tabular-nums">{days(inDays(row, row.balance.remainingMinutes))}</TableCell>
                                <TableCell>
                                  <div className="flex items-center gap-2" title={`Đã dùng ${days(share)}%`}>
                                    <div className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
                                      <div className="h-full rounded-full" style={{ width: `${share}%`, background: "var(--chart-1)" }} />
                                    </div>
                                    <span className="w-10 text-right text-xs text-muted-foreground tabular-nums">{Math.round(share)}%</span>
                                  </div>
                                </TableCell>
                              </TableRow>
                            )
                          })}
                          {rows.length === 0 && <EmptyRow colSpan={6}>Không có nhân viên nào khớp bộ lọc.</EmptyRow>}
                        </TableBody>
                      </Table>
                    </div>
                  )}
              </CardContent>
            </Card>
          </>
        )}
      </div>
    </>
  )
}
