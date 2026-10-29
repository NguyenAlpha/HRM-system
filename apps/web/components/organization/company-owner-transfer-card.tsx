"use client"

import { useRouter } from "next/navigation"
import { FormEvent, useEffect, useMemo, useState } from "react"
import { Loader2, UserRoundCog } from "lucide-react"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
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
import { Textarea } from "@/components/ui/textarea"
import { SectionError } from "@/components/workforce/workforce-shared"
import type { components } from "@/lib/api-schema"
import { apiErrorMessage } from "@/lib/api-helpers"
import { authorizedRequest, logout } from "@/lib/auth/client"
import { PORTAL_CONFIG } from "@/lib/auth/config"
import { AuthApiError } from "@/lib/auth/types"
import { employeeRequest, type EmployeeSummary } from "@/lib/employee"
import type { Page } from "@/lib/pagination"

type TransferInput = components["schemas"]["TransferCompanyOwnerRequest"]

/**
 * The Company Owner hands ownership to another working employee. It takes effect at once and ends
 * this session, so the owner signs in again without the role.
 */
export function CompanyOwnerTransferCard({ ownAccountId }: { ownAccountId: number }) {
  const router = useRouter()
  const [employees, setEmployees] = useState<EmployeeSummary[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [employeeId, setEmployeeId] = useState("")
  const [reason, setReason] = useState("")
  const [confirming, setConfirming] = useState(false)

  useEffect(() => {
    let active = true
    employeeRequest<Page<EmployeeSummary>>("hrm", "?page=0&size=100")
      .then((page) => { if (active) setEmployees(page.content) })
      .catch((caught: unknown) => { if (active) setLoadError(apiErrorMessage(caught)) })
    return () => { active = false }
  }, [])

  // The API accepts only an active account of an employee still working and not leaving.
  const candidates = useMemo(
    () => (employees ?? []).filter((employee) => employee.account?.status === "ACTIVE"
      && employee.account.id !== ownAccountId
      && (employee.employmentStatus === "ACTIVE" || employee.employmentStatus === "PROBATION")
      && employee.terminationDate === null),
    [employees, ownAccountId],
  )
  const items = useMemo(
    () => Object.fromEntries(candidates.map((employee) => [
      String(employee.id), `${employee.fullName} (${employee.account?.username})`,
    ])),
    [candidates],
  )
  const recipient = candidates.find((employee) => String(employee.id) === employeeId) ?? null

  function startConfirming(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (recipient && reason.trim()) setConfirming(true)
  }

  async function transferred() {
    toast.success(`Đã chuyển quyền Company Owner cho ${recipient?.fullName}. Vui lòng đăng nhập lại.`)
    try {
      await logout("hrm")
    } finally {
      router.replace(PORTAL_CONFIG.hrm.loginPath)
      router.refresh()
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Chuyển giao Company Owner</CardTitle>
        <CardDescription>
          Trao quyền chủ sở hữu doanh nghiệp cho một nhân sự đang làm việc. Có hiệu lực ngay; bạn mất quyền Company
          Owner nhưng giữ các vai trò khác (ví dụ Giám đốc).
        </CardDescription>
      </CardHeader>
      <CardContent>
        <SectionError error={loadError} />
        <form onSubmit={startConfirming} className="grid gap-4">
          <div className="grid gap-1.5">
            <Label htmlFor="owner-transfer-employee">Người nhận</Label>
            <Select value={employeeId} items={items} onValueChange={(value) => setEmployeeId(value ?? "")} disabled={!employees}>
              <SelectTrigger id="owner-transfer-employee" className="w-full">
                <SelectValue placeholder={employees ? "Chọn nhân sự..." : "Đang tải..."} />
              </SelectTrigger>
              <SelectContent>
                {candidates.map((employee) => (
                  <SelectItem key={employee.id} value={String(employee.id)}>
                    {employee.fullName} ({employee.account?.username})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground">Chỉ hiện nhân sự đang làm việc có tài khoản đã kích hoạt.</p>
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="owner-transfer-reason">Lý do</Label>
            <Textarea id="owner-transfer-reason" rows={2} maxLength={100} value={reason}
              onChange={(event) => setReason(event.target.value)} />
          </div>
          <div>
            <Button type="submit" variant="destructive" disabled={!recipient || !reason.trim()}>
              <UserRoundCog /> Chuyển giao...
            </Button>
          </div>
        </form>
      </CardContent>

      {confirming && recipient?.account && (
        <ConfirmTransferDialog
          recipient={recipient}
          accountId={recipient.account.id}
          reason={reason.trim()}
          onClose={() => setConfirming(false)}
          onTransferred={transferred}
        />
      )}
    </Card>
  )
}

/** Confirms with the owner's own password, since the owner cannot take the role back. */
function ConfirmTransferDialog({ recipient, accountId, reason, onClose, onTransferred }: {
  recipient: EmployeeSummary
  accountId: number
  reason: string
  onClose: () => void
  onTransferred: () => void
}) {
  const [password, setPassword] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await authorizedRequest("hrm", "/company-owner/transfer", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ accountId, password, reason } satisfies TransferInput),
      })
      onTransferred()
    } catch (caught) {
      // The shared message for INVALID_CREDENTIALS speaks of a username, which this form does not have.
      setError(caught instanceof AuthApiError && caught.code === "INVALID_CREDENTIALS"
        ? "Mật khẩu không đúng."
        : apiErrorMessage(caught))
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={submit}>
          <DialogHeader>
            <DialogTitle>Xác nhận chuyển giao</DialogTitle>
            <DialogDescription>
              {recipient.fullName} sẽ trở thành Company Owner ngay. Bạn không tự lấy lại được quyền này; phiên đăng
              nhập hiện tại sẽ kết thúc.
            </DialogDescription>
          </DialogHeader>
          {error && <div className="mt-4"><SectionError error={error} /></div>}
          <div className="mt-4 grid gap-1.5">
            <Label htmlFor="owner-transfer-password">Mật khẩu của bạn</Label>
            <Input id="owner-transfer-password" type="password" autoComplete="current-password" required maxLength={100}
              value={password} onChange={(event) => setPassword(event.target.value)} />
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Hủy</Button>
            <Button type="submit" variant="destructive" disabled={busy || !password}>
              {busy && <Loader2 className="animate-spin" />} Chuyển giao
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
