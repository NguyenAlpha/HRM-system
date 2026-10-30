"use client"

import { useEffect, useMemo, useState } from "react"
import { Briefcase, Eye, Loader2, Pencil, Plus, RefreshCw, Trash2, TrendingUp } from "lucide-react"

import { RowActionItem, RowActionsCell } from "@/components/common/row-actions"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
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
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  EmptyRow,
  SectionError,
  money,
  today,
  useWorkforceAction,
  workforceApi,
} from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import type {
  PositionAllowance,
  PositionAllowanceSummary,
  SeniorityAllowance,
  WorkforceBody,
} from "@/lib/workforce"
import { formatDateRange } from "@/lib/format-date"

type Run = (success: string, action: () => Promise<unknown>) => Promise<boolean>
type Period = { effectiveFrom: string; effectiveTo: string | null }

/** Whether a rule applies today, starts later or has ended. */
function PeriodBadge({ period }: { period: Period }) {
  const day = today()
  if (period.effectiveFrom > day) return <Badge variant="outline">Sắp áp dụng</Badge>
  if (period.effectiveTo !== null && period.effectiveTo < day) return <Badge variant="secondary">Hết hiệu lực</Badge>
  return <Badge variant="secondary">Đang áp dụng</Badge>
}

const periodText = (period: Period) => formatDateRange(period.effectiveFrom, period.effectiveTo)

const yearsText = (rule: Pick<SeniorityAllowance, "minYears" | "maxYears">) =>
  rule.maxYears === null ? `Từ ${rule.minYears} năm` : `${rule.minYears} – dưới ${rule.maxYears} năm`

/** Company-wide position and seniority allowances that payroll reads for every employee. */
export function AllowancePolicyPage() {
  const { session } = usePortalSession()
  const permissions = useMemo(
    () => new Set(session.account.permissions.map((permission) => permission.code)),
    [session],
  )
  const canRead = permissions.has("compensation.read")
  const canManage = permissions.has("compensation.manage")
  const [tab, setTab] = useState("positions")

  return (
    <>
      <PageHeader
        title="Chính sách phụ cấp"
        description="Phụ cấp chức vụ theo tháng và phụ cấp thâm niên theo % lương cơ bản, áp dụng chung khi tính lương."
        backToOverview
      />

      <div className="mt-6">
        {!canRead && <p role="alert">Tài khoản chưa có quyền truy cập mục này.</p>}
        {canRead && (
          <Card>
            <CardHeader>
              <CardTitle>Chính sách phụ cấp</CardTitle>
              <CardDescription>
                Mỗi mức có ngày hiệu lực; mức mới tự kết thúc mức đang áp dụng vào ngày trước đó. Đổi chính sách
                không tự tính lại kỳ lương đã tính, kế toán cần tính lại kỳ bị ảnh hưởng.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Tabs value={tab} onValueChange={setTab}>
                <TabsList>
                  <TabsTrigger value="positions"><Briefcase /> Phụ cấp chức vụ</TabsTrigger>
                  <TabsTrigger value="seniority"><TrendingUp /> Phụ cấp thâm niên</TabsTrigger>
                </TabsList>
                <TabsContent value="positions" className="mt-4 grid gap-4">
                  <PositionAllowances canManage={canManage} />
                </TabsContent>
                <TabsContent value="seniority" className="mt-4 grid gap-4">
                  <SeniorityAllowances canManage={canManage} />
                </TabsContent>
              </Tabs>
            </CardContent>
          </Card>
        )}
      </div>
    </>
  )
}

