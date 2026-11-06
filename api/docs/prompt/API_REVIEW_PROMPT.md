# Prompt đánh giá API HRM

Prompt dùng lại để đánh giá toàn bộ `api` theo một bộ tiêu chí cố định. Mỗi lần chạy lưu kết quả thành một file
mới trong thư mục này, tên `API_REVIEW_<yyyy-MM-dd>.md` (VD `API_REVIEW_2026-11-03.md`); không sửa file của lần
chạy trước.

Cách dùng: dán phần **Prompt** bên dưới cho agent (Claude Code…), mở ở thư mục gốc repo. Chạy xong thì dùng
`/review-prompt-improve api/docs/prompt/API_REVIEW_PROMPT.md` trong cùng phiên để cải tiến prompt.

---

## Prompt

Đánh giá một vòng API trong `api/` của dự án HRM theo bộ tiêu chí dưới đây. Đọc `AGENTS.md` và `api/AGENTS.md`
trước, rồi đọc `api/docs/concepts/` (`APPROVAL_ROUTING.md`, `EFFECTIVE_DATING.md`, `DATA_SCOPE.md`,
`PAYROLL_LIFECYCLE.md`, `ROLE_GRANT_POLICY.md`…): đó là quy tắc dự kiến, việc của bạn là đối chiếu code với chúng.
Chỉ đánh giá và ghi kết quả, **không sửa code** trong lần chạy này. Web (`apps/web`) nằm ngoài phạm vi có chủ đích,
trừ hai cặp khớp với API (`ErrorCode` ↔ bản dịch, `openapi.json` ↔ `lib/api-schema.ts`); không cần liệt kê nó ở mục
"Phạm vi chưa kiểm".

Lược đồ database (ràng buộc, kiểu, `NOT NULL`, `CHECK` ↔ enum, khóa ngoại, index, migration, `api/docs/database/`)
thuộc [DB_REVIEW_PROMPT.md](./DB_REVIEW_PROMPT.md), không trừ điểm ở đây. Prompt này giữ phần code dùng DB: khóa
`FOR UPDATE` và transaction, truy vấn theo ngày hiệu lực, `@Size` ↔ độ dài cột, dữ liệu seed.

### Nguyên tắc

- Mỗi kết luận phải có bằng chứng: đường dẫn `file:dòng`, lệnh đã chạy hoặc kết quả truy vấn. Không kết luận
  theo cảm tính hay theo tên method.
- Ưu tiên lỗi **giữa các phần**, không chỉ trong từng module: danh mục permission ↔ code kiểm tra quyền, quy tắc
  nghiệp vụ ↔ dữ liệu seed, tài liệu ↔ code, DTO ↔ type sinh ra cho web.
- **Tái hiện trước khi ghi:** lỗi nghi ngờ về luồng (duyệt đơn, vòng đời, hiệu lực theo ngày, lỗi trả về) thì gửi
  request thật tới API chạy trên DB tạm (mục "Chạy test và API trên DB tạm") và ghi request cùng kết quả vào "Tình
  huống". Lỗi chỉ xảy ra khi hai giao dịch chạy song song thì đọc code là đủ.
- Phần nào không kiểm được thì ghi rõ "chưa kiểm" và lý do, không bỏ qua im lặng.
- Đọc file kết quả gần nhất `API_REVIEW_*.md` trong `api/docs/prompt/` (nếu có) để so sánh: lỗi nào đã sửa, lỗi nào
  còn, lỗi nào mới. Giữ nguyên mã lỗi `API-<số>` của lỗi cũ; lỗi mới lấy số tiếp theo.

### Bộ tiêu chí

