"use client"

import { ChevronLeft, ChevronRight, Download } from "lucide-react"
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts"

import { Button } from "@/components/ui/button"
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from "@/components/ui/chart"
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

/** A headline figure with a short caption under it; same card style as the dashboard figures. */
export function StatTile({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="min-w-0 rounded-xl border bg-card p-5">
      <p className="text-sm text-muted-foreground">{label}</p>
      <p className="mt-1 truncate text-2xl font-semibold tracking-tight tabular-nums">{value}</p>
      {hint && <p className="mt-1 text-xs text-muted-foreground">{hint}</p>}
    </div>
  )
}

/**
 * One series over the months of a year as columns (Recharts via the shadcn chart wrapper): bars
 * capped at 24px with a 4px rounded data end, hairline horizontal gridlines, a tooltip per column.
 * A month without data shows no bar. The single series needs no legend; `label` names it.
 */
export function MonthlyColumnChart({ label, points, format }: {
  /** What the series measures; names the chart for screen readers and the tooltip. */
  label: string
  points: { month: number; value: number | null }[]
  format: (value: number) => string
}) {
  const data = points.map((point) => ({ month: point.month, value: point.value }))
  const config = { value: { label, color: "var(--chart-1)" } } satisfies ChartConfig

  return (
    <figure aria-label={label} className="pt-3">
      <ChartContainer config={config} className="aspect-auto h-52 w-full">
        <BarChart data={data} margin={{ top: 4, right: 0, bottom: 0, left: 0 }}>
          <CartesianGrid vertical={false} />
          <XAxis dataKey="month" tickLine={false} axisLine={false} tickMargin={8} tickFormatter={(month) => `T${month}`} />
          <YAxis tickLine={false} axisLine={false} width={64} tickCount={3} tickFormatter={(value) => format(Number(value))} />
          <ChartTooltip
            cursor={{ fill: "var(--muted)", opacity: 0.6 }}
            content={(
              <ChartTooltipContent
                labelFormatter={(_, payload) => `Tháng ${payload[0]?.payload.month ?? ""}`}
                formatter={(value) => (
                  <div className="flex w-full items-center justify-between gap-4">
                    <span className="text-muted-foreground">{label}</span>
                    <span className="font-medium tabular-nums">{format(Number(value))}</span>
                  </div>
                )}
              />
            )}
          />
          <Bar dataKey="value" fill="var(--color-value)" radius={[4, 4, 0, 0]} maxBarSize={24} />
        </BarChart>
      </ChartContainer>
    </figure>
  )
}

/** Short money for axis labels: 1,5 tỷ, 320 tr. */
export const shortMoney = (value: number) => value >= 1e9
  ? `${(value / 1e9).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} tỷ`
  : `${Math.round(value / 1e6).toLocaleString("vi-VN")} tr`
