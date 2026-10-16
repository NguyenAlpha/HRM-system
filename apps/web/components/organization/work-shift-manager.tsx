"use client"

import { useEffect, useState } from "react"
import { useForm, useWatch } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Loader2, Pencil, Plus, RefreshCw, Trash2 } from "lucide-react"
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
import { Badge } from "@/components/ui/badge"
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import type { Portal } from "@/lib/auth/types"
import {
  createWorkShift,
  deleteWorkShift,
  listWorkShifts,
  updateWorkShift,
  type WorkShift,
} from "@/lib/reference"

const CODE_PATTERN = /^[A-Z][A-Z0-9_-]*$/
const minutes = (label: string, min: number) =>
  z.string().trim().min(1, "Bắt buộc").refine((value) => Number.isInteger(Number(value)) && Number(value) >= min, `${label} phải là số nguyên ≥ ${min}`)

const shiftSchema = z.object({
  code: z.string().trim().min(1, "Bắt buộc").max(20, "Tối đa 20 ký tự").regex(CODE_PATTERN, "Chữ hoa, số, gạch dưới/ngang; bắt đầu bằng chữ cái"),
  name: z.string().trim().min(1, "Bắt buộc").max(30, "Tối đa 30 ký tự"),
  startTime: z.string().min(1, "Bắt buộc"),
  endTime: z.string().min(1, "Bắt buộc"),
  crossesMidnight: z.string(),
  breakMinutes: minutes("Phút nghỉ", 0),
  breakStartTime: z.string(),
  standardWorkMinutes: minutes("Phút công chuẩn", 1),
  graceLateMinutes: minutes("Phút đi trễ cho phép", 0),
  isActive: z.string(),
}).refine((values) => Number(values.breakMinutes) === 0 || values.breakStartTime !== "", {
  path: ["breakStartTime"],
  message: "Bắt buộc khi có giờ nghỉ",
})

type ShiftValues = z.infer<typeof shiftSchema>

const EMPTY_FORM: ShiftValues = {
  code: "", name: "", startTime: "08:00", endTime: "17:00", crossesMidnight: "false",
  breakMinutes: "60", breakStartTime: "12:00", standardWorkMinutes: "480", graceLateMinutes: "15", isActive: "true",
}

/** API times are HH:mm:ss; the time input and the table show HH:mm. */
const hhmm = (time: string | null) => time ? time.slice(0, 5) : ""

const fieldError = (message?: string) => message && <p className="text-xs font-medium text-destructive">{message}</p>

