# Đánh giá Schema 0.1 — HRM

> **Ngày đánh giá:** 2026-09-24  
> **Đối tượng:** Thiết kế dữ liệu, SQL migration V1–V8 và các service thực thi tính toàn vẹn dữ liệu.  
> **Bối cảnh:** Một doanh nghiệp bán lẻ/phân phối; một trụ sở, hai chi nhánh và các kho là địa điểm làm việc. Payroll gồm lương cơ bản, phụ cấp, tăng ca.  
> **Stack trong repository:** PostgreSQL 16 · Java 21 · Spring Boot 4.0.8 · Spring Data JPA · Flyway.  
> **Kết quả:** **6.7 / 10** — mô hình phù hợp đồ án, nhưng lịch sử hiệu lực và dữ liệu đầu vào/đóng kỳ lương cần được bảo vệ chắc hơn.

---

## 1. Phạm vi và cách chấm

Đây là lần đánh giá đầu tiên của HRM, không phải bản tiếp theo của QuickTech POS. Bốn tài liệu QuickTech chỉ được tham khảo về bố cục: bảng điểm, vấn đề có dẫn chứng, điểm mạnh và thứ tự xử lý.

Tiêu chí được xây dựng từ [DATABASE_SCHEMA.md](./DATABASE_SCHEMA.md), đặc biệt các mục 3, 4, 6, 7, 8 và 10.2. Không trừ điểm vì thiếu multi-tenant, tồn kho hàng hóa, hoa hồng, bảo hiểm, thuế, số dư phép năm hay audit log toàn hệ thống: các chức năng này nằm ngoài phạm vi đã xác định. Không yêu cầu partition, read replica hay materialized view khi chưa có bằng chứng cần thiết.

### Căn cứ và giới hạn

| Nguồn | Cách sử dụng |
|---|---|
| [.claude/CLAUDE.md](../../../.claude/CLAUDE.md) | Hướng dẫn làm việc: kiểm chứng, chỉ thay đổi phần được yêu cầu |
| [OVERVIEW.md](../../../docs/OVERVIEW.md) | Tài liệu này còn ghi Phase 0, chưa phản ánh mã nguồn hiện tại |
| [DATABASE_SCHEMA.md](./DATABASE_SCHEMA.md), [ENTITY_ATTRIBUTES.md](./ENTITY_ATTRIBUTES.md) | Ý định nghiệp vụ và mô hình 19 bảng ban đầu |
| [Migration V1–V8](../../src/main/resources/db/migration) | Căn cứ cho cấu trúc được tạo thực tế: **20 bảng**, có thêm `refresh_tokens` |
| [Entity](../../src/main/java/com/htttdn/hrm/entity), [service](../../src/main/java/com/htttdn/hrm/service) | Kiểm tra quy tắc nào đã được thực thi, quy tắc nào mới được mô tả |

Đã chạy SQL V1–V8 bằng `psql` trên PostgreSQL **16.13** trong container tạm, không mở cổng ra host, không dùng database hay dữ liệu hiện có. Container đã được xóa sau kiểm tra. Đây là kiểm tra DDL và dữ liệu thử, **không phải** chạy ứng dụng, Flyway engine, kiểm thử tải hay kiểm thử đồng thời qua JPA.

Kết quả catalog: **20 bảng, 38 FK, 42 CHECK, 20 PK, 14 UNIQUE constraint**; **55 index tổng cộng**, gồm index phục vụ PK/UNIQUE, 5 partial unique index và 16 index thường. Số lượng index không được dùng thay cho việc xem xét truy vấn thực tế.

Đánh giá sử dụng nội dung working tree tại thời điểm đọc, kể cả các thay đổi đang có của người phát triển. Chỉ bổ sung tài liệu review; không sửa schema hoặc mã nghiệp vụ.

### Thang điểm

- **9–10:** Đáp ứng tốt phạm vi đồ án và có bảo vệ/kiểm chứng cho các tình huống quan trọng.
- **7–8.5:** Mô hình hợp lý, còn thiếu một số ràng buộc hoặc kiểm chứng.
- **5–6.5:** Đã có cấu trúc chính, nhưng tình huống nghiệp vụ hợp lệ có thể tạo dữ liệu sai hoặc không nhất quán.
- **0–4.5:** Thiếu hoặc sai thành phần cốt lõi của tiêu chí.

