"use client"

import { useState } from "react"
import { Ban, Check, CircleCheck, Copy, KeyRound, Link2, Loader2, MailPlus, Trash2 } from "lucide-react"
import { toast } from "sonner"

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
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import { changeAccountStatus, deleteAccount, issueAccountInvitation, type Account, type AccountInvitation } from "@/lib/account"
import type { AccountSummary, Portal } from "@/lib/auth/types"

export const ACCOUNT_STATUS_LABELS: Record<Account["status"], string> = {
  PENDING: "Chờ kích hoạt",
  ACTIVE: "Đang hoạt động",
  LOCKED: "Đang khóa",
  DISABLED: "Đã vô hiệu hóa",
}

export const ACCOUNT_STATUS_VARIANTS: Record<Account["status"], "default" | "secondary" | "destructive" | "outline"> = {
  PENDING: "secondary",
  ACTIVE: "secondary",
  LOCKED: "destructive",
  DISABLED: "outline",
}

/** The status badge, or "Đã xóa" for a soft-deleted account (which is always DISABLED). */
export function AccountStatusBadge({ account }: { account: Account }) {
  if (account.deletedAt) return <Badge variant="destructive">Đã xóa</Badge>
  return <Badge variant={ACCOUNT_STATUS_VARIANTS[account.status]}>{ACCOUNT_STATUS_LABELS[account.status]}</Badge>
}

export type AccountActionKind = "invitations/resend" | "password-reset" | "suspend" | "activate" | "delete"

export const ACCOUNT_ACTIONS: Record<AccountActionKind, {
  label: string
  title: string
  description: string
  destructive: boolean
  icon: typeof Ban
}> = {
  "invitations/resend": {
    label: "Gửi lại lời mời",
    title: "Gửi lại lời mời kích hoạt?",
    description: "Mã kích hoạt cũ chưa dùng sẽ bị thu hồi và hệ thống phát mã mới.",
    destructive: false,
    icon: MailPlus,
  },
  "password-reset": {
    label: "Đặt lại mật khẩu",
    title: "Đặt lại mật khẩu?",
    description: "Tài khoản về trạng thái chờ kích hoạt, mật khẩu cũ bị xóa và mọi phiên đăng nhập bị thu hồi. Người dùng tự đặt mật khẩu mới bằng mã kích hoạt.",
    destructive: true,
    icon: KeyRound,
  },
  suspend: {
    label: "Vô hiệu hóa",
    title: "Vô hiệu hóa tài khoản?",
    description: "Người dùng không thể đăng nhập và mọi phiên đăng nhập bị thu hồi. Có thể kích hoạt lại sau.",
    destructive: true,
    icon: Ban,
  },
  activate: {
    label: "Kích hoạt lại",
    title: "Kích hoạt lại tài khoản?",
    description: "Tài khoản được mở lại, xóa trạng thái khóa và bộ đếm đăng nhập sai.",
    destructive: false,
    icon: CircleCheck,
  },
  delete: {
    label: "Xóa tài khoản",
    title: "Xóa tài khoản?",
    description: "Tài khoản bị vô hiệu hóa vĩnh viễn, không kích hoạt lại được; các role nghiệp vụ bị thu hồi. Lịch sử thao tác vẫn được giữ, tên đăng nhập và email không dùng lại được. Nhân viên có thể được cấp tài khoản mới.",
    destructive: true,
    icon: Trash2,
  },
}

// Only a holder of the same role may change these accounts; the API's account guard enforces it.
const PROTECTED_ROLES = ["COMPANY_OWNER", "SYSTEM_ADMIN"]

/** What the signed-in account may do to other accounts. */
export type AccountViewer = {
  accountId: number
  roleCodes: string[]
  canManage: boolean
  canManageActivation: boolean
  canAssignRole: boolean
}

export function accountViewer(account: AccountSummary): AccountViewer {
  const has = (code: string) => account.permissions.some((permission) => permission.code === code)
  return {
    accountId: account.accountId,
    roleCodes: account.roles.map((role) => role.code),
    canManage: has("account.manage"),
    canManageActivation: has("account.activation.manage"),
    canAssignRole: has("account.role.assign"),
  }
}

/** True when the account holds a protected role the viewer does not, so the API would refuse to change it. */
export function isGuardedAccount(account: Account, viewer: AccountViewer) {
  return PROTECTED_ROLES.some((role) => account.roleCodes.includes(role) && !viewer.roleCodes.includes(role))
}

// Mirrors the API's status rules; the API still rejects anything not allowed.
export function availableAccountActions(account: Account, viewer: AccountViewer): AccountActionKind[] {
  const self = account.id === viewer.accountId
  const actions: AccountActionKind[] = []
  if (account.deletedAt) return actions
  // Reactivating stays allowed on a protected account, so only the other actions are dropped.
  const guarded = isGuardedAccount(account, viewer)
  if (viewer.canManageActivation && !guarded && account.status === "PENDING") actions.push("invitations/resend")
  if (viewer.canManageActivation && !guarded && !self && (account.status === "ACTIVE" || account.status === "LOCKED")) actions.push("password-reset")
  if (viewer.canManage && (account.status === "DISABLED" || account.status === "LOCKED")) actions.push("activate")
  if (viewer.canManage && !guarded && !self && (account.status === "ACTIVE" || account.status === "LOCKED")) actions.push("suspend")
  if (viewer.canManage && !guarded && !self) actions.push("delete")
  return actions
}

