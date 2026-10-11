"use client"

import { Fragment, useEffect, useState } from "react"
import { Ban, Calculator, CheckCheck, FileSpreadsheet, ListPlus, Loader2, Lock, Plus, Wallet } from "lucide-react"

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { PayrollAdjustmentsPanel } from "@/components/workforce/payroll-adjustments-panel"
import {
  EmptyRow,
  SectionError,
  money,
  today,
  useWorkforceAction,
  workforceApi,
  type WorkforceContext,
} from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import { authorizedDownload } from "@/lib/auth/client"
import type { Page } from "@/lib/pagination"
import {
  PAYROLL_PERIOD_STATUS_LABELS,
  type PayrollPeriod,
  type PayrollPeriodStatus,
  type PayrollProfile,
  type Payslip,
  type SalaryHistory,
  type TaxDependent,
  type WorkforceBody,
} from "@/lib/workforce"

type Run = (success: string, action: () => Promise<unknown>) => Promise<boolean>

const PERIOD_TONE: Record<PayrollPeriodStatus, "default" | "secondary" | "outline" | "destructive"> = {
  DRAFT: "outline",
  CALCULATED: "secondary",
  APPROVED: "default",
  PAID: "default",
  LOCKED: "outline",
  CANCELLED: "destructive",
}

const periodLabel = (period: Pick<PayrollPeriod, "month" | "year">) => `${String(period.month).padStart(2, "0")}/${period.year}`

