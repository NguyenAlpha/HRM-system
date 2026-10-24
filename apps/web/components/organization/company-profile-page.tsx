"use client"

import { useRouter } from "next/navigation"
import { FormEvent, useEffect, useRef, useState } from "react"
import { Loader2, PencilLine, Trash2, Upload } from "lucide-react"
import { toast } from "sonner"

import { PhoneInput } from "@/components/common/phone-input"
import { InfoField } from "@/components/employee/employee-detail-page"
import { BrandMark } from "@/components/portal/brand-mark"
import { PageHeader } from "@/components/portal/page-header"
import { usePortalSession } from "@/components/portal/portal-shell"
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
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
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
import { Textarea } from "@/components/ui/textarea"
import { SectionError, workforceApi } from "@/components/workforce/workforce-shared"
import { apiErrorMessage, friendlyErrorMessage } from "@/lib/api-helpers"
import { authorizedRequest } from "@/lib/auth/client"
import { formatDateTime } from "@/lib/format-date"
import { LOGO_TYPES, MAX_LOGO_BYTES, logoUrl } from "@/lib/branding"
import type { CompanyProfile, WorkforceBody } from "@/lib/workforce"

/** The company's own details; only the Company Owner (`company.manage`) edits them. */
export function CompanyProfilePage() {
  const router = useRouter()
  const { session } = usePortalSession()
  const canManage = session.account.permissions.some((permission) => permission.code === "company.manage")
  const [company, setCompany] = useState<CompanyProfile | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [editing, setEditing] = useState(false)

  useEffect(() => {
    let active = true
    workforceApi<CompanyProfile>("company")
      .then((result) => { if (active) { setCompany(result); setError(null) } })
      .catch((caught: unknown) => { if (active) setError(apiErrorMessage(caught)) })
    return () => { active = false }
  }, [revision])

  // Reloads the card and, through the server layout, the name and logo in the sidebar.
  function reload() {
    setRevision((value) => value + 1)
    router.refresh()
  }

  return (
    <>
      <PageHeader
        title="Thông tin doanh nghiệp"
        description="Tên, mã số thuế và địa chỉ in trên phiếu lương và bảng lương năm."
        backToOverview
      />

      <div className="mt-6 grid max-w-3xl gap-4">
        <SectionError error={error} />
        {!company && !error && <Skeleton className="h-48 w-full rounded-xl" />}
        {company && (
          <Card>
            <CardHeader>
              <CardTitle>{company.name}</CardTitle>
              <CardDescription>Cập nhật lần cuối {formatDateTime(company.updatedAt)}</CardDescription>
              {canManage && (
                <CardAction>
                  <Button type="button" variant="outline" size="sm" onClick={() => setEditing(true)}>
                    <PencilLine /> Sửa thông tin
                  </Button>
                </CardAction>
              )}
            </CardHeader>
            <CardContent>
              <CompanyLogo company={company} canManage={canManage} onChanged={reload} />
              <dl className="grid gap-4 sm:grid-cols-2">
                <InfoField label="Mã doanh nghiệp" value={company.code} />
                <InfoField label="Mã số thuế" value={company.taxCode} />
                <InfoField label="Điện thoại" value={company.phone} />
                <InfoField label="Email" value={company.email} />
                <div className="sm:col-span-2"><InfoField label="Địa chỉ" value={company.address} /></div>
              </dl>
            </CardContent>
          </Card>
        )}
      </div>

      {editing && company && (
        <EditCompanyDialog
          company={company}
          onClose={() => setEditing(false)}
          onSaved={() => { setEditing(false); reload() }}
        />
      )}
    </>
  )
}

