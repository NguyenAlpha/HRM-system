# Review prompt đánh giá web — 2026-11-05

| Mục | Giá trị |
|---|---|
| Ngày | 2026-11-05 |
| Prompt | [WEB_REVIEW_PROMPT.md](./WEB_REVIEW_PROMPT.md) (bản đầu tiên, cùng ngày) |
| File kết quả đã dựa vào | [WEB_REVIEW_2026-11-05.md](./WEB_REVIEW_2026-11-05.md) |
| Cùng phiên với lần chạy | Có: dựa vào lệnh đã chạy, ngõ cụt và kết quả sai trong phiên, không chỉ file kết quả |

## Câu trả lời

### 0. Thay đổi lần trước có hiệu quả không

Bỏ qua: đây là lần review prompt đầu tiên.

### 1. Lỗi ngoài tiêu chí

Không có lỗi nào "ngoài tiêu chí": cả 6 lỗi đều tìm ra nhờ đúng tiêu chí của nó. Nhưng hai lỗi tìm được nhờ việc làm
**ngoài lệnh gợi ý**, nên prompt chỉ dẫn tới chúng một cách tình cờ:

- `WEB-003` (lý do đổi lương): tiêu chí 4 chỉ bảo so form với `@Size` của DTO, mà DTO này không có `@Size`. Lỗi lộ ra
  khi xem cột DB (`VARCHAR(250)`) và cách `GlobalExceptionHandler` trả lỗi DB (`409 CONFLICT` câu chung). Nhóm tổng
  quát: **giới hạn thật nằm ở cột DB khi DTO không khai báo**.
- `WEB-001` (thao tác chỉ cho phạm vi công ty): tìm ra nhờ gợi ý `grep "scopeType === \"COMPANY\""`, nhưng phải tự
  đối chiếu tay từng `requireCompanyWide` với danh mục permission để thấy gốc lỗi: API dùng `requireCompanyWide` với
  permission không `companyWide`. Nhóm tổng quát: **permission được kiểm `requireCompanyWide` nhưng không đánh dấu
  `companyWide`**; mỗi permission như vậy là một chỗ web có thể hiện nút sai.

### 2. Lệnh gợi ý sai

- **`toISOString().slice`**: 3/3 kết quả là dương tính giả. Prompt chỉ nêu trường hợp "ngày 15"; hai chỗ còn lại tạo
  ngày bằng `Date.UTC(...)` rồi đọc lại bằng `toISOString()` (cùng UTC nên không lệch). Sửa ghi chú.
- **Ghi chú E2E đã cũ**: prompt bảo `pnpm dev` từng trả 404 cho route BFF; lần này E2E 11/11 pass trên `pnpm dev`. Sửa
  thành "nếu gặp 404 thì…".
- **Lệnh dùng trong lúc tạo prompt bằng `grep -B4 companyWideOnly`** (để lấy danh sách permission `companyWide`) đếm ra
  23 thay vì 17 và che mất `compensation.manage`. Không có trong prompt, nhưng là lý do thêm script
  `company-wide-gaps.mjs` thay cho grep.
- Script tạm đối chiếu lời gọi `workforceApi` báo sai 9 lần (`${base}/…`, `${a ? "approve" : "reject"}`) và xếp nhầm
  biểu thức trong query string vào nhóm cần kiểm tay. Bản đưa vào repo bỏ qua query string và chỉ coi `${…Id}` là id.

### 3. Tiêu chí không hiệu quả

- Tiêu chí 5–9 không tìm ra lỗi nào, nhưng mỗi tiêu chí có lệnh kiểm thật và kết luận có bằng chứng (mã lỗi 94/94,
  bảng nhãn, cache, quy ước giao diện). Một lần chạy chưa đủ để bỏ; giữ nguyên.
- Tiêu chí 3 và 4 **luôn sẽ là "kiểm một phần"** vì "Cần kiểm" đòi mọi nút, mọi form. Hai người chạy sẽ kiểm phạm vi
  khác nhau mà vẫn cùng bị chặn ở 8 điểm, nên điểm hai tiêu chí này không phân biệt được lần kiểm kỹ với lần kiểm sơ.
  Viết lại: nêu rõ phần **bắt buộc** (kiểm đủ phần này thì không bị coi là một phần) và phần lấy mẫu.

### 4. Thiếu ngữ cảnh

- Phải tự tìm cách so `queryKeys` với query param của API (đọc `forwardWithSession`, viết lệnh `node -e` đọc
  `openapi.json`): thêm script `query-params.mjs`.
- Phải tự viết script đối chiếu lời gọi trang ↔ whitelist: thêm `callers-vs-bff.mjs`.
- Phải tự nghĩ cách xác minh lỗi `Origin` bằng `curl`: thêm lệnh dò cả 8 handler phiên với tài khoản không tồn tại
  (không tạo phiên thật; 403 là có kiểm, 401/200 là không).