export function PayrollSection({ employeeId, permissions, month }: WorkforceContext) {
  const canReadCompensation = permissions.has("compensation.read")
  const canManageCompensation = permissions.has("compensation.manage")
  const canReadPayslip = permissions.has("payroll.self.read") || permissions.has("report.payroll.read")
  const canReadPeriods = permissions.has("report.payroll.read")

  const [salaryHistory, setSalaryHistory] = useState<SalaryHistory[]>([])
  const [profiles, setProfiles] = useState<PayrollProfile[]>([])
  const [dependents, setDependents] = useState<TaxDependent[]>([])
  const [payslips, setPayslips] = useState<Payslip[]>([])
  const [periods, setPeriods] = useState<PayrollPeriod[]>([])
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [selectedPayslip, setSelectedPayslip] = useState<Payslip | null>(null)
  const reload = () => setRevision((value) => value + 1)
  const { busy, run } = useWorkforceAction(reload)

  useEffect(() => {
    let active = true
    const compensation = employeeId > 0 && canReadCompensation
    Promise.all([
      compensation ? workforceApi<SalaryHistory[]>(`compensation/employees/${employeeId}/salary-history`) : Promise.resolve([]),
      compensation ? workforceApi<PayrollProfile[]>(`compensation/employees/${employeeId}/payroll-profiles`) : Promise.resolve([]),
      compensation ? workforceApi<TaxDependent[]>(`compensation/employees/${employeeId}/tax-dependents`) : Promise.resolve([]),
      employeeId > 0 && canReadPayslip
        ? workforceApi<Page<Payslip>>(`payroll/employees/${employeeId}/payslips?size=100`).then((page) => page.content)
        : Promise.resolve([]),
      canReadPeriods
        ? workforceApi<Page<PayrollPeriod>>("payroll/periods?size=100&sort=periodStart,desc").then((page) => page.content)
        : Promise.resolve([]),
    ])
      .then(([history, loadedProfiles, loadedDependents, loadedPayslips, loadedPeriods]) => {
        if (!active) return
        setSalaryHistory(history)
        setProfiles(loadedProfiles)
        setDependents(loadedDependents)
        setPayslips(loadedPayslips)
        setPeriods(loadedPeriods)
        setError(null)
      })
      .catch((caught: unknown) => { if (active) setError(apiErrorMessage(caught)) })
    return () => { active = false }
  }, [employeeId, canReadCompensation, canReadPayslip, canReadPeriods, revision])

  const periodById = new Map(periods.map((period) => [period.id, period]))

  return (
    <div className="grid gap-6">
      <SectionError error={error} />

      {canReadPeriods && (
        <PeriodsCard periods={periods} permissions={permissions} month={month} busy={busy} run={run} onChanged={reload} />
      )}

      {canReadPayslip && (
        <Card>
          <CardHeader>
            <CardTitle>Phiếu lương của nhân viên #{employeeId || "—"}</CardTitle>
            <CardDescription>Nhân viên chỉ thấy phiếu của kỳ đã duyệt; chọn một phiếu để xem chi tiết.</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="rounded-xl border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Kỳ</TableHead>
                    <TableHead className="text-right">Tổng thu nhập</TableHead>
                    <TableHead className="text-right">Bảo hiểm</TableHead>
                    <TableHead className="text-right">Thuế TNCN</TableHead>
                    <TableHead className="text-right">Thực nhận</TableHead>
                    <TableHead className="text-right" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {payslips.map((slip) => {
                    const period = periodById.get(slip.payrollPeriodId)
                    return (
                      <TableRow key={slip.id}>
                        <TableCell className="font-medium">{period ? periodLabel(period) : `Kỳ #${slip.payrollPeriodId}`}</TableCell>
                        <TableCell className="text-right">{money(slip.grossPay)}</TableCell>
                        <TableCell className="text-right">
                          {money(slip.employeeSocialInsurance + slip.employeeHealthInsurance + slip.employeeUnemploymentInsurance)}
                        </TableCell>
                        <TableCell className="text-right">{money(slip.personalIncomeTax)}</TableCell>
                        <TableCell className="text-right font-semibold">{money(slip.netPay)}</TableCell>
                        <TableCell className="text-right">
                          <Button type="button" variant="outline" size="sm" onClick={() => setSelectedPayslip(slip)}>Chi tiết</Button>
                        </TableCell>
                      </TableRow>
                    )
                  })}
                  {payslips.length === 0 && <EmptyRow colSpan={6}>{employeeId ? "Chưa có phiếu lương." : "Nhập mã nhân viên để xem phiếu lương."}</EmptyRow>}
                </TableBody>
              </Table>
            </div>
          </CardContent>
        </Card>
      )}

      {(canReadCompensation || canManageCompensation) && (
        <SalaryCard employeeId={employeeId} history={salaryHistory} canRead={canReadCompensation}
          canManage={canManageCompensation} busy={busy} run={run} />
      )}

      {canReadCompensation && (
        <DeductionsCard employeeId={employeeId} profiles={profiles} dependents={dependents}
          canManage={canManageCompensation} busy={busy} run={run} />
      )}

      {selectedPayslip && (
        <PayslipDialog payslip={selectedPayslip} period={periodById.get(selectedPayslip.payrollPeriodId)}
          onClose={() => setSelectedPayslip(null)} />
      )}
    </div>
  )
}

