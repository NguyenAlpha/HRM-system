"use client"

import { useState } from "react"
import { ChevronLeft, ChevronRight, Download } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

/** A cell of an exported table; numbers stay numbers so Excel can sum them. */
type CsvCell = string | number | null | undefined

/**
 * Downloads a table as CSV for Excel: UTF-8 with a byte-order mark so Vietnamese text opens
 * correctly, comma separated, every text cell quoted.
 */
export function downloadCsv(filename: string, header: string[], rows: CsvCell[][]) {
  const cell = (value: CsvCell) => typeof value === "number" ? String(value)
    : `"${(value ?? "").replaceAll("\"", "\"\"")}"`
  const csv = [header, ...rows].map((row) => row.map(cell).join(",")).join("\r\n")
  const url = URL.createObjectURL(new Blob(["﻿", csv], { type: "text/csv;charset=utf-8" }))
  const link = document.createElement("a")
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

export function ExportButton({ onClick, disabled }: { onClick: () => void; disabled?: boolean }) {
  return (
    <Button type="button" variant="outline" size="sm" onClick={onClick} disabled={disabled}>
      <Download /> Xuất CSV
    </Button>
  )
}

/** Previous / next year around the year shown; the next year stops at `maxYear`. */
export function YearPicker({ year, maxYear, onChange }: { year: number; maxYear?: number; onChange: (year: number) => void }) {
  return (
    <div className="flex items-center gap-1">
      <Button type="button" variant="outline" size="icon-sm" aria-label="Năm trước" onClick={() => onChange(year - 1)}>
        <ChevronLeft />
      </Button>
      <span className="w-14 text-center font-semibold tabular-nums">{year}</span>
      <Button type="button" variant="outline" size="icon-sm" aria-label="Năm sau" disabled={maxYear !== undefined && year >= maxYear}
        onClick={() => onChange(year + 1)}>
        <ChevronRight />
      </Button>
    </div>
  )
}

/** Previous / next month around a YYYY-MM month, with the month input between. */
export function MonthPicker({ month, onChange }: { month: string; onChange: (month: string) => void }) {
  const shift = (by: number) => {
    const date = new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)) - 1 + by, 1))
    onChange(date.toISOString().slice(0, 7))
  }
  return (
    <div className="flex items-center gap-1">
      <Button type="button" variant="outline" size="icon-sm" aria-label="Tháng trước" onClick={() => shift(-1)}><ChevronLeft /></Button>
      <Input type="month" aria-label="Tháng" className="w-44" value={month}
        onChange={(event) => { if (event.target.value) onChange(event.target.value) }} />
      <Button type="button" variant="outline" size="icon-sm" aria-label="Tháng sau" onClick={() => shift(1)}><ChevronRight /></Button>
    </div>
  )
}

/** A headline figure with a short caption under it. */
export function StatTile({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-xl border bg-card px-4 py-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
      {hint && <p className="mt-0.5 text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}

/**
 * One series over the months of a year as columns: thin bars with a rounded data end on a shared
 * baseline, 2px apart, recessive gridlines, a tooltip per column. A month without data shows no bar.
 */
export function MonthlyColumnChart({ label, points, format }: {
  /** What the series measures; names the chart for screen readers. */
  label: string
  points: { month: number; value: number | null }[]
  format: (value: number) => string
}) {
  const [hovered, setHovered] = useState<number | null>(null)
  const max = Math.max(0, ...points.map((point) => point.value ?? 0))
  const top = max > 0 ? max : 1
  const shown = points.find((point) => point.month === hovered)

  return (
    <figure className="grid gap-2 pt-3" aria-label={label}>
      <div className="relative h-44">
        {[1, 0.5, 0].map((fraction) => (
          <div key={fraction} className="absolute inset-x-0 flex items-center gap-2" style={{ bottom: `${fraction * 100}%` }}>
            <span className="w-16 shrink-0 -translate-y-1/2 text-right text-[10px] text-muted-foreground tabular-nums">
              {format(top * fraction)}
            </span>
            <span className={`h-px flex-1 -translate-y-1/2 ${fraction === 0 ? "bg-border" : "bg-border/50"}`} />
          </div>
        ))}
        <div className="absolute inset-y-0 right-0 left-[4.5rem] flex items-end gap-[2px]">
          {points.map((point) => (
            <div key={point.month} className="relative flex h-full flex-1 items-end justify-center"
              onMouseEnter={() => setHovered(point.month)} onMouseLeave={() => setHovered(null)}>
              {point.value !== null && point.value > 0 && (
                <div className={`w-full max-w-7 rounded-t-[4px] transition-opacity ${hovered === null || hovered === point.month ? "" : "opacity-50"}`}
                  style={{ height: `${(point.value / top) * 100}%`, background: "var(--green)" }} />
              )}
            </div>
          ))}
        </div>
        {shown && (
          <div role="status" className="pointer-events-none absolute -top-2 right-0 rounded-md border bg-popover px-2.5 py-1.5 text-xs shadow-md">
            <span className="text-muted-foreground">Tháng {shown.month}: </span>
            <span className="font-semibold tabular-nums">{shown.value === null ? "Chưa có dữ liệu" : format(shown.value)}</span>
          </div>
        )}
      </div>
      <div className="ml-[4.5rem] flex gap-[2px] text-center text-[10px] text-muted-foreground">
        {points.map((point) => <span key={point.month} className="flex-1">T{point.month}</span>)}
      </div>
    </figure>
  )
}

/** Short money for axis labels: 1,5 tỷ, 320 tr. */
export const shortMoney = (value: number) => value >= 1e9
  ? `${(value / 1e9).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} tỷ`
  : `${Math.round(value / 1e6).toLocaleString("vi-VN")} tr`
