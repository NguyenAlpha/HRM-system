"use client"

import { useEffect, useState, type ReactNode } from "react"
import { CalendarX, Eye, Loader2, Plus } from "lucide-react"

import { RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import { PayslipDialog, payslipDeductions, periodLabel } from "@/components/payroll/payroll-shared"
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  EmptyRow,
  SectionError,
  money,
  today,
  useWorkforceAction,
  workforceApi,
} from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import type { Page } from "@/lib/pagination"
import type { PayrollProfile, Payslip, SalaryHistory, TaxDependent, WorkforceBody } from "@/lib/workforce"

type Form = "salary" | "profile" | "dependent"

const REGION_ITEMS = { 1: "Vùng I", 2: "Vùng II", 3: "Vùng III", 4: "Vùng IV" }

const period = (row: { effectiveFrom: string; effectiveTo: string | null }) =>
  `${row.effectiveFrom} → ${row.effectiveTo ?? "hiện tại"}`

/**
 * Salary, insurance and tax deductions of one employee, plus their payslips: the inputs HR keeps
 * for payroll. Each list adds a dated entry through a dialog; a new entry closes the current one.
 */
export function EmployeeCompensationCard({ employeeId, hireDate, permissions }: {
  employeeId: number
  hireDate: string
  permissions: ReadonlySet<string>
}) {
  const canManage = permissions.has("compensation.manage")
  const canReadPayslips = permissions.has("report.payroll.read")
  const [salaries, setSalaries] = useState<SalaryHistory[]>([])
  const [profiles, setProfiles] = useState<PayrollProfile[]>([])
  const [dependents, setDependents] = useState<TaxDependent[]>([])
  const [payslips, setPayslips] = useState<Payslip[]>([])
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [form, setForm] = useState<Form | null>(null)
  const [ending, setEnding] = useState<TaxDependent | null>(null)
  const [selectedPayslip, setSelectedPayslip] = useState<Payslip | null>(null)
  const reload = () => setRevision((value) => value + 1)
  const { busy, run } = useWorkforceAction(reload)
  const base = `compensation/employees/${employeeId}`

  useEffect(() => {
    let active = true
    Promise.all([
      workforceApi<SalaryHistory[]>(`${base}/salary-history`),
      workforceApi<PayrollProfile[]>(`${base}/payroll-profiles`),
      workforceApi<TaxDependent[]>(`${base}/tax-dependents`),
      canReadPayslips
        ? workforceApi<Page<Payslip>>(`payroll/employees/${employeeId}/payslips?size=100&sort=payrollPeriod.periodStart,desc`)
          .then((page) => page.content)
        : Promise.resolve([]),
    ])
      .then(([loadedSalaries, loadedProfiles, loadedDependents, loadedPayslips]) => {
        if (!active) return
        setSalaries(loadedSalaries)
        setProfiles(loadedProfiles)
        setDependents(loadedDependents)
        setPayslips(loadedPayslips)
        setError(null)
      })
      .catch((caught: unknown) => { if (active) setError(apiErrorMessage(caught)) })
    return () => { active = false }
  }, [base, canReadPayslips, employeeId, revision])

  const currentSalary = salaries.find((salary) => salary.effectiveTo === null)
  // The first entry of each history starts on the hire date; later ones default to today.
  const firstDate = (rows: unknown[]) => rows.length === 0 ? hireDate : today()

  const addButton = (kind: Form, label: string) => canManage && (
    <Button type="button" size="sm" onClick={() => setForm(kind)}><Plus /> {label}</Button>
  )

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
        <div>
          <CardTitle>Lương & bảo hiểm</CardTitle>
          <CardDescription>Dữ liệu đầu vào của kỳ lương; mỗi mục có ngày hiệu lực, mục mới tự kết thúc mục đang áp dụng.</CardDescription>
        </div>
        {currentSalary && (
          <div className="text-right">
            <p className="text-xs text-muted-foreground">Lương cơ bản hiện tại</p>
            <p className="text-xl font-semibold tabular-nums">{money(currentSalary.baseSalary)}</p>
          </div>
        )}
      </CardHeader>
      <CardContent className="grid gap-4">
        <SectionError error={error} />
        <Tabs defaultValue="salary">
          <TabsList>
            <TabsTrigger value="salary">Lương cơ bản</TabsTrigger>
            <TabsTrigger value="profile">Bảo hiểm & thuế</TabsTrigger>
            <TabsTrigger value="dependents">Người phụ thuộc</TabsTrigger>
            {canReadPayslips && <TabsTrigger value="payslips">Phiếu lương</TabsTrigger>}
          </TabsList>

          <TabsContent value="salary" className="mt-4 grid gap-3">
            <Toolbar hint="Lương tháng theo hợp đồng; phải phủ mọi ngày làm việc của kỳ cần tính.">
              {addButton("salary", "Thêm mức lương")}
            </Toolbar>
            <ListTable headers={["Hiệu lực", "Lương tháng", "Lý do"]} alignRight={[1]} empty="Chưa có mức lương; chưa thể tính lương.">
              {salaries.map((salary) => (
                <TableRow key={salary.id}>
                  <TableCell>{period(salary)}{salary.effectiveTo === null && <Badge variant="secondary" className="ml-2">Hiện tại</Badge>}</TableCell>
                  <TableCell className="text-right font-medium tabular-nums">{money(salary.baseSalary)}</TableCell>
                  <TableCell className="text-muted-foreground">{salary.reason ?? "—"}</TableCell>
                </TableRow>
              ))}
            </ListTable>
          </TabsContent>

          <TabsContent value="profile" className="mt-4 grid gap-3">
            <Toolbar hint="Lương đóng bảo hiểm do HR xác nhận, tách khỏi thu nhập thực tế; vùng lương quyết định trần BHTN.">
              {addButton("profile", "Thêm hồ sơ")}
            </Toolbar>
            <ListTable headers={["Hiệu lực", "Lương đóng BH", "Vùng", "Tham gia"]} alignRight={[1]}
              empty="Chưa có hồ sơ bảo hiểm/thuế; chưa thể tính lương.">
              {profiles.map((profile) => (
                <TableRow key={profile.id}>
                  <TableCell>{period(profile)}</TableCell>
                  <TableCell className="text-right tabular-nums">{money(profile.insuranceSalary)}</TableCell>
                  <TableCell>{REGION_ITEMS[profile.wageRegion as 1 | 2 | 3 | 4]}</TableCell>
                  <TableCell className="text-sm">
                    {[profile.socialInsurance && "BHXH", profile.healthInsurance && "BHYT", profile.unemploymentInsurance && "BHTN"]
                      .filter(Boolean).join(", ") || "Không tham gia"}
                  </TableCell>
                </TableRow>
              ))}
            </ListTable>
          </TabsContent>

          <TabsContent value="dependents" className="mt-4 grid gap-3">
            <Toolbar hint="Mỗi người phụ thuộc còn hiệu lực vào ngày trả lương được giảm trừ một suất khi tính thuế TNCN.">
              {addButton("dependent", "Thêm người phụ thuộc")}
            </Toolbar>
            <ListTable headers={["Họ tên", "Số định danh", "Hiệu lực", "Thao tác"]} alignRight={[3]} empty="Chưa đăng ký người phụ thuộc.">
              {dependents.map((dependent) => (
                <TableRow key={dependent.id}>
                  <TableCell className="font-medium">{dependent.fullName}</TableCell>
                  <TableCell className="text-muted-foreground">{dependent.identifier ?? "—"}</TableCell>
                  <TableCell>{period(dependent)}</TableCell>
                  <RowActionsCell label={dependent.fullName}>
                    {canManage && !dependent.effectiveTo && (
                      <RowActionItem icon={CalendarX} destructive disabled={busy} onClick={() => setEnding(dependent)}>Kết thúc giảm trừ</RowActionItem>
                    )}
                  </RowActionsCell>
                </TableRow>
              ))}
            </ListTable>
          </TabsContent>

          {canReadPayslips && (
            <TabsContent value="payslips" className="mt-4 grid gap-3">
              <ListTable headers={["Tháng", "Tổng thu nhập", "Khấu trừ", "Thực nhận", "Thao tác"]} alignRight={[1, 2, 3, 4]}
                empty="Chưa có phiếu lương.">
                {payslips.map((slip) => (
                  <TableRow key={slip.id} className="cursor-pointer" onClick={() => setSelectedPayslip(slip)}>
                    <TableCell className="font-medium">{periodLabel(slip.periodYear, slip.periodMonth)}</TableCell>
                    <TableCell className="text-right tabular-nums">{money(slip.grossPay)}</TableCell>
                    <TableCell className="text-right tabular-nums">−{money(payslipDeductions(slip))}</TableCell>
                    <TableCell className="text-right font-semibold tabular-nums">{money(slip.netPay)}</TableCell>
                    <RowActionsCell label={periodLabel(slip.periodYear, slip.periodMonth)}>
                      <RowActionItem icon={Eye} onClick={() => setSelectedPayslip(slip)}>Xem phiếu lương</RowActionItem>
                    </RowActionsCell>
                  </TableRow>
                ))}
              </ListTable>
            </TabsContent>
          )}
        </Tabs>
      </CardContent>

      {form === "salary" && (
        <SalaryDialog defaultDate={firstDate(salaries)} busy={busy} onClose={() => setForm(null)}
          onSubmit={(body) => run("Đã lưu mức lương", () => workforceApi(`${base}/salary-history`, "POST", body))} />
      )}
      {form === "profile" && (
        <ProfileDialog defaultDate={firstDate(profiles)} defaultSalary={currentSalary?.baseSalary} busy={busy}
          onClose={() => setForm(null)}
          onSubmit={(body) => run("Đã lưu hồ sơ bảo hiểm", () => workforceApi(`${base}/payroll-profiles`, "POST", body))} />
      )}
      {form === "dependent" && (
        <DependentDialog busy={busy} onClose={() => setForm(null)}
          onSubmit={(body) => run("Đã thêm người phụ thuộc", () => workforceApi(`${base}/tax-dependents`, "POST", body))} />
      )}
      {ending && (
        <EndDependentDialog dependent={ending} busy={busy} onClose={() => setEnding(null)}
          onSubmit={(effectiveTo) => run("Đã kết thúc người phụ thuộc", () => workforceApi(
            `${base}/tax-dependents/${ending.id}/end`, "PUT", { effectiveTo } satisfies WorkforceBody["EndDependentCommand"]))} />
      )}
      {selectedPayslip && <PayslipDialog payslip={selectedPayslip} onClose={() => setSelectedPayslip(null)} />}
    </Card>
  )
}

