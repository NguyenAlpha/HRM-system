# Prompt đánh giá API HRM

Prompt dùng lại để đánh giá toàn bộ `api` theo một bộ tiêu chí cố định. Mỗi lần chạy lưu kết quả thành một file
mới trong thư mục này, tên `API_REVIEW_<yyyy-MM-dd>.md` (VD `API_REVIEW_2026-11-03.md`); không sửa file của lần
chạy trước.

Cách dùng: dán phần **Prompt** bên dưới cho agent (Claude Code…), mở ở thư mục gốc repo.

---

## Prompt

Đánh giá một vòng API trong `api/` của dự án HRM theo bộ tiêu chí dưới đây. Đọc `AGENTS.md` và `api/AGENTS.md`
trước. Chỉ đánh giá và ghi kết quả, **không sửa code** trong lần chạy này.

### Nguyên tắc

- Mỗi kết luận phải có bằng chứng: đường dẫn `file:dòng`, lệnh đã chạy hoặc kết quả truy vấn. Không kết luận
  theo cảm tính hay theo tên method.
- Ưu tiên lỗi **giữa các phần**, không chỉ trong từng module: danh mục permission ↔ code kiểm tra quyền, quy tắc
  nghiệp vụ ↔ dữ liệu seed, tài liệu ↔ code, DTO ↔ type sinh ra cho web.
- Phần nào không kiểm được thì ghi rõ "chưa kiểm" và lý do, không bỏ qua im lặng.
- Đọc file kết quả gần nhất trong `api/docs/prompt/` (nếu có) để so sánh: lỗi nào đã sửa, lỗi nào còn, lỗi nào mới.

### Bộ tiêu chí

| # | Tiêu chí | Cần kiểm |
|---|---|---|
| 1 | Phân quyền và phạm vi dữ liệu | Mọi method controller gọi được có `@PreAuthorize` hoặc kiểm tra tương đương (người duyệt, chính chủ, `EmployeeAccessScopeService`); thao tác trên nhân viên có kiểm tra phạm vi; thao tác chung cả công ty gọi `requireCompanyWide`; permission `companyWide` đánh dấu đúng |
| 2 | Bảo vệ dữ liệu nhạy cảm | CCCD, ngân hàng, mã số thuế, lương chỉ xuất hiện ở endpoint có quyền tương ứng; log không in mật khẩu, token, dữ liệu nhạy cảm |
| 3 | Quy trình duyệt đúng nghiệp vụ | Người duyệt chọn đúng theo phân công đang hiệu lực; đơn không bị kẹt khi người duyệt nghỉ việc/đổi quản lý; trạng thái đơn chuyển hợp lệ |
| 4 | Toàn vẹn bảng lương | Đổi đầu vào (lương, phụ cấp, phân công, khấu trừ, chấm công, ngày lễ) sau khi tính thì kỳ lương phải tính lại hoặc bị chặn; tách người tính/người duyệt; kỳ hủy không còn phiếu lương |
| 5 | Toàn vẹn dữ liệu và đồng thời | Ràng buộc DB (unique, exclusion) cho bất biến quan trọng; chỗ nào chỉ dựa vào khóa trong code |
| 6 | Thời gian và múi giờ | Không có `LocalDate.now()`/`Instant.now()` thiếu `Clock` ngoài seeder |
| 7 | Validation và lỗi trả về | `@RequestBody` có `@Valid`; trường chuỗi có `@Size` khớp cột DB; lỗi trả mã `ErrorCode` để web dịch, không lộ 500 |
| 8 | Dữ liệu seed khớp quy tắc | Seeder có đi qua cùng kiểm tra với API không; dữ liệu demo có vi phạm quy tắc nào (phạm vi role, hạn mức phép…) |
| 9 | Kiểm thử | Toàn bộ test pass; service nào không có test; có test giữ các phần khớp nhau (permission ↔ code, seed ↔ quy tắc) không |
| 10 | Bảo trì và tài liệu | Class quá lớn, code chết (permission/ErrorCode/endpoint không dùng), số liệu và mô tả trong `api/docs` khớp code |

### Gợi ý lệnh kiểm

- Method public thiếu `@PreAuthorize`, rồi đối chiếu với lời gọi trong `controller/`.
- Đối chiếu permission: mọi code trong `PermissionSeeder` phải được code ngoài `config/seed` dùng, và ngược lại.
- `grep -rn "LocalDate.now()\|Instant.now()"` ngoài `config/seed`.
- `grep -n "EXCLUDE\|UNIQUE"` trong `src/main/resources/db/migration`.
- `@RequestBody` không có `@Valid` trong `controller/`.
- Chạy test trên DB tạm (xem `api/AGENTS.md`), ghi số test và kết quả.

### Định dạng file kết quả

Lưu thành `api/docs/prompt/API_REVIEW_<yyyy-MM-dd>.md` (ngày chạy), gồm:

1. **Thông tin lần chạy**: ngày, commit (`git log --oneline -1`), số test và kết quả, người/agent chạy.
2. **Bảng tiêu chí**: mỗi tiêu chí một dòng với kết quả `Đạt` / `Đạt có lưu ý` / `Chưa đạt` / `Chưa kiểm` và
   bằng chứng ngắn.
3. **Danh sách lỗi**: mỗi lỗi có mức độ (Cao/Trung bình/Thấp), vị trí `file:dòng`, mô tả, tình huống gây lỗi cụ
   thể, đề xuất sửa, trạng thái (`Chưa sửa`).
4. **So sánh với lần trước**: lỗi đã sửa (kèm commit), lỗi còn tồn, lỗi mới.
5. **Phạm vi chưa kiểm**.
