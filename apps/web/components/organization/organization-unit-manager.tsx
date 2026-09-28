"use client"

import { useEffect, useMemo, useState } from "react"
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
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import type { Portal } from "@/lib/auth/types"
import {
  createOrganizationUnit,
  deleteOrganizationUnit,
  listOrganizationUnits,
  ORGANIZATION_UNIT_TYPE_LABELS,
  ORGANIZATION_UNIT_TYPES,
  updateOrganizationUnit,
  type OrganizationUnit,
} from "@/lib/reference"

const CODE_PATTERN = /^[A-Z][A-Z0-9_-]*$/

const unitSchema = z.object({
  code: z.string().trim().min(1, "Bắt buộc").max(30, "Tối đa 30 ký tự").regex(CODE_PATTERN, "Chữ hoa, số, gạch dưới/ngang; bắt đầu bằng chữ cái"),
  name: z.string().trim().min(1, "Bắt buộc").max(150, "Tối đa 150 ký tự"),
  unitType: z.string().min(1, "Bắt buộc"),
  parentUnitId: z.string().optional(),
  isActive: z.string(),
})

type UnitValues = z.infer<typeof unitSchema>

const EMPTY_FORM: UnitValues = { code: "", name: "", unitType: "", parentUnitId: "", isActive: "true" }

