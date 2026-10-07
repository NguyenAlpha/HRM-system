# HRM System

Hệ thống quản trị nhân sự gồm Spring Boot API, Next.js web và PostgreSQL. Chức năng hiện có bao gồm xác thực hai portal, quản lý nhân viên và cơ cấu tổ chức, RBAC theo phạm vi, ca làm việc, nghỉ phép, chấm công, ngày lễ, lương cơ bản, bảo hiểm nhân viên, thuế TNCN năm 2026, kỳ lương và báo cáo tổng hợp.

## Chạy nhanh

Yêu cầu: Java 21+, Node.js 20.9+, npm 10+ và Docker.

```powershell
docker compose up -d database

Set-Location api
./mvnw.cmd spring-boot:run

# Terminal khác
Set-Location apps/web
Copy-Item .env.example .env.local
npm ci
npm run dev
```

Mở `http://localhost:3000`. API chạy tại `http://localhost:8080`; PostgreSQL local dùng cổng `5432`.

Flyway tự áp dụng migration V1–V34. Không cần xóa volume database khi cập nhật source.

## Tài liệu

- [Cài đặt và kiểm tra](docs/SETUP.md)
- [Danh sách tính năng hiện có](docs/FEATURES.md)
- [Tổng quan chức năng và kiến trúc](docs/OVERVIEW.md)
- [Cấu trúc source](docs/PROJECT_STRUCTURE.md)
- [Cơ cấu tổ chức và phân quyền](docs/ORGANIZATION_STRUCTURE.md)
- [Tài liệu API](api/docs/api/README.md)
- [Chấm công](api/docs/api/ATTENDANCE.md) và [tính lương](api/docs/api/PAYROLL.md)
- [Tài liệu web](apps/web/docs/README.md)

## Kiểm tra

```powershell
Set-Location api
./mvnw.cmd test

Set-Location ../apps/web
npm run lint
npm run typecheck
npm run build
```

Backend test cần PostgreSQL đang chạy. Một số test tích hợp giả định database test sạch; xem [SETUP.md](docs/SETUP.md) để biết cách chạy cô lập.
