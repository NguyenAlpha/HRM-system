# Prompt đánh giá database HRM

Prompt dùng lại để đánh giá lược đồ PostgreSQL của HRM (migration Flyway trong `api/src/main/resources/db/migration`
và tài liệu `api/docs/database/DATABASE_SCHEMA.md`) theo một bộ tiêu chí cố định. Mỗi lần chạy lưu kết quả thành một
file mới trong thư mục này, tên `DB_REVIEW_<yyyy-MM-dd>.md`; không sửa file của lần chạy trước.

Cách dùng: dán phần **Prompt** bên dưới cho agent (Claude Code…), mở ở thư mục gốc repo. Chạy xong thì dùng
`/review-prompt-improve api/docs/prompt/DB_REVIEW_PROMPT.md` trong cùng phiên để cải tiến prompt.

---

## Prompt

Đánh giá một vòng lược đồ database của HRM theo bộ tiêu chí dưới đây. Đọc trước:

- `AGENTS.md` và `api/AGENTS.md` (mục Database: chỉ có baseline `V1`–`V8`, sửa thẳng file của domain cho tới lần
  deploy đầu; dữ liệu RBAC chỉ nằm trong seeder).
- `api/docs/database/DATABASE_SCHEMA.md`: §1 nguyên tắc thiết kế (kiểu dữ liệu, xóa mềm), §11 index, §12 ràng buộc và
  quy tắc do service kiểm, §15 tổ chức migration. Tài liệu tự ghi "khi tài liệu khác với database, migration là nguồn
  chuẩn".
- `api/docs/concepts/EFFECTIVE_DATING.md` §2–3 (bảng nào không được chồng hiệu lực, theo cột nào) và
  `PAYROLL_LIFECYCLE.md` §2 (điều kiện của từng bước kỳ lương).

Chỉ đánh giá và ghi kết quả, **không sửa code hay migration** trong lần chạy này.

**Ranh giới với [API_REVIEW_PROMPT.md](./API_REVIEW_PROMPT.md)**: prompt này giữ phần **lược đồ** (ràng buộc, kiểu,
`NOT NULL`, `CHECK` ↔ enum Java, khóa ngoại, index, migration, tài liệu schema). Phần **code dùng DB** thuộc prompt
API và nằm ngoài phạm vi ở đây:

- khóa `FOR UPDATE` và transaction có phủ mọi giao dịch cạnh tranh không;
- truy vấn "hiện tại" theo ngày hiệu lực;
- `@Size` của DTO ↔ độ dài cột;
- dữ liệu seed ↔ quy tắc nghiệp vụ;
- entity ↔ bảng về cột tồn tại và kiểu (đã có `spring.jpa.hibernate.ddl-auto=validate` chặn lúc khởi động).

Riêng `NOT NULL` và `CHECK` mà `validate` không xét thì thuộc prompt này. Cũng ngoài phạm vi có chủ đích, không cần
liệt kê ở mục "Phạm vi chưa kiểm":

- che hoặc mã hóa CCCD, tài khoản ngân hàng, mã số thuế (dự án demo, giữ mặc định dev);
- giá trị thuế/bảo hiểm trong `V8` so với văn bản pháp luật (bước tùy chọn của prompt API);
- hiệu năng trên dữ liệu lớn (chỉ kiểm tĩnh);
- web.

### Nguyên tắc

- Mỗi kết luận có bằng chứng: `file:dòng` của migration, dòng in ra của script, kết quả truy vấn. Không kết luận theo
  tên cột.
- Ưu tiên lỗi giữa các phần:
  - enum Java (`@Enumerated`) ↔ `CHECK (... IN (...))` ↔ `VARCHAR(n)`;
  - field entity ↔ `NOT NULL`;
  - bảng có `effective_from` ↔ `CHECK` khoảng ngày và `EXCLUDE` ↔ bảng ở `EFFECTIVE_DATING.md` §2;
  - bảng có `deleted_at` ↔ unique có `WHERE deleted_at IS NULL`;
  - chỗ code xóa cứng ↔ `ON DELETE` của khóa ngoại trỏ tới bảng đó;
  - cột `status` ↔ `CHECK` ràng các cột đi kèm (người duyệt, thời điểm);
  - `DATABASE_SCHEMA.md` (bảng, cột, kiểu, `NOT NULL`, §11, §12, §1.2, §15) ↔ lược đồ thật;
  - lời `V8` "phủ 2020–2026" ↔ dữ liệu.
