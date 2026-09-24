# Hướng dẫn chạy HRM API và Web

Tài liệu này hướng dẫn thiết lập môi trường phát triển local cho toàn bộ hệ thống HRM:

- PostgreSQL 16 chạy bằng Docker Compose.
- Spring Boot API chạy tại `http://localhost:8080`.
- Next.js Web chạy tại `http://localhost:3000`.

## 1. Yêu cầu môi trường

Cài đặt các công cụ sau:

| Công cụ | Phiên bản |
|:--------|:----------|
| Java JDK | 21 |
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

Tất cả lệnh bên dưới được chạy từ thư mục gốc của repository, trừ khi có ghi chú khác.

## 2. Khởi động PostgreSQL

Chạy database:

```powershell
docker compose up -d database
```

Kiểm tra container:

```powershell
docker compose ps
```

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
| `COMPANY_SEED_ENABLED` | `true` | Seed company profile |
| `RBAC_SEED_ENABLED` | `true` | Seed role, permission và mapping |
| `ADMIN_SEED_ENABLED` | `true` | Seed system admin |
| `USER_SEED_ENABLED` | `true` | Seed các tài khoản nhân viên mẫu |

Thông tin admin có thể được thay bằng:

```powershell
$env:ADMIN_SEED_USERNAME = "admin"
$env:ADMIN_SEED_EMAIL = "admin@hrm.local"
$env:ADMIN_SEED_PASSWORD = "YourSecurePassword"
./mvnw.cmd spring-boot:run
```

Seeder có tính idempotent và không đổi mật khẩu của account đã tồn tại. Vì vậy biến mật khẩu
mới chỉ có tác dụng khi account admin chưa được tạo.

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
| Đăng nhập quản trị | `http://localhost:3000/admin/login` |
| Admin dashboard | `http://localhost:3000/admin` |

## 5. Tài khoản phát triển

Các tài khoản sau được tạo khi seeder tương ứng được bật:

| Portal | Username | Password | Role |
|:-------|:---------|:---------|:-----|
| Admin | `admin` | `Admin@123` | `SYSTEM_ADMIN` |
| HRM | `employee01` | `Employee@123` | `EMPLOYEE` |
| HRM | `hr01` | `HrStaff@123` | `HR_STAFF` |
| HRM | `payroll01` | `Payroll@123` | `PAYROLL_ACCOUNTANT` |

Tài khoản `SYSTEM_ADMIN` phải đăng nhập tại `/admin/login`. Các tài khoản còn lại đăng nhập
tại `/login`. Đăng nhập sai portal sẽ trả `ADMIN_PORTAL_REQUIRED` hoặc
`ADMIN_ACCESS_REQUIRED`.

## 6. Kiểm tra sau khi setup

Thực hiện lần lượt:

1. Mở `/login`, đăng nhập bằng `employee01` và kiểm tra được chuyển tới `/dashboard`.
2. Đăng xuất, mở `/admin/login`, đăng nhập bằng `admin` và kiểm tra được chuyển tới `/admin`.
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

Chỉ sử dụng khi chắc chắn không cần giữ dữ liệu. Sau khi database được tạo lại, restart API
để Flyway chạy migration và seeder từ đầu.

## 8. Lỗi thường gặp

### API báo không cấu hình được DataSource

Kiểm tra PostgreSQL đang chạy:

```powershell
docker compose ps
docker compose logs database
```

Đồng thời kiểm tra `DB_URL`, `DB_USERNAME` và `DB_PASSWORD` nếu đã override giá trị mặc định.

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

- Auth API: [`api/docs/api/AUTH.md`](../api/docs/api/AUTH.md)
- Web development: [`apps/web/docs/DEVELOPMENT.md`](../apps/web/docs/DEVELOPMENT.md)
- Web authentication: [`apps/web/docs/AUTH.md`](../apps/web/docs/AUTH.md)
- Database schema: [`api/docs/database/DATABASE_SCHEMA.md`](../api/docs/database/DATABASE_SCHEMA.md)
