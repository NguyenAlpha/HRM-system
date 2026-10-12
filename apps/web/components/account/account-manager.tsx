"use client"

import Link from "next/link"
import { useEffect, useState } from "react"
import { Ban, Check, CircleCheck, Copy, KeyRound, Link2, Loader2, MailPlus, RefreshCw } from "lucide-react"
import { toast } from "sonner"

import { Pagination, ErrorAlert } from "@/components/common/list-controls"
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
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import {
  changeAccountStatus,
  issueAccountInvitation,
  listAccounts,
  type Account,
  type AccountInvitation,
  type AccountPage,
} from "@/lib/account"
import { PORTAL_CONFIG } from "@/lib/auth/config"
import type { Portal } from "@/lib/auth/types"

const STATUS_LABELS: Record<Account["status"], string> = {
  PENDING: "Chờ kích hoạt",
  ACTIVE: "Đang hoạt động",
  LOCKED: "Đang khóa",
  DISABLED: "Đã vô hiệu hóa",
}

const STATUS_VARIANTS: Record<Account["status"], "default" | "secondary" | "destructive" | "outline"> = {
  PENDING: "secondary",
  ACTIVE: "default",
  LOCKED: "destructive",
  DISABLED: "outline",
}

type ActionKind = "invitations/resend" | "password-reset" | "suspend" | "activate"

const ACTIONS: Record<ActionKind, { label: string; title: string; description: string; destructive: boolean }> = {
  "invitations/resend": {
    label: "Gửi lại lời mời",
    title: "Gửi lại lời mời kích hoạt?",
    description: "Mã kích hoạt cũ chưa dùng sẽ bị thu hồi và hệ thống phát mã mới.",
    destructive: false,
  },
  "password-reset": {
    label: "Đặt lại mật khẩu",
    title: "Đặt lại mật khẩu?",
    description: "Tài khoản về trạng thái chờ kích hoạt, mật khẩu cũ bị xóa và mọi phiên đăng nhập bị thu hồi. Người dùng tự đặt mật khẩu mới bằng mã kích hoạt.",
    destructive: true,
  },
  suspend: {
    label: "Vô hiệu hóa",
    title: "Vô hiệu hóa tài khoản?",
    description: "Người dùng không thể đăng nhập và mọi phiên đăng nhập bị thu hồi. Có thể kích hoạt lại sau.",
    destructive: true,
  },
  activate: {
    label: "Kích hoạt lại",
    title: "Kích hoạt lại tài khoản?",
    description: "Tài khoản được mở lại, xóa trạng thái khóa và bộ đếm đăng nhập sai.",
    destructive: false,
  },
}

const ICONS: Record<ActionKind, typeof Ban> = {
  "invitations/resend": MailPlus,
  "password-reset": KeyRound,
  suspend: Ban,
  activate: CircleCheck,
}

function formatDateTime(value: string | null) {
  return value ? new Date(value).toLocaleString("vi-VN") : "—"
}