- **Thử trước khi ghi:** ràng buộc nghi là thiếu thì thử câu lệnh vi phạm trên DB tạm (`BEGIN; INSERT …; ROLLBACK;`) và
  ghi câu lệnh cùng kết quả vào "Tình huống". Lỗi chỉ xảy ra khi hai giao dịch chạy song song thì đọc migration là đủ.
- Phần không kiểm được ghi "chưa kiểm" và lý do.
- Đọc file kết quả gần nhất `DB_REVIEW_*.md` trong `api/docs/prompt/` (nếu có) để so sánh; giữ mã lỗi `DB-<số>` qua các
  lần chạy, lỗi mới lấy số tiếp theo.
- **Tạm thời, cho tới khi prompt API được sửa sau lần chạy DB đầu tiên:** lỗi đã có mã `API-<số>` trong file
  `API_REVIEW_*.md` mới nhất (VD `API-006` "một Giám đốc" không có ràng buộc DB; phần "33 bảng" của `API-013`) thì chỉ
  ghi tham chiếu ở tiêu chí tương ứng, không tạo mã mới, không trừ điểm.

### Bộ tiêu chí

| # | Tiêu chí | Trọng số | Cần kiểm |
|---|---|---|---|
| 1 | Bất biến nghiệp vụ có ràng buộc DB | 20% | **Bắt buộc:** mỗi bảng có `effective_from` có `CHECK` khoảng ngày và `EXCLUDE` đúng cột nêu ở `EFFECTIVE_DATING.md` §2, hoặc lý do ghi rõ (`schema-checks.sql` mục 3); mỗi quy tắc "chỉ một … đang mở" có unique một phần; mỗi bảng có `status` có `CHECK` ràng cột đi kèm (mục 7), hoặc dữ liệu cho thấy thêm được an toàn (mục 8); tổng phiếu lương ↔ dòng chi tiết (mục 9); từng dòng ở §12.2 "Quy tắc do Service layer kiểm tra" được xét: DB đã chặn, nên chặn, hay chỉ code giữ được (ghi lý do). Làm đủ phần bắt buộc thì tiêu chí không bị coi là kiểm một phần |
| 2 | Enum Java ↔ `CHECK` ↔ độ dài cột | 12% | Mọi cột `@Enumerated` có `CHECK` nhận đúng các hằng Java (thiếu thì lưu bị từ chối, thừa là giá trị chết) và `VARCHAR(n)` đủ dài cho hằng dài nhất (`entity-vs-schema.mjs` phần 1); `CHECK` cố ý hẹp hơn enum có lý do |
| 3 | `NOT NULL` và kiểu dữ liệu | 12% | Cột `NOT NULL` không có `DEFAULT` thì field entity khai báo không null (`entity-vs-schema.mjs` phần 2); kiểu theo §1: thời điểm `TIMESTAMPTZ`, ngày nghiệp vụ `DATE`, tiền `NUMERIC(15,2)`, tỷ lệ/hệ số có độ chính xác đủ, phút `INTEGER` (`schema-checks.sql` mục 4, 5); cột không entity nào map (dữ liệu chết hoặc chỉ SQL ghi) |
| 4 | Xóa mềm, khóa ngoại và xóa cứng | 10% | Unique trên bảng có `deleted_at` có `WHERE deleted_at IS NULL`, hoặc ý định "không dùng lại" ghi trong migration/tài liệu (mục 2); mỗi bảng code xóa cứng có khóa ngoại trỏ tới với `ON DELETE` phù hợp hoặc code xóa bảng con trước (mục 6 ↔ `grep delete`) |
| 5 | Index | 10% | **Bắt buộc:** mỗi khóa ngoại không có index (mục 1, trừ khóa ngoại audit `*_by_account_id` chỉ đếm) được xét có truy vấn nào lọc hay join theo nó không. **Lấy mẫu** (ghi truy vấn đã xem): `EXPLAIN` với `enable_seqscan = off` cho truy vấn nặng (bảng chấm công tháng, đơn chờ duyệt, phân công theo quản lý, báo cáo) |
| 6 | Dữ liệu tham chiếu trong migration | 8% | Quy tắc thuế và bảo hiểm phủ liên tục 01/01/2020–31/12/2026 (mục 10), bậc thuế liền nhau, bậc đầu từ 0, chỉ bậc cuối mở (mục 11); mỗi dòng có `source_reference` |
| 7 | Migration và quy ước baseline | 8% | Chỉ có `V1`–`V8`, không file vá; `flyway_schema_history` trên DB mới có đủ 8 dòng thành công; bản sao trong `api/target/classes` không lệch; mỗi bảng nằm ở file của domain như §15; trigger và chú thích trong migration đúng với hành vi |
| 8 | Tài liệu schema khớp migration | 15% | `doc-vs-schema.mjs` (bảng, cột, kiểu, `NOT NULL`, §11 index, §12.1 tên constraint, §1.2 danh sách bảng, §15 file migration); số liệu trong tài liệu (số bảng…); §12.2 không ghi là "service kiểm" cho quy tắc DB đã chặn; `EFFECTIVE_DATING.md` §2–3 khớp `EXCLUDE` thật; trigger (mục 12) có trong tài liệu |
| 9 | Kiểm thử giữ lược đồ khớp | 5% | Test nào giữ các cặp ở mục "Nguyên tắc" (VD `SeedDataIntegrationTest` chạy migration trên DB trống; `validate` lúc khởi động); cặp nào chưa có test giữ (enum ↔ `CHECK`, tài liệu ↔ lược đồ) |

