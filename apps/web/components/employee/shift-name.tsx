"use client"

import { useState } from "react"
import { CircleHelp } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { hours } from "@/components/workforce/attendance-section"
import type { AssignmentShift } from "@/lib/employee"

/** "08:00:00" → "08:00". */
const clock = (time: string) => time.slice(0, 5)

/** A HH:mm time plus some minutes, wrapping past midnight. */
function addMinutes(time: string, minutes: number) {
  const total = (Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5)) + minutes) % (24 * 60)
  return `${String(Math.floor(total / 60)).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`
}

/** A shift's name with a "?" that opens its working hours: start and end, break, standard hours, grace. */
export function ShiftName({ name, shift }: { name: string; shift: AssignmentShift }) {
  const [open, setOpen] = useState(false)
  const rows: [string, string][] = [
    ["Giờ làm", `${clock(shift.startTime)}–${clock(shift.endTime)}${shift.crossesMidnight ? " (qua đêm)" : ""}`],
    ["Nghỉ giữa ca", shift.breakMinutes === 0 ? "Không có"
      : shift.breakStartTime
        ? `${clock(shift.breakStartTime)}–${addMinutes(shift.breakStartTime, shift.breakMinutes)} (${hours(shift.breakMinutes)})`
        : hours(shift.breakMinutes)],
    ["Công chuẩn mỗi ngày", hours(shift.standardWorkMinutes)],
    ["Được đi muộn", shift.graceLateMinutes === 0 ? "Không" : `${shift.graceLateMinutes} phút`],
  ]

  return (
    <span className="inline-flex items-center gap-1">
      {name}
      <Button type="button" variant="ghost" size="icon-xs" aria-label={`Xem giờ làm của ${name}`} title="Xem giờ làm"
        className="text-muted-foreground" onClick={() => setOpen(true)}>
        <CircleHelp />
      </Button>
      {open && (
        <Dialog open onOpenChange={(next) => { if (!next) setOpen(false) }}>
          <DialogContent className="sm:max-w-sm">
            <DialogHeader>
              <DialogTitle>{name}</DialogTitle>
              <DialogDescription>Giờ làm của ca; công tính lương không gồm giờ nghỉ giữa ca.</DialogDescription>
            </DialogHeader>
            <dl className="grid gap-3">
              {rows.map(([label, value]) => (
                <div key={label} className="flex items-baseline justify-between gap-4 text-sm">
                  <dt className="text-muted-foreground">{label}</dt>
                  <dd className="font-medium tabular-nums">{value}</dd>
                </div>
              ))}
            </dl>
          </DialogContent>
        </Dialog>
      )}
    </span>
  )
}
