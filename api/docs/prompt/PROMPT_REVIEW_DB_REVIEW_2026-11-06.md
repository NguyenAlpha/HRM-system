# Review prompt DB — 2026-11-06

| Mục | Giá trị |
|---|---|
| Ngày | 2026-11-06 |
| Prompt | [DB_REVIEW_PROMPT.md](./DB_REVIEW_PROMPT.md) (bản `7e1739a4`) |
| Kết quả đã dựa vào | [DB_REVIEW_2026-11-06.md](./DB_REVIEW_2026-11-06.md) (lần chạy đầu, 9,5 Tốt) |
| Cùng phiên | Có. Có một lần nén ngữ cảnh giữa lần chạy và lần review; các ghi chú đã lưu trước khi nén |

## Trả lời các câu hỏi

### 0. Thay đổi lần trước có hiệu quả không

Bỏ qua: đây là lần chạy đầu, chưa có thay đổi nào để đo.

### 1. Lỗi ngoài tiêu chí hoặc ngoài lệnh gợi ý

Không có lỗi nào nằm ngoài tiêu chí. Có ba lỗi thuộc đúng tiêu chí nhưng chỉ lộ ra nhờ việc làm thêm mà prompt không
chỉ cách:

- **`DB-002`** (tiêu chí 4): mục 2 của `schema-checks.sql` chỉ liệt kê 6 unique. Biết unique nào gây hại phải đối
  chiếu thêm với kiểm tra trùng trong repository (`existsByNationalIdAndIdNot` không có `DeletedAtIsNull`). Sau đó tôi
  tự viết câu SQL "xóa mềm rồi dùng lại". Nhóm tổng quát của lỗi này: cặp unique xóa mềm ↔ kiểm tra trùng trong code.
  → thay đổi 1, 2.
- **Kết luận tiêu chí 5** (không trừ điểm cho 15 khóa ngoại không index): tôi kết luận bằng cách grep tên method
  repository từng cột một (`existsCurrentOrFutureBy…`, `existsBy…AndStatus`). Prompt chỉ nói "được xét có truy vấn nào
  lọc theo nó", không cho lệnh. → thay đổi 1.
- **`DB-005`**:
  - Phần §12.2 lộ ra vì prompt bảo đọc từng dòng, đúng như mong đợi.
  - Phần §15 ("V8 có quy tắc phép năm") chỉ lộ ra nhờ tình cờ `grep` chữ "phép" trong `V8`.
  - Phần §1 (`NUMERIC(8,4)`) lộ ra nhờ mục 5.

  Nhóm tổng quát: câu tài liệu nói migration chứa gì, dùng kiểu gì. → thay đổi 4.

### 2. Lệnh gợi ý sai

- Không lệnh nào của prompt cho dương tính giả hay âm tính giả. Lý do là các script mới viết và đã được thử phá lúc tạo
  prompt.
- **Lỗi của tôi, không phải của lệnh:** bản nháp đầu ghi sai khoảng dòng migration ở `DB-001`/`DB-002`, phải sửa lại
  bằng `grep -n`. → thay đổi 3.
- **Script tạm đưa vào prompt:** phép grep repository từng cột (câu 1) và câu SQL thử xóa mềm (`DB-002`) nay thành
  `scripts/repo-usage.mjs` và một khối lệnh. Lúc viết script tôi gặp hai lỗi của chính nó và đã sửa trước khi đưa vào:
  - bản đầu đếm được 13/15 khóa ngoại: `COALESCE(organization_unit_id, …)` trong index GiST được coi là index đứng
    đầu bằng cột đó. Sửa thành chỉ xét cột đầu của danh sách cột;
  - bản đầu tìm JPQL `.employee` ở mọi repository, nên in ra khoảng 40 dòng không liên quan cho
    `payroll_adjustments.employee_id`. Sửa thành chỉ xét repository của chính entity, đổi lại là không thấy join viết ở
    repository khác (có ghi trong prompt).

### 3. Tiêu chí không hiệu quả

- **Tiêu chí 7 có phần "chú thích trong migration đúng với hành vi" là kiểu đòi kiểm mọi thứ.** Lần chạy không đọc hết
  phần này (có ghi ở "Phạm vi chưa kiểm") mà vẫn chấm 10. Theo quy tắc 3, lẽ ra phải là kiểm một phần, tối đa 8.
  Kiểm thật thì rẻ: `grep -n "^\s*--"` ra 68 dòng. → thay đổi 5: đổi thành phần bắt buộc có lệnh.