Điểm tổng là `Σ(điểm × trọng số) / 100`, làm tròn một chữ số thập phân. Trọng số cao hơn dành cho payroll vì đây là nơi nhiều nguồn dữ liệu hội tụ. Điểm là nhận định kỹ thuật dựa trên bằng chứng, không phải tỷ lệ test pass. Vấn đề ưu tiên cao vẫn phải xử lý dù điểm trung bình khá.

## 2. Bảng điểm

| # | Tiêu chí phù hợp HRM | Trọng số | Điểm /10 | Căn cứ cho điểm |
|---|---|---:|---:|---|
| S1 | Phản ánh phạm vi đồ án | 10% | 9.0 | Đủ cơ cấu, nhân sự, đơn từ, chấm công, lương và RBAC; một ca chính/ngày cần được ghi rõ là giới hạn MVP |
| S2 | Chuẩn hóa và định danh nghiệp vụ | 10% | 8.0 | Tách hồ sơ/tài khoản, tổ chức/địa điểm và các khoản lương tốt; snapshot đơn vị trên phiếu chỉ có tên, thiếu định danh ổn định để báo cáo |
| S3 | Kiểu dữ liệu và thời gian | 10% | 8.0 | Tiền `NUMERIC`/`BigDecimal`, thời điểm `TIMESTAMPTZ`/`Instant`, ngày `DATE`; nguồn múi giờ chưa thống nhất, công chuẩn chưa snapshot |
| S4 | Khóa và ràng buộc trong từng bản ghi | 10% | 6.0 | FK, enum, số không âm và UNIQUE chính đã có; thiếu thứ tự ngày/giờ, cấu trúc cây và liên hệ trạng thái–người thực hiện |
| S5 | Lịch sử phân công và lương theo hiệu lực | 10% | 5.0 | Có khoảng hiệu lực nhưng không chặn chồng khoảng, ngày kết thúc trước ngày bắt đầu; service chỉ đóng dòng chưa có ngày kết thúc |
| S6 | Nguồn chấm công, nghỉ hưởng lương và công chuẩn | 10% | 5.5 | Có snapshot giờ ca, tăng ca có người duyệt; chưa đảm bảo đủ ngày công, công chuẩn còn đọc từ danh mục ca hiện tại |
| S7 | Toàn vẹn phiếu lương và đóng kỳ | 15% | 5.5 | Snapshot tiền và CHECK tổng tốt; bảo vệ sau duyệt chưa kín, thiếu đối soát tổng dòng với tổng phiếu và cơ chế chống ghi đồng thời |
| S8 | Mô hình RBAC và dữ liệu nhạy cảm | 10% | 7.0 | Scope có FK/CHECK rõ, mật khẩu và refresh token được hash; truy vấn nghiệp vụ chưa thực thi scope, quy tắc email đăng nhập chưa thống nhất |
| S9 | Vòng đời, xóa mềm và truy vết tối thiểu | 5% | 7.0 | Phân biệt nghỉ việc/xóa nhầm, giữ dữ liệu lịch sử; xóa hồ sơ đang có nghiệp vụ và thiếu người/lý do xóa chưa được chặn đầy đủ |
| S10 | Migration, tài liệu và kiểm chứng dữ liệu | 10% | 6.5 | SQL V1–V8 dựng được DB mới, `ddl-auto=validate`; tài liệu lệch phiên bản và chưa thấy integration test cho các invariant HRM chính |
| | **Tổng** | **100%** | **6.675 → 6.7** | **Nền thiết kế tốt; cần hoàn thiện tính đúng của lịch sử và chốt lương** |

Chi phí truy vấn, cách fetch dữ liệu, index theo workload và các thao tác đọc/ghi được chấm riêng trong [QUERY_REVIEW_0.1.md](./QUERY_REVIEW_0.1.md). Không lấy trung bình hai báo cáo vì có một số góc nhìn liên quan cùng vấn đề.

## 3. Ba vấn đề cần ưu tiên

