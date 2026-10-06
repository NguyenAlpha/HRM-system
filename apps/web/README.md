# HRM Web

Frontend Next.js 16 của hệ thống HRM, sử dụng App Router, React 19, TypeScript strict, Tailwind CSS 4 và alias `@/*`.

## Yêu cầu

- Node.js 20.9 trở lên
- npm 10 trở lên

## Chạy local

```bash
cp .env.example .env.local
npm install
npm run dev
```

Mở [http://localhost:3000](http://localhost:3000). Spring Boot API mặc định chạy tại `http://localhost:8080`; thay `API_URL` trong `.env.local` nếu dùng địa chỉ khác.

## Hai portal

| Portal | Đăng nhập | Trang chính | Tài khoản |
| --- | --- | --- | --- |
| HRM Workspace | `/login` | `/dashboard` | Tài khoản đã xác thực, không có `SYSTEM_ADMIN` |
| Admin Console | `/admin/login` | `/admin` | Tài khoản có `SYSTEM_ADMIN` |

Access token và refresh token được lưu trong bộ cookie `HttpOnly` riêng của từng portal. Chỉ đặt
`AUTH_COOKIE_SECURE=false` khi phát triển bằng HTTP local; môi trường HTTPS phải dùng `true`.

## Màn hình nghiệp vụ

| Route | Chức năng |
| --- | --- |
| `/employees`, `/employees/{id}` | Danh sách, tạo và xem hồ sơ nhân viên |
| `/organization` | Đơn vị, địa điểm, vị trí và ca làm việc |
| `/attendance` | Chuẩn bị bảng công, vào/ra ca, xử lý công và tăng ca |
| `/leave-requests` | Tạo và xử lý đơn nghỉ phép |
| `/payslips` | Lương, bảo hiểm, người phụ thuộc, kỳ lương và phiếu lương |
| `/reports/workforce` | Báo cáo nhân sự và phân bố lương cơ bản |
| `/rbac`, `/role-grant`, `/role-requests` | Custom role, gán role và yêu cầu cấp role |
| `/admin/company-owner`, `/admin/organization`, `/admin/rbac`, `/admin/role-grant` | Các chức năng quản trị hệ thống |

Mỗi trang tự ẩn thao tác không có quyền để cải thiện trải nghiệm; Spring Boot API vẫn là nơi quyết định authorization và scope dữ liệu.

## Lệnh kiểm tra

```bash
npm run dev       # server phát triển
npm run build     # tạo production build
npm run start     # chạy production build
npm run lint      # ESLint
npm run typecheck # kiểm tra TypeScript
```

## Cấu trúc

```text
app/          App Router pages và Route Handlers
components/   UI theo module auth, employee, organization, RBAC, workforce, report
lib/          Kiểu dữ liệu, API client và helper auth phía server/client
public/       Static assets
```

Xem [tài liệu frontend](docs/README.md), [xác thực](docs/AUTH.md), [RBAC](docs/RBAC.md) và [hướng dẫn phát triển](docs/DEVELOPMENT.md).