export function formatDateTime(value: string | null) {
  return value ? new Date(value).toLocaleString("vi-VN") : "—"
}

/** Confirms one lifecycle action; a resend or password reset then shows the new one-time activation token. */
export function AccountActionDialog({ portal, account, kind, onClose, onDone, onSessionExpired }: {
  portal: Portal
  account: Account
  kind: AccountActionKind
  onClose: () => void
  onDone: () => void
  onSessionExpired: () => void
}) {
  const [running, setRunning] = useState(false)
  const [invitation, setInvitation] = useState<AccountInvitation | null>(null)
  const [reason, setReason] = useState("")
  const action = ACCOUNT_ACTIONS[kind]

  async function confirm() {
    setRunning(true)
    try {
      if (kind === "delete") {
        await deleteAccount(portal, account.id, reason.trim())
        toast.success(`Đã xóa tài khoản ${account.username}`)
        onClose()
      } else if (kind === "invitations/resend" || kind === "password-reset") {
        setInvitation(await issueAccountInvitation(portal, account.id, kind))
      } else {
        await changeAccountStatus(portal, account.id, kind)
        toast.success(`${action.label}: ${account.username}`)
        onClose()
      }
      onDone()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      toast.error(apiErrorMessage(caught))
    } finally {
      setRunning(false)
    }
  }

  if (invitation) return <InvitationDialog username={account.username} invitation={invitation} onClose={onClose} />

  return (
    <AlertDialog open onOpenChange={(open) => { if (!open && !running) onClose() }}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{action.title}</AlertDialogTitle>
          <AlertDialogDescription>
            Tài khoản <strong>{account.username}</strong>. {action.description}
          </AlertDialogDescription>
        </AlertDialogHeader>
        {kind === "delete" && (
          <div className="grid gap-1.5">
            <Label htmlFor="account-delete-reason">Lý do xóa</Label>
            <Textarea id="account-delete-reason" maxLength={100} value={reason} disabled={running}
              placeholder="Ví dụ: tạo nhầm, gõ sai email" onChange={(event) => setReason(event.target.value)} />
          </div>
        )}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={running}>Hủy</AlertDialogCancel>
          <AlertDialogAction
            className={action.destructive ? "bg-destructive text-white hover:bg-destructive/90" : undefined}
            disabled={running || (kind === "delete" && !reason.trim())}
            onClick={(event) => { event.preventDefault(); void confirm() }}
          >
            {running && <Loader2 className="animate-spin" />}
            {action.label}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}

function InvitationDialog({ username, invitation, onClose }: {
  username: string
  invitation: AccountInvitation
  onClose: () => void
}) {
  const [copied, setCopied] = useState<"link" | "token" | null>(null)
  const link = `/activate/${invitation.activationToken}`

  async function copy(kind: "link" | "token") {
    try {
      await navigator.clipboard.writeText(kind === "link" ? `${window.location.origin}${link}` : invitation.activationToken)
      setCopied(kind)
      toast.success(kind === "link" ? "Đã sao chép link kích hoạt" : "Đã sao chép mã kích hoạt")
      setTimeout(() => setCopied(null), 1500)
    } catch {
      toast.error("Không thể sao chép, hãy tự copy thủ công")
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Mã kích hoạt mới cho {username}</DialogTitle>
          <DialogDescription>
            Gửi link hoặc mã bên dưới cho người dùng để họ tự đặt mật khẩu. Mã chỉ hiển thị một lần duy nhất.
          </DialogDescription>
        </DialogHeader>

        <div className="mt-4 grid min-w-0 gap-3">
          <div className="flex min-w-0 items-start gap-2 rounded-lg bg-muted px-3 py-2.5 text-sm">
            <Link2 className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
            <div className="min-w-0 flex-1">
              <p className="text-xs text-muted-foreground">Link kích hoạt — gửi trực tiếp cho người dùng</p>
              <code className="mt-0.5 block truncate font-semibold">{link}</code>
            </div>
            <Button type="button" variant="ghost" size="icon-sm" onClick={() => void copy("link")} aria-label="Sao chép link kích hoạt">
              {copied === "link" ? <Check /> : <Copy />}
            </Button>
          </div>
          <div className="flex min-w-0 items-start gap-2 rounded-lg bg-muted px-3 py-2.5 text-sm">
            <KeyRound className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
            <div className="min-w-0 flex-1">
              <p className="text-xs text-muted-foreground">Mã kích hoạt (dùng một lần)</p>
              <code className="mt-0.5 block truncate font-semibold">{invitation.activationToken}</code>
            </div>
            <Button type="button" variant="ghost" size="icon-sm" onClick={() => void copy("token")} aria-label="Sao chép mã kích hoạt">
              {copied === "token" ? <Check /> : <Copy />}
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">Hết hạn lúc {formatDateTime(invitation.expiresAt)}.</p>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>Đóng</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
