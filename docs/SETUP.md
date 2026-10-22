# Hướng dẫn chạy HRM API và Web

Tài liệu này hướng dẫn thiết lập môi trường phát triển local cho toàn bộ hệ thống HRM:

- PostgreSQL 16 chạy bằng Docker Compose.
- Spring Boot API chạy tại `http://localhost:8080`.
- Next.js Web chạy tại `http://localhost:3000`.

## 1. Yêu cầu môi trường

Cài đặt các công cụ sau:

| Công cụ | Phiên bản |
|:--------|:----------|
| Java JDK | 25 |
| Node.js | 20.9 trở lên |
| npm | 10 trở lên |
| Docker | Docker Desktop hoặc Docker Engine có Compose |

Kiểm tra phiên bản:

```powershell
java -version
node --version
npm --version
docker --version
docker compose version
```

Các port mặc định cần còn trống:

| Port | Dịch vụ |
|:----:|:--------|
| `5432` | PostgreSQL |
| `8080` | Spring Boot API |
| `3000` | Next.js Web |

Mỗi khối lệnh bên dưới bắt đầu từ thư mục gốc `HRM-system` trong terminal riêng, trừ khi có ghi
chú khác. Nếu terminal đang ở `api` hoặc `apps/web`, quay về thư mục gốc trước khi chạy khối mới
hoặc bỏ qua lệnh `Set-Location` đã thực hiện.

## 2. Khởi động PostgreSQL

Chạy database:

```powershell
docker compose up -d database
```

Kiểm tra container:

```powershell
docker compose ps
docker compose exec -T database pg_isready -U hrm-user -d hrm
```

Chỉ khởi động API khi `pg_isready` trả `accepting connections`. Nếu database vừa mới khởi tạo và
chưa sẵn sàng, đợi vài giây rồi chạy lại lệnh kiểm tra. Giữ container database ở trạng thái `Up`
trong suốt lúc chạy API.

Cấu hình database mặc định trong `docker-compose.yaml`:

```text
Database: hrm
Username: hrm-user
Password: hrm-password
Port:     5432
```

Dữ liệu được lưu trong Docker volume `hrm_data`, vì vậy vẫn tồn tại sau khi container dừng
hoặc khởi động lại.

## 3. Khởi động API

Mở terminal thứ nhất.

PowerShell trên Windows:

```powershell
Set-Location api
./mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
cd api
./mvnw spring-boot:run
```

API khởi động thành công khi log hiển thị ứng dụng đã start trên port `8080`. Trong lần chạy
đầu tiên, Flyway tự động tạo schema và các seeder khởi tạo dữ liệu phát triển.

