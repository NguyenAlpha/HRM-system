"use client"

import { useEffect, useState } from "react"

import { Pagination, ErrorAlert } from "@/components/common/list-controls"
import { ALL_OPTION, FilterSelect, ListToolbar, useDebouncedValue } from "@/components/common/list-filters"
import { Badge } from "@/components/ui/badge"
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
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import type { Portal } from "@/lib/auth/types"
import type { Page } from "@/lib/pagination"
import {
  COMPANY_WIDE_PERMISSION_HINT,
  PERMISSION_MODULES,
  rbacRequest,
  type Permission,
} from "@/lib/rbac"

/** What one request asked for, so a reply that no longer matches the filters does not count as loaded. */
function requestKey(page: number, moduleFilter: string, keyword: string) {
  return JSON.stringify([page, moduleFilter, keyword])
}

export function PermissionManager({ portal, onSessionExpired }: { portal: Portal; onSessionExpired: () => void }) {
  const [page, setPage] = useState(0)
  const [moduleFilter, setModuleFilter] = useState("")
  const [keywordInput, setKeywordInput] = useState("")
  // The keyword goes through its debounced copy so typing does not fire a request per key.
  const keyword = useDebouncedValue(keywordInput.trim())
  const [result, setResult] = useState<{ key: string; data: Page<Permission> | null; error: string | null } | null>(null)

  const key = requestKey(page, moduleFilter, keyword)
  const loading = result?.key !== key || keyword !== keywordInput.trim()
  const data = result?.data ?? null
  const error = result?.key === key ? result.error : null

  useEffect(() => {
    let active = true
    const query = new URLSearchParams({ page: String(page), size: "20", sort: "id,desc" })
    if (moduleFilter) query.set("module", moduleFilter)
    if (keyword) query.set("keyword", keyword)
    rbacRequest<Page<Permission>>(portal, `/permissions?${query}`)
      .then((fetched) => {
        if (!active) return
        if (page > 0 && page >= fetched.totalPages) {
          setPage(Math.max(0, fetched.totalPages - 1))
          return
        }
        setResult({ key, data: fetched, error: null })
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setResult((previous) => ({ key, data: previous?.data ?? null, error: apiErrorMessage(caught) }))
      })
    return () => { active = false }
  }, [portal, page, moduleFilter, keyword, key, onSessionExpired])

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
        <div>
          <CardTitle>Danh mục quyền hệ thống</CardTitle>
          <CardDescription>
            Permission được định nghĩa trong code; bạn chỉ có thể xem và chọn quyền để ủy quyền cho vai trò tùy chỉnh.
          </CardDescription>
        </div>
      </CardHeader>
      <CardContent className="grid grid-cols-1 gap-4">
        <ErrorAlert error={error} />

        <ListToolbar
          keyword={keywordInput}
          onKeywordChange={(value) => { setKeywordInput(value); setPage(0) }}
          placeholder="Tìm theo mã, tên hoặc mô tả quyền..."
          activeFilters={moduleFilter ? 1 : 0}
          onReset={() => { setKeywordInput(""); setModuleFilter(""); setPage(0) }}
        >
          <FilterSelect
            id="permission-module"
            label="Module"
            value={moduleFilter || ALL_OPTION}
            onChange={(value) => { setModuleFilter(value === ALL_OPTION ? "" : value); setPage(0) }}
            options={PERMISSION_MODULES.map((module) => ({ value: module, label: module }))}
            allLabel="Tất cả module"
          />
        </ListToolbar>

        <div className="rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Mã / ID</TableHead>
                <TableHead>Tên</TableHead>
                <TableHead>Module</TableHead>
                <TableHead>Chính sách gán</TableHead>
                <TableHead>Mô tả</TableHead>
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

              {!loading && data?.content.map((permission) => (
                <TableRow key={permission.id}>
                  <TableCell>
                    <code className="block text-xs md:max-w-36 md:truncate" title={permission.code}>{permission.code}</code>
                    <span className="block text-xs text-muted-foreground">#{permission.id}</span>
                  </TableCell>
                  <TableCell className="font-medium">
                    <span className="block">{permission.name}</span>
                    {permission.companyWide && (
                      <Badge variant="outline" className="mt-1" title={COMPANY_WIDE_PERMISSION_HINT}>Toàn công ty</Badge>
                    )}
                  </TableCell>
                  <TableCell><Badge variant="secondary">{permission.module}</Badge></TableCell>
                  <TableCell>
                    <Badge variant={permission.assignmentPolicy === "DELEGABLE" ? "secondary" : "outline"}>
                      {permission.assignmentPolicy === "DELEGABLE" ? "Có thể ủy quyền" : "Chỉ system role"}
                    </Badge>
                  </TableCell>
                  <TableCell className="max-w-xs min-w-56 text-sm text-muted-foreground whitespace-normal break-words">{permission.description}</TableCell>
                </TableRow>
              ))}

              {!loading && data?.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="h-24 text-center text-muted-foreground">
                    {keyword || moduleFilter ? "Không tìm thấy quyền phù hợp." : "Chưa có quyền trong danh sách này."}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>

        <Pagination page={page} totalPages={data?.totalPages ?? 0} totalElements={data?.totalElements ?? 0} disabled={loading}
          onChange={setPage} />
      </CardContent>
    </Card>
  )
}
