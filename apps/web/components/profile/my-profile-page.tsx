"use client"

import { useRouter } from "next/navigation"
import { FormEvent, useEffect, useState } from "react"
import { GraduationCap, KeyRound, Loader2, PencilLine } from "lucide-react"
import { toast } from "sonner"

import { PhoneInput } from "@/components/common/phone-input"
import { InfoField } from "@/components/employee/employee-detail-page"
import { formatDate } from "@/lib/format-date"
import { PageHeader } from "@/components/portal/page-header"
import { roleScope } from "@/components/portal/portal-dashboard"
import { usePortalSession } from "@/components/portal/portal-shell"
import { Badge } from "@/components/ui/badge"
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { SectionError } from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import { changePassword } from "@/lib/auth/client"
import { PORTAL_CONFIG } from "@/lib/auth/config"
import {
  EDUCATION_LEVEL_LABELS,
  EDUCATION_LEVELS,
  EMPLOYMENT_STATUS_LABELS,
  EMPLOYMENT_TYPE_LABELS,
  GENDER_LABELS,
  employeeRequest,
  type EducationLevel,
  type EmployeeDetail,
  type SelfProfile,
  type SelfProfileInput,
} from "@/lib/employee"

/** The signed-in employee's own record (contact details and education self-editable), plus their sign-in account and password change. */
export function MyProfilePage() {
  const { session } = usePortalSession()
  const { account } = session
  const canRead = !!account.employee && account.permissions.some((permission) => permission.code === "profile.self.read")
  const canEdit = account.permissions.some((permission) => permission.code === "profile.self.update")
  const [detail, setDetail] = useState<EmployeeDetail | null>(null)
  const [profile, setProfile] = useState<SelfProfile | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [changingPassword, setChangingPassword] = useState(false)
  const [editing, setEditing] = useState(false)
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    if (!canRead) return
    let active = true
    Promise.all([
      employeeRequest<EmployeeDetail>("hrm", "/me"),
      employeeRequest<SelfProfile>("hrm", "/me/profile"),
    ])
      .then(([nextDetail, nextProfile]) => {
        if (!active) return
        setDetail(nextDetail)
        setProfile(nextProfile)
      })
      .catch((caught: unknown) => { if (active) setError(apiErrorMessage(caught)) })
    return () => { active = false }
  }, [canRead, revision])

  const assignment = detail?.currentAssignment

  return (
    <>
      <PageHeader
        title="Hồ sơ của tôi"
        description="Thông tin nhân sự công ty đang lưu về bạn. Bạn tự sửa được liên hệ và học vấn; thông tin khác hãy liên hệ phòng Nhân sự."
        actions={(
          <div className="flex flex-wrap gap-2">
            {canEdit && profile && (
              <Button type="button" variant="outline" onClick={() => setEditing(true)}><PencilLine /> Sửa thông tin</Button>
            )}
            <Button type="button" variant="outline" onClick={() => setChangingPassword(true)}><KeyRound /> Đổi mật khẩu</Button>
          </div>
        )}
      />

      <div className="mt-6 grid gap-4">
        {!canRead && <p role="alert">Tài khoản chưa gắn với hồ sơ nhân viên.</p>}
        <SectionError error={error} />
        {canRead && !detail && !error && <Skeleton className="h-64 w-full rounded-xl" />}

        {detail && (
          <div className="grid gap-4 lg:grid-cols-3">
            <Card className="lg:col-span-2">
              <CardHeader>
                <CardTitle>{detail.fullName}</CardTitle>
                <CardDescription>{detail.employeeCode} · {assignment?.positionTitle ?? "Chưa có chức danh"}</CardDescription>
                <CardAction><Badge variant="secondary">{EMPLOYMENT_STATUS_LABELS[detail.employmentStatus]}</Badge></CardAction>
              </CardHeader>
              <CardContent className="space-y-5">
                <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  <InfoField label="Ngày sinh" value={formatDate(detail.dateOfBirth)} />
                  <InfoField label="Giới tính" value={detail.gender ? GENDER_LABELS[detail.gender] : null} />
                  <InfoField label="Điện thoại" value={detail.phone} />
                  <InfoField label="Email công việc" value={detail.workEmail} />
                  <InfoField label="Email cá nhân" value={profile?.personalEmail} />
                  <InfoField label="Ngày vào làm" value={formatDate(detail.hireDate)} />
                  <div className="sm:col-span-2 lg:col-span-3">
                    <InfoField label="Địa chỉ" value={profile?.address} />
                  </div>
                </dl>
                <Separator />
                <div>
                  <p className="flex items-center gap-2 text-sm font-medium"><GraduationCap /> Học vấn</p>
                  <dl className="mt-3 grid gap-4 sm:grid-cols-2">
                    <InfoField
                      label="Trình độ cao nhất"
                      value={detail.highestEducationLevel ? EDUCATION_LEVEL_LABELS[detail.highestEducationLevel] : null}
                    />
                    <InfoField label="Năm tốt nghiệp" value={detail.graduationYear ? String(detail.graduationYear) : null} />
                    <InfoField label="Chuyên ngành" value={detail.major} />
                    <InfoField label="Trường / đơn vị đào tạo" value={detail.institution} />
                  </dl>
                </div>
              </CardContent>
            </Card>

            <div className="grid content-start gap-4">
              <Card>
                <CardHeader>
                  <CardTitle>Công việc</CardTitle>
                </CardHeader>
                <CardContent>
                  {assignment
                    ? (
                      <dl className="grid gap-4">
                        <InfoField label="Đơn vị" value={assignment.organizationUnitName} />
                        <InfoField label="Chức danh" value={assignment.positionTitle} />
                        <InfoField label="Quản lý trực tiếp" value={assignment.managerEmployeeName} />
                        <InfoField label="Địa điểm" value={assignment.workLocationName} />
                        <InfoField label="Ca làm việc" value={assignment.shiftName} />
                        <InfoField label="Loại hợp đồng" value={EMPLOYMENT_TYPE_LABELS[assignment.employmentType]} />
                      </dl>
                    )
                    : <p className="text-sm text-muted-foreground">Chưa có phân công hiện tại.</p>}
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Tài khoản</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-4">
                  <dl className="grid gap-4">
                    <InfoField label="Tên đăng nhập" value={account.username} />
                    <InfoField label="Email đăng nhập" value={account.email} />
                  </dl>
                  <div>
                    <p className="text-xs text-muted-foreground">Vai trò</p>
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {account.roles.map((role) => (
                        <Badge key={`${role.code}:${role.scopeType}:${role.organizationUnitId ?? role.workLocationId ?? "company"}`}
                          variant="outline">
                          {role.name} · {roleScope(role)}
                        </Badge>
                      ))}
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>
          </div>
        )}
      </div>

      {changingPassword && <ChangePasswordDialog onClose={() => setChangingPassword(false)} />}
      {editing && profile && (
        <EditProfileDialog profile={profile} onClose={() => setEditing(false)}
          onSaved={() => { setEditing(false); setRevision((value) => value + 1) }} />
      )}
    </>
  )
}

