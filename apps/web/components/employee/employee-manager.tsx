"use client"

import { useEffect, useState } from "react"
import { BadgeCheck, BriefcaseBusiness, Eye, KeyRound, Loader2, Pencil, Plus, RefreshCw } from "lucide-react"
import { toast } from "sonner"

import { EmployeeAssignmentDialog } from "@/components/employee/employee-assignment-dialog"
import { EmployeeCreateDialog } from "@/components/employee/employee-create-dialog"
import { EmployeeDetailDialog } from "@/components/employee/employee-detail-dialog"
import { EmployeeEditDialog } from "@/components/employee/employee-edit-dialog"
import { EmployeeProvisionDialog } from "@/components/employee/employee-provision-dialog"
import {
  ALL_OPTION,
  FilterSelect,
  ListToolbar,
  SortableHead,
  sortParam,
  useDebouncedValue,
  type SortState,
} from "@/components/common/list-filters"
import { Pagination, ErrorAlert } from "@/components/common/list-controls"
import { RowActionItem, RowActionLink, RowActionsCell } from "@/components/common/row-actions"
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
  employeeErrorMessage,
  employeeListQuery,
  employeeMutation,
  employeeRequest,
  EMPLOYMENT_STATUS_LABELS,
  isSessionExpired,
  type EmployeeDetail,
  type EmployeeFilters,
  type EmployeeSummary,
  type EmploymentStatus,
} from "@/lib/employee"
import { PORTAL_CONFIG } from "@/lib/auth/config"
import type { Portal } from "@/lib/auth/types"
import type { Page } from "@/lib/pagination"
import { getJobPositionOptions, getOrganizationUnitOptions } from "@/lib/reference"

const EMPTY_FILTERS: EmployeeFilters = { keyword: "", employmentStatus: "", organizationUnitId: "", positionId: "" }

const DEFAULT_SORT: SortState = { field: "id", direction: "desc" }

const SORT_OPTIONS = [
  { value: "fullName,asc", label: "Họ tên A → Z" },
  { value: "fullName,desc", label: "Họ tên Z → A" },
  { value: "employeeCode,asc", label: "Mã nhân sự" },
  { value: "hireDate,desc", label: "Ngày vào làm (mới nhất)" },
  { value: "hireDate,asc", label: "Ngày vào làm (lâu nhất)" },
]

type EmployeeCapabilities = {
  canReadDetail: boolean
  canCreate: boolean
  canUpdate: boolean
  canAssign: boolean
  canConfirmProbation: boolean
  canProvisionAccount: boolean
}

