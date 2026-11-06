# Review prompt đánh giá API — 2026-11-05

| Mục | Giá trị |
|---|---|
| Ngày | 2026-11-05 (xong ngày 2026-11-06) |
| Prompt | [API_REVIEW_PROMPT.md](./API_REVIEW_PROMPT.md) |
| File kết quả đã dựa vào | [API_REVIEW_2026-11-05.md](./API_REVIEW_2026-11-05.md) |
| Cùng phiên với lần chạy | Có: dựa vào lệnh đã chạy, script viết tạm, ngõ cụt và request thật trong phiên. Giữa chừng phiên bị nén ngữ cảnh, nên mọi con số dưới đây được kiểm lại bằng cách chạy lại script, không lấy từ trí nhớ |

## Câu trả lời

### 0. Thay đổi lần trước có hiệu quả không

| Thay đổi (dòng lịch sử) | Lần này | Kết luận |
|---|---|---|
| Đọc `concepts/` trước (2026-11-03) | `PAYROLL_LIFECYCLE.md` §2 dẫn tới `API-010`; đọc `concepts/` thấy 24 link hỏng, 4 tên method cũ (`API-012`) và câu sai ở `ROLE_GRANT_POLICY.md` (`API-013`); `APPROVAL_ROUTING.md` §2 cho biết `API-001` là giới hạn đã biết | Giữ |
| Tiêu chí 6 "Thời gian và hiệu lực" (2026-11-03) | `grep EffectiveToIsNull` tìm ra `API-007` (Trung bình); lần trước tiêu chí này chỉ kiểm `Clock` | Giữ |
| Tiêu chí 11 "Vòng đời và tham chiếu" (2026-11-03) | Lần đầu chấm, tìm ra `API-008`. Mục "đơn chờ người đó duyệt" trùng tiêu chí 3: `API-001` khớp cả hai, phải tự chọn | Giữ, thêm quy tắc gắn (câu 3) |
| Bỏ đánh giá kích thước class (2026-11-03) | Không thấy thiếu | Giữ |
| Đối chiếu permission chỉ lấy `@PreAuthorize` và hằng trong `service/` (2026-11-03) | Không còn dương tính giả, nhưng thừa: `PermissionCatalogueTest` (thêm ở `06cd73dc` khi sửa `API-003`) giữ đúng cặp này và fail khi lệch | Bỏ |
| Kiểm quyền bắt đầu từ lời gọi trong `controller/` (2026-11-03) | Hướng đúng nhưng phải tự viết script; bản tạm sai nhiều (câu 2) | Thay bằng `controller-calls.mjs` |
| Độ phủ test: tên class chỉ là chỉ báo, tìm theo đường dẫn endpoint (2026-11-03) | Theo tên class: 13 ứng viên; theo đường dẫn: còn 4 thật sự không có test (`API-011`) | Giữ, đưa vào script |
| Mã `API-<số>`, "tìm ra nhờ" (2026-11-03) | Theo dõi được 4 lỗi cũ qua hai lần chạy; cả 9 lỗi mới ghi được "tìm ra nhờ" | Giữ |
| Bước tùy chọn công thức lương (2026-11-03) | Không chạy, đúng điều kiện: `V8`, `PayslipCalculator`, `InsuranceTaxCalculator` không đổi | Giữ |
| Thang điểm 10, trần khi còn lỗi Cao (2026-11-03) | 7,0 do trần `API-001`; trung bình 8,7 | Giữ (câu 6) |
| Kiểm một phần = min(10 − trừ, 8) (2026-11-05) | Tiêu chí 8: 9,5 → 8. Hai lần chạy liền tiêu chí 8 đều bị trần 8 | Giữ quy tắc, sửa tiêu chí 8 (câu 3) |

### 1. Lỗi ngoài tiêu chí hoặc ngoài lệnh gợi ý

Không lỗi nào "ngoài tiêu chí": 9 lỗi mới đều tìm ra nhờ đúng tiêu chí của nó. Nhưng phần lớn chỉ lộ ra, hoặc chỉ rõ
mức độ, nhờ việc prompt không chỉ:

- `API-009` (thiếu tham số bắt buộc, sai `Content-Type` → 500): chỉ thấy khi gửi request sai dạng tới API chạy thật,
  vì `GlobalExceptionHandler` có handler bắt mọi exception. Prompt không có bước chạy API. Nhóm tổng quát: **lỗi trả
  về của request sai dạng**.
- `API-001`, `API-002`, `API-007`, `API-008`: request thật trên DB tạm cho thấy điều đọc code không thấy rõ. `API-007`
  chặn cả việc duyệt đơn nghỉ việc (400 với `field` là `terminationDate`, trường không có trên form duyệt). `API-008`
  để đơn nằm trong danh sách chờ rồi trả 409 khi duyệt. `API-001` và `API-002` có số đơn cụ thể (#23, #28, #55).
  Nhóm: **lỗi luồng nghiệp vụ cần tái hiện trên dữ liệu thật** mới chắc về mức độ.
- `API-010` (một kỳ cho mỗi tháng trả lương): `grep EXCLUDE|UNIQUE` chỉ liệt kê ràng buộc đã có, nên bất biến không có
  ràng buộc không bao giờ hiện ra. Tìm ra nhờ đi từ cột "Điều kiện thêm" ở `PAYROLL_LIFECYCLE.md` §2. Nhóm: **bất biến
  trong tài liệu không có ràng buộc DB, và khóa không phủ giao dịch cạnh tranh**.
- `API-012` (link hỏng), `API-013` (endpoint branding/logo không có tài liệu): tìm bằng script tạm `links.py` và so tay
  đường dẫn; prompt chỉ nói "mô tả khớp code". Nhóm: **tài liệu trỏ vào code không còn; endpoint không có tài liệu**.
- `API-015` (seed chọn người duyệt bằng logic riêng): thấy khi đọc `StaffSeeder`; prompt không chỉ cách so seeder với
  service. Nhóm: **seeder ghi thẳng repository, bỏ qua kiểm tra của service**.

### 2. Lệnh gợi ý sai

- **`grep "LocalDate.now()\|Instant.now()"`** ngoài `config/seed`: 1/1 kết quả là dương tính giả (Javadoc
  `ClockConfig.java:12`), và âm tính giả với `now(ZoneId…)`, `YearMonth.now()`. Lần chạy đã tự đổi sang `\.now(` bỏ
  `now(clock)`. Thử phá trên bản sao (`LocalDate.now(clock)` → `LocalDate.now(ZoneId.of("UTC"))` trong
  `LeaveEntitlementService`): lệnh cũ không thấy, lệnh mới thấy.
- **Script tạm kiểm quyền** (`authz.py`): bản đầu báo 62 lời gọi thiếu `@PreAuthorize` vì file CRLF làm sai ranh giới
  khối annotation; sửa CRLF còn 27, trong đó 17 là dương tính giả (annotation gộp `@CanManageRbac`,
  `@CanManagePermissionOverrides` đặt trên class). Bản đưa vào repo `controller-calls.mjs`: 160 lời gọi, 10 dòng, 0
  dương tính giả.
- **Tìm test theo tên class:** 9/13 dương tính giả (13 service không có tên trong `src/test`, 4 thật sự không có
  test). `controller-calls.mjs` tìm thêm theo đường dẫn: 3 `NONE` + 3 `PATH`, một `PATH` không tính
  (`AccountActivationService` chỉ qua `bff-guards.spec.ts`), khớp 4.
- **Đếm `ErrorCode` bằng vòng lặp bash chỉ bắt dòng có dấu phẩy:** bỏ sót phần tử cuối `INTERNAL_ERROR`, nên file kết
  quả ghi "92/93" thay vì 93/94. Kết luận không đổi (một mã không dùng, mọi mã đều có bản dịch). `error-codes.mjs` bắt
  cả phần tử cuối.
- **`@RequestBody` không có `@Valid`:** 2/2 kết quả là body tùy chọn không mang ràng buộc nào. Ghi vào prompt như
  ngoại lệ đã xét.
- **So `@Size` với độ dài cột theo tên trường** (`sizecols.py`): 8/8 dương tính giả (tên trường DTO khác tên cột, nhiều
  bảng có cột cùng tên). Không đưa vào; prompt ghi "đừng".
- **Endpoint không dùng, so bằng regex đường dẫn trong `apps/web`** (`{id}` khớp mọi đoạn): 1/1 kết quả là lỗi tách
  đường dẫn, không phải endpoint thừa. Chiều web → API là việc của prompt web; bỏ khỏi tiêu chí 10 (đề xuất ở mục cuối).
- **`docs-vs-code.mjs` chỉ đếm `api/docs/api`:** báo thêm 7 endpoint rate limit/traffic vốn có tài liệu trong
  `concepts/` (7/10 dương tính giả). Bản trong repo đếm cả `concepts/`.

Script và lệnh mới, đều chạy trên repo và thử phá trên bản sao (`%TEMP%`):

| Script / lệnh | Kết quả trên repo | Thử phá |
|---|---|---|
| `controller-calls.mjs` | 160 lời gọi, 10 không có `@PreAuthorize` (đều hợp lệ); 3 `NONE`, 3 `PATH` | Bỏ `@PreAuthorize` của `PayrollService.approve` → hiện dòng `POST /api/payroll/periods/{id}/approve`; xóa hai test của `EmployeeSensitiveDataService` → service thành `NONE` |
| `request-sizes.mjs` | 107 trường, 7 thiếu `@Size` (tiêu chí tìm kiếm, `MinimalEmployeeCommand` nội bộ) | Bỏ `@Size` ở `EndEmploymentRequest.terminationReason` → hiện dòng đó |
| `error-codes.mjs` | 94 mã, 94 bản dịch; không dùng `EMPLOYEE_CODE_TAKEN` | Xóa bản dịch `INTERNAL_ERROR` (phần tử cuối enum) và thêm một mã lạ trên web → hiện ở `untranslated` và `web only` |
| `docs-vs-code.mjs` | 24 link hỏng (đều trong `concepts/`), 3 endpoint không tài liệu nào nhắc | Thêm một link hỏng, đổi đường dẫn một mapping → cả hai hiện |
| `seed-checks.sql` | 6 truy vấn, 0 dòng trên DB dev `hrm` và trên DB tạm vừa seed | Trên bản sao DB dev: cho người duyệt là chính người nộp, cho một người duyệt nghỉ việc → truy vấn 1, 2, 4 bắt được. Trên DB tạm vừa seed: đổi người duyệt một đơn chờ, cho người có đơn chờ nghỉ việc, chép một đơn đã duyệt thành đơn chờ → truy vấn 3, 5, 6 bắt được |
| Lệnh `Clock` | Rỗng | Như trên |
| Lệnh đầu vào bảng lương | 12 dòng; `NO` chỉ có `AttendanceService`, `LeaveAttendanceService` (chấm công bị chặn khi duyệt kỳ) | Bỏ `PayrollPeriodInvalidator` khỏi `AllowancePolicyService` → hai dòng phụ cấp thành `NO` |

### 3. Tiêu chí không hiệu quả

- **Tiêu chí 8 luôn bị "kiểm một phần":** hai lần chạy liền đều trần 8, vì "Cần kiểm" ("dữ liệu demo có vi phạm quy
  tắc nào") không có điểm dừng. Viết lại thành phần bắt buộc kiểm được hết: `SeedDataIntegrationTest`, `seed-checks.sql`
  trên DB tạm vừa seed, so các bảng seeder ghi thẳng với kiểm tra của service.
- **Tiêu chí 3 và 11 trùng nhau** ở đơn chờ người đã nghỉ duyệt: `API-001` khớp cả hai. Thêm quy tắc gắn: đơn kẹt ở
  người duyệt thuộc tiêu chí 3. Tiêu chí 11 thêm "đơn đang mở của chính người đó", đúng chỗ của `API-008`.
- **Mức độ lỗi không có định nghĩa:** `API-002`, `API-007` (Trung bình) và `API-005`, `API-010` (Thấp) cùng loại
  "điều kiện ít gặp"; người chạy khác có thể xếp khác và điểm tổng lệch theo. Thêm định nghĩa theo hậu quả × mức
  thường gặp; xếp lại 15 lỗi hiện có theo định nghĩa thì không lỗi nào đổi mức.
- **Tiêu chí 2** hai lần không tìm ra gì, nhưng kiểm nhanh và rủi ro cao nếu hỏng: giữ. **Tiêu chí 4** hai lần đều 10
  với trọng số 15%: không phải vì không kiểm được, mà vì cách kiểm là tự liệt kê đầu vào. Thêm lệnh liệt kê đầu vào và
  chỗ ghi (câu 4), giữ trọng số.

### 4. Thiếu ngữ cảnh

- `api/AGENTS.md` nói test cần PostgreSQL nhưng không có lệnh chạy test trên DB tạm; prompt trỏ tới đó nên phải tự tìm
  tên biến `DB_URL`. Ghi thẳng lệnh vào prompt.
- Chạy API để tái hiện lỗi: phải tự tìm cổng (`--server.port`), thân đăng nhập (`usernameOrEmail`), chỗ của token
  (`data.accessToken`), tài khoản seed và mật khẩu, cách tắt và xóa DB. Thêm mục "Chạy test và API trên DB tạm".
- Bất biến cần ràng buộc đã có sẵn thành bảng trong `PAYROLL_LIFECYCLE.md` §2 (cột "Điều kiện thêm") và
  `EFFECTIVE_DATING.md` §2 (cột "Không được chồng lấn theo"): chỉ thẳng vào tiêu chí 5.
- Đầu vào bảng lương: phải tự lần các repository mà `PayslipCalculator`, `InsuranceTaxCalculator`,
  `AttendanceCalendarService` đọc. Thêm lệnh.
- Ngoại lệ đã xét (10 lời gọi không `@PreAuthorize`, 2 body tùy chọn, 7 trường không `@Size`): ghi kèm lệnh để lần sau
  chỉ xem phần khác đi.

### 5. Định dạng kết quả

- Bằng chứng trong bảng tiêu chí thành đoạn văn (tiêu chí 1 và 11 mỗi ô 4–5 câu), lặp lại nội dung của từng lỗi: giới
  hạn tối đa hai câu.
- "Trung bình trước khi áp trần" phải tự thêm để giải thích 7,0: đưa vào định dạng. Đây là số so sánh được giữa các lần
  khi trần không đổi.
- Dòng "Kiểm chứng chạy thật" phải tự thêm vào bảng thông tin. Đưa vào định dạng, và thêm trường "xác nhận bằng" cho
  từng lỗi để biết lỗi nào đã tái hiện, lỗi nào chỉ đọc code (`API-006`, `API-010`).

### 6. Điểm có phản ánh đúng không

7,0 "Khá" với một lỗi Cao (đơn kẹt khi người duyệt nghỉ việc) và hai lỗi Trung bình về luồng nhân sự: hợp lý. Trần
làm đúng việc: trung bình 8,7 nhưng một lỗi chặn quy trình duyệt thường ngày giữ điểm ở 7,0. Hai chỗ lệch đã xử lý ở
câu 3: tiêu chí 8 không thể quá 8 dù kiểm kỹ, và mức độ không định nghĩa. Giữ nguyên trọng số và mức trừ.

### 7. Phạm vi chưa kiểm

- Công thức thuế/bảo hiểm: giữ tùy chọn, chỉ chạy khi `V8` hay hai class tính đổi.
- Tiêu chí 8 kiểm một phần (truy vấn trên DB dev có thể cũ; chưa kiểm đơn trùng ngày): thành bước bắt buộc,
  `seed-checks.sql` chạy trên DB tạm vừa seed và có truy vấn đơn trùng ngày (số 5).
- Hai tình huống đồng thời (`API-006`, `API-010`) chỉ đọc code: ngoài phạm vi có chủ đích, chạy hai giao dịch song song
  tốn công mà đọc khóa đã đủ kết luận. Prompt ghi rõ, kết quả ghi "xác nhận bằng: đọc code".
- Endpoint web không gọi: chuyển sang prompt web (đề xuất ở mục cuối).
- Web ngoài phạm vi, trừ hai cặp khớp với API: ghi vào prompt để không phải liệt kê lại.

## Thay đổi

| # | Thay đổi | Lý do | Bằng chứng |
|---|---|---|---|
| 1 | Gợi ý lệnh dùng `controller-calls.mjs`, `request-sizes.mjs`, `error-codes.mjs`, `docs-vs-code.mjs`, kèm kết quả đã xét; bỏ đối chiếu permission bằng tay | Câu 0, 2, 4 | `authz.py` 17/27 dương tính giả; tên class 9/13; bash `ErrorCode` sót phần tử cuối; `PermissionCatalogueTest` đã giữ cặp permission |
| 2 | Lệnh `Clock`: `\.now(` bỏ `now(clock)` | Câu 2 | Lệnh cũ 1/1 dương tính giả, sót `now(ZoneId…)` |
| 3 | Mục "Chạy test và API trên DB tạm": lệnh test, khởi động API ở cổng 18080, tài khoản, `seed-checks.sql`, dọn; nguyên tắc "tái hiện trước khi ghi" | Câu 1, 4 | `API-001`, `002`, `007`, `008`, `009` tái hiện bằng request thật; lệnh DB tạm không có trong `api/AGENTS.md` |
| 4 | Lệnh dò request sai dạng; tiêu chí 7 gồm thiếu tham số, sai `Content-Type`, JSON hỏng | Câu 1 | `API-009` |
| 5 | Tiêu chí 5 xét từng bất biến trong `concepts/`, gợi ý xét khóa có phủ giao dịch cạnh tranh không | Câu 1, 4 | `API-010`; `grep EXCLUDE\|UNIQUE` không thể thấy bất biến thiếu ràng buộc |
| 6 | Lệnh liệt kê đầu vào bảng lương và service ghi vào chúng | Câu 3, 4 | Tiêu chí 4 hai lần 10, cách kiểm phải tự nghĩ |
| 7 | Tiêu chí 8: phần bắt buộc (`SeedDataIntegrationTest`, `seed-checks.sql` trên DB tạm, bảng seeder ghi thẳng ↔ service) | Câu 3, 7 | Hai lần trần 8; `API-015` |
| 8 | Tiêu chí 11 thêm "đơn đang mở của chính người đó"; quy tắc gắn đơn kẹt ở người duyệt vào tiêu chí 3; tiêu chí 10 bỏ "endpoint không dùng", thêm link và endpoint thiếu tài liệu | Câu 2, 3 | `API-001` khớp hai tiêu chí; `API-008`; regex endpoint 1/1 sai; `API-012`, `API-013` |
| 9 | Định nghĩa mức Cao / Trung bình / Thấp theo hậu quả × mức thường gặp | Câu 3 | 15 lỗi xếp lại không đổi mức |
| 10 | File kết quả: "xác nhận bằng", trung bình trước trần, chạy API thật hay không, bằng chứng tối đa hai câu | Câu 5 | Ba mục phải tự thêm trong lần chạy; ô bằng chứng 4–5 câu |

Prompt dài từ 103 lên 233 dòng, gần hết ở mục gợi ý lệnh và mục chạy API. Đổi lại, năm việc phải viết tay trong lần
chạy (kiểm quyền, độ phủ test, độ dài trường, `ErrorCode`, link tài liệu) thành script đã thử phá, và việc tái hiện
lỗi có lệnh sẵn. Không đổi trọng số và mức trừ; định nghĩa mức không đổi mức lỗi nào. Điểm tiêu chí 8 không còn so
trực tiếp với các lần trước (trước luôn bị trần 8); các tiêu chí khác vẫn so được với 2026-11-05.

## Chạy nguyên văn khối lệnh

Sáu khối `bash` của prompt đã sửa được trích ra file bằng `awk` và chạy nguyên văn; các lệnh một dòng trong gợi ý
cũng chạy lại.

| Khối | Kết quả |
|---|---|
| Lệnh `Clock` | Rỗng |
| Đầu vào bảng lương | 12 dòng như bảng ở câu 2 |
| Chạy test trên DB tạm | 584 test, 0 lỗi, `BUILD SUCCESS`, khoảng 5,5 phút; DB đã xóa |
| Chạy API trên DB tạm + `seed-checks.sql` | **Bản đầu sai:** vòng chờ gọi thử `GET /api/public/branding`, nhưng cổng mở trước khi seeder chạy (seeder là runner, chạy sau dòng log "Started HrmApplication"). Khối xong sau 16 giây, `seed-checks.sql` chạy lúc `StaffSeeder` còn đang tạo nhân viên nên 6 truy vấn ra 0 dòng mà không chứng minh gì. Sửa: chờ dòng log `Payroll seed` của seeder cuối (`@Order(520)`). Chạy lại bản sửa trên DB mới: seed xong sau khoảng 2,5 phút, 6 truy vấn ra 0 dòng trên dữ liệu đủ (54 đơn nghỉ, 12 đang chờ) |
| Dò lỗi trả về | `missing from/to` 500, `missing year` 500, `Content-Type text/plain` 500, `malformed JSON` 400, `unknown employee` 404: đúng ghi chú (`API-009`); hai dòng sau không phải 401 nên đăng nhập bằng hàm `login` chạy đúng |
| Dọn | `taskkill` dừng đúng tiến trình đang nghe cổng 18080, DB tạm đã xóa; chạy hai lần (sau bản đầu và sau bản sửa) |
| Lệnh một dòng | `EffectiveToIsNull`: 4 chỗ như ghi chú; `@RequestBody` không `@Valid`: 2 dòng; bảng seeder ghi thẳng: 22 repository; số liệu tài liệu: 11 dòng, lệch đúng `API-013` (29 controller trong khi có 32; "33 bảng") |

## Đề xuất thay đổi chung (chưa áp dụng)

Các thay đổi sau không riêng gì HRM API. Theo quy tắc của skill, chỉ đề xuất cho skill `/review-prompt-create` và prompt
web (`apps/web/docs/prompt/WEB_REVIEW_PROMPT.md`), chưa sửa:

1. **Định nghĩa mức độ** (thay đổi 9) vào quy tắc chấm của `/review-prompt-create` và prompt web. Trước khi áp dụng
   cho prompt web, xếp lại các lỗi `WEB-001`…`WEB-007` theo định nghĩa để chắc không lỗi nào đổi mức.
2. **Định dạng kết quả** (thay đổi 10): trường "xác nhận bằng", trung bình trước trần, bằng chứng trong bảng tối đa hai
   câu, cho cả skill và prompt web.
3. **Nguyên tắc "tái hiện trước khi ghi"**: skill tạo prompt yêu cầu prompt mới có cách chạy ứng dụng trên dữ liệu tạm
   (lệnh khởi động, tài khoản, dọn). Prompt web đã có E2E và lệnh dò `Origin`, nên chỉ cần thêm câu nguyên tắc.
4. **Quy tắc gắn lỗi khi hai tiêu chí chồng nhau**: skill tạo prompt, ở bước viết tiêu chí, ghi luôn quy tắc gắn cho
   mỗi cặp tiêu chí có phần chung.
5. **Riêng prompt web**: kiểm chiều API → web (endpoint API không trang nào gọi). Prompt web hiện ghi chiều này là
   "kiểm tay"; prompt API đã bỏ nó khỏi tiêu chí 10.

## Ghi nhận cho lần chạy sau

- File kết quả 2026-11-05 ghi "92/93 `ErrorCode` được dùng"; đúng là 93/94 (94 mã, 93 dùng, mọi mã đều có bản dịch).
  Lần chạy sau dùng `error-codes.mjs` sẽ ra 94; không phải mã mới.
- `docs-vs-code.mjs` báo 3 endpoint không tài liệu nào nhắc, file kết quả ghi 4 (`API-013`): `PUT /api/company/logo` chỉ
  được nhắc trong `concepts/RATE_LIMIT.md`, không có trong `api/docs/api`. Vẫn thuộc `API-013`.
- `docs-vs-code.mjs` đếm cả `api/docs/prompt/`: link tới file review chưa tạo (như lúc sửa prompt này) hiện thành link
  hỏng.
- API nhận request trong khoảng 2,5 phút seeder còn chạy (seeder là runner, chạy sau khi cổng mở), nên lúc đó dữ liệu
  chưa đủ. Với dev thì không sao, nhưng tái hiện lỗi hay chạy E2E ngay sau khi khởi động sẽ ra kết luận sai. Prompt đã
  chờ dòng log `Payroll seed`.
- Lệnh đầu vào bảng lương không thấy `EmploymentLifecycleService` ghi `employee_assignments` (đóng phân công bằng
  setter, không gọi `save`); service này có gọi `PayrollPeriodInvalidator`. Ghi chú đã có trong prompt.