Swagger UI có tại `http://localhost:8080/swagger-ui.html`; xem
[api/docs/api/README.md](../api/docs/api/README.md#openapi-và-swagger-ui) để gọi thử endpoint cần JWT.

### Biến môi trường của API

Các giá trị mặc định đã khớp với `docker-compose.yaml`; local development không bắt buộc
phải khai báo thêm biến môi trường.

| Biến | Mặc định | Ý nghĩa |
|:-----|:---------|:--------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/hrm` | JDBC URL của PostgreSQL |
| `DB_USERNAME` | `hrm-user` | Database username |
| `DB_PASSWORD` | `hrm-password` | Database password |
| `JWT_SECRET` | Secret phát triển trong properties | Secret Base64, tối thiểu 32 bytes sau decode |
| `JWT_ISSUER` | `https://hrm.local` | JWT issuer |
| `JWT_ACCESS_TOKEN_EXPIRATION_SECONDS` | `900` | Thời gian sống access token |
| `JWT_REFRESH_TOKEN_EXPIRATION_DAYS` | `30` | Thời gian sống refresh token |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Origin được phép gọi API |
| `API_DOCS_ENABLED` | `true` | Bật `/v3/api-docs` và Swagger UI; đặt `false` ngoài môi trường phát triển |
| `COMPANY_SEED_ENABLED` | `true` | Seed company profile, work locations, organization units và job positions cơ bản |
| `RBAC_SEED_ENABLED` | `true` | Seed role, permission và mapping |
| `ADMIN_SEED_ENABLED` | `true` | Seed system admin |

Thông tin admin có thể được thay bằng:

```powershell
$env:ADMIN_SEED_USERNAME = "admin"
$env:ADMIN_SEED_EMAIL = "admin@hrm.local"
$env:ADMIN_SEED_PASSWORD = "YourSecurePassword"
./mvnw.cmd spring-boot:run
```

Seeder có tính idempotent và không đổi mật khẩu của account đã tồn tại. Vì vậy biến mật khẩu
mới chỉ có tác dụng khi account admin chưa được tạo.

Hệ thống không seed nhân viên hay tài khoản HRM nào. Database mới đi qua chuỗi bootstrap thực tế:
`admin` đăng nhập `/admin/login` và tạo Company Owner tại `/admin/company-owner`, Company Owner kích hoạt
tài khoản qua link mời rồi đăng nhập `/login` để tạo nhân viên và cấp role (ví dụ HR Manager) cho người khác.

> Các credential, database password và JWT secret mặc định chỉ dành cho local development.
> Không sử dụng chúng ở staging hoặc production.

## 4. Khởi động Web

Giữ API đang chạy và mở terminal thứ hai.

PowerShell trên Windows:

```powershell
Set-Location apps/web
Copy-Item .env.example .env.local
npm ci
npm run dev
```

macOS/Linux:

```bash
cd apps/web
cp .env.example .env.local
npm ci
npm run dev
```

Nội dung local mặc định:

```dotenv
API_URL=http://localhost:8080
AUTH_COOKIE_SECURE=false
AUTH_REFRESH_COOKIE_MAX_AGE_SECONDS=2592000
```

`API_URL` được Next.js Route Handlers sử dụng ở server để gọi Spring Boot. Không cần đưa
access token hoặc refresh token vào biến môi trường của web.

Mở các địa chỉ sau:

| Chức năng | URL |
|:----------|:----|
| Trang mặc định | `http://localhost:3000` |
| Đăng nhập nhân viên | `http://localhost:3000/login` |
| HRM dashboard | `http://localhost:3000/dashboard` |
| Nhân viên | `http://localhost:3000/employees` |
| Tổ chức và ca làm | `http://localhost:3000/organization` |
| Chấm công | `http://localhost:3000/attendance` |
| Đơn nghỉ | `http://localhost:3000/leave-requests` |
| Lương, bảo hiểm và thuế | `http://localhost:3000/payslips` |
| Báo cáo nhân sự | `http://localhost:3000/reports/workforce` |
| Đăng nhập quản trị | `http://localhost:3000/admin/login` |
| Admin dashboard | `http://localhost:3000/admin` |

## 5. Tài khoản phát triển

Khi các cờ seed để mặc định (bật), database mới có sẵn các tài khoản sau:

| Portal | Username | Password | Role chính |
|:-------|:---------|:---------|:-----------|
| Admin | `admin` | `Admin@123` (`ADMIN_SEED_PASSWORD`) | `SYSTEM_ADMIN` |
| HRM | `owner` | `password` | `COMPANY_OWNER`, `DIRECTOR`, `PAYROLL_APPROVER` |
| HRM | `hr` | `password` | `HR_MANAGER` (toàn công ty) |
| HRM | `accountant` | `password` | `PAYROLL_ACCOUNTANT` (toàn công ty) |
| HRM | `branch-hn` | `password` | `MANAGER` (địa điểm chi nhánh Hà Nội) |
| HRM | `employee` | `password` | `EMPLOYEE` (chính mình) |

`owner` do `CompanyOwnerSeeder` tạo; các tài khoản HRM còn lại (20 người) khai báo trong
`api/src/main/resources/seed/staff.json`, mọi tài khoản trong file đều có role `EMPLOYEE`. Danh sách đầy đủ, phân
công và dữ liệu đi kèm xem ở [ORGANIZATION_STRUCTURE.md](ORGANIZATION_STRUCTURE.md#7-seeder-hiện-có).

Tắt `OWNER_SEED_ENABLED` và `STAFF_SEED_ENABLED` nếu muốn bắt đầu từ database chỉ có `admin` và đi qua luồng khởi
tạo thật: `admin` tạo Company Owner tại `/admin/company-owner`, Company Owner kích hoạt qua link mời rồi tạo nhân
viên và cấp role cho người khác.

Tài khoản `SYSTEM_ADMIN` phải đăng nhập tại `/admin/login`. Tài khoản HRM (Company Owner và người
được tạo sau đó) đăng nhập tại `/login`. Đăng nhập sai portal sẽ trả `ADMIN_PORTAL_REQUIRED` hoặc
`ADMIN_ACCESS_REQUIRED`.

## 6. Kiểm tra sau khi setup

Ngày 2026-10-11 chuỗi migration được gộp lại thành baseline mới. Database local tạo trước ngày đó phải chạy `docker compose down -v` một lần (xem mục 7), nếu không API sẽ dừng vì Flyway báo lệch checksum. Trước khi tính lương một kỳ, HR cần nhập hồ sơ bảo hiểm, người phụ thuộc và kế toán cần khai báo ngày dự kiến trả lương theo [hướng dẫn tính lương](../api/docs/api/PAYROLL.md). Quy tắc thuế TNCN và bảo hiểm nạp sẵn (migration `V8__reference_data.sql`) có hiệu lực từ 2020-01-01 đến 2026-12-31; kỳ lương ngoài khoảng này sẽ báo thiếu quy tắc để bổ sung migration phù hợp.

Thực hiện lần lượt:

1. Mở `/admin/login`, đăng nhập bằng `admin` và kiểm tra được chuyển tới `/admin`.
2. Mở `/login`, đăng nhập bằng `owner` / `password` và kiểm tra được chuyển tới `/dashboard`. Nếu đã tắt seed Company Owner, tạo Company Owner tại `/admin/company-owner` và kích hoạt qua link mời theo [workflow khởi tạo](../api/docs/api/ORGANIZATION_COMPANY_OWNER_ADMIN.md).
3. Kiểm tra dashboard hiển thị account, role và permission.
4. Thử refresh session và đăng xuất để kiểm tra cookie/session hoạt động.

Chạy toàn bộ test backend:

```powershell
Set-Location api
./mvnw.cmd test
```

Kiểm tra web:

```powershell
Set-Location apps/web
npm run lint
npm run typecheck
npm run build
```

Trên macOS/Linux, thay `./mvnw.cmd` bằng `./mvnw`.

## 7. Dừng hệ thống

Dừng API và Web bằng `Ctrl+C` tại hai terminal tương ứng.

Dừng PostgreSQL nhưng giữ dữ liệu:

```powershell
docker compose stop database
```

Khởi động lại database sau đó:

```powershell
docker compose start database
```

### Reset hoàn toàn database local

Lệnh sau xóa container và Docker volume, toàn bộ dữ liệu local sẽ mất:

```powershell
docker compose down -v
docker compose up -d database
```

Chỉ sử dụng khi chắc chắn không cần giữ dữ liệu (hoặc sao lưu trước, xem [BACKUP.md](BACKUP.md)). Sau khi database được tạo lại, restart API
để Flyway chạy migration và seeder từ đầu.

## 8. Lỗi thường gặp

### API báo không cấu hình được DataSource

Kiểm tra PostgreSQL đang chạy:

```powershell
docker compose ps
docker compose logs database
docker compose exec -T database pg_isready -U hrm-user -d hrm
```

Đồng thời kiểm tra `DB_URL`, `DB_USERNAME` và `DB_PASSWORD` nếu đã override giá trị mặc định.
Nếu `docker compose ps` không cho thấy database đang `Up`, chạy `docker compose up -d database`
rồi đợi `pg_isready` trả `accepting connections` trước khi khởi động lại API. Không cần xóa volume.

### `./mvnw.cmd test` báo lỗi RBAC trên database đã có dữ liệu

Test tích hợp ghi dữ liệu thử và có một số giả định về dữ liệu ban đầu. Chạy chúng trên database
test riêng với `DB_URL` trỏ đến database đó, không dùng database `hrm` đang chứa dữ liệu phát triển.
Nếu lỗi là `Status expected:<405> but was:<500>` tại `RbacManagementTest`, đó là lỗi trả mã HTTP
của API; `GlobalExceptionHandler` hiện xử lý trường hợp này bằng `METHOD_NOT_ALLOWED`.

### Port đã được sử dụng

Kiểm tra process đang giữ port trên Windows:

```powershell
Get-NetTCPConnection -State Listen | Where-Object LocalPort -In 3000, 5432, 8080
```

Hoặc trên macOS/Linux:

```bash
lsof -i :3000
lsof -i :5432
lsof -i :8080
```

### Web báo `API_UNAVAILABLE`

- Xác nhận API vẫn chạy tại `http://localhost:8080`.
- Kiểm tra `API_URL` trong `apps/web/.env.local`.
- Restart `npm run dev` sau khi thay đổi file môi trường.

### Đăng nhập thành công nhưng cookie không được lưu

Khi chạy bằng HTTP local, phải đặt:

```dotenv
AUTH_COOKIE_SECURE=false
```

Production dùng HTTPS phải đặt `AUTH_COOKIE_SECURE=true`.

### Thay mật khẩu seed nhưng mật khẩu cũ vẫn còn

Seeder không ghi đè account đã tồn tại. Có thể đổi mật khẩu qua chức năng Change Password,
hoặc reset database local nếu không cần giữ dữ liệu.

## 9. Tài liệu liên quan

- Sao lưu và phục hồi database: [`BACKUP.md`](BACKUP.md)
- Auth API: [`api/docs/api/AUTH.md`](../api/docs/api/AUTH.md)
- Web development: [`apps/web/docs/DEVELOPMENT.md`](../apps/web/docs/DEVELOPMENT.md)
- Web authentication: [`apps/web/docs/AUTH.md`](../apps/web/docs/AUTH.md)
- Danh mục API: [`api/docs/api/README.md`](../api/docs/api/README.md)
- Database schema hiện hành: [`api/docs/database/DATABASE_SCHEMA.md`](../api/docs/database/DATABASE_SCHEMA.md)
