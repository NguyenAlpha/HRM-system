"use client"

import Link from "next/link"
import { useEffect, useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { Menu } from "@base-ui/react/menu"
import { z } from "zod"
import { BadgeCheck, BriefcaseBusiness, Ellipsis, Eye, KeyRound, Loader2, Pencil, Plus, RefreshCw } from "lucide-react"
import { toast } from "sonner"

import { EmployeeAssignmentDialog } from "@/components/employee/employee-assignment-dialog"
import { EmployeeCreateDialog } from "@/components/employee/employee-create-dialog"
import { EmployeeDetailDialog } from "@/components/employee/employee-detail-dialog"
import { EmployeeProvisionDialog } from "@/components/employee/employee-provision-dialog"
import { Pagination, RbacFeedback } from "@/components/admin/rbac-controls"
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
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import {
  EDUCATION_LEVEL_LABELS,
  EDUCATION_LEVELS,
  employeeErrorMessage,
  employeeMutation,
  employeeRequest,
  EMPLOYMENT_STATUS_LABELS,
  GENDER_LABELS,
  GENDERS,
  isSessionExpired,
  type EmployeeDetail,
  type EmployeeSummary,
  type Page,
} from "@/lib/employee"

const GENDER_NONE = "NONE"
const EDU_NONE = "NONE"

const GENDER_ITEMS: Record<string, string> = { [GENDER_NONE]: "Không chọn", ...GENDER_LABELS }
const EDUCATION_ITEMS: Record<string, string> = { [EDU_NONE]: "Không chọn", ...EDUCATION_LEVEL_LABELS }

const employeeEditSchema = z
  .object({
    fullName: z.string().trim().min(1, "Bắt buộc").max(200, "Tối đa 200 ký tự"),
    dateOfBirth: z.string().optional(),
    gender: z.string(),
    highestEducationLevel: z.string(),
    major: z.string().max(200, "Tối đa 200 ký tự").optional(),
    institution: z.string().max(200, "Tối đa 200 ký tự").optional(),
    graduationYear: z.string().optional(),
    workEmail: z.string().max(100, "Tối đa 100 ký tự").optional(),
    phone: z.string().max(20, "Tối đa 20 ký tự").optional(),
  })
  .refine((data) => !data.workEmail || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.workEmail), {
    message: "Email không hợp lệ",
    path: ["workEmail"],
  })
  .refine((data) => !data.graduationYear || /^\d{4}$/.test(data.graduationYear), {
    message: "Năm phải gồm 4 chữ số",
    path: ["graduationYear"],
  })

type EmployeeEditValues = z.infer<typeof employeeEditSchema>

function toFormValues(detail: EmployeeDetail): EmployeeEditValues {
  return {
    fullName: detail.fullName,
    dateOfBirth: detail.dateOfBirth ?? "",
    gender: detail.gender ?? GENDER_NONE,
    highestEducationLevel: detail.highestEducationLevel ?? EDU_NONE,
    major: detail.major ?? "",
    institution: detail.institution ?? "",
    graduationYear: detail.graduationYear ? String(detail.graduationYear) : "",
    workEmail: detail.workEmail ?? "",
    phone: detail.phone ?? "",
  }
}

