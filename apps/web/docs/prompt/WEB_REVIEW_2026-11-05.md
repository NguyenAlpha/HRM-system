# Đánh giá web HRM — 2026-11-05

Chạy theo [WEB_REVIEW_PROMPT.md](./WEB_REVIEW_PROMPT.md).

## 1. Thông tin lần chạy

| Mục | Giá trị |
|---|---|
| Ngày | 2026-11-05 |
| Commit | `477a2b75` fix(api): cap leave and resignation review comments at 100 characters |
| typecheck / lint / build | Sạch / sạch / sạch (`pnpm typecheck` gồm `check:api`) |
| E2E | 11 test, 11 pass (1,2 phút), API + PostgreSQL + `pnpm dev` đang chạy |
| Người chạy | Claude Code |
| Lần trước | Không có (lần chạy đầu tiên) |

## 2. Điểm

**Điểm tổng: 9,4 / 10 — Tốt.** Trung bình có trọng số 935 / 100 = 9,35, làm tròn 9,4. Không có lỗi Cao nên không áp
trần.

| # | Tiêu chí | Trọng số | Điểm | Lỗi đã trừ | Bằng chứng |
|---|---|---|---|---|---|
| 1 | Phiên đăng nhập và BFF | 20% | 9,5 | `WEB-002` Thấp −0,5 | Cookie `HttpOnly`, `SameSite=lax`, `Secure` theo `AUTH_COOKIE_SECURE`/`NODE_ENV` (`lib/auth/server.ts:23-28`); body BFF chỉ có `account` và `expiresAt` (`sessionData`); refresh gộp theo portal và thử lại một lần (`lib/auth/client.ts:13`, `requestWithRefresh`); refresh lỗi xóa cookie trừ 429; nhầm portal thu hồi token (`handleLogin`); không có `console.*`, `localStorage` chỉ giữ trạng thái sidebar. Request ghi được chuyển tiếp có `Origin` lạ trả 403 (đã thử bằng `curl`), nhưng 4 handler phiên không kiểm `Origin`. |
| 2 | Whitelist BFF khớp API | 12% | 9,5 | `WEB-004` Thấp −0,5 | `bff-vs-openapi.mjs`: 98 cặp route + method, đều có endpoint. 51 lời gọi `workforceApi` khớp `WORKFORCE_ROUTES` (script tạm, xem mục 5); các handler một endpoint và hai route timeline trỏ đúng API. `queryKeys` của 7 nhóm handler phủ đủ query param của API; thừa `fromYear`. |
| 3 | Ẩn/hiện theo quyền khớp API | 12% | 8 | `WEB-001` TB −2 | Kiểm một phần (tối đa 8): mã quyền web ⊂ `PermissionSeeder` (lệnh `comm`, chiều thiếu rỗng); toàn bộ mục sidebar so với `@PreAuthorize`; các thao tác gọi `requireCompanyWide`. Chưa đi từng nút của mọi trang. |
| 4 | Form khớp ràng buộc API | 10% | 8 | `WEB-003` Thấp −0,5 | Kiểm một phần (tối đa 8): mọi trường có `@Size` trong DTO đã so với `zod .max`/`maxLength` (tổ chức, role, nhân sự, dữ liệu nhạy cảm, đề xuất role, ý kiến duyệt, hồ sơ doanh nghiệp); nút gửi đều khóa khi đang gửi. Chưa kiểm hết trường bắt buộc và khoảng ngày của mọi form. |
| 5 | Đồng bộ type và danh mục với API | 8% | 10 | — | `check:api` sạch; 94 `ErrorCode` khớp `ERROR_MESSAGES` hai chiều; type viết tay chỉ là type của giao diện/BFF; bảng nhãn enum đều `Record<Enum, string>` (3 bảng `Record<string, string>` ứng với trường API kiểu `string`). |
| 6 | Xử lý lỗi, trạng thái tải, rỗng và hết phiên | 8% | 10 | — | `toast.error` đều qua `apiErrorMessage`/`friendlyErrorMessage` hoặc câu tiếng Việt cố định; các `.catch(() => undefined)` chỉ ở tải lựa chọn cho bộ lọc phụ; mọi file có `<TableBody>` có trạng thái tải và rỗng; E2E `session-expiry.spec.ts` pass. |
| 7 | Ngày giờ và số | 8% | 10 | — | `today()` theo `Asia/Ho_Chi_Minh`; 3 chỗ `toISOString().slice(0, 7)` an toàn (`Date.UTC` hai chiều, hoặc ngày 15); thời điểm gửi API ghép `+07:00`; không in ngày thô. |
| 8 | Cache và làm mới dữ liệu | 7% | 10 | — | Khóa trong code khớp bảng `docs/CACHING.md`; thao tác ghi gọi `invalidateQueries` đúng khóa (nhân sự, kỳ lương, phiếu lương, phụ cấp, doanh nghiệp, danh mục, dashboard); `portal-shell.tsx:47` xóa cache khi rời layout. |
| 9 | Quy ước giao diện của dự án | 5% | 10 | — | Bảng dùng `RowActionsCell` (nút "Cấp tài khoản" thuộc chính bảng mẫu; nút xóa bậc phụ cấp là form sửa dòng); không có Sheet/drawer; màu trực tiếp duy nhất là màu chấm công có chủ ý. |
| 10 | Kiểm thử và build | 7% | 9,5 | `WEB-006` Thấp −0,5 | typecheck, lint, build sạch; E2E 11/11 pass. Có E2E cho đăng nhập, admin vào nhầm HRM, hết phiên, duyệt phép năm, vô hiệu hóa tài khoản. |
| 11 | Tài liệu khớp code | 3% | 9,5 | `WEB-005` Thấp −0,5 | `CACHING.md`, `ARCHITECTURE.md`, `DEVELOPMENT.md` khớp; bảng route trong `README.md` thiếu nhiều trang. |

