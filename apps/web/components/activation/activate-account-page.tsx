"use client"

import Link from "next/link"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { CheckCircle2, Eye, EyeOff, Loader2, TriangleAlert } from "lucide-react"

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { completeAccountActivation } from "@/lib/activation"
import { AuthApiError } from "@/lib/auth/types"

const activateSchema = z
  .object({
    token: z.string().trim().min(1, "Bắt buộc"),
    password: z.string().min(8, "Tối thiểu 8 ký tự").max(100, "Tối đa 100 ký tự"),
    passwordConfirmation: z.string().min(1, "Bắt buộc"),
  })
  .refine((data) => data.password === data.passwordConfirmation, {
    message: "Mật khẩu xác nhận không khớp",
    path: ["passwordConfirmation"],
  })

type ActivateValues = z.infer<typeof activateSchema>

export function ActivateAccountPage({ initialToken }: { initialToken: string }) {
  const [error, setError] = useState<AuthApiError | null>(null)
  const [success, setSuccess] = useState(false)
  const [showPassword, setShowPassword] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ActivateValues>({
    resolver: zodResolver(activateSchema),
    defaultValues: { token: initialToken, password: "", passwordConfirmation: "" },
  })

  async function onSubmit(values: ActivateValues) {
    setError(null)
    try {
      await completeAccountActivation(values.token.trim(), values.password, values.passwordConfirmation)
      setSuccess(true)
    } catch (caught) {
      setError(
        caught instanceof AuthApiError
          ? caught
          : new AuthApiError("Không thể kích hoạt tài khoản", 0, "UNKNOWN_ERROR"),
      )
    }
  }

  return (
    <main className="flex min-h-svh items-center justify-center bg-[#fbfcfb] px-6 py-12">
      <div className="w-full max-w-sm">
        <Link className="brand mb-8 justify-center" href="/login">
          <span className="brand-mark">H</span>
          <span>
            <strong>HRM</strong>
            <small>People Workspace</small>
          </span>
        </Link>

        <Card>
          {success ? (
            <>
              <CardHeader className="items-center text-center">
                <CheckCircle2 className="size-10 text-[#147a55]" />
                <CardTitle className="mt-2">Kích hoạt thành công</CardTitle>
                <CardDescription>Mật khẩu đã được đặt. Bạn có thể đăng nhập ngay bây giờ.</CardDescription>
              </CardHeader>
              <CardContent>
                <Button type="button" className="w-full bg-[#147a55] text-white hover:bg-[#0d5f42]" render={<Link href="/login" />}>
                  Đến trang đăng nhập
                </Button>
              </CardContent>
            </>
          ) : (
            <>
              <CardHeader>
                <CardTitle>Kích hoạt tài khoản</CardTitle>
                <CardDescription>Nhập mã kích hoạt đã được cấp và đặt mật khẩu để hoàn tất.</CardDescription>
              </CardHeader>
              <CardContent>
                <form className="grid gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
                  {error && (
                    <Alert variant="destructive" className="animate-in fade-in slide-in-from-top-1">
                      <TriangleAlert />
                      <AlertTitle className="text-xs tracking-wide">{error.code}</AlertTitle>
                      <AlertDescription>{error.message}</AlertDescription>
                    </Alert>
                  )}

                  <div className="grid gap-1.5">
                    <Label htmlFor="activate-token">Mã kích hoạt</Label>
                    <Input
                      id="activate-token"
                      autoComplete="off"
                      aria-invalid={!!errors.token}
                      {...register("token")}
                    />
                    {errors.token && <p className="text-xs font-medium text-destructive">{errors.token.message}</p>}
                  </div>

                  <div className="grid gap-1.5">
                    <Label htmlFor="activate-password">Mật khẩu mới</Label>
                    <div className="relative">
                      <Input
                        id="activate-password"
                        type={showPassword ? "text" : "password"}
                        autoComplete="new-password"
                        className="pr-11"
                        aria-invalid={!!errors.password}
                        {...register("password")}
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword((visible) => !visible)}
                        aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                        className="absolute top-1/2 right-2.5 -translate-y-1/2 rounded-md p-1.5 text-muted-foreground transition-colors hover:text-foreground"
                      >
                        {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                      </button>
                    </div>
                    {errors.password && <p className="text-xs font-medium text-destructive">{errors.password.message}</p>}
                  </div>

                  <div className="grid gap-1.5">
                    <Label htmlFor="activate-password-confirm">Xác nhận mật khẩu</Label>
                    <Input
                      id="activate-password-confirm"
                      type={showPassword ? "text" : "password"}
                      autoComplete="new-password"
                      aria-invalid={!!errors.passwordConfirmation}
                      {...register("passwordConfirmation")}
                    />
                    {errors.passwordConfirmation && (
                      <p className="text-xs font-medium text-destructive">{errors.passwordConfirmation.message}</p>
                    )}
                  </div>

                  <Button type="submit" className="h-11 bg-[#147a55] text-white hover:bg-[#0d5f42]" disabled={isSubmitting}>
                    {isSubmitting && <Loader2 className="animate-spin" />}
                    Kích hoạt tài khoản
                  </Button>
                </form>
              </CardContent>
            </>
          )}
        </Card>

        <p className="mt-6 text-center text-sm text-[var(--muted-ink)]">
          Đã có tài khoản? <Link className="font-semibold text-[#147a55] hover:text-[#0d5f42]" href="/login">Đăng nhập</Link>
        </p>
      </div>
    </main>
  )
}
