# Prompt đánh giá web HRM

Prompt dùng lại để đánh giá toàn bộ `apps/web` (HRM Workspace và Admin Console) theo một bộ tiêu chí cố định. Mỗi lần
chạy lưu kết quả thành một file mới trong thư mục này, tên `WEB_REVIEW_<yyyy-MM-dd>.md`; không sửa file của lần chạy
trước.

Cách dùng: dán phần **Prompt** bên dưới cho agent (Claude Code…), mở ở thư mục gốc repo. Chạy xong thì dùng
`/review-prompt-improve apps/web/docs/prompt/WEB_REVIEW_PROMPT.md` trong cùng phiên để cải tiến prompt.

---

## Prompt

Đánh giá một vòng web trong `apps/web/` của dự án HRM theo bộ tiêu chí dưới đây, gồm cả HRM Workspace (`/login`,
`app/(hrm)/`) và Admin Console (`/admin/*`). Đọc trước:

- `AGENTS.md` và `apps/web/AGENTS.md`. Đây là Next.js 16: trước khi kết luận về một API của Next (`proxy.ts`, Route
  Handler, `cookies()`, layout…), đọc hướng dẫn tương ứng trong `apps/web/node_modules/next/dist/docs/`, đừng dựa vào
  hiểu biết về bản cũ.
- `apps/web/docs/`: `ARCHITECTURE.md` (hai nửa giao diện/BFF), `AUTH.md` (cookie, refresh, portal), `RBAC.md`,
  `CACHING.md` (khóa cache và lúc làm mới), `DEVELOPMENT.md` (lệnh, E2E).
- Phía API khi cần đối chiếu: `api/docs/api/*.md`, `api/docs/concepts/DATA_SCOPE.md`, `api/docs/api/RBAC.md` (mục
  "Permission toàn công ty và scope của role").

Chỉ đánh giá và ghi kết quả, **không sửa code** trong lần chạy này. Accessibility và hiệu năng render nằm ngoài phạm
vi có chủ đích (web nội bộ, đồ án); không cần liệt kê chúng ở mục "Phạm vi chưa kiểm".

### Nguyên tắc

- Mỗi kết luận phải có bằng chứng: đường dẫn `file:dòng`, lệnh đã chạy hoặc kết quả. Không kết luận theo tên hàm.
- Ưu tiên lỗi **giữa các phần**, không chỉ trong từng component:
  - whitelist route BFF (`lib/auth/server.ts`) ↔ endpoint và method trong `openapi.json`;
  - `queryKeys` của mỗi handler BFF ↔ query param mà trang gửi ↔ `@RequestParam` của API;
  - mã quyền dùng để ẩn/hiện ↔ `PermissionSeeder` ↔ `@PreAuthorize`/`requireCompanyWide` của endpoint trang gọi;
  - permission API kiểm bằng `requireCompanyWide` ↔ cờ `companyWide` trong `PermissionSeeder` (permission không có cờ
    thì role gán được phạm vi hẹp, nên web không được hiện thao tác đó chỉ dựa vào mã quyền);
  - giới hạn form (`zod .max`, `maxLength`, bắt buộc) ↔ `@Size`/`@NotBlank` của DTO, hoặc ↔ độ dài cột DB khi DTO không
    khai báo;
  - `ErrorCode` của API ↔ `ERROR_MESSAGES` trong `lib/api-helpers.ts`;
  - enum API ↔ bảng nhãn tiếng Việt (`*_LABELS`);
  - thư mục route trong `app/(hrm)` ↔ `HRM_PAGES` và `config.matcher` trong `proxy.ts`;
  - bảng khóa cache trong `docs/CACHING.md` ↔ `useQuery`/`invalidateQueries` trong code;
  - `openapi.json` ↔ `lib/api-schema.ts` (`pnpm check:api`).
- Quyền ở UI chỉ để ẩn/hiện; API mới là nơi quyết định. Lỗi ở tiêu chí 3 là lệch giữa hai bên (nút hiện mà API trả
  403, hoặc ẩn mất chức năng người dùng có quyền), không phải "UI thiếu kiểm tra quyền".