export function EmployeeManager({ portal, capabilities, onSessionExpired }: {
  portal: Portal
  capabilities: EmployeeCapabilities
  onSessionExpired: () => void
}) {
  const {
    canReadDetail,
    canCreate,
    canUpdate,
    canAssign,
    canConfirmProbation,
    canProvisionAccount,
  } = capabilities
  const canEdit = canReadDetail && canUpdate
  const [data, setData] = useState<Page<EmployeeSummary> | null>(null)
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [loadedQuery, setLoadedQuery] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [sheetRevision, setSheetRevision] = useState(0)
  const [editing, setEditing] = useState<EmployeeDetail | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [provisioning, setProvisioning] = useState<EmployeeSummary | null>(null)
  const [assigning, setAssigning] = useState<EmployeeSummary | null>(null)
  const [confirming, setConfirming] = useState<EmployeeSummary | null>(null)
  const [confirmingEmployment, setConfirmingEmployment] = useState(false)
  const [filters, setFilters] = useState<EmployeeFilters>(EMPTY_FILTERS)
  const [sort, setSort] = useState<SortState>(DEFAULT_SORT)
  const [unitOptions, setUnitOptions] = useState<{ value: string; label: string }[]>([])
  const [positionOptions, setPositionOptions] = useState<{ value: string; label: string }[]>([])
  const keyword = useDebouncedValue(filters.keyword)

  // Unit and position filters need organization.read; without it those two filters are not shown.
  useEffect(() => {
    let active = true
    Promise.all([getOrganizationUnitOptions(portal), getJobPositionOptions(portal)])
      .then(([units, positions]) => {
        if (!active) return
        setUnitOptions(units.map((unit) => ({ value: String(unit.id), label: `${unit.name} (${unit.code})` })))
        setPositionOptions(positions.map((position) => ({ value: String(position.id), label: position.title })))
      })
      .catch(() => undefined)
    return () => { active = false }
  }, [portal])

  // The keyword goes through its debounced copy so typing does not fire a request per key.
  const query = employeeListQuery(page, 10, {
    keyword,
    employmentStatus: filters.employmentStatus,
    organizationUnitId: filters.organizationUnitId,
    positionId: filters.positionId,
  }, sortParam(sort))
  const requestKey = `${query}#${revision}`
  // Each response records the query it answered, so picking the value already selected or typing
  // back to the same keyword cannot leave the table loading; a keyword still settling counts too.
  const loading = loadedQuery !== requestKey || keyword !== filters.keyword

  useEffect(() => {
    let active = true
    employeeRequest<Page<EmployeeSummary>>(portal, query)
      .then((result) => {
        if (!active) return
        if (page > 0 && page >= result.totalPages) {
          setPage(Math.max(0, result.totalPages - 1))
          return
        }
        setData(result)
        setLoadedQuery(requestKey)
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isSessionExpired(caught)) onSessionExpired()
        setError(employeeErrorMessage(caught))
        setLoadedQuery(requestKey)
      })
    return () => { active = false }
  }, [portal, page, query, requestKey, onSessionExpired])

  function changeFilters(next: Partial<EmployeeFilters>) {
    setPage(0)
    setFilters((current) => ({ ...current, ...next }))
  }

  function changeSort(next: SortState) {
    setPage(0)
    setSort(next)
  }

  function reload() {
    setError(null)
    setRevision((value) => value + 1)
  }

  function openEdit(detail: EmployeeDetail) {
    setEditing(detail)
  }

  async function openEditFromList(employeeId: number) {
    try {
      const detail = await employeeRequest<EmployeeDetail>(portal, `/${employeeId}`)
      openEdit(detail)
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      toast.error("Không thể tải hồ sơ nhân sự", { description: employeeErrorMessage(caught) })
    }
  }

  async function confirmEmployeeEmployment() {
    if (!confirming) return
    setConfirmingEmployment(true)
    try {
      const saved = await employeeMutation<EmployeeDetail>(portal, `/${confirming.id}/confirm`, "POST")
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
            {canCreate && (
              <Button type="button" size="sm" onClick={() => setCreateOpen(true)}>
                <Plus /> Thêm nhân sự
              </Button>
            )}
          </div>
        </CardHeader>
        <CardContent className="grid gap-4">
          <ErrorAlert error={error} />

          <ListToolbar
            keyword={filters.keyword}
            onKeywordChange={(value) => changeFilters({ keyword: value })}
            placeholder="Tìm theo họ tên, mã nhân sự hoặc email công việc"
            activeFilters={[filters.employmentStatus, filters.organizationUnitId, filters.positionId].filter(Boolean).length}
            onReset={() => { changeFilters(EMPTY_FILTERS); changeSort(DEFAULT_SORT) }}
          >
            <FilterSelect
              id="employee-status-filter"
              label="Trạng thái làm việc"
              value={filters.employmentStatus || ALL_OPTION}
              onChange={(value) => changeFilters({ employmentStatus: value === ALL_OPTION ? "" : value as EmploymentStatus })}
              options={Object.entries(EMPLOYMENT_STATUS_LABELS).map(([value, label]) => ({ value, label }))}
            />
            {unitOptions.length > 0 && (
              <FilterSelect
                id="employee-unit-filter"
                label="Đơn vị (phân công hiện tại)"
                value={filters.organizationUnitId || ALL_OPTION}
                onChange={(value) => changeFilters({ organizationUnitId: value === ALL_OPTION ? "" : value })}
                options={unitOptions}
              />
            )}
            {positionOptions.length > 0 && (
              <FilterSelect
                id="employee-position-filter"
                label="Vị trí công việc"
                value={filters.positionId || ALL_OPTION}
                onChange={(value) => changeFilters({ positionId: value === ALL_OPTION ? "" : value })}
                options={positionOptions}
              />
            )}
            <FilterSelect
              id="employee-sort"
              label="Sắp xếp theo"
              value={SORT_OPTIONS.some((option) => option.value === sortParam(sort)) ? sortParam(sort) : ALL_OPTION}
              onChange={(value) => {
                if (value === ALL_OPTION) return changeSort(DEFAULT_SORT)
                const [field, direction] = value.split(",")
                changeSort({ field, direction: direction as SortState["direction"] })
              }}
              options={SORT_OPTIONS}
              allLabel="Mới thêm gần đây"
            />
          </ListToolbar>

          <div className="rounded-xl border">
            <Table>
              <TableHeader>
                <TableRow>
                  <SortableHead label="Nhân sự" field="fullName" sort={sort} onSort={changeSort} />
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
                    className={canReadDetail ? "cursor-pointer" : undefined}
                    onClick={() => { if (canReadDetail) setSelectedId(employee.id) }}
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
                      ) : canProvisionAccount ? (
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
                    <RowActionsCell label={employee.fullName}>
                      {canReadDetail && (
                        <RowActionLink icon={Eye} href={`${PORTAL_CONFIG[portal].pagePrefix}/employees/${employee.id}`}>
                          Xem chi tiết
                        </RowActionLink>
                      )}
                      {canEdit && (
                        <RowActionItem icon={Pencil} onClick={() => { void openEditFromList(employee.id) }}>Sửa hồ sơ</RowActionItem>
                      )}
                      {canAssign && (employee.employmentStatus === "ACTIVE" || employee.employmentStatus === "PROBATION") && (
                        <RowActionItem icon={BriefcaseBusiness} onClick={() => setAssigning(employee)}>Thay đổi phân công</RowActionItem>
                      )}
                      {canConfirmProbation && employee.employmentStatus === "PROBATION" && (
                        <RowActionItem icon={BadgeCheck} onClick={() => setConfirming(employee)}>Xác nhận chính thức</RowActionItem>
                      )}
                    </RowActionsCell>
                  </TableRow>
                ))}

                {!loading && data?.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6} className="h-24 text-center text-muted-foreground">
                      {filters.keyword || filters.employmentStatus || filters.organizationUnitId || filters.positionId
                        ? "Không có nhân sự phù hợp với bộ lọc."
                        : "Chưa có nhân sự nào trong phạm vi của bạn."}
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </div>

          <Pagination page={page} totalPages={data?.totalPages ?? 0} totalElements={data?.totalElements ?? 0} disabled={loading}
            onChange={(next) => { setError(null); setPage(next) }} />
        </CardContent>
      </Card>

      {selectedId !== null && (
        <EmployeeDetailDialog
          key={`${selectedId}-${sheetRevision}`}
          portal={portal}
          employeeId={selectedId}
          canUpdate={canUpdate}
          canProvisionAccount={canProvisionAccount}
          onClose={() => setSelectedId(null)}
          onEdit={openEdit}
          onProvision={setProvisioning}
          onSessionExpired={onSessionExpired}
        />
      )}

      {editing && (
        <EmployeeEditDialog
          portal={portal}
          detail={editing}
          onClose={() => setEditing(null)}
          onSaved={() => { setSheetRevision((value) => value + 1); reload() }}
          onSessionExpired={onSessionExpired}
        />
      )}

      {createOpen && (
        <EmployeeCreateDialog
          onClose={() => setCreateOpen(false)}
          onCreated={() => { setPage(0); reload() }}
          onSessionExpired={onSessionExpired}
        />
      )}

      {provisioning && (
        <EmployeeProvisionDialog
          portal={portal}
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
