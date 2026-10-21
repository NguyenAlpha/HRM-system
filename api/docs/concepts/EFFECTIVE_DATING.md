# Dữ liệu theo khoảng hiệu lực (effective dating)

Nhiều dữ liệu trong HRM không phải "một giá trị hiện tại" mà là **lịch sử theo thời gian**: lương tháng 3 khác lương
tháng 9, nhân viên đổi phòng giữa năm, quy tắc thuế đổi theo luật. Tài liệu giải thích quy ước chung cho các bảng
này và cách database chặn dữ liệu chồng lấn.

## 1. Quy ước

Mỗi dòng có hai cột `DATE`:

| Cột | Ý nghĩa |
|:----|:--------|
| `effective_from` | Ngày bắt đầu có hiệu lực, **tính cả ngày này** |
| `effective_to` | Ngày cuối cùng có hiệu lực, **tính cả ngày này**; `NULL` là chưa kết thúc |

Một dòng có hiệu lực tại ngày `d` khi:

```sql
effective_from <= d AND (effective_to IS NULL OR effective_to >= d)
```

Mọi truy vấn kiểu `findEffective(…, date)` và `findActive…(…, date)` trong repository đều dùng điều kiện này. Mỗi
bảng còn có `CHECK (effective_to IS NULL OR effective_to >= effective_from)`.

## 2. Các bảng dùng quy ước này

| Bảng | Nội dung | Không được chồng lấn theo |
|:-----|:---------|:--------------------------|
| `employee_assignments` | Phân công (đơn vị, địa điểm, chức danh, quản lý, loại hợp đồng) | Cùng nhân viên, **chỉ phân công chính** (`WHERE is_primary`) |
| `employee_salary_history` | Lương cơ bản | Cùng nhân viên |
| `position_allowance_rules` | Phụ cấp theo chức danh | Cùng chức danh |
| `seniority_allowance_rules` | Bậc phụ cấp thâm niên | Khoảng số năm (`int4range`) **và** khoảng ngày cùng chồng nhau |
| `employee_payroll_profiles` | Hồ sơ bảo hiểm/thuế của nhân viên | Cùng nhân viên |
| `employee_tax_dependents` | Người phụ thuộc | Cùng nhân viên và cùng tên (không phân biệt hoa thường) |
| `payroll_tax_rules`, `payroll_insurance_rules` | Quy tắc thuế TNCN, bảo hiểm | Toàn bảng (mỗi ngày chỉ một quy tắc) |
| `account_role_assignments` | Gán role cho account | Kiểm tra ở service (`ROLE_ASSIGNMENT_EXISTS`), không có ràng buộc DB |
| `account_permission_overrides` | Cấp thêm hoặc thu hồi một permission | Cùng lần gán role và cùng permission |
| `role_assignment_requests` | Đề xuất cấp role đang chờ duyệt | Cùng account, role, scope (`WHERE status = 'PENDING' AND request_type = 'GRANT'`) |

Đơn nghỉ đã duyệt (`leave_requests`) dùng cùng ý tưởng nhưng theo **thời điểm** (`tstzrange(start_at, end_at, '[)')`,
`WHERE status = 'APPROVED'`), để chặn hai đơn đã duyệt trùng giờ.

## 3. Database chặn chồng lấn: `EXCLUDE USING GIST`

Ví dụ trong [`V4__employment_and_salary.sql`](../../src/main/resources/db/migration/V4__employment_and_salary.sql):

```sql
CONSTRAINT excl_employee_salary_history_overlap
    EXCLUDE USING GIST (
        employee_id WITH =,
        DATERANGE(effective_from, effective_to + 1, '[)') WITH &&
    )
```

Đọc là: "không được có hai dòng mà `employee_id` **bằng nhau** và khoảng ngày **giao nhau** (`&&`)".

- `btree_gist` (bật trong `V1__organization.sql`) cho phép dùng toán tử `=` của kiểu thường trong index GiST.
- `DATERANGE(from, to + 1, '[)')` biến khoảng "tính cả hai đầu" thành khoảng nửa mở chuẩn của PostgreSQL. Khi
  `effective_to` là `NULL`, `to + 1` cũng là `NULL`, nghĩa là cận trên vô hạn. Riêng
  `account_permission_overrides` viết `daterange(from, to, '[]')`, cho kết quả tương đương.
- Ràng buộc có thể có `WHERE` (exclusion constraint một phần), ví dụ chỉ áp cho phân công chính.

