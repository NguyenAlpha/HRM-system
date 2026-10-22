"use client"

import { FormEvent, useEffect, useState } from "react"
import { ChevronLeft, ChevronRight, Loader2, Plus, RefreshCw, Trash2 } from "lucide-react"
import { toast } from "sonner"

import { ErrorAlert } from "@/components/common/list-controls"
import { RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import { Button } from "@/components/ui/button"
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
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { EmptyRow, today, workforceApi } from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import type { CompanyHoliday, WorkforceBody } from "@/lib/workforce"

const WEEKDAY_NAMES = ["Chủ nhật", "Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy"]

const weekdayName = (date: string) => WEEKDAY_NAMES[new Date(`${date}T00:00:00Z`).getUTCDay()]

const displayDate = (date: string) => `${date.slice(8, 10)}/${date.slice(5, 7)}/${date.slice(0, 4)}`

/** Company holidays of one year. Days off that count as paid and not as work days in attendance and payroll. */
export function CompanyHolidayManager({ canManage }: { canManage: boolean }) {
  const [year, setYear] = useState(Number(today().slice(0, 4)))
  const [holidays, setHolidays] = useState<CompanyHoliday[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [adding, setAdding] = useState(false)
  const [toDelete, setToDelete] = useState<CompanyHoliday | null>(null)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    let active = true
    workforceApi<CompanyHoliday[]>(`company-holidays?from=${year}-01-01&to=${year}-12-31`)
      .then((result) => { if (active) { setHolidays(result); setError(null) } })
      .catch((caught: unknown) => { if (active) { setHolidays([]); setError(apiErrorMessage(caught)) } })
    return () => { active = false }
  }, [year, revision])

  function reload() {
    setHolidays(null)
    setRevision((value) => value + 1)
  }

  function changeYear(next: number) {
    setHolidays(null)
    setYear(next)
  }

  async function confirmDelete() {
    if (!toDelete) return
    setDeleting(true)
    try {
      await workforceApi(`company-holidays/${toDelete.id}`, "DELETE")
      toast.success(`Đã xóa ngày lễ ${toDelete.name}`)
      setToDelete(null)
      reload()
    } catch (caught) {
      toast.error(apiErrorMessage(caught))
    } finally {
      setDeleting(false)
    }
  }

  const loading = holidays === null

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-1">
          <Button type="button" variant="outline" size="icon-sm" aria-label="Năm trước" onClick={() => changeYear(year - 1)}>
            <ChevronLeft />
          </Button>
          <span className="w-16 text-center font-semibold tabular-nums">{year}</span>
          <Button type="button" variant="outline" size="icon-sm" aria-label="Năm sau" onClick={() => changeYear(year + 1)}>
            <ChevronRight />
          </Button>
        </div>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" size="sm" onClick={reload} disabled={loading}>
            <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
          </Button>
          {canManage && (
            <Button type="button" size="sm" onClick={() => setAdding(true)}><Plus /> Thêm ngày lễ</Button>
          )}
        </div>
      </div>

      <p className="text-xs text-muted-foreground">
        Ngày lễ được nghỉ hưởng lương và không tính là ngày làm việc. Thêm hoặc xóa ngày lễ đưa kỳ lương đã tính của tháng đó về nháp.
      </p>

      <ErrorAlert error={error} />

      <div className="rounded-xl border bg-card">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Ngày</TableHead>
              <TableHead>Thứ</TableHead>
              <TableHead>Tên ngày lễ</TableHead>
              {canManage && <TableHead className="text-right">Thao tác</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading && Array.from({ length: 3 }).map((_, index) => (
              <TableRow key={index}>
                {Array.from({ length: canManage ? 4 : 3 }).map((__, cell) => (
                  <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
                ))}
              </TableRow>
            ))}
            {!loading && holidays.length === 0 && (
              <EmptyRow colSpan={canManage ? 4 : 3}>Chưa có ngày lễ nào trong năm {year}.</EmptyRow>
            )}
            {!loading && holidays.map((holiday) => (
              <TableRow key={holiday.id}>
                <TableCell className="font-medium tabular-nums">{displayDate(holiday.holidayDate)}</TableCell>
                <TableCell className="text-muted-foreground">{weekdayName(holiday.holidayDate)}</TableCell>
                <TableCell>{holiday.name}</TableCell>
                {canManage && (
                  <RowActionsCell label={`ngày lễ ${holiday.name}`}>
                    <RowActionItem icon={Trash2} destructive onClick={() => setToDelete(holiday)}>Xóa ngày lễ</RowActionItem>
                  </RowActionsCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {adding && (
        <HolidayDialog defaultYear={year} onClose={() => setAdding(false)} onSaved={(saved) => {
          setAdding(false)
          const savedYear = Number(saved.slice(0, 4))
          if (savedYear === year) reload()
          else changeYear(savedYear)
        }} />
      )}

      <AlertDialog open={toDelete !== null} onOpenChange={(open) => { if (!open && !deleting) setToDelete(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa ngày lễ {toDelete?.name}?</AlertDialogTitle>
            <AlertDialogDescription>
              Ngày {toDelete && displayDate(toDelete.holidayDate)} sẽ trở lại là ngày làm việc bình thường.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>Hủy</AlertDialogCancel>
            <AlertDialogAction variant="destructive" disabled={deleting} onClick={(event) => {
              event.preventDefault()
              void confirmDelete()
            }}>
              {deleting && <Loader2 className="animate-spin" />} Xóa
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}

function HolidayDialog({ defaultYear, onClose, onSaved }: {
  defaultYear: number
  onClose: () => void
  /** Called with the saved date. */
  onSaved: (date: string) => void
}) {
  const [date, setDate] = useState(`${defaultYear}-01-01`)
  const [name, setName] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await workforceApi("company-holidays", "POST", { date, name: name.trim() } satisfies WorkforceBody["CompanyHolidayRequest"])
      toast.success(`Đã thêm ngày lễ ${name.trim()}`)
      onSaved(date)
    } catch (caught) {
      setError(apiErrorMessage(caught))
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={submit}>
          <DialogHeader>
            <DialogTitle>Thêm ngày lễ</DialogTitle>
            <DialogDescription>Mỗi ngày nghỉ là một dòng; kỳ nghỉ nhiều ngày thì thêm từng ngày.</DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="holiday-date">Ngày</Label>
              <Input id="holiday-date" type="date" required value={date} onChange={(event) => setDate(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="holiday-name">Tên ngày lễ</Label>
              <Input id="holiday-name" required maxLength={30} placeholder="Quốc khánh"
                value={name} onChange={(event) => setName(event.target.value)} />
            </div>
            {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Hủy</Button>
            <Button type="submit" disabled={busy || !date || !name.trim()}>{busy && <Loader2 className="animate-spin" />} Lưu</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
