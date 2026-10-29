"use client"

import { FormEvent, useEffect, useMemo, useState } from "react"
import { Loader2, UserRoundCheck } from "lucide-react"
import { toast } from "sonner"

import { todayIso } from "@/components/account/account-access"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
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
import { apiErrorMessage, isPortalSessionExpired } from "@/lib/api-helpers"
import type { Portal } from "@/lib/auth/types"
import { employeeRequest, type EmployeeSummary } from "@/lib/employee"
import { formatDate } from "@/lib/format-date"
import type { Page } from "@/lib/pagination"
import { appointDirector } from "@/lib/role-assignment"

/**
 * Appoints the next Director in one step: the current Director works until the day before the
 * handover, which may lie ahead, and the new one starts on it.
 */
export function DirectorAppointmentCard({ portal, onSessionExpired }: { portal: Portal; onSessionExpired: () => void }) {
  const [employees, setEmployees] = useState<EmployeeSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [employeeId, setEmployeeId] = useState("")
  const [handoverDate, setHandoverDate] = useState(todayIso)
  const [reason, setReason] = useState("")
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    employeeRequest<Page<EmployeeSummary>>(portal, "?page=0&size=100")
      .then((page) => { if (active) setEmployees(page.content) })
      .catch((caught: unknown) => {
        if (!active) return
        if (isPortalSessionExpired(caught)) onSessionExpired()
        setError(apiErrorMessage(caught))
      })
    return () => { active = false }
  }, [portal, onSessionExpired])

  // A Director must be able to sign in and still be working.
  const candidates = useMemo(
    () => (employees ?? []).filter((employee) => employee.account?.status === "ACTIVE"
      && (employee.employmentStatus === "ACTIVE" || employee.employmentStatus === "PROBATION")
      && employee.terminationDate === null),
    [employees],
  )
  const items = useMemo(
    () => Object.fromEntries(candidates.map((employee) => [
      String(employee.id), `${employee.fullName} (${employee.account?.username})`,
    ])),
    [candidates],
  )
  const appointee = candidates.find((employee) => String(employee.id) === employeeId) ?? null

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!appointee?.account) return
    setBusy(true)
    setError(null)
    try {
      await appointDirector(portal, appointee.account.id, { handoverDate, reason: reason.trim() })
      toast.success(`Đã bổ nhiệm ${appointee.fullName} làm Giám đốc từ ${formatDate(handoverDate)}`)
      setEmployeeId("")
      setReason("")
      setHandoverDate(todayIso())
    } catch (caught) {
      if (isPortalSessionExpired(caught)) onSessionExpired()
      setError(apiErrorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Bổ nhiệm Giám đốc mới</CardTitle>
        <CardDescription>
          Giám đốc hiện tại giữ vai trò tới hết ngày trước ngày bàn giao; người được bổ nhiệm bắt đầu từ ngày bàn giao.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <SectionError error={error} />
        <form onSubmit={submit} className="mt-2 grid gap-4">
          <div className="grid gap-1.5">
            <Label htmlFor="director-employee">Người được bổ nhiệm</Label>
            <Select value={employeeId} items={items} onValueChange={(value) => setEmployeeId(value ?? "")} disabled={!employees}>
              <SelectTrigger id="director-employee" className="w-full">
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
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="director-handover">Ngày bàn giao</Label>
            <Input id="director-handover" type="date" min={todayIso()} required value={handoverDate}
              onChange={(event) => setHandoverDate(event.target.value)} />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="director-reason">Lý do</Label>
            <Textarea id="director-reason" rows={2} maxLength={100} value={reason}
              onChange={(event) => setReason(event.target.value)} />
          </div>
          <div>
            <Button type="submit" disabled={busy || !appointee || !handoverDate || !reason.trim()}>
              {busy ? <Loader2 className="animate-spin" /> : <UserRoundCheck />} Bổ nhiệm
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
