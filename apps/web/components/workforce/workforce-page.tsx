"use client"

import Link from "next/link"
import { useEffect, useMemo, useState } from "react"
import { useRouter } from "next/navigation"

import { PortalSidebar } from "@/components/auth/portal-sidebar"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { AttendanceSection } from "@/components/workforce/attendance-section"
import { LeaveSection } from "@/components/workforce/leave-section"
import { PayrollSection } from "@/components/workforce/payroll-section"
import { today, type WorkforceContext } from "@/components/workforce/workforce-shared"
import { apiErrorMessage } from "@/lib/api-helpers"
import { getCurrentSession } from "@/lib/auth/client"
import { AuthApiError, type SessionData } from "@/lib/auth/types"

type Section = "attendance" | "leave" | "payroll"

const SECTION_TEXT: Record<Section, { title: string; description: string }> = {
  attendance: { title: "Chấm công", description: "Vào ca, ra ca, bảng công tháng, tăng ca và ngày lễ công ty." },
  leave: { title: "Đơn nghỉ phép", description: "Số dư phép năm, tạo và duyệt đơn nghỉ, chốt phép cuối năm." },
  payroll: { title: "Phiếu lương", description: "Kỳ lương, phiếu lương, lương cơ bản, bảo hiểm và người phụ thuộc." },
}

/** Permissions that let the user look at someone other than themselves in each section. */
const OTHERS_PERMISSIONS: Record<Section, string[]> = {
  attendance: ["attendance.read", "attendance.manage"],
  leave: ["request.read", "request.manage"],
  payroll: ["report.payroll.read", "compensation.read", "compensation.manage"],
}

export function WorkforcePage({ section }: { section: Section }) {
  const router = useRouter()
  const [session, setSession] = useState<SessionData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [employeeInput, setEmployeeInput] = useState("")
  const [month, setMonth] = useState(today().slice(0, 7))

  useEffect(() => {
    let active = true
    getCurrentSession("hrm")
      .then((value) => {
        if (!active) return
        setSession(value)
        // Employee pages link here with ?employeeId=; otherwise start with the signed-in employee.
        const requested = new URLSearchParams(window.location.search).get("employeeId")
        if (requested && /^[1-9]\d*$/.test(requested)) setEmployeeInput(requested)
        else if (value.account.employee) setEmployeeInput(String(value.account.employee.id))
      })
      .catch((caught: unknown) => {
        if (!active) return
        if (caught instanceof AuthApiError && caught.status === 401) router.replace("/login")
        setError(apiErrorMessage(caught))
      })
    return () => { active = false }
  }, [router])

  const permissions = useMemo(
    () => new Set(session?.account.permissions.map((permission) => permission.code) ?? []),
    [session],
  )

  if (!session) {
    return (
      <main className="dashboard-loading">
        <p role={error ? "alert" : "status"}>{error ?? "Đang tải phiên đăng nhập..."}</p>
        {error && <Link href="/dashboard">Về trang tổng quan</Link>}
      </main>
    )
  }

  const canUseSection = section === "attendance"
    ? ["attendance.self.read", "attendance.read", "attendance.manage"].some((code) => permissions.has(code))
    : section === "leave"
      ? ["request.self.read", "request.read", "request.approve", "request.self.create"].some((code) => permissions.has(code))
      : ["payroll.self.read", "report.payroll.read", "payroll.calculate", "compensation.read", "compensation.manage"]
        .some((code) => permissions.has(code))
  const canViewOthers = OTHERS_PERMISSIONS[section].some((code) => permissions.has(code))
  const employeeId = /^[1-9]\d*$/.test(employeeInput) ? Number(employeeInput) : 0
  const context: WorkforceContext = { employeeId, permissions, month }
  const { title, description } = SECTION_TEXT[section]

  return (
    <main className="portal-shell hrm-shell">
      <PortalSidebar portal="hrm" account={session.account} active={section} />
      <section className="portal-content">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow">HRM WORKSPACE</p>
            <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">{title}</h1>
            <p className="mt-1.5 max-w-xl text-[var(--muted-ink)]">{description}</p>
          </div>
          {canUseSection && (
            <div className="flex flex-wrap items-end gap-3">
              <div className="grid gap-1.5">
                <Label htmlFor="workforce-employee">Mã nhân viên (ID)</Label>
                <Input id="workforce-employee" type="number" min="1" className="w-36" value={employeeInput}
                  disabled={!canViewOthers} onChange={(event) => setEmployeeInput(event.target.value)} />
              </div>
              {section !== "payroll" && (
                <div className="grid gap-1.5">
                  <Label htmlFor="workforce-month">{section === "leave" ? "Năm phép (theo tháng)" : "Tháng"}</Label>
                  <Input id="workforce-month" type="month" className="w-44" value={month}
                    onChange={(event) => { if (event.target.value) setMonth(event.target.value) }} />
                </div>
              )}
            </div>
          )}
        </header>

        <div className="mt-6">
          {!canUseSection && <p role="alert">Tài khoản chưa có quyền truy cập mục này.</p>}
          {canUseSection && section === "attendance" && <AttendanceSection {...context} />}
          {canUseSection && section === "leave" && <LeaveSection {...context} />}
          {canUseSection && section === "payroll" && <PayrollSection {...context} />}
        </div>
      </section>
    </main>
  )
}
