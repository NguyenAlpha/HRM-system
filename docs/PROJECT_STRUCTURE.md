# Cấu trúc đồ án HRM

Tài liệu này mô tả cấu trúc hiện tại của đồ án HRM (Human Resource Management), gồm backend Java Spring Boot, frontend Next.js, cơ sở dữ liệu PostgreSQL và hệ thống tài liệu.

## 1. Cây thư mục cấp cao

```text
HRM-system/
├── api/                    # Backend Spring Boot và Maven
├── apps/
│   └── web/                # Frontend Next.js
├── docs/                   # Tài liệu cấp toàn dự án
├── .github/                # Metadata và workflow hỗ trợ dự án
├── docker-compose.yaml     # Khởi chạy PostgreSQL local
├── .gitignore
└── README.md               # Hướng dẫn nhanh
```

## 2. Backend `api/`

Backend là ứng dụng Spring Boot build bằng Maven. Package gốc là `com.htttdn.hrm`.

```text
api/
├── pom.xml                         # Dependencies và cấu hình Maven
├── mvnw / mvnw.cmd                 # Maven Wrapper cho Linux/Windows
├── .mvn/wrapper/                   # Cấu hình Maven Wrapper
├── src/
│   ├── main/
│   │   ├── java/com/htttdn/hrm/
│   │   │   ├── HrmApplication.java # Entry point của Spring Boot
│   │   │   ├── config/             # Cấu hình ứng dụng và security
│   │   │   │   └── seed/            # Seeder dữ liệu khởi tạo
│   │   │   ├── controller/         # REST controller
│   │   │   ├── dto/                # DTO giao tiếp API
│   │   │   │   ├── request/
│   │   │   │   └── response/
│   │   │   ├── entity/             # JPA entity và enum miền nghiệp vụ
│   │   │   │   └── enums/
│   │   │   ├── exception/          # Exception nghiệp vụ và xử lý lỗi chung
│   │   │   ├── repository/         # Spring Data repository
│   │   │   ├── security/           # JWT, user details và xử lý lỗi bảo mật
│   │   │   └── service/            # Service nghiệp vụ
│   │   │       └── impl/           # Các implementation của service
│   │   └── resources/
│   │       ├── application.properties # Cấu hình runtime
│   │       └── db/migration/          # Flyway migrations V1 đến V34
│   └── test/java/                  # Unit, integration và context tests
├── docs/                           # Tài liệu API và database của backend
└── target/                         # Output Maven sinh tự động, không phải source
```

### Vai trò các package chính

| Package | Trách nhiệm |
| --- | --- |
| `config` | Khai báo bean, cấu hình ứng dụng, security và dữ liệu seed. |
| `controller` | Nhận HTTP request và trả response cho xác thực, nhân sự, tổ chức, RBAC và báo cáo. |
| `dto` | Mô hình dữ liệu request/response, được chia theo module. |
| `entity` | Mô hình dữ liệu JPA cho nhân sự, tài khoản, RBAC, chấm công và payroll. |
| `repository` | Truy vấn và persistence thông qua Spring Data JPA. |
| `service` | Interface và logic nghiệp vụ; `impl` chứa implementation. |
| `security` | JWT, xác thực tài khoản, entry point và access-denied handler. |
| `exception` | Exception dùng chung và chuẩn hóa error response. |

### Database migrations

Các migration nằm trong `api/src/main/resources/db/migration/` và được Flyway chạy theo thứ tự:

