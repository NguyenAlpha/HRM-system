"use client"

import { useState } from "react"
import { toast } from "sonner"

import { TableCell, TableRow } from "@/components/ui/table"
import { apiErrorMessage } from "@/lib/api-helpers"
import { authorizedRequest } from "@/lib/auth/client"

/** What every workforce section receives from the page shell. */
export type WorkforceContext = {
  /** Employee whose data is shown; 0 while none is selected. */
  employeeId: number
  /** "NV0002 · Trần Thị Bình" for titles; "—" while none is selected. */
  employeeLabel: string
  permissions: ReadonlySet<string>
  /** Selected month as YYYY-MM. */
  month: string
}

export const workforceApi = <T,>(path: string, method = "GET", body?: unknown) =>
  authorizedRequest<T>("hrm", `/workforce/${path}`, {
    method,
    ...(body === undefined ? {} : { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }),
  })

/** Today in the business time zone, as YYYY-MM-DD. */
export const today = () => new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit",
}).format(new Date())

export const money = (amount: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount)

export const toDays = (minutes: number, dayMinutes: number) =>
  dayMinutes > 0 ? Number((minutes / dayMinutes).toFixed(2)) : 0

export const dateTime = (instant: string | null) =>
  instant ? new Date(instant).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" }) : "—"

export const time = (instant: string | null) =>
  instant ? new Date(instant).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" }) : "—"

/** First and last day of a YYYY-MM month. */
export function monthRange(month: string): { first: string; last: string } {
  const lastDay = new Date(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0).getDate()
  return { first: `${month}-01`, last: `${month}-${String(lastDay).padStart(2, "0")}` }
}

/**
 * Runs one mutation at a time: disables the section while it runs, toasts the outcome and
 * calls `onDone` (usually a reload) after a success.
 */
export function useWorkforceAction(onDone: () => void) {
  const [busy, setBusy] = useState(false)

  async function run(success: string, action: () => Promise<unknown>): Promise<boolean> {
    setBusy(true)
    try {
      await action()
      toast.success(success)
      onDone()
      return true
    } catch (caught) {
      toast.error(apiErrorMessage(caught))
      return false
    } finally {
      setBusy(false)
    }
  }

  return { busy, run }
}

export function SectionError({ error }: { error: string | null }) {
  return error ? <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">{error}</p> : null
}

export function EmptyRow({ colSpan, children }: { colSpan: number; children: React.ReactNode }) {
  return (
    <TableRow>
      <TableCell colSpan={colSpan} className="h-24 text-center text-muted-foreground">{children}</TableCell>
    </TableRow>
  )
}
