"use client"

import { useEffect, useState } from "react"
import { Loader2, ShieldMinus } from "lucide-react"
import { toast } from "sonner"

import { isRevocable, scopeLabel, todayIso, validityLabel } from "@/components/account/account-access"
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
import { Skeleton } from "@/components/ui/skeleton"
import { Textarea } from "@/components/ui/textarea"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import type { Portal } from "@/lib/auth/types"
import { listAccountRoleAssignments, revokeAccountRole, type AccountRoleAssignment } from "@/lib/role-assignment"

const REASON_MAX = 100

/**
 * Revokes one role assignment of an account. Given an assignment it revokes that one; otherwise it
 * loads the account's revocable assignments and lets the user pick.
 */
export function RevokeRoleDialog({ portal, accountId, username, assignment, onClose, onRevoked, onSessionExpired }: {
  portal: Portal
  accountId: number
  username: string
  assignment?: AccountRoleAssignment
  onClose: () => void
  onRevoked: () => void
  onSessionExpired: () => void
}) {
  const [options, setOptions] = useState<AccountRoleAssignment[] | null>(assignment ? [assignment] : null)
  const [selectedId, setSelectedId] = useState(assignment ? String(assignment.id) : "")
  const [reason, setReason] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [running, setRunning] = useState(false)

  useEffect(() => {
    if (assignment) return
    let active = true
    listAccountRoleAssignments(portal, accountId)
      .then((loaded) => {
        if (!active) return
        const today = todayIso()
        const revocable = loaded.filter((item) => isRevocable(item, today))
        setOptions(revocable)
        if (revocable.length === 1) setSelectedId(String(revocable[0].id))
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setError(apiErrorMessage(caught))
      })
    return () => { active = false }
  }, [portal, accountId, assignment, onSessionExpired])

  const selected = options?.find((item) => String(item.id) === selectedId) ?? null
  const items = Object.fromEntries((options ?? []).map((item) => [String(item.id), `${item.roleName} · ${scopeLabel(item)}`]))

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!selected) return
    setRunning(true)
    setError(null)
    try {
      await revokeAccountRole(portal, accountId, selected.id, reason.trim())
      toast.success(`Đã thu hồi role ${selected.roleName} của ${username}`)
      onRevoked()
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
            <DialogTitle>Thu hồi role của {username}</DialogTitle>
            <DialogDescription>
              Role mất hiệu lực ngay, lịch sử gán vẫn được giữ. Mọi phiên đăng nhập của tài khoản bị thu hồi;
              access token đã cấp còn hiệu lực tối đa 15 phút.
            </DialogDescription>
          </DialogHeader>

          <div className="mt-4 grid gap-4">
            <ErrorAlert error={error} />

            <div className="grid gap-1.5">
              <Label htmlFor="revoke-role-assignment">Role</Label>
              {options === null && !error && <Skeleton className="h-9 w-full" />}
              {options?.length === 0 && (
                <p className="text-sm text-muted-foreground">Tài khoản không có role nào có thể thu hồi.</p>
              )}
              {options && options.length > 0 && (
                <Select value={selectedId} items={items} onValueChange={(value) => setSelectedId(String(value ?? ""))}
                  disabled={Boolean(assignment)}>
                  <SelectTrigger id="revoke-role-assignment" className="w-full">
                    <SelectValue placeholder="Chọn role cần thu hồi" />
                  </SelectTrigger>
                  <SelectContent>
                    {options.map((item) => (
                      <SelectItem key={item.id} value={String(item.id)}>{items[String(item.id)]}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
              {selected && <p className="text-xs text-muted-foreground">Hiệu lực {validityLabel(selected)}</p>}
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="revoke-role-reason">Lý do thu hồi</Label>
              <Textarea id="revoke-role-reason" required maxLength={REASON_MAX} value={reason}
                onChange={(event) => setReason(event.target.value)} placeholder="VD: Chuyển công tác, không còn phụ trách nhân sự" />
              <p className="text-xs text-muted-foreground">{reason.length}/{REASON_MAX} ký tự</p>
            </div>
          </div>

          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" disabled={running} onClick={onClose}>Hủy</Button>
            <Button type="submit" variant="destructive" disabled={running || !selected || !reason.trim()}>
              {running ? <Loader2 className="animate-spin" /> : <ShieldMinus />} Thu hồi role
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
