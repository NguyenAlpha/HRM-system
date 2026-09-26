"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { motion, MotionConfig } from "motion/react"
import {
  ArrowRight,
  Check,
  Copy,
  Eye,
  EyeOff,
  Loader2,
  ShieldCheck,
  TriangleAlert,
  Users,
} from "lucide-react"
import { toast } from "sonner"

import { cn } from "@/lib/utils"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { PORTAL_CONFIG } from "@/lib/auth/config"
import { login } from "@/lib/auth/client"
import { AuthApiError, type Portal } from "@/lib/auth/types"

interface LoginFormProps {
  portal: Portal
}

const THEME = {
  hrm: {
    icon: Users,
    eyebrow: "HRM WORKSPACE",
    title: "Chào mừng trở lại",
    description: "Đăng nhập để quản lý công việc, chấm công, đơn từ và thông tin cá nhân.",
    headline: "Một nơi cho mọi\nnhịp làm việc.",
    story: "Theo dõi công việc và kết nối mọi trải nghiệm nhân sự trong một không gian thống nhất.",
    submitLabel: "Đăng nhập hệ thống",
    alternateLabel: "Bạn là quản trị viên hệ thống?",
    alternateHref: "/admin/login",
    alternateAction: "Đến cổng Admin",
    usernamePlaceholder: "employee@company.vn",
    devHint: null,
    heroBg:
      "bg-[radial-gradient(circle_at_85%_18%,rgba(119,215,173,0.24),transparent_24rem),linear-gradient(145deg,#0c5a3d,#123d30_70%,#102e26)]",
    accentText: "text-emerald-200",
    accentSoft: "text-emerald-300/70",
    button: "bg-[#147a55] hover:bg-[#0d5f42] focus-visible:ring-[#147a55]/25",
    ring: "focus-visible:border-[#147a55] focus-visible:ring-[#147a55]/15",
    link: "text-[#147a55] hover:text-[#0d5f42]",
    blob: "bg-emerald-400/20",
  },
  admin: {
    icon: ShieldCheck,
    eyebrow: "SYSTEM ADMINISTRATION",
    title: "Cổng quản trị",
    description: "Không gian dành riêng cho quản trị tài khoản, vai trò và phân quyền hệ thống.",
    headline: "Kiểm soát hệ thống.\nRõ ràng và an toàn.",
    story: "Quản lý quyền truy cập với một cổng đăng nhập độc lập dành cho quản trị viên.",
    submitLabel: "Đăng nhập Admin",
    alternateLabel: "Bạn là người dùng HRM?",
    alternateHref: "/login",
    alternateAction: "Về cổng nhân viên",
    usernamePlaceholder: "admin",
    devHint: "admin / Admin@123",
    heroBg:
      "bg-[radial-gradient(circle_at_80%_18%,rgba(97,131,195,0.30),transparent_24rem),linear-gradient(145deg,#1a2c4c,#111d33_70%,#0c1526)]",
    accentText: "text-indigo-200",
    accentSoft: "text-indigo-300/70",
    button: "bg-[#263f69] hover:bg-[#1a3157] focus-visible:ring-[#4e6fa9]/25",
    ring: "focus-visible:border-[#4e6fa9] focus-visible:ring-[#4e6fa9]/15",
    link: "text-[#456398] hover:text-[#263f69]",
    blob: "bg-indigo-400/20",
  },
} satisfies Record<Portal, Record<string, unknown>>

const loginSchema = z.object({
  usernameOrEmail: z
    .string()
    .trim()
    .min(1, "Vui lòng nhập tên đăng nhập hoặc email")
    .max(100, "Tối đa 100 ký tự"),
  password: z
    .string()
    .min(1, "Vui lòng nhập mật khẩu")
    .max(100, "Tối đa 100 ký tự"),
})

type LoginValues = z.infer<typeof loginSchema>

