"use client"

import { useState } from "react"
import { Loader2 } from "lucide-react"
import { toast } from "sonner"

import { ErrorAlert } from "@/components/common/list-controls"
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
import { Button } from "@/components/ui/button"
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import {
  formatQuota,
  POLICY_LABELS,
  restoreRateLimitPolicy,
  updateRateLimitPolicy,
  type RateLimitPolicyStats,
  type RateLimitStats,
} from "@/lib/rate-limit-stats"

// Same bounds as the API's RateLimitPolicyRequest.
const MAX_REQUESTS = 100_000
const MAX_WINDOW_SECONDS = 86_400

const UNITS = { 1: "giây", 60: "phút", 3600: "giờ" } as const
type Unit = keyof typeof UNITS
const UNIT_ITEMS = Object.fromEntries(Object.entries(UNITS))

/** The largest unit the window is a whole number of: 900 → 15 phút. */
function splitWindow(seconds: number): { amount: number; unit: Unit } {
  const unit: Unit = seconds % 3600 === 0 ? 3600 : seconds % 60 === 0 ? 60 : 1
  return { amount: seconds / unit, unit }
}

const policyLabel = (policy: RateLimitPolicyStats) => POLICY_LABELS[policy.policy] ?? policy.policy

type DialogProps = {
  policy: RateLimitPolicyStats
  onClose: () => void
  onSaved: (stats: RateLimitStats) => void
  onSessionExpired: () => void
}

/** Edits a policy's quota; it applies at once and every key starts over with a full bucket. */
export function RateLimitPolicyEditDialog({ policy, onClose, onSaved, onSessionExpired }: DialogProps) {
  const initialWindow = splitWindow(policy.windowSeconds)
  const [maxRequests, setMaxRequests] = useState(String(policy.maxRequests))
  const [windowAmount, setWindowAmount] = useState(String(initialWindow.amount))
  const [unit, setUnit] = useState<Unit>(initialWindow.unit)
  const [error, setError] = useState<string | null>(null)
  const [running, setRunning] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    const requests = Number(maxRequests)
    const windowSeconds = Number(windowAmount) * unit
    if (!Number.isInteger(requests) || requests < 1 || requests > MAX_REQUESTS) {
      setError(`Số request tối đa phải là số nguyên từ 1 đến ${MAX_REQUESTS.toLocaleString("vi-VN")}.`)
      return
    }
    if (!Number.isInteger(windowSeconds) || windowSeconds < 1 || windowSeconds > MAX_WINDOW_SECONDS) {
      setError("Khoảng thời gian phải là số nguyên, từ 1 giây đến 24 giờ.")
      return
    }
    setRunning(true)
    setError(null)
    try {
      const stats = await updateRateLimitPolicy(policy.policy, {
        enabled: policy.enabled,
        maxRequests: requests,
        windowSeconds,
      })
      toast.success(`Đã đổi hạn mức "${policyLabel(policy)}" thành ${formatQuota(requests, windowSeconds)}`)
      onSaved(stats)
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
      <DialogContent className="sm:max-w-md">
        <form onSubmit={(event) => void submit(event)}>
          <DialogHeader>
            <DialogTitle>Sửa hạn mức &ldquo;{policyLabel(policy)}&rdquo;</DialogTitle>
            <DialogDescription>
              Có hiệu lực ngay và mọi người được đếm lại từ đầu. Mặc định trong application.properties:{" "}
              {formatQuota(policy.defaultMaxRequests, policy.defaultWindowSeconds)}.
            </DialogDescription>
          </DialogHeader>

          <div className="mt-4 grid gap-4">
            <ErrorAlert error={error} />

            <div className="grid gap-1.5">
              <Label htmlFor="rate-limit-max-requests">Số request tối đa</Label>
              <Input id="rate-limit-max-requests" required type="number" min="1" max={MAX_REQUESTS} step="1"
                value={maxRequests} disabled={running} onChange={(event) => setMaxRequests(event.target.value)} />
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="rate-limit-window">Trong khoảng thời gian</Label>
              <div className="flex gap-2">
                <Input id="rate-limit-window" required type="number" min="1" step="1" className="flex-1"
                  value={windowAmount} disabled={running} onChange={(event) => setWindowAmount(event.target.value)} />
                <Select value={String(unit)} items={UNIT_ITEMS} disabled={running}
                  onValueChange={(value) => setUnit(Number(value) as Unit)}>
                  <SelectTrigger className="w-28" aria-label="Đơn vị thời gian">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {Object.entries(UNITS).map(([seconds, label]) => (
                      <SelectItem key={seconds} value={seconds}>{label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <p className="text-xs text-muted-foreground">Tối đa 24 giờ.</p>
            </div>
          </div>

          <DialogFooter className="mt-6">
            <Button type="button" variant="outline" onClick={onClose} disabled={running}>Hủy</Button>
            <Button type="submit" disabled={running}>
              {running && <Loader2 className="animate-spin" />}
              Lưu
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export type RateLimitPolicyAction = "disable" | "enable" | "restore"

/** Confirms switching a policy off or on, or dropping the administrator's change. */
export function RateLimitPolicyConfirmDialog({ policy, action, onClose, onSaved, onSessionExpired }: DialogProps & {
  action: RateLimitPolicyAction
}) {
  const [running, setRunning] = useState(false)
  const label = policyLabel(policy)
  const copy = {
    disable: {
      title: `Tắt mức "${label}"?`,
      description: "Request thuộc mức này không còn bị đếm và chặn cho tới khi bật lại. Các mức khác vẫn áp dụng.",
      confirm: "Tắt",
      done: `Đã tắt mức "${label}"`,
    },
    enable: {
      title: `Bật lại mức "${label}"?`,
      description: `Áp dụng ngay với hạn mức ${formatQuota(policy.maxRequests, policy.windowSeconds)}; mọi người được đếm lại từ đầu.`,
      confirm: "Bật",
      done: `Đã bật mức "${label}"`,
    },
    restore: {
      title: `Khôi phục mặc định cho "${label}"?`,
      description: `Hạn mức về ${formatQuota(policy.defaultMaxRequests, policy.defaultWindowSeconds)} như trong application.properties và mức được bật lại; mọi người được đếm lại từ đầu.`,
      confirm: "Khôi phục",
      done: `Đã khôi phục mặc định cho "${label}"`,
    },
  }[action]

  async function confirm() {
    setRunning(true)
    try {
      const stats = action === "restore"
        ? await restoreRateLimitPolicy(policy.policy)
        : await updateRateLimitPolicy(policy.policy, {
          enabled: action === "enable",
          maxRequests: policy.maxRequests,
          windowSeconds: policy.windowSeconds,
        })
      toast.success(copy.done)
      onSaved(stats)
      onClose()
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      toast.error(apiErrorMessage(caught))
    } finally {
      setRunning(false)
    }
  }

  return (
    <AlertDialog open onOpenChange={(open) => { if (!open && !running) onClose() }}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{copy.title}</AlertDialogTitle>
          <AlertDialogDescription>{copy.description}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={running}>Hủy</AlertDialogCancel>
          <AlertDialogAction
            className={action === "disable" ? "bg-destructive text-white hover:bg-destructive/90" : undefined}
            disabled={running}
            onClick={(event) => { event.preventDefault(); void confirm() }}
          >
            {running && <Loader2 className="animate-spin" />}
            {copy.confirm}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
