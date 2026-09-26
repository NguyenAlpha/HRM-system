# Web Authentication

Web sử dụng mô hình Backend for Frontend (BFF). Trình duyệt chỉ gọi Route Handlers của
Next.js; Next.js gọi Spring Boot Auth API và lưu token vào cookie `HttpOnly`.

## Tổng quan luồng

```text
Browser
   │  username/password hoặc request có cookie
   ▼
Next.js BFF
   │  Bearer access token / refresh token
   ▼
Spring Boot API
```

Token không được trả về JavaScript phía trình duyệt. Response của BFF chỉ chứa thông tin
account và thời điểm access token hết hạn.

## Phân tách portal

| Portal | Page routes | BFF prefix | Role rule |
|:-------|:------------|:-----------|:----------|
| `hrm` | `/login`, `/dashboard` | `/api/session` | Account không có `SYSTEM_ADMIN` |
| `admin` | `/admin/login`, `/admin` | `/api/admin-session` | Account có `SYSTEM_ADMIN` |

Nếu một account có nhiều role và một trong số đó là `SYSTEM_ADMIN`, account được xem là
admin và chỉ được dùng Admin Console.

Khi đăng nhập nhầm portal, BFF thu hồi refresh token vừa được API cấp và trả `403`:

| Error code | Trường hợp |
|:-----------|:-----------|
| `ADMIN_PORTAL_REQUIRED` | Admin đăng nhập tại HRM Workspace |
| `ADMIN_ACCESS_REQUIRED` | Người dùng thường đăng nhập tại Admin Console |

## Session endpoints

Hai prefix cung cấp cùng tập endpoint dưới đây. `{prefix}` là `/api/session` hoặc
`/api/admin-session`.

| Method | Endpoint | Chức năng |
|:------:|:---------|:----------|
| `POST` | `{prefix}/login` | Đăng nhập, kiểm tra portal và tạo hai cookie token |
| `POST` | `{prefix}/refresh` | Xoay vòng access token và refresh token |
| `GET` | `{prefix}/me` | Lấy account hiện tại bằng access token trong cookie |
| `POST` | `{prefix}/logout` | Thu hồi refresh token và xóa cookie của portal |
| `POST` | `{prefix}/change-password` | Đổi mật khẩu; nếu thành công sẽ xóa cookie của portal |

### Response envelope

BFF giữ cùng response envelope với API:

```json
{
  "success": true,
  "data": {
    "account": {
      "accountId": 2,
      "username": "employee01",
      "email": "employee01@hrm.local",
      "status": "ACTIVE",
      "employee": {
        "id": 1,
        "employeeCode": "EMP001",
        "fullName": "Nguyen Van An"
      },
      "roles": [
        {
          "code": "EMPLOYEE",
          "name": "Nhân viên",
          "scopeType": "SELF",
          "organizationUnitId": null,
          "organizationUnitName": null,
          "workLocationId": null,
          "workLocationName": null
        }
      ],
      "permissions": [
        {
          "code": "profile.self.read",
          "name": "Xem hồ sơ cá nhân",
          "module": "EMPLOYEE"
        }
      ]
    },
    "expiresAt": "2026-09-24T03:15:00.000Z"
  },
  "error": null
}
```

Response lỗi:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Phiên đăng nhập không tồn tại",
    "field": null
  }
}
```

`accessToken` và `refreshToken` từ Spring Boot không xuất hiện trong body BFF trả về browser.
Giao diện hiển thị `name`; các kiểm tra điều hướng hoặc phân quyền phía client vẫn dùng `code`.

### Login

Request:

```json
{
  "usernameOrEmail": "employee01",
  "password": "Employee@123"
}
```

Nếu thông tin hợp lệ và account đúng portal, BFF tạo cookie rồi trả `SessionData`. Lỗi xác
thực của Spring Boot được chuyển tiếp theo status và envelope gốc.

### Refresh

BFF đọc refresh token từ cookie tương ứng, gọi `POST /api/auth/refresh`, sau đó ghi đè cả
access cookie và refresh cookie. Client gom các yêu cầu refresh đồng thời trong cùng portal
về một Promise để tránh tự reuse refresh token do nhiều request chạy song song.

Nếu refresh thất bại, cookie của portal đó bị xóa. Hai portal dùng cookie khác nhau nên thao
tác này không xóa session của portal còn lại.

### Me và tự refresh

`GET {prefix}/me` dùng access token trong cookie để gọi `GET /api/auth/me`. Client tự gọi
refresh và thử lại request một lần khi BFF trả lỗi có code `UNAUTHORIZED`.

`proxy.ts` chỉ kiểm tra cookie có tồn tại để redirect sớm; nó không xác minh chữ ký hoặc hạn
token. Việc xác thực thật diễn ra khi dashboard gọi endpoint `me` qua BFF.

### Logout

Nếu có đủ hai token, BFF gọi API để revoke refresh token. Cookie của portal luôn được xóa ở
browser, kể cả khi không có đủ token để gọi API.

Access token là JWT stateless nên API không thể thu hồi ngay token đã phát; xóa cookie giúp
browser ngừng sử dụng token đó.

### Change password

BFF gửi access token qua header `Authorization` tới API. Khi đổi mật khẩu thành công:

- API thu hồi toàn bộ refresh token của account.
- BFF xóa cookie của portal hiện tại.
- UI chuyển người dùng về trang đăng nhập tương ứng.

## Cookie

| Portal | Access cookie | Refresh cookie |
|:-------|:--------------|:---------------|
| HRM | `hrm_access_token` | `hrm_refresh_token` |
| Admin | `hrm_admin_access_token` | `hrm_admin_refresh_token` |

Thuộc tính chung:

| Thuộc tính | Giá trị |
|:-----------|:--------|
| `HttpOnly` | `true` |
| `SameSite` | `Lax` |
| `Path` | `/` |
| `Secure` | Theo `AUTH_COOKIE_SECURE`; mặc định bật khi `NODE_ENV=production` |

Access cookie dùng `expiresIn` do API trả về. Refresh cookie dùng
`AUTH_REFRESH_COOKIE_MAX_AGE_SECONDS`, mặc định 30 ngày.

## Error do BFF tạo

Ngoài error được chuyển tiếp từ Spring Boot, BFF có thể trả:

| HTTP | Code | Nguyên nhân |
|:----:|:-----|:------------|
| `400` | `VALIDATION_ERROR` | Request body không phải JSON hợp lệ |
| `401` | `UNAUTHORIZED` | Thiếu cookie cần thiết cho thao tác |
| `403` | `ADMIN_PORTAL_REQUIRED` | Admin dùng HRM portal |
| `403` | `ADMIN_ACCESS_REQUIRED` | Account thường dùng Admin portal |
| `502` | `API_UNAVAILABLE` | Next.js không kết nối được tới Spring Boot API |
| Theo upstream | `INVALID_API_RESPONSE` | API trả response không đọc được theo envelope JSON |

## Quy tắc khi mở rộng

- Browser gọi BFF session endpoint, không tự đọc hoặc gắn JWT.
- Mỗi route protected phải kiểm tra authorization ở API; redirect của `proxy.ts` không phải
  cơ chế phân quyền.
- Kiểm tra role/permission ở UI chỉ dùng để điều chỉnh trải nghiệm, không thay thế kiểm tra
  quyền phía Spring Boot.
- Không dùng chung cookie giữa HRM và Admin portal.
- Khi thêm cơ chế gọi API nghiệp vụ, BFF phải lấy access token từ cookie ở server và gắn
  header `Authorization`; không gửi token về Client Component.
