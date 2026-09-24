# Đánh giá Truy vấn 0.1 — HRM

> **Ngày đánh giá:** 2026-09-24  
> **Đối tượng:** Repository, service đọc/ghi dữ liệu và khả năng hỗ trợ của index trong migration V1–V8.  
> **Bối cảnh:** HRM một doanh nghiệp; ưu tiên đúng người, đúng phạm vi, đúng kỳ công/lương.  
> **Kết quả:** **5.3 / 10** — lookup cơ bản và tải quyền đã có nền tốt; truy vấn lịch sử, payroll và kiểm soát ghi cần được hoàn thiện.

---

## 1. Phương pháp và bảng điểm

Tiêu chí dựa trên các luồng thực tế của đồ án: điều chuyển, thay đổi lương, duyệt đơn/tăng ca, tính lương tháng, xem phiếu lương và quản lý theo đơn vị/địa điểm. Không chấm theo số JOIN đơn thuần; một query nhiều JOIN có điều kiện đúng vẫn có thể phù hợp hơn hàng trăm truy vấn nhỏ.

Nguồn chính là [repository](../../src/main/java/com/htttdn/hrm/repository), [service](../../src/main/java/com/htttdn/hrm/service), [migration](../../src/main/resources/db/migration) và [DATABASE_SCHEMA.md](./DATABASE_SCHEMA.md). Tài liệu tổng quan còn ghi Phase 0 nên không được dùng để kết luận dự án chưa có truy vấn.

**Mức độ bằng chứng:**

- **Đã xác nhận từ code:** Điều kiện lọc thiếu, repository được gọi trong vòng lặp, đường ghi không kiểm tra kỳ khóa.
- **Đã thử SQL trên PostgreSQL 16.13:** DDL và 17 tình huống constraint, có bảng kết quả trong [Schema Review](./SCHEMA_REVIEW_0.1.md).
- **Đã kiểm tra SQL đề xuất:** 7 khối SQL của hai báo cáo thực thi được với tham số mẫu trên DB tạm; query đối soát phân biệt được phiếu khớp/lệch tổng. Đoạn JPQL minh họa chưa chạy qua Hibernate.
- **Cần kiểm thử runtime:** Số SQL do Hibernate sinh, thứ tự flush cụ thể, thời gian chạy, execution plan và hành vi cạnh tranh giữa hai request.

Không suy đoán số lượng nhân viên hoặc tuyên bố truy vấn chắc chắn full scan/timeout. Chưa chạy ứng dụng hoặc đo latency. Điểm dưới đây đánh giá tính đúng và cách tổ chức truy vấn; không phải kết quả benchmark.

| # | Tiêu chí | Trọng số | Điểm /10 | Nhận xét |
|---|---|---:|---:|---|
| Q1 | Lấy đúng dữ liệu theo thời điểm/kỳ lương | 25% | 4.0 | Payroll dùng trạng thái hiện tại, phân công không có ngày kết thúc; có thể bỏ người đã nghỉ và lấy sai đơn vị |
| Q2 | Lọc theo scope và vòng đời dữ liệu | 20% | 5.0 | Có lọc xóa mềm và quyền theo ngày; scope chưa được đưa vào các luồng đọc/ghi nghiệp vụ |
| Q3 | Số lượt truy vấn, fetch và xử lý theo tập | 20% | 5.0 | Tải quyền theo lô tốt; danh sách phiếu có N+1 tường minh, tính lương lặp nhiều SELECT/người |
| Q4 | Index, điều kiện lọc, sắp xếp và phân trang | 15% | 7.5 | Lookup theo nhân viên/kỳ được hỗ trợ; email `LOWER`, hàng chờ duyệt và báo cáo theo tháng cần xem lại |
| Q5 | Tổng hợp và báo cáo có thể giải thích | 10% | 7.0 | Có snapshot/tổng tiền sẵn; thiếu định danh đơn vị và quy ước lịch sử, chưa có query báo cáo đầy đủ để đo |
| Q6 | Tính lại, tính nguyên tử và cập nhật đồng thời | 10% | 5.0 | Có transaction và UNIQUE; thiếu khóa/version, cơ chế thay thế phiếu nháp còn rủi ro |
| | **Tổng** | **100%** | **5.325 → 5.3** | **Ưu tiên tính đúng trước khi tối ưu thêm index** |

