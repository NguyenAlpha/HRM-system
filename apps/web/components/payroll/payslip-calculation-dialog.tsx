"use client"

import type { ReactNode } from "react"
import { useQuery } from "@tanstack/react-query"

import { periodLabel } from "@/components/payroll/payroll-shared"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { SectionError, money, workforceApi } from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import type { Payslip, PayslipCalculation } from "@/lib/workforce"

const number = (value: number) => value.toLocaleString("vi-VN", { maximumFractionDigits: 2 })
const percent = (rate: number) => `${number(rate * 100)}%`
const hoursOf = (minutes: number) => `${number(minutes / 60)} giờ`

/**
 * How one payslip was calculated, step by step with its own figures: pay for the hours worked,
 * allowances, overtime, insurance, the family deductions and each tax bracket, then net pay. Kept
 * apart from the payslip so the slip itself stays a plain statement.
 */
export function PayslipCalculationDialog({ payslip, onClose }: { payslip: Payslip; onClose: () => void }) {
  // A released payslip never changes, so its calculation is fetched once per open payslip.
  const { data: calc, error, isPending } = useQuery({
    queryKey: ["payslip-calculation", payslip.id],
    queryFn: () => workforceApi<PayslipCalculation>(`payroll/payslips/${payslip.id}/calculation`),
    staleTime: Infinity,
  })

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>Cách tính lương tháng {periodLabel(payslip.periodYear, payslip.periodMonth)}</DialogTitle>
          <DialogDescription>
            {payslip.employeeNameSnapshot} · {payslip.employeeCodeSnapshot}. Mỗi bước ghi công thức, rồi thay số
            của phiếu lương vào; kết quả từng bước khớp với phiếu.
          </DialogDescription>
        </DialogHeader>
        <SectionError error={error ? apiErrorMessage(error) : null} />
        {isPending && <Skeleton className="h-96 w-full rounded-xl" />}
        {calc && <Steps payslip={payslip} calc={calc} />}
      </DialogContent>
    </Dialog>
  )
}

