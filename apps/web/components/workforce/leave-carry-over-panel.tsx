"use client"

import { useState } from "react"
import { Loader2 } from "lucide-react"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { apiErrorMessage } from "@/lib/api-helpers"
import { authorizedRequest } from "@/lib/auth/client"
import type { LeaveCarryOver } from "@/lib/workforce"

const days = (minutes: number, dayMinutes: number) => dayMinutes > 0 ? Number((minutes / dayMinutes).toFixed(2)) : 0

/** Year-end close: before December the year to close is usually the one that just ended. */
function defaultFromYear(): number {
  const now = new Date()
  return now.getMonth() === 11 ? now.getFullYear() : now.getFullYear() - 1
}

/** Previews and applies carrying each employee's unused annual leave into the next year. */
export function LeaveCarryOverPanel() {
  const [fromYear, setFromYear] = useState(String(defaultFromYear()))
  const [preview, setPreview] = useState<LeaveCarryOver | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")

  async function run(method: "GET" | "POST") {
    setBusy(true)
    setError("")
    try {
      const result = await authorizedRequest<LeaveCarryOver>(
        "hrm", `/workforce/leave-entitlements/carry-over?fromYear=${Number(fromYear)}`, { method },
      )
      setPreview(result)
      if (method === "POST") {
        toast.success(`Đã chốt phép năm ${result.fromYear} sang ${result.toYear}`)
      }
    } catch (caught) {
      setError(apiErrorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Chốt phép năm</CardTitle>
        <CardDescription>
          Chuyển số ngày phép năm còn dư của từng nhân viên sang năm sau theo quy tắc phép năm. Xem trước rồi
          mới áp dụng; chạy lại sẽ tính lại và ghi đè.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-end gap-2">
          <div className="grid gap-1.5">
            <Label htmlFor="carry-over-year">Năm cần chốt</Label>
            <Input id="carry-over-year" type="number" min="2000" className="w-32" value={fromYear}
              onChange={(event) => { setFromYear(event.target.value); setPreview(null) }} />
          </div>
          <Button type="button" variant="outline" disabled={busy || !fromYear} onClick={() => void run("GET")}>
            {busy && <Loader2 className="animate-spin" />} Xem trước
          </Button>
          <Button type="button" disabled={busy || !preview} onClick={() => void run("POST")}>
            Áp dụng
          </Button>
        </div>
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        {preview && <>
          <p className="text-sm text-muted-foreground">
            Chuyển tối đa {preview.maxCarryOverDays} ngày sang năm {preview.toYear}
            {preview.expiresOn ? `, dùng đến hết ${preview.expiresOn}` : ""}.
          </p>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Mã NV</TableHead>
                <TableHead>Họ tên</TableHead>
                <TableHead className="text-right">Còn dư {preview.fromYear}</TableHead>
                <TableHead className="text-right">Đang chuyển</TableHead>
                <TableHead className="text-right">Sẽ chuyển</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {preview.items.map((item) => (
                <TableRow key={item.employeeId}>
                  <TableCell>{item.employeeCode}</TableCell>
                  <TableCell>{item.fullName}</TableCell>
                  <TableCell className="text-right">{days(item.remainingMinutes, item.standardDayMinutes)} ngày</TableCell>
                  <TableCell className="text-right">{days(item.currentCarriedOverMinutes, item.standardDayMinutes)} ngày</TableCell>
                  <TableCell className="text-right font-medium">{days(item.carriedOverMinutes, item.standardDayMinutes)} ngày</TableCell>
                </TableRow>
              ))}
              {preview.items.length === 0 && (
                <TableRow><TableCell colSpan={5} className="text-center text-muted-foreground">Không có nhân viên nào.</TableCell></TableRow>
              )}
            </TableBody>
          </Table>
        </>}
      </CardContent>
    </Card>
  )
}