### S-01 — Lịch sử hiệu lực chưa được bảo vệ

**Mức độ: Cao · Ảnh hưởng S5, liên quan S4.**

**Bằng chứng:** [V4](../../src/main/resources/db/migration/V4__create_assignment_and_compensation_tables.sql), dòng 3–41, chưa có CHECK thứ tự ngày hoặc exclusion constraint. [EmployeeServiceImpl](../../src/main/java/com/htttdn/hrm/service/impl/EmployeeServiceImpl.java), dòng 195–196 và 232–240, chỉ tìm dòng `effective_to IS NULL` rồi đóng tại ngày trước mốc mới.

Đã kiểm chứng bằng SQL: database chấp nhận hai phân công chính chồng thời gian, hai `BASIC_SALARY` cùng mã `BASE` đồng thời có hiệu lực và phân công có `effective_to < effective_from`.

**Tình huống:** Đang có phân công B từ 01/09, nhập bổ sung phân công A từ 01/08. Service có thể đóng B vào 31/07, tạo một khoảng ngược. Hai yêu cầu điều chuyển đồng thời cũng có thể cùng đọc một dòng cũ rồi tạo hai phân công chính mới. `@Transactional` không tự làm cho hai yêu cầu này chạy tuần tự.

**Hướng xử lý:**

1. Thống nhất khoảng ngày bao gồm cả hai đầu, phù hợp cách service hiện đóng tại `effectiveFrom.minusDays(1)`.
2. Thêm CHECK thứ tự ngày cho phân công, compensation, gán vai trò và ngoại lệ quyền.
3. Chặn giao nhau của phân công chính; chặn giao nhau theo mã khoản lương, đồng thời chặn hai lương cơ bản dù dùng hai mã khác nhau.
4. Kiểm tra cả lịch sử khi nhập hồi tố; khóa cùng nhân viên trong transaction cập nhật lịch sử. Chốt quy tắc ngày đầu tháng của compensation; DTO/service hiện chưa thực thi quy tắc này trong tài liệu.

SQL minh họa cho migration mới, **chưa áp dụng vào dự án**:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE employee_assignments
  ADD CONSTRAINT chk_assignment_dates
    CHECK (effective_to IS NULL OR effective_to >= effective_from),
  ADD CONSTRAINT ex_primary_assignment_period
    EXCLUDE USING gist (
      employee_id WITH =,
      daterange(effective_from, effective_to, '[]') WITH &&
    ) WHERE (is_primary);

ALTER TABLE employee_compensations
  ADD CONSTRAINT chk_compensation_dates
    CHECK (effective_to IS NULL OR effective_to >= effective_from),
  ADD CONSTRAINT ex_compensation_code_period
    EXCLUDE USING gist (
      employee_id WITH =,
      component_code WITH =,
      daterange(effective_from, effective_to, '[]') WITH &&
    ),
  ADD CONSTRAINT ex_basic_salary_period
    EXCLUDE USING gist (
      employee_id WITH =,
      daterange(effective_from, effective_to, '[]') WITH &&
    ) WHERE (component_type = 'BASIC_SALARY');