- Lỗi DB vượt độ dài trả `409 CONFLICT` câu chung (`GlobalExceptionHandler.handleDataIntegrity`): ghi vào prompt để
  người chạy biết vì sao lệch giới hạn ở DB là lỗi trải nghiệm thật.
- Các lệnh heuristic đã dùng cho tiêu chí 6 và 9 (vòng lặp bảng thiếu trạng thái tải/rỗng, nút đặt thẳng trong bảng,
  màu Tailwind trực tiếp) kèm các ngoại lệ đã biết (nút "Cấp tài khoản" của bảng mẫu, form sửa bậc phụ cấp, màu chấm
  công): thêm vào gợi ý để lần sau không phải xét lại.

### 5. Định dạng kết quả

- Thiếu chỗ ghi web chạy bằng `pnpm dev` hay `pnpm start` khi E2E: thêm vào "Thông tin lần chạy".
- Với tiêu chí kiểm một phần, file kết quả phải tự giải thích "9,5 → 8" (tiêu chí 4). Quy tắc chấm chưa nói rõ trần 8 áp
  sau khi trừ: thêm "điểm = min(10 − tổng trừ, 8)".

### 6. Điểm có phản ánh đúng không

9,4 "Tốt" với một lỗi Trung bình (trang lương 403 với HR theo chi nhánh) và một login CSRF mức Thấp: hợp lý cho một web
mà mọi đường giữa các phần đều khớp. Lo ngại chính là điểm tiêu chí 3 và 4 bị trần 8 bất kể kỹ hay sơ (đã xử lý ở câu
3). Giữ nguyên trọng số và mức trừ.

### 7. Phạm vi chưa kiểm

- Từng nút / từng form: thành phần lấy mẫu có chủ đích (câu 3), phần bắt buộc nêu rõ trong tiêu chí.
- Lời gọi ngoài `workforceApi` (nhân sự, tài khoản, RBAC…): đi qua các handler đã có whitelist, kiểm bằng mắt danh sách
  trong `lib/*.ts`; giữ là bước tay, ghi rõ trong gợi ý.
- Accessibility và hiệu năng render: chưa có tiêu chí; ghi rõ là **ngoài phạm vi có chủ đích** (web nội bộ, đồ án),
  để các lần sau không liệt kê lại như phần sót.

## Thay đổi

| # | Thay đổi | Lý do | Bằng chứng |
|---|---|---|---|
| 1 | Tiêu chí 4: khi DTO không có `@Size`, so với độ dài cột DB; nêu lỗi DB trả `409 CONFLICT` câu chung | Câu 1 | `WEB-003`: `V4__employment_and_salary.sql:41` |
| 2 | Tiêu chí 3: thêm cặp `requireCompanyWide` ↔ cờ `companyWide`; script `company-wide-gaps.mjs` | Câu 1, 2 | `WEB-001`; `grep -B4` đếm sai 23/17 |
| 3 | Tiêu chí 3 và 4: tách phần bắt buộc và phần lấy mẫu | Câu 3 | Cả hai bị trần 8 dù tiêu chí 4 đã so hết `@Size` |
| 4 | Cách chấm: kiểm một phần = min(10 − tổng trừ, 8) | Câu 5 | Tiêu chí 4: 9,5 → 8 phải giải thích riêng |
| 5 | Script `callers-vs-bff.mjs`, `query-params.mjs`; lệnh dò `Origin` bằng `curl` | Câu 4 | Viết script tạm trong lần chạy; `WEB-002` xác minh bằng `curl` |
| 6 | Sửa ghi chú `toISOString`, ghi chú E2E/`pnpm dev`; thêm lệnh heuristic cho tiêu chí 6, 9 kèm ngoại lệ | Câu 2, 4 | 3/3 dương tính giả; E2E 11/11 trên `pnpm dev` |
| 7 | "Thông tin lần chạy" ghi cách chạy web khi E2E | Câu 5 | — |
| 8 | Ghi rõ accessibility và hiệu năng render ngoài phạm vi | Câu 7 | Mục "Phạm vi chưa kiểm" của lần chạy |

Prompt dài thêm chủ yếu ở mục gợi ý lệnh; đổi lại ba thứ phải viết tay trong lần chạy (đối chiếu lời gọi, query param,
permission `companyWide`) thành lệnh chạy sẵn. Không đổi trọng số nên điểm lần sau so sánh trực tiếp được với 9,4, trừ
tiêu chí 3 và 4 vì phạm vi "kiểm đủ" của chúng đã được định nghĩa lại.

Ghi nhận cho lần chạy sau (không thuộc prompt): lệnh dò `Origin` cho thấy `logout` của cả hai portal trả 200 và xóa
cookie với `Origin` lạ, tức một trang lạ đăng xuất được người dùng; cùng gốc với `WEB-002`.
