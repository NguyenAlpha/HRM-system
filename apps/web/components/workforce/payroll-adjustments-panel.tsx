"use client"

import { useEffect, useState } from "react"
import { Loader2, Trash2 } from "lucide-react"
import { toast } from "sonner"

import { RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { apiErrorMessage } from "@/lib/api-helpers"
import { authorizedRequest } from "@/lib/auth/client"
import type { PayrollAdjustment, PayrollAdjustmentType, WorkforceBody } from "@/lib/workforce"

export const PAYROLL_ADJUSTMENT_TYPES = [
  "BONUS", "TAXABLE_ALLOWANCE", "NON_TAXABLE_ALLOWANCE", "DEDUCTION",
] as const satisfies readonly PayrollAdjustmentType[]

export const PAYROLL_ADJUSTMENT_LABELS: Record<PayrollAdjustmentType, string> = {
  BONUS: "Thưởng",
  TAXABLE_ALLOWANCE: "Phụ cấp khác (chịu thuế)",
  NON_TAXABLE_ALLOWANCE: "Phụ cấp khác (miễn thuế)",
  DEDUCTION: "Khấu trừ (tạm ứng, phạt)",
}

const TYPE_ITEMS = { ...PAYROLL_ADJUSTMENT_LABELS }

const money = (amount: number) => new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount)

const request = <T,>(path: string, method = "GET", body?: unknown) => authorizedRequest<T>("hrm", `/workforce/${path}`, {
  method,
  ...(body === undefined ? {} : { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }),
})

/**
 * Bonuses, other allowances and deductions for one payroll period. They reach payslips on the
 * next calculation, so any change sends a calculated period back to draft (`onChanged` reloads it).
 */
export function PayrollAdjustmentsPanel({ periodId, editable, onChanged }: {
  periodId: number
  editable: boolean
  onChanged: () => void
}) {
  const [rows, setRows] = useState<PayrollAdjustment[]>([])
  const [employeeId, setEmployeeId] = useState("")
  const [type, setType] = useState<PayrollAdjustmentType>("BONUS")
  const [description, setDescription] = useState("")
  const [amount, setAmount] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let active = true
    request<PayrollAdjustment[]>(`payroll/periods/${periodId}/adjustments`)
      .then((result) => { if (active) setRows(result) })
      .catch((caught: unknown) => { if (active) setError(apiErrorMessage(caught)) })
    return () => { active = false }
  }, [periodId, reloadKey])

  async function act(action: () => Promise<unknown>, done: string) {
    setBusy(true)
    setError("")
    try {
      await action()
      toast.success(done, { description: "Tính lại kỳ lương để đưa vào phiếu lương." })
      setReloadKey((key) => key + 1)
      onChanged()
    } catch (caught) {
      setError(apiErrorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  function add(event: React.FormEvent) {
    event.preventDefault()
    void act(async () => {
      await request(`payroll/periods/${periodId}/adjustments`, "POST", {
        employeeId: Number(employeeId), type, description: description.trim(), amount: Number(amount),
      } satisfies WorkforceBody["CreatePayrollAdjustmentRequest"])
      setDescription("")
      setAmount("")
    }, "Đã thêm khoản điều chỉnh")
  }

  return (
    <div className="w-full space-y-3 rounded-lg border bg-muted/30 p-3">
      <div className="rounded-xl border bg-background">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Nhân viên</TableHead>
              <TableHead>Loại</TableHead>
              <TableHead>Nội dung</TableHead>
              <TableHead className="text-right">Số tiền</TableHead>
              {editable && <TableHead className="text-right">Thao tác</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.id}>
                <TableCell>{row.employeeCode} · {row.employeeName}</TableCell>
                <TableCell>{PAYROLL_ADJUSTMENT_LABELS[row.type]}</TableCell>
                <TableCell>{row.description}</TableCell>
                <TableCell className="text-right">{row.type === "DEDUCTION" ? "−" : ""}{money(row.amount)}</TableCell>
                {editable && <RowActionsCell label={row.description}>
                  <RowActionItem icon={Trash2} destructive disabled={busy}
                    onClick={() => void act(() => request(`payroll/periods/${periodId}/adjustments/${row.id}`, "DELETE"), "Đã xóa khoản điều chỉnh")}>
                    Xóa
                  </RowActionItem>
                </RowActionsCell>}
              </TableRow>
            ))}
            {rows.length === 0 && (
              <TableRow><TableCell colSpan={editable ? 5 : 4} className="h-24 text-center text-muted-foreground">Chưa có khoản điều chỉnh.</TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {editable && <form className="grid gap-2 sm:grid-cols-[8rem_14rem_1fr_9rem_auto] sm:items-end" onSubmit={add}>
        <div className="grid gap-1.5">
          <Label htmlFor={`adjustment-employee-${periodId}`}>Mã nhân viên (ID)</Label>
          <Input id={`adjustment-employee-${periodId}`} required type="number" min="1" value={employeeId}
            onChange={(event) => setEmployeeId(event.target.value)} />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor={`adjustment-type-${periodId}`}>Loại</Label>
          <Select value={type} items={TYPE_ITEMS} onValueChange={(value) => setType(value as PayrollAdjustmentType)}>
            <SelectTrigger id={`adjustment-type-${periodId}`} className="w-full"><SelectValue /></SelectTrigger>
            <SelectContent>
              {PAYROLL_ADJUSTMENT_TYPES.map((value) => <SelectItem key={value} value={value}>{PAYROLL_ADJUSTMENT_LABELS[value]}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor={`adjustment-description-${periodId}`}>Nội dung</Label>
          <Input id={`adjustment-description-${periodId}`} required maxLength={250} value={description}
            onChange={(event) => setDescription(event.target.value)} />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor={`adjustment-amount-${periodId}`}>Số tiền (VND)</Label>
          <Input id={`adjustment-amount-${periodId}`} required type="number" min="1" step="1" value={amount}
            onChange={(event) => setAmount(event.target.value)} />
        </div>
        <Button type="submit" disabled={busy}>{busy && <Loader2 className="animate-spin" />} Thêm</Button>
      </form>}

      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    </div>
  )
}
