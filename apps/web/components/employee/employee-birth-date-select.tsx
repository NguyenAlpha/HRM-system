"use client"

import { useState } from "react"

import { daysInMonth, maximumEmployeeBirthDate } from "@/lib/employee-birth-date"

type BirthDateParts = { year: number | null; month: number | null; day: number | null }

function parseBirthDate(value: string): BirthDateParts {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  return match
    ? { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) }
    : { year: null, month: null, day: null }
}

const selectClassName = "h-9 min-w-0 w-full rounded-md border border-input bg-background px-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 aria-invalid:border-destructive"

export function EmployeeBirthDateSelect({ value, onChange, id, invalid = false }: {
  value: string
  onChange: (value: string) => void
  id: string
  invalid?: boolean
}) {
  const [parts, setParts] = useState<BirthDateParts>(() => parseBirthDate(value))
  const cutoff = maximumEmployeeBirthDate()
  const cutoffYear = Number(cutoff.slice(0, 4))
  const cutoffMonth = Number(cutoff.slice(5, 7))
  const cutoffDay = Number(cutoff.slice(8, 10))
  const years = Array.from({ length: cutoffYear - 1900 + 1 }, (_, index) => cutoffYear - index)
  const lastMonth = parts.year === cutoffYear ? cutoffMonth : 12
  const lastDay = parts.year && parts.month
    ? Math.min(daysInMonth(parts.year, parts.month),
      parts.year === cutoffYear && parts.month === cutoffMonth ? cutoffDay : 31)
    : 0

  function update(next: BirthDateParts) {
    setParts(next)
    onChange(next.year && next.month && next.day
      ? `${next.year}-${String(next.month).padStart(2, "0")}-${String(next.day).padStart(2, "0")}`
      : "")
  }

  function changeYear(raw: string) {
    const year = raw ? Number(raw) : null
    const month = year && parts.month && (year < cutoffYear || parts.month <= cutoffMonth)
      ? parts.month : null
    const maxDay = year && month
      ? Math.min(daysInMonth(year, month), year === cutoffYear && month === cutoffMonth ? cutoffDay : 31)
      : 0
    update({ year, month, day: parts.day && parts.day <= maxDay ? parts.day : null })
  }

  function changeMonth(raw: string) {
    const month = raw ? Number(raw) : null
    const maxDay = parts.year && month
      ? Math.min(daysInMonth(parts.year, month), parts.year === cutoffYear && month === cutoffMonth ? cutoffDay : 31)
      : 0
    update({ year: parts.year, month, day: parts.day && parts.day <= maxDay ? parts.day : null })
  }

  return (
    <div className="grid grid-cols-3 gap-2" role="group" aria-label="Ngày sinh">
      <select id={id} aria-label="Năm sinh" aria-invalid={invalid} className={selectClassName}
        value={parts.year ?? ""} onChange={(event) => changeYear(event.target.value)}>
        <option value="">Năm</option>
        {years.map((year) => <option key={year} value={year}>{year}</option>)}
      </select>
      <select aria-label="Tháng sinh" aria-invalid={invalid} className={selectClassName}
        value={parts.month ?? ""} disabled={!parts.year}
        onChange={(event) => changeMonth(event.target.value)}>
        <option value="">Tháng</option>
        {Array.from({ length: lastMonth }, (_, index) => index + 1)
          .map((month) => <option key={month} value={month}>{month}</option>)}
      </select>
      <select aria-label="Ngày sinh" aria-invalid={invalid} className={selectClassName}
        value={parts.day ?? ""} disabled={!parts.month}
        onChange={(event) => update({ ...parts, day: event.target.value ? Number(event.target.value) : null })}>
        <option value="">Ngày</option>
        {Array.from({ length: lastDay }, (_, index) => index + 1)
          .map((day) => <option key={day} value={day}>{day}</option>)}
      </select>
    </div>
  )
}