| Migration | Phạm vi |
| --- | --- |
| `V1__create_organization_structure_tables.sql` | Company profile, địa điểm, đơn vị tổ chức, vị trí công việc và ca làm. |
| `V2__create_employee_and_account_tables.sql` | Employee và account. |
| `V3__create_rbac_tables.sql` | Permission, role, role assignment và permission override. |
| `V4__create_assignment_and_compensation_tables.sql` | Phân công nhân sự và chế độ đãi ngộ. |
| `V5__create_employee_request_tables.sql` | Đơn nghỉ phép và đơn nghỉ việc. |
| `V6__create_attendance_tables.sql` | Chấm công và làm thêm giờ. |
| `V7__create_payroll_tables.sql` | Kỳ lương, payslip và các khoản trong payslip. |
| `V8__create_refresh_tokens.sql` | Refresh token phục vụ xác thực phiên. |
| `V9` – `V19` | Bổ sung activation token, permission, audit, identity key và bảo vệ khoảng thời gian. |
| `V20__align_core_schema_with_approved_design.sql` | Đồng bộ schema lõi với thiết kế HRM đã duyệt. |
| `V21__harden_account_permission_overrides.sql` | Bổ sung audit thu hồi và ràng buộc cho permission override. |
| `V22` – `V24` | Loại bỏ các vai trò Team Lead, Warehouse Supervisor và Branch Manager đã ngừng sử dụng. |
| `V25__separate_employee_self_service_permissions.sql` | Tách permission tự phục vụ dành cho nhân viên. |
| `V26__split_employee_permissions.sql` | Tách quyền quản lý nhân viên theo từng hành động. |
| `V27__archive_and_remove_legacy_compensation_requests.sql` | Backfill, lưu trữ và loại bỏ mô hình compensation/request cũ. |
| `V28__create_employee_code_counters.sql` | Bộ đếm cấp mã nhân viên tự động theo tiền tố vị trí. |
| `V29` – `V30` | Ngày lễ công ty và số phút nghỉ trong bản ghi công. |
| `V31` – `V33` | Hồ sơ bảo hiểm, người phụ thuộc, quy tắc thuế/bảo hiểm 2026 có ngày hiệu lực và snapshot khấu trừ trên phiếu lương. |
| `V34__track_overtime_tax_exemption.sql` | Xác nhận miễn thuế từng khoản tăng ca và snapshot phần tăng ca miễn thuế. |

### Tài liệu backend

- `api/docs/api/AUTH.md`: hợp đồng và luồng xác thực API.
- `api/docs/api/REPORT.md`: hợp đồng API báo cáo nhân sự và lương cơ bản.
- `api/docs/api/ATTENDANCE.md`: quy trình chấm công, ngày lễ và tăng ca.
- `api/docs/api/PAYROLL.md`: công thức lương, bảo hiểm, thuế và các API dữ liệu đầu vào.
- `api/docs/Fix_database/DATABASE_SCHEMA.md`: thiết kế lõi V20; phần bổ sung V29–V34 ở cuối tài liệu.
- `api/docs/database/DATABASE_SCHEMA.md`: bản thiết kế cũ, chỉ giữ để tham chiếu lịch sử.
- `api/docs/database/ENTITY_ATTRIBUTES.md`: mô hình entity cũ, chỉ giữ để tham chiếu lịch sử.
- `api/docs/database/SCHEMA_REVIEW_0.1.md`: báo cáo rà soát schema trước V20.
- `api/docs/database/QUERY_REVIEW_0.1.md`: báo cáo rà soát truy vấn trước V20.
- `api/docs/api/README.md`: mục lục tài liệu API hiện hành.

## 3. Frontend `apps/web/`

Frontend dùng Next.js App Router, React, TypeScript và Tailwind CSS.

```text
apps/web/
├── app/
│   ├── layout.tsx                    # Root layout
│   ├── page.tsx                      # Trang gốc, điều hướng vào login
│   ├── globals.css                   # CSS toàn ứng dụng
│   ├── login/page.tsx                # Đăng nhập HRM Workspace
│   ├── dashboard/page.tsx            # Dashboard HRM Workspace
│   ├── employees/                     # Danh sách và chi tiết nhân viên
│   ├── organization/page.tsx          # Cơ cấu tổ chức
│   ├── attendance/page.tsx            # Chấm công
│   ├── leave-requests/page.tsx         # Đơn nghỉ phép
│   ├── payslips/page.tsx               # Lương, bảo hiểm, thuế và kỳ lương
│   ├── rbac/page.tsx                   # Custom role tại HRM Workspace
│   ├── role-grant/page.tsx             # Gán role trực tiếp theo quyền
│   ├── role-requests/page.tsx          # Yêu cầu cấp role
│   ├── reports/workforce/page.tsx    # Báo cáo nhân sự và phân bố lương
│   ├── admin/
│   │   ├── login/page.tsx            # Đăng nhập Admin Console
│   │   ├── page.tsx                  # Trang Admin Console
│   │   ├── company-owner/page.tsx    # Bootstrap Company Owner
│   │   ├── organization/page.tsx     # Quản trị tổ chức từ Admin Console
│   │   ├── rbac/page.tsx             # Quản lý custom role
│   │   └── role-grant/page.tsx       # Gán role quản trị
│   └── api/
│       ├── session/                  # BFF session cho người dùng HRM
│       └── admin-session/            # BFF session cho system admin
├── components/auth/                  # Component login, session và sidebar
├── components/employee/              # Danh sách và form hồ sơ nhân viên
├── components/organization/          # Quản lý tổ chức, địa điểm, vị trí và ca
├── components/rbac/                  # Role, permission và role assignment
├── components/workforce/             # Chấm công, nghỉ phép và lương
├── components/report/                # Dashboard và biểu đồ phân bố báo cáo
├── lib/
│   ├── api.ts                        # API utility dùng chung
│   ├── report.ts                     # Client và kiểu dữ liệu báo cáo
│   ├── report-server.ts              # BFF gọi Report API
│   └── auth/
│       ├── client.ts                 # Helper phía client
│       ├── config.ts                 # Cấu hình cookie và auth
│       ├── server.ts                 # Helper phía server
│       └── types.ts                  # TypeScript types cho auth
├── proxy.ts                          # Proxy/route protection
├── .env.example                      # Biến môi trường mẫu
├── package.json                      # Scripts và dependencies
├── package-lock.json
├── tsconfig.json
├── next.config.mjs
├── postcss.config.mjs
├── eslint.config.mjs
├── next-env.d.ts
├── README.md
└── docs/
    ├── README.md                     # Tài liệu frontend
    ├── AUTH.md                        # Kiến trúc authentication BFF
    ├── DEVELOPMENT.md                 # Chạy local và checklist
    └── RBAC.md                        # Giao diện quản trị vai trò và quyền
```