function PositionAllowances({ canManage }: { canManage: boolean }) {
  const [positions, setPositions] = useState<PositionAllowanceSummary[]>([])
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [loadedRevision, setLoadedRevision] = useState(-1)
  const [selected, setSelected] = useState<PositionAllowanceSummary | null>(null)
  const loading = loadedRevision !== revision
  const reload = () => setRevision((value) => value + 1)
  const { busy, run } = useWorkforceAction(reload)

  useEffect(() => {
    let active = true
    workforceApi<PositionAllowanceSummary[]>("compensation/position-allowances")
      .then((result) => { if (active) { setPositions(result); setError(null) } })
      .catch((caught: unknown) => { if (active) setError(apiErrorMessage(caught)) })
      .finally(() => { if (active) setLoadedRevision(revision) })
    return () => { active = false }
  }, [revision])

  return (
    <>
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm text-muted-foreground">
          Trả theo tỷ lệ công như lương cơ bản, theo chức danh trong phân công chính của từng ngày.
        </p>
        <Button type="button" variant="outline" size="sm" onClick={reload} disabled={loading}>
          <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
        </Button>
      </div>

      <SectionError error={error} />

      <div className="rounded-xl border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Mã / Chức danh</TableHead>
              <TableHead className="text-right">Phụ cấp tháng hiện tại</TableHead>
              <TableHead>Hiệu lực từ</TableHead>
              <TableHead className="text-right">Thao tác</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading && Array.from({ length: 4 }).map((_, index) => (
              <TableRow key={index}>
                {Array.from({ length: 4 }).map((__, cell) => (
                  <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
                ))}
              </TableRow>
            ))}

            {!loading && positions.map((position) => (
              <TableRow key={position.jobPositionId}>
                <TableCell>
                  <span className="font-medium">{position.title}</span>
                  {!position.active && <Badge variant="outline" className="ml-2">Tạm tắt</Badge>}
                  <span className="block text-xs text-muted-foreground"><code>{position.code}</code></span>
                </TableCell>
                <TableCell className="text-right font-medium">
                  {position.currentMonthlyAmount === null
                    ? <span className="font-normal text-muted-foreground">Không có</span>
                    : money(position.currentMonthlyAmount)}
                </TableCell>
                <TableCell className="text-muted-foreground">{position.currentEffectiveFrom ?? "—"}</TableCell>
                <RowActionsCell label={position.code}>
                  {canManage && position.active
                    ? <RowActionItem icon={Pencil} onClick={() => setSelected(position)}>Đổi phụ cấp</RowActionItem>
                    : <RowActionItem icon={Eye} onClick={() => setSelected(position)}>Xem lịch sử</RowActionItem>}
                </RowActionsCell>
              </TableRow>
            ))}

            {!loading && positions.length === 0 && <EmptyRow colSpan={4}>Chưa có vị trí công việc.</EmptyRow>}
          </TableBody>
        </Table>
      </div>

      {selected && (
        <PositionAllowanceDialog position={selected} canManage={canManage && selected.active} busy={busy} run={run}
          onClose={() => setSelected(null)} />
      )}
    </>
  )
}

