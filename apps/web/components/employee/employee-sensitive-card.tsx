"use client"

import { useState } from "react"
import { Eye, EyeOff, Loader2, PencilLine, ShieldAlert } from "lucide-react"

import { EmployeeSensitiveDialog } from "@/components/employee/employee-sensitive-dialog"
import { RbacFeedback } from "@/components/admin/rbac-controls"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  employeeErrorMessage,
  getEmployeeSensitiveData,
  isSessionExpired,
  type EmployeeDetail,
  type EmployeeSensitiveData,
} from "@/lib/employee"

function SensitiveField({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="mt-1 break-words text-sm font-medium">{value || "—"}</dd>
    </div>
  )
}

export function EmployeeSensitiveCard({ employee, canManage, onSessionExpired }: {
  employee: EmployeeDetail
  canManage: boolean
  onSessionExpired: () => void
}) {
  const [data, setData] = useState<EmployeeSensitiveData | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)

  async function reveal() {
    setLoading(true)
    setError(null)
    try {
      setData(await getEmployeeSensitiveData(employee.id))
    } catch (caught) {
      if (isSessionExpired(caught)) onSessionExpired()
      setError(employeeErrorMessage(caught))
    } finally {
      setLoading(false)
    }
  }

  function hide() {
    setData(null)
    setError(null)
  }

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><ShieldAlert /> Dữ liệu nhạy cảm</CardTitle>
          <CardDescription>
            CCCD, email cá nhân, địa chỉ, mã số thuế và tài khoản ngân hàng. Chỉ tải khi bạn yêu cầu.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {error && <RbacFeedback error={error} />}

          {data && (
            <dl className="grid gap-4 sm:grid-cols-2">
              <SensitiveField label="Số CCCD" value={data.nationalId} />
              <SensitiveField label="Mã số thuế" value={data.taxCode} />
              <SensitiveField label="Email cá nhân" value={data.personalEmail} />
              <SensitiveField label="Địa chỉ" value={data.address} />
              <SensitiveField label="Ngân hàng" value={data.bankName} />
              <SensitiveField label="Số tài khoản" value={data.bankAccountNumber} />
              <SensitiveField label="Chủ tài khoản" value={data.bankAccountHolder} />
            </dl>
          )}

          <div className="flex flex-wrap items-center gap-2">
            {data ? (
              <Button type="button" variant="outline" onClick={hide}>
                <EyeOff /> Ẩn đi
              </Button>
            ) : (
              <Button type="button" variant="outline" onClick={() => void reveal()} disabled={loading}>
                {loading ? <Loader2 className="animate-spin" /> : <Eye />} Xem dữ liệu
              </Button>
            )}
            {canManage && (
              <Button type="button" onClick={() => setEditing(true)}>
                <PencilLine /> Cập nhật
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      {editing && (
        <EmployeeSensitiveDialog
          employee={employee}
          onClose={() => setEditing(false)}
          onSaved={(saved) => { setData(saved); setEditing(false) }}
          onSessionExpired={onSessionExpired}
        />
      )}
    </>
  )
}