### Cách chấm điểm

Điểm suy ra từ danh sách lỗi theo quy tắc cố định, không chấm theo cảm nhận: cùng một danh sách lỗi thì luôn ra cùng
một điểm.

Mức độ của mỗi lỗi xét theo hậu quả và mức thường gặp:

- **Cao:** hậu quả nặng xảy ra khi dùng bình thường. Hậu quả nặng gồm: dữ liệu sai lọt vào DB mà không gì chặn (hai
  bản ghi hiệu lực chồng nhau làm sai lương, trạng thái không khớp cột đi kèm); thao tác hợp lệ bị DB từ chối (hằng enum
  mới mà `CHECK` chưa có, lưu ra `409 CONFLICT`); mất dữ liệu (xóa dây chuyền ngoài ý muốn).
- **Trung bình:** hậu quả nặng nhưng cần một điều kiện ít gặp (hằng enum hiếm dùng, tạo lại mã đã xóa mềm, sửa hồi tố
  qua nhiều kỳ); hoặc ràng buộc chạm tiền hay quyền mà không có test nào.
- **Thấp:** hậu quả nặng nhưng hiếm (chỉ khi ghi thẳng DB bằng SQL hay seeder, hoặc hai giao dịch song song); hậu quả
  nhẹ dù thường gặp (tài liệu lệch, tên constraint trong tài liệu sai, index thiếu khi dữ liệu còn nhỏ, cột thừa); thiếu
  test cho cặp khác.