export function OrganizationUnitManager({ portal, onSessionExpired }: { portal: Portal; onSessionExpired: () => void }) {
  const [units, setUnits] = useState<OrganizationUnit[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<OrganizationUnit | null>(null)
  const [dialogError, setDialogError] = useState<string | null>(null)
  const [unitToDelete, setUnitToDelete] = useState<OrganizationUnit | null>(null)
  const [deleting, setDeleting] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<UnitValues>({ resolver: zodResolver(unitSchema), defaultValues: EMPTY_FORM })

  const unitTypeValue = watch("unitType")
  const parentUnitIdValue = watch("parentUnitId")
  const isActiveValue = watch("isActive")

  const unitTypeItems = useMemo(
    () => Object.fromEntries(ORGANIZATION_UNIT_TYPES.map((type) => [type, ORGANIZATION_UNIT_TYPE_LABELS[type]])),
    [],
  )
  const parentUnitItems = useMemo(
    () => Object.fromEntries(
      units.filter((unit) => unit.id !== editing?.id).map((unit) => [String(unit.id), unit.name]),
    ),
    [units, editing],
  )
  const statusItems = { true: "Hoạt động", false: "Tạm tắt" }

  useEffect(() => {
    let active = true
    listOrganizationUnits(portal)
      .then((result) => {
        if (!active) return
        setUnits(result)
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

  function openEdit(unit: OrganizationUnit) {
    setEditing(unit)
    setDialogError(null)
    reset({
      code: unit.code,
      name: unit.name,
      unitType: unit.unitType,
      parentUnitId: unit.parentUnitId ? String(unit.parentUnitId) : "",
      isActive: String(unit.isActive),
    })
    setDialogOpen(true)
  }

  async function onSubmit(values: UnitValues) {
    setDialogError(null)
    try {
      if (editing) {
        await updateOrganizationUnit(portal, editing.id, {
          parentUnitId: values.parentUnitId ? Number(values.parentUnitId) : null,
          name: values.name.trim(),
          isActive: values.isActive === "true",
        })
        toast.success(`Đã cập nhật đơn vị ${values.name.trim()}`)
      } else {
        await createOrganizationUnit(portal, {
          parentUnitId: values.parentUnitId ? Number(values.parentUnitId) : null,
          code: values.code.trim(),
          name: values.name.trim(),
          unitType: values.unitType as OrganizationUnit["unitType"],
        })
        toast.success(`Đã tạo đơn vị ${values.name.trim()}`)
      }
      setDialogOpen(false)
      reload()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setDialogError(apiErrorMessage(caught))
    }
  }

  async function confirmDelete() {
    if (!unitToDelete) return
    setDeleting(true)
    try {
      await deleteOrganizationUnit(portal, unitToDelete.id)
      toast.success(`Đã xóa đơn vị ${unitToDelete.name}`)
      setUnitToDelete(null)
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
          <Plus /> Thêm đơn vị
        </Button>
      </div>

      <RbacFeedback error={error} />

      <div className="rounded-xl border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Mã / Tên</TableHead>
              <TableHead>Loại</TableHead>
              <TableHead>Đơn vị cha</TableHead>
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

            {!loading && units.map((unit) => (
              <TableRow key={unit.id}>
                <TableCell>
                  <span className="font-medium">{unit.name}</span>
                  <span className="block text-xs text-muted-foreground"><code>{unit.code}</code></span>
                </TableCell>
                <TableCell><Badge variant="secondary">{ORGANIZATION_UNIT_TYPE_LABELS[unit.unitType]}</Badge></TableCell>
                <TableCell className="text-sm text-muted-foreground">{unit.parentUnitName ?? "—"}</TableCell>
                <TableCell>
                  <Badge variant={unit.isActive ? "default" : "outline"}>{unit.isActive ? "Hoạt động" : "Tạm tắt"}</Badge>
                </TableCell>
                <TableCell>
                  <div className="flex justify-end gap-1">
                    <Button type="button" variant="ghost" size="icon-sm" onClick={() => openEdit(unit)} aria-label={`Sửa ${unit.code}`}>
                      <Pencil />
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      className="text-muted-foreground hover:text-destructive"
                      onClick={() => setUnitToDelete(unit)}
                      aria-label={`Xóa ${unit.code}`}
                    >
                      <Trash2 />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}

            {!loading && units.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} className="h-24 text-center text-muted-foreground">Chưa có đơn vị tổ chức.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <form onSubmit={handleSubmit(onSubmit)}>
            <DialogHeader>
              <DialogTitle>{editing ? `Sửa đơn vị ${editing.code}` : "Thêm đơn vị tổ chức"}</DialogTitle>
              <DialogDescription>
                {editing ? "Cập nhật tên, đơn vị cha và trạng thái." : "Tạo đơn vị tổ chức mới."}
              </DialogDescription>
            </DialogHeader>

            <div className="mt-4 grid gap-4">
              <RbacFeedback error={dialogError} />

              <div className="grid gap-1.5">
                <Label htmlFor="unit-code">Mã đơn vị</Label>
                <Input id="unit-code" placeholder="VD: SALES" disabled={Boolean(editing)} aria-invalid={!!errors.code} {...register("code")} />
                {errors.code && <p className="text-xs font-medium text-destructive">{errors.code.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="unit-name">Tên đơn vị</Label>
                <Input id="unit-name" aria-invalid={!!errors.name} {...register("name")} />
                {errors.name && <p className="text-xs font-medium text-destructive">{errors.name.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="unit-type">Loại đơn vị</Label>
                <Select
                  value={unitTypeValue}
                  items={unitTypeItems}
                  onValueChange={(value) => setValue("unitType", value ?? "")}
                  disabled={Boolean(editing)}
                >
                  <SelectTrigger id="unit-type" className="w-full" aria-invalid={!!errors.unitType}>
                    <SelectValue placeholder="Chọn loại..." />
                  </SelectTrigger>
                  <SelectContent>
                    {ORGANIZATION_UNIT_TYPES.map((type) => (
                      <SelectItem key={type} value={type}>{ORGANIZATION_UNIT_TYPE_LABELS[type]}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.unitType && <p className="text-xs font-medium text-destructive">{errors.unitType.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="unit-parent">Đơn vị cha (tùy chọn)</Label>
                <Select
                  value={parentUnitIdValue || undefined}
                  items={{ "": "Không có", ...parentUnitItems }}
                  onValueChange={(value) => setValue("parentUnitId", value === "" ? "" : (value ?? ""))}
                >
                  <SelectTrigger id="unit-parent" className="w-full">
                    <SelectValue placeholder="Không có" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="">Không có</SelectItem>
                    {Object.entries(parentUnitItems).map(([id, name]) => (
                      <SelectItem key={id} value={id}>{name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {editing && (
                <div className="grid gap-1.5">
                  <Label htmlFor="unit-status">Trạng thái</Label>
                  <Select value={isActiveValue} items={statusItems} onValueChange={(value) => setValue("isActive", value ?? "true")}>
                    <SelectTrigger id="unit-status" className="w-full">
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
                {editing ? "Lưu thay đổi" : "Tạo đơn vị"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog open={unitToDelete !== null} onOpenChange={(open) => { if (!open) setUnitToDelete(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa đơn vị {unitToDelete?.code}?</AlertDialogTitle>
            <AlertDialogDescription>Đơn vị sẽ bị xóa mềm; lịch sử được giữ lại.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              disabled={deleting}
              onClick={(event) => { event.preventDefault(); void confirmDelete() }}
            >
              {deleting && <Loader2 className="animate-spin" />}
              Xóa đơn vị
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
