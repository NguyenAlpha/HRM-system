"use client"

import { useEffect, useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { KeyRound, Loader2, Plus, RefreshCw } from "lucide-react"
import { toast } from "sonner"

import { EmployeeCreateDialog } from "@/components/employee/employee-create-dialog"
import { EmployeeDetailSheet } from "@/components/employee/employee-detail-sheet"
import { EmployeeProvisionDialog } from "@/components/employee/employee-provision-dialog"
import { Pagination, RbacFeedback } from "@/components/admin/rbac-controls"
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

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors, isSubmitting },
  } = useForm<EmployeeEditValues>({ resolver: zodResolver(employeeEditSchema) })

  useEffect(() => {
    let active = true
    employeeRequest<Page<EmployeeSummary>>(`?page=${page}&size=10`)
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
      const saved = await employeeMutation<EmployeeDetail>(`/${editing.id}`, "PUT", body)
      toast.success(`Đã cập nhật hồ sơ ${saved.fullName}`)
      setDialogOpen(false)
      setSheetRevision((value) => value + 1)
      reload()
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      setDialogError(employeeErrorMessage(caught))
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
                  <TableHead>Liên hệ</TableHead>
                  <TableHead>Ngày vào làm</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead>Tài khoản</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loading &&
                  Array.from({ length: 6 }).map((_, index) => (
                    <TableRow key={index}>
                      {Array.from({ length: 5 }).map((__, cell) => (
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
                      <span className="block text-xs text-muted-foreground"><code>{employee.employeeCode}</code></span>
                    </TableCell>
                    <TableCell>
                      <span className="block text-sm">{employee.workEmail || "—"}</span>
                      <span className="block text-xs text-muted-foreground">{employee.phone || "—"}</span>
                    </TableCell>
                    <TableCell className="text-sm">{employee.hireDate}</TableCell>
                    <TableCell>
                      <Badge variant={employee.employmentStatus === "ACTIVE" ? "default" : "outline"}>
                        {EMPLOYMENT_STATUS_LABELS[employee.employmentStatus]}
                      </Badge>
                    </TableCell>
                    <TableCell onClick={(event) => event.stopPropagation()}>
                      {employee.account ? (
                        <span className="text-sm">{employee.account.username}</span>
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
                  </TableRow>
                ))}

                {!loading && data?.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} className="h-24 text-center text-muted-foreground">
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
        <EmployeeDetailSheet
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
    </>
  )
}
