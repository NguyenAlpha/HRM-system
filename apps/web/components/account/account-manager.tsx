"use client"

import Link from "next/link"
import { useEffect, useState } from "react"
import { Eye, RefreshCw, ShieldMinus } from "lucide-react"

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
import { holdsRevocableRole } from "@/components/account/account-access"
import {
  ACCOUNT_ACTIONS,
  ACCOUNT_STATUS_LABELS,
  AccountStatusBadge,
  AccountActionDialog,
  availableAccountActions,
  isGuardedAccount,
  type AccountActionKind,
  type AccountViewer,
} from "@/components/account/account-actions"
import { AccountDetailDialog } from "@/components/account/account-detail-dialog"
import { RevokeRoleDialog } from "@/components/account/revoke-role-dialog"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { listAccounts, type Account, type AccountFilters, type AccountPage } from "@/lib/account"
import { PORTAL_CONFIG } from "@/lib/auth/config"
import { formatDateTime } from "@/lib/format-date"
import type { Page } from "@/lib/pagination"
import { rbacRequest, type Role } from "@/lib/rbac"
import type { Portal } from "@/lib/auth/types"

const EMPTY_FILTERS: AccountFilters = { keyword: "", status: "", roleCode: "", deleted: false }
const DELETED_OPTION = "DELETED"

