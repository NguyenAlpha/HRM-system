import { expect, test, type Browser } from "@playwright/test"

import { EMPLOYEE, OWNER, signIn } from "./auth"

// Each sign-in gets its own browser context, like a fresh browser with no cookies.
async function signInFresh(browser: Browser, user: { username: string; password: string }) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await page.goto("/login")
  await signIn(page, user.username, user.password)
  return { context, page }
}

async function expectEmployeeSignsIn(browser: Browser) {
  const { context, page } = await signInFresh(browser, EMPLOYEE)
  await expect(page).toHaveURL(/\/dashboard$/)
  await context.close()
}

/** Owner opens the employee's account and runs one action from the "…" menu. */
async function ownerRunsAccountAction(browser: Browser, action: "Vô hiệu hóa" | "Kích hoạt lại", status: string) {
  const { context, page } = await signInFresh(browser, OWNER)
  await expect(page).toHaveURL(/\/dashboard$/)

  await page.goto(`/accounts/${EMPLOYEE.accountId}`)
  await expect(page.getByRole("heading", { name: EMPLOYEE.username })).toBeVisible()

  await page.getByRole("button", { name: `Mở menu thao tác cho ${EMPLOYEE.username}` }).click()
  await page.getByRole("menuitem", { name: action }).click()
  await page.getByRole("alertdialog").getByRole("button", { name: action }).click()

  await expect(page.getByText(status, { exact: true })).toBeVisible()
  await context.close()
}

test("owner vô hiệu hóa rồi kích hoạt lại tài khoản employee", async ({ browser }) => {
  // Five sign-ins in a row outgrow the 30s default.
  test.slow()

  await test.step("employee đăng nhập được", () => expectEmployeeSignsIn(browser))

  await test.step("owner vô hiệu hóa tài khoản employee", () =>
    ownerRunsAccountAction(browser, "Vô hiệu hóa", "Đã vô hiệu hóa"))

  await test.step("employee không đăng nhập được nữa", async () => {
    const { context, page } = await signInFresh(browser, EMPLOYEE)
    await expect(page.getByText("Tài khoản đã bị vô hiệu hóa.")).toBeVisible()
    await expect(page).toHaveURL(/\/login$/)
    await context.close()
  })

  await test.step("owner kích hoạt lại tài khoản employee", () =>
    ownerRunsAccountAction(browser, "Kích hoạt lại", "Đang hoạt động"))

  await test.step("employee đăng nhập lại được", () => expectEmployeeSignsIn(browser))
})