- Phần nào không kiểm được thì ghi rõ "chưa kiểm" và lý do, không bỏ qua im lặng.
- Đọc file kết quả gần nhất `WEB_REVIEW_*.md` trong `apps/web/docs/prompt/` (nếu có) để so sánh: lỗi nào đã sửa, lỗi
  nào còn, lỗi nào mới. Giữ nguyên mã lỗi `WEB-<số>` của lỗi cũ; lỗi mới lấy số tiếp theo.

### Bộ tiêu chí

| # | Tiêu chí | Trọng số | Cần kiểm |
|---|---|---|---|
| 1 | Phiên đăng nhập và BFF | 20% | Token chỉ nằm trong cookie `HttpOnly` (`Secure` theo môi trường, `SameSite=Lax`), không xuất hiện trong body trả về trình duyệt, `localStorage`, URL hay log; mọi Route Handler chặn method lạ (405) và request ghi có `Origin` khác (403); refresh gộp một lần cho các request đồng thời và chỉ thử lại một lần; refresh thất bại thì xóa cookie (trừ 429); hai portal không dùng chung cookie, đăng nhập nhầm portal bị thu hồi token; đăng xuất/đổi mật khẩu xóa cookie; route handler không cần phiên (`account-activations`, `branding`) không gắn Bearer |
| 2 | Whitelist BFF khớp API | 12% | Mỗi route + method trong các danh sách `*_ROUTES` có endpoint thật; mỗi lời gọi trong `lib/*.ts` và `components/` đi qua một route có trong whitelist; mỗi query param trang gửi có trong `queryKeys` của handler (nếu không, bộ lọc bị bỏ âm thầm); các handler một endpoint (reference, cấp tài khoản, Company Owner, thống kê hệ thống, rate limit) trỏ đúng đường dẫn API |
| 3 | Ẩn/hiện theo quyền khớp API | 12% | **Bắt buộc:** mã quyền web dùng đều có trong `PermissionSeeder`; mọi mục sidebar (cả hai portal) khớp quyền của lời gọi API đầu tiên của trang; với mọi permission mà `company-wide-gaps.mjs` liệt kê, web có thao tác nào hiện chỉ theo mã quyền không; trang Admin Console và HRM Workspace không lộ chức năng của portal kia. **Lấy mẫu** (ghi trang đã xem): nút thao tác trong từng trang so với quyền của endpoint nút gọi. Làm đủ phần bắt buộc thì tiêu chí không bị coi là kiểm một phần |
| 4 | Form khớp ràng buộc API | 10% | **Bắt buộc:** mọi trường có `@Size` trong DTO có `zod .max`/`maxLength` bằng nó ở form tương ứng; mọi cột văn bản `VARCHAR(n)` mà DTO không có `@Size` có giới hạn ở cả DTO và form (vượt độ dài cột thì `GlobalExceptionHandler.handleDataIntegrity` trả `409 CONFLICT` câu chung, người dùng không biết sửa gì); nút gửi bị khóa khi đang gửi. **Lấy mẫu** (ghi form đã xem): trường bắt buộc khớp `@NotNull`/`@NotBlank`, khoảng ngày (từ ≤ đến) và giá trị số kiểm như API, lỗi theo `field` của API hiện đúng ô. Làm đủ phần bắt buộc thì tiêu chí không bị coi là kiểm một phần |
| 5 | Đồng bộ type và danh mục với API | 8% | `pnpm check:api` sạch; không có type tự viết tay trùng với type trong `lib/api-schema.ts`; mọi `ErrorCode` có câu tiếng Việt; bảng nhãn của enum API khai báo `Record<Enum, string>` để TypeScript bắt thiếu nhãn (bảng `Record<string, string>` cho enum là lỗi) |
| 6 | Xử lý lỗi, trạng thái tải, rỗng và hết phiên | 8% | Mọi lời gọi API có nhánh lỗi hiện câu tiếng Việt qua `apiErrorMessage`/`friendlyErrorMessage`, không hiện `message` tiếng Anh hay mã lỗi; hết phiên (`isPortalSessionExpired`) đưa về trang đăng nhập đúng portal; bảng có trạng thái đang tải và trạng thái rỗng; thao tác thành công có phản hồi (toast/đóng dialog) và tải lại dữ liệu liên quan |
| 7 | Ngày giờ và số | 8% | "Hôm nay" và tháng hiện tại tính theo `Asia/Ho_Chi_Minh` (không lấy ngày từ `toISOString()` của giờ địa phương); hiển thị ngày qua `lib/format-date.ts` (`dd/MM/yyyy`, `dd/MM/yyyy HH:mm`), không in chuỗi ISO; ngày gửi lên API đúng dạng `yyyy-MM-dd`; tiền và số theo `vi-VN` |
| 8 | Cache và làm mới dữ liệu | 7% | Khóa cache trong code khớp bảng ở `docs/CACHING.md` (có `portal` khi dữ liệu khác nhau giữa hai portal); mỗi thao tác ghi làm hết hạn đúng khóa liên quan; `PortalShell` xóa cache khi rời layout để người đăng nhập sau không thấy dữ liệu của người trước |
| 9 | Quy ước giao diện của dự án | 5% | Bảng danh sách theo chuẩn bảng nhân sự, thao tác dòng chỉ qua menu "…" `RowActionsCell`; popup chỉ là `Dialog` giữa màn hình, không có Sheet/drawer; chỉ dùng token màu shadcn; ngày theo tiêu chí 7 |
| 10 | Kiểm thử và build | 7% | `pnpm typecheck`, `pnpm lint`, `pnpm build` sạch; `pnpm test:e2e` chạy và ghi số pass/fail (bắt buộc; không chạy được thì tiêu chí này là kiểm một phần); luồng rủi ro (đăng nhập, hết phiên, nhầm portal, duyệt đơn) có E2E hay chưa |
| 11 | Tài liệu khớp code | 3% | `apps/web/README.md`, `docs/*.md` khớp code: bảng route, bảng khóa cache, danh sách endpoint session, lệnh kiểm tra |

