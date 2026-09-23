# API Reference — Auth

Xác thực tài khoản HRM bằng access token JWT và refresh token. HRM không hỗ trợ đăng ký công khai; tài khoản được tạo bởi quản trị viên.

---

## Endpoint access

| Endpoint | Public | Yêu cầu Bearer token |
|:---------|:------:|:--------------------:|
| `POST /api/auth/login` | ✅ | ❌ |
| `POST /api/auth/refresh` | ✅ | ❌ |
| `POST /api/auth/logout` | ❌ | ✅ |
| `GET /api/auth/me` | ❌ | ✅ |
| `POST /api/auth/change-password` | ❌ | ✅ |

---

## Response envelope

Mọi API đều trả về cùng một cấu trúc bọc ngoài:

```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

Khi lỗi:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ERROR_CODE",
    "message": "Mô tả lỗi",
    "field": "tên field nếu là lỗi validation, ngược lại null"
  }
}
```

Các endpoint cần xác thực nhận access token qua header:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Thiếu token, token hết hạn hoặc chữ ký không hợp lệ trả về:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Authentication required. Provide a valid Bearer token",
    "field": null
  }
}
```

---

## POST `/api/auth/login`

Đăng nhập bằng username hoặc email. Endpoint public, không cần JWT.

### Request

```json
{
  "usernameOrEmail": "admin",
  "password": "Admin@123"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `usernameOrEmail` | string | ✅ | Không rỗng, tối đa 100 ký tự |
| `password` | string | ✅ | Không rỗng, tối đa 100 ký tự |

Email được chuẩn hóa về chữ thường. Username giữ nguyên chữ hoa/thường.

### Response `200 OK`

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "IuZp6QY...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "account": {
      "id": 1,
      "employeeId": null,
      "username": "admin",
      "email": "admin@hrm.local",
      "status": "ACTIVE",
      "roles": ["SYSTEM_ADMIN"],
      "permissions": ["rbac.manage"]
    }
  },
  "error": null
}
```

| Field | Ý nghĩa |
|:------|:--------|
| `accessToken` | JWT gửi trong header `Authorization`; mặc định hết hạn sau 900 giây |
| `refreshToken` | Token opaque dùng một lần để lấy token mới; mặc định hết hạn sau 30 ngày |
| `tokenType` | Luôn là `Bearer` |
| `expiresIn` | Thời hạn access token tính bằng giây |
| `account.roles` | Các role đang có hiệu lực tại thời điểm phát JWT |
| `account.permissions` | Hợp quyền role và permission override đang có hiệu lực |

### JWT payload

```json
{
  "iss": "https://hrm.local",
  "sub": "admin",
  "accountId": 1,
  "roles": ["SYSTEM_ADMIN"],
  "permissions": ["rbac.manage"],
  "iat": 178...,
  "exp": 178...
}
```

Spring Security chuyển role thành authority có prefix `ROLE_`; ví dụ `SYSTEM_ADMIN` thành `ROLE_SYSTEM_ADMIN`. Permission giữ nguyên, ví dụ `rbac.manage`.

> Role có scope `SELF`, `ORG_UNIT` hoặc `LOCATION` vẫn cần được kiểm tra scope tại authorization/service layer. Việc có permission trong JWT không tự động cho phép truy cập dữ liệu ngoài scope được gán.

### Theo dõi đăng nhập sai

- Mỗi lần sai mật khẩu tăng `failedLoginCount` để phục vụ audit.
- Hệ thống hiện chưa tự động khóa account theo số lần đăng nhập sai.
- Đăng nhập thành công đặt bộ đếm về 0 và cập nhật `lastLoginAt`.
- Account ở trạng thái `LOCKED` do quản trị viên thiết lập vẫn không thể đăng nhập.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Request body hoặc field không hợp lệ |
| 401 | `INVALID_CREDENTIALS` | Không tìm thấy account hoặc sai mật khẩu |
| 401 | `ACCOUNT_PENDING` | Account chưa được kích hoạt |
| 401 | `ACCOUNT_LOCKED` | Account đang bị quản trị viên khóa |
| 401 | `ACCOUNT_DISABLED` | Account đã bị vô hiệu hóa |

---

## POST `/api/auth/refresh`

Đổi refresh token hiện tại lấy một access token và refresh token mới. Endpoint public, không cần access token.

### Request

```json
{
  "refreshToken": "IuZp6QY..."
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `refreshToken` | string | ✅ | Không rỗng, tối đa 200 ký tự |

### Response `200 OK`

Cấu trúc response giống login, nhưng cả `accessToken` và `refreshToken` đều là giá trị mới.

```json
{
  "success": true,
  "data": {
    "accessToken": "new-access-token...",
    "refreshToken": "new-refresh-token...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "account": {
      "id": 1,
      "employeeId": null,
      "username": "admin",
      "email": "admin@hrm.local",
      "status": "ACTIVE",
      "roles": ["SYSTEM_ADMIN"],
      "permissions": ["rbac.manage"]
    }
  },
  "error": null
}
```

### Rotation và reuse detection

- Refresh token cũ bị revoke ngay khi sử dụng thành công.
- Client bắt buộc thay token cũ bằng `refreshToken` mới trong response.
- Nếu token đã dùng/revoke được gửi lại, hệ thống coi là nguy cơ token bị đánh cắp và revoke toàn bộ refresh token của account.
- Database chỉ lưu SHA-256 hash của refresh token, không lưu credential dạng rõ.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu refresh token hoặc body không hợp lệ |
| 401 | `REFRESH_TOKEN_INVALID` | Token không tồn tại, đã dùng, đã revoke hoặc account không còn active |
| 401 | `REFRESH_TOKEN_EXPIRED` | Refresh token đã hết hạn |

---

## POST `/api/auth/logout`

Thu hồi refresh token của phiên hiện tại. Endpoint yêu cầu access token hợp lệ.

### Headers

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### Request

```json
{
  "refreshToken": "IuZp6QY..."
}
```

Refresh token chỉ được revoke nếu thuộc account trong access token. Gửi token không tồn tại, đã revoke hoặc thuộc account khác vẫn trả thành công để logout có tính idempotent.

### Response `200 OK`

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

> Access token là JWT stateless nên vẫn có hiệu lực đến `exp`; client phải xóa access token ngay sau khi logout.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu refresh token hoặc body không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |

---

## GET `/api/auth/me`

Lấy thông tin account, role và permission hiện hành. Endpoint yêu cầu access token hợp lệ.

### Response `200 OK`

```json
{
  "success": true,
  "data": {
    "id": 1,
    "employeeId": null,
    "username": "admin",
    "email": "admin@hrm.local",
    "status": "ACTIVE",
    "roles": ["SYSTEM_ADMIN"],
    "permissions": ["rbac.manage"]
  },
  "error": null
}
```

`roles` và `permissions` được đọc lại từ database nên phản ánh phân quyền hiện tại, có thể mới hơn claims trong access token.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 404 | `RESOURCE_NOT_FOUND` | Account trong JWT không còn tồn tại |

---

## POST `/api/auth/change-password`

Đổi mật khẩu của account đang đăng nhập. Endpoint yêu cầu access token hợp lệ.

### Request

```json
{
  "currentPassword": "Admin@123",
  "newPassword": "NewPassword@123"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `currentPassword` | string | ✅ | Không rỗng, tối đa 100 ký tự |
| `newPassword` | string | ✅ | 8–100 ký tự và khác mật khẩu hiện tại |

### Response `200 OK`

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

Sau khi đổi mật khẩu:

- Toàn bộ refresh token của account bị revoke.
- Access token hiện tại vẫn có hiệu lực đến `exp`.
- Client nên xóa token hiện tại và chuyển người dùng về màn hình đăng nhập.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Field không hợp lệ hoặc mật khẩu mới giống mật khẩu hiện tại |
| 401 | `INVALID_CREDENTIALS` | Mật khẩu hiện tại không đúng |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |

---

## Cấu hình

| Biến môi trường | Mặc định | Ý nghĩa |
|:----------------|:---------|:--------|
| `JWT_SECRET` | Không có | Secret Base64, tối thiểu 32 bytes sau decode; bắt buộc để ứng dụng khởi động |
| `JWT_ISSUER` | `https://hrm.local` | Giá trị claim `iss` và issuer được decoder chấp nhận |
| `JWT_ACCESS_TOKEN_EXPIRATION_SECONDS` | `900` | Thời hạn access token theo giây |
| `JWT_REFRESH_TOKEN_EXPIRATION_DAYS` | `30` | Thời hạn refresh token theo ngày |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Danh sách origin frontend, phân tách bằng dấu phẩy |

Tạo secret phát triển bằng OpenSSL:

```bash
openssl rand -base64 32
```

PowerShell:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
```

Không commit giá trị `JWT_SECRET` vào repository.

---

## Lưu token phía frontend

- Không lưu refresh token trong `localStorage` nếu có thể tránh; ưu tiên HttpOnly cookie hoặc lưu phía Next.js BFF/server.
- Access token chỉ nên giữ trong memory hoặc session ngắn.
- Khi refresh thành công phải ghi đè refresh token cũ ngay lập tức.
- Khi nhận `REFRESH_TOKEN_INVALID`, xóa toàn bộ trạng thái đăng nhập và yêu cầu người dùng đăng nhập lại.
