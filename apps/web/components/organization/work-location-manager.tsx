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
  createWorkLocation,
  deleteWorkLocation,
  listWorkLocations,
  LOCATION_TYPE_LABELS,
  LOCATION_TYPES,
  updateWorkLocation,
  type WorkLocation,
} from "@/lib/reference"

const CODE_PATTERN = /^[A-Z][A-Z0-9_-]*$/

const locationSchema = z.object({
  code: z.string().trim().min(1, "Bắt buộc").max(30, "Tối đa 30 ký tự").regex(CODE_PATTERN, "Chữ hoa, số, gạch dưới/ngang; bắt đầu bằng chữ cái"),
  name: z.string().trim().min(1, "Bắt buộc").max(150, "Tối đa 150 ký tự"),
  locationType: z.string().min(1, "Bắt buộc"),
  address: z.string().trim().min(1, "Bắt buộc"),
  phone: z.string().trim().max(20, "Tối đa 20 ký tự").optional(),
  parentLocationId: z.string().optional(),
  isActive: z.string(),
})

type LocationValues = z.infer<typeof locationSchema>

const EMPTY_FORM: LocationValues = { code: "", name: "", locationType: "", address: "", phone: "", parentLocationId: "", isActive: "true" }

export function WorkLocationManager({ portal, onSessionExpired }: { portal: Portal; onSessionExpired: () => void }) {
  const [locations, setLocations] = useState<WorkLocation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<WorkLocation | null>(null)
  const [dialogError, setDialogError] = useState<string | null>(null)
  const [locationToDelete, setLocationToDelete] = useState<WorkLocation | null>(null)
  const [deleting, setDeleting] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<LocationValues>({ resolver: zodResolver(locationSchema), defaultValues: EMPTY_FORM })

  const locationTypeValue = watch("locationType")
  const parentLocationIdValue = watch("parentLocationId")
  const isActiveValue = watch("isActive")

  const locationTypeItems = useMemo(
    () => Object.fromEntries(LOCATION_TYPES.map((type) => [type, LOCATION_TYPE_LABELS[type]])),
    [],
  )
  const parentLocationItems = useMemo(
    () => Object.fromEntries(
      locations.filter((location) => location.id !== editing?.id).map((location) => [String(location.id), location.name]),
    ),
    [locations, editing],
  )
  const statusItems = { true: "Hoạt động", false: "Tạm tắt" }

  useEffect(() => {
    let active = true
    listWorkLocations(portal)
      .then((result) => {
        if (!active) return
        setLocations(result)
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

  function openEdit(location: WorkLocation) {
    setEditing(location)
    setDialogError(null)
    reset({
      code: location.code,
      name: location.name,
      locationType: location.locationType,
      address: location.address ?? "",
      phone: location.phone ?? "",
      parentLocationId: location.parentLocationId ? String(location.parentLocationId) : "",
      isActive: String(location.isActive),
    })
    setDialogOpen(true)
  }

  async function onSubmit(values: LocationValues) {
    setDialogError(null)
    try {
      if (editing) {
        await updateWorkLocation(portal, editing.id, {
          parentLocationId: values.parentLocationId ? Number(values.parentLocationId) : null,
          name: values.name.trim(),
          address: values.address.trim(),
          phone: values.phone?.trim() || null,
          isActive: values.isActive === "true",
        })
        toast.success(`Đã cập nhật địa điểm ${values.name.trim()}`)
      } else {
        await createWorkLocation(portal, {
          parentLocationId: values.parentLocationId ? Number(values.parentLocationId) : null,
          code: values.code.trim(),
          name: values.name.trim(),
          locationType: values.locationType as WorkLocation["locationType"],
          address: values.address.trim(),
          phone: values.phone?.trim() || null,
        })
        toast.success(`Đã tạo địa điểm ${values.name.trim()}`)
      }
      setDialogOpen(false)
      reload()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setDialogError(apiErrorMessage(caught))
    }
  }

  async function confirmDelete() {
    if (!locationToDelete) return
    setDeleting(true)
    try {
      await deleteWorkLocation(portal, locationToDelete.id)
      toast.success(`Đã xóa địa điểm ${locationToDelete.name}`)
      setLocationToDelete(null)
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
          <Plus /> Thêm địa điểm
        </Button>
      </div>

      <RbacFeedback error={error} />

      <div className="rounded-xl border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Mã / Tên</TableHead>
              <TableHead>Loại</TableHead>
              <TableHead>Địa chỉ / SĐT</TableHead>
              <TableHead>Địa điểm cha</TableHead>
              <TableHead>Trạng thái</TableHead>
              <TableHead className="text-right">Thao tác</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading &&
              Array.from({ length: 4 }).map((_, index) => (
                <TableRow key={index}>
                  {Array.from({ length: 6 }).map((__, cell) => (
                    <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
                  ))}
                </TableRow>
              ))}

            {!loading && locations.map((location) => (
              <TableRow key={location.id}>
                <TableCell>
                  <span className="font-medium">{location.name}</span>
                  <span className="block text-xs text-muted-foreground"><code>{location.code}</code></span>
                </TableCell>
                <TableCell><Badge variant="secondary">{LOCATION_TYPE_LABELS[location.locationType]}</Badge></TableCell>
                <TableCell className="max-w-xs text-sm text-wrap text-muted-foreground">
                  {location.address ?? "—"}
                  {location.phone && <span className="block">{location.phone}</span>}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">{location.parentLocationName ?? "—"}</TableCell>
                <TableCell>
                  <Badge variant={location.isActive ? "default" : "outline"}>{location.isActive ? "Hoạt động" : "Tạm tắt"}</Badge>
                </TableCell>
                <TableCell>
                  <div className="flex justify-end gap-1">
                    <Button type="button" variant="ghost" size="icon-sm" onClick={() => openEdit(location)} aria-label={`Sửa ${location.code}`}>
                      <Pencil />
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      className="text-muted-foreground hover:text-destructive"
                      onClick={() => setLocationToDelete(location)}
                      aria-label={`Xóa ${location.code}`}
                    >
                      <Trash2 />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}

            {!loading && locations.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="h-24 text-center text-muted-foreground">Chưa có địa điểm làm việc.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <form onSubmit={handleSubmit(onSubmit)}>
            <DialogHeader>
              <DialogTitle>{editing ? `Sửa địa điểm ${editing.code}` : "Thêm địa điểm làm việc"}</DialogTitle>
              <DialogDescription>
                {editing ? "Cập nhật tên, địa chỉ, SĐT, đơn vị cha và trạng thái." : "Tạo địa điểm làm việc mới."}
              </DialogDescription>
            </DialogHeader>

            <div className="mt-4 grid max-h-[65vh] gap-4 overflow-y-auto pr-1">
              <RbacFeedback error={dialogError} />

              <div className="grid gap-1.5">
                <Label htmlFor="location-code">Mã địa điểm</Label>
                <Input id="location-code" placeholder="VD: BRANCH-03" disabled={Boolean(editing)} aria-invalid={!!errors.code} {...register("code")} />
                {errors.code && <p className="text-xs font-medium text-destructive">{errors.code.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="location-name">Tên địa điểm</Label>
                <Input id="location-name" aria-invalid={!!errors.name} {...register("name")} />
                {errors.name && <p className="text-xs font-medium text-destructive">{errors.name.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="location-type">Loại địa điểm</Label>
                <Select
                  value={locationTypeValue}
                  items={locationTypeItems}
                  onValueChange={(value) => setValue("locationType", value ?? "")}
                  disabled={Boolean(editing)}
                >
                  <SelectTrigger id="location-type" className="w-full" aria-invalid={!!errors.locationType}>
                    <SelectValue placeholder="Chọn loại..." />
                  </SelectTrigger>
                  <SelectContent>
                    {LOCATION_TYPES.map((type) => (
                      <SelectItem key={type} value={type}>{LOCATION_TYPE_LABELS[type]}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.locationType && <p className="text-xs font-medium text-destructive">{errors.locationType.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="location-address">Địa chỉ</Label>
                <Input id="location-address" aria-invalid={!!errors.address} {...register("address")} />
                {errors.address && <p className="text-xs font-medium text-destructive">{errors.address.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="location-phone">Số điện thoại (tùy chọn)</Label>
                <Input id="location-phone" aria-invalid={!!errors.phone} {...register("phone")} />
                {errors.phone && <p className="text-xs font-medium text-destructive">{errors.phone.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="location-parent">Địa điểm cha (tùy chọn)</Label>
                <Select
                  value={parentLocationIdValue || undefined}
                  items={{ "": "Không có", ...parentLocationItems }}
                  onValueChange={(value) => setValue("parentLocationId", value === "" ? "" : (value ?? ""))}
                >
                  <SelectTrigger id="location-parent" className="w-full">
                    <SelectValue placeholder="Không có" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="">Không có</SelectItem>
                    {Object.entries(parentLocationItems).map(([id, name]) => (
                      <SelectItem key={id} value={id}>{name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {editing && (
                <div className="grid gap-1.5">
                  <Label htmlFor="location-status">Trạng thái</Label>
                  <Select value={isActiveValue} items={statusItems} onValueChange={(value) => setValue("isActive", value ?? "true")}>
                    <SelectTrigger id="location-status" className="w-full">
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
                {editing ? "Lưu thay đổi" : "Tạo địa điểm"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog open={locationToDelete !== null} onOpenChange={(open) => { if (!open) setLocationToDelete(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa địa điểm {locationToDelete?.code}?</AlertDialogTitle>
            <AlertDialogDescription>Địa điểm sẽ bị xóa mềm; lịch sử được giữ lại.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>Hủy</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-white hover:bg-destructive/90"
              disabled={deleting}
              onClick={(event) => { event.preventDefault(); void confirmDelete() }}
            >
              {deleting && <Loader2 className="animate-spin" />}
              Xóa địa điểm
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
