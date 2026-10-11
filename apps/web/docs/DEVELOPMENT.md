# Development Guide

## Yêu cầu

- Node.js 20.9 trở lên
- npm 10 trở lên
- HRM API đang chạy và kết nối được tới PostgreSQL

## Chạy local

Khởi động API từ thư mục `api`:

```powershell
./mvnw.cmd spring-boot:run
```

Chuẩn bị và chạy web từ thư mục `apps/web`:

```powershell
Copy-Item .env.example .env.local
npm install
npm run dev
```

Mở `http://localhost:3000`. Trang `/` tự chuyển tới `/login`.

## Biến môi trường

| Biến | Mặc định | Mục đích |
|:-----|:---------|:---------|
| `API_URL` | `http://localhost:8080` | URL server-side để Next.js Route Handlers gọi Spring Boot API |
| `AUTH_COOKIE_SECURE` | Theo `NODE_ENV` | Bật thuộc tính `Secure` cho cookie; local HTTP dùng `false`, production HTTPS dùng `true` |
| `AUTH_REFRESH_COOKIE_MAX_AGE_SECONDS` | `2592000` | Thời gian sống của refresh-token cookie, mặc định 30 ngày |

Ví dụ `.env.local`:

```dotenv
API_URL=http://localhost:8080
AUTH_COOKIE_SECURE=false
AUTH_REFRESH_COOKIE_MAX_AGE_SECONDS=2592000
```

Ưu tiên `API_URL` thay vì `NEXT_PUBLIC_API_URL` cho các request xác thực. `API_URL` chỉ được
đọc ở server và không làm lộ địa chỉ nội bộ của API vào JavaScript phía trình duyệt.

## Tài khoản seed dùng khi phát triển

| Portal | Username | Password mặc định | Role | Điều kiện API |
|:-------|:---------|:------------------|:-----|:--------------|
| Admin | `admin` | `Admin@123` | `SYSTEM_ADMIN` | `ADMIN_SEED_ENABLED=true` |

Hệ thống không seed nhân viên hay tài khoản HRM nào. Database mới đi qua chuỗi bootstrap thực tế:
`admin` đăng nhập `/admin/login` và tạo Company Owner tại `/admin/company-owner`, Company Owner kích hoạt
tài khoản qua link mời rồi đăng nhập `/login` để tạo nhân viên và cấp role (ví dụ HR Manager) cho người khác.

Không dùng mật khẩu mặc định ở môi trường thật. Production cần tắt seeder hoặc truyền mật
khẩu an toàn qua biến môi trường.

## Kiểu dữ liệu sinh từ API

Hai file sinh tự động, không sửa tay và luôn commit cùng thay đổi DTO:

| File | Sinh bởi | Nội dung |
|:-----|:---------|:---------|
| `openapi.json` | `OpenApiSnapshotTest` ở `api/` | Bản chụp spec OpenAPI của API |
| `lib/api-schema.ts` | `npm run gen:api` | TypeScript type sinh từ `openapi.json` bằng `openapi-typescript` |

Sau khi đổi DTO hoặc endpoint ở `api/` (không cần bật API):

```powershell
# trong api/
./mvnw.cmd test "-Dtest=OpenApiSnapshotTest" "-Dopenapi.snapshot.update=true"
# trong apps/web/
npm run gen:api
npm run typecheck
```

Typecheck báo lỗi ở những chỗ frontend không còn khớp DTO mới.

Quên một trong hai bước sẽ bị chặn:

- Quên cập nhật `openapi.json`: `OpenApiSnapshotTest` fail khi chạy `./mvnw.cmd test`, liệt kê path
  và schema bị lệch.
- Quên `npm run gen:api`: `npm run typecheck` fail ở bước `check:api` với thông báo
  `Generated types are not up-to-date`.

`.gitattributes` giữ hai file này ở LF trên mọi máy; `check:api` so sánh từng byte nên CRLF do
`core.autocrlf` sẽ làm check fail.

Module dùng type sinh ra định nghĩa alias trong file `lib/<module>.ts`, ví dụ
`type EmployeeDetail = components["schemas"]["EmployeeDetailResponse"]`, và component chỉ import
alias đó; body gửi lên API được kiểm bằng `satisfies` với type request tương ứng. Mọi module đã
dùng type sinh ra; chỉ còn viết tay các type không phải DTO: `ApiEnvelope<T>` và `Page<T>` (spec
sinh một bản riêng cho từng `T`; `Page<T>` định nghĩa một lần ở `lib/pagination.ts`), state/filter của giao diện như `ReportFilters`.