function PeriodsCard({ periods, permissions, month, busy, run, onChanged }: {
  periods: PayrollPeriod[]
  permissions: ReadonlySet<string>
  month: string
  busy: boolean
  run: Run
  onChanged: () => void
}) {
  const canCalculate = permissions.has("payroll.calculate")
  const [periodMonth, setPeriodMonth] = useState(month)
  const [taxPaymentDate, setTaxPaymentDate] = useState(today())
  const [adjustingId, setAdjustingId] = useState<number | null>(null)
  const [cancelling, setCancelling] = useState<PayrollPeriod | null>(null)

  return (
    <Card>
      <CardHeader>
        <CardTitle>Kỳ lương</CardTitle>
        <CardDescription>
          Nháp → Đã tính → Đã duyệt → Đã trả → Đã khóa. Người tính lương và người duyệt phải là hai tài khoản khác nhau.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {canCalculate && (
          <form className="flex flex-wrap items-end gap-2" onSubmit={(event) => {
            event.preventDefault()
            void run("Đã tạo kỳ lương", () => workforceApi("payroll/periods", "POST", {
              year: Number(periodMonth.slice(0, 4)), month: Number(periodMonth.slice(5, 7)), taxPaymentDate,
            } satisfies WorkforceBody["CreatePayrollPeriodRequest"]))
          }}>
            <div className="grid gap-1.5">
              <Label htmlFor="period-month">Tháng</Label>
              <Input id="period-month" type="month" required value={periodMonth} onChange={(event) => setPeriodMonth(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="period-payment-date">Ngày dự kiến trả lương</Label>
              <Input id="period-payment-date" type="date" required value={taxPaymentDate} onChange={(event) => setTaxPaymentDate(event.target.value)} />
            </div>
            <Button type="submit" disabled={busy}><Plus /> Tạo kỳ</Button>
          </form>
        )}
        <div className="rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Kỳ</TableHead>
                <TableHead>Khoảng ngày</TableHead>
                <TableHead>Trả lương dự kiến</TableHead>
                <TableHead>Trạng thái</TableHead>
                <TableHead className="text-right">Thao tác</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {periods.map((period) => {
                const editable = period.status === "DRAFT" || period.status === "CALCULATED"
                return (
                  <Fragment key={period.id}>
                    <TableRow>
                      <TableCell className="font-medium">{periodLabel(period)}</TableCell>
                      <TableCell className="text-muted-foreground">{period.periodStart} → {period.periodEnd}</TableCell>
                      <TableCell>{period.taxPaymentDate ?? "—"}</TableCell>
                      <TableCell><Badge variant={PERIOD_TONE[period.status]}>{PAYROLL_PERIOD_STATUS_LABELS[period.status]}</Badge></TableCell>
                      <TableCell>
                        <div className="flex flex-wrap justify-end gap-1">
                          {canCalculate && editable && (
                            <Button type="button" size="sm" variant="outline" disabled={busy}
                              onClick={() => void run("Đã tính kỳ lương", () => workforceApi(`payroll/periods/${period.id}/calculate`, "POST",
                                { taxPaymentDate: period.taxPaymentDate ?? taxPaymentDate } satisfies WorkforceBody["PayrollActionRequest"]))}>
                              <Calculator /> {period.status === "DRAFT" ? "Tính lương" : "Tính lại"}
                            </Button>
                          )}
                          {period.status === "CALCULATED" && permissions.has("payroll.approve") && (
                            <Button type="button" size="sm" disabled={busy}
                              onClick={() => void run("Đã duyệt kỳ lương", () => workforceApi(`payroll/periods/${period.id}/approve`, "POST"))}>
                              <CheckCheck /> Duyệt
                            </Button>
                          )}
                          {period.status === "APPROVED" && permissions.has("payroll.mark_paid") && (
                            <Button type="button" size="sm" disabled={busy}
                              onClick={() => void run("Đã ghi nhận thanh toán", () => workforceApi(`payroll/periods/${period.id}/mark-paid`, "POST",
                                { taxPaymentDate: today() } satisfies WorkforceBody["PayrollActionRequest"]))}>
                              <Wallet /> Đã trả hôm nay
                            </Button>
                          )}
                          {period.status === "PAID" && permissions.has("payroll.lock") && (
                            <Button type="button" size="sm" variant="outline" disabled={busy}
                              onClick={() => void run("Đã khóa kỳ lương", () => workforceApi(`payroll/periods/${period.id}/lock`, "POST"))}>
                              <Lock /> Khóa
                            </Button>
                          )}
                          {canCalculate && editable && (
                            <Button type="button" size="sm" variant="ghost" className="text-muted-foreground hover:text-destructive"
                              disabled={busy} onClick={() => setCancelling(period)}>
                              <Ban /> Hủy kỳ
                            </Button>
                          )}
                          {canCalculate && (
                            <Button type="button" size="sm" variant={adjustingId === period.id ? "secondary" : "ghost"}
                              onClick={() => setAdjustingId(adjustingId === period.id ? null : period.id)}>
                              <ListPlus /> Khoản cộng/trừ
                            </Button>
                          )}
                          {period.status !== "DRAFT" && period.status !== "CANCELLED" && (
                            <Button type="button" size="sm" variant="ghost" disabled={busy}
                              onClick={() => void run("Đã tải bảng lương",
                                () => authorizedDownload("hrm", `/workforce/payroll/periods/${period.id}/export`))}>
                              <FileSpreadsheet /> Xuất Excel
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                    {adjustingId === period.id && (
                      <TableRow>
                        <TableCell colSpan={5} className="whitespace-normal">
                          <PayrollAdjustmentsPanel periodId={period.id} editable={editable} onChanged={onChanged} />
                        </TableCell>
                      </TableRow>
                    )}
                  </Fragment>
                )
              })}
              {periods.length === 0 && <EmptyRow colSpan={5}>Chưa có kỳ lương.</EmptyRow>}
            </TableBody>
          </Table>
        </div>
      </CardContent>

      <AlertDialog open={cancelling !== null} onOpenChange={(open) => { if (!open && !busy) setCancelling(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Hủy {cancelling && periodLabel(cancelling)}?</AlertDialogTitle>
            <AlertDialogDescription>
              Kỳ chuyển sang Đã hủy và không thể khôi phục; phiếu lương đã tính của kỳ được giữ lại để tra cứu.
              Sau đó có thể tạo kỳ mới cho cùng tháng.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={busy}>Không</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              disabled={busy}
              onClick={(event) => {
                event.preventDefault()
                if (!cancelling) return
                void run("Đã hủy kỳ lương", () => workforceApi(`payroll/periods/${cancelling.id}/cancel`, "POST"))
                  .then(() => setCancelling(null))
              }}
            >
              {busy && <Loader2 className="animate-spin" />}
              Hủy kỳ
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  )
}

function SalaryCard({ employeeId, history, canRead, canManage, busy, run }: {
  employeeId: number
  history: SalaryHistory[]
  canRead: boolean
  canManage: boolean
  busy: boolean
  run: Run
}) {
  const [amount, setAmount] = useState("")
  const [effectiveFrom, setEffectiveFrom] = useState(() => {
    // The employee page links here with ?effectiveFrom= after a transfer that needs a new salary.
    const value = typeof window === "undefined" ? null : new URLSearchParams(window.location.search).get("effectiveFrom")
    return value && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : today()
  })
  const [reason, setReason] = useState("")

  return (
    <Card>
      <CardHeader>
        <CardTitle>Lương cơ bản của nhân viên #{employeeId || "—"}</CardTitle>
        <CardDescription>Mỗi mức có ngày hiệu lực; mức mới tự đóng mức đang áp dụng. Phải có mức lương trước khi tính kỳ.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {canRead && (
          <div className="rounded-xl border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Hiệu lực</TableHead>
                  <TableHead className="text-right">Lương tháng</TableHead>
                  <TableHead>Lý do</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {history.map((salary) => (
                  <TableRow key={salary.id}>
                    <TableCell>{salary.effectiveFrom} → {salary.effectiveTo ?? "hiện tại"}</TableCell>
                    <TableCell className="text-right font-medium">{money(salary.baseSalary)}</TableCell>
                    <TableCell className="text-muted-foreground">{salary.reason ?? "—"}</TableCell>
                  </TableRow>
                ))}
                {history.length === 0 && <EmptyRow colSpan={3}>Chưa có mức lương hiệu lực.</EmptyRow>}
              </TableBody>
            </Table>
          </div>
        )}
        {canManage && (
          <form className="flex flex-wrap items-end gap-2" onSubmit={(event) => {
            event.preventDefault()
            void run("Đã lưu mức lương", () => workforceApi(`compensation/employees/${employeeId}/salary-history`, "POST", {
              baseSalary: Number(amount), effectiveFrom, reason: reason.trim() || null, note: null,
            } satisfies WorkforceBody["SetSalaryCommand"])).then((ok) => { if (ok) { setAmount(""); setReason("") } })
          }}>
            <div className="grid gap-1.5">
              <Label htmlFor="salary-amount">Lương tháng (VND)</Label>
              <Input id="salary-amount" required type="number" min="1" step="1" value={amount} onChange={(event) => setAmount(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="salary-from">Hiệu lực từ</Label>
              <Input id="salary-from" required type="date" value={effectiveFrom} onChange={(event) => setEffectiveFrom(event.target.value)} />
            </div>
            <div className="grid min-w-48 flex-1 gap-1.5">
              <Label htmlFor="salary-reason">Lý do</Label>
              <Input id="salary-reason" value={reason} onChange={(event) => setReason(event.target.value)} />
            </div>
            <Button type="submit" disabled={busy || !employeeId}>Lưu mức lương</Button>
          </form>
        )}
      </CardContent>
    </Card>
  )
}

function DeductionsCard({ employeeId, profiles, dependents, canManage, busy, run }: {
  employeeId: number
  profiles: PayrollProfile[]
  dependents: TaxDependent[]
  canManage: boolean
  busy: boolean
  run: Run
}) {
  const [profileDate, setProfileDate] = useState(today())
  const [insuranceSalary, setInsuranceSalary] = useState("")
  const [wageRegion, setWageRegion] = useState("1")
  const [social, setSocial] = useState(true)
  const [health, setHealth] = useState(true)
  const [unemployment, setUnemployment] = useState(true)
  const [dependentName, setDependentName] = useState("")
  const [dependentDate, setDependentDate] = useState(today())
  const [ending, setEnding] = useState<TaxDependent | null>(null)
  const regionItems = { 1: "Vùng I", 2: "Vùng II", 3: "Vùng III", 4: "Vùng IV" }
  const yesNo = (value: boolean) => value ? "Có" : "Không"

  return (
    <Card>
      <CardHeader>
        <CardTitle>Bảo hiểm và giảm trừ thuế</CardTitle>
        <CardDescription>Lương đóng bảo hiểm do HR xác nhận, tách khỏi tổng thu nhập; người phụ thuộc được giảm trừ theo thời gian hiệu lực.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-6">
        <div className="grid gap-3">
          <h3 className="font-medium">Hồ sơ bảo hiểm</h3>
          <div className="rounded-xl border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Hiệu lực</TableHead>
                  <TableHead className="text-right">Lương đóng BH</TableHead>
                  <TableHead>Vùng</TableHead>
                  <TableHead>BHXH</TableHead>
                  <TableHead>BHYT</TableHead>
                  <TableHead>BHTN</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {profiles.map((profile) => (
                  <TableRow key={profile.id}>
                    <TableCell>{profile.effectiveFrom} → {profile.effectiveTo ?? "hiện tại"}</TableCell>
                    <TableCell className="text-right">{money(profile.insuranceSalary)}</TableCell>
                    <TableCell>{profile.wageRegion}</TableCell>
                    <TableCell>{yesNo(profile.socialInsurance)}</TableCell>
                    <TableCell>{yesNo(profile.healthInsurance)}</TableCell>
                    <TableCell>{yesNo(profile.unemploymentInsurance)}</TableCell>
                  </TableRow>
                ))}
                {profiles.length === 0 && <EmptyRow colSpan={6}>Chưa có hồ sơ; chưa thể tính lương thực nhận.</EmptyRow>}
              </TableBody>
            </Table>
          </div>
          {canManage && (
            <form className="flex flex-wrap items-end gap-3" onSubmit={(event) => {
              event.preventDefault()
              void run("Đã lưu hồ sơ bảo hiểm", () => workforceApi(`compensation/employees/${employeeId}/payroll-profiles`, "POST", {
                effectiveFrom: profileDate, taxResident: true, socialInsurance: social, healthInsurance: health,
                unemploymentInsurance: unemployment, insuranceSalary: Number(insuranceSalary), wageRegion: Number(wageRegion),
              } satisfies WorkforceBody["ProfileCommand"]))
            }}>
              <div className="grid gap-1.5">
                <Label htmlFor="profile-from">Hiệu lực từ</Label>
                <Input id="profile-from" required type="date" value={profileDate} onChange={(event) => setProfileDate(event.target.value)} />
              </div>
              <div className="grid gap-1.5">
                <Label htmlFor="profile-salary">Lương đóng bảo hiểm</Label>
                <Input id="profile-salary" required type="number" min="0" value={insuranceSalary} onChange={(event) => setInsuranceSalary(event.target.value)} />
              </div>
              <div className="grid gap-1.5">
                <Label htmlFor="profile-region">Vùng lương tối thiểu</Label>
                <Select value={wageRegion} items={regionItems} onValueChange={(value) => setWageRegion(value ?? "1")}>
                  <SelectTrigger id="profile-region" className="w-32"><SelectValue /></SelectTrigger>
                  <SelectContent>{Object.entries(regionItems).map(([value, label]) => <SelectItem key={value} value={value}>{label}</SelectItem>)}</SelectContent>
                </Select>
              </div>
              {([["BHXH", social, setSocial], ["BHYT", health, setHealth], ["BHTN", unemployment, setUnemployment]] as const).map(([label, checked, set]) => (
                <label key={label} className="flex h-8 items-center gap-1.5 text-sm">
                  <input type="checkbox" checked={checked} onChange={(event) => set(event.target.checked)} /> {label}
                </label>
              ))}
              <Button type="submit" disabled={busy || !employeeId}>Lưu hồ sơ</Button>
            </form>
          )}
        </div>

        <div className="grid gap-3">
          <h3 className="font-medium">Người phụ thuộc</h3>
          <div className="rounded-xl border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Họ tên</TableHead>
                  <TableHead>Hiệu lực</TableHead>
                  {canManage && <TableHead className="text-right">Thao tác</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {dependents.map((dependent) => (
                  <TableRow key={dependent.id}>
                    <TableCell className="font-medium">{dependent.fullName}</TableCell>
                    <TableCell>{dependent.effectiveFrom} → {dependent.effectiveTo ?? "hiện tại"}</TableCell>
                    {canManage && (
                      <TableCell className="text-right">
                        {!dependent.effectiveTo && (
                          <Button type="button" size="sm" variant="outline" disabled={busy} onClick={() => setEnding(dependent)}>Kết thúc</Button>
                        )}
                      </TableCell>
                    )}
                  </TableRow>
                ))}
                {dependents.length === 0 && <EmptyRow colSpan={canManage ? 3 : 2}>Chưa đăng ký người phụ thuộc.</EmptyRow>}
              </TableBody>
            </Table>
          </div>
          {canManage && (
            <form className="flex flex-wrap items-end gap-2" onSubmit={(event) => {
              event.preventDefault()
              void run("Đã thêm người phụ thuộc", () => workforceApi(`compensation/employees/${employeeId}/tax-dependents`, "POST",
                { fullName: dependentName.trim(), effectiveFrom: dependentDate } satisfies WorkforceBody["DependentCommand"]))
                .then((ok) => { if (ok) setDependentName("") })
            }}>
              <div className="grid min-w-48 flex-1 gap-1.5">
                <Label htmlFor="dependent-name">Họ tên</Label>
                <Input id="dependent-name" required value={dependentName} onChange={(event) => setDependentName(event.target.value)} />
              </div>
              <div className="grid gap-1.5">
                <Label htmlFor="dependent-from">Hiệu lực từ</Label>
                <Input id="dependent-from" required type="date" value={dependentDate} onChange={(event) => setDependentDate(event.target.value)} />
              </div>
              <Button type="submit" disabled={busy || !employeeId}>Thêm người phụ thuộc</Button>
            </form>
          )}
        </div>
      </CardContent>

      {ending && (
        <EndDependentDialog dependent={ending} busy={busy} onClose={() => setEnding(null)}
          onSubmit={(effectiveTo) => run("Đã kết thúc người phụ thuộc", () => workforceApi(
            `compensation/employees/${employeeId}/tax-dependents/${ending.id}/end`, "PUT",
            { effectiveTo } satisfies WorkforceBody["EndDependentCommand"]))} />
      )}
    </Card>
  )
}

function EndDependentDialog({ dependent, busy, onClose, onSubmit }: {
  dependent: TaxDependent
  busy: boolean
  onClose: () => void
  onSubmit: (effectiveTo: string) => Promise<boolean>
}) {
  const [effectiveTo, setEffectiveTo] = useState(today())
  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-sm">
        <form onSubmit={(event) => {
          event.preventDefault()
          void onSubmit(effectiveTo).then((ok) => { if (ok) onClose() })
        }}>
          <DialogHeader>
            <DialogTitle>Kết thúc giảm trừ cho {dependent.fullName}</DialogTitle>
            <DialogDescription>Ngày cuối cùng còn được giảm trừ; không được trước {dependent.effectiveFrom}.</DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-1.5">
            <Label htmlFor="dependent-end">Ngày kết thúc</Label>
            <Input id="dependent-end" type="date" required min={dependent.effectiveFrom} value={effectiveTo}
              onChange={(event) => setEffectiveTo(event.target.value)} />
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose}>Hủy</Button>
            <Button type="submit" disabled={busy}>{busy && <Loader2 className="animate-spin" />} Xác nhận</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function PayslipDialog({ payslip, period, onClose }: { payslip: Payslip; period?: PayrollPeriod; onClose: () => void }) {
  // `|| 0` keeps a zero deduction from rendering as "-0 ₫".
  const lines: [string, number, boolean?][] = [
    ["Tổng thu nhập", payslip.grossPay, true],
    ["BHXH", -payslip.employeeSocialInsurance || 0],
    ["BHYT", -payslip.employeeHealthInsurance || 0],
    ["BHTN", -payslip.employeeUnemploymentInsurance || 0],
    ["Thuế TNCN", -payslip.personalIncomeTax || 0],
    ...(payslip.otherDeductions > 0 ? [["Khấu trừ khác", -payslip.otherDeductions] as [string, number]] : []),
  ]
  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Phiếu lương {period ? periodLabel(period) : `kỳ #${payslip.payrollPeriodId}`} · {payslip.employeeNameSnapshot}</DialogTitle>
          <DialogDescription>
            {payslip.employeeCodeSnapshot} · {payslip.organizationUnitSnapshot} · {payslip.workLocationSnapshot}.
            Công {payslip.payableWorkMinutes}/{payslip.scheduledWorkMinutes} phút, tăng ca {payslip.approvedOvertimeMinutes} phút.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4">
          <div className="rounded-xl border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Khoản thu nhập</TableHead>
                  <TableHead className="text-right">Thành tiền</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {payslip.items.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell className="whitespace-normal">{item.description}</TableCell>
                    <TableCell className="text-right">{item.componentType === "DEDUCTION" ? "−" : ""}{money(item.amount)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <div className="rounded-xl border">
            <Table>
              <TableBody>
                {lines.map(([label, amount, strong]) => (
                  <TableRow key={label}>
                    <TableCell className={strong ? "font-medium" : ""}>{label}</TableCell>
                    <TableCell className={`text-right ${strong ? "font-medium" : ""}`}>{money(amount)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
              <TableFooter>
                <TableRow>
                  <TableCell className="font-semibold">Thực nhận</TableCell>
                  <TableCell className="text-right text-base font-semibold">{money(payslip.netPay)}</TableCell>
                </TableRow>
              </TableFooter>
            </Table>
          </div>
          <dl className="grid gap-2 text-sm text-muted-foreground sm:grid-cols-2">
            <div>Căn cứ BHXH/BHYT: <span className="text-foreground">{money(payslip.insuranceSalaryBase)}</span></div>
            <div>Căn cứ BHTN: <span className="text-foreground">{money(payslip.unemploymentInsuranceBase)}</span></div>
            <div>Tăng ca miễn thuế: <span className="text-foreground">{money(payslip.taxExemptOvertimePay)}</span></div>
            <div>Phụ cấp khác miễn thuế: <span className="text-foreground">{money(payslip.taxExemptOtherEarnings)}</span></div>
            <div className="sm:col-span-2">Thu nhập tính thuế sau giảm trừ: <span className="text-foreground">{money(payslip.taxableIncome)}</span></div>
          </dl>
        </div>
      </DialogContent>
    </Dialog>
  )
}
