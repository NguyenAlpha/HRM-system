"use client"

import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { Loader2, Pencil, Plus, RefreshCw, ShieldCheck, Trash2 } from "lucide-react"
import { toast } from "sonner"

import { Pagination, RbacFeedback } from "@/components/admin/rbac-controls"
import { RolePermissions } from "@/components/admin/role-permissions"
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
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Textarea } from "@/components/ui/textarea"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { isSessionExpired, rbacErrorMessage, rbacMutation, rbacRequest, type Page, type Role } from "@/lib/rbac"

const CODE_PATTERN = /^(?!ROLE_)[A-Z][A-Z0-9_]*$/

const roleSchema = z.object({
  code: z
    .string()
    .trim()
    .min(1, "Bắt buộc")
    .max(50, "Tối đa 50 ký tự")
    .regex(CODE_PATTERN, "Chữ hoa, số, gạch dưới; bắt đầu bằng chữ cái; không bắt đầu bằng ROLE_"),
  name: z.string().trim().min(1, "Bắt buộc").max(150, "Tối đa 150 ký tự"),
  description: z.string().max(1000, "Tối đa 1000 ký tự").optional(),
})

type RoleValues = z.infer<typeof roleSchema>

const EMPTY_FORM: RoleValues = { code: "", name: "", description: "" }