Công thức: `Σ(điểm × trọng số) / 100`, làm tròn một chữ số thập phân; dùng cùng thang điểm với Schema Review. Q1 có trọng số cao nhất vì truy vấn nhanh nhưng lấy sai tháng lương vẫn không đáp ứng đồ án.

## 2. Những vấn đề cần sửa

### Q-01 — “Chưa có ngày kết thúc” không có nghĩa “đang hiệu lực tại ngày cần xem”

**Mức độ: Cao · Q1.**

**Bằng chứng:**

- [EmployeeAssignmentRepository](../../src/main/java/com/htttdn/hrm/repository/EmployeeAssignmentRepository.java), dòng 12: `findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull`.
- [EmployeeServiceImpl](../../src/main/java/com/htttdn/hrm/service/impl/EmployeeServiceImpl.java), dòng 195–219, dùng method này khi thay và lấy phân công hiện tại.
- [AttendanceServiceImpl](../../src/main/java/com/htttdn/hrm/service/impl/AttendanceServiceImpl.java), dòng 75–78, dùng method đó khi checkin.
- [PayrollServiceImpl](../../src/main/java/com/htttdn/hrm/service/impl/PayrollServiceImpl.java), dòng 114–115, chọn người đang `ACTIVE/PROBATION`; dòng 138–140 lấy phân công chưa kết thúc khi tính bất kỳ kỳ nào.

**Ba trường hợp tái hiện từ logic:**

1. Ngày 24/09 tạo trước điều chuyển có hiệu lực 01/10. Phân công tháng 9 được đóng 30/09 nên method bỏ qua nó, lấy phân công tháng 10 dù chưa đến ngày áp dụng.
2. Nhân viên làm đến 15/09 rồi nghỉ. Khi tính lương tháng 9, trạng thái hiện tại là `RESIGNED`; danh sách đầu vào loại nhân viên dù đã có công trong tháng.
3. Tháng 10 tính lại kỳ tháng 8: nhân viên còn làm nhưng đã chuyển đơn vị. Phiếu tháng 8 lấy đơn vị hiện tại làm snapshot.

**Hướng xử lý:** Tách rõ ngày hiệu lực của phân công, khoảng tuyển dụng giao với kỳ lương và mốc dùng để phân bổ phiếu. `findFirst` cũng không nên che giấu việc có nhiều phân công chính hợp lệ.

```sql
-- Mẫu đề xuất: phân công tại một ngày cụ thể.
SELECT a.*
FROM employee_assignments a
WHERE a.employee_id = :employee_id
  AND a.is_primary = true
  AND a.effective_from <= :as_of_date
  AND (a.effective_to IS NULL OR a.effective_to >= :as_of_date);

-- Mẫu đề xuất: ứng viên tính lương có thời gian làm việc giao kỳ.
-- Cần bảo vệ quy tắc chỉ xóa mềm hồ sơ tạo nhầm trước khi dùng bộ lọc này.
SELECT e.id
FROM employees e
WHERE e.deleted_at IS NULL
  AND e.hire_date <= :period_end
  AND (e.termination_date IS NULL OR e.termination_date >= :period_start);
```

Nhân viên đã nghỉ trong kỳ cần mốc phân công phù hợp, chẳng hạn ngày làm việc cuối trong kỳ, không mặc định cuối tháng sau khi phân công đã đóng. Nếu chuyển đơn vị giữa tháng, định nghĩa chính sách phân bổ MVP trước khi viết query báo cáo.

Các câu SQL trong tài liệu là đề xuất, placeholder cần được bind. Chúng không phải query đã có trong repository.

### Q-02 — Tải phiếu lương có N+1 tường minh; tính lương đọc lặp theo từng người

**Mức độ: Trung bình, cần xử lý cùng payroll · Q3.**

**N+1 đã thấy trong code:** [PayrollServiceImpl](../../src/main/java/com/htttdn/hrm/service/impl/PayrollServiceImpl.java), dòng 342–343, lấy một page phiếu rồi `.map(this::toResponse)`. Mỗi lần map ở dòng 371 lại gọi `payslipItemRepository.findByPayslipId(...)`.

