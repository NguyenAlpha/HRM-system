"use client"

import { useState } from "react"
import { Check, Copy, KeyRound, Link2 } from "lucide-react"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"
import type { AccountInvitation } from "@/lib/account"
import { formatDateTime } from "@/lib/format-date"
import { cn } from "@/lib/utils"

/**
 * The one-time activation link and token of a new or reset account, each with a copy button, then when
 * they expire. `recipient` is who the link goes to; `note` follows the expiry.
 */
export function ActivationInvitation({ invitation, recipient, note, className }: {
  invitation: Pick<AccountInvitation, "activationToken" | "expiresAt">
  recipient: string
  note?: string
  className?: string
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
    <div className={cn("grid min-w-0 gap-3", className)}>
      <div className="flex min-w-0 items-start gap-2 rounded-lg bg-muted px-3 py-2.5 text-sm">
        <Link2 className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
        <div className="min-w-0 flex-1">
          <p className="text-xs text-muted-foreground">Link kích hoạt — gửi trực tiếp cho {recipient}</p>
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
      <p className="text-xs text-muted-foreground">
        Hết hạn lúc {formatDateTime(invitation.expiresAt)}.{note && ` ${note}`}
      </p>
    </div>
  )
}