/** Logo preview; the Company Owner uploads, replaces or removes it. */
function CompanyLogo({ company, canManage, onChanged }: {
  company: CompanyProfile
  canManage: boolean
  onChanged: () => void
}) {
  const fileInput = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const hasLogo = company.logoUpdatedAt !== null

  async function change(method: "PUT" | "DELETE", file?: File) {
    setBusy(true)
    let body: FormData | undefined
    if (file) {
      body = new FormData()
      body.append("file", file)
    }
    try {
      await authorizedRequest("hrm", "/workforce/company/logo", { method, body })
      toast.success(method === "PUT" ? "Đã cập nhật logo" : "Đã xóa logo")
      setConfirmingDelete(false)
      onChanged()
    } catch (caught) {
      toast.error(apiErrorMessage(caught))
    } finally {
      setBusy(false)
    }
  }

  function upload(file: File) {
    // Same rule as the API, checked first so a wrong file fails without uploading it.
    if (!LOGO_TYPES.includes(file.type) || file.size > MAX_LOGO_BYTES) {
      toast.error(friendlyErrorMessage("COMPANY_LOGO_INVALID", "", null))
      return
    }
    void change("PUT", file)
  }

  return (
    <div className="mb-5 flex items-center gap-4 border-b pb-5">
      {/* `!` beats the unlayered .brand-mark size rules. */}
      <BrandMark
        branding={{ name: company.name, logoUrl: logoUrl(company.logoUpdatedAt) }}
        className="size-16! rounded-xl! text-2xl!"
      />
      <div className="grid gap-1">
        <p className="text-sm font-medium">Logo</p>
        <p className="text-xs text-muted-foreground">
          Hiện ở thanh bên và trang đăng nhập. PNG, JPG hoặc WebP, tối đa 512 KB.
        </p>
        {canManage && (
          <div className="mt-1 flex gap-2">
            <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => fileInput.current?.click()}>
              {busy ? <Loader2 className="animate-spin" /> : <Upload />} {hasLogo ? "Đổi logo" : "Tải logo lên"}
            </Button>
            {hasLogo && (
              <Button type="button" variant="ghost" size="sm" disabled={busy} onClick={() => setConfirmingDelete(true)}>
                <Trash2 /> Xóa logo
              </Button>
            )}
            <input
              ref={fileInput}
              type="file"
              accept={LOGO_TYPES.join(",")}
              className="hidden"
              onChange={(event) => {
                const file = event.target.files?.[0]
                // Cleared so choosing the same file again still fires a change.
                event.target.value = ""
                if (file) upload(file)
              }}
            />
          </div>
        )}
      </div>

      <AlertDialog open={confirmingDelete} onOpenChange={(open) => { if (!open && !busy) setConfirmingDelete(false) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa logo doanh nghiệp?</AlertDialogTitle>
            <AlertDialogDescription>
              Thanh bên và trang đăng nhập sẽ hiện chữ cái đầu của tên doanh nghiệp thay cho logo.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={busy}>Hủy</AlertDialogCancel>
            <AlertDialogAction variant="destructive" disabled={busy} onClick={(event) => {
              event.preventDefault()
              void change("DELETE")
            }}>
              {busy && <Loader2 className="animate-spin" />} Xóa logo
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

function EditCompanyDialog({ company, onClose, onSaved }: {
  company: CompanyProfile
  onClose: () => void
  onSaved: () => void
}) {
  const [code, setCode] = useState(company.code)
  const [name, setName] = useState(company.name)
  const [taxCode, setTaxCode] = useState(company.taxCode ?? "")
  const [phone, setPhone] = useState(company.phone ?? "")
  const [email, setEmail] = useState(company.email ?? "")
  const [address, setAddress] = useState(company.address ?? "")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await workforceApi("company", "PUT", {
        code: code.trim(),
        name: name.trim(),
        taxCode: taxCode.trim() || null,
        phone: phone.trim() || null,
        email: email.trim() || null,
        address: address.trim() || null,
      } satisfies WorkforceBody["CompanyProfileRequest"])
      toast.success("Đã cập nhật thông tin doanh nghiệp")
      onSaved()
    } catch (caught) {
      setError(apiErrorMessage(caught))
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={submit}>
          <DialogHeader>
            <DialogTitle>Sửa thông tin doanh nghiệp</DialogTitle>
            <DialogDescription>Thay đổi áp dụng cho phiếu lương in từ nay; phiếu đã in không đổi.</DialogDescription>
          </DialogHeader>
          {error && <div className="mt-4"><SectionError error={error} /></div>}
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <div className="grid gap-1.5 sm:col-span-2">
              <Label htmlFor="company-name">Tên doanh nghiệp</Label>
              <Input id="company-name" required maxLength={40} value={name} onChange={(event) => setName(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="company-code">Mã doanh nghiệp</Label>
              <Input id="company-code" required maxLength={20} value={code} onChange={(event) => setCode(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="company-tax-code">Mã số thuế</Label>
              <Input id="company-tax-code" maxLength={30} value={taxCode} onChange={(event) => setTaxCode(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="company-phone">Điện thoại</Label>
              <PhoneInput id="company-phone" value={phone} onChange={(event) => setPhone(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="company-email">Email</Label>
              <Input id="company-email" type="email" maxLength={30} value={email} onChange={(event) => setEmail(event.target.value)} />
            </div>
            <div className="grid gap-1.5 sm:col-span-2">
              <Label htmlFor="company-address">Địa chỉ</Label>
              <Textarea id="company-address" rows={2} value={address} onChange={(event) => setAddress(event.target.value)} />
            </div>
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Hủy</Button>
            <Button type="submit" disabled={busy}>{busy && <Loader2 className="animate-spin" />} Lưu</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