export function LoginForm({ portal }: LoginFormProps) {
  const router = useRouter()
  const theme = THEME[portal]
  const HeroIcon = theme.icon
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<AuthApiError | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { usernameOrEmail: "", password: "" },
  })

  async function onSubmit(values: LoginValues) {
    setError(null)
    try {
      const session = await login(portal, values.usernameOrEmail, values.password)
      toast.success("Đăng nhập thành công", {
        description: `Chào mừng trở lại, ${session.account.username}`,
      })
      router.replace(PORTAL_CONFIG[portal].homePath)
      router.refresh()
    } catch (caughtError) {
      setError(
        caughtError instanceof AuthApiError
          ? caughtError
          : new AuthApiError("Đăng nhập không thành công", 0, "UNKNOWN_ERROR"),
      )
    }
  }

  return (
    <MotionConfig reducedMotion="user">
      <main className="grid min-h-svh lg:grid-cols-[minmax(24rem,42%)_1fr]">
        <section
          aria-label="Giới thiệu hệ thống"
          className={cn(
            "relative hidden flex-col justify-between overflow-hidden p-10 text-white lg:flex xl:p-16",
            theme.heroBg,
          )}
        >
          <motion.div
            aria-hidden
            className={cn("absolute -top-16 -right-16 h-72 w-72 rounded-full blur-3xl", theme.blob)}
            animate={{ x: [0, 24, 0], y: [0, 18, 0] }}
            transition={{ duration: 16, repeat: Infinity, ease: "easeInOut" }}
          />
          <motion.div
            aria-hidden
            className={cn("absolute -bottom-24 -left-10 h-56 w-56 rounded-full blur-3xl", theme.blob)}
            animate={{ x: [0, -16, 0], y: [0, -12, 0] }}
            transition={{ duration: 20, repeat: Infinity, ease: "easeInOut", delay: 1 }}
          />
          <div
            aria-hidden
            className="pointer-events-none absolute right-[-9rem] bottom-[-12rem] h-[28rem] w-[28rem] rounded-full border border-white/10 shadow-[0_0_0_4rem_rgba(255,255,255,0.03),0_0_0_8rem_rgba(255,255,255,0.02)]"
          />

          <Link className="brand relative z-10" href={portal === "admin" ? "/admin/login" : "/login"}>
            <span className="brand-mark">H</span>
            <span>
              <strong>HRM</strong>
              <small>{portal === "admin" ? "Admin Console" : "People Workspace"}</small>
            </span>
          </Link>

          <motion.div
            className="relative z-10 max-w-lg"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, ease: "easeOut" }}
          >
            <p className={cn("flex items-center gap-2 text-xs font-extrabold tracking-[0.16em]", theme.accentText)}>
              <HeroIcon className="size-3.5" aria-hidden />
              {theme.eyebrow}
            </p>
            <h1 className="mt-5 whitespace-pre-line text-5xl leading-[0.98] font-semibold tracking-tight xl:text-6xl">
              {theme.headline}
            </h1>
            <p className="mt-6 max-w-md text-[1.05rem] leading-[1.75] text-white/75">{theme.story}</p>
          </motion.div>

          <p className="relative z-10 text-xs tracking-[0.08em] text-white/50">
            HRM Platform · Internal access only
          </p>
        </section>

        <section className="flex min-h-svh items-center justify-center bg-[#fbfcfb] px-6 py-12 sm:px-10">
          <motion.div
            className="w-full max-w-sm"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: "easeOut" }}
          >
            <div className="brand mb-10 lg:hidden">
              <span className="brand-mark">H</span>
              <strong>HRM</strong>
            </div>

            <p className="eyebrow">{theme.eyebrow}</p>
            <h2 className="mt-2 text-3xl font-semibold tracking-tight sm:text-4xl">{theme.title}</h2>
            <p className="mt-2 text-sm leading-relaxed text-[var(--muted-ink)]">{theme.description}</p>

            {error && (
              <Alert variant="destructive" className="mt-6 animate-in fade-in slide-in-from-top-1">
                <TriangleAlert />
                <AlertTitle className="text-xs tracking-wide">{error.code}</AlertTitle>
                <AlertDescription>
                  {error.message}
                  {error.code === "ADMIN_PORTAL_REQUIRED" && (
                    <Link className="mt-1 block font-semibold underline" href="/admin/login">
                      Đăng nhập tại cổng Admin
                    </Link>
                  )}
                  {error.code === "ADMIN_ACCESS_REQUIRED" && (
                    <Link className="mt-1 block font-semibold underline" href="/login">
                      Đăng nhập tại cổng HRM
                    </Link>
                  )}
                </AlertDescription>
              </Alert>
            )}

            <form className="mt-8 grid gap-5" onSubmit={handleSubmit(onSubmit)} noValidate>
              <div className="grid gap-2">
                <Label htmlFor={`${portal}-username`}>Tên đăng nhập hoặc email</Label>
                <Input
                  id={`${portal}-username`}
                  type="text"
                  autoComplete="username"
                  autoFocus
                  placeholder={theme.usernamePlaceholder}
                  aria-invalid={!!errors.usernameOrEmail}
                  className={cn("h-11 px-3.5", theme.ring)}
                  {...register("usernameOrEmail")}
                />
                {errors.usernameOrEmail && (
                  <p className="text-xs font-medium text-destructive animate-in fade-in">
                    {errors.usernameOrEmail.message}
                  </p>
                )}
              </div>

              <div className="grid gap-2">
                <Label htmlFor={`${portal}-password`}>Mật khẩu</Label>
                <div className="relative">
                  <Input
                    id={`${portal}-password`}
                    type={showPassword ? "text" : "password"}
                    autoComplete="current-password"
                    placeholder="Nhập mật khẩu"
                    aria-invalid={!!errors.password}
                    className={cn("h-11 px-3.5 pr-11", theme.ring)}
                    {...register("password")}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((visible) => !visible)}
                    aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                    className="absolute top-1/2 right-2.5 -translate-y-1/2 rounded-md p-1.5 text-[var(--muted-ink)] transition-colors hover:text-foreground"
                  >
                    {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                  </button>
                </div>
                {errors.password && (
                  <p className="text-xs font-medium text-destructive animate-in fade-in">
                    {errors.password.message}
                  </p>
                )}
              </div>

              <Button
                type="submit"
                disabled={isSubmitting}
                className={cn("mt-1 h-11 w-full text-white", theme.button)}
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="size-4 animate-spin" />
                    Đang xác thực...
                  </>
                ) : (
                  <>
                    {theme.submitLabel}
                    <ArrowRight className="size-4" />
                  </>
                )}
              </Button>
            </form>

            {theme.devHint && process.env.NODE_ENV === "development" && (
              <DevCredentials value={theme.devHint} />
            )}

            <p className="mt-8 text-center text-sm text-[var(--muted-ink)]">
              {theme.alternateLabel}{" "}
              <Link className={cn("font-semibold", theme.link)} href={theme.alternateHref}>
                {theme.alternateAction}
              </Link>
            </p>
          </motion.div>
        </section>
      </main>
    </MotionConfig>
  )
}

function DevCredentials({ value }: { value: string }) {
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
      toast.success("Đã sao chép thông tin đăng nhập")
      setTimeout(() => setCopied(false), 1500)
    } catch {
      toast.error("Không thể sao chép, hãy tự nhập")
    }
  }

  return (
    <div className="mt-6 flex flex-col items-start gap-2 rounded-xl bg-[#f0f3f8] px-3.5 py-2.5 text-xs sm:flex-row sm:items-center sm:justify-between">
      <span className="text-[#68738a]">
        Development seed · <code className="font-semibold text-[#253957]">{value}</code>
      </span>
      <button
        type="button"
        onClick={handleCopy}
        aria-label="Sao chép thông tin đăng nhập"
        className="shrink-0 rounded-md p-1.5 text-[#68738a] transition-colors hover:bg-white hover:text-[#253957]"
      >
        {copied ? <Check className="size-3.5" /> : <Copy className="size-3.5" />}
      </button>
    </div>
  )
}