Với page N phiếu, đường code này phát sinh **N lần gọi SELECT lấy items ngoài SELECT lấy page**; có thể có thêm count query tùy cách Spring Data xử lý page. Đây là N+1 do gọi repository, không cần giả định lazy loading mới kết luận được.

**Sửa phù hợp:** Nếu màn hình danh sách chỉ cần các tổng, trả DTO tóm tắt. Nếu cần cả chi tiết, lấy page phiếu trước, rồi một query `WHERE payslip_id IN (:ids)` cho toàn bộ items và nhóm theo ID. Giữ thứ tự page ban đầu.

Không fetch một collection nhiều dòng rồi trực tiếp phân trang trên kết quả đó. Với mô hình hiện tại, lấy page + query items theo tập ID là cách ít thay đổi nhất.

**Tính lương theo từng người:** Với nhân viên đi hết luồng tính, `calculateForEmployee()` gọi ít nhất năm SELECT tường minh:

| Lần | Dữ liệu | Vị trí trong PayrollServiceImpl |
|---|---|---|
| 1 | Toàn bộ compensation để lọc lương cơ bản | 130 và 251–255 |
| 2 | Phân công chưa có ngày kết thúc | 138–140 |
| 3 | Chấm công của tháng | 145–146 |
| 4 | Lặp lại việc đọc compensation để lấy phụ cấp | 200 và 251–255 |
| 5 | Phiếu đã tồn tại để tính lại | 218 |

Vì vậy chi phí SELECT tường minh tăng xấp xỉ **5 × số nhân viên đủ dữ liệu**, chưa tính truy vấn chung, lazy loading, xóa và insert. `shift.standardWorkMinutes`, tên địa điểm và tên phòng ban có thể làm phát sinh SELECT bổ sung; không kết luận mỗi bản ghi đều phát sinh vì persistence context có thể tái sử dụng entity đã tải.

Nên tải compensation một lần, lọc thời gian ngay trong query, lấy công/phân công/phiếu nháp theo tập nhân viên và nhóm trong bộ nhớ. Có thể chia tập ID để giới hạn bộ nhớ nhưng vẫn phải giữ tính nguyên tử của lần tính kỳ. Thêm index không loại bỏ được các lượt gọi repository lặp này.

```java
// Ví dụ JPQL đề xuất, chưa thêm vào repository.
@Query("""
    SELECT c FROM EmployeeCompensation c
    WHERE c.employee.id IN :employeeIds
      AND c.effectiveFrom <= :asOfDate
      AND (c.effectiveTo IS NULL OR c.effectiveTo >= :asOfDate)
    """)
List<EmployeeCompensation> findEffectiveForEmployees(
    @Param("employeeIds") List<Long> employeeIds,
    @Param("asOfDate") LocalDate asOfDate
);
```

Chọn `asOfDate` theo chính sách lương của kỳ. Nếu cho phép mức lương đổi giữa tháng, một mốc duy nhất không đủ; hiện thiết kế MVP yêu cầu thay đổi từ đầu tháng nhưng service chưa enforce.

### Q-03 — Quyền chức năng chưa gắn với phạm vi dữ liệu cần đọc/ghi

**Mức độ: Cao trước khi mở API nghiệp vụ · Q2.**

[AccountAuthorizationService](../../src/main/java/com/htttdn/hrm/service/AccountAuthorizationService.java) xử lý quyền cộng dồn và override theo từng lần gán, nhưng snapshot cuối chỉ còn role/permission. [EmployeeServiceImpl](../../src/main/java/com/htttdn/hrm/service/impl/EmployeeServiceImpl.java), dòng 126–128, lấy toàn bộ nhân viên chưa xóa; các method đọc phiếu hoặc duyệt đơn nhận ID đích mà chưa đối chiếu phạm vi của actor.

Ví dụ, cùng `employee.read` nhưng quản lý kho 1 chỉ được đọc nhân viên trong kho 1; trưởng chi nhánh được đọc cả kho con. Hai trường hợp không thể phân biệt bằng danh sách permission phẳng.