**Vì sao không chỉ kiểm tra ở service:** hai transaction chạy song song có thể cùng đọc thấy "chưa có dòng chồng lấn"
rồi cùng ghi. Ràng buộc trong database là chốt chặn cuối cùng. Vi phạm sinh `DataIntegrityViolationException`,
được `GlobalExceptionHandler` (hoặc service bắt riêng) đổi thành `409 CONFLICT`.

## 4. Cách ghi: đóng dòng cũ, mở dòng mới

Lương cơ bản, phụ cấp và phân công chính dùng cùng một mẫu:

1. Khóa dòng "cha" bằng `SELECT … FOR UPDATE` (nhân viên hoặc chức danh) để các thay đổi trên cùng đối tượng chạy
   lần lượt.
2. Tìm dòng đang mở (`effective_to IS NULL`), cũng khóa `FOR UPDATE`. Có nhiều hơn một dòng mở thì báo `409`.
3. Ngày bắt đầu mới phải **sau** ngày bắt đầu của dòng đang mở.
4. Đóng dòng cũ: `effective_to = effective_from mới − 1 ngày`.
5. `flush()` để câu `UPDATE` xuống database trước, rồi mới `INSERT` dòng mới. Nếu không, ràng buộc exclusion (kiểm tra
   ngay ở từng câu lệnh) sẽ thấy hai dòng chồng nhau.

```text
Trước:  [2025-01-01 ............................ NULL]
Đặt lương mới từ 2026-04-01
Sau:    [2025-01-01 ... 2026-03-31] [2026-04-01 ... NULL]
```

Thay đổi có ngày hiệu lực lùi về quá khứ có thể ảnh hưởng kỳ lương đã tính, xem
[PAYROLL_LIFECYCLE.md](./PAYROLL_LIFECYCLE.md#4-thay-đổi-dữ-liệu-hồi-tố).

## 5. Thu hồi khác với kết thúc

Gán role và permission override có thêm cột `revoked_at`. Thu hồi đặt `revoked_at` (và kéo `effective_to` về hôm
nay nếu đang hiệu lực). Truy vấn "đang hiệu lực" lọc thêm `revoked_at IS NULL`, nên quyền mất **ngay**, không đợi
hết ngày. Kết thúc tự nhiên (`effective_to` trong quá khứ) thì giữ nguyên lịch sử, không cần `revoked_at`.

## 6. Múi giờ của "hôm nay"

Ngày nghiệp vụ của công ty là giờ Việt Nam (`Asia/Ho_Chi_Minh`). Seeder, job định kỳ và `mark-paid` dùng múi giờ này
tường minh, nhưng **phần lớn service gọi `LocalDate.now()` không truyền múi giờ**, tức theo múi giờ mặc định của JVM.
Ứng dụng không tự đặt múi giờ mặc định.

Nếu máy chủ chạy UTC (mặc định thường gặp của container và App Service Linux), từ 00:00 đến 07:00 giờ Việt Nam,
"hôm nay" ở các service đó vẫn là ngày hôm trước: role hết hạn chưa mất, phân công mới chưa có hiệu lực, phạm vi dữ
liệu tính theo ngày cũ. Cách khắc phục là đặt múi giờ cho JVM (ví dụ biến môi trường `TZ=Asia/Ho_Chi_Minh` hoặc
`-Duser.timezone=Asia/Ho_Chi_Minh`), hoặc dùng một `Clock` theo giờ Việt Nam trong code.

## Code

- Migration: [`V3__rbac.sql`](../../src/main/resources/db/migration/V3__rbac.sql),
  [`V4__employment_and_salary.sql`](../../src/main/resources/db/migration/V4__employment_and_salary.sql),
  [`V5__leave.sql`](../../src/main/resources/db/migration/V5__leave.sql),
  [`V7__payroll.sql`](../../src/main/resources/db/migration/V7__payroll.sql)
- Ghi theo mẫu đóng/mở: [`EmployeeSalaryHistoryService.setSalary`](../../src/main/java/com/htttdn/hrm/service/EmployeeSalaryHistoryService.java),
  [`AllowancePolicyService`](../../src/main/java/com/htttdn/hrm/service/AllowancePolicyService.java),
  [`EmployeeAssignmentService`](../../src/main/java/com/htttdn/hrm/service/EmployeeAssignmentService.java)
- Xử lý vi phạm ràng buộc: [`GlobalExceptionHandler`](../../src/main/java/com/htttdn/hrm/exception/GlobalExceptionHandler.java)
