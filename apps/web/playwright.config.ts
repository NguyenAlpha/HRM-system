import { defineConfig, devices } from "@playwright/test"

/**
 * E2E tests drive the real stack: the API (port 8080) and Postgres must already be running.
 * The web dev server is started here unless one is already up on port 3000.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  reporter: [["list"], ["html", { open: "never" }]],
  // `next dev` compiles each page on its first visit, which can take longer than the 5s default.
  expect: { timeout: 15_000 },
  use: {
    baseURL: "http://localhost:3000",
    locale: "vi-VN",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] }, testIgnore: /account-status/ },
    // Disables the shared employee account for a while, so it runs only after the other tests finish.
    {
      name: "account-status",
      use: { ...devices["Desktop Chrome"] },
      testMatch: /account-status/,
      dependencies: ["chromium"],
    },
  ],
  webServer: {
    command: "npm run dev",
    url: "http://localhost:3000/login",
    reuseExistingServer: true,
    timeout: 120_000,
  },
})