**Hướng xử lý:** Giữ lần gán vai trò làm nguồn scope; đánh giá hiệu lực quyền theo ngày nghiệp vụ và truy vấn cây đơn vị/địa điểm. Sau đó đưa phạm vi vào điều kiện SELECT/UPDATE, hoặc kiểm tra tương đương trong service. Kiểm tra cả quan hệ actor–đối tượng ở thao tác đọc bằng ID và thao tác ghi, không chỉ ở danh sách.

Mẫu dưới chỉ minh họa cách lấy cây địa điểm; **không phải query authorization đầy đủ**. `:location_id` phải xuất phát từ lần gán quyền đã được kiểm tra, không lấy tùy ý từ client:

```sql
WITH RECURSIVE location_scope AS (
    SELECT id FROM work_locations
    WHERE id = :location_id AND deleted_at IS NULL AND is_active = true
    UNION
    SELECT child.id
    FROM work_locations child
    JOIN location_scope parent ON child.parent_location_id = parent.id
    WHERE child.deleted_at IS NULL AND child.is_active = true
)
SELECT id FROM location_scope;
```

`UNION` trên tập ID tránh lặp vô hạn nếu dữ liệu cây đang có chu trình; vẫn cần chặn chu trình tại lúc ghi. CTE đệ quy là cơ chế có sẵn của PostgreSQL, chưa cần bảng closure cho quy mô cơ cấu hiện tại. [PostgreSQL 16 — Recursive queries](https://www.postgresql.org/docs/16/queries-with.html#QUERIES-WITH-RECURSIVE).

Scope theo ngày cấp quyền và đơn vị lịch sử của dữ liệu là hai mốc khác nhau, cần được quy định. Không mặc định nhân viên chuyển sang chi nhánh mới thì quản lý mới được xem mọi dữ liệu lương cũ. Chưa thấy controller nghiệp vụ trong repository nên chưa xác nhận rò rỉ qua endpoint.

Ngoài ra, `AccountAuthorizationService` dùng `LocalDate.now()` theo timezone hệ thống; chấm công dùng `Asia/Ho_Chi_Minh`, còn company profile có cột timezone. Nên dùng cùng nguồn `Clock`/múi giờ nghiệp vụ để quyền không bắt đầu hoặc hết hạn lệch ngày khi triển khai ở UTC.

### Q-04 — Login không nhất quán về định danh và index

**Mức độ: Trung bình · Q4, liên quan Q2.**

[AccountRepository](../../src/main/java/com/htttdn/hrm/repository/AccountRepository.java), dòng 17–22:

```jpql
WHERE account.username = :login OR LOWER(account.email) = LOWER(:login)
```

UNIQUE hiện có trên `email` gốc không enforce quy tắc này. SQL thử xác nhận hai email chỉ khác hoa/thường cùng được lưu và cùng khớp login. Còn có khả năng username của một người bằng email của người khác.

Đây là vấn đề tính đúng trước khi là hiệu năng. Chọn một quy tắc: chuẩn hóa email, unique theo dạng chuẩn hóa, đồng thời quy định namespace hoặc thứ tự tra cứu username/email không nhập nhằng. Rà soát dữ liệu trước khi thêm constraint.

```sql
-- Một phần giải pháp cho email, chưa giải quyết nhập nhằng username/email.
CREATE UNIQUE INDEX uq_accounts_email_lower
    ON accounts (lower(email));
```

Expression index hỗ trợ điều kiện dùng cùng biểu thức và enforce tính duy nhất theo biểu thức đó. Nó không bảo đảm planner luôn dùng index cho toàn bộ điều kiện OR. [PostgreSQL 16 — Indexes on expressions](https://www.postgresql.org/docs/16/indexes-expressional.html).

### Q-05 — Tính lại phiếu có nguy cơ xung đột UNIQUE và giữ phiếu cũ không còn hợp lệ

**Mức độ: Cao · Q6.**

[PayrollServiceImpl](../../src/main/java/com/htttdn/hrm/service/impl/PayrollServiceImpl.java), dòng 218–243, xóa items, `delete(existing)`, rồi `save(payslip)` với entity mới cho cùng `(payroll_period_id, employee_id)`. [Payslip](../../src/main/java/com/htttdn/hrm/entity/Payslip.java), dòng 31–33, dùng `GenerationType.IDENTITY`.

**Rủi ro cần integration test:** Thứ tự gọi delete/save không bảo đảm DELETE xuống DB trước INSERT. Hibernate có hàng đợi flush và có thể insert trước delete; với UNIQUE người/kỳ, lần tính lại có thể thất bại. Chưa chạy luồng JPA để khẳng định exception cụ thể trong dự án. [Hibernate 7.2 — Flush operation order](https://docs.hibernate.org/orm/7.2/userguide/html_single/#flushing-order).

Giải pháp đơn giản là cập nhật lại header phiếu nháp đang có và thay tập items trong transaction. Nếu chọn xóa rồi tạo lại, phải chủ động bảo đảm thứ tự SQL và kiểm thử trên PostgreSQL, giữ nguyên UNIQUE hiện có.

**Lỗi logic riêng đã thấy:** Các nhánh `return` vì thiếu lương, phân công hoặc công nằm ở dòng 134–155, trước khi dọn phiếu cũ. Nhân viên không còn trong danh sách `ACTIVE/PROBATION` cũng không được xét. Do đó lần tính lại có thể để nguyên phiếu nháp từ lần trước và vẫn đánh dấu kỳ `CALCULATED`.

Phải xác định toàn bộ tập nhân viên cần tính, báo lỗi dữ liệu thiếu hoặc loại bỏ phiếu nháp không còn hợp lệ một cách có chủ đích. Kiểm tra tập kết quả trước khi cho chuyển sang `CALCULATED`, thay vì coi việc vòng lặp chạy xong là đủ.

### Q-06 — Kiểm tra trạng thái rồi cập nhật chưa an toàn khi chạy đồng thời

**Mức độ: Cao · Q6.**

Payroll, duyệt đơn, thay compensation và phân công đều dùng `@Transactional`, nhưng chưa có version/khóa/conditional update tương ứng. Riêng `adjust()` còn kiểm tra khóa kỳ trong khi checkout và duyệt OT không kiểm tra; xem S-03 của [Schema Review](./SCHEMA_REVIEW_0.1.md).

Transaction bảo đảm commit/rollback theo nhóm, không tự ngăn hai transaction cùng đọc trạng thái cũ rồi cùng ghi. PostgreSQL mặc định dùng Read Committed; từng SELECT có snapshot riêng. [PostgreSQL 16 — Transaction isolation](https://www.postgresql.org/docs/16/transaction-iso.html#XACT-READ-COMMITTED).

Ví dụ cần kiểm thử: A đọc kỳ `CALCULATED` để duyệt, B đọc kỳ đó để sửa công, cả hai qua kiểm tra rồi cùng commit. Dùng chung khóa hàng kỳ trên cả hai đường trước khi kiểm tra và ghi, hoặc thiết kế cơ chế version/điều kiện ghi bao phủ cả aggregate. Không chỉ thêm khóa ở `approve()`.

Đối với approve/reject cùng một đơn, có thể dùng version hoặc cập nhật `WHERE status = 'PENDING'` và kiểm tra số dòng bị ảnh hưởng. Đối với lịch sử phân công/lương, kết hợp khóa nhân viên với constraint chống giao nhau.

**Điểm tốt có thể học ngay trong code hiện tại:** [RefreshTokenRepository](../../src/main/java/com/htttdn/hrm/repository/RefreshTokenRepository.java), dòng 17–23, cập nhật có điều kiện `revokedAt IS NULL`; service kiểm tra affected row count. Đây là mẫu kiểm soát một bước chuyển trạng thái tốt, không phải kết luận mọi race condition của phiên đăng nhập đã được kiểm thử.

## 3. Index hiện tại có phù hợp không?

### Các đường truy cập đã được hỗ trợ tốt

| Workload | Index/constraint đang có | Nhận định |
|---|---|---|
| Công một người trong khoảng ngày | UNIQUE `(employee_id, work_date)` | Phù hợp điều kiện bằng employee và khoảng ngày |
| Phiếu của một người trong một kỳ | UNIQUE `(payroll_period_id, employee_id)` | Phù hợp lookup hiện tại |
| Danh sách phiếu của một người | `idx_payslips_employee_period` | Hỗ trợ lọc người; sắp theo tháng thực tế vẫn cần xét bảng kỳ |
| Items của phiếu | `idx_payslip_items_payslip` | Phù hợp query lấy items, nhưng không giải quyết N+1 |
| Phân công theo nhân viên/đơn vị/địa điểm | Ba index ở V4 | Đúng hướng; điều kiện ngày và `is_primary` phải được viết đúng |
| Role assignment/override có hiệu lực | Index theo account hoặc assignment và khoảng ngày | Phù hợp các query lấy quyền hiện có |
| Refresh token | UNIQUE `token_hash`, index account và expires_at | Phù hợp tra token, thu hồi theo account và dọn token hết hạn |

### Các điểm nên điều chỉnh có căn cứ

**1. Attendance đang có hai index gần trùng công dụng cho lookup cá nhân.**

[V6](../../src/main/resources/db/migration/V6__create_attendance_tables.sql) vừa có UNIQUE `(employee_id, work_date)`, vừa có index `(employee_id, work_date DESC)`. Với `employee_id = :id`, B-tree của UNIQUE có thể scan ngược để trả ngày giảm dần. Index DESC có khả năng dư cho workload này; chưa đề xuất xóa trước khi xem các workload khác, nhất là thứ tự kết hợp nhiều nhân viên. [PostgreSQL 16 — Indexes and ORDER BY](https://www.postgresql.org/docs/16/indexes-ordering.html).

**2. Báo cáo công toàn công ty theo tháng có hướng lọc khác truy vấn một nhân viên.**

Index bắt đầu bằng employee phù hợp hồ sơ cá nhân. Nếu báo cáo cần đọc mọi người trong một tháng, ứng viên cần đo là:

```sql
CREATE INDEX idx_attendance_work_date_employee
    ON attendance_records (work_date, employee_id);
```

Không thêm chỉ vì báo cáo có cột ngày; so sánh `EXPLAIN (ANALYZE, BUFFERS)` và chi phí ghi trên dữ liệu đại diện. Thứ tự cột đầu có ảnh hưởng đến phần index cần quét. [PostgreSQL 16 — Multicolumn indexes](https://www.postgresql.org/docs/16/indexes-multicolumn.html).

**3. Index đơn từ chưa khớp mọi cách lọc/sắp xếp trong repository.**

V5 có `(employee_id, status, submitted_at DESC)` và `(request_type, status)`. [EmployeeRequestRepository](../../src/main/java/com/htttdn/hrm/repository/EmployeeRequestRepository.java) còn có `findByStatus(...)` và `findByEmployeeId(...)`. Cột `status` chen giữa có thể khiến index thứ nhất không đáp ứng trực tiếp thứ tự thời gian cho danh sách mọi trạng thái của một người.

Chốt query màn hình hàng chờ duyệt trước. Nếu luôn lọc trạng thái rồi xếp thời gian, ứng viên là `(status, submitted_at DESC, id DESC)`; nếu luôn kèm loại đơn, cân nhắc `(request_type, status, submitted_at DESC, id DESC)`. Dòng DRAFT có `submitted_at = NULL`, nên quy định vị trí NULL hoặc dùng `created_at` cho danh sách nháp.

**4. `Pageable` hiện chấp nhận được cho quy mô đồ án.**

Không trừ điểm chỉ vì dùng OFFSET. Cần sort ổn định có ID làm khóa phụ; bảng lương năm phải sort theo `payroll_periods.year/month`, không mặc định ID kỳ là thứ tự lịch. Chỉ chuyển sang keyset khi lịch sử dài hoặc màn hình yêu cầu duyệt liên tục và đo được lợi ích.

Không thêm index cho mọi FK theo một danh sách máy móc. Các FK actor thường có đường truy cập khác các FK nhân viên/ngày; lựa chọn cần dựa vào truy vấn và cách xóa/cập nhật thực tế.

## 4. Tổng hợp và báo cáo HRM

### Q-07 — Tổng hợp phải dựa vào dữ liệu của đúng kỳ và đúng cấp

**Mức độ: Trung bình · Q5.**

`payslips` đã có tổng theo từng thành phần, nên báo cáo lương tháng toàn công ty có thể aggregate trực tiếp header, không cần JOIN `payslip_items`:

```sql
-- Báo cáo ví dụ: chỉ lấy các kỳ đã được duyệt trở đi.
SELECT pp.year, pp.month,
       COUNT(*) AS employee_count,
       SUM(p.basic_salary_pay) AS basic_pay,
       SUM(p.allowance_pay) AS allowance_pay,
       SUM(p.overtime_pay) AS overtime_pay,
       SUM(p.net_pay) AS net_pay
FROM payroll_periods pp
JOIN payslips p ON p.payroll_period_id = pp.id
WHERE pp.year = :year
  AND pp.status IN ('APPROVED', 'PAID', 'LOCKED')
GROUP BY pp.year, pp.month
ORDER BY pp.year, pp.month;
```

Nếu báo cáo mang nghĩa “đã chi trả”, phải dùng `PAID/LOCKED` hoặc điều kiện thanh toán phù hợp; không trộn các kỳ nháp hay đã hủy vào tổng chính thức.

Hai điểm cần tránh:

- JOIN nhiều dòng công với nhiều compensation của cùng người rồi SUM sẽ nhân số dòng. Ví dụ hai dòng công × ba khoản compensation thành sáu dòng; cần aggregate từng nguồn trước hoặc xử lý các tập riêng.
- Báo cáo lương theo đơn vị cần định danh snapshot ổn định, không GROUP BY tên hiện tại hoặc lấy phân công hiện hành của người đã điều chuyển. Xem S-05.

### Query đối soát tổng dòng lương

CHECK hiện tại chỉ đối chiếu các cột trong `payslips`. Query dưới tìm phiếu thiếu dòng hoặc lệch tổng thành phần; nên chạy trước duyệt/integration test. Đây là query đề xuất, chưa được tích hợp vào service:

```sql
WITH item_totals AS (
    SELECT payslip_id,
           COUNT(*) AS item_count,
           COALESCE(SUM(amount) FILTER
               (WHERE component_type = 'BASIC_SALARY'), 0) AS basic_pay,
           COALESCE(SUM(amount) FILTER
               (WHERE component_type = 'ALLOWANCE'), 0) AS allowance_pay,
           COALESCE(SUM(amount) FILTER
               (WHERE component_type = 'OVERTIME'), 0) AS overtime_pay
    FROM payslip_items
    GROUP BY payslip_id
)
SELECT p.id, p.payroll_period_id, p.employee_id
FROM payslips p
LEFT JOIN item_totals i ON i.payslip_id = p.id
WHERE p.payroll_period_id = :period_id
  AND (
      COALESCE(i.item_count, 0) = 0
      OR p.basic_salary_pay <> COALESCE(i.basic_pay, 0)
      OR p.allowance_pay <> COALESCE(i.allowance_pay, 0)
      OR p.overtime_pay <> COALESCE(i.overtime_pay, 0)
  );
```

Không JOIN thêm chấm công/compensation vào tập items trước SUM. Query này chỉ đối soát header với items, chưa chứng minh số tiền đúng với công, lương cấu hình hoặc chính sách làm tròn. Với mỗi dòng, cần quy định rõ amount đã làm tròn là giá trị có thẩm quyền; `quantity`/`unit_rate` snapshot có precision khác nhau.

Không cần thêm materialized view ở giai đoạn này. Tài liệu nghiệp vụ vốn yêu cầu báo cáo từ dữ liệu nguồn; ưu tiên query đúng, index phù hợp và đo trước khi tăng độ phức tạp.

## 5. Điểm tốt trong truy vấn/JPA hiện có

- **Tải quyền theo tập:** [AccountRoleAssignmentRepository](../../src/main/java/com/htttdn/hrm/repository/AccountRoleAssignmentRepository.java) fetch role; [RolePermissionRepository](../../src/main/java/com/htttdn/hrm/repository/RolePermissionRepository.java) và [AccountPermissionOverrideRepository](../../src/main/java/com/htttdn/hrm/repository/AccountPermissionOverrideRepository.java) dùng `IN` và fetch permission. Luồng thông thường dùng ba query chính, không lặp query cho từng role.
- **Kiểm tra hiệu lực quyền bằng cả hai cận ngày:** Đây là mẫu tốt để áp dụng cho phân công/compensation, thay cách chỉ xét `effectiveTo IS NULL`.
- **Các quan hệ chủ yếu là to-one LAZY:** Không mặc định tải toàn bộ đồ thị khi đọc một entity. Truy cập ID của proxy không phải bằng chứng đủ để kết luận phát sinh JOIN hoặc N+1.
- **`open-in-view=false`:** DTO được map trong các service có transaction, giúp kiểm soát điểm truy cập database rõ hơn.
- **List nghiệp vụ đã có `Page` và lọc xóa mềm ở nhiều nơi:** Không cần thay toàn bộ API phân trang chỉ vì tài liệu mẫu dùng keyset.
- **UNIQUE và CHECK hỗ trợ việc ghi:** Trùng ngày công và tổng tiền sai đã bị PostgreSQL chặn trong thử nghiệm.

`saveAll(items)` không tự chứng minh insert được batch. Entity hiện dùng IDENTITY; nếu số dòng lương trở thành điểm nghẽn, đo SQL trước rồi cân nhắc SEQUENCE/JDBC batching. Đây là tối ưu sau các lỗi tính đúng. [Hibernate 7.2 — Batching](https://docs.hibernate.org/orm/7.2/userguide/html_single/#batch).

## 6. Thứ tự xử lý và tiêu chí kiểm chứng

| Ưu tiên | Việc cần làm | Kết quả cần chứng minh |
|---|---|---|
| Cao | Q-01: Lọc nhân viên/phân công theo kỳ | Điều chuyển tương lai không tác động hiện tại; người nghỉ giữa tháng vẫn được tính; kỳ cũ dùng đúng đơn vị |
| Cao | Q-03: Thực thi scope và actor | Quản lý chi nhánh chỉ xử lý đúng chi nhánh/kho con; nhân viên chỉ đọc phiếu của mình |
| Cao | Q-05: Tính lại phiếu nháp | Chạy tính hai lần vẫn thành công, không trùng phiếu, không giữ phiếu cũ sai tập nhân viên |
| Cao | Q-06: Khóa/điều kiện ghi và kiểm tra kỳ | Duyệt đồng thời sửa công không tạo dữ liệu mâu thuẫn; mọi đường ghi từ chối sửa nguồn của kỳ đã duyệt |
| Nên làm | Q-02: Đọc theo tập, loại N+1 items | Số SELECT lấy items không tăng theo số phiếu trong page; compensation không bị đọc hai lần/người |
| Nên làm | Q-04: Chuẩn hóa định danh login | Mỗi login hợp lệ xác định duy nhất một account; DB ngăn email trùng theo quy tắc ứng dụng |
| Nên làm | Q-07: Đối soát và báo cáo lịch sử | Tổng header bằng tổng items; tổng báo cáo bằng tổng các phiếu thuộc đúng trạng thái/kỳ |
| Sau khi có dữ liệu đại diện | Rà soát index, sort, batching | Có SQL/plan và số đo trước–sau; chỉ thêm hoặc bỏ index có bằng chứng |

Các kiểm tra runtime còn cần thực hiện:

1. Integration test PostgreSQL/JPA cho tính lại phiếu và ràng buộc mới; kiểm tra SQL flush thực tế.
2. Test hai transaction cho điều chuyển, thay lương, approve/reject đơn và duyệt lương/sửa công.
3. Theo dõi số SQL trên page phiếu và một lần tính tháng; tách SELECT, INSERT, UPDATE, DELETE.
4. `EXPLAIN (ANALYZE, BUFFERS)` cho công theo người, công theo tháng và hàng chờ duyệt trên dữ liệu giả có quy mô đại diện.

Chưa thực hiện các phép đo này trong lần review. Các con số số lượt repository nêu ở trên xuất phát từ code, không được trình bày như kết quả profiler hay benchmark.
