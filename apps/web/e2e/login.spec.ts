import { expect, test } from "@playwright/test"

import { ADMIN, EMPLOYEE, signIn } from "./auth"

test.describe("Đăng nhập HRM (/login)", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login")
  })

  test("bỏ trống cả hai ô thì báo lỗi và không gọi API", async ({ page }) => {
    let loginCalled = false
    page.on("request", (request) => {
      if (request.url().includes("/api/session/login")) loginCalled = true
    })

    await page.getByRole("button", { name: "Đăng nhập hệ thống" }).click()

    await expect(page.getByText("Vui lòng nhập tên đăng nhập hoặc email")).toBeVisible()
    await expect(page.getByText("Vui lòng nhập mật khẩu")).toBeVisible()
    await expect(page).toHaveURL(/\/login$/)
    expect(loginCalled).toBe(false)
  })

  test("nút con mắt hiện và ẩn mật khẩu", async ({ page }) => {
    const password = page.getByLabel("Mật khẩu", { exact: true })
    await password.fill("bi-mat")
    await expect(password).toHaveAttribute("type", "password")

    await page.getByRole("button", { name: "Hiện mật khẩu" }).click()
    await expect(password).toHaveAttribute("type", "text")

    await page.getByRole("button", { name: "Ẩn mật khẩu" }).click()
    await expect(password).toHaveAttribute("type", "password")
  })

  test("sai tài khoản thì báo lỗi tiếng Việt và ở lại trang", async ({ page }) => {
    // A fresh unknown name each run, so the wrong-password limit never hits a real account.
    await signIn(page, `e2e-unknown-${Date.now()}`, "Sai@12345")

    await expect(page.getByText("Không thể đăng nhập")).toBeVisible()
    await expect(page.getByText("Tên đăng nhập hoặc mật khẩu không đúng.")).toBeVisible()
    await expect(page).toHaveURL(/\/login$/)
  })

  test("tài khoản admin bị chặn và được chỉ sang cổng Admin", async ({ page }) => {
    await signIn(page, ADMIN.username, ADMIN.password)

    const adminLink = page.getByRole("link", { name: "Đăng nhập tại cổng Admin" })
    await expect(adminLink).toBeVisible()
    await adminLink.click()
    await expect(page).toHaveURL(/\/admin\/login$/)
  })

  test("tài khoản HRM hợp lệ vào được dashboard", async ({ page }) => {
    await signIn(page, EMPLOYEE.username, EMPLOYEE.password)

    await expect(page).toHaveURL(/\/dashboard$/)
  })
})

test.describe("Đăng nhập Admin (/admin/login)", () => {
  test("admin đăng nhập thành công rồi vào được trang đã bảo vệ", async ({ page }) => {
    await page.goto("/admin/login")
    await signIn(page, ADMIN.username, ADMIN.password)

    await expect(page).toHaveURL(/\/admin$/)

    // The session survives a reload instead of bouncing back to the login page.
    await page.reload()
    await expect(page).toHaveURL(/\/admin$/)
  })

  test("chưa đăng nhập mà mở trang Admin thì bị đưa về trang đăng nhập", async ({ page }) => {
    await page.goto("/admin/accounts")
    await expect(page).toHaveURL(/\/admin\/login/)
  })
})
