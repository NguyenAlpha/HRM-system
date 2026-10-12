"use client"

import { useEffect, useState, type ReactNode } from "react"
import { ArrowDown, ArrowUp, ArrowUpDown, Search, SlidersHorizontal, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { TableHead } from "@/components/ui/table"

/** Column and direction sent to the API as `sort=field,direction`. */
export interface SortState {
  field: string
  direction: "asc" | "desc"
}

export function sortParam(sort: SortState): string {
  return `${sort.field},${sort.direction}`
}

/** Value that only updates after the user stops typing for `delay` ms. */
export function useDebouncedValue<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(timer)
  }, [value, delay])
  return debounced
}

/** Table header that sorts by `field`; clicking again flips the direction. */
export function SortableHead({ label, field, sort, onSort, className }: {
  label: string
  field: string
  sort: SortState
  onSort: (sort: SortState) => void
  className?: string
}) {
  const active = sort.field === field
  const Icon = !active ? ArrowUpDown : sort.direction === "asc" ? ArrowUp : ArrowDown
  return (
    <TableHead className={className} aria-sort={active ? (sort.direction === "asc" ? "ascending" : "descending") : "none"}>
      <button
        type="button"
        className="inline-flex items-center gap-1 font-medium hover:text-foreground"
        onClick={() => onSort({ field, direction: active && sort.direction === "asc" ? "desc" : "asc" })}
      >
        {label}
        <Icon className={`size-3.5 ${active ? "" : "opacity-40"}`} />
      </button>
    </TableHead>
  )
}

/** Keyword search plus a collapsible "advanced filters" panel holding the given filter controls. */
export function ListToolbar({ keyword, onKeywordChange, placeholder, activeFilters, onReset, children }: {
  keyword: string
  onKeywordChange: (keyword: string) => void
  placeholder: string
  /** Number of advanced filters currently applied; shown on the toggle. */
  activeFilters: number
  onReset: () => void
  children: ReactNode
}) {
  const [advancedOpen, setAdvancedOpen] = useState(activeFilters > 0)
  return (
    <div className="grid gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-60 flex-1">
          <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={keyword}
            onChange={(event) => onKeywordChange(event.target.value)}
            placeholder={placeholder}
            aria-label={placeholder}
            className="pl-8"
          />
        </div>
        <Button type="button" variant={advancedOpen ? "secondary" : "outline"} size="sm" onClick={() => setAdvancedOpen((open) => !open)}>
          <SlidersHorizontal /> Bộ lọc nâng cao{activeFilters > 0 ? ` (${activeFilters})` : ""}
        </Button>
        {(keyword || activeFilters > 0) && (
          <Button type="button" variant="ghost" size="sm" onClick={onReset}>
            <X /> Xóa bộ lọc
          </Button>
        )}
      </div>
      {advancedOpen && (
        <div className="grid gap-3 rounded-lg border bg-muted/30 p-3 sm:grid-cols-2 lg:grid-cols-3">{children}</div>
      )}
    </div>
  )
}

export const ALL_OPTION = "ALL"

/** Labelled select whose first option ({@link ALL_OPTION}) clears the filter. */
export function FilterSelect({ id, label, value, onChange, options, allLabel = "Tất cả" }: {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  options: { value: string; label: string }[]
  allLabel?: string
}) {
  const items = Object.fromEntries([[ALL_OPTION, allLabel], ...options.map((option) => [option.value, option.label])])
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Select value={value} items={items} onValueChange={(next) => onChange(String(next ?? ALL_OPTION))}>
        <SelectTrigger id={id} className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL_OPTION}>{allLabel}</SelectItem>
          {options.map((option) => <SelectItem key={option.value} value={option.value}>{option.label}</SelectItem>)}
        </SelectContent>
      </Select>
    </div>
  )
}