1. **Gắn lỗi vào tiêu chí:** mỗi lỗi thuộc đúng **một** tiêu chí mà nó vi phạm trực tiếp nhất (trường "thuộc tiêu
   chí"; khác với "tìm ra nhờ"). Không trừ một lỗi ở hai tiêu chí. Khi hai tiêu chí có phần chung:
   - thiếu ràng buộc cho một quy tắc nghiệp vụ thuộc tiêu chí 1; unique theo xóa mềm thuộc tiêu chí 4;
   - `VARCHAR` quá ngắn cho hằng enum thuộc tiêu chí 2, không phải 3;
   - tài liệu sai mà lược đồ đúng thuộc tiêu chí 8; lược đồ sai thì lỗi thuộc tiêu chí của lỗi đó, tài liệu sửa theo;
   - thiếu test thuộc tiêu chí 9;
   - dữ liệu vi phạm do code ghi sai (service hay seeder) thuộc prompt API: ghi vào mục "Chuyển cho prompt API" của
     file kết quả, không trừ điểm ở đây; phía DB chỉ xét quy tắc đó có nên có ràng buộc không (tiêu chí 1).
2. **Điểm tiêu chí** bắt đầu từ 10, trừ theo mỗi lỗi **chưa sửa**: Cao −4, Trung bình −2, Thấp −0,5; thấp nhất 0.
3. **Kiểm một phần:** tiêu chí chỉ kiểm được một phần thì điểm = min(10 − tổng trừ, 8) và ghi rõ phần chưa kiểm.
   **Chưa kiểm:** không chấm, bỏ khỏi điểm tổng.
4. **Điểm tổng** = trung bình có trọng số của các tiêu chí đã chấm (chia cho tổng trọng số của chúng), làm tròn 1 chữ
   số thập phân.
5. **Trần:** còn lỗi Cao chưa sửa thì điểm tổng tối đa 7,0; còn từ hai lỗi Cao thì tối đa 5,0.
6. **Xếp loại:** 9,0–10 Tốt · 7,0–8,9 Khá · 5,0–6,9 Trung bình · dưới 5,0 Yếu.

Trọng số là lựa chọn của project (sai thì đắt nhất: bất biến dữ liệu, rồi tới tài liệu schema); đổi trọng số thì ghi
vào "Lịch sử thay đổi", vì điểm các lần chạy chỉ so sánh được khi cùng trọng số.

### Gợi ý lệnh kiểm

Chạy ở gốc repo. Script trong `api/docs/prompt/scripts/` chỉ đọc và in danh sách cần xem: mỗi dòng là chỉ báo, mở
migration xác nhận rồi mới ghi thành lỗi. Ghi chú "lần tạo prompt" là kết quả ngày 2026-11-06, lần sau chỉ cần xem
phần khác đi.

**DB tạm.** Cần container `hrm-database-1` đang chạy (`docker compose up -d database`). Chạy API trên DB tạm ở cổng
18080 để Flyway tạo lược đồ và seeder nạp dữ liệu; mục 8–9 của `schema-checks.sql` cần dữ liệu seed. Seeder chạy sau
khi cổng đã mở, nên chờ dòng log của seeder cuối (`PayrollSeeder`, `@Order(520)`), khoảng 2,5 phút, tối đa 5 phút:

```bash
rm -f /tmp/hrm_db_api.log
docker exec hrm-database-1 psql -U hrm-user -d postgres -c "CREATE DATABASE hrm_rev_db"
(cd api && DB_URL=jdbc:postgresql://localhost:5432/hrm_rev_db ./mvnw -q spring-boot:run \
  -Dspring-boot.run.arguments=--server.port=18080 > /tmp/hrm_db_api.log 2>&1 &)
for i in $(seq 60); do grep -q "Payroll seed" /tmp/hrm_db_api.log 2>/dev/null && break; sleep 5; done
grep "Payroll seed" /tmp/hrm_db_api.log
```

**Xuất catalog và chạy các script:**

```bash
docker exec -i hrm-database-1 psql -U hrm-user -d hrm_rev_db -At -F '|' < api/docs/prompt/scripts/schema-export.sql > /tmp/hrm_schema.txt
node api/docs/prompt/scripts/entity-vs-schema.mjs /tmp/hrm_schema.txt
node api/docs/prompt/scripts/doc-vs-schema.mjs /tmp/hrm_schema.txt
docker exec -i hrm-database-1 psql -U hrm-user -d hrm_rev_db -q < api/docs/prompt/scripts/schema-checks.sql
```

- `entity-vs-schema.mjs` (tiêu chí 2, 3): so 33 entity với lược đồ ở phần `validate` không xét. Dòng "mapped but not in
  the export" là script đọc sai, không phải lỗi lược đồ. Lần tạo prompt: 407 field, 24 cột enum, chỉ một dòng:
  `employees.scheduled_end_status` (`CHECK` chỉ nhận trạng thái kết thúc, hẹp hơn `EmploymentStatus` có chủ đích).
- `doc-vs-schema.mjs` (tiêu chí 8): lần tạo prompt so 319 dòng cột của 33 bảng. Kết quả:
  - 8 cột có trong DB mà tài liệu thiếu: `account_permission_overrides.revoked_*`, `company_profile.logo*`,
    `role_assignment_requests.request_type`, `target_assignment_id`;
  - 3 index ngoài §11: `idx_employees_scheduled_end`, `idx_leave_requests_approver_status`,
    `idx_resignation_requests_approver_status`;
  - 9/21 tên constraint ở §12.1 không có trong DB (§12.1 dùng tên rút gọn, VD `chk_single_company` thay cho
    `chk_company_profile_single`);
  - kiểu và `NOT NULL` khớp hết.

  Script chỉ đọc số bảng ở tiêu đề §1.2; số ở chỗ khác thì xem bằng
  `grep -noE "\b[0-9]+ (bảng|index|constraint|cột|migration)" api/docs/database/DATABASE_SCHEMA.md` (lần tạo prompt:
  dòng 62 ghi "33 bảng", dòng 43 và 57 ghi 32, chính là phần của `API-013`).
- `schema-checks.sql`, kết quả lần tạo prompt (mục "must be 0 rows" phải ra 0):
  - Mục 1: 15 khóa ngoại không có index, cộng 28 khóa ngoại audit.
  - Mục 2: 6 unique không loại bản ghi đã xóa (`employees`: `national_id`, `employee_code`, `work_email`; `accounts`:
    `username`, `email`; `roles.code`). Chú thích ở `V2` nói `accounts` cố ý giữ username/email, và `employee_code`
    không dùng lại theo §3; các unique còn lại chưa có ý định ghi ở đâu.
  - Mục 3: chỉ `account_role_assignments` không có `EXCLUDE` (`API-006`).
  - Mục 4: 0 dòng. Mục 5: 33 cột tiền `(15,2)`; tỷ lệ bảo hiểm/thuế `(6,5)` trong khi §1 ghi tỷ lệ là `NUMERIC(8,4)`.
  - Mục 7: `attendance_records`, `leave_requests`, `payroll_periods` không có `CHECK` trạng thái ↔ cột đi kèm.
  - Mục 8–11: 0 dòng.
- Chỗ code xóa cứng, để so với mục 6 (tiêu chí 4):
  `grep -rhoE "\b\w+Repository\.(delete\w*)\(" api/src/main/java/com/htttdn/hrm/service | sort | uniq -c`. Lần tạo
  prompt: phiếu lương (xóa dòng chi tiết trước), chấm công, ngày lễ, khoản cộng/trừ, `role_permissions`, cấu hình rate
  limit; mọi khóa ngoại là `NO ACTION`.
**Thử ràng buộc nghi là thiếu (tiêu chí 1).** Trên `hrm_rev_db`, bọc câu lệnh vi phạm trong `BEGIN; … ROLLBACK;`. VD
chép một lần gán role đang hiệu lực thành lần gán thứ hai trùng role, scope và ngày. `INSERT 0 1` nghĩa là DB nhận
(lần tạo prompt: nhận, đúng `API-006`); bảng có `EXCLUDE` sẽ báo `conflicting key value violates exclusion constraint`:

```bash
docker exec -i hrm-database-1 psql -U hrm-user -d hrm_rev_db <<'SQL'
BEGIN;
INSERT INTO account_role_assignments (account_id, role_id, scope_type, organization_unit_id, work_location_id,
                                      effective_from, effective_to, granted_by_account_id, reason)
SELECT account_id, role_id, scope_type, organization_unit_id, work_location_id,
       effective_from, effective_to, granted_by_account_id, 'probe: duplicate assignment'
FROM account_role_assignments WHERE revoked_at IS NULL ORDER BY id LIMIT 1;
ROLLBACK;
SQL
```

**Index (tiêu chí 5, lấy mẫu).** `enable_seqscan = off` buộc Postgres dùng index nếu có, nên kết luận được cả khi dữ
liệu nhỏ. Vẫn ra `Seq Scan`, hoặc cột lọc chỉ nằm ở `Filter` mà không ở `Index Cond`, nghĩa là không có index dùng
được. Lấy điều kiện lọc từ method repository (`@Query` hoặc tên method `findBy…`), không đoán:

```bash
docker exec -i hrm-database-1 psql -U hrm-user -d hrm_rev_db -q <<'SQL'
SET enable_seqscan = off;
EXPLAIN (COSTS OFF) SELECT * FROM attendance_records WHERE employee_id IN (3, 4, 5) AND work_date BETWEEN '2026-10-01' AND '2026-10-31' ORDER BY work_date;
EXPLAIN (COSTS OFF) SELECT * FROM attendance_records WHERE check_out_at IS NOT NULL AND overtime_approved_at IS NULL AND worked_minutes > payable_minutes;
EXPLAIN (COSTS OFF) SELECT * FROM leave_requests WHERE approver_employee_id = 5 AND status = 'PENDING' ORDER BY created_at DESC;
EXPLAIN (COSTS OFF) SELECT * FROM employee_assignments WHERE manager_employee_id = 5;
EXPLAIN (COSTS OFF) SELECT * FROM attendance_records WHERE leave_request_id = 7;
SQL
```

Lần tạo prompt: chấm công theo nhân viên và khoảng ngày, đơn chờ theo người duyệt dùng được index; tăng ca chờ duyệt,
phân công theo quản lý, chấm công theo đơn nghỉ thì không.

**Migration (tiêu chí 7).** Chưa build thì không có `api/target/classes`, bỏ qua dòng `diff`. Lần tạo prompt: 8 file,
`target/classes` khớp, 8 dòng thành công.

```bash
ls api/src/main/resources/db/migration
diff -rq api/src/main/resources/db/migration api/target/classes/db/migration && echo "target/classes khớp"
docker exec hrm-database-1 psql -U hrm-user -d hrm_rev_db -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"
```

**Dọn khi xong** (Git Bash trên Windows; Linux/macOS thì `kill` PID đang nghe cổng 18080):

```bash
taskkill //PID $(netstat -ano | grep LISTENING | grep ":18080 " | awk '{print $5}' | head -1) //F
docker exec hrm-database-1 psql -U hrm-user -d postgres -c "DROP DATABASE hrm_rev_db WITH (FORCE)"
```

### Định dạng file kết quả

Lưu thành `api/docs/prompt/DB_REVIEW_<yyyy-MM-dd>.md` (ngày chạy), gồm:

1. **Thông tin lần chạy**: ngày, commit (`git log --oneline -1`), DB tạm đã dùng và kết quả seed, người/agent chạy.
2. **Điểm**: điểm tổng /10, trung bình có trọng số trước khi áp trần, xếp loại, trần áp dụng (nếu có), và bảng mỗi
   tiêu chí một dòng: trọng số, điểm /10 (hoặc "Chưa kiểm"), các lỗi đã trừ (mã và mức), bằng chứng ngắn (tối đa hai
   câu; chi tiết để ở từng lỗi).
3. **Danh sách lỗi**: mỗi lỗi có mã ổn định `DB-<số>`, mức độ (Cao/Trung bình/Thấp), **thuộc tiêu chí** (đúng một),
   vị trí `file:dòng`, mô tả, tình huống gây lỗi cụ thể, đề xuất sửa, **tìm ra nhờ** (số tiêu chí, hoặc "ngoài tiêu
   chí"), **xác nhận bằng** (câu lệnh SQL trên DB tạm / truy vấn catalog / script / đọc migration), trạng thái
   (`Chưa sửa` / `Đã sửa <commit>`).
4. **So sánh với lần trước**: điểm tổng và điểm từng tiêu chí trước → sau; lỗi đã sửa (kèm commit), lỗi còn tồn,
   lỗi mới.
5. **Chuyển cho prompt API**: dữ liệu vi phạm do code ghi và lỗi đã có mã `API-<số>` gặp lại ở đây (mã, một dòng).
6. **Phạm vi chưa kiểm**.

---

## Lịch sử thay đổi

| Ngày | Thay đổi | Review |
|---|---|---|
| 2026-11-06 | Bản đầu tiên, sinh bằng /review-prompt-create. Ranh giới với prompt API chốt với người dùng; `API-006` và phần "33 bảng" của `API-013` tạm chỉ ghi tham chiếu cho tới khi prompt API được sửa | — |