- **Tiêu chí 5** lấy mẫu `EXPLAIN` hợp lý: mẫu đã chỉ ra hai truy vấn quét bảng. Phần bắt buộc nay có script.
- Các tiêu chí khác đều có lệnh hoặc mục SQL rõ ràng. Hai người chạy sẽ ra cùng danh sách chỉ báo.

### 4. Thiếu ngữ cảnh

- Tôi phải tự tìm entity ↔ repository ↔ method cho từng khóa ngoại. Việc này nay đã có script.
- Tôi phải tự tìm cách thử "xóa mềm rồi dùng lại" mà không tạo cả hồ sơ mới (nhiều cột `NOT NULL`). Mẹo dùng `UPDATE`
  một hồ sơ khác để lấy giá trị nay đã nằm trong prompt.
- Người dùng hỏi JPA Specification thuộc prompt nào. Câu trả lời là prompt API, nhưng prompt API chưa có phần tầng
  repository. Đây là đề xuất cho lần sửa prompt API (xem "Ghi nhận cho lần chạy sau").

### 5. Định dạng kết quả

- Mục 5 "Chuyển cho prompt API" lần này chủ yếu chứa tham chiếu `API-006`/`API-013`. Hai lỗi đó nay đã chuyển hẳn sang
  prompt DB, nên mục 5 chỉ còn dữ liệu vi phạm do code ghi và lỗi về code dùng DB thấy trong lúc chạy.
- Các phần khác đủ và so được giữa các lần chạy.

### 6. Điểm có phản ánh đúng không

- 9,5 với một lỗi Trung bình và năm lỗi Thấp là hợp lý: lược đồ chặt, lỗi còn lại chủ yếu là tài liệu.
- Có một sai so với quy tắc: tiêu chí 7 chấm 10 dù kiểm một phần. Tính đúng thì tiêu chí 7 = 8, tổng trọng số
  945 − 16 = 929, điểm tổng 9,29 ≈ 9,3, vẫn Tốt. File kết quả không sửa. Lần sau phần đó là bắt buộc nên vấn đề không
  lặp lại.
- Không đổi trọng số. Hai lỗi chuyển sang mỗi lỗi trừ −0,5: `DB-007` ở tiêu chí 1, `DB-008` ở tiêu chí 8. Vì vậy hai
  tiêu chí này không so trực tiếp được với lần 2026-11-06 (đã ghi trong lịch sử).

### 7. Phạm vi chưa kiểm

- **Chú thích migration:** thành phần bắt buộc của tiêu chí 7 (thay đổi 5).
- **`EXPLAIN` cho truy vấn báo cáo:** vẫn là lấy mẫu. Ngoài phạm vi "hiệu năng dữ liệu lớn", và phần bắt buộc của tiêu
  chí 5 đã có script, nên không thêm.

## Thay đổi đã áp dụng

