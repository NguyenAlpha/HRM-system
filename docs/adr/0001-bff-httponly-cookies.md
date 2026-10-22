# 0001. Web giữ JWT trong cookie HttpOnly qua BFF

- **Trạng thái:** Đang áp dụng
- **Áp dụng từ:** 2026-09-23 (`81e3b70`, tách hai portal đăng nhập)

## Bối cảnh

API phát JWT access token (15 phút) và refresh token (30 ngày). Web cần giữ hai token này ở đâu đó để gọi API.
Cách phổ biến là lưu trong `localStorage` và gắn header ở trình duyệt, nhưng khi đó mọi đoạn JavaScript chạy trên
trang (kể cả script bị chèn qua lỗ hổng XSS) đều đọc được token. Refresh token 30 ngày bị lộ là mất quyền kiểm soát
phiên trong thời gian dài. Hệ thống cũng có hai nhóm người dùng tách biệt (nghiệp vụ và quản trị hệ thống).

## Quyết định

- Trình duyệt **không bao giờ cầm JWT**. Route Handlers của Next.js (`apps/web/app/api/**`) làm BFF: nhận request
  từ trình duyệt, đọc token từ cookie `HttpOnly; SameSite=Lax; Secure`, gắn `Authorization: Bearer` rồi gọi API ở
  phía server.
- Mỗi portal có bộ cookie và prefix BFF riêng (`/api/session` + `hrm_*`, `/api/admin-session` + `hrm_admin_*`). BFF
  kiểm tra account đúng portal.
- BFF chỉ chuyển tiếp các đường dẫn và tham số nằm trong whitelist, và phần lớn route ghi từ chối `Origin` lạ.

## Hệ quả

- JavaScript trên trang không đọc được token, nên XSS không lấy được token để dùng ở nơi khác.
- Mọi request đi thêm một bước qua server Next.js; thêm endpoint ở API thì phải thêm route BFF tương ứng.
- Refresh do BFF thực hiện; client phải gộp các lần refresh đồng thời vì API phát hiện refresh token bị dùng lại.
- Cookie `SameSite=Lax` cùng kiểm tra `Origin` giảm rủi ro CSRF cho các request ghi.
- Việc tách portal là chính sách của web: API không tự phân biệt request đến từ portal nào.

## Phương án đã cân nhắc

- **Lưu token trong `localStorage`, gọi API trực tiếp từ trình duyệt:** đơn giản hơn, không cần BFF, nhưng token lộ
  trước XSS và API phải mở CORS cho trình duyệt.
- **Session phía server (cookie session id, không dùng JWT):** API sẽ phải giữ trạng thái phiên; giữ JWT cho phép
  API stateless và vẫn dùng được Swagger hay client khác bằng Bearer token.

Chi tiết: [TOKEN_LIFECYCLE.md](../../api/docs/concepts/TOKEN_LIFECYCLE.md), [apps/web/docs/AUTH.md](../../apps/web/docs/AUTH.md).
