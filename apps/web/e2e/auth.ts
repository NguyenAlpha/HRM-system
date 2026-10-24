import type { Page } from "@playwright/test"

// Seeded by the API; also shown on /admin/login in development.
export const ADMIN = { username: "admin", password: "Admin@123" }

// Seeded HRM accounts: the Company Owner, the HR manager and an EMPLOYEE (account id 4) whose
// direct manager is that HR manager, so hr approves the employee's leave.
export const OWNER = { username: "owner", password: "password" }
export const HR = { username: "hr", password: "password" }
export const EMPLOYEE = { username: "employee", password: "password", accountId: 4, employeeCode: "NV0003" }

export async function signIn(page: Page, username: string, password: string) {
  // "Tên đăng nhập hoặc email" on the admin portal, "…, email hoặc mã nhân viên" on the HRM one.
  await page.getByLabel(/^Tên đăng nhập/).fill(username)
  await page.getByLabel("Mật khẩu", { exact: true }).fill(password)
  await page.getByRole("button", { name: /^Đăng nhập (hệ thống|Admin)$/ }).click()
}