| # | Thay đổi | Lý do và bằng chứng |
|---|---|---|
| 1 | Script `scripts/repo-usage.mjs`, chạy chung khối "Xuất catalog". Phần 1: mỗi khóa ngoại không index (như mục 1) cùng các dòng repository của entity dùng cột đó. Phần 2: mỗi unique trên bảng xóa mềm không loại bản ghi đã xóa cùng các `existsBy/findBy/countBy` trên cột, đánh dấu `counts deleted` / `skips deleted` (xét cả `@Query` ngay trên method). Tiêu chí 4, 5 và cặp ở "Nguyên tắc" dẫn tới script. Ghi kết quả lần 2026-11-06 | Câu 1: kết luận tiêu chí 5 và `DB-002` phải làm tay. Chạy trên repo: 15 khóa ngoại (khớp mục 1), 4 không dòng nào dùng; phần 2 cho đúng các method của `DB-002`. Thử phá trên bản sao `api/src/main/java` và bản sao export (1) đổi tên `existsByWorkLocationIdAndStatus`: dòng đó thành "no repository line uses it"; (2) thêm `AndDeletedAtIsNull` vào `existsByNationalIdAndIdNot`: thành `skips deleted`; (3) thêm index cho `manager_employee_id`: 15 → 14. Cả ba đều phát hiện được |
| 2 | Khối SQL thử xóa mềm rồi dùng lại giá trị (`UPDATE … deleted_at`, `UPDATE` hồ sơ khác lấy `national_id`, `ROLLBACK`) | Câu 1, `DB-002`. Chạy: `duplicate key value violates unique constraint "employees_national_id_key"`, đúng kết quả lần chạy |
| 3 | Nguyên tắc: số dòng lấy bằng `grep -n`, có ví dụ | Câu 2: khoảng dòng sai trong bản nháp kết quả |
| 4 | Tiêu chí 8: đọc từng dòng §12.2; mỗi câu ở §1, §15 nói migration chứa gì hay dùng kiểu gì thì kiểm bằng `grep` hoặc mục 5 | Câu 1: phần §15 của `DB-005` lộ ra do tình cờ |
| 5 | Tiêu chí 7: phần bắt buộc đọc mọi chú thích `--` (lệnh `grep -n`, 68 dòng) và đối chiếu câu nêu quy tắc/ý định với ràng buộc hay `DEFAULT` | Câu 3, 6: tiêu chí 7 chấm 10 dù kiểm một phần |
| 6 | Ranh giới với prompt API thôi là tạm thời: "thiếu ràng buộc DB" và lỗi `DATABASE_SCHEMA.md` chỉ thuộc prompt DB. `API-006` → `DB-007` (tiêu chí 1), phần "33 bảng" của `API-013` → `DB-008` (tiêu chí 8). Mục 5 của file kết quả đổi nội dung. Prompt API: thêm đoạn ranh giới; tiêu chí 5 chỉ còn khóa `FOR UPDATE` cho bất biến DB không chặn; tiêu chí 10 bỏ `api/docs/database/`; thêm dòng lịch sử | Ranh giới đã chốt với người dùng: chuyển sau lần chạy DB đầu. Lần chạy xác nhận `API-006` bằng SQL (`INSERT 0 1`), phần "33 bảng" vẫn còn |
| 7 | Sửa nhỏ: thêm dòng trống trước đoạn "Thử ràng buộc" (đoạn bị dính vào mục danh sách phía trên); chú thích "lần tạo prompt" của `API-006` đổi thành `DB-007` | Đọc lại prompt |

Đã chạy nguyên văn mọi khối lệnh của prompt đã sửa, trích bằng `awk` ra file, trên DB tạm `hrm_rev_db` mới:

| Khối | Kết quả |
|---|---|
| Khởi động | Seed xong ("Payroll seed completed: 72 periods") |
| Xuất catalog và script | 4 script ra như đã ghi; `repo-usage.mjs` 15 khóa ngoại |
| Thử `API-006` | `INSERT 0 1` |
| Thử xóa mềm | Lỗi duplicate key như mong đợi |
| `EXPLAIN` | Như lần chạy |
| Migration | 8 dòng thành công |
| Dọn | Đã xóa DB |

## Đề xuất chung (chưa áp dụng)

- **"Số dòng lấy bằng `grep -n`, không ước theo trí nhớ":** không riêng HRM. Nên thêm vào nguyên tắc "bằng chứng" của
  `/review-prompt-create`, prompt API và prompt web.
- **Mẫu kiểm "unique trên bảng xóa mềm ↔ kiểm tra trùng trong code":** dùng được cho mọi project có xóa mềm. Có thể
  thêm vào danh sách cặp gợi ý của `/review-prompt-create` cho phần database.

## Ghi nhận cho lần chạy sau

- **Prompt API, lần sửa tới:** thêm phần "truy vấn lọc động" cho tầng repository:
  - query JPQL `:x IS NULL OR` (2 query trong `LeaveRequestRepository`) là chỗ nên dùng Specification;
  - 4 repository có `JpaSpecificationExecutor` (Account, Employee, LeaveRequest, Resignation);
  - N+1 khi map danh sách;
  - phân trang.
- **Khi chạy lại prompt API:** đóng `API-006` với trạng thái "Chuyển sang `DB-007`", bỏ phần `DATABASE_SCHEMA.md:62`
  khỏi `API-013`.
- **`repo-usage.mjs` phần 1:** `attendance_records.leave_request_id` chỉ có `IS NULL`/`IS NOT NULL` (không chọn lọc).
  `EXPLAIN` cho thấy chấm công theo đơn nghỉ quét bảng, nhưng `grep` trong `repository` và `service` không thấy truy vấn
  nào tìm chấm công theo `leave_request_id`. Vì vậy mẫu `EXPLAIN` này không cần giữ: lần sau nên thay bằng một truy vấn
  thật (VD báo cáo).