export function AccountManager({ portal, currentAccountId, canManage, canManageActivation, onSessionExpired }: {
  portal: Portal
  currentAccountId: number
  canManage: boolean
  canManageActivation: boolean
  onSessionExpired: () => void
}) {
  const [data, setData] = useState<AccountPage | null>(null)
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState<{ account: Account; kind: ActionKind } | null>(null)
  const [running, setRunning] = useState(false)
  // The Admin Console only reads accounts: SYSTEM_ADMIN holds neither lifecycle permission.
  const hasActions = canManage || canManageActivation
  const columns = hasActions ? 5 : 4
  const [invitation, setInvitation] = useState<{ account: Account; invitation: AccountInvitation } | null>(null)

  useEffect(() => {
    let active = true
    listAccounts(portal, page)
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
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setError(apiErrorMessage(caught))
        setLoading(false)
      })
    return () => { active = false }
  }, [portal, page, revision, onSessionExpired])

  function reload() {
    setLoading(true)
    setError(null)
    setRevision((value) => value + 1)
  }

  // Mirrors the API's status rules; the API still rejects anything not allowed.
  function availableActions(account: Account): ActionKind[] {
    const self = account.id === currentAccountId
    const actions: ActionKind[] = []
    if (canManageActivation && account.status === "PENDING") actions.push("invitations/resend")
    if (canManageActivation && !self && (account.status === "ACTIVE" || account.status === "LOCKED")) actions.push("password-reset")
    if (canManage && (account.status === "DISABLED" || account.status === "LOCKED")) actions.push("activate")
    if (canManage && !self && (account.status === "ACTIVE" || account.status === "LOCKED")) actions.push("suspend")
    return actions
  }

  async function confirmAction() {
    if (!pending) return
    const { account, kind } = pending
    setRunning(true)
    try {
      if (kind === "invitations/resend" || kind === "password-reset") {
        const issued = await issueAccountInvitation(account.id, kind)
        setInvitation({ account, invitation: issued })
      } else {
        await changeAccountStatus(account.id, kind)
        toast.success(`${ACTIONS[kind].label}: ${account.username}`)
      }
      setPending(null)
      reload()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      toast.error(apiErrorMessage(caught))
    } finally {
      setRunning(false)
    }
  }

  return (
    <>
      <Card>
        <CardHeader className="flex flex-row flex-wrap items-start justify-between gap-3">
          <div>
            <CardTitle>Danh sách tài khoản</CardTitle>
            <CardDescription>
              {hasActions
                ? "Gửi lại lời mời, đặt lại mật khẩu, vô hiệu hóa hoặc kích hoạt lại tài khoản đăng nhập."
                : "Danh sách tài khoản đăng nhập (chỉ xem)."}
            </CardDescription>
          </div>
          <Button type="button" variant="outline" size="sm" onClick={reload} disabled={loading}>
            <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
          </Button>
        </CardHeader>
        <CardContent className="grid gap-4">
          <ErrorAlert error={error} />

          <div className="rounded-xl border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Tài khoản</TableHead>
                  <TableHead>Nhân viên</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead>Đăng nhập gần nhất</TableHead>
                  {hasActions && <TableHead className="text-right">Thao tác</TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {loading &&
                  Array.from({ length: 5 }).map((_, index) => (
                    <TableRow key={index}>
                      {Array.from({ length: columns }).map((__, cell) => (
                        <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
                      ))}
                    </TableRow>
                  ))}

                {!loading && data?.content.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={columns} className="py-8 text-center text-muted-foreground">Chưa có tài khoản nào.</TableCell>
                  </TableRow>
                )}

                {!loading && data?.content.map((account) => (
                  <TableRow key={account.id}>
                    <TableCell>
                      <span className="font-medium">{account.username}</span>
                      {account.id === currentAccountId && <span className="ml-1.5 text-xs text-muted-foreground">(bạn)</span>}
                      <span className="block text-xs text-muted-foreground">{account.email}</span>
                    </TableCell>
                    <TableCell>
                      {account.employeeId === null
                        ? <span className="text-muted-foreground">—</span>
                        : <Link className="text-sm underline-offset-4 hover:underline" href={`${PORTAL_CONFIG[portal].pagePrefix}/employees/${account.employeeId}`}>Hồ sơ #{account.employeeId}</Link>}
                    </TableCell>
                    <TableCell>
                      <Badge variant={STATUS_VARIANTS[account.status]}>{STATUS_LABELS[account.status]}</Badge>
                      {account.lockedUntil && (
                        <span className="mt-1 block text-xs text-muted-foreground">Khóa đến {formatDateTime(account.lockedUntil)}</span>
                      )}
                    </TableCell>
                    <TableCell className="text-sm">{formatDateTime(account.lastLoginAt)}</TableCell>
                    {hasActions && <TableCell>
                      <div className="flex justify-end gap-1">
                        {availableActions(account).map((kind) => {
                          const Icon = ICONS[kind]
                          return (
                            <Tooltip key={kind}>
                              <TooltipTrigger
                                render={
                                  <Button
                                    type="button"
                                    variant="ghost"
                                    size="icon-sm"
                                    className={ACTIONS[kind].destructive ? "text-muted-foreground hover:text-destructive" : undefined}
                                    onClick={() => setPending({ account, kind })}
                                    aria-label={`${ACTIONS[kind].label} ${account.username}`}
                                  />
                                }
                              >
                                <Icon />
                              </TooltipTrigger>
                              <TooltipContent>{ACTIONS[kind].label}</TooltipContent>
                            </Tooltip>
                          )
                        })}
                      </div>
                    </TableCell>}
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
              onChange={(next) => { setLoading(true); setPage(next) }}
            />
          )}
        </CardContent>
      </Card>

      <AlertDialog open={pending !== null} onOpenChange={(open) => { if (!open && !running) setPending(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{pending && ACTIONS[pending.kind].title}</AlertDialogTitle>
            <AlertDialogDescription>
              {pending && <>Tài khoản <strong>{pending.account.username}</strong>. {ACTIONS[pending.kind].description}</>}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={running}>Hủy</AlertDialogCancel>
            <AlertDialogAction
              className={pending && ACTIONS[pending.kind].destructive ? "bg-destructive text-white hover:bg-destructive/90" : undefined}
              disabled={running}
              onClick={(event) => { event.preventDefault(); void confirmAction() }}
            >
              {running && <Loader2 className="animate-spin" />}
              {pending && ACTIONS[pending.kind].label}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {invitation && (
        <InvitationDialog
          username={invitation.account.username}
          invitation={invitation.invitation}
          onClose={() => setInvitation(null)}
        />
      )}
    </>
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
