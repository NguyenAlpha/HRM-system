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

Các tài khoản dưới đây chỉ tồn tại khi seeder tương ứng được bật trong API:

`USER_SEED_ENABLED` mặc định là `false`; đặt thành `true` khi muốn sử dụng ba account demo của portal HRM.

| Portal | Username | Password mặc định | Role | Điều kiện API |
|:-------|:---------|:------------------|:-----|:--------------|
| HRM | `employee01` | `Employee@123` | `EMPLOYEE` | `USER_SEED_ENABLED=true` |
| HRM | `hr01` | `HrStaff@123` | `HR_STAFF` | `USER_SEED_ENABLED=true` |
| HRM | `payroll01` | `Payroll@123` | `PAYROLL_ACCOUNTANT` | `USER_SEED_ENABLED=true` |
| Admin | `admin` | `Admin@123` | `SYSTEM_ADMIN` | `ADMIN_SEED_ENABLED=true` |

Không dùng mật khẩu mặc định ở môi trường thật. Production cần tắt seeder hoặc truyền mật
khẩu an toàn qua biến môi trường.

## Lệnh kiểm tra

```powershell
npm run lint
npm run typecheck
npm run build
```

Dự án chưa cấu hình test runner tự động cho web. Trước khi bàn giao thay đổi auth, thực hiện
ít nhất checklist thủ công sau:

1. Đăng nhập `employee01` tại `/login` và kiểm tra chuyển tới `/dashboard`.
2. Đăng nhập `admin` tại `/admin/login` và kiểm tra chuyển tới `/admin`.
3. Dùng tài khoản admin tại `/login`; hệ thống phải trả `ADMIN_PORTAL_REQUIRED`.
4. Dùng tài khoản thường tại `/admin/login`; hệ thống phải trả `ADMIN_ACCESS_REQUIRED`.
5. Refresh phiên và kiểm tra thông tin tài khoản vẫn tải được.
6. Logout và kiểm tra route được bảo vệ chuyển về đúng trang đăng nhập.
7. Đổi mật khẩu và kiểm tra phiên bị xóa, người dùng được đưa về trang đăng nhập.

## Cấu trúc liên quan đến auth

```text
app/
├── login/                    # Trang đăng nhập HRM
├── dashboard/                # Trang HRM sau đăng nhập
├── admin/login/              # Trang đăng nhập Admin
├── admin/                    # Trang Admin sau đăng nhập
└── api/
    ├── session/              # BFF endpoints của HRM
    └── admin-session/        # BFF endpoints của Admin
components/auth/              # Form đăng nhập và dashboard dùng chung
lib/auth/                     # Kiểu dữ liệu, client, cấu hình portal và xử lý server
proxy.ts                      # Điều hướng sơ bộ dựa trên sự tồn tại của cookie
```

## Lưu ý khi triển khai

- Web server phải gọi được `API_URL` từ mạng nội bộ/container của nó.
- Production phải chạy HTTPS và đặt `AUTH_COOKIE_SECURE=true`.
- Không ghi access token hoặc refresh token vào log.
- Không đưa token vào `localStorage`, `sessionStorage`, URL hoặc response trả cho JavaScript.
- Thời gian sống refresh cookie nên khớp với thời gian sống refresh token của API.