function ChangePasswordDialog({ onClose }: { onClose: () => void }) {
  const router = useRouter()
  const { portal } = usePortalSession()
  const [currentPassword, setCurrentPassword] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (newPassword !== confirmPassword) {
      setError("Mật khẩu xác nhận không khớp.")
      return
    }
    setBusy(true)
    setError(null)
    try {
      await changePassword(portal, currentPassword, newPassword)
      router.replace(PORTAL_CONFIG[portal].loginPath)
      router.refresh()
    } catch (caught) {
      setError(apiErrorMessage(caught))
      setBusy(false)
    }
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open && !busy) onClose() }}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={submit}>
          <DialogHeader>
            <DialogTitle>Đổi mật khẩu</DialogTitle>
            <DialogDescription>Sau khi đổi, mọi phiên đăng nhập bị đóng và bạn đăng nhập lại bằng mật khẩu mới.</DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="current-password">Mật khẩu hiện tại</Label>
              <Input id="current-password" type="password" autoComplete="current-password" required
                value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="new-password">Mật khẩu mới</Label>
              <Input id="new-password" type="password" autoComplete="new-password" minLength={8} maxLength={100} required
                value={newPassword} onChange={(event) => setNewPassword(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="confirm-password">Xác nhận mật khẩu mới</Label>
              <Input id="confirm-password" type="password" autoComplete="new-password" minLength={8} maxLength={100} required
                value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} />
            </div>
            {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
          </div>
          <DialogFooter className="mt-4">
            <Button type="button" variant="outline" onClick={onClose} disabled={busy}>Hủy</Button>
            <Button type="submit" disabled={busy}>{busy && <Loader2 className="animate-spin" />} Đổi mật khẩu</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

const EDUCATION_NONE = "NONE"
const EDUCATION_ITEMS: Record<string, string> = { [EDUCATION_NONE]: "Không chọn", ...EDUCATION_LEVEL_LABELS }

/** Only contact details and education; name, identity, tax, bank, pay and job stay with HR. */
function EditProfileDialog({ profile, onClose, onSaved }: {
  profile: SelfProfile
  onClose: () => void
  onSaved: () => void
}) {
  const [phone, setPhone] = useState(profile.phone ?? "")
  const [personalEmail, setPersonalEmail] = useState(profile.personalEmail ?? "")
  const [address, setAddress] = useState(profile.address ?? "")
  const [education, setEducation] = useState<string>(profile.highestEducationLevel ?? EDUCATION_NONE)
  const [graduationYear, setGraduationYear] = useState(profile.graduationYear ? String(profile.graduationYear) : "")
  const [major, setMajor] = useState(profile.major ?? "")
  const [institution, setInstitution] = useState(profile.institution ?? "")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    const body = {
      phone: phone.trim() || null,
      personalEmail: personalEmail.trim() || null,
      address: address.trim() || null,
      highestEducationLevel: education === EDUCATION_NONE ? null : education as EducationLevel,
      graduationYear: graduationYear ? Number(graduationYear) : null,
      major: major.trim() || null,
      institution: institution.trim() || null,
    } satisfies SelfProfileInput
    try {
      await employeeRequest("hrm", "/me/profile", { method: "PUT", body: JSON.stringify(body) })
      toast.success("Đã cập nhật hồ sơ")
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
            <DialogTitle>Sửa thông tin cá nhân</DialogTitle>
            <DialogDescription>Họ tên, ngày sinh, giấy tờ, lương và chức danh do phòng Nhân sự cập nhật.</DialogDescription>
          </DialogHeader>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <div className="grid gap-1.5">
              <Label htmlFor="self-phone">Điện thoại</Label>
              <PhoneInput id="self-phone" value={phone} onChange={(event) => setPhone(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="self-email">Email cá nhân</Label>
              <Input id="self-email" type="email" maxLength={30} value={personalEmail}
                onChange={(event) => setPersonalEmail(event.target.value)} />
            </div>
            <div className="grid gap-1.5 sm:col-span-2">
              <Label htmlFor="self-address">Địa chỉ</Label>
              <Input id="self-address" value={address} onChange={(event) => setAddress(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="self-education">Trình độ cao nhất</Label>
              <Select value={education} items={EDUCATION_ITEMS}
                onValueChange={(value) => setEducation(String(value ?? EDUCATION_NONE))}>
                <SelectTrigger id="self-education" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {[EDUCATION_NONE, ...EDUCATION_LEVELS].map((level) => (
                    <SelectItem key={level} value={level}>{EDUCATION_ITEMS[level]}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="self-graduation">Năm tốt nghiệp</Label>
              <Input id="self-graduation" type="number" min={1950} max={new Date().getFullYear()} value={graduationYear}
                onChange={(event) => setGraduationYear(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="self-major">Chuyên ngành</Label>
              <Input id="self-major" maxLength={30} value={major} onChange={(event) => setMajor(event.target.value)} />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="self-institution">Trường / đơn vị đào tạo</Label>
              <Input id="self-institution" maxLength={60} value={institution}
                onChange={(event) => setInstitution(event.target.value)} />
            </div>
            {error && <p role="alert" className="text-sm text-destructive sm:col-span-2">{error}</p>}
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