### Hai portal

- **HRM Workspace**: `/login` và `/dashboard`, dành cho người dùng HRM thông thường.
- **Admin Console**: `/admin/login` và `/admin`, dành cho tài khoản có role `SYSTEM_ADMIN`.

### Authentication BFF

Các route trong `app/api/session/` và `app/api/admin-session/` là Backend for Frontend. Chúng gọi các endpoint `/api/auth/*` của Spring Boot, lưu access/refresh token trong HttpOnly cookies và không đưa token trực tiếp về JavaScript phía trình duyệt.

## 4. Hạ tầng local

```text
docker-compose.yaml
└── database: postgres:16-alpine
```

Container database dùng các giá trị local trong file compose:

- Database: `hrm`
- User: `hrm-user`
- Port: `5432`
- Volume: `hrm_data`

Các service ứng dụng dự kiến chạy ở:

```text
PostgreSQL :5432
Spring Boot API :8080
Next.js Web :3000
```

## 5. Tests và kiểm tra chất lượng

Backend tests nằm trong `api/src/test/java/`, bao gồm:

- Application context.
- JWT, login, refresh token và authorization.
- Seeder dữ liệu.
- Global exception handler.
- Chấm công, lịch làm, lương, bảo hiểm, thuế và tổng hợp báo cáo.

Frontend hiện có các script kiểm tra trong `apps/web/package.json`:

```text
npm run lint
npm run typecheck
npm run build
```

## 6. Thư mục không thuộc source chính

Các thư mục sau là metadata hoặc output sinh tự động và không nên dùng làm nguồn để chỉnh sửa chức năng:

```text
.git/
api/target/
apps/web/node_modules/
apps/web/.next/
.github/modernize/
```

- `api/target/` chứa class đã compile, test reports và file JAR do Maven tạo.
- `apps/web/node_modules/` chứa dependencies cài bằng npm.
- `apps/web/.next/` chứa output build của Next.js.
- `.github/modernize/` chứa metadata của các workflow modernization.

## 7. Tài liệu cấp dự án

- `README.md`: hướng dẫn nhanh ở root.
- `docs/SETUP.md`: hướng dẫn cài đặt và chạy dự án.
- `docs/OVERVIEW.md`: tổng quan sản phẩm, tech stack và cách chạy.
- `docs/PROJECT_STRUCTURE.md`: tài liệu cấu trúc hiện tại này.

## 8. Luồng phụ thuộc chính

```text
Browser
   │
   ▼
Next.js Web (BFF routes + UI)
   │  HTTP qua BFF với Bearer token phía server
   ▼
Spring Boot API
   │
   ├── Spring Security / JWT
   ├── Service → Repository → JPA
   ├── Flyway migrations
   ▼
PostgreSQL
```

Các REST controller hiện bao phủ xác thực, account, nhân sự, phân công, tổ chức, ca làm, RBAC,
permission override, yêu cầu cấp role, nghỉ phép, chấm công, ngày lễ, lương và báo cáo. Khi tích hợp
module mới, dùng [mục lục API](../api/docs/api/README.md) và controller đang chạy làm nguồn sự thật.