| # | Tiêu chí | Trọng số | Cần kiểm |
|---|---|---|---|
| 1 | Phân quyền và phạm vi dữ liệu | 15% | Mọi method controller gọi được có `@PreAuthorize` hoặc kiểm tra tương đương (người duyệt, chính chủ, `EmployeeAccessScopeService`); thao tác trên nhân viên có kiểm tra phạm vi; thao tác chung cả công ty gọi `requireCompanyWide`; permission `companyWide` đánh dấu đúng |
| 2 | Bảo vệ dữ liệu nhạy cảm | 10% | CCCD, ngân hàng, mã số thuế, lương chỉ xuất hiện ở endpoint có quyền tương ứng; log không in mật khẩu, token, dữ liệu nhạy cảm |
| 3 | Quy trình duyệt đúng nghiệp vụ | 10% | Người duyệt chọn đúng theo phân công đang hiệu lực; đơn không bị kẹt khi người duyệt nghỉ việc/đổi quản lý; trạng thái đơn chuyển hợp lệ |
| 4 | Toàn vẹn bảng lương | 15% | Đổi đầu vào (lương, phụ cấp, phân công, khấu trừ, chấm công, ngày lễ) sau khi tính thì kỳ lương phải tính lại hoặc bị chặn; tách người tính/người duyệt; kỳ hủy không còn phiếu lương |
| 5 | Toàn vẹn dữ liệu và đồng thời | 10% | Mỗi bất biến nêu trong `concepts/` (cột "Điều kiện thêm" ở `PAYROLL_LIFECYCLE.md` §2, cột "Không được chồng lấn theo" ở `EFFECTIVE_DATING.md` §2, "một Giám đốc"…) mà DB không chặn (xem tiêu chí 1 của file `DB_REVIEW_*.md` mới nhất) được giữ bằng khóa `FOR UPDATE` trên mọi dòng mà giao dịch cạnh tranh cùng đọc; thiếu ràng buộc DB thuộc prompt DB, không trừ ở đây |
| 6 | Thời gian và hiệu lực | 8% | Không có `LocalDate.now()`/`Instant.now()` thiếu `Clock` ngoài seeder; mọi truy vấn "hiện tại" trên dữ liệu theo khoảng hiệu lực (phân công, lương, phụ cấp, lần gán role) xét theo ngày chứ không lấy bản ghi "mở" (`…EffectiveToIsNull`), vì bản ghi có thể hẹn ngày tương lai |
| 7 | Validation và lỗi trả về | 7% | `@RequestBody` có `@Valid`; trường chuỗi có `@Size` khớp cột DB; lỗi trả mã `ErrorCode` để web dịch, không lộ 500, kể cả request sai dạng (thiếu tham số bắt buộc, sai `Content-Type`, JSON hỏng) |
| 8 | Dữ liệu seed khớp quy tắc | 5% | **Bắt buộc:** `SeedDataIntegrationTest` pass; `seed-checks.sql` trả 0 dòng trên DB tạm vừa seed; mỗi bảng seeder ghi thẳng bằng repository mà có quy tắc nghiệp vụ (đơn, phân công, gán role, chấm công) được so với kiểm tra của service cùng loại. Làm đủ phần bắt buộc thì tiêu chí không bị coi là kiểm một phần |
| 9 | Kiểm thử | 8% | Toàn bộ test pass; service nào không có test; có test giữ các phần khớp nhau (permission ↔ code, seed ↔ quy tắc) không |
| 10 | Bảo trì và tài liệu | 4% | Code chết (permission, `ErrorCode` không dùng); link, tên class/method, số liệu và mô tả trong `api/docs` (kể cả `concepts/`, trừ `api/docs/database/` thuộc prompt DB) khớp code; mỗi endpoint có trong tài liệu |
| 11 | Vòng đời và tham chiếu | 8% | Khi một thực thể kết thúc (nhân viên nghỉ việc, tài khoản bị khóa, role bị thu hồi/xóa, đơn vị/địa điểm ngừng hoạt động), những thứ đang tham chiếu tới nó được xử lý: đơn chờ người đó duyệt, đơn đang mở của chính người đó, phân công có quản lý là người đó, lần gán role theo đơn vị đó, phạm vi dữ liệu |

### Cách chấm điểm

Điểm suy ra từ danh sách lỗi theo quy tắc cố định, không chấm theo cảm nhận: cùng một danh sách lỗi thì luôn ra cùng
một điểm.