export function WorkShiftManager({ portal, onSessionExpired }: { portal: Portal; onSessionExpired: () => void }) {
  const [shifts, setShifts] = useState<WorkShift[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<WorkShift | null>(null)
  const [dialogError, setDialogError] = useState<string | null>(null)
  const [shiftToDelete, setShiftToDelete] = useState<WorkShift | null>(null)
  const [deleting, setDeleting] = useState(false)

  const {
    control,
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<ShiftValues>({ resolver: zodResolver(shiftSchema), defaultValues: EMPTY_FORM })

  const crossesMidnightValue = useWatch({ control, name: "crossesMidnight" })
  const isActiveValue = useWatch({ control, name: "isActive" })
  const hasBreak = Number(useWatch({ control, name: "breakMinutes" })) > 0
  const overnightItems = { false: "Trong ngày", true: "Qua đêm" }
  const statusItems = { true: "Hoạt động", false: "Tạm tắt" }

  useEffect(() => {
    let active = true
    listWorkShifts(portal)
      .then((result) => {
        if (!active) return
        setShifts(result)
        setLoading(false)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setError(apiErrorMessage(caught))
        setLoading(false)
      })
    return () => { active = false }
  }, [portal, revision, onSessionExpired])

  function reload() {
    setLoading(true)
    setError(null)
    setRevision((value) => value + 1)
  }

  function openCreate() {
    setEditing(null)
    setDialogError(null)
    reset(EMPTY_FORM)
    setDialogOpen(true)
  }

  function openEdit(shift: WorkShift) {
    setEditing(shift)
    setDialogError(null)
    reset({
      code: shift.code,
      name: shift.name,
      startTime: hhmm(shift.startTime),
      endTime: hhmm(shift.endTime),
      crossesMidnight: String(shift.crossesMidnight),
      breakMinutes: String(shift.breakMinutes),
      breakStartTime: hhmm(shift.breakStartTime),
      standardWorkMinutes: String(shift.standardWorkMinutes),
      graceLateMinutes: String(shift.graceLateMinutes),
      isActive: String(shift.isActive),
    })
    setDialogOpen(true)
  }

  async function onSubmit(values: ShiftValues) {
    setDialogError(null)
    const breakMinutes = Number(values.breakMinutes)
    const schedule = {
      name: values.name.trim(),
      startTime: values.startTime,
      endTime: values.endTime,
      breakMinutes,
      breakStartTime: breakMinutes > 0 ? values.breakStartTime : null,
      standardWorkMinutes: Number(values.standardWorkMinutes),
      graceLateMinutes: Number(values.graceLateMinutes),
      crossesMidnight: values.crossesMidnight === "true",
    }
    try {
      if (editing) {
        await updateWorkShift(portal, editing.id, { ...schedule, isActive: values.isActive === "true" })
        toast.success(`Đã cập nhật ca ${schedule.name}`)
      } else {
        await createWorkShift(portal, { ...schedule, code: values.code.trim() })
        toast.success(`Đã tạo ca ${schedule.name}`)
      }
      setDialogOpen(false)
      reload()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setDialogError(apiErrorMessage(caught))
    }
  }

  async function confirmDelete() {
    if (!shiftToDelete) return
    setDeleting(true)
    try {
      await deleteWorkShift(portal, shiftToDelete.id)
      toast.success(`Đã xóa ca ${shiftToDelete.name}`)
      setShiftToDelete(null)
      reload()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      toast.error(apiErrorMessage(caught))
    } finally {
      setDeleting(false)
    }
  }

  return (
    <>
      <div className="flex items-center justify-end gap-2">
        <Button type="button" variant="outline" size="sm" onClick={reload} disabled={loading}>
          <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
        </Button>
        <Button type="button" size="sm" onClick={openCreate}>
          <Plus /> Thêm ca
        </Button>
      </div>

      <ErrorAlert error={error} />

      <div className="rounded-xl border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Mã / Tên ca</TableHead>
              <TableHead>Giờ làm</TableHead>
              <TableHead>Nghỉ giữa ca</TableHead>
              <TableHead>Công chuẩn</TableHead>
              <TableHead>Trễ cho phép</TableHead>
              <TableHead>Trạng thái</TableHead>
              <TableHead className="text-right">Thao tác</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading &&
              Array.from({ length: 3 }).map((_, index) => (
                <TableRow key={index}>
                  {Array.from({ length: 7 }).map((__, cell) => (
                    <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
                  ))}
                </TableRow>
              ))}

            {!loading && shifts.map((shift) => (
              <TableRow key={shift.id}>
                <TableCell>
                  <span className="font-medium">{shift.name}</span>
                  <span className="block text-xs text-muted-foreground"><code>{shift.code}</code></span>
                </TableCell>
                <TableCell>
                  {hhmm(shift.startTime)}–{hhmm(shift.endTime)}
                  {shift.crossesMidnight && <Badge variant="secondary" className="ml-2">Qua đêm</Badge>}
                </TableCell>
                <TableCell>{shift.breakMinutes > 0 ? `${shift.breakMinutes} phút từ ${hhmm(shift.breakStartTime)}` : "Không"}</TableCell>
                <TableCell>{shift.standardWorkMinutes} phút</TableCell>
                <TableCell>{shift.graceLateMinutes} phút</TableCell>
                <TableCell>
                  <Badge variant={shift.isActive ? "default" : "outline"}>{shift.isActive ? "Hoạt động" : "Tạm tắt"}</Badge>
                </TableCell>
                <RowActionsCell label={shift.code}>
                  <RowActionItem icon={Pencil} onClick={() => openEdit(shift)}>Sửa</RowActionItem>
                  <RowActionItem icon={Trash2} destructive onClick={() => setShiftToDelete(shift)}>Xóa</RowActionItem>
                </RowActionsCell>
              </TableRow>
            ))}

            {!loading && shifts.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} className="h-24 text-center text-muted-foreground">Chưa có ca làm việc.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-lg">
          <form onSubmit={handleSubmit(onSubmit)}>
            <DialogHeader>
              <DialogTitle>{editing ? `Sửa ca ${editing.code}` : "Thêm ca làm việc"}</DialogTitle>
              <DialogDescription>
                {editing
                  ? "Ca đã có chấm công chỉ đổi được tên và trạng thái; muốn đổi giờ, hãy tạo ca mới."
                  : "Giờ nghỉ phải nằm trọn trong ca; chấm công dùng nó để tính đúng ngày làm một phần ca."}
              </DialogDescription>
            </DialogHeader>

            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <div className="sm:col-span-2"><ErrorAlert error={dialogError} /></div>

              <div className="grid gap-1.5">
                <Label htmlFor="shift-code">Mã ca</Label>
                <Input id="shift-code" placeholder="VD: OFFICE_DAY" disabled={Boolean(editing)} aria-invalid={!!errors.code} {...register("code")} />
                {fieldError(errors.code?.message)}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="shift-name">Tên ca</Label>
                <Input id="shift-name" aria-invalid={!!errors.name} {...register("name")} />
                {fieldError(errors.name?.message)}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="shift-start">Bắt đầu</Label>
                <Input id="shift-start" type="time" aria-invalid={!!errors.startTime} {...register("startTime")} />
                {fieldError(errors.startTime?.message)}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="shift-end">Kết thúc</Label>
                <Input id="shift-end" type="time" aria-invalid={!!errors.endTime} {...register("endTime")} />
                {fieldError(errors.endTime?.message)}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="shift-overnight">Loại ca</Label>
                <Select value={crossesMidnightValue} items={overnightItems} onValueChange={(value) => setValue("crossesMidnight", value ?? "false")}>
                  <SelectTrigger id="shift-overnight" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="false">Trong ngày</SelectItem>
                    <SelectItem value="true">Qua đêm</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="shift-standard">Phút công chuẩn</Label>
                <Input id="shift-standard" type="number" min="1" aria-invalid={!!errors.standardWorkMinutes} {...register("standardWorkMinutes")} />
                {fieldError(errors.standardWorkMinutes?.message)}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="shift-break">Phút nghỉ giữa ca</Label>
                <Input id="shift-break" type="number" min="0" aria-invalid={!!errors.breakMinutes} {...register("breakMinutes")} />
                {fieldError(errors.breakMinutes?.message)}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="shift-break-start">Bắt đầu nghỉ</Label>
                <Input id="shift-break-start" type="time" disabled={!hasBreak} aria-invalid={!!errors.breakStartTime} {...register("breakStartTime")} />
                {fieldError(errors.breakStartTime?.message)}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="shift-grace">Phút đi trễ cho phép</Label>
                <Input id="shift-grace" type="number" min="0" aria-invalid={!!errors.graceLateMinutes} {...register("graceLateMinutes")} />
                {fieldError(errors.graceLateMinutes?.message)}
              </div>

              {editing && (
                <div className="grid gap-1.5">
                  <Label htmlFor="shift-status">Trạng thái</Label>
                  <Select value={isActiveValue} items={statusItems} onValueChange={(value) => setValue("isActive", value ?? "true")}>
                    <SelectTrigger id="shift-status" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="true">Hoạt động</SelectItem>
                      <SelectItem value="false">Tạm tắt</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              )}
            </div>

            <DialogFooter className="mt-4">
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="animate-spin" />}
                {editing ? "Lưu thay đổi" : "Tạo ca"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog open={shiftToDelete !== null} onOpenChange={(open) => { if (!open) setShiftToDelete(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa ca {shiftToDelete?.code}?</AlertDialogTitle>
            <AlertDialogDescription>
              Ca sẽ bị xóa mềm; chấm công lịch sử được giữ lại. Ca đang được phân công hiện tại hoặc tương lai không xóa được.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              disabled={deleting}
              onClick={(event) => { event.preventDefault(); void confirmDelete() }}
            >
              {deleting && <Loader2 className="animate-spin" />}
              Xóa ca
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