## 3. Danh sách lỗi

### WEB-001 — Thao tác chỉ dành cho phạm vi toàn công ty vẫn hiện cho người có phạm vi hẹp

- **Mức độ:** Trung bình
- **Thuộc tiêu chí:** 3
- **Vị trí:** `components/portal/portal-sidebar.tsx:118,124` (mục "Kỳ lương", "Quỹ lương & thuế"),
  `components/workforce/attendance-section.tsx:125,200` ("Chuẩn bị ngay"),
  `components/workforce/allowance-policy-page.tsx:73` (sửa phụ cấp); phía API
  `service/payroll/PayrollQueryService.java:49,55,64`, `PayrollReportService.java:42`, `AttendanceService.java:100`,
  `AllowancePolicyService.java:56,131`
- **Mô tả:** Web chỉ xét mã quyền. API đòi phạm vi `COMPANY` cho các thao tác này bằng `requireCompanyWide`, dù
  `report.payroll.read`, `attendance.manage` và `compensation.manage` không phải permission `companyWide`, nên role có
  chúng gán được với phạm vi hẹp. Không chỗ nào trong web xét `scopeType` (`grep "scopeType === \"COMPANY\""` rỗng),
  và phiên (`/api/auth/me`) không cho biết permission nào đang có ở phạm vi công ty.
- **Tình huống:** Owner gán `HR_MANAGER` theo một chi nhánh (hợp lệ, `api/docs/api/RBAC.md` nêu đúng ví dụ này). HR đó
  thấy "Kỳ lương" và "Quỹ lương & thuế" trên sidebar; mở ra thì API trả 403. Nút "Chuẩn bị ngay" và các nút sửa phụ cấp
  cũng hiện rồi trả 403.
- **Đề xuất:** Cho API trả thêm trong phiên danh sách permission đang có ở phạm vi `COMPANY` (hoặc cờ theo từng
  permission), và web dùng nó cho các thao tác trên; hoặc, nếu xem kỳ lương là việc chung cả công ty, đánh dấu
  `report.payroll.read` là `companyWide`. Đây là quyết định nghiệp vụ, cần chọn trước khi sửa.
- **Tìm ra nhờ:** tiêu chí 3
- **Trạng thái:** Chưa sửa

### WEB-002 — Các handler phiên không kiểm `Origin`: đăng nhập giả mạo được

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 1
- **Vị trí:** `lib/auth/server.ts:134` (`handleLogin`), `:165` (`handleRefresh`), `:231` (`handleLogout`), `:253`
  (`handleChangePassword`); `docs/AUTH.md:206`
- **Mô tả:** Kiểm tra `Origin` chỉ nằm trong `sessionAccessToken` (`lib/auth/server.ts:310-314`), tức các request được
  chuyển tiếp và `account-activations`. Bốn handler phiên không kiểm, trong khi `AUTH.md` viết đây là kiểm tra chung
  của mọi handler BFF. `handleLogin` đọc body bằng `request.json()`, nhận cả body `text/plain` mà một form HTML ở trang
  khác gửi được.
- **Tình huống:** Đã thử: `POST /api/session/login` với `Origin: https://evil.example`, `Content-Type: text/plain`
  → 200 và hai `Set-Cookie`. Một trang lạ có thể đăng nhập người dùng vào tài khoản của kẻ tấn công (login CSRF); người
  dùng sau đó nhập dữ liệu cá nhân vào tài khoản đó. Refresh, logout, đổi mật khẩu cần cookie, mà `SameSite=Lax` không
  gửi cookie theo POST khác trang, nên rủi ro chủ yếu ở đăng nhập.
