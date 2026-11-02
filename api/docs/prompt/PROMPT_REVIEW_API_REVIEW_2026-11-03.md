# Review prompt API_REVIEW — 2026-11-03

Chạy theo skill `/review-prompt-improve` (Claude Code, cấp user; bản đầu nằm trong thư mục này), ngay sau lần chạy
[API_REVIEW_2026-11-03.md](./API_REVIEW_2026-11-03.md), cùng phiên làm việc.

| Mục | Giá trị |
|---|---|
| Prompt được review | [API_REVIEW_PROMPT.md](./API_REVIEW_PROMPT.md) |
| Kết quả dựa vào | [API_REVIEW_2026-11-03.md](./API_REVIEW_2026-11-03.md) |
| Người chạy | Claude Code |

## 1. Lỗi ngoài tiêu chí

- **Lỗi 1 (đơn kẹt khi người duyệt nghỉ việc)** tìm ra khi tự hỏi "người duyệt nghỉ việc thì sao", không do tiêu chí
  nào dẫn tới. Tiêu chí 3 chỉ nhắc "đơn không bị kẹt" nhưng không chỉ cách tìm. Nhóm tổng quát: **khi một thực thể kết
  thúc** (nhân viên nghỉ việc, role bị thu hồi/xóa, đơn vị ngừng hoạt động) **thì những thứ đang tham chiếu tới nó**
  (đơn chờ duyệt, phân công, lần gán role, phạm vi) được xử lý thế nào.
- **Lỗi 2 và 5 (lấy phân công "mở" thay vì "đang hiệu lực")** tìm ra nhờ để ý tên method
  `findFirst…EffectiveToIsNull`. Nhóm tổng quát: **truy vấn "hiện tại" trên dữ liệu theo khoảng hiệu lực** phải xét theo
  ngày, nhất là khi cho phép bản ghi hẹn ngày tương lai. Tiêu chí 6 hiện chỉ kiểm `Clock`, không kiểm điều này.
- Ba lỗi đã sửa trước lần chạy (phạm vi theo tên role, seed sai phạm vi, permission không dùng) thuộc nhóm "giữa các
  phần" mà tiêu chí 1, 8, 10 đã bao quát; không cần thêm.

## 2. Lệnh gợi ý cho kết quả sai

- **Đối chiếu permission bằng regex chuỗi** khớp nhầm `account.employee` (đường dẫn JPQL trong `AccountRepository`) →
  dương tính giả. Sửa: chỉ lấy mã từ `@PreAuthorize(...)` và hằng `static final String` trong `service/`, so với tiền tố
  module của `PermissionModule`; bỏ qua `repository/`.
- **Tìm method public thiếu `@PreAuthorize`** trả ~60 method, phần lớn là helper nội bộ → nhiễu. Sửa: bắt đầu từ lời gọi
  trong `controller/`, rồi mới xem method đó có kiểm tra quyền không.
- **Tìm test theo tên class** báo 6 service "không có test" trong khi một số được phủ qua test endpoint hoặc E2E → âm
  tính giả về độ phủ. Sửa: ghi rõ đây chỉ là chỉ báo; muốn kết luận thì tìm theo đường dẫn endpoint trong `src/test` và
  `apps/web/e2e`.

## 3. Tiêu chí không hiệu quả

- **Tiêu chí 6 (thời gian và múi giờ)** kiểm xong bằng một lệnh grep, không tìm ra gì; trong khi lỗi thời gian thật
  (lỗi 2, 5) lại nằm ở hiệu lực theo ngày. Gộp thành **"Thời gian và hiệu lực"**.
- **Tiêu chí 10 ("class quá lớn")** không có ngưỡng, kết quả chỉ là ghi số dòng. Giữ phần code chết và tài liệu ↔ code
  (đã tìm ra lỗi thật: số permission, tổng quyền mỗi role); bỏ đánh giá kích thước class trừ khi có lý do cụ thể.
- Tiêu chí 2 (dữ liệu nhạy cảm) kiểm nhanh và không tìm ra gì, nhưng rủi ro cao nếu hỏng → giữ nguyên.

## 4. Thiếu ngữ cảnh

- `api/docs/concepts/` đã mô tả quy tắc dự kiến: `APPROVAL_ROUTING.md`, `EFFECTIVE_DATING.md`, `DATA_SCOPE.md`,
  `PAYROLL_LIFECYCLE.md`, `ROLE_GRANT_POLICY.md`. Lần chạy phải tự lần lại các quy tắc này trong code. Prompt nên yêu
  cầu đọc chúng trước và đối chiếu code với tài liệu.
- Cách chạy test trên DB tạm đã có trong `api/AGENTS.md`; prompt đã trỏ tới, giữ nguyên.

## 5. Định dạng kết quả

- Lỗi đánh số "Lỗi 1, 2…" theo từng lần chạy nên lần sau khó so sánh. Đổi sang **mã ổn định** `API-<số>`, giữ nguyên
  mã qua các lần chạy. Ánh xạ cho lần chạy 2026-11-03: Lỗi 1–6 → `API-001`…`API-006`.
- Thêm cột/trường **"Tìm ra nhờ"** (tiêu chí nào, hay ngoài tiêu chí) để lần review prompt sau biết tiêu chí nào hiệu
  quả.

## 6. Phạm vi chưa kiểm

- Công thức lương (thuế, bảo hiểm): giữ ngoài phạm vi mặc định, thêm làm **bước tùy chọn** khi quy định thuế/bảo hiểm
  thay đổi hoặc khi `V8__reference_data.sql` đổi.
- Kiểm toàn bộ `staff.json`: sẽ được tự động hóa khi `API-003` (test seed ↔ quy tắc) xong; không thêm bước tay.

## Thay đổi đã áp dụng vào API_REVIEW_PROMPT.md

| # | Thay đổi | Lý do (mục) |
|---|---|---|
| 1 | Thêm bước đọc `api/docs/concepts/` và đối chiếu code với tài liệu | 4 |
| 2 | Gộp tiêu chí 6 thành "Thời gian và hiệu lực", thêm kiểm truy vấn "hiện tại" theo ngày và bản ghi hẹn tương lai | 1, 3 |
| 3 | Thêm tiêu chí "Vòng đời và tham chiếu" | 1 |
| 4 | Bỏ đánh giá kích thước class khỏi tiêu chí 10 | 3 |
| 5 | Sửa gợi ý lệnh: đối chiếu permission, tìm method thiếu kiểm tra quyền, độ phủ test | 2 |
| 6 | Mã lỗi ổn định `API-<số>`, trường "Tìm ra nhờ" | 5 |
| 7 | Bước tùy chọn: đối chiếu công thức lương với quy định | 6 |
| 8 | Thêm mục "Lịch sử thay đổi" | quy ước của `/review-prompt-improve` |

Số tiêu chí giữ 10→11 (gộp 1, thêm 2): tăng một tiêu chí vì nhóm lỗi "vòng đời và tham chiếu" tìm ra lỗi mức Cao duy
nhất của lần chạy.
