"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { FormEvent, useState } from "react"

import { PORTAL_CONFIG } from "@/lib/auth/config"
import { login } from "@/lib/auth/client"
import { AuthApiError, type Portal } from "@/lib/auth/types"

interface LoginFormProps {
  portal: Portal
}

const CONTENT = {
  hrm: {
    eyebrow: "HRM WORKSPACE",
    title: "Chào mừng trở lại",
    description: "Đăng nhập để quản lý công việc, chấm công, đơn từ và thông tin cá nhân.",
    submitLabel: "Đăng nhập hệ thống",
    alternateLabel: "Bạn là quản trị viên hệ thống?",
    alternateHref: "/admin/login",
    alternateAction: "Đến cổng Admin",
  },
  admin: {
    eyebrow: "SYSTEM ADMINISTRATION",
    title: "Cổng quản trị",
    description: "Không gian dành riêng cho quản trị tài khoản, vai trò và phân quyền hệ thống.",
    submitLabel: "Đăng nhập Admin",
    alternateLabel: "Bạn là người dùng HRM?",
    alternateHref: "/login",
    alternateAction: "Về cổng nhân viên",
  },
} satisfies Record<Portal, Record<string, string>>

export function LoginForm({ portal }: LoginFormProps) {
  const router = useRouter()
  const content = CONTENT[portal]
  const [usernameOrEmail, setUsernameOrEmail] = useState("")
  const [password, setPassword] = useState("")
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<AuthApiError | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)

    try {
      await login(portal, usernameOrEmail.trim(), password)
      router.replace(PORTAL_CONFIG[portal].homePath)
      router.refresh()
    } catch (caughtError) {
      setError(
        caughtError instanceof AuthApiError
          ? caughtError
          : new AuthApiError("Đăng nhập không thành công", 0, "UNKNOWN_ERROR"),
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className={`auth-page ${portal === "admin" ? "admin-auth" : "hrm-auth"}`}>
      <section className="auth-story" aria-label="Giới thiệu hệ thống">
        <Link className="brand" href={portal === "admin" ? "/admin/login" : "/login"}>
          <span className="brand-mark">H</span>
          <span>
            <strong>HRM</strong>
            <small>{portal === "admin" ? "Admin Console" : "People Workspace"}</small>
          </span>
        </Link>

        <div className="story-copy">
          <p className="eyebrow">{content.eyebrow}</p>
          <h1>{portal === "admin" ? "Kiểm soát hệ thống.\nRõ ràng và an toàn." : "Một nơi cho mọi\nnhịp làm việc."}</h1>
          <p>
            {portal === "admin"
              ? "Quản lý quyền truy cập với một cổng đăng nhập độc lập dành cho quản trị viên."
              : "Theo dõi công việc và kết nối mọi trải nghiệm nhân sự trong một không gian thống nhất."}
          </p>
        </div>

        <p className="story-footer">HRM Platform · Internal access only</p>
      </section>

      <section className="auth-panel">
        <div className="login-card">
          <div className="mobile-brand">
            <span className="brand-mark">H</span>
            <strong>HRM</strong>
          </div>
          <p className="eyebrow">{content.eyebrow}</p>
          <h2>{content.title}</h2>
          <p className="form-intro">{content.description}</p>

          {error && (
            <div className="form-alert" role="alert">
              <strong>{error.code}</strong>
              <span>{error.message}</span>
              {error.code === "ADMIN_PORTAL_REQUIRED" && (
                <Link href="/admin/login">Đăng nhập tại cổng Admin</Link>
              )}
              {error.code === "ADMIN_ACCESS_REQUIRED" && (
                <Link href="/login">Đăng nhập tại cổng HRM</Link>
              )}
            </div>
          )}

          <form className="auth-form" onSubmit={handleSubmit}>
            <label htmlFor={`${portal}-username`}>
              Tên đăng nhập hoặc email
              <input
                id={`${portal}-username`}
                name="usernameOrEmail"
                type="text"
                autoComplete="username"
                maxLength={100}
                placeholder={portal === "admin" ? "admin" : "employee@company.vn"}
                value={usernameOrEmail}
                onChange={(event) => setUsernameOrEmail(event.target.value)}
                required
                autoFocus
              />
            </label>

            <label htmlFor={`${portal}-password`}>
              Mật khẩu
              <span className="password-field">
                <input
                  id={`${portal}-password`}
                  name="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  maxLength={100}
                  placeholder="Nhập mật khẩu"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  required
                />
                <button
                  className="password-toggle"
                  type="button"
                  onClick={() => setShowPassword((visible) => !visible)}
                  aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                >
                  {showPassword ? "Ẩn" : "Hiện"}
                </button>
              </span>
            </label>

            <button className="submit-button" type="submit" disabled={submitting}>
              {submitting ? "Đang xác thực..." : content.submitLabel}
              <span aria-hidden="true">→</span>
            </button>
          </form>

          {portal === "admin" && process.env.NODE_ENV === "development" && (
            <div className="dev-credentials">
              <span>Development seed</span>
              <code>admin / Admin@123</code>
            </div>
          )}

          <p className="alternate-portal">
            {content.alternateLabel} <Link href={content.alternateHref}>{content.alternateAction}</Link>
          </p>
        </div>
      </section>
    </main>
  )
}