```

`NULL` ở cận trên biểu diễn khoảng chưa có ngày kết thúc. Partial UNIQUE chỉ áp dụng cho `effective_to IS NULL` không đủ ngăn hai khoảng hữu hạn giao nhau. Cách dùng range và exclusion constraint này được PostgreSQL hỗ trợ trực tiếp. [PostgreSQL 16 — Constraints on ranges](https://www.postgresql.org/docs/16/rangetypes.html#RANGETYPES-CONSTRAINT).

Phải xử lý dữ liệu vi phạm trước khi thêm constraint. Service cũng cần thực hiện việc đóng khoảng cũ trước khi insert khoảng mới ở mức SQL; không chỉ dựa vào thứ tự gọi setter/save của JPA.

### S-02 — Chưa có nguồn công chuẩn đầy đủ và ổn định cho kỳ lương

**Mức độ: Cao · Ảnh hưởng S6, liên quan S3.**

**Bằng chứng:** [V6](../../src/main/resources/db/migration/V6__create_attendance_tables.sql), dòng 3–31, snapshot giờ vào/ra dự kiến nhưng không snapshot số phút công chuẩn hoặc nghỉ giữa ca. [PayrollServiceImpl](../../src/main/java/com/htttdn/hrm/service/impl/PayrollServiceImpl.java), dòng 145–155, tính mẫu số bằng tổng `record.getShift().getStandardWorkMinutes()` của những bản ghi chấm công đã tồn tại.

**Hậu quả cụ thể:**

- Nếu tháng chỉ có một bản ghi với 480 phút công chuẩn và 480 phút được trả, công thức hiện tại cho `lương tháng × 480 / 480`: nhận đủ lương tháng. Luồng tính chưa xác nhận rằng các ngày còn lại đã được ghi là đi làm, nghỉ hay vắng mặt.
- Đổi `work_shifts.standard_work_minutes` sau khi đã chấm công làm thay đổi mẫu số khi tính lại kỳ nháp. Snapshot giờ bắt đầu/kết thúc chưa giải quyết được việc này.
- [EmployeeRequestServiceImpl](../../src/main/java/com/htttdn/hrm/service/impl/EmployeeRequestServiceImpl.java), dòng 116–139, duyệt nghỉ phép mới đổi trạng thái đơn; chưa chuyển ngày nghỉ có lương thành nguồn `payable_minutes` mà payroll đang sử dụng.
- Checkout ở [AttendanceServiceImpl](../../src/main/java/com/htttdn/hrm/service/impl/AttendanceServiceImpl.java), dòng 127–133, lấy thời gian giữa hai lần chấm và giới hạn theo công chuẩn, chưa xử lý `break_minutes`. Ca 08:00–17:00 nghỉ 60 phút, về 16:00 vẫn có thể được tính đủ 480 phút.

Đây là kết luận từ đường đi trong code và phép tính, chưa phải kết quả chạy API payroll.

**Hướng xử lý vừa đủ cho đồ án:** Xác định lịch làm việc chuẩn của tháng rồi tạo đủ các ngày cần theo dõi; ngày vắng phải hiện diện hoặc được phát hiện là dữ liệu chưa hoàn tất. Lưu công chuẩn áp dụng cho từng ngày, cùng thông tin nghỉ giữa ca cần thiết. Có thể mở rộng `attendance_records` hiện có, chưa cần một hệ thống xếp lịch mới.

Duyệt nghỉ phép phải cập nhật hoặc cung cấp đầu vào xác định được cho ngày công tương ứng. Chỉ một nguồn chịu trách nhiệm về phút hưởng lương, tránh cộng cả đơn nghỉ và bản ghi chấm công lần nữa. Trước tính lương, báo rõ nhân viên/ngày thiếu dữ liệu; không âm thầm bỏ qua.

### S-03 — Bảo vệ kỳ lương đã duyệt chưa bao phủ mọi đường ghi

**Mức độ: Cao · Ảnh hưởng S7.**

**Điểm đã có:** `calculate()` từ chối kỳ đã duyệt; `approve()` kiểm tra người duyệt khác người tính. CHECK trên `payslips` bảo vệ `gross_pay = basic_salary_pay + allowance_pay + overtime_pay` và `net_pay = gross_pay`.

**Khoảng trống có bằng chứng:**

- [AttendanceServiceImpl](../../src/main/java/com/htttdn/hrm/service/impl/AttendanceServiceImpl.java), dòng 158–170, có kiểm tra kỳ đã duyệt trong `adjust()`. `checkOut()` và `approveOvertime()` tại dòng 113–154 không có kiểm tra tương ứng.
- [V7](../../src/main/resources/db/migration/V7__create_payroll_tables.sql) không bảo vệ nội dung phiếu/dòng lương theo trạng thái bảng cha; tổng dòng chi tiết chưa được đối soát với tổng phiếu ở DB.
- SQL thử đã sửa được số tiền dòng lương và phút công sau khi đặt kỳ thành `APPROVED`. SQL cũng chấp nhận kỳ `APPROVED` thiếu người duyệt hoặc dùng cùng người tính/người duyệt. Đây là thiếu bảo vệ ở DB; riêng service `approve()` đã có kiểm tra tách người.
- Chưa thấy `@Version`, khóa bi quan hoặc cập nhật có điều kiện trạng thái trong các entity/repository nghiệp vụ. Hai transaction sửa công và duyệt lương có thể cùng vượt qua kiểm tra trước khi ghi.

**Hướng xử lý:** Dùng chung kiểm tra kỳ lương cho mọi đường sửa công/tăng ca. Việc tính lại, duyệt và sửa nguồn công phải phối hợp trên cùng khóa kỳ, hoặc một cơ chế đồng thời tương đương có kiểm thử. Chỉ thêm `@Version` ở `PayrollPeriod` chưa đủ bảo vệ transaction chỉ sửa `AttendanceRecord`.

Cho phép service chịu trách nhiệm invariant đúng như mục 10.2 của thiết kế, nhưng cần bảo vệ đầy đủ và kiểm thử bằng database thật. Nếu có đường ghi trực tiếp ngoài ứng dụng, cân nhắc trigger bảo vệ dữ liệu đã duyệt. Không cố dùng CHECK đọc bảng cha/con để thay thế cơ chế này. [PostgreSQL 16 — Check constraints](https://www.postgresql.org/docs/16/ddl-constraints.html#DDL-CONSTRAINTS-CHECK-CONSTRAINTS).

“Bất biến từ APPROVED” nên được diễn đạt là **không sửa nội dung tính lương**. Trạng thái kỳ và thông tin trả/khóa vẫn cần chuyển tiếp `APPROVED → PAID → LOCKED`.

## 4. Các vấn đề tiếp theo

### S-04 — Thiếu CHECK cho một số trạng thái dữ liệu không hợp lệ

**Mức độ: Trung bình · S4.**

| Vị trí | Thiếu bảo vệ | Mức xử lý phù hợp |
|---|---|---|
| [V1](../../src/main/resources/db/migration/V1__create_organization_structure_tables.sql), `work_locations` | Kho không có cha; trụ sở/chi nhánh có cha; tự làm cha | CHECK hình dạng trong một dòng; loại của bản ghi cha cần service/trigger |
| V1, `organization_units` | Tự làm cha hoặc vòng A → B → A | CHECK loại bỏ tự tham chiếu; service kiểm tra chu trình dài hơn |
| [V2](../../src/main/resources/db/migration/V2__create_employee_and_account_tables.sql), `employees` | Kết thúc trước ngày tuyển, xóa mềm thiếu người/lý do | CHECK ngày và nhóm trường xóa mềm |
| [V3](../../src/main/resources/db/migration/V3__create_rbac_tables.sql) | Khoảng ngày vai trò/override bị ngược | CHECK thứ tự ngày; xác định cách xử lý override chồng thời gian |
| [V6](../../src/main/resources/db/migration/V6__create_attendance_tables.sql) | Giờ dự kiến kết thúc trước bắt đầu; có đủ hai mốc chấm nhưng checkout trước checkin | CHECK thứ tự thời điểm; không áp dụng `payable <= worked` vì nghỉ có lương có thể không làm thực tế |
| [V7](../../src/main/resources/db/migration/V7__create_payroll_tables.sql) | Tháng/năm không khớp ngày đầu/cuối kỳ; metadata duyệt không phù hợp trạng thái | CHECK kỳ tháng và nhóm trường hành động; service kiểm soát chuyển trạng thái |

Không bắt database đếm cứng đúng hai chi nhánh hoặc đúng sáu địa điểm. Đây là dữ liệu cấu hình/demo, không phải giới hạn cấu trúc bắt buộc.

### S-05 — Snapshot tên chưa đủ định danh đơn vị trong báo cáo lịch sử

**Mức độ: Trung bình · S2.**

`payslips.work_location_snapshot` và `organization_unit_snapshot` chỉ lưu tên. Mã danh mục là UNIQUE nhưng tên không UNIQUE. Hai đơn vị trùng tên sẽ bị gộp nếu báo cáo nhóm theo snapshot; một đơn vị đổi tên có thể bị tách làm hai nhóm qua các tháng.

Nên giữ tên snapshot để in phiếu, đồng thời lưu ID đơn vị/địa điểm tại lúc chốt phân bổ. Các danh mục đã dùng nên được giữ bằng xóa mềm. Với điều chuyển giữa tháng, chốt quy ước MVP, ví dụ quy toàn bộ phiếu về đơn vị tại cuối kỳ; chỉ cần phân bổ nhiều đơn vị nếu đề bài thực sự yêu cầu.

Không lấy đơn vị hiện tại của nhân viên để giải thích lương quá khứ. Vấn đề này đang xuất hiện trong service, xem Q-01 của [Query Review](./QUERY_REVIEW_0.1.md).

### S-06 — Mô hình scope tốt, nhưng tầng sử dụng chưa giữ đủ ngữ cảnh

**Mức độ: Cao trước khi mở API nghiệp vụ · S8.**

FK scope và `chk_account_role_assignments_scope` đã đúng; thử SQL scope LOCATION không có địa điểm bị từ chối. Tuy nhiên [AccountAuthorizationService](../../src/main/java/com/htttdn/hrm/service/AccountAuthorizationService.java), dòng 64–84 và 116, trả về hai danh sách role/permission, bỏ thông tin đơn vị/địa điểm của lần gán.

Các service nhân sự, đơn từ và lương hiện chưa kiểm tra scope của người gọi. Việc reviewer/approver ID tồn tại không chứng minh người đó có quyền trên nhân viên đích. Repository mới có controller xác thực, vì vậy chưa kết luận đã có endpoint công khai làm lộ dữ liệu; đây là khoảng trống cần hoàn thiện khi nối nghiệp vụ.

Giữ mô hình hiện tại, bổ sung kiểm tra quyền theo **permission + lần gán vai trò + scope + thời hạn**, bao gồm nút con. Không mặc định `rbac.manage` cho phép xem lương. Cần lấy actor từ tài khoản xác thực khi expose API, không tin actor ID gửi trong request.

CCCD và ngân hàng đang lưu bằng cột chuỗi thông thường. Schema đã nhận diện dữ liệu nhạy cảm; DTO nhân viên hiện không trả CCCD/ngân hàng là điểm tốt. Chưa có căn cứ xác nhận mã hóa lưu trữ hoặc chính sách truy cập ngoài ứng dụng. Với đồ án, ưu tiên giới hạn quyền và trường dữ liệu trả về; chưa cần thêm một hệ thống audit toàn diện.

### S-07 — Quy tắc duy nhất của email khác quy tắc tìm tài khoản

**Mức độ: Trung bình · S8.**

[V2](../../src/main/resources/db/migration/V2__create_employee_and_account_tables.sql) dùng UNIQUE trên email gốc; [AccountRepository](../../src/main/java/com/htttdn/hrm/repository/AccountRepository.java), dòng 17–22, tìm bằng `LOWER(email)`. Database thử chấp nhận `reviewer@example.test` và `Reviewer@EXAMPLE.TEST`; điều kiện tìm đăng nhập khớp **hai dòng** dù method trả `Optional<Account>`.

Chuẩn hóa email và áp dụng unique index tương ứng, sau khi rà soát dữ liệu trùng. Đường `username = :login OR LOWER(email) = LOWER(:login)` còn cần quy tắc để username của tài khoản A không nhập nhằng với email của tài khoản B. Chi tiết truy vấn ở Q-04.

### S-08 — Xóa nhầm hồ sơ chưa được phân biệt bằng kiểm tra nghiệp vụ

**Mức độ: Trung bình · S9.**

[EmployeeServiceImpl](../../src/main/java/com/htttdn/hrm/service/impl/EmployeeServiceImpl.java), dòng 287–296, đánh dấu xóa mà chưa kiểm tra hồ sơ đã có công/lương/đơn từ hay chưa. Vì vậy quy ước “chỉ xóa hồ sơ tạo nhầm” mới được mô tả, chưa được bảo vệ.

Nên từ chối luồng xóa nhầm khi đã phát sinh nghiệp vụ, hoặc định nghĩa một luồng xử lý riêng có chủ đích. Dữ liệu lịch sử vẫn phải truy xuất được dù danh mục/hồ sơ bị ẩn khỏi danh sách hiện hành. Tránh áp dụng bộ lọc xóa mềm toàn cục mà vô tình làm mất liên kết lịch sử.

### S-09 — Tài liệu và kiểm thử chưa theo kịp implementation

**Mức độ: Trung bình · S10.**

`OVERVIEW.md` vẫn ghi chưa có schema/auth; tài liệu database và entity mới liệt kê 19 bảng, thiếu refresh token V8. Các test hiện có tập trung auth, authorization, refresh token và seeder; [HrmApplicationTests](../../src/test/java/com/htttdn/hrm/HrmApplicationTests.java) chỉ kiểm tra context.

Nên cập nhật danh mục 20 bảng và thêm kiểm thử invariant trọng tâm: ngày giao nhau, nhập hồi tố, nghỉ việc giữa tháng, thiếu ngày công, thay đổi danh mục ca, tính lại lương, khóa kỳ và hai thao tác đồng thời. Không dùng kiểm thử mock repository để thay thế kiểm tra constraint/flush/locking của PostgreSQL và JPA.

## 5. Điểm mạnh nên giữ

- **Đúng phạm vi một doanh nghiệp:** `company_profile.id = 1`, không thêm `company_id` vào mọi bảng khi chưa có yêu cầu.
- **Tách cơ cấu quản trị khỏi địa điểm làm việc:** Một phòng ban có thể bố trí nhân viên ở trụ sở, chi nhánh hoặc kho.
- **Có lịch sử phân công và compensation:** Đây là nền tảng đúng để xử lý điều chuyển, đổi ca và thay đổi lương.
- **Tách hồ sơ khỏi tài khoản:** Nhân viên không bắt buộc có tài khoản; bootstrap admin có thể không có hồ sơ nhân sự.
- **RBAC có phạm vi và ngoại lệ theo lần gán:** Không dùng cặp type/id không có FK cho scope.
- **Đơn từ có CHECK theo loại:** Phân biệt LEAVE/RESIGNATION cùng tập trường phù hợp, không cần tách thêm bảng chỉ để tăng mức chuẩn hóa.
- **Tiền và thời gian dùng kiểu thích hợp:** Không dùng số thực nhị phân cho tiền; ngày công độc lập với thời điểm chấm công.
- **Snapshot phiếu lương:** Giữ tên nhân viên, khoản tiền và chi tiết tại lúc tính; không phụ thuộc hoàn toàn vào dữ liệu danh mục tương lai.
- **UNIQUE nghiệp vụ đã có:** Một bản ghi công/người/ngày, một kỳ/năm/tháng, một phiếu/người/kỳ; mã nhân viên không tái sử dụng.
- **Bảo vệ credential:** BCrypt cho mật khẩu; V8 chỉ lưu hash SHA-256 của refresh token ngẫu nhiên. Không áp dụng SHA-256 này như cách hash mật khẩu người dùng.
- **Migration tách nhóm rõ ràng:** V1 tạo ca trước khi V4 tham chiếu; FK nhân viên–tài khoản được bổ sung sau khi cả hai bảng tồn tại.

## 6. Kiểm chứng trực tiếp trên PostgreSQL

Mỗi ca dùng dữ liệu giả tối thiểu và transaction riêng. “Chấp nhận” là kết quả thử ghi SQL trực tiếp, không khẳng định mọi đường API cũng chấp nhận. Những quy tắc được chủ đích giao cho service đã được đối chiếu riêng ở phần trên.

| # | Tình huống thử | Kết quả hiện tại | Đánh giá |
|---|---|---|---|
| 1 | Hai phân công chính chồng thời gian | Chấp nhận | Thiếu bảo vệ |
| 2 | Hai lương cơ bản cùng mã cùng hiệu lực | Chấp nhận | Thiếu bảo vệ |
| 3 | Phân công kết thúc trước ngày bắt đầu | Chấp nhận | Thiếu CHECK |
| 4 | Kho không có địa điểm cha | Chấp nhận | Cần bảo vệ cấu trúc |
| 5 | Đơn vị tổ chức là cha của chính nó | Chấp nhận | Cần bảo vệ cấu trúc |
| 6 | Kỳ APPROVED không có người/thời điểm duyệt | Chấp nhận | Thiếu kiểm tra metadata ở DB |
| 7 | Người tính và người duyệt trùng nhau | Chấp nhận | Service có kiểm tra, DB chưa có |
| 8 | Sửa amount dòng phiếu của kỳ APPROVED | Chấp nhận | DB không bảo vệ bất biến/tổng dòng |
| 9 | Sửa phút công trong kỳ APPROVED | Chấp nhận | Service phải bao phủ mọi đường ghi |
| 10 | Giờ kết thúc ca dự kiến trước giờ bắt đầu | Chấp nhận | Thiếu CHECK |
| 11 | Kỳ tháng 9 dùng khoảng ngày tháng 10 | Chấp nhận | Thiếu CHECK |
| 12 | Xóa mềm nhân viên không có người/lý do | Chấp nhận | Thiếu CHECK |
| 13 | Hai email khác hoa/thường | Chấp nhận; login predicate khớp 2 dòng | Khác biệt giữa ghi và đọc |
| 14 | Trùng nhân viên và ngày công | Từ chối đúng UNIQUE | Đã bảo vệ |
| 15 | Tăng ca > 0 thiếu người/thời điểm duyệt | Từ chối đúng CHECK | Đã bảo vệ |
| 16 | Tổng phiếu không bằng tổng các cột thành phần | Từ chối đúng CHECK | Đã bảo vệ |
| 17 | Scope LOCATION không có location ID | Từ chối đúng CHECK | Đã bảo vệ |

Kết quả này xác nhận các ràng buộc được nêu, không chứng minh database hiện có của người dùng chứa dữ liệu sai. Chưa chạy benchmark, `EXPLAIN ANALYZE` trên dữ liệu đại diện hoặc toàn bộ test ứng dụng.

Đã kiểm tra thêm **7 khối SQL đề xuất trong hai báo cáo** trên một database tạm khác: đều thực thi được sau khi bind tham số. Constraint đề xuất chặn được phân công chồng ngày, hai lương cơ bản khác mã chồng ngày và khoảng ngày ngược; chấp nhận hai khoảng liền kề không giao nhau. Query đối soát trả 0 dòng với dữ liệu khớp và 1 dòng sau khi làm lệch chi tiết phiếu. Các kiểm tra này không thay đổi schema dự án và chưa thay thế integration test JPA.

## 7. Thứ tự xử lý đề xuất

### Ưu tiên cao — trước khi nghiệm thu luồng chấm công/tính lương

- [ ] **S-01:** Chặn lịch sử chồng/ngược ngày; xử lý nhập hồi tố và cập nhật đồng thời.
- [ ] **S-02:** Xác định công chuẩn đủ tháng, snapshot đầu vào ca, nối nghỉ có lương với công hưởng lương.
- [ ] **S-03:** Bảo vệ kỳ đã duyệt ở mọi đường ghi và đối soát tổng chi tiết trước duyệt.
- [ ] **S-06:** Thực thi scope và danh tính actor trước khi nối các service vào API nghiệp vụ.
- [ ] **Q-01/Q-05:** Sửa lựa chọn dữ liệu lịch sử và cơ chế tính lại phiếu trong [Query Review](./QUERY_REVIEW_0.1.md).

### Nên hoàn thiện trong vòng chỉnh sửa tiếp theo

- [ ] **S-04/S-07/S-08:** CHECK nội dòng, email duy nhất theo quy tắc đăng nhập và chặn xóa nhầm hồ sơ đã có nghiệp vụ.
- [ ] **S-05:** Bổ sung định danh đơn vị/địa điểm vào snapshot và quy ước điều chuyển giữa kỳ.
- [ ] **S-09:** Đồng bộ tài liệu, bổ sung integration test cho invariant và cạnh tranh cập nhật.

### Chỉ thực hiện khi có nhu cầu được đo hoặc phạm vi mới

- Mã hóa các trường nhạy cảm theo yêu cầu triển khai cụ thể; vẫn phải giới hạn quyền đọc trước đó.
- Tìm kiếm nâng cao, tổng hợp sẵn hoặc partition khi workload chứng minh cần thiết.
- Các module ngoài phạm vi tại mục 12 của thiết kế không phải điều kiện để tăng điểm review này.