- **Đề xuất:** Đưa kiểm tra `Origin` (và yêu cầu `Content-Type: application/json`) vào một hàm dùng chung cho cả bốn
  handler; thêm E2E hoặc test gọi route với `Origin` lạ.
- **Tìm ra nhờ:** tiêu chí 1
- **Trạng thái:** Chưa sửa

### WEB-003 — Lý do đổi lương không giới hạn độ dài, vượt 250 ký tự thì báo "xung đột dữ liệu"

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 4
- **Vị trí:** `components/employee/employee-compensation-card.tsx:326`;
  `api/.../service/employee/EmployeeSalaryHistoryService.java:140` (`SetSalaryCommand.reason`, không `@Size`);
  `api/src/main/resources/db/migration/V4__employment_and_salary.sql:41` (`reason VARCHAR(250)`)
- **Mô tả:** Ô "Lý do" không có `maxLength`, DTO không có `@Size`, còn cột giới hạn 250 ký tự.
- **Tình huống:** Nhập lý do 300 ký tự → DB từ chối → `GlobalExceptionHandler` trả `409 CONFLICT` với câu xung đột dữ
  liệu chung, người dùng không biết sửa gì.
- **Đề xuất:** Thêm `@Size(max = 250)` cho `reason` của `SetSalaryCommand` và `maxLength={250}` trên ô nhập.
- **Tìm ra nhờ:** tiêu chí 4
- **Trạng thái:** Chưa sửa

### WEB-004 — `queryKeys` của workforce có khóa thừa `fromYear`

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 2
- **Vị trí:** `lib/auth/server.ts:450`
- **Mô tả:** `fromYear` được cho qua nhưng không trang nào gửi và không endpoint API nào nhận (`grep` trong web và API
  chỉ thấy dòng này).
- **Tình huống:** Không gây lỗi; làm danh sách khóa khó đối chiếu với API.
- **Đề xuất:** Bỏ `fromYear` khỏi danh sách.
- **Tìm ra nhờ:** tiêu chí 2
- **Trạng thái:** Chưa sửa

### WEB-005 — Bảng route trong `README.md` thiếu nhiều trang và mô tả sai `/payslips`

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 11
- **Vị trí:** `apps/web/README.md:37-45`
- **Mô tả:** Thiếu `/dashboard`, `/profile`, `/resignations`, `/payroll`, `/allowances`, `/company`,
  `/reports/turnover|monthly|attendance|leave|payroll`, `/admin/rate-limit`, `/admin/traffic`. `/payslips` được mô tả là
  "Lương, bảo hiểm, người phụ thuộc, kỳ lương và phiếu lương", nhưng hiện là "Phiếu lương của tôi"; kỳ lương ở
  `/payroll`, lương và bảo hiểm ở hồ sơ nhân sự.
- **Đề xuất:** Viết lại bảng theo `find app -name page.tsx`.
- **Tìm ra nhờ:** tiêu chí 11
- **Trạng thái:** Chưa sửa

### WEB-006 — E2E chưa phủ chiều ngược của tách portal và các kiểm tra của BFF

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 10
- **Vị trí:** `e2e/`
- **Mô tả:** Có E2E cho admin đăng nhập nhầm HRM, nhưng không có cho tài khoản thường đăng nhập ở `/admin/login`, cho
  request có `Origin` lạ (403) hay route ngoài whitelist (404/405), và cho nhiều request cùng refresh một lúc.
- **Đề xuất:** Thêm một spec gọi thẳng các route BFF bằng `request` của Playwright cho các trường hợp trên.
- **Tìm ra nhờ:** tiêu chí 10
- **Trạng thái:** Chưa sửa

## 4. So sánh với lần trước

Lần chạy đầu tiên, chưa có dữ liệu so sánh.

## 5. Phạm vi chưa kiểm

- Từng nút thao tác của mọi trang so với quyền API (tiêu chí 3): mới kiểm sidebar và các thao tác `requireCompanyWide`.
- Trường bắt buộc và khoảng ngày của mọi form (tiêu chí 4): mới kiểm các trường có `@Size`.
- Lời gọi ngoài `workforceApi` so với whitelist: kiểm bằng mắt danh sách đường dẫn trong `lib/*.ts`, không có script.
  Script tạm cho `workforceApi` (thay `${…}` bằng `1`) báo dương tính giả với đường dẫn ghép từ biến (`${base}`) và
  biểu thức chọn (`${a ? "approve" : "reject"}`); đã kiểm tay các chỗ đó.
- Accessibility và hiệu năng render: prompt không có tiêu chí cho hai phần này.