### Cách chấm điểm

Điểm suy ra từ danh sách lỗi theo quy tắc cố định, không chấm theo cảm nhận: cùng một danh sách lỗi thì luôn ra cùng
một điểm.

1. **Gắn lỗi vào tiêu chí:** mỗi lỗi thuộc đúng **một** tiêu chí mà nó vi phạm trực tiếp nhất (trường "thuộc tiêu
   chí"; khác với "tìm ra nhờ"). Không trừ một lỗi ở hai tiêu chí.
2. **Điểm tiêu chí** bắt đầu từ 10, trừ theo mỗi lỗi **chưa sửa**: Cao −4, Trung bình −2, Thấp −0,5; thấp nhất 0.
3. **Kiểm một phần:** tiêu chí chỉ kiểm được một phần thì điểm = min(10 − tổng trừ, 8) và ghi rõ phần chưa kiểm.
   **Chưa kiểm:** không chấm, bỏ khỏi điểm tổng.
4. **Điểm tổng** = trung bình có trọng số của các tiêu chí đã chấm (chia cho tổng trọng số của chúng), làm tròn 1 chữ
   số thập phân.
5. **Trần:** còn lỗi Cao chưa sửa thì điểm tổng tối đa 7,0; còn từ hai lỗi Cao thì tối đa 5,0.
6. **Xếp loại:** 9,0–10 Tốt · 7,0–8,9 Khá · 5,0–6,9 Trung bình · dưới 5,0 Yếu.

Trọng số là lựa chọn của project (phần nào sai thì đắt nhất: ở đây là phiên đăng nhập và BFF); đổi trọng số thì ghi
vào "Lịch sử thay đổi", vì điểm các lần chạy chỉ so sánh được khi cùng trọng số.

### Gợi ý lệnh kiểm

Chạy trong `apps/web/` trừ khi ghi khác.

- `pnpm typecheck` (gồm `check:api`: báo lỗi nếu `lib/api-schema.ts` lệch `openapi.json`), `pnpm lint`, `pnpm build`.
- E2E: cần API ở `http://localhost:8080` với DB đã seed và web đang chạy (xem `docs/DEVELOPMENT.md`), rồi
  `pnpm test:e2e` (lần 2026-11-05: 11 test, khoảng 1 phút trên `pnpm dev`). Tài khoản test ở `e2e/auth.ts`.
  `account-status.spec.ts` vô hiệu hóa rồi kích hoạt lại `employee`. Nếu dev server trả 404 cho route BFF dạng
  `[...path]` (từng gặp), chạy bằng `pnpm build && pnpm start` và ghi lại.
- Whitelist BFF ↔ API: `node docs/prompt/scripts/bff-vs-openapi.mjs .` (in số cặp route + method đã kiểm và các route
  không có endpoint). Script chỉ đọc các danh sách có trong biến `PREFIX`: đối chiếu với
  `grep -n "_ROUTES: Route\[\]" lib/auth/server.ts`; danh sách mới chưa có trong `PREFIX` sẽ bị bỏ qua mà không báo.
  Script không kiểm các handler một endpoint và không kiểm chiều ngược (endpoint API web chưa mở): hai phần này kiểm
  tay.
- Lời gọi trang ↔ whitelist: `node docs/prompt/scripts/callers-vs-bff.mjs .` đối chiếu mọi `workforceApi(...)` với
  `WORKFORCE_ROUTES`; dòng `X` là lời gọi không có route, dòng `?` là đường dẫn ghép từ biến (`${base}/…`) hoặc chọn
  hành động trong `${…}`, kiểm tay (lần 2026-11-05: 43 khớp, 9 kiểm tay, 0 lỗi). Lời gọi qua `authorizedRequest` ở
  `lib/*.ts` (nhân sự, tài khoản, RBAC, đề xuất role…) chưa có script: xem danh sách đường dẫn bằng
  `grep -rnE "authorizedRequest|employeeRequest" lib`.
- Query param ↔ `queryKeys`: `node docs/prompt/scripts/query-params.mjs .` in query param API nhận theo từng nhóm
  handler, cùng các khóa BFF bỏ mất (`dropped by BFF`: bộ lọc không có tác dụng) hoặc thừa (`no endpoint takes`). Hai
  route timeline của Admin Console tự lọc param trong `app/api/admin-session/*/timeline/route.ts`: xem tay.
- `Origin` ở các handler BFF (web đang chạy ở cổng 3000): dùng tài khoản không tồn tại để không tạo phiên thật. 403 là
  handler có kiểm `Origin`; 401/200 là không kiểm.

  ```bash
  for p in session admin-session; do for r in login refresh logout change-password; do
    printf "%-14s %-16s " $p $r
    curl -s -o /dev/null -w "%{http_code}\n" -X POST "http://localhost:3000/api/$p/$r" -H "Origin: https://evil.example" \
      -H "Content-Type: text/plain" --data '{"usernameOrEmail":"origin-probe","password":"x"}'
  done; done
  curl -s -o /dev/null -w "forwarded write: %{http_code}\n" -X POST http://localhost:3000/api/session/workforce/leave-requests \
    -H "Origin: https://evil.example" --data '{}'   # phải là 403
  ```

- Permission `requireCompanyWide` không đánh dấu `companyWide` (chạy ở gốc repo):
  `node apps/web/docs/prompt/scripts/company-wide-gaps.mjs`. Lần 2026-11-05: `attendance.manage`,
  `compensation.manage`, `report.payroll.read`. Đừng lấy danh sách `companyWide` bằng `grep -B… companyWideOnly`: khối
  khai báo dài ngắn khác nhau nên lấy nhầm mã của dòng bên cạnh (đếm ra 23 thay vì 17).
- Mã quyền web ↔ danh mục (chạy ở gốc repo):

  ```bash
  grep -rhoE "\"[a-z_]+(\.[a-z_]+)+\"" apps/web/components apps/web/lib apps/web/app --include=*.ts --include=*.tsx \
    --exclude=api-schema.ts | tr -d '"' | sort -u > /tmp/web_codes.txt
  grep -oE "\"[a-z_]+(\.[a-z_]+)+\"," api/src/main/java/com/htttdn/hrm/config/seed/PermissionSeeder.java \
    | tr -d '",' | sort -u > /tmp/catalogue.txt
  comm -23 /tmp/web_codes.txt /tmp/catalogue.txt   # web dùng nhưng danh mục không có: lỗi
  comm -13 /tmp/web_codes.txt /tmp/catalogue.txt   # danh mục có nhưng web không nhắc: chỉ để tham khảo
  ```

  Lần tạo prompt: chiều đầu rỗng; chiều sau chỉ có `request.self.cancel` (nút hủy đơn của chính mình không cần kiểm
  quyền ở UI).
- `ErrorCode` ↔ `ERROR_MESSAGES` (chạy ở gốc repo):

  ```bash
  grep -oE "^\s+[A-Z][A-Z_]+[,;]?\s*(//.*)?$" api/src/main/java/com/htttdn/hrm/dto/response/common/ErrorCode.java \
    | grep -oE "[A-Z][A-Z_]+" | sort -u > /tmp/api_codes.txt
  sed -n '/const ERROR_MESSAGES/,/^}/p' apps/web/lib/api-helpers.ts | grep -oE "^\s+[A-Z][A-Z_]+:" | tr -d ' :' \
    | sort -u > /tmp/web_messages.txt
  comm -3 /tmp/api_codes.txt /tmp/web_messages.txt
  ```

  Lấy cả phần tử cuối enum (không có dấu phẩy); regex chỉ bắt `[,(]` sẽ bỏ sót nó. Lần tạo prompt: 94 mã, khớp hết.
- Trang ↔ `proxy.ts`: `ls "app/(hrm)"` so với `HRM_PAGES` và `config.matcher` (lần tạo prompt: 16 trang, khớp).
- Bảng nhãn không được TypeScript bảo vệ: `grep -rnE "LABELS[A-Z_]*: Record<string, string>" lib components`.
  `FIELD_LABELS` (tên trường) và nhãn không ứng với enum API thì bỏ qua.
- Ngày theo UTC: `grep -rnE "toISOString\(\)\.(slice|substring|split)" components lib app`. Dương tính giả: ngày tạo
  bằng `Date.UTC(...)` (vào và ra cùng UTC) hoặc `new Date(năm, tháng, 15)` (giữa tháng) không lệch; ngày giờ địa
  phương đầu/cuối tháng hoặc `new Date()` lúc 0h–7h giờ Việt Nam thì lệch. Lần 2026-11-05: 3 kết quả, cả 3 là dương
  tính giả.
- Cột văn bản có giới hạn (tiêu chí 4, chạy ở gốc repo):
  `grep -nE "^\s*\w*(reason|note|description|comment|address)\w*\s+VARCHAR\(" api/src/main/resources/db/migration/V*.sql`,
  rồi tìm DTO và ô nhập tương ứng. Cột `TEXT` không giới hạn, bỏ qua.
- Bảng thiếu trạng thái tải hoặc rỗng (tiêu chí 6):

  ```bash
  for f in $(grep -rl "<TableBody" components | grep -v components/ui/); do
    echo "$(grep -c 'Skeleton\|Đang tải\|Loader2' $f) $(grep -cE 'Chưa có|Không có|Không tìm thấy|colSpan' $f) $f"
  done | awk '$1==0 || $2==0'
  ```

- Nút đặt thẳng trong dòng bảng (tiêu chí 9): với mỗi file có `<TableBody>`, đếm `<Button` giữa `<TableBody>` và
  `</TableBody>` (`awk '/<TableBody/,/<\/TableBody>/' file | grep -c "<Button"`). Ngoại lệ đã biết: nút "Cấp tài khoản"
  trong `employee-manager.tsx` (chính là bảng mẫu), nút xóa bậc trong form sửa bậc phụ cấp
  (`allowance-policy-page.tsx`, không phải bảng danh sách).
- Màu Tailwind trực tiếp thay token shadcn (tiêu chí 9):
  `grep -rnE "bg-(red|green|blue|yellow|orange|gray|slate|zinc)-[0-9]+|text-(red|green|blue|yellow|orange)-[0-9]+" components --include=*.tsx | grep -v components/ui/`.
  Màu trạng thái chấm công trong `attendance-section.tsx` được giữ có chủ ý.
- Lưu trữ phía trình duyệt: `grep -rnE "localStorage|sessionStorage" components lib app` (lần tạo prompt chỉ có trạng
  thái thu gọn sidebar).
- Sheet/drawer: `grep -rnE "from \"@/components/ui/sheet\"|<Sheet" components`. Đừng grep chữ `Sheet` trần: khớp nhầm
  `sheetRevision`, `FileSpreadsheet`.
- Ẩn/hiện theo phạm vi: `grep -rnE "scopeType === \"COMPANY\"" components lib` (lần tạo prompt: không có chỗ nào; UI
  chỉ xét mã quyền), rồi đối chiếu với các method API gọi `requireCompanyWide`
  (`grep -rn "requireCompanyWide" api/src/main/java/com/htttdn/hrm/service`).
- Sidebar: điều kiện hiện từng mục ở `components/portal/portal-sidebar.tsx`; với mỗi mục, mở trang, tìm lời gọi API
  đầu tiên rồi so với `@PreAuthorize` của method service tương ứng.

### Định dạng file kết quả

Lưu thành `apps/web/docs/prompt/WEB_REVIEW_<yyyy-MM-dd>.md` (ngày chạy), gồm:

1. **Thông tin lần chạy**: ngày, commit (`git log --oneline -1`), kết quả `typecheck`/`lint`/`build`, số test E2E và
   kết quả, web chạy bằng `pnpm dev` hay `pnpm start` khi chạy E2E, người/agent chạy.
2. **Điểm**: điểm tổng /10, xếp loại, trần áp dụng (nếu có), và bảng mỗi tiêu chí một dòng: trọng số, điểm /10
   (hoặc "Chưa kiểm"), các lỗi đã trừ (mã và mức), bằng chứng ngắn.
3. **Danh sách lỗi**: mỗi lỗi có mã ổn định `WEB-<số>`, mức độ (Cao/Trung bình/Thấp), **thuộc tiêu chí** (đúng một),
   vị trí `file:dòng`, mô tả, tình huống gây lỗi cụ thể, đề xuất sửa, **tìm ra nhờ** (số tiêu chí, hoặc "ngoài tiêu
   chí"), trạng thái (`Chưa sửa` / `Đã sửa <commit>`).
4. **So sánh với lần trước**: điểm tổng và điểm từng tiêu chí trước → sau; lỗi đã sửa (kèm commit), lỗi còn tồn,
   lỗi mới.
5. **Phạm vi chưa kiểm**.

---

## Lịch sử thay đổi

| Ngày | Thay đổi | Review |
|---|---|---|
| 2026-11-05 | Bản đầu tiên, sinh bằng /review-prompt-create | — |
| 2026-11-05 | Tiêu chí 3 và 4 tách phần bắt buộc / lấy mẫu; tiêu chí 4 so cả độ dài cột DB khi DTO không có `@Size`; cặp `requireCompanyWide` ↔ `companyWide`; kiểm một phần = min(10 − trừ, 8); thêm script `callers-vs-bff.mjs`, `query-params.mjs`, `company-wide-gaps.mjs` và lệnh dò `Origin`; sửa ghi chú `toISOString` và E2E; thêm lệnh heuristic cho tiêu chí 6, 9; accessibility và hiệu năng ngoài phạm vi. Điểm tiêu chí 3, 4 của lần 2026-11-05 không so trực tiếp được với lần sau. | [PROMPT_REVIEW_WEB_REVIEW_2026-11-05](./PROMPT_REVIEW_WEB_REVIEW_2026-11-05.md) |