export function AccountManager({ portal, viewer, onSessionExpired }: {
  portal: Portal
  viewer: AccountViewer
  onSessionExpired: () => void
}) {
  const [data, setData] = useState<AccountPage | null>(null)
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [loadedQuery, setLoadedQuery] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState<{ account: Account; kind: AccountActionKind } | null>(null)
  const [revoking, setRevoking] = useState<Account | null>(null)
  // Without either lifecycle permission the list is read-only; the row menu then only opens the detail view.
  const hasActions = viewer.canManage || viewer.canManageActivation
  const [viewing, setViewing] = useState<Account | null>(null)
  const [filters, setFilters] = useState<AccountFilters>(EMPTY_FILTERS)
  const [sort, setSort] = useState<SortState>({ field: "id", direction: "desc" })
  const [roleOptions, setRoleOptions] = useState<{ value: string; label: string }[]>([])
  const keyword = useDebouncedValue(filters.keyword)

  // Role filter options; listing roles needs rbac.manage, so without it the role filter stays empty.
  useEffect(() => {
    let active = true
    rbacRequest<Page<Role>>(portal, "/roles?page=0&size=100&sort=name,asc")
      .then((result) => {
        if (active) setRoleOptions(result.content.map((role) => ({ value: role.code, label: role.name })))
      })
      .catch(() => undefined)
    return () => { active = false }
  }, [portal])

  const requestKey = JSON.stringify([page, revision, keyword, filters.status, filters.roleCode, filters.deleted, sortParam(sort)])
  // Each response records the request it answered, so picking the value already selected or typing
  // back to the same keyword cannot leave the table loading; a keyword still settling counts too.
  const loading = loadedQuery !== requestKey || keyword !== filters.keyword

  useEffect(() => {
    let active = true
    // The keyword goes through its debounced copy so typing does not fire a request per key.
    listAccounts(portal, page, { keyword, status: filters.status, roleCode: filters.roleCode, deleted: filters.deleted }, sortParam(sort))
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
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setError(apiErrorMessage(caught))
        setLoadedQuery(requestKey)
      })
    return () => { active = false }
  }, [portal, page, revision, keyword, filters.status, filters.roleCode, filters.deleted, sort, requestKey, onSessionExpired])

  function changeFilters(next: Partial<AccountFilters>) {
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

  // Revoking needs account.role.assign, and the account guard rules apply as for the other actions.
  function canRevokeRole(account: Account) {
    return viewer.canAssignRole && !isGuardedAccount(account, viewer) && holdsRevocableRole(account.roleCodes)
  }

  return (
    <>
      <Card>
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle>Danh sách tài khoản</CardTitle>
            <CardDescription>
              {hasActions
                ? "Gửi lại lời mời, đặt lại mật khẩu, vô hiệu hóa hoặc kích hoạt lại tài khoản đăng nhập. Bấm vào một dòng để xem role và permission."
                : "Danh sách tài khoản đăng nhập (chỉ xem). Bấm vào một dòng để xem role và permission."}
            </CardDescription>
          </div>
          <Button type="button" variant="outline" size="sm" onClick={reload} disabled={loading}>
            <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
          </Button>
        </CardHeader>
        <CardContent className="grid gap-4">
          <ErrorAlert error={error} />

          <ListToolbar
            keyword={filters.keyword}
            onKeywordChange={(value) => changeFilters({ keyword: value })}
            placeholder="Tìm theo tên đăng nhập, email, tên hoặc mã nhân viên"
            activeFilters={(filters.status ? 1 : 0) + (filters.roleCode ? 1 : 0) + (filters.deleted ? 1 : 0)}
            onReset={() => changeFilters(EMPTY_FILTERS)}
          >
            <FilterSelect
              id="account-status-filter"
              label="Trạng thái"
              value={filters.status || ALL_OPTION}
              onChange={(value) => changeFilters({ status: value === ALL_OPTION ? "" : value as Account["status"] })}
              options={Object.entries(ACCOUNT_STATUS_LABELS).map(([value, label]) => ({ value, label }))}
            />
            <FilterSelect
              id="account-deleted-filter"
              label="Hiển thị"
              value={filters.deleted ? DELETED_OPTION : ALL_OPTION}
              allLabel="Tài khoản chưa xóa"
              onChange={(value) => changeFilters({ deleted: value === DELETED_OPTION })}
              options={[{ value: DELETED_OPTION, label: "Tài khoản đã xóa" }]}
            />
            {roleOptions.length > 0 && (
              <FilterSelect
                id="account-role-filter"
                label="Vai trò đang giữ"
                value={filters.roleCode || ALL_OPTION}
                onChange={(value) => changeFilters({ roleCode: value === ALL_OPTION ? "" : value })}
                options={roleOptions}
              />
            )}
          </ListToolbar>

          <div className="rounded-xl border">
            <Table>
              <TableHeader>
                <TableRow>
                  <SortableHead label="Tài khoản" field="username" sort={sort} onSort={changeSort} />
                  <TableHead>Nhân viên</TableHead>
                  <SortableHead label="Trạng thái" field="status" sort={sort} onSort={changeSort} />
                  <SortableHead label="Đăng nhập gần nhất" field="lastLoginAt" sort={sort} onSort={changeSort} />
                  <TableHead className="text-right">Thao tác</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loading &&
                  Array.from({ length: 5 }).map((_, index) => (
                    <TableRow key={index}>
                      {Array.from({ length: 5 }).map((__, cell) => (
                        <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
                      ))}
                    </TableRow>
                  ))}

                {!loading && data?.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} className="h-24 text-center text-muted-foreground">
                      {filters.keyword || filters.status || filters.roleCode || filters.deleted ? "Không có tài khoản phù hợp với bộ lọc." : "Chưa có tài khoản nào."}
                    </TableCell>
                  </TableRow>
                )}

                {!loading && data?.content.map((account) => (
                  <TableRow key={account.id} className="cursor-pointer" onClick={() => setViewing(account)}>
                    <TableCell>
                      <span className="font-medium">{account.username}</span>
                      {account.id === viewer.accountId && <span className="ml-1.5 text-xs text-muted-foreground">(bạn)</span>}
                      <span className="block text-xs text-muted-foreground">{account.email}</span>
                    </TableCell>
                    <TableCell>
                      {account.employeeId === null
                        ? <span className="text-muted-foreground">—</span>
                        : (
                          <Link className="text-sm underline-offset-4 hover:underline" onClick={(event) => event.stopPropagation()} href={`${PORTAL_CONFIG[portal].pagePrefix}/employees/${account.employeeId}`}>
                            {account.employeeFullName}
                            <span className="block text-xs text-muted-foreground">{account.employeeCode}</span>
                          </Link>
                        )}
                    </TableCell>
                    <TableCell>
                      <AccountStatusBadge account={account} />
                      {account.deletedAt && (
                        <span className="mt-1 block max-w-56 truncate text-xs text-muted-foreground" title={account.deletionReason ?? undefined}>
                          {formatDateTime(account.deletedAt)} · {account.deletionReason}
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="text-sm">{formatDateTime(account.lastLoginAt)}</TableCell>
                    <RowActionsCell label={account.username}>
                      <RowActionLink icon={Eye} href={`${PORTAL_CONFIG[portal].pagePrefix}/accounts/${account.id}`}>
                        Xem chi tiết
                      </RowActionLink>
                      {availableAccountActions(account, viewer).map((kind) => (
                        <RowActionItem key={kind} icon={ACCOUNT_ACTIONS[kind].icon} destructive={ACCOUNT_ACTIONS[kind].destructive}
                          onClick={() => setPending({ account, kind })}>
                          {ACCOUNT_ACTIONS[kind].label}
                        </RowActionItem>
                      ))}
                      {canRevokeRole(account) && (
                        <RowActionItem icon={ShieldMinus} destructive onClick={() => setRevoking(account)}>Thu hồi role</RowActionItem>
                      )}
                    </RowActionsCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          {data && (
            <Pagination
              page={data.number}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              disabled={loading}
              onChange={(next) => setPage(next)}
            />
          )}
        </CardContent>
      </Card>

      {pending && (
        <AccountActionDialog
          portal={portal}
          account={pending.account}
          kind={pending.kind}
          onClose={() => setPending(null)}
          onDone={reload}
          onSessionExpired={onSessionExpired}
        />
      )}

      {revoking && (
        <RevokeRoleDialog
          portal={portal}
          accountId={revoking.id}
          username={revoking.username}
          onClose={() => setRevoking(null)}
          onRevoked={reload}
          onSessionExpired={onSessionExpired}
        />
      )}

      {viewing && (
        <AccountDetailDialog portal={portal} account={viewing} onClose={() => setViewing(null)} onSessionExpired={onSessionExpired} />
      )}
    </>
  )
}
