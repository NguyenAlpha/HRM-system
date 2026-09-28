"use client"

import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Loader2, Pencil, Plus, RefreshCw, Trash2 } from "lucide-react"
import { toast } from "sonner"

import { RbacFeedback } from "@/components/admin/rbac-controls"
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
import { Textarea } from "@/components/ui/textarea"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import type { Portal } from "@/lib/auth/types"
import {
  createJobPosition,
  deleteJobPosition,
  listJobPositions,
  updateJobPosition,
  type JobPosition,
} from "@/lib/reference"

const CODE_PATTERN = /^[A-Z][A-Z0-9_-]*$/

const positionSchema = z.object({
  code: z.string().trim().min(1, "Bắt buộc").max(30, "Tối đa 30 ký tự").regex(CODE_PATTERN, "Chữ hoa, số, gạch dưới/ngang; bắt đầu bằng chữ cái"),
  title: z.string().trim().min(1, "Bắt buộc").max(150, "Tối đa 150 ký tự"),
  description: z.string().max(1000, "Tối đa 1000 ký tự").optional(),
  isManagerial: z.string(),
  isActive: z.string(),
})

type PositionValues = z.infer<typeof positionSchema>

const EMPTY_FORM: PositionValues = { code: "", title: "", description: "", isManagerial: "false", isActive: "true" }