Mức độ của mỗi lỗi xét theo hậu quả và mức thường gặp:

- **Cao:** hậu quả nặng (vượt quyền hoặc lộ dữ liệu, sai tiền, một quy trình chính bị chặn hoặc kẹt, mất dữ liệu) xảy
  ra trong thao tác thường ngày qua web.
- **Trung bình:** hậu quả nặng nhưng cần một điều kiện ít gặp (bản ghi hẹn ngày tương lai, một tổ hợp dữ liệu riêng);
  hoặc luồng công khai, chạm tiền hay quyền mà không có test nào.
- **Thấp:** hậu quả nặng nhưng hiếm (hai giao dịch song song, nhiều điều kiện ít gặp cùng lúc, chỉ khi gọi API hay ghi
  DB không qua web); hậu quả nhẹ dù thường gặp (sai mã lỗi hoặc HTTP status, việc thừa phải gỡ tay, tài liệu lệch,
  code chết); thiếu test cho luồng khác.

1. **Gắn lỗi vào tiêu chí:** mỗi lỗi thuộc đúng **một** tiêu chí mà nó vi phạm trực tiếp nhất (trường "thuộc tiêu
   chí"; khác với "tìm ra nhờ"). Không trừ một lỗi ở hai tiêu chí. Đơn kẹt vì người duyệt không còn duyệt được thuộc
   tiêu chí 3, kể cả khi tìm ra nhờ tiêu chí 11.
2. **Điểm tiêu chí** bắt đầu từ 10, trừ theo mỗi lỗi **chưa sửa**: Cao −4, Trung bình −2, Thấp −0,5; thấp nhất 0.
3. **Kiểm một phần:** tiêu chí chỉ kiểm được một phần thì điểm = min(10 − tổng trừ, 8) và ghi rõ phần chưa kiểm.
   **Chưa kiểm:** không chấm, bỏ khỏi điểm tổng.
4. **Điểm tổng** = trung bình có trọng số của các tiêu chí đã chấm (chia cho tổng trọng số của chúng), làm tròn 1 chữ
   số thập phân.
5. **Trần:** còn lỗi Cao chưa sửa thì điểm tổng tối đa 7,0; còn từ hai lỗi Cao thì tối đa 5,0.
6. **Xếp loại:** 9,0–10 Tốt · 7,0–8,9 Khá · 5,0–6,9 Trung bình · dưới 5,0 Yếu.

Trọng số là lựa chọn của project (phần nào sai thì đắt nhất); đổi trọng số thì ghi vào "Lịch sử thay đổi", vì điểm
các lần chạy chỉ so sánh được khi cùng trọng số.

### Gợi ý lệnh kiểm

Chạy ở gốc repo. Script trong `api/docs/prompt/scripts/` chỉ đọc file và in danh sách cần xem: mỗi dòng là chỉ báo,
mở code xác nhận rồi mới ghi thành lỗi. Ghi chú "lần 2026-11-05" là kết quả đã xét, lần sau chỉ cần xem phần khác đi.

- **Quyền (tiêu chí 1) và độ phủ test (tiêu chí 9):** `node api/docs/prompt/scripts/controller-calls.mjs`.
  - Phần đầu: lời gọi service từ controller mà method không có `@PreAuthorize` (đã tính annotation trên class và
    annotation gộp như `@CanManageRbac`). Mỗi dòng phải là endpoint công khai, của chính người gọi, hoặc tự kiểm tra
    trong code. Lần 2026-11-05: 160 lời gọi, 10 dòng, đều hợp lệ (`AuthService` ×5, kích hoạt tài khoản, hai endpoint
    branding, `CompanyProfileService.get`, `LeaveEntitlementService.getBalance`).
  - Phần sau: service không test nào chạm tới. `NONE` kết luận được; `PATH` là có file nhắc đường dẫn, mở file xem có
    thật sự gọi service không (test chặn BFF không tính). Lần 2026-11-05: `NONE` `JobPositionService`,
    `OrganizationCompanyOwnerProvisioningService`, `RoleAssignmentOptionService`; `PATH` `AccountActivationService`
    (chỉ `bff-guards.spec.ts`, không tính), `ApiTrafficService` và `EmployeeSensitiveDataService` (có test thật).
  - Danh mục permission ↔ code đã có `PermissionCatalogueTest` giữ: test pass là đủ, không đối chiếu tay.
- **Thời gian (tiêu chí 6):** lệnh sau phải rỗng. Đừng grep `LocalDate.now()\|Instant.now()`: bỏ sót
  `now(ZoneId…)`, `YearMonth.now()` và chỉ khớp Javadoc của `ClockConfig`.

  ```bash
  grep -rn "\.now(" api/src/main/java --include=*.java | grep -v "config/seed" | grep -v "now(clock)"
  ```

- `grep -rn "EffectiveToIsNull" api/src/main/java/com/htttdn/hrm/service`: mỗi chỗ dùng có cần "đang hiệu lực hôm
  nay" không. Lần 2026-11-05: `ApproverResolver` (`API-002`), `LeaveEntitlementService` (`API-005`),
  `EmploymentLifecycleService` ×2 (`API-007`).
- **Bất biến (tiêu chí 5):** `grep -n "EXCLUDE\|UNIQUE" api/src/main/resources/db/migration/*.sql` chỉ thấy bất biến
  đã có ràng buộc. Với mỗi bất biến trong `concepts/` không có ràng buộc, tìm khóa `FOR UPDATE` trong service và xét
  giao dịch cạnh tranh có phải chờ cùng khóa không: khóa dòng của mình rồi đọc dòng khác không khóa là chưa đủ
  (`API-010`).
- **Đầu vào bảng lương (tiêu chí 4):** là các bảng mà `PayslipCalculator`, `InsuranceTaxCalculator`,
  `AttendanceCalendarService` đọc. Lệnh sau in service ghi (`save`/`delete`) vào từng bảng và service đó có gọi
  `PayrollPeriodInvalidator` không:

  ```bash
  S=api/src/main/java/com/htttdn/hrm/service
  for repo in $(grep -hoE "private final \w+Repository" $S/payroll/PayslipCalculator.java \
      $S/payroll/InsuranceTaxCalculator.java $S/attendance/AttendanceCalendarService.java \
      | awk '{print $3}' | grep -v "^Payslip" | sort -u); do
    for f in $(grep -rl "private final $repo " $S); do
      field=$(grep -oE "private final $repo \w+" "$f" | awk '{print $4}')
      grep -qE "\b$field\.(save|delete)\w*\(" "$f" || continue
      printf "%-34s %-44s %s\n" "$repo" "${f#$S/}" "$(grep -q PayrollPeriodInvalidator "$f" && echo invalidator || echo NO)"
    done
  done
  ```

  Dòng `NO` phải có lý do khác: sửa chấm công sau lúc tính thì bị chặn khi duyệt kỳ (`PayrollService.approve` so
  `updated_at`). Bảng quy tắc thuế/bảo hiểm không có service ghi (dữ liệu tham chiếu trong `V8`). Lệnh không thấy chỗ
  ghi bằng setter mà không gọi `save` (JPA tự lưu), VD `EmploymentLifecycleService` đóng phân công: xem thêm các
  service đổi ngày hay trạng thái của bản ghi đầu vào. Lần 2026-11-05: 12 dòng, `NO` chỉ có `AttendanceService` và
  `LeaveAttendanceService`.
- **Validation (tiêu chí 7):**
  - `grep -rn "@RequestBody" api/src/main/java/com/htttdn/hrm/controller | grep -v "@Valid"`. Lần 2026-11-05: hai body
    tùy chọn không mang ràng buộc nào (`PayrollController.java:74, 91`), hợp lệ.
  - `node api/docs/prompt/scripts/request-sizes.mjs`: trường chuỗi của request thiếu `@Size`. Lần 2026-11-05: 107
    trường, 7 thiếu, đều hợp lệ (`keyword`, `roleCode` của tiêu chí tìm kiếm; `MinimalEmployeeCommand` nội bộ). Giới
    hạn mới hay đổi thì so tay với cột trong migration; đừng so theo tên trường (8/8 dương tính giả).
  - `node api/docs/prompt/scripts/error-codes.mjs`: `ErrorCode` không dùng (tiêu chí 10) và thiếu bản dịch trên web
    (tiêu chí 7). Đừng đếm bằng grep chỉ bắt dòng có dấu phẩy: bỏ sót phần tử cuối của enum.
  - Request sai dạng: lệnh dò ở mục "Chạy test và API trên DB tạm".
- **Seed (tiêu chí 8):** `SeedDataIntegrationTest` chạy trong bộ test, `seed-checks.sql` chạy trên DB tạm (mục dưới).
  Bảng seeder ghi thẳng:
  `grep -rhoE "\b\w+Repository\.(save|saveAll|saveAndFlush)\(" api/src/main/java/com/htttdn/hrm/config/seed | sed 's/\..*//' | sort | uniq -c`.
  Với bảng có quy tắc nghiệp vụ, so cách seeder chọn giá trị với kiểm tra của service (VD người duyệt so với
  `ApproverResolver`, `API-015`).
- **Tài liệu (tiêu chí 10):** `node api/docs/prompt/scripts/docs-vs-code.mjs` in link tương đối hỏng trong `api/docs`
  và `docs`, và endpoint không tài liệu nào trong `api/docs/api`, `api/docs/concepts` nhắc dạng `METHOD /api/…`.
  Endpoint chỉ được nhắc trong `concepts/` (rate limit, traffic) vẫn tính là có tài liệu. Script không kiểm tên
  method: class đã chuyển chỗ (link hỏng) thường kéo theo method đổi tên, xem tay. Số liệu:
  `grep -rnoE "\b[0-9]+ (controller|permission|bảng|module|system role)" api/docs docs --include=*.md | grep -v "/archive/\|/prompt/"`,
  so với code (VD `ls api/src/main/java/com/htttdn/hrm/controller | wc -l`).
- Tùy chọn, khi quy định thuế/bảo hiểm thay đổi hoặc `V8__reference_data.sql` đổi: đối chiếu công thức trong
  `PayslipCalculator`/`InsuranceTaxCalculator` với quy định năm hiện hành.

### Chạy test và API trên DB tạm

Cần container `hrm-database-1` đang chạy (`docker compose up -d database`). Test chạy trên DB trống để không phụ
thuộc dữ liệu dev; ghi số test và kết quả (lần 2026-11-05: 584 test, khoảng 5,5 phút):

```bash
docker exec hrm-database-1 psql -U hrm-user -d postgres -c "CREATE DATABASE hrm_rev_tmp"
(cd api && DB_URL=jdbc:postgresql://localhost:5432/hrm_rev_tmp ./mvnw clean test > /tmp/hrm_test.log 2>&1)
grep -E "Tests run:|BUILD" /tmp/hrm_test.log | tail -3
docker exec hrm-database-1 psql -U hrm-user -d postgres -c "DROP DATABASE hrm_rev_tmp WITH (FORCE)"
```

Sau đó chạy API trên một DB tạm khác, ở cổng 18080 để không đụng API dev. Seeder chạy **sau khi** cổng đã mở, nên
đừng chờ bằng cách gọi thử một endpoint: chờ dòng log của seeder cuối (`PayrollSeeder`, `@Order(520)`; thêm seeder
chạy sau nó thì đổi dòng chờ). Seed mất khoảng 2,5 phút; vòng chờ tối đa 5 phút, quá thì xem `/tmp/hrm_api.log`. Chạy `seed-checks.sql` ngay
khi seed xong, trước khi gửi request nào làm đổi dữ liệu; mỗi truy vấn phải trả 0 dòng:

```bash
rm -f /tmp/hrm_api.log
docker exec hrm-database-1 psql -U hrm-user -d postgres -c "CREATE DATABASE hrm_rev_api"
(cd api && DB_URL=jdbc:postgresql://localhost:5432/hrm_rev_api ./mvnw -q spring-boot:run \
  -Dspring-boot.run.arguments=--server.port=18080 > /tmp/hrm_api.log 2>&1 &)
for i in $(seq 60); do grep -q "Payroll seed" /tmp/hrm_api.log 2>/dev/null && break; sleep 5; done
grep "Payroll seed" /tmp/hrm_api.log
docker exec -i hrm-database-1 psql -U hrm-user -d hrm_rev_api -q < api/docs/prompt/scripts/seed-checks.sql
```

Tài khoản (mật khẩu đều là `password`): `owner` (Company Owner), `hr` (HR_MANAGER phạm vi công ty) và mọi `username`
trong `api/src/main/resources/seed/staff.json`. Gọi thẳng API thì không cần header `Origin`. Lệnh dò lỗi trả về
(tiêu chí 7); hàm `login` dùng lại được khi tái hiện lỗi khác (`TOKEN=$(login hr)`):

```bash
API=http://localhost:18080
login() { curl -s -X POST $API/api/auth/login -H "Content-Type: application/json" \
  -d "{\"usernameOrEmail\":\"$1\",\"password\":\"password\"}" | sed -E 's/.*"accessToken":"([^"]+)".*/\1/'; }
TOKEN=$(login owner)
probe() { printf "%-26s " "$1"; shift; curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOKEN" "$@"; }
probe "missing from/to" $API/api/company-holidays
probe "missing year" $API/api/reports/hr/turnover
probe "Content-Type text/plain" -X POST $API/api/company-holidays -H "Content-Type: text/plain" --data x
probe "malformed JSON" -X POST $API/api/company-holidays -H "Content-Type: application/json" --data "{"
probe "unknown employee" $API/api/employees/999999999
```

Mong đợi 400, 400, 415, 400, 404; dòng nào 500 là lỗi. Lần 2026-11-05: ba dòng đầu trả 500 (`API-009`).

Dọn khi xong (Git Bash trên Windows; Linux/macOS thì `kill` PID đang nghe cổng 18080):

```bash
taskkill //PID $(netstat -ano | grep LISTENING | grep ":18080 " | awk '{print $5}' | head -1) //F
docker exec hrm-database-1 psql -U hrm-user -d postgres -c "DROP DATABASE hrm_rev_api WITH (FORCE)"
```

### Định dạng file kết quả

Lưu thành `api/docs/prompt/API_REVIEW_<yyyy-MM-dd>.md` (ngày chạy), gồm:

1. **Thông tin lần chạy**: ngày, commit (`git log --oneline -1`), số test và kết quả, có chạy API trên DB tạm không
   (lỗi nào tái hiện bằng request thật), người/agent chạy.
2. **Điểm**: điểm tổng /10, trung bình có trọng số trước khi áp trần, xếp loại, trần áp dụng (nếu có), và bảng mỗi
   tiêu chí một dòng: trọng số, điểm /10 (hoặc "Chưa kiểm"), các lỗi đã trừ (mã và mức), bằng chứng ngắn (tối đa hai
   câu; chi tiết để ở từng lỗi).
3. **Danh sách lỗi**: mỗi lỗi có mã ổn định `API-<số>`, mức độ (Cao/Trung bình/Thấp), **thuộc tiêu chí** (đúng một),
   vị trí `file:dòng`, mô tả, tình huống gây lỗi cụ thể, đề xuất sửa, **tìm ra nhờ** (số tiêu chí, hoặc "ngoài tiêu
   chí"), **xác nhận bằng** (request thật / test / truy vấn DB / đọc code), trạng thái (`Chưa sửa` /
   `Đã sửa <commit>`).
4. **So sánh với lần trước**: điểm tổng và điểm từng tiêu chí trước → sau; lỗi đã sửa (kèm commit), lỗi còn tồn,
   lỗi mới.
5. **Phạm vi chưa kiểm**.

---

## Lịch sử thay đổi

| Ngày | Thay đổi | Review |
|---|---|---|
| 2026-11-03 | Bản đầu tiên | — |
| 2026-11-03 | Đọc `concepts/` trước; gộp tiêu chí thời gian thành "Thời gian và hiệu lực"; thêm tiêu chí 11 "Vòng đời và tham chiếu"; bỏ đánh giá kích thước class; sửa gợi ý lệnh đối chiếu permission, kiểm tra quyền, độ phủ test; mã lỗi `API-<số>` và trường "tìm ra nhờ"; bước tùy chọn đối chiếu công thức lương. Lỗi 1–6 của lần chạy 2026-11-03 tương ứng `API-001`…`API-006`. | [PROMPT_REVIEW_API_REVIEW_2026-11-03](./PROMPT_REVIEW_API_REVIEW_2026-11-03.md) |
| 2026-11-03 | Chuyển từ Đạt/Chưa đạt sang thang điểm 10: trọng số từng tiêu chí, trừ điểm theo mức lỗi, trần khi còn lỗi Cao, trường "thuộc tiêu chí". File kết quả 2026-11-03 được chuyển sang định dạng này (điểm tổng 7,0). | Yêu cầu của người dùng |
| 2026-11-05 | Quy tắc 3: kiểm một phần = min(10 − tổng trừ, 8), cùng câu chữ với `/review-prompt-create` và prompt web. Điểm lần 2026-11-03 không đổi (tiêu chí 8 kiểm một phần, không có lỗi trừ, vẫn 8). | Đồng bộ theo [PROMPT_REVIEW_WEB_REVIEW_2026-11-05](../../../apps/web/docs/prompt/PROMPT_REVIEW_WEB_REVIEW_2026-11-05.md) |
| 2026-11-05 | Gợi ý lệnh dùng script `controller-calls.mjs`, `request-sizes.mjs`, `error-codes.mjs`, `docs-vs-code.mjs`, `seed-checks.sql` kèm kết quả đã xét; bỏ đối chiếu permission bằng tay (đã có `PermissionCatalogueTest`); sửa lệnh `Clock`; thêm lệnh đầu vào bảng lương; mục chạy test và API trên DB tạm (tài khoản, dò lỗi trả về, dọn) và nguyên tắc tái hiện trước khi ghi; tiêu chí 5 xét từng bất biến trong `concepts/`; tiêu chí 7 gồm request sai dạng; tiêu chí 8 có phần bắt buộc; tiêu chí 10 bỏ "endpoint không dùng", thêm link và endpoint thiếu tài liệu; tiêu chí 11 gồm đơn của chính người kết thúc, đơn kẹt ở người duyệt thuộc tiêu chí 3; định nghĩa mức Cao/Trung bình/Thấp (giữ nguyên mức mọi lỗi cũ); file kết quả có "xác nhận bằng", trung bình trước trần, bằng chứng tối đa hai câu. Điểm tiêu chí 8 không còn so trực tiếp với các lần trước (trước luôn bị trần 8 vì kiểm một phần); các tiêu chí khác vẫn so được. | [PROMPT_REVIEW_API_REVIEW_2026-11-05](./PROMPT_REVIEW_API_REVIEW_2026-11-05.md) |
| 2026-11-06 | Phần lược đồ chuyển sang [DB_REVIEW_PROMPT.md](./DB_REVIEW_PROMPT.md): tiêu chí 5 chỉ xét khóa `FOR UPDATE` cho bất biến DB không chặn, tiêu chí 10 bỏ `api/docs/database/`. `API-006` đóng ở lần chạy sau với trạng thái "Chuyển sang `DB-007`"; `API-013` bỏ phần `DATABASE_SCHEMA.md:62` (nay là `DB-008`). Tiêu chí 5 tăng 0,5 so với lần 2026-11-05 chỉ vì chuyển lỗi, không phải vì sửa; tiêu chí 10 không đổi (`API-013` còn các phần khác) | [PROMPT_REVIEW_DB_REVIEW_2026-11-06](./PROMPT_REVIEW_DB_REVIEW_2026-11-06.md) |
