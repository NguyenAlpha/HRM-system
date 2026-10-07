# Tổng quan hệ thống HRM

## 1. Phạm vi hiện tại

HRM là ứng dụng quản trị nhân sự cho một công ty, gồm hai portal web dùng chung Spring Boot API:

| Khu vực | Người dùng | Chức năng chính |
| --- | --- | --- |
| HRM Workspace | Nhân viên, HR, kế toán lương, người duyệt | Hồ sơ nhân viên, tổ chức, phân quyền nghiệp vụ, ca làm, nghỉ phép, chấm công, lương và báo cáo |
| Admin Console | `SYSTEM_ADMIN` | Khởi tạo Company Owner, quản trị account và custom role ở phạm vi hệ thống |

Hệ thống đã có database PostgreSQL với Flyway V1–V34, xác thực JWT/refresh token, BFF giữ token trong cookie `HttpOnly`, kiểm tra permission và scope tại service, cùng giao diện nghiệp vụ cho các module chính.

## 2. Module nghiệp vụ

| Module | Nội dung đã triển khai |
| --- | --- |
| Xác thực | Đăng nhập hai portal, refresh rotation, đăng xuất, đổi mật khẩu, kích hoạt account |
| Nhân sự | Tạo hồ sơ, cập nhật, xác nhận tuyển dụng, thông tin nhạy cảm, phân công và vòng đời nhân viên |
| Tổ chức | Hồ sơ công ty, địa điểm, đơn vị tổ chức, chức danh và ca làm việc |
| RBAC | System/custom role, permission catalog chỉ đọc, gán quyền, role assignment, permission override và yêu cầu cấp role |
| Nghỉ phép | Tạo, gửi, duyệt, từ chối, hủy đơn; đồng bộ số phút nghỉ sang chấm công |
| Chấm công | Chuẩn bị tháng, vào/ra ca, xử lý `MISSING_PUNCH`, ngày lễ và duyệt tăng ca |
| Lương | Lịch sử lương, phụ cấp, kỳ lương, phiếu lương, BHXH/BHYT/BHTN nhân viên và thuế TNCN năm 2026 |
| Báo cáo | Phân bố nhân sự, trình độ, thâm niên và lương cơ bản theo phạm vi |

Chi tiết công thức và giới hạn pháp lý nằm trong [PAYROLL.md](../api/docs/api/PAYROLL.md). Bộ quy tắc thuế và bảo hiểm nạp sẵn chỉ có hiệu lực trong năm 2026; kỳ ngoài phạm vi sẽ dừng và yêu cầu bổ sung quy tắc.

## 3. Kiến trúc

```text
Browser
   │  Cookie HttpOnly
   ▼
Next.js 16 BFF + UI (:3000)
   │  Bearer access token
   ▼
Spring Boot 4 API (:8080)
   │  Controller → Service → Repository
   ▼
PostgreSQL 16 (:5432)
```

- Browser không nhận JWT trong JavaScript.
- Next.js Route Handlers chỉ chuyển tiếp các đường dẫn đã allowlist.
- Spring Security và `@PreAuthorize` kiểm tra permission; service kiểm tra phạm vi `SELF`, `ORG_UNIT`, `LOCATION` hoặc `COMPANY`.
- JPA dùng `ddl-auto=validate`; Flyway là nguồn tạo và thay đổi schema.
- Thời điểm lưu bằng UTC; ngày công hiện được xác định theo `Asia/Ho_Chi_Minh`.

## 4. Công nghệ

| Thành phần | Công nghệ |
| --- | --- |
| Backend | Java 21, Spring Boot 4.0.8, Spring Security, Spring Data JPA, Flyway |
| Database | PostgreSQL 16 |
| Frontend | Next.js 16.3.5 App Router, React 19, TypeScript strict, Tailwind CSS 4 |
| Kiểm tra | JUnit, Mockito, Spring integration tests, ESLint, TypeScript, Next production build |

## 5. Luồng vận hành chính

1. `SYSTEM_ADMIN` khởi tạo Company Owner đầu tiên.
2. Company Owner/HR tạo cơ cấu tổ chức, vị trí, ca làm và hồ sơ nhân viên.
3. Nhân viên kích hoạt account; role `EMPLOYEE/SELF` được cấp theo workflow.
4. HR tạo phân công, lương, hồ sơ bảo hiểm/thuế và chuẩn bị dữ liệu chấm công tháng.
5. Nhân viên chấm công và gửi đơn nghỉ; HR xử lý dữ liệu thiếu và duyệt tăng ca.
6. Kế toán tạo kỳ, nhập ngày dự kiến trả, tính lương; người khác duyệt; sau thanh toán kỳ được khóa.

## 6. Giới hạn đã biết

- Lịch làm việc cố định thứ 2–thứ 7, trừ ngày lễ công ty; chưa có lịch cá nhân hoặc xoay ca theo ngày.
- Một bản ghi công chỉ có một cặp vào/ra và một đơn nghỉ trong ngày; nghỉ nửa ngày cần HR đối soát.
- Chưa tính phụ trội làm đêm, thưởng, hoa hồng, truy thu, hoàn thuế, quyết toán năm hoặc người không cư trú.
- Quy tắc thuế/bảo hiểm chỉ được seed đến hết năm 2026 và phải cập nhật bằng migration khi chính sách thay đổi.
- Dữ liệu nhạy cảm được kiểm soát bằng quyền và phạm vi; chưa có mã hóa cấp cột trong ứng dụng.

## 7. Tài liệu liên quan

- [Cài đặt](SETUP.md)
- [Cấu trúc source](PROJECT_STRUCTURE.md)
- [Cơ cấu tổ chức](ORGANIZATION_STRUCTURE.md)
- [Danh mục API](../api/docs/api/README.md)
- [Tài liệu web](../apps/web/docs/README.md)
- [Schema runtime và lịch sử migration](../api/docs/database/DATABASE_SCHEMA.md)