export function EmployeeManager({ canManage, onSessionExpired }: {
  canManage: boolean
  onSessionExpired: () => void
}) {
  const [data, setData] = useState<Page<EmployeeSummary> | null>(null)
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [sheetRevision, setSheetRevision] = useState(0)
  const [editing, setEditing] = useState<EmployeeDetail | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [dialogError, setDialogError] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [provisioning, setProvisioning] = useState<EmployeeSummary | null>(null)
  const [assigning, setAssigning] = useState<EmployeeSummary | null>(null)
  const [confirming, setConfirming] = useState<EmployeeSummary | null>(null)
  const [confirmingEmployment, setConfirmingEmployment] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors, isSubmitting },
  } = useForm<EmployeeEditValues>({ resolver: zodResolver(employeeEditSchema) })

  useEffect(() => {
    let active = true
    employeeRequest<Page<EmployeeSummary>>("hrm", `?page=${page}&size=10`)
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
        setError(employeeErrorMessage(caught))
        setLoading(false)
      })
    return () => { active = false }
  }, [page, revision, onSessionExpired])

  function reload() {
    setLoading(true)
    setError(null)
    setRevision((value) => value + 1)
  }

  function openEdit(detail: EmployeeDetail) {
    setEditing(detail)
    setDialogError(null)
    reset(toFormValues(detail))
    setDialogOpen(true)
  }

  async function openEditFromList(employeeId: number) {
    try {
      const detail = await employeeRequest<EmployeeDetail>("hrm", `/${employeeId}`)
      openEdit(detail)
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      toast.error("Không thể tải hồ sơ nhân sự", { description: employeeErrorMessage(caught) })
    }
  }

  async function onSubmit(values: EmployeeEditValues) {
    if (!editing) return
    setDialogError(null)
    try {
      const body = {
        fullName: values.fullName.trim(),
        dateOfBirth: values.dateOfBirth || null,
        gender: values.gender === GENDER_NONE ? null : values.gender,
        highestEducationLevel: values.highestEducationLevel === EDU_NONE ? null : values.highestEducationLevel,
        major: values.major?.trim() || null,
        institution: values.institution?.trim() || null,
        graduationYear: values.graduationYear ? Number(values.graduationYear) : null,
        workEmail: values.workEmail?.trim() || null,
        phone: values.phone?.trim() || null,
      }
      const saved = await employeeMutation<EmployeeDetail>("hrm", `/${editing.id}`, "PUT", body)
      toast.success(`Đã cập nhật hồ sơ ${saved.fullName}`)
      setDialogOpen(false)
      setSheetRevision((value) => value + 1)
      reload()
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      setDialogError(employeeErrorMessage(caught))
    }
  }

  async function confirmEmployeeEmployment() {
    if (!confirming) return
    setConfirmingEmployment(true)
    try {
      const saved = await employeeMutation<EmployeeDetail>("hrm", `/${confirming.id}/confirm`, "POST")
      toast.success(`Đã xác nhận ${saved.fullName} là nhân sự chính thức`)
      setConfirming(null)
      setSheetRevision((value) => value + 1)
      reload()
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      toast.error("Không thể xác nhận nhân sự", { description: employeeErrorMessage(caught) })
    } finally {
      setConfirmingEmployment(false)
    }
  }

  return (
    <>
      <Card>
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle>Danh sách nhân sự</CardTitle>
            <CardDescription>Xem hồ sơ và cập nhật thông tin nhân sự trong phạm vi được phân công.</CardDescription>
          </div>
          <div className="flex items-center gap-2">
            <Button type="button" variant="outline" size="sm" onClick={reload} disabled={loading}>
              <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
            </Button>
            {canManage && (
              <Button type="button" size="sm" onClick={() => setCreateOpen(true)}>
                <Plus /> Thêm nhân sự
              </Button>
            )}
          </div>
        </CardHeader>
        <CardContent className="grid gap-4">
          <RbacFeedback error={error} />

          <div className="rounded-xl border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Nhân sự</TableHead>
                  <TableHead>Đơn vị</TableHead>
                  <TableHead>Địa điểm</TableHead>
                  <TableHead>Vị trí công việc</TableHead>
                  <TableHead>Tài khoản</TableHead>
                  <TableHead className="text-right">Thao tác</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loading &&
                  Array.from({ length: 6 }).map((_, index) => (
                    <TableRow key={index}>
                      {Array.from({ length: 6 }).map((__, cell) => (
                        <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
                      ))}
                    </TableRow>
                  ))}

                {!loading && data?.content.map((employee) => (
                  <TableRow
                    key={employee.id}
                    className="cursor-pointer"
                    onClick={() => setSelectedId(employee.id)}
                  >
                    <TableCell>
                      <span className="font-medium">{employee.fullName}</span>
                      <span className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                        <code>{employee.employeeCode}</code>
                        <Badge variant={employee.employmentStatus === "ACTIVE" ? "default" : "outline"}>
                          {EMPLOYMENT_STATUS_LABELS[employee.employmentStatus]}
                        </Badge>
                      </span>
                    </TableCell>
                    <TableCell className="text-sm">
                      {employee.currentAssignment?.organizationUnitName ?? "—"}
                    </TableCell>
                    <TableCell className="text-sm">
                      {employee.currentAssignment?.workLocationName ?? "—"}
                    </TableCell>
                    <TableCell className="text-sm">
                      {employee.currentAssignment?.positionTitle ?? "—"}
                    </TableCell>
                    <TableCell onClick={(event) => event.stopPropagation()}>
                      {employee.account ? (
                        <div>
                          <span className="text-sm font-medium">{employee.account.username}</span>
                          <span className="block text-xs text-muted-foreground">{employee.account.status}</span>
                        </div>
                      ) : canManage ? (
                        <Tooltip>
                          <TooltipTrigger
                            render={
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={() => setProvisioning(employee)}
                              />
                            }
                          >
                            <KeyRound /> Cấp tài khoản
                          </TooltipTrigger>
                          <TooltipContent>Tạo tài khoản đăng nhập cho {employee.fullName}</TooltipContent>
                        </Tooltip>
                      ) : (
                        <span className="text-sm text-muted-foreground">Chưa có tài khoản</span>
                      )}
                    </TableCell>
                    <TableCell className="text-right" onClick={(event) => event.stopPropagation()}>
                      <Menu.Root>
                        <Menu.Trigger
                          aria-label={`Mở menu thao tác cho ${employee.fullName}`}
                          title="Thao tác"
                          render={<Button type="button" variant="ghost" size="icon-sm" />}
                        >
                          <Ellipsis />
                        </Menu.Trigger>
                        <Menu.Portal>
                          <Menu.Positioner side="bottom" align="end" sideOffset={4} className="isolate z-50">
                            <Menu.Popup className="min-w-44 origin-(--transform-origin) rounded-lg bg-popover p-1 text-popover-foreground shadow-md ring-1 ring-foreground/10 outline-none data-open:animate-in data-open:fade-in-0 data-open:zoom-in-95 data-closed:animate-out data-closed:fade-out-0 data-closed:zoom-out-95">
                              <Menu.LinkItem
                                closeOnClick
                                render={<Link href={`/employees/${employee.id}`} />}
                                className="flex cursor-default items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none select-none data-highlighted:bg-accent data-highlighted:text-accent-foreground"
                              >
                                <Eye className="size-4" /> Xem chi tiết
                              </Menu.LinkItem>
                              {canManage && (
                                <Menu.Item
                                  onClick={() => { void openEditFromList(employee.id) }}
                                  className="flex cursor-default items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none select-none data-highlighted:bg-accent data-highlighted:text-accent-foreground"
                                >
                                  <Pencil className="size-4" /> Sửa hồ sơ
                                </Menu.Item>
                              )}
                              {canManage && (employee.employmentStatus === "ACTIVE" || employee.employmentStatus === "PROBATION") && (
                                <Menu.Item
                                  onClick={() => setAssigning(employee)}
                                  className="flex cursor-default items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none select-none data-highlighted:bg-accent data-highlighted:text-accent-foreground"
                                >
                                  <BriefcaseBusiness className="size-4" /> Thay đổi phân công
                                </Menu.Item>
                              )}
                              {canManage && employee.employmentStatus === "PROBATION" && (
                                <Menu.Item
                                  onClick={() => setConfirming(employee)}
                                  className="flex cursor-default items-center gap-2 rounded-md px-2 py-1.5 text-sm outline-none select-none data-highlighted:bg-accent data-highlighted:text-accent-foreground"
                                >
                                  <BadgeCheck className="size-4" /> Xác nhận chính thức
                                </Menu.Item>
                              )}
                            </Menu.Popup>
                          </Menu.Positioner>
                        </Menu.Portal>
                      </Menu.Root>
                    </TableCell>
                  </TableRow>
                ))}

                {!loading && data?.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} className="h-24 text-center text-muted-foreground">
                      Chưa có nhân sự nào trong phạm vi của bạn.
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

      {selectedId !== null && (
        <EmployeeDetailDialog
          key={`${selectedId}-${sheetRevision}`}
          employeeId={selectedId}
          canManage={canManage}
          onClose={() => setSelectedId(null)}
          onEdit={openEdit}
          onProvision={setProvisioning}
          onSessionExpired={onSessionExpired}
        />
      )}

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-lg">
          <form onSubmit={handleSubmit(onSubmit)}>
            <DialogHeader>
              <DialogTitle>Sửa hồ sơ {editing?.fullName}</DialogTitle>
              <DialogDescription>Cập nhật thông tin cá nhân và học vấn của nhân sự.</DialogDescription>
            </DialogHeader>

            <div className="mt-4 grid max-h-[65vh] gap-4 overflow-y-auto pr-1">
              <RbacFeedback error={dialogError} />

              <div className="grid gap-1.5">
                <Label htmlFor="emp-fullname">Họ tên</Label>
                <Input id="emp-fullname" aria-invalid={!!errors.fullName} {...register("fullName")} />
                {errors.fullName && <p className="text-xs font-medium text-destructive">{errors.fullName.message}</p>}
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-1.5">
                  <Label htmlFor="emp-dob">Ngày sinh</Label>
                  <Input id="emp-dob" type="date" {...register("dateOfBirth")} />
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="emp-gender">Giới tính</Label>
                  <Controller
                    control={control}
                    name="gender"
                    render={({ field }) => (
                      <Select value={field.value} items={GENDER_ITEMS} onValueChange={field.onChange}>
                        <SelectTrigger id="emp-gender" className="w-full">
                          <SelectValue placeholder="Chọn..." />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value={GENDER_NONE}>Không chọn</SelectItem>
                          {GENDERS.map((g) => <SelectItem key={g} value={g}>{GENDER_LABELS[g]}</SelectItem>)}
                        </SelectContent>
                      </Select>
                    )}
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-1.5">
                  <Label htmlFor="emp-education">Trình độ học vấn</Label>
                  <Controller
                    control={control}
                    name="highestEducationLevel"
                    render={({ field }) => (
                      <Select value={field.value} items={EDUCATION_ITEMS} onValueChange={field.onChange}>
                        <SelectTrigger id="emp-education" className="w-full">
                          <SelectValue placeholder="Chọn..." />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value={EDU_NONE}>Không chọn</SelectItem>
                          {EDUCATION_LEVELS.map((e) => <SelectItem key={e} value={e}>{EDUCATION_LEVEL_LABELS[e]}</SelectItem>)}
                        </SelectContent>
                      </Select>
                    )}
                  />
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="emp-grad-year">Năm tốt nghiệp</Label>
                  <Input id="emp-grad-year" inputMode="numeric" placeholder="VD: 2020" aria-invalid={!!errors.graduationYear} {...register("graduationYear")} />
                  {errors.graduationYear && <p className="text-xs font-medium text-destructive">{errors.graduationYear.message}</p>}
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-1.5">
                  <Label htmlFor="emp-major">Chuyên ngành</Label>
                  <Input id="emp-major" {...register("major")} />
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="emp-institution">Trường / đơn vị đào tạo</Label>
                  <Input id="emp-institution" {...register("institution")} />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-1.5">
                  <Label htmlFor="emp-email">Email công việc</Label>
                  <Input id="emp-email" type="email" aria-invalid={!!errors.workEmail} {...register("workEmail")} />
                  {errors.workEmail && <p className="text-xs font-medium text-destructive">{errors.workEmail.message}</p>}
                </div>
                <div className="grid gap-1.5">
                  <Label htmlFor="emp-phone">Điện thoại</Label>
                  <Input id="emp-phone" aria-invalid={!!errors.phone} {...register("phone")} />
                  {errors.phone && <p className="text-xs font-medium text-destructive">{errors.phone.message}</p>}
                </div>
              </div>
            </div>

            <DialogFooter>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting && <Loader2 className="animate-spin" />}
                Lưu thay đổi
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {createOpen && (
        <EmployeeCreateDialog
          onClose={() => setCreateOpen(false)}
          onCreated={() => { setPage(0); reload() }}
          onSessionExpired={onSessionExpired}
        />
      )}

      {provisioning && (
        <EmployeeProvisionDialog
          employeeId={provisioning.id}
          employeeName={provisioning.fullName}
          onClose={() => setProvisioning(null)}
          onProvisioned={() => { setSheetRevision((value) => value + 1); reload() }}
          onSessionExpired={onSessionExpired}
        />
      )}

      {assigning && (
        <EmployeeAssignmentDialog
          employee={assigning}
          onClose={() => setAssigning(null)}
          onAssigned={() => { setSheetRevision((value) => value + 1); reload() }}
          onSessionExpired={onSessionExpired}
        />
      )}

      <AlertDialog
        open={confirming !== null}
        onOpenChange={(open) => { if (!open && !confirmingEmployment) setConfirming(null) }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xác nhận nhân sự chính thức?</AlertDialogTitle>
            <AlertDialogDescription>
              Chuyển {confirming?.fullName} ({confirming?.employeeCode}) từ “Thử việc” sang “Đang làm việc”.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={confirmingEmployment}>Hủy</AlertDialogCancel>
            <AlertDialogAction
              disabled={confirmingEmployment}
              onClick={(event) => { event.preventDefault(); void confirmEmployeeEmployment() }}
            >
              {confirmingEmployment && <Loader2 className="animate-spin" />}
              Xác nhận chính thức
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
