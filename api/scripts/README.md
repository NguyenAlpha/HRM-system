# Nạp dữ liệu nhân viên demo

[`seed_demo_employees.sql`](seed_demo_employees.sql) triển khai bộ dữ liệu trong
[`docs/DEMO_EMPLOYEE_SEED.md`](../../docs/DEMO_EMPLOYEE_SEED.md). Script chỉ dành cho
database demo/test và không nằm trong Flyway hay luồng khởi động mặc định.

Chạy từ thư mục gốc `HRM-system`, sau khi API đã khởi động ít nhất một lần để tạo schema,
danh mục, RBAC và một account `SYSTEM_ADMIN` đang hoạt động. Database phải đang chạy:

```powershell
docker compose cp api/scripts/seed_demo_employees.sql database:/tmp/seed_demo_employees.sql
docker compose exec -T database psql -v ON_ERROR_STOP=1 -U hrm-user -d hrm -f /tmp/seed_demo_employees.sql
```

Lần nạp đầu trên database chưa có dữ liệu `DEMO-*` sẽ có 36 nhân viên, 37 phân công
(gồm lịch sử đổi quản lý của SR), 70 dòng lương, 36 hồ sơ payroll, 10 người phụ thuộc,
36 hạn mức phép năm 2026, 8 đơn nghỉ và 1.236 bản ghi chấm công. Hai ngày nghỉ công ty
là 02–03/09/2026. Script in các số đếm chính trước khi `COMMIT`.

Có thể chạy lại script để bổ sung bản ghi còn thiếu. Script không cập nhật hồ sơ hoặc
chấm công đã được chỉnh sửa. Nếu mã nhân viên hay account `DEMO-*` trùng một danh tính
khác, transaction sẽ dừng. Các account demo ở trạng thái `PENDING`, không có mật khẩu,
token hay role nghiệp vụ. Admin hiện có được ghi vào các cột audit bắt buộc, kể cả người
duyệt đơn nghỉ lịch sử. Muốn đăng nhập bằng account demo, hãy provision token kích hoạt và
cấp role nghiệp vụ qua luồng của ứng dụng.

Tháng 10 chỉ có chấm công tới ngày 10/10; bản ghi `MISSING_PUNCH` của INT ngày 10/10
được giữ để thử luồng xử lý trước khi tính lương tháng 10. Script không tạo kỳ lương hoặc
phiếu lương.

## Kỳ lương và phiếu lương lịch sử

Sau khi nạp bộ nhân viên trên, chạy API và đảm bảo có account được cấp `payroll.calculate`
(bộ `UserSeeder` tùy chọn tạo `payroll01`). Script dưới đây nạp ngày công chuẩn giả lập
cho tháng 7–8/2026, tạo kỳ tháng 7–10, rồi gọi **PayrollService** để tính phiếu tháng 7–9:

```powershell
./api/scripts/calculate_demo_payroll_history.ps1
```

Script hỏi mật khẩu của `payroll01`. Có thể đặt `DEMO_PAYROLL_PASSWORD` trong terminal nếu
chạy tự động; thay `-PayrollUsername` và `-ApiBaseUrl` khi cần. Cần Docker Compose,
PostgreSQL và API đang chạy. Lệnh có thể chạy lại: bản ghi chấm công và kỳ đã có được
giữ nguyên; kỳ đã tính sẽ không bị tính lại.

Trên database demo ban đầu, kết quả là 34 phiếu tháng 7, 34 phiếu tháng 8 và 35 phiếu
tháng 9 (tổng 103). Tháng 10 chỉ có kỳ `DRAFT` vì chấm công chưa đủ và còn
`MISSING_PUNCH`; không có phiếu tháng 10. Ba kỳ lịch sử ở trạng thái `CALCULATED`.
HR hoặc kế toán có quyền `report.payroll.read` xem được phiếu; nhân viên chỉ xem phiếu
sau khi kỳ được người có quyền duyệt qua luồng ứng dụng. Seeder không tự ghi nhận đã duyệt
hoặc đã trả lương.

Trang `/payslips` mở hồ sơ nhân viên gắn với account đang đăng nhập. Account `hr01` của
`UserSeeder` gắn với `EMP002`, một hồ sơ tối giản riêng và không có phiếu lịch sử. Để xem
phiếu của bộ nhân viên 36 người, chọn ID nhân viên `DEMO-*` trong ô **Mã nhân viên**;
có thể tra ID từ danh sách nhân viên hoặc bằng mã trong database.