export function JobPositionManager({ portal, onSessionExpired }: { portal: Portal; onSessionExpired: () => void }) {
  const [positions, setPositions] = useState<JobPosition[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<JobPosition | null>(null)
  const [dialogError, setDialogError] = useState<string | null>(null)
  const [positionToDelete, setPositionToDelete] = useState<JobPosition | null>(null)
  const [deleting, setDeleting] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<PositionValues>({ resolver: zodResolver(positionSchema), defaultValues: EMPTY_FORM })

  const isManagerialValue = watch("isManagerial")
  const isActiveValue = watch("isActive")
  const booleanItems = { true: "Có", false: "Không" }
  const statusItems = { true: "Hoạt động", false: "Tạm tắt" }

  useEffect(() => {
    let active = true
    listJobPositions(portal)
      .then((result) => {
        if (!active) return
        setPositions(result)
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

  function openEdit(position: JobPosition) {
    setEditing(position)
    setDialogError(null)
    reset({
      code: position.code,
      title: position.title,
      description: position.description ?? "",
      isManagerial: String(position.isManagerial),
      isActive: String(position.isActive),
    })
    setDialogOpen(true)
  }

  async function onSubmit(values: PositionValues) {
    setDialogError(null)
    try {
      if (editing) {
        await updateJobPosition(portal, editing.id, {
          title: values.title.trim(),
          description: values.description?.trim() || null,
          isManagerial: values.isManagerial === "true",
          isActive: values.isActive === "true",
        })
        toast.success(`Đã cập nhật vị trí ${values.title.trim()}`)
      } else {
        await createJobPosition(portal, {
          code: values.code.trim(),
          title: values.title.trim(),
          description: values.description?.trim() || null,
          isManagerial: values.isManagerial === "true",
        })
        toast.success(`Đã tạo vị trí ${values.title.trim()}`)
      }
      setDialogOpen(false)
      reload()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setDialogError(apiErrorMessage(caught))
    }
  }

  async function confirmDelete() {
    if (!positionToDelete) return
    setDeleting(true)
    try {
      await deleteJobPosition(portal, positionToDelete.id)
      toast.success(`Đã xóa vị trí ${positionToDelete.title}`)
      setPositionToDelete(null)
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
          <Plus /> Thêm vị trí
        </Button>
      </div>

      <RbacFeedback error={error} />

      <div className="rounded-xl border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Mã / Chức danh</TableHead>
              <TableHead>Mô tả</TableHead>
              <TableHead>Quản lý</TableHead>
              <TableHead>Trạng thái</TableHead>
              <TableHead className="text-right">Thao tác</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading &&
              Array.from({ length: 4 }).map((_, index) => (
                <TableRow key={index}>
                  {Array.from({ length: 5 }).map((__, cell) => (
                    <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
                  ))}
                </TableRow>
              ))}

            {!loading && positions.map((position) => (
              <TableRow key={position.id}>
                <TableCell>
                  <span className="font-medium">{position.title}</span>
                  <span className="block text-xs text-muted-foreground"><code>{position.code}</code></span>
                </TableCell>
                <TableCell className="max-w-sm text-sm text-wrap text-muted-foreground">{position.description ?? "—"}</TableCell>
                <TableCell>{position.isManagerial && <Badge variant="secondary">Quản lý</Badge>}</TableCell>
                <TableCell>
                  <Badge variant={position.isActive ? "default" : "outline"}>{position.isActive ? "Hoạt động" : "Tạm tắt"}</Badge>
                </TableCell>
                <TableCell>
                  <div className="flex justify-end gap-1">
                    <Button type="button" variant="ghost" size="icon-sm" onClick={() => openEdit(position)} aria-label={`Sửa ${position.code}`}>
                      <Pencil />
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      className="text-muted-foreground hover:text-destructive"
                      onClick={() => setPositionToDelete(position)}
                      aria-label={`Xóa ${position.code}`}
                    >
                      <Trash2 />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}

            {!loading && positions.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="h-24 text-center text-muted-foreground">Chưa có vị trí công việc.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <form onSubmit={handleSubmit(onSubmit)}>
            <DialogHeader>
              <DialogTitle>{editing ? `Sửa vị trí ${editing.code}` : "Thêm vị trí công việc"}</DialogTitle>
              <DialogDescription>
                {editing ? "Cập nhật chức danh, mô tả, cấp quản lý và trạng thái." : "Tạo vị trí công việc mới."}
              </DialogDescription>
            </DialogHeader>

            <div className="mt-4 grid gap-4">
              <RbacFeedback error={dialogError} />

              <div className="grid gap-1.5">
                <Label htmlFor="position-code">Mã vị trí</Label>
                <Input id="position-code" placeholder="VD: SALES_REP" disabled={Boolean(editing)} aria-invalid={!!errors.code} {...register("code")} />
                {errors.code && <p className="text-xs font-medium text-destructive">{errors.code.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="position-title">Chức danh</Label>
                <Input id="position-title" aria-invalid={!!errors.title} {...register("title")} />
                {errors.title && <p className="text-xs font-medium text-destructive">{errors.title.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="position-description">Mô tả</Label>
                <Textarea id="position-description" rows={3} {...register("description")} />
                {errors.description && <p className="text-xs font-medium text-destructive">{errors.description.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="position-managerial">Vị trí quản lý</Label>
                <Select value={isManagerialValue} items={booleanItems} onValueChange={(value) => setValue("isManagerial", value ?? "false")}>
                  <SelectTrigger id="position-managerial" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="true">Có</SelectItem>
                    <SelectItem value="false">Không</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {editing && (
                <div className="grid gap-1.5">
                  <Label htmlFor="position-status">Trạng thái</Label>
                  <Select value={isActiveValue} items={statusItems} onValueChange={(value) => setValue("isActive", value ?? "true")}>
                    <SelectTrigger id="position-status" className="w-full">
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

            <DialogFooter>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="animate-spin" />}
                {editing ? "Lưu thay đổi" : "Tạo vị trí"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog open={positionToDelete !== null} onOpenChange={(open) => { if (!open) setPositionToDelete(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa vị trí {positionToDelete?.code}?</AlertDialogTitle>
            <AlertDialogDescription>Vị trí sẽ bị xóa mềm; lịch sử được giữ lại.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              disabled={deleting}
              onClick={(event) => { event.preventDefault(); void confirmDelete() }}
            >
              {deleting && <Loader2 className="animate-spin" />}
              Xóa vị trí
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
