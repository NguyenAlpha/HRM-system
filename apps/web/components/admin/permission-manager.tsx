"use client"

import { useEffect, useState } from "react"
import { RefreshCw } from "lucide-react"

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
import {
  isSessionExpired,
  PERMISSION_MODULES,
  rbacErrorMessage,
  rbacRequest,
  type Page,
  type Permission,
} from "@/lib/rbac"

const ALL_MODULES = "ALL"

export function PermissionManager({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [data, setData] = useState<Page<Permission> | null>(null)
  const [page, setPage] = useState(0)
  const [moduleFilter, setModuleFilter] = useState("")
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    rbacRequest<Page<Permission>>(`/permissions?page=${page}&size=10&sort=id,desc${moduleFilter ? `&module=${moduleFilter}` : ""}`)
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
  }, [page, moduleFilter, revision, onSessionExpired])

  function reload() {
    setLoading(true)
    setError(null)
    setRevision((value) => value + 1)
  }

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
        <div>
          <CardTitle>Danh mục quyền hệ thống</CardTitle>
          <CardDescription>
            Permission được định nghĩa trong code; bạn chỉ có thể xem và chọn quyền để ủy quyền cho vai trò tùy chỉnh.
          </CardDescription>
        </div>
        <Button type="button" variant="outline" size="sm" onClick={reload} disabled={loading}>
          <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
        </Button>
      </CardHeader>
      <CardContent className="grid gap-4">
        <RbacFeedback error={error} />

        <Select
          value={moduleFilter || ALL_MODULES}
          disabled={loading}
          onValueChange={(value) => {
            setModuleFilter(!value || value === ALL_MODULES ? "" : value)
            setPage(0)
            setLoading(true)
            setError(null)
          }}
        >
          <SelectTrigger className="w-52">
            <SelectValue placeholder="Tất cả module" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_MODULES}>Tất cả module</SelectItem>
            {PERMISSION_MODULES.map((module) => (
              <SelectItem key={module} value={module}>{module}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <div className="rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Mã / ID</TableHead>
                <TableHead>Tên</TableHead>
                <TableHead>Module</TableHead>
                <TableHead>Chính sách gán</TableHead>
                <TableHead>Mô tả</TableHead>
                <TableHead>Trạng thái</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading &&
                Array.from({ length: 5 }).map((_, index) => (
                  <TableRow key={index}>
                    {Array.from({ length: 6 }).map((__, cell) => (
                      <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
                    ))}
                  </TableRow>
                ))}

              {!loading && data?.content.map((permission) => (
                <TableRow key={permission.id}>
                  <TableCell>
                    <code className="text-xs">{permission.code}</code>
                    <span className="block text-xs text-muted-foreground">#{permission.id}</span>
                  </TableCell>
                  <TableCell className="font-medium">{permission.name}</TableCell>
                  <TableCell><Badge variant="secondary">{permission.module}</Badge></TableCell>
                  <TableCell>
                    <Badge variant={permission.assignmentPolicy === "DELEGABLE" ? "default" : "outline"}>
                      {permission.assignmentPolicy === "DELEGABLE" ? "Có thể ủy quyền" : "Chỉ system role"}
                    </Badge>
                  </TableCell>
                  <TableCell className="max-w-xs text-sm text-muted-foreground text-wrap">{permission.description}</TableCell>
                  <TableCell>
                    <Badge variant={permission.isActive ? "default" : "outline"}>
                      {permission.isActive ? "Hoạt động" : "Tạm tắt"}
                    </Badge>
                  </TableCell>
                </TableRow>
              ))}

              {!loading && data?.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="h-24 text-center text-muted-foreground">
                    Chưa có quyền trong danh sách này.
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
  )
}
