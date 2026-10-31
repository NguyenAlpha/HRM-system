import { expect, test } from "@playwright/test"

import { EMPLOYEE, signIn } from "./auth"

// A workforce request (which handles no expiry itself) that finds the session gone sends the user to sign in.
test("hết phiên giữa chừng trên trang chấm công thì về trang đăng nhập", async ({ page, context }) => {
  test.slow()
  await page.goto("/login")
  await signIn(page, EMPLOYEE.username, EMPLOYEE.password)
  await expect(page).toHaveURL(/\/dashboard$/)
  await page.goto("/attendance")
  await expect(page.getByText("Bảng công tháng").first()).toBeVisible()

  await context.clearCookies()
  await page.getByRole("button", { name: "Tháng trước" }).click()

  await expect(page).toHaveURL(/\/login$/)
})