export function RoleManager({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [data, setData] = useState<Page<Role> | null>(null)
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<Role | null>(null)
  const [dialogError, setDialogError] = useState<string | null>(null)
  const [roleToDelete, setRoleToDelete] = useState<Role | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [selected, setSelected] = useState<Role | null>(null)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<RoleValues>({
    resolver: zodResolver(roleSchema),
    defaultValues: EMPTY_FORM,
  })

  useEffect(() => {
    let active = true
    rbacRequest<Page<Role>>(`/roles?page=${page}&size=10&sort=id,desc`)
      .then((result) => {
        if (!active) return
        if (page > 0 && page >= result.totalPages) {
          setPage(Math.max(0, result.totalPages - 1))
          return
        }
        setData(result)
        setLoading(false)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isSessionExpired(caught)) onSessionExpired()
        setError(rbacErrorMessage(caught))
        setLoading(false)
      })
    return () => { active = false }
  }, [page, revision, onSessionExpired])

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

  function openEdit(role: Role) {
    setEditing(role)
    setDialogError(null)
    reset({ code: role.code, name: role.name, description: role.description ?? "" })
    setDialogOpen(true)
  }

  async function onSubmit(values: RoleValues) {
    setDialogError(null)
    try {
      const body = { name: values.name.trim(), description: values.description?.trim() || null }
      const saved = editing
        ? await rbacMutation<Role>(`/roles/${editing.id}`, "PUT", body)
        : await rbacMutation<Role>("/roles", "POST", { ...body, code: values.code.trim() })
      if (selected?.id === saved.id) setSelected(saved)
      toast.success(editing ? `Đã cập nhật vai trò ${saved.code}` : `Đã tạo vai trò ${saved.code}`)
      if (!editing) setPage(0)
      setDialogOpen(false)
      reload()
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      setDialogError(rbacErrorMessage(caught))
    }
  }

  async function confirmDelete() {
    if (!roleToDelete) return
    setDeleting(true)
    try {
      await rbacMutation(`/roles/${roleToDelete.id}`, "DELETE")
      if (selected?.id === roleToDelete.id) setSelected(null)
      toast.success(`Đã xóa vai trò ${roleToDelete.code}`)
      setRoleToDelete(null)
      reload()
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      toast.error(rbacErrorMessage(caught))
    } finally {
      setDeleting(false)
    }
  }

  return (
    <>
      <Card>
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle>Danh sách vai trò</CardTitle>
            <CardDescription>Tạo, sửa và xóa vai trò tùy chỉnh trong doanh nghiệp.</CardDescription>
          </div>
          <div className="flex items-center gap-2">
            <Button type="button" variant="outline" size="sm" onClick={reload} disabled={loading}>
              <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
            </Button>
            <Button type="button" size="sm" onClick={openCreate}>
              <Plus /> Thêm vai trò
            </Button>
          </div>
        </CardHeader>
        <CardContent className="grid gap-4">
          <RbacFeedback error={error} />

          <div className="rounded-xl border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Mã / ID</TableHead>
                  <TableHead>Tên / mô tả</TableHead>
                  <TableHead>Loại</TableHead>
                  <TableHead className="text-right">Thao tác</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loading &&
                  Array.from({ length: 5 }).map((_, index) => (
                    <TableRow key={index}>
                      {Array.from({ length: 4 }).map((__, cell) => (
                        <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
                      ))}
                    </TableRow>
                  ))}

                {!loading && data?.content.map((role) => (
                  <TableRow key={role.id}>
                    <TableCell>
                      <code className="text-xs">{role.code}</code>
                      <span className="block text-xs text-muted-foreground">#{role.id}</span>
                    </TableCell>
                    <TableCell>
                      <span className="font-medium">{role.name}</span>
                      {role.description && (
                        <span className="block max-w-xs text-xs text-wrap text-muted-foreground">{role.description}</span>
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge variant={role.isSystem ? "secondary" : "default"}>
                        {role.isSystem ? "Hệ thống" : "Tùy chỉnh"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <div className="flex justify-end gap-1">
                        <Tooltip>
                          <TooltipTrigger
                            render={
                              <Button
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                disabled={role.isSystem}
                                onClick={() => openEdit(role)}
                                aria-label={`Sửa ${role.code}`}
                              />
                            }
                          >
                            <Pencil />
                          </TooltipTrigger>
                          <TooltipContent>
                            {role.isSystem ? "System role do ứng dụng định nghĩa" : "Sửa vai trò"}
                          </TooltipContent>
                        </Tooltip>

                        <Tooltip>
                          <TooltipTrigger
                            render={
                              <Button
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                onClick={() => setSelected(role)}
                                aria-label={`Quyền của ${role.code}`}
                              />
                            }
                          >
                            <ShieldCheck />
                          </TooltipTrigger>
                          <TooltipContent>{role.isSystem ? "Xem quyền" : "Phân quyền"}</TooltipContent>
                        </Tooltip>

                        <Tooltip>
                          <TooltipTrigger
                            render={
                              <Button
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                disabled={role.isSystem}
                                className="text-muted-foreground hover:text-destructive"
                                onClick={() => setRoleToDelete(role)}
                                aria-label={`Xóa ${role.code}`}
                              />
                            }
                          >
                            <Trash2 />
                          </TooltipTrigger>
                          <TooltipContent>
                            {role.isSystem ? "Không thể xóa vai trò hệ thống" : "Xóa vai trò"}
                          </TooltipContent>
                        </Tooltip>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}

                {!loading && data?.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} className="h-24 text-center text-muted-foreground">
                      Chưa có vai trò.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>

          <Pagination page={page} totalPages={data?.totalPages ?? 0} totalElements={data?.totalElements ?? 0} disabled={loading}
            onChange={(next) => { setLoading(true); setError(null); setPage(next) }} />
        </CardContent>
      </Card>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <form onSubmit={handleSubmit(onSubmit)}>
            <DialogHeader>
              <DialogTitle>{editing ? `Sửa vai trò ${editing.code}` : "Thêm vai trò"}</DialogTitle>
              <DialogDescription>
                {editing ? "Cập nhật tên và mô tả của vai trò." : "Tạo vai trò tùy chỉnh mới cho doanh nghiệp."}
              </DialogDescription>
            </DialogHeader>

            <div className="mt-4 grid gap-4">
              <RbacFeedback error={dialogError} />

              <div className="grid gap-1.5">
                <Label htmlFor="role-code">Mã vai trò</Label>
                <Input
                  id="role-code"
                  placeholder="VD: RBAC_MANAGER"
                  disabled={Boolean(editing)}
                  aria-invalid={!!errors.code}
                  {...register("code")}
                />
                {errors.code && <p className="text-xs font-medium text-destructive">{errors.code.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="role-name">Tên vai trò</Label>
                <Input id="role-name" aria-invalid={!!errors.name} {...register("name")} />
                {errors.name && <p className="text-xs font-medium text-destructive">{errors.name.message}</p>}
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="role-description">Mô tả</Label>
                <Textarea id="role-description" rows={3} {...register("description")} />
                {errors.description && <p className="text-xs font-medium text-destructive">{errors.description.message}</p>}
              </div>
            </div>

            <DialogFooter>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="animate-spin" />}
                {editing ? "Lưu vai trò" : "Tạo vai trò"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog open={roleToDelete !== null} onOpenChange={(open) => { if (!open) setRoleToDelete(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa vai trò {roleToDelete?.code}?</AlertDialogTitle>
            <AlertDialogDescription>
              Vai trò sẽ bị xóa mềm; lịch sử được giữ lại. Hành động này không thể hoàn tác từ giao diện.
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
              Xóa vai trò
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {selected && (
        <RolePermissions
          key={selected.id}
          role={selected}
          onClose={() => setSelected(null)}
          onSessionExpired={onSessionExpired}
        />
      )}
    </>
  )
}