function Steps({ payslip, calc }: { payslip: Payslip; calc: PayslipCalculation }) {
  const items = payslip.items
  const base = items.find((item) => item.componentType === "BASE_SALARY")
  const allowances = items.filter((item) =>
    item.componentType === "POSITION_ALLOWANCE" || item.componentType === "SENIORITY_ALLOWANCE")
  const overtime = items.filter((item) => item.componentType === "OVERTIME")
  const others = items.filter((item) =>
    ["BONUS", "TAXABLE_ALLOWANCE", "NON_TAXABLE_ALLOWANCE"].includes(item.componentType))
  const insurance = payslip.employeeSocialInsurance + payslip.employeeHealthInsurance + payslip.employeeUnemploymentInsurance
  const familyDeduction = calc.personalDeduction + calc.dependentDeduction * calc.dependentCount
  // Hours of one shift, recovered from the hourly rate the base salary line was paid at.
  const shiftHours = base && base.unitRate > 0 ? payslip.contractualBasicSalary / (calc.companyWorkdays * base.unitRate) : 0
  const insured = payslip.insuranceSalaryBase > 0
  // Share of the month's standard hours that was paid; allowances are prorated by it.
  const workRatio = shiftHours > 0 ? payslip.payableWorkMinutes / (calc.companyWorkdays * shiftHours * 60) : 0

  return (
    <ol className="grid gap-3">
      <Step n={1} title="Lương theo công" result={payslip.basicSalaryPay}
        formula="Lương cơ bản ÷ (Ngày công chuẩn của tháng × Giờ một ca) × Giờ công được trả">
        <p>Ngày công chuẩn của tháng: <b>{calc.companyWorkdays} ngày</b> (thứ 2 đến thứ 7, trừ ngày lễ của công ty).</p>
        {base && (
          <>
            <Calc>
              Lương 1 giờ = {money(payslip.contractualBasicSalary)} ÷ ({calc.companyWorkdays} × {number(shiftHours)} giờ)
              = <b>{money(base.unitRate)}</b>
            </Calc>
            <Calc>
              Lương theo công = {money(base.unitRate)} × {hoursOf(payslip.payableWorkMinutes)} = <b>{money(payslip.basicSalaryPay)}</b>
            </Calc>
          </>
        )}
        <Note>
          Giờ công được trả gồm giờ đi làm và giờ nghỉ có lương (phép năm, nghỉ lễ…); nghỉ không lương và vắng không
          phép không được tính. Nếu lương đổi giữa tháng, hệ thống tính từng ngày theo mức lương của ngày đó rồi cộng lại.
        </Note>
      </Step>

      <Step n={2} title="Phụ cấp" result={payslip.allowancePay}
        formula="Phụ cấp chức vụ theo tháng × Tỉ lệ công + Lương cơ bản × % thâm niên × Tỉ lệ công">
        {allowances.length === 0 && <p>Tháng này không có phụ cấp.</p>}
        {allowances.length > 0 && workRatio > 0 && (
          <Calc>
            Tỉ lệ công = {hoursOf(payslip.payableWorkMinutes)} ÷ ({calc.companyWorkdays} × {number(shiftHours)} giờ)
            {" "}= <b>{percent(workRatio)}</b>
          </Calc>
        )}
        {workRatio > 0 && allowances.map((item) => (
          <Calc key={item.id}>
            {item.description} ={" "}
            {item.componentType === "SENIORITY_ALLOWANCE"
              ? <>{money(item.unitRate)} × {percent(item.amount / (item.unitRate * workRatio))}</>
              : <>{money(Math.round(item.amount / workRatio))}</>}
            {" "}× {percent(workRatio)} = <b>{money(item.amount)}</b>
          </Calc>
        ))}
        <Note>
          Vắng không lương làm giảm tỉ lệ công nên cũng làm giảm phụ cấp. Phụ cấp chức vụ theo vị trí công việc; % thâm
          niên theo số năm làm việc.
        </Note>
      </Step>

      <Step n={3} title="Tăng ca" result={payslip.overtimePay}
        formula="Lương 1 giờ × Giờ tăng ca được duyệt × Hệ số (ngày thường 1,5 · Chủ nhật 2 · ngày lễ 3)">
        {overtime.length === 0 && <p>Tháng này không có tăng ca được duyệt.</p>}
        {overtime.map((item) => (
          <Calc key={item.id}>
            {item.description}: {money(item.unitRate)} × {number(item.quantity)} giờ × {number(item.multiplier)}
            {" "}= <b>{money(item.amount)}</b>
          </Calc>
        ))}
      </Step>

      <Step n={4} title="Thưởng và phụ cấp khác" result={payslip.otherEarningsPay}
        formula="Các khoản kế toán nhập cho kỳ lương">
        {others.length === 0 && <p>Tháng này không có khoản thưởng hay phụ cấp khác.</p>}
        {others.map((item) => (
          <Calc key={item.id}>
            {item.description}{item.componentType === "NON_TAXABLE_ALLOWANCE" ? " (miễn thuế)" : ""} = <b>{money(item.amount)}</b>
          </Calc>
        ))}
      </Step>

      <Step n={5} title="Tổng thu nhập" result={payslip.grossPay}
        formula="Lương theo công + Phụ cấp + Tăng ca + Thưởng, phụ cấp khác">
        <Calc>
          {money(payslip.basicSalaryPay)} + {money(payslip.allowancePay)} + {money(payslip.overtimePay)}
          {" "}+ {money(payslip.otherEarningsPay)} = <b>{money(payslip.grossPay)}</b>
        </Calc>
      </Step>

      <Step n={6} title="Bảo hiểm bắt buộc (phần người lao động)" result={insurance} sign="−"
        formula="Căn cứ đóng × tỉ lệ; căn cứ là lương đóng bảo hiểm HR khai báo, có trần">
        {insured ? (
          <>
            <Calc>BHXH = {money(payslip.insuranceSalaryBase)} × {percent(calc.socialRate)} = <b>{money(payslip.employeeSocialInsurance)}</b></Calc>
            <Calc>BHYT = {money(payslip.insuranceSalaryBase)} × {percent(calc.healthRate)} = <b>{money(payslip.employeeHealthInsurance)}</b></Calc>
            <Calc>BHTN = {money(payslip.unemploymentInsuranceBase)} × {percent(calc.unemploymentRate)} = <b>{money(payslip.employeeUnemploymentInsurance)}</b></Calc>
          </>
        ) : (
          <p>Tháng này không đóng bảo hiểm: không làm việc từ 14 ngày công trở lên, hoặc hồ sơ không tham gia.</p>
        )}
        <Note>Căn cứ đóng tách khỏi lương thực nhận, nên tăng ca và thưởng không làm tăng bảo hiểm.</Note>
      </Step>

      <Step n={7} title="Thu nhập tính thuế" result={payslip.taxableIncome}
        formula="Tổng thu nhập − Phụ cấp miễn thuế − Bảo hiểm − Giảm trừ bản thân − Giảm trừ người phụ thuộc (không âm)">
        <Calc>
          Giảm trừ gia cảnh = {money(calc.personalDeduction)} + {calc.dependentCount} người phụ thuộc × {money(calc.dependentDeduction)}
          {" "}= <b>{money(familyDeduction)}</b>
        </Calc>
        <Calc>
          {money(payslip.grossPay)} − {money(payslip.taxExemptOtherEarnings)} − {money(insurance)} − {money(familyDeduction)}
          {" "}= <b>{money(payslip.taxableIncome)}</b>
        </Calc>
        {payslip.taxableIncome === 0 && <Note>Kết quả âm được tính là 0, nên tháng này không phải nộp thuế.</Note>}
      </Step>

      <Step n={8} title="Thuế TNCN (lũy tiến từng phần)" result={payslip.personalIncomeTax} sign="−"
        formula="Mỗi bậc đánh thuế phần thu nhập nằm trong bậc đó theo thuế suất của bậc, rồi cộng lại">
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-xs">
            <thead className="bg-muted/50 text-muted-foreground">
              <tr>
                <th className="px-2 py-1.5 text-left font-medium">Bậc</th>
                <th className="px-2 py-1.5 text-left font-medium">Thu nhập tính thuế/tháng</th>
                <th className="px-2 py-1.5 text-right font-medium">Thuế suất</th>
                <th className="px-2 py-1.5 text-right font-medium">Phần thu nhập trong bậc</th>
                <th className="px-2 py-1.5 text-right font-medium">Thuế</th>
              </tr>
            </thead>
            <tbody className="tabular-nums">
              {calc.taxBrackets.map((bracket, index) => (
                <tr key={bracket.lowerBound} className={`border-t ${bracket.taxedIncome > 0 ? "" : "text-muted-foreground"}`}>
                  <td className="px-2 py-1.5">{index + 1}</td>
                  <td className="px-2 py-1.5">
                    {bracket.upperBound === null
                      ? `Trên ${money(bracket.lowerBound)}`
                      : `${money(bracket.lowerBound)} – ${money(bracket.upperBound)}`}
                  </td>
                  <td className="px-2 py-1.5 text-right">{percent(bracket.rate)}</td>
                  <td className="px-2 py-1.5 text-right">{money(bracket.taxedIncome)}</td>
                  <td className="px-2 py-1.5 text-right">{money(bracket.tax)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Step>

      <Step n={9} title="Thực nhận" result={payslip.netPay} strong
        formula="Tổng thu nhập − Bảo hiểm − Thuế TNCN − Khấu trừ khác">
        <Calc>
          {money(payslip.grossPay)} − {money(insurance)} − {money(payslip.personalIncomeTax)} − {money(payslip.otherDeductions)}
          {" "}= <b>{money(payslip.netPay)}</b>
        </Calc>
        <Note>Khấu trừ khác (tạm ứng, phạt…) trừ sau thuế nên không làm giảm thu nhập tính thuế.</Note>
      </Step>
    </ol>
  )
}

function Step({ n, title, formula, result, sign = "", strong = false, children }: {
  n: number
  title: string
  formula: string
  result: number
  sign?: string
  strong?: boolean
  children: ReactNode
}) {
  return (
    <li className={`grid gap-2 rounded-xl border p-4 text-sm ${strong ? "bg-primary/5" : ""}`}>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="font-semibold">{n}. {title}</h3>
        <span className={`tabular-nums font-semibold ${strong ? "text-lg text-primary" : ""}`}>
          {result === 0 ? "" : sign}{money(result)}
        </span>
      </div>
      <p className="rounded-md bg-muted/60 px-2.5 py-1.5 font-mono text-xs">{formula}</p>
      {children}
    </li>
  )
}

function Calc({ children }: { children: ReactNode }) {
  return <p className="tabular-nums">{children}</p>
}

function Note({ children }: { children: ReactNode }) {
  return <p className="text-xs text-muted-foreground">{children}</p>
}
