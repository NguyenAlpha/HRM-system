import { expect, test } from "@playwright/test"

import { EMPLOYEE, HR, signIn } from "./auth"

// The checks the BFF makes before anything reaches the API (docs/AUTH.md).

test("tài khoản thường bị chặn ở cổng Admin và được chỉ sang cổng HRM", async ({ page }) => {
  await page.goto("/admin/login")
  await signIn(page, EMPLOYEE.username, EMPLOYEE.password)

  const hrmLink = page.getByRole("link", { name: "Đăng nhập tại cổng HRM" })
  await expect(hrmLink).toBeVisible()
  await expect(page).toHaveURL(/\/admin\/login$/)
})

test("mọi request ghi gửi từ trang khác bị chặn 403", async ({ request }) => {
  // An account that does not exist: the request must stop at the Origin check, before any sign-in happens.
  const body = { usernameOrEmail: "origin-probe", password: "x", currentPassword: "x", newPassword: "y" }
  const paths = [
    ...["session", "admin-session"].flatMap((prefix) =>
      ["login", "refresh", "logout", "change-password"].map((action) => `/api/${prefix}/${action}`)),
    "/api/session/workforce/leave-requests",
    "/api/account-activations/some-token/complete",
  ]
  for (const path of paths) {
    const response = await request.post(path, { headers: { Origin: "https://evil.example" }, data: body })
    expect(response.status(), path).toBe(403)
    expect((await response.json()).error.code, path).toBe("FORBIDDEN")
  }
})

test("đăng nhập chỉ nhận body JSON", async ({ request }) => {
  // What an HTML form on another site can send when the browser leaves Origin out.
  const response = await request.post("/api/session/login", {
    headers: { "Content-Type": "text/plain" },
    data: JSON.stringify({ usernameOrEmail: EMPLOYEE.username, password: EMPLOYEE.password }),
  })
  expect(response.status()).toBe(400)
  expect(response.headers()["set-cookie"]).toBeUndefined()
})

test("đường dẫn ngoài whitelist trả 404, method không cho phép trả 405", async ({ request }) => {
  expect((await request.get("/api/session/workforce/not-a-route")).status()).toBe(404)
  expect((await request.delete("/api/session/workforce/leave-requests")).status()).toBe(405)
  expect((await request.get("/api/session/rbac/not-a-route")).status()).toBe(404)
})

// The API rotates refresh tokens and treats a reused one as stolen, so requests that all find the access token
// expired at once must share a single refresh (lib/auth/client.ts) or the user is signed out everywhere.
test("nhiều request cùng hết hạn access token chỉ làm mới phiên một lần", async ({ page, context }) => {
  test.slow()
  await page.goto("/login")
  await signIn(page, HR.username, HR.password)
  await expect(page).toHaveURL(/\/dashboard$/)

  const refreshes: string[] = []
  let expired = 0
  page.on("request", (sent) => {
    if (sent.method() === "POST" && sent.url().endsWith("/api/session/refresh")) refreshes.push(sent.url())
  })
  page.on("response", (received) => {
    if (received.status() === 401 && received.url().includes("/api/session/")) expired++
  })

  await context.clearCookies({ name: "hrm_access_token" })
  // The employee page loads its list and its filter options at the same time.
  await page.getByRole("link", { name: "Danh sách nhân sự" }).click()

  await expect(page).toHaveURL(/\/employees$/)
  await expect(page.getByRole("table")).toBeVisible()
  expect(expired).toBeGreaterThan(1)
  expect(refreshes).toHaveLength(1)
})