function Toolbar({ hint, children }: { hint: string; children: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <p className="text-sm text-muted-foreground">{hint}</p>
      {children}
    </div>
  )
}

function ListTable({ headers, alignRight, empty, children }: {
  headers: string[]
  alignRight: number[]
  empty: string
  children: ReactNode[]
}) {
  return (
    <div className="rounded-xl border">
      <Table>
        <TableHeader>
          <TableRow>
            {headers.map((header, index) => (
              <TableHead key={header} className={alignRight.includes(index) ? "text-right" : undefined}>{header}</TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {children}
          {children.length === 0 && <EmptyRow colSpan={headers.length}>{empty}</EmptyRow>}
        </TableBody>
      </Table>
    </div>
  )
}

/** Shared frame of the add dialogs: title, fields, cancel and save. */
function FormDialog({ title, description, busy, onClose, onSubmit, children }: {
  title: string
  description: string
  busy: boolean
  onClose: () => void
  onSubmit: () => Promise<boolean>
  children: ReactNode
}) {
  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={(event) => {
          event.preventDefault()
          void onSubmit().then((ok) => { if (ok) onClose() })
        }}>
          <DialogHeader>
            <DialogTitle>{title}</DialogTitle>
            <DialogDescription>{description}</DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-4">{children}</div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Hủy</Button>
            <Button type="submit" disabled={busy}>{busy && <Loader2 className="animate-spin" />} Lưu</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function SalaryDialog({ defaultDate, busy, onClose, onSubmit }: {
  defaultDate: string
  busy: boolean
  onClose: () => void
  onSubmit: (body: WorkforceBody["SetSalaryCommand"]) => Promise<boolean>
}) {
  const [amount, setAmount] = useState("")
  const [effectiveFrom, setEffectiveFrom] = useState(defaultDate)
  const [reason, setReason] = useState("")
  return (
    <FormDialog title="Thêm mức lương" description="Mức đang áp dụng tự kết thúc vào ngày trước ngày hiệu lực." busy={busy}
      onClose={onClose}
      onSubmit={() => onSubmit({ baseSalary: Number(amount), effectiveFrom, reason: reason.trim() || null, note: null })}>
      <div className="grid gap-1.5">
        <Label htmlFor="salary-amount">Lương tháng (VND)</Label>
        <Input id="salary-amount" required type="number" min="1" step="1" value={amount} onChange={(event) => setAmount(event.target.value)} />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="salary-from">Hiệu lực từ</Label>
        <Input id="salary-from" required type="date" value={effectiveFrom} onChange={(event) => setEffectiveFrom(event.target.value)} />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="salary-reason">Lý do</Label>
        <Input id="salary-reason" placeholder="VD: Mức lương ban đầu, điều chỉnh định kỳ" value={reason}
          onChange={(event) => setReason(event.target.value)} />
      </div>
    </FormDialog>
  )
}

function ProfileDialog({ defaultDate, defaultSalary, busy, onClose, onSubmit }: {
  defaultDate: string
  defaultSalary?: number
  busy: boolean
  onClose: () => void
  onSubmit: (body: WorkforceBody["ProfileCommand"]) => Promise<boolean>
}) {
  const [effectiveFrom, setEffectiveFrom] = useState(defaultDate)
  const [insuranceSalary, setInsuranceSalary] = useState(defaultSalary ? String(defaultSalary) : "")
  const [wageRegion, setWageRegion] = useState("1")
  const [social, setSocial] = useState(true)
  const [health, setHealth] = useState(true)
  const [unemployment, setUnemployment] = useState(true)
  return (
    <FormDialog title="Thêm hồ sơ bảo hiểm & thuế"
      description="Nhân viên cư trú tại Việt Nam. Hồ sơ đang áp dụng tự kết thúc vào ngày trước ngày hiệu lực." busy={busy}
      onClose={onClose}
      onSubmit={() => onSubmit({
        effectiveFrom, taxResident: true, socialInsurance: social, healthInsurance: health,
        unemploymentInsurance: unemployment, insuranceSalary: Number(insuranceSalary), wageRegion: Number(wageRegion),
      })}>
      <div className="grid gap-1.5">
        <Label htmlFor="profile-from">Hiệu lực từ</Label>
        <Input id="profile-from" required type="date" value={effectiveFrom} onChange={(event) => setEffectiveFrom(event.target.value)} />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="profile-salary">Lương đóng bảo hiểm (VND)</Label>
        <Input id="profile-salary" required type="number" min="0" step="1" value={insuranceSalary}
          onChange={(event) => setInsuranceSalary(event.target.value)} />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="profile-region">Vùng lương tối thiểu</Label>
        <Select value={wageRegion} items={REGION_ITEMS} onValueChange={(value) => setWageRegion(value ?? "1")}>
          <SelectTrigger id="profile-region" className="w-full"><SelectValue /></SelectTrigger>
          <SelectContent>
            {Object.entries(REGION_ITEMS).map(([value, label]) => <SelectItem key={value} value={value}>{label}</SelectItem>)}
          </SelectContent>
        </Select>
      </div>
      <fieldset className="grid gap-1.5">
        <legend className="mb-1.5 text-sm font-medium">Tham gia bảo hiểm</legend>
        <div className="flex gap-4">
          {([["BHXH", social, setSocial], ["BHYT", health, setHealth], ["BHTN", unemployment, setUnemployment]] as const)
            .map(([label, checked, set]) => (
              <label key={label} className="flex items-center gap-1.5 text-sm">
                <input type="checkbox" checked={checked} onChange={(event) => set(event.target.checked)} /> {label}
              </label>
            ))}
        </div>
      </fieldset>
    </FormDialog>
  )
}

function DependentDialog({ busy, onClose, onSubmit }: {
  busy: boolean
  onClose: () => void
  onSubmit: (body: WorkforceBody["DependentCommand"]) => Promise<boolean>
}) {
  const [fullName, setFullName] = useState("")
  const [identifier, setIdentifier] = useState("")
  const [effectiveFrom, setEffectiveFrom] = useState(today())
  return (
    <FormDialog title="Thêm người phụ thuộc" description="Người phụ thuộc đã đăng ký giảm trừ gia cảnh với cơ quan thuế." busy={busy}
      onClose={onClose}
      onSubmit={() => onSubmit({ fullName: fullName.trim(), identifier: identifier.trim() || null, effectiveFrom, effectiveTo: null })}>
      <div className="grid gap-1.5">
        <Label htmlFor="dependent-name">Họ tên</Label>
        <Input id="dependent-name" required value={fullName} onChange={(event) => setFullName(event.target.value)} />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="dependent-identifier">Số định danh (CCCD / mã số thuế)</Label>
        <Input id="dependent-identifier" value={identifier} onChange={(event) => setIdentifier(event.target.value)} />
      </div>
      <div className="grid gap-1.5">
        <Label htmlFor="dependent-from">Giảm trừ từ ngày</Label>
        <Input id="dependent-from" required type="date" value={effectiveFrom} onChange={(event) => setEffectiveFrom(event.target.value)} />
      </div>
    </FormDialog>
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
    <FormDialog title={`Kết thúc giảm trừ cho ${dependent.fullName}`}
      description={`Ngày cuối cùng còn được giảm trừ; không được trước ${dependent.effectiveFrom}.`} busy={busy}
      onClose={onClose} onSubmit={() => onSubmit(effectiveTo)}>
      <div className="grid gap-1.5">
        <Label htmlFor="dependent-end">Ngày kết thúc</Label>
        <Input id="dependent-end" type="date" required min={dependent.effectiveFrom} value={effectiveTo}
          onChange={(event) => setEffectiveTo(event.target.value)} />
      </div>
    </FormDialog>
  )
}

