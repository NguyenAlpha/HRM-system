"use client"

import { useState } from "react"
import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { cn } from "cn"

/**
 * A YYYY-MM month shown as MM/yyyy, picked from a year of months. Stands in for the browser's month
 * input, whose spelled-out month name ("Tháng Mười Một 2026") does not fit and varies by browser.
 */
export function MonthInput({ id, value, onChange, className }: {
  id?: string
  value: string
  onChange: (month: string) => void
  className?: string
}) {
  const [open, setOpen] = useState(false)
  const [year, setYear] = useState(Number(value.slice(0, 4)))
  const selectedYear = Number(value.slice(0, 4))
  const selectedMonth = Number(value.slice(5, 7))

  return (
    <Popover open={open} onOpenChange={(next) => {
      // Each opening starts on the year of the month shown.
      if (next) setYear(selectedYear)
      setOpen(next)
    }}>
      <PopoverTrigger render={
        <Button id={id} type="button" variant="outline" className={cn("font-normal tabular-nums", className)} />
      }>
        {value.slice(5, 7)}/{value.slice(0, 4)} <CalendarDays className="text-muted-foreground" />
      </PopoverTrigger>
      <PopoverContent className="w-64">
        <div className="flex items-center justify-between">
          <Button type="button" variant="ghost" size="icon-sm" aria-label="Năm trước" onClick={() => setYear(year - 1)}>
            <ChevronLeft />
          </Button>
          <span className="font-semibold tabular-nums">{year}</span>
          <Button type="button" variant="ghost" size="icon-sm" aria-label="Năm sau" onClick={() => setYear(year + 1)}>
            <ChevronRight />
          </Button>
        </div>
        <div className="grid grid-cols-3 gap-1">
          {Array.from({ length: 12 }, (_, index) => index + 1).map((month) => (
            <Button key={month} type="button" size="sm"
              variant={year === selectedYear && month === selectedMonth ? "default" : "ghost"}
              onClick={() => {
                onChange(`${year}-${String(month).padStart(2, "0")}`)
                setOpen(false)
              }}>
              Tháng {month}
            </Button>
          ))}
        </div>
      </PopoverContent>
    </Popover>
  )
}