function PositionAllowanceDialog({ position, canManage, busy, run, onClose }: {
  position: PositionAllowanceSummary
  canManage: boolean
  busy: boolean
  run: Run
  onClose: () => void
}) {
  const [history, setHistory] = useState<PositionAllowance[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [amount, setAmount] = useState("")
  const [effectiveFrom, setEffectiveFrom] = useState(today())
  const [note, setNote] = useState("")

  useEffect(() => {
    let active = true
    workforceApi<PositionAllowance[]>(`compensation/job-positions/${position.jobPositionId}/allowances`)
      .then((result) => { if (active) { setHistory(result); setError(null) } })
      .catch((caught: unknown) => { if (active) setError(apiErrorMessage(caught)) })
    return () => { active = false }
  }, [position.jobPositionId, revision])

  async function submit() {
    const ok = await run(`Đã đặt phụ cấp cho ${position.title}`, () =>
      workforceApi(`compensation/job-positions/${position.jobPositionId}/allowances`, "POST", {
        monthlyAmount: Number(amount), effectiveFrom, note: note.trim() || null,
      } satisfies WorkforceBody["SetPositionAllowanceCommand"]))
    if (ok) {
      setAmount("")
      setNote("")
      setRevision((value) => value + 1)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Phụ cấp chức vụ · {position.title}</DialogTitle>
          <DialogDescription>
            Lịch sử các mức phụ cấp của chức danh <code>{position.code}</code>, mới nhất trước.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4">
          <SectionError error={error} />
          <div className="max-h-64 overflow-y-auto rounded-xl border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Hiệu lực</TableHead>
                  <TableHead className="text-right">Phụ cấp tháng</TableHead>
                  <TableHead>Ghi chú</TableHead>
                  <TableHead>Trạng thái</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {history === null && !error && (
                  <TableRow><TableCell colSpan={4}><Skeleton className="h-4 w-full" /></TableCell></TableRow>
                )}
                {history?.map((rule) => (
                  <TableRow key={rule.id}>
                    <TableCell>{periodText(rule)}</TableCell>
                    <TableCell className="text-right font-medium">{money(rule.monthlyAmount)}</TableCell>
                    <TableCell className="text-muted-foreground">{rule.note ?? "—"}</TableCell>
                    <TableCell><PeriodBadge period={rule} /></TableCell>
                  </TableRow>
                ))}
                {history?.length === 0 && <EmptyRow colSpan={4}>Chức danh này chưa có phụ cấp.</EmptyRow>}
              </TableBody>
            </Table>
          </div>

          {canManage && (
            <form id="position-allowance-form" className="grid gap-3 sm:grid-cols-[1fr_1fr]" onSubmit={(event) => {
              event.preventDefault()
              void submit()
            }}>
              <div className="grid gap-1.5">
                <Label htmlFor="position-allowance-amount">Phụ cấp tháng mới (VND)</Label>
                <Input id="position-allowance-amount" required type="number" min="0" step="1" value={amount}
                  onChange={(event) => setAmount(event.target.value)} />
              </div>
              <div className="grid gap-1.5">
                <Label htmlFor="position-allowance-from">Hiệu lực từ</Label>
                <Input id="position-allowance-from" required type="date" value={effectiveFrom}
                  onChange={(event) => setEffectiveFrom(event.target.value)} />
              </div>
              <div className="grid gap-1.5 sm:col-span-2">
                <Label htmlFor="position-allowance-note">Ghi chú</Label>
                <Input id="position-allowance-note" value={note} onChange={(event) => setNote(event.target.value)} />
              </div>
              <p className="text-xs text-muted-foreground sm:col-span-2">
                Nhập 0 để bỏ phụ cấp từ ngày hiệu lực. Ngày hiệu lực phải sau ngày bắt đầu của mức đang áp dụng.
              </p>
            </form>
          )}
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Đóng</Button>
          {canManage && (
            <Button type="submit" form="position-allowance-form" disabled={busy}>
              {busy && <Loader2 className="animate-spin" />} Lưu mức mới
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function SeniorityAllowances({ canManage }: { canManage: boolean }) {
  const [rules, setRules] = useState<SeniorityAllowance[]>([])
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [loadedRevision, setLoadedRevision] = useState(-1)
  const [adding, setAdding] = useState(false)
  const loading = loadedRevision !== revision
  const reload = () => setRevision((value) => value + 1)
  const { busy, run } = useWorkforceAction(reload)

  useEffect(() => {
    let active = true
    workforceApi<SeniorityAllowance[]>("compensation/seniority-allowances")
      // Group by tier so each tier's changes sit together, newest first within a tier.
      .then((result) => {
        if (!active) return
        setRules(result.toSorted((a, b) => a.minYears - b.minYears || b.effectiveFrom.localeCompare(a.effectiveFrom)))
        setError(null)
      })
      .catch((caught: unknown) => { if (active) setError(apiErrorMessage(caught)) })
      .finally(() => { if (active) setLoadedRevision(revision) })
    return () => { active = false }
  }, [revision])

  return (
    <>
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm text-muted-foreground">
          % lương cơ bản theo số năm thâm niên tròn tính từ ngày bắt đầu thâm niên; mỗi năm thâm niên thuộc đúng một bậc.
        </p>
        <div className="flex gap-2">
          <Button type="button" variant="outline" size="sm" onClick={reload} disabled={loading}>
            <RefreshCw className={loading ? "animate-spin" : ""} /> Tải lại
          </Button>
          {canManage && (
            <Button type="button" size="sm" onClick={() => setAdding(true)} disabled={loading}><Pencil /> Sửa bảng</Button>
          )}
        </div>
      </div>

      <SectionError error={error} />

      <div className="rounded-xl border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Thâm niên</TableHead>
              <TableHead className="text-right">Tỷ lệ</TableHead>
              <TableHead>Hiệu lực</TableHead>
              <TableHead>Ghi chú</TableHead>
              <TableHead>Trạng thái</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading && Array.from({ length: 3 }).map((_, index) => (
              <TableRow key={index}>
                {Array.from({ length: 5 }).map((__, cell) => (
                  <TableCell key={cell}><Skeleton className="h-4 w-full" /></TableCell>
                ))}
              </TableRow>
            ))}

            {!loading && rules.map((rule) => (
              <TableRow key={rule.id}>
                <TableCell className="font-medium">{yearsText(rule)}</TableCell>
                <TableCell className="text-right font-medium">{Number(rule.percentage)}%</TableCell>
                <TableCell>{periodText(rule)}</TableCell>
                <TableCell className="text-muted-foreground">{rule.note ?? "—"}</TableCell>
                <TableCell><PeriodBadge period={rule} /></TableCell>
              </TableRow>
            ))}

            {!loading && rules.length === 0 && <EmptyRow colSpan={5}>Chưa có bậc phụ cấp thâm niên.</EmptyRow>}
          </TableBody>
        </Table>
      </div>

      {adding && (
        <SeniorityTableDialog busy={busy} run={run} onClose={() => setAdding(false)}
          current={rules.filter((rule) => rule.effectiveTo === null).toSorted((a, b) => a.minYears - b.minYears)} />
      )}
    </>
  )
}

/** One row of the table being edited; its lower bound is the previous row's upper bound, the last row is open. */
type TierRow = { key: number; maxYears: string; percentage: string }

/**
 * Declares the whole seniority table from one date: the rows always join from 0 years to an open last row, so
 * no year is left without a rate. Starts from the table in force today.
 */
function SeniorityTableDialog({ current, busy, run, onClose }: {
  current: SeniorityAllowance[]
  busy: boolean
  run: Run
  onClose: () => void
}) {
  const [rows, setRows] = useState<TierRow[]>(() => current.length > 0
    ? current.map((rule, index) => ({
      key: index,
      maxYears: rule.maxYears == null ? "" : String(rule.maxYears),
      percentage: String(Number(rule.percentage)),
    }))
    : [{ key: 0, maxYears: "", percentage: "0" }])
  const [nextKey, setNextKey] = useState(rows.length)
  const [effectiveFrom, setEffectiveFrom] = useState(today())
  const [note, setNote] = useState("")
  const minYears = (index: number) => (index === 0 ? 0 : Number(rows[index - 1].maxYears))

  function update(index: number, change: Partial<TierRow>) {
    setRows((previous) => previous.map((row, at) => (at === index ? { ...row, ...change } : row)))
  }

  function addRow() {
    // The open last row gets an upper bound to fill in; the new row becomes the open one.
    setRows((previous) => [...previous, { key: nextKey, maxYears: "", percentage: previous.at(-1)?.percentage ?? "0" }])
    setNextKey((value) => value + 1)
  }

  function removeRow(index: number) {
    setRows((previous) => {
      const next = previous.filter((_, at) => at !== index)
      return next.map((row, at) => (at === next.length - 1 ? { ...row, maxYears: "" } : row))
    })
  }

  async function submit() {
    const ok = await run("Đã cập nhật bảng phụ cấp thâm niên", () =>
      workforceApi("compensation/seniority-allowances", "POST", {
        effectiveFrom,
        note: note.trim() || null,
        tiers: rows.map((row, index) => ({
          minYears: minYears(index),
          maxYears: index === rows.length - 1 ? null : Number(row.maxYears),
          percentage: Number(row.percentage),
        })),
      } satisfies WorkforceBody["SetSeniorityAllowancesCommand"]))
    if (ok) onClose()
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={(event) => { event.preventDefault(); void submit() }}>
          <DialogHeader>
            <DialogTitle>Bảng phụ cấp thâm niên</DialogTitle>
            <DialogDescription>
              Khai báo cả bảng: mỗi năm thâm niên thuộc đúng một bậc, bậc cuối không giới hạn. Bảng đang áp dụng kết
              thúc vào ngày trước ngày hiệu lực.
            </DialogDescription>
          </DialogHeader>

          <div className="mt-4 grid gap-4">
            <div className="rounded-lg border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Từ (năm)</TableHead>
                    <TableHead>Đến dưới (năm)</TableHead>
                    <TableHead>Tỷ lệ (%)</TableHead>
                    <TableHead className="w-10" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.map((row, index) => {
                    const last = index === rows.length - 1
                    const from = minYears(index)
                    return (
                      <TableRow key={row.key}>
                        <TableCell className="tabular-nums">{Number.isFinite(from) ? from : "—"}</TableCell>
                        <TableCell>
                          {last ? <span className="text-sm text-muted-foreground">Không giới hạn</span> : (
                            <Input aria-label={`Bậc ${index + 1}: đến dưới (năm)`} required type="number" step="1"
                              min={Number.isFinite(from) ? from + 1 : 1} value={row.maxYears}
                              onChange={(event) => update(index, { maxYears: event.target.value })} />
                          )}
                        </TableCell>
                        <TableCell>
                          <Input aria-label={`Bậc ${index + 1}: tỷ lệ (%)`} required type="number" min="0" step="0.01"
                            value={row.percentage} onChange={(event) => update(index, { percentage: event.target.value })} />
                        </TableCell>
                        <TableCell>
                          {rows.length > 1 && (
                            <Button type="button" variant="ghost" size="icon" aria-label={`Xóa bậc ${index + 1}`}
                              onClick={() => removeRow(index)}><Trash2 /></Button>
                          )}
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </div>
            <Button type="button" variant="outline" size="sm" className="justify-self-start" onClick={addRow}>
              <Plus /> Thêm bậc
            </Button>
            <div className="grid grid-cols-2 gap-3">
              <div className="grid gap-1.5">
                <Label htmlFor="seniority-from">Hiệu lực từ</Label>
                <Input id="seniority-from" required type="date" value={effectiveFrom}
                  onChange={(event) => setEffectiveFrom(event.target.value)} />
              </div>
              <div className="grid gap-1.5">
                <Label htmlFor="seniority-note">Ghi chú</Label>
                <Input id="seniority-note" value={note} onChange={(event) => setNote(event.target.value)} />
              </div>
            </div>
          </div>

          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Hủy</Button>
            <Button type="submit" disabled={busy}>
              {busy && <Loader2 className="animate-spin" />} Lưu bảng
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
