import { expect, test, type Browser, type Page } from "@playwright/test"

import { EMPLOYEE, HR, signIn } from "./auth"

// Each sign-in gets its own browser context, like a fresh browser with no cookies.
async function signInFresh(browser: Browser, user: { username: string; password: string }) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await page.goto("/login")
  await signIn(page, user.username, user.password)
  await expect(page).toHaveURL(/\/dashboard$/)
  return { context, page }
}

/**
 * The number at the start of a stat, written "8.5" on the leave page and "15,5" on the attendance
 * page. `loaded` tells a figure from the placeholder shown while the data is still loading.
 */
async function statValue(page: Page, label: string, loaded = /\d/) {
  const value = page.locator(`dt:text-is("${label}") + dd`)
  await expect(value).toHaveText(loaded)
  return Number((await value.innerText()).trim().split(/\s/)[0].replace(",", "."))
}

async function annualLeave(page: Page) {
  await page.goto("/leave-requests")
  return { used: await statValue(page, "Đã dùng"), remaining: await statValue(page, "Còn lại") }
}

/** "Công tính lương" of the month, and the list view of the timesheet opened for the rows. */
async function payableDays(page: Page) {
  await page.goto("/attendance")
  // The summary tiles appear once the month's records have arrived.
  const days = await statValue(page, "Công tính lương", /\/ [1-9]\d* ngày/)
  await page.getByRole("tab", { name: "Danh sách" }).click()
  return days
}

/** Today in Vietnam as YYYY-MM-DD. */
function today() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh" }).format(new Date())
}

/**
 * The first day of this month, before today, still missing its punches with nothing payable,
 * i.e. without a leave already attached; null when there is none.
 */
async function firstMissingDay(page: Page): Promise<string | null> {
  const month = today().slice(0, 7)
  const rows = page.getByRole("row").filter({ hasText: "Thiếu chấm công" })
  await page.getByRole("columnheader", { name: "Công" }).waitFor()
  for (const row of await rows.all()) {
    const cells = await row.getByRole("cell").allInnerTexts()
    const dayMonth = /(\d{2})\/(\d{2})/.exec(cells[0])
    if (!dayMonth) continue
    const date = `${month}-${dayMonth[1]}`
    if (date < today() && cells[5].trim().startsWith("0h /")) return date
  }
  return null
}

test("nghỉ phép năm buổi sáng vào ngày thiếu chấm công được duyệt thì trừ phép và cộng công", async ({ browser }) => {
  test.slow()
  const reason = `E2E phép năm buổi sáng ${Date.now()}`

  const employee = await signInFresh(browser, EMPLOYEE)
  const before = await test.step("ghi nhận phép năm và công tính lương ban đầu", async () => {
    const leave = await annualLeave(employee.page)
    const payable = await payableDays(employee.page)
    return { ...leave, payable }
  })

  const day = await test.step("tìm ngày thiếu chấm công trong tháng", () => firstMissingDay(employee.page))
  test.skip(day === null, "Tháng này không có ngày thiếu chấm công nào chưa có đơn nghỉ")

  const leaveId = await test.step(`employee gửi đơn phép năm buổi sáng ngày ${day}`, async () => {
    const page = employee.page
    await page.goto("/leave-requests")
    await page.getByRole("button", { name: "Tạo đơn nghỉ" }).click()
    const dialog = page.getByRole("dialog")
    await expect(dialog.getByLabel("Loại nghỉ")).toHaveText(/Phép năm/)
    await dialog.getByLabel("Thời gian nghỉ").click()
    await page.getByRole("option", { name: "Buổi sáng (08:00–12:00)" }).click()
    await dialog.getByLabel("Ngày nghỉ").fill(day!)
    await dialog.getByLabel("Lý do").fill(reason)
    await dialog.getByRole("button", { name: "Gửi duyệt" }).click()
    await expect(page.getByText("Đã gửi đơn nghỉ")).toBeVisible()

    const row = page.getByRole("row").filter({ hasText: reason })
    await expect(row).toContainText("Chờ duyệt")
    return /#(\d+)/.exec(await row.innerText())![1]
  })
  await employee.context.close()

  await test.step(`hr duyệt đơn #${leaveId}`, async () => {
    const { context, page } = await signInFresh(browser, HR)
    await page.goto("/leave-requests")
    await page.getByRole("tab", { name: /Chờ tôi duyệt/ }).click()
    await page.getByRole("button", { name: `Mở menu thao tác cho đơn #${leaveId}` }).click()
    await page.getByRole("menuitem", { name: "Duyệt" }).click()
    await page.getByRole("dialog").getByRole("button", { name: "Duyệt đơn" }).click()
    await expect(page.getByText("Đã duyệt đơn")).toBeVisible()
    await context.close()
  })

  await test.step("employee thấy phép năm bị trừ nửa ngày và công tính lương tăng nửa ngày", async () => {
    const { context, page } = await signInFresh(browser, EMPLOYEE)
    const leave = await annualLeave(page)
    expect(leave.used).toBeCloseTo(before.used + 0.5)
    expect(leave.remaining).toBeCloseTo(before.remaining - 0.5)
    await expect(page.getByRole("row").filter({ hasText: reason })).toContainText("Đã duyệt")

    expect(await payableDays(page)).toBeCloseTo(before.payable + 0.5)
    // The morning is paid leave; the afternoon still waits for its punches.
    const dayCell = `${day!.slice(8, 10)}/${day!.slice(5, 7)}`
    const dayRow = page.getByRole("row").filter({ hasText: dayCell })
    await expect(dayRow.getByRole("cell").nth(5)).toHaveText("4h / 8h")
    await context.close()
  })
})
