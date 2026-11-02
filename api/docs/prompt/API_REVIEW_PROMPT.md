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
Chỉ đánh giá và ghi kết quả, **không sửa code** trong lần chạy này.

### Nguyên tắc

- Mỗi kết luận phải có bằng chứng: đường dẫn `file:dòng`, lệnh đã chạy hoặc kết quả truy vấn. Không kết luận
  theo cảm tính hay theo tên method.
- Ưu tiên lỗi **giữa các phần**, không chỉ trong từng module: danh mục permission ↔ code kiểm tra quyền, quy tắc
  nghiệp vụ ↔ dữ liệu seed, tài liệu ↔ code, DTO ↔ type sinh ra cho web.
- Phần nào không kiểm được thì ghi rõ "chưa kiểm" và lý do, không bỏ qua im lặng.
- Đọc file kết quả gần nhất `API_REVIEW_*.md` trong `api/docs/prompt/` (nếu có) để so sánh: lỗi nào đã sửa, lỗi nào
  còn, lỗi nào mới. Giữ nguyên mã lỗi `API-<số>` của lỗi cũ; lỗi mới lấy số tiếp theo.

### Bộ tiêu chí

| # | Tiêu chí | Cần kiểm |
|---|---|---|
| 1 | Phân quyền và phạm vi dữ liệu | Mọi method controller gọi được có `@PreAuthorize` hoặc kiểm tra tương đương (người duyệt, chính chủ, `EmployeeAccessScopeService`); thao tác trên nhân viên có kiểm tra phạm vi; thao tác chung cả công ty gọi `requireCompanyWide`; permission `companyWide` đánh dấu đúng |
| 2 | Bảo vệ dữ liệu nhạy cảm | CCCD, ngân hàng, mã số thuế, lương chỉ xuất hiện ở endpoint có quyền tương ứng; log không in mật khẩu, token, dữ liệu nhạy cảm |
| 3 | Quy trình duyệt đúng nghiệp vụ | Người duyệt chọn đúng theo phân công đang hiệu lực; đơn không bị kẹt khi người duyệt nghỉ việc/đổi quản lý; trạng thái đơn chuyển hợp lệ |
| 4 | Toàn vẹn bảng lương | Đổi đầu vào (lương, phụ cấp, phân công, khấu trừ, chấm công, ngày lễ) sau khi tính thì kỳ lương phải tính lại hoặc bị chặn; tách người tính/người duyệt; kỳ hủy không còn phiếu lương |
| 5 | Toàn vẹn dữ liệu và đồng thời | Ràng buộc DB (unique, exclusion) cho bất biến quan trọng; chỗ nào chỉ dựa vào khóa trong code |
| 6 | Thời gian và hiệu lực | Không có `LocalDate.now()`/`Instant.now()` thiếu `Clock` ngoài seeder; mọi truy vấn "hiện tại" trên dữ liệu theo khoảng hiệu lực (phân công, lương, phụ cấp, lần gán role) xét theo ngày chứ không lấy bản ghi "mở" (`…EffectiveToIsNull`), vì bản ghi có thể hẹn ngày tương lai |
| 7 | Validation và lỗi trả về | `@RequestBody` có `@Valid`; trường chuỗi có `@Size` khớp cột DB; lỗi trả mã `ErrorCode` để web dịch, không lộ 500 |
| 8 | Dữ liệu seed khớp quy tắc | Seeder có đi qua cùng kiểm tra với API không; dữ liệu demo có vi phạm quy tắc nào (phạm vi role, hạn mức phép…) |
| 9 | Kiểm thử | Toàn bộ test pass; service nào không có test; có test giữ các phần khớp nhau (permission ↔ code, seed ↔ quy tắc) không |
| 10 | Bảo trì và tài liệu | Code chết (permission/ErrorCode/endpoint không dùng); số liệu và mô tả trong `api/docs` (kể cả `concepts/`) khớp code |
| 11 | Vòng đời và tham chiếu | Khi một thực thể kết thúc (nhân viên nghỉ việc, tài khoản bị khóa, role bị thu hồi/xóa, đơn vị/địa điểm ngừng hoạt động), những thứ đang tham chiếu tới nó được xử lý: đơn chờ người đó duyệt, phân công có quản lý là người đó, lần gán role theo đơn vị đó, phạm vi dữ liệu |

