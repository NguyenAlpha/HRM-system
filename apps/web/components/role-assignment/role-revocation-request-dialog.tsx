"use client"

import { useEffect, useState } from "react"
import { Loader2, ShieldMinus } from "lucide-react"
import { toast } from "sonner"

import { scopeLabel, validityLabel } from "@/components/account/account-access"
import { ErrorAlert } from "@/components/common/list-controls"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { employeeRequest, type EmployeeSummary } from "@/lib/employee"
import type { Page } from "@/lib/pagination"
import {
  createRoleRevocationRequest,
  listRevocableAssignments,
  type AccountRoleAssignment,
} from "@/lib/role-assignment"

const REASON_MAX = 100

/** HR picks an employee and one of their roles, then asks for it to be taken back. */
export function RoleRevocationRequestDialog({ onClose, onCreated, onSessionExpired }: {
  onClose: () => void
  onCreated: () => void
  onSessionExpired: () => void
}) {
  const [employees, setEmployees] = useState<EmployeeSummary[] | null>(null)
  const [accountId, setAccountId] = useState("")
  const [assignments, setAssignments] = useState<AccountRoleAssignment[] | null>(null)
  const [assignmentId, setAssignmentId] = useState("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [running, setRunning] = useState(false)

  useEffect(() => {
    let active = true
    employeeRequest<Page<EmployeeSummary>>("hrm", "?page=0&size=100")
      .then((page) => { if (active) setEmployees(page.content.filter((employee) => employee.account !== null)) })
      .catch((caught: unknown) => {
        if (!active) return
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setError(apiErrorMessage(caught))
      })
    return () => { active = false }
  }, [onSessionExpired])

  function chooseAccount(next: string) {
    setAccountId(next)
    setAssignmentId("")
    setAssignments(null)
    setError(null)
    listRevocableAssignments(Number(next))
      .then((loaded) => {
        setAssignments(loaded)
        if (loaded.length === 1) setAssignmentId(String(loaded[0].id))
      })
      .catch((caught: unknown) => {
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setError(apiErrorMessage(caught))
      })
  }

  const employeeItems = Object.fromEntries((employees ?? []).map((employee) =>
    [String(employee.account!.id), `${employee.fullName} (${employee.account!.username})`]))
  const assignmentItems = Object.fromEntries((assignments ?? []).map((assignment) =>
    [String(assignment.id), `${assignment.roleName} · ${scopeLabel(assignment)}`]))
  const selected = assignments?.find((assignment) => String(assignment.id) === assignmentId) ?? null

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!selected) return
    setRunning(true)
    setError(null)
    try {
      const created = await createRoleRevocationRequest(selected.id, reason.trim())
      toast.success(created.status === "PENDING"
        ? `Đã gửi đề xuất thu hồi role ${created.roleName}, đang chờ Company Owner duyệt`
        : `Đã thu hồi role ${created.roleName} của ${created.employeeName ?? `account #${created.accountId}`}`)
      onCreated()
      onClose()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setError(apiErrorMessage(caught))
    } finally {
      setRunning(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !running) onClose() }}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={(event) => void submit(event)}>
          <DialogHeader>
            <DialogTitle>Đề xuất thu hồi role</DialogTitle>
            <DialogDescription>
              Role cần duyệt chỉ bị thu hồi khi Company Owner phê duyệt; role tự động duyệt bị thu hồi ngay.
              Nhân sự nghỉ việc thì không cần đề xuất: kết thúc làm việc đã tự thu hồi role.
            </DialogDescription>
          </DialogHeader>

          <div className="mt-4 grid gap-4">
            <ErrorAlert error={error} />

            <div className="grid gap-1.5">
              <Label htmlFor="revoke-req-account">Nhân sự (đã có tài khoản)</Label>
              <Select value={accountId} items={employeeItems} onValueChange={(value) => chooseAccount(String(value ?? ""))}
                disabled={employees === null}>
                <SelectTrigger id="revoke-req-account" className="w-full">
                  <SelectValue placeholder={employees === null ? "Đang tải..." : "Chọn nhân sự..."} />
                </SelectTrigger>
                <SelectContent>
                  {(employees ?? []).map((employee) => (
                    <SelectItem key={employee.account!.id} value={String(employee.account!.id)}>
                      {employeeItems[String(employee.account!.id)]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {accountId && (
              <div className="grid gap-1.5">
                <Label htmlFor="revoke-req-assignment">Role cần thu hồi</Label>
                {assignments?.length === 0 ? (
                  <p className="text-sm text-muted-foreground">Nhân sự không có role nào có thể thu hồi.</p>
                ) : (
                  <Select value={assignmentId} items={assignmentItems} onValueChange={(value) => setAssignmentId(String(value ?? ""))}
                    disabled={assignments === null}>
                    <SelectTrigger id="revoke-req-assignment" className="w-full">
                      <SelectValue placeholder={assignments === null ? "Đang tải..." : "Chọn role..."} />
                    </SelectTrigger>
                    <SelectContent>
                      {(assignments ?? []).map((assignment) => (
                        <SelectItem key={assignment.id} value={String(assignment.id)}>
                          {assignmentItems[String(assignment.id)]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
                {selected && <p className="text-xs text-muted-foreground">Hiệu lực {validityLabel(selected)}</p>}
              </div>
            )}

            <div className="grid gap-1.5">
              <Label htmlFor="revoke-req-reason">Lý do thu hồi</Label>
              <Textarea id="revoke-req-reason" rows={2} required maxLength={REASON_MAX} value={reason}
                onChange={(event) => setReason(event.target.value)} placeholder="VD: Thôi kiêm nhiệm từ tháng 11" />
            </div>
          </div>

          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" disabled={running} onClick={onClose}>Hủy</Button>
            <Button type="submit" variant="destructive" disabled={running || !selected || !reason.trim()}>
              {running ? <Loader2 className="animate-spin" /> : <ShieldMinus />} Gửi đề xuất
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