Khi thêm DTO mới, gắn `@Nullable` cho field có thể null (xem
[quy ước null](../../../api/docs/api/README.md#openapi-và-swagger-ui)); thiếu annotation thì type
sinh ra coi field đó luôn có giá trị.

## Lệnh kiểm tra

```powershell
npm run lint
npm run typecheck
npm run build
```

CI (`.github/workflows/ci.yml`) chạy ba lệnh này cùng `./mvnw test` của API (trên PostgreSQL
rỗng) cho mỗi push lên `main` và mỗi pull request.

Dự án chưa cấu hình test runner tự động cho web. Trước khi bàn giao thay đổi, thực hiện ít nhất checklist thủ công sau:

1. Đăng nhập một tài khoản HRM đã kích hoạt (ví dụ Company Owner) tại `/login` và kiểm tra chuyển tới `/dashboard`.
2. Đăng nhập `admin` tại `/admin/login` và kiểm tra chuyển tới `/admin`.
3. Dùng tài khoản admin tại `/login`; hệ thống phải trả `ADMIN_PORTAL_REQUIRED`.
4. Dùng tài khoản thường tại `/admin/login`; hệ thống phải trả `ADMIN_ACCESS_REQUIRED`.
5. Refresh phiên và kiểm tra thông tin tài khoản vẫn tải được.
6. Logout và kiểm tra route được bảo vệ chuyển về đúng trang đăng nhập.
7. Đổi mật khẩu và kiểm tra phiên bị xóa, người dùng được đưa về trang đăng nhập.
8. Với tài khoản HR, mở `/employees`, `/organization`, `/attendance`, `/leave-requests` và `/payslips`; kiểm tra dữ liệu tải đúng theo permission.
9. Chuẩn bị một tháng công, xử lý bản ghi `MISSING_PUNCH`, sau đó tính thử kỳ lương tương ứng.
10. Với tài khoản không có quyền, kiểm tra thao tác bị chặn bởi API kể cả khi gọi trực tiếp BFF.

## Cấu trúc ứng dụng

```text
app/
├── login/                    # Trang đăng nhập HRM
├── activate/                 # Kích hoạt tài khoản bằng token mời, không cần đăng nhập
├── (hrm)/                    # Route group, không xuất hiện trên URL
│   ├── layout.tsx            # Khung chung HRM: tải phiên một lần, sidebar
│   ├── dashboard/            # Trang HRM sau đăng nhập
│   ├── employees/            # Hồ sơ nhân viên
│   ├── accounts/             # Vòng đời tài khoản đăng nhập
│   ├── organization/         # Tổ chức và ca làm
│   ├── attendance/           # Chấm công
│   ├── leave-requests/       # Đơn nghỉ
│   ├── payslips/             # Bảo hiểm, thuế và lương
│   ├── reports/workforce/    # Báo cáo nhân sự
│   └── rbac/, role-grant/, role-requests/
├── admin/login/              # Trang đăng nhập Admin, nằm ngoài khung Admin
├── admin/(console)/          # Trang Admin; layout.tsx là khung chung của Admin
└── api/
    ├── session/              # BFF endpoints của HRM
    ├── admin-session/        # BFF endpoints của Admin
    └── account-activations/  # BFF kích hoạt tài khoản, không gắn portal
components/                   # UI chia theo tính năng, không theo portal
├── portal/portal-shell.tsx   # PortalShell (khung dùng chung) và hook usePortalSession()
├── common/                   # Thành phần dùng chung mọi tính năng (ErrorAlert, Pagination)
lib/                          # API client, kiểu dữ liệu và auth helpers
├── api-schema.ts             # Type sinh từ openapi.json (npm run gen:api), không sửa tay
openapi.json                  # Bản chụp spec OpenAPI do OpenApiSnapshotTest ghi, không sửa tay
proxy.ts                      # Điều hướng sơ bộ dựa trên sự tồn tại của cookie
```

Trang mới của một portal đặt trong `app/(hrm)/` hoặc `app/admin/(console)/` để tự nằm trong khung
chung. Component của trang chỉ render nội dung riêng: tiêu đề dùng `PageHeader`
(`components/portal/page-header.tsx`, tự đổi dòng HRM WORKSPACE/ADMIN CONSOLE và link "Về tổng quan"
theo portal) và phần thân; phiên đăng nhập, quyền và hàm `onSessionExpired` lấy từ `usePortalSession()`. Sidebar tự đánh dấu mục đang mở theo URL; khi
thêm mục menu, sửa `components/portal/portal-sidebar.tsx` và thêm đường dẫn vào `HRM_PAGES` cùng
`config.matcher` trong `proxy.ts`.

Component chia theo tính năng. Tính năng có ở cả hai portal (RBAC, danh mục tổ chức, cấp role trực
tiếp) chỉ có một component trang, lấy `portal` từ `usePortalSession()`; `app/(hrm)/...` và
`app/admin/(console)/...` cùng render component đó.

## Lưu ý khi triển khai

- Web server phải gọi được `API_URL` từ mạng nội bộ/container của nó.
- Production phải chạy HTTPS và đặt `AUTH_COOKIE_SECURE=true`.
- Không ghi access token hoặc refresh token vào log.
- Không đưa token vào `localStorage`, `sessionStorage`, URL hoặc response trả cho JavaScript.
- Thời gian sống refresh cookie nên khớp với thời gian sống refresh token của API.