### Gợi ý lệnh kiểm

- Kiểm tra quyền: bắt đầu từ lời gọi service trong `controller/`, rồi xem method đó có `@PreAuthorize` hoặc kiểm tra
  tương đương. Đừng liệt kê mọi method public thiếu annotation: phần lớn là helper nội bộ.
- Đối chiếu permission: lấy mã từ `@PreAuthorize(...)` và hằng `static final String` trong `service/` (bỏ qua
  `repository/`, nơi có đường dẫn JPQL như `account.employee` trông giống mã permission), so hai chiều với
  `PermissionSeeder`.
- `grep -rn "LocalDate.now()\|Instant.now()"` ngoài `config/seed`.
- `grep -n "EXCLUDE\|UNIQUE"` trong `src/main/resources/db/migration`.
- `@RequestBody` không có `@Valid` trong `controller/`.
- `grep -rn "EffectiveToIsNull"` trong `service/`: mỗi chỗ dùng có cần "đang hiệu lực hôm nay" không.
- Độ phủ test: tìm test theo tên class chỉ là chỉ báo; muốn kết luận một service chưa có test thì tìm đường dẫn
  endpoint của nó trong `src/test` và `apps/web/e2e`.
- Chạy test trên DB tạm (xem `api/AGENTS.md`), ghi số test và kết quả.
- Tùy chọn, khi quy định thuế/bảo hiểm thay đổi hoặc `V8__reference_data.sql` đổi: đối chiếu công thức trong
  `PayslipCalculator`/`InsuranceTaxCalculator` với quy định năm hiện hành.

### Định dạng file kết quả

Lưu thành `api/docs/prompt/API_REVIEW_<yyyy-MM-dd>.md` (ngày chạy), gồm:

1. **Thông tin lần chạy**: ngày, commit (`git log --oneline -1`), số test và kết quả, người/agent chạy.
2. **Bảng tiêu chí**: mỗi tiêu chí một dòng với kết quả `Đạt` / `Đạt có lưu ý` / `Chưa đạt` / `Chưa kiểm` và
   bằng chứng ngắn.
3. **Danh sách lỗi**: mỗi lỗi có mã ổn định `API-<số>`, mức độ (Cao/Trung bình/Thấp), vị trí `file:dòng`, mô tả,
   tình huống gây lỗi cụ thể, đề xuất sửa, **tìm ra nhờ** (số tiêu chí, hoặc "ngoài tiêu chí"), trạng thái
   (`Chưa sửa` / `Đã sửa <commit>`).
4. **So sánh với lần trước**: lỗi đã sửa (kèm commit), lỗi còn tồn, lỗi mới.
5. **Phạm vi chưa kiểm**.

---

## Lịch sử thay đổi

| Ngày | Thay đổi | Review |
|---|---|---|
| 2026-11-03 | Bản đầu tiên | — |
| 2026-11-03 | Đọc `concepts/` trước; gộp tiêu chí thời gian thành "Thời gian và hiệu lực"; thêm tiêu chí 11 "Vòng đời và tham chiếu"; bỏ đánh giá kích thước class; sửa gợi ý lệnh đối chiếu permission, kiểm tra quyền, độ phủ test; mã lỗi `API-<số>` và trường "tìm ra nhờ"; bước tùy chọn đối chiếu công thức lương. Lỗi 1–6 của lần chạy 2026-11-03 tương ứng `API-001`…`API-006`. | [PROMPT_REVIEW_API_REVIEW_2026-11-03](./PROMPT_REVIEW_API_REVIEW_2026-11-03.md) |
