# API Reference — RBAC

Quản lý role, permission và quan hệ permission của role. Việc gán role cho account được mô tả riêng tại [Account Role Assignments](./ACCOUNT_ROLE_ASSIGNMENTS.md); API RBAC không trực tiếp thay đổi assignment của account.

---

## Endpoint access

| Endpoint | Yêu cầu Bearer token | Role được phép mặc định |
|:---------|:--------------------:|:-----------------------:|
| `POST /api/roles` | ✅ | `SYSTEM_ADMIN` |
| `GET /api/roles` | ✅ | `SYSTEM_ADMIN` |
| `GET /api/roles/{id}` | ✅ | `SYSTEM_ADMIN` |
| `PUT /api/roles/{id}` | ✅ | `SYSTEM_ADMIN` |
| `DELETE /api/roles/{id}` | ✅ | `SYSTEM_ADMIN` |
| `GET /api/roles/{roleId}/permissions` | ✅ | `SYSTEM_ADMIN` |
| `POST /api/roles/{roleId}/permissions` | ✅ | `SYSTEM_ADMIN` |
| `DELETE /api/roles/{roleId}/permissions/{permissionId}` | ✅ | `SYSTEM_ADMIN` |
| `POST /api/permissions` | ✅ | `SYSTEM_ADMIN` |
| `GET /api/permissions` | ✅ | `SYSTEM_ADMIN` |
| `GET /api/permissions/{id}` | ✅ | `SYSTEM_ADMIN` |
| `PUT /api/permissions/{id}` | ✅ | `SYSTEM_ADMIN` |
| `DELETE /api/permissions/{id}` | ✅ | `SYSTEM_ADMIN` |

Danh sách role được phép gọi các API này có thể thay đổi bằng cấu hình `RBAC_MANAGEMENT_ALLOWED_ROLES`. Permission `rbac.manage` trong JWT không thay thế yêu cầu về role.

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

Tất cả endpoint nhận access token qua header:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Thiếu token, token hết hạn hoặc chữ ký không hợp lệ trả `401 UNAUTHORIZED`. Token hợp lệ nhưng không chứa role được phép quản trị RBAC trả `403 FORBIDDEN`.

Việc kiểm tra quyền được áp dụng tại service bằng `@CanManageRbac`, vì vậy các lời gọi service từ controller khác cũng tuân theo cùng chính sách.

---

## Phân trang

Hai endpoint danh sách `GET /api/roles` và `GET /api/permissions` nhận các query parameter sau:

| Parameter | Type | Mặc định | Ý nghĩa |
|:----------|:-----|:--------:|:--------|
| `page` | integer | `0` | Số trang, bắt đầu từ 0 |
| `size` | integer | `20` | Số phần tử mỗi trang |
| `sort` | string | `id,asc` | Thuộc tính và chiều sắp xếp, ví dụ `code,asc` hoặc `id,desc` |

Ví dụ response phân trang được rút gọn:

```json
{
  "success": true,
  "data": {
    "content": [ ... ],
    "totalElements": 5,
    "totalPages": 1,
    "number": 0,
    "size": 20
  },
  "error": null
}
```

---

## POST `/api/roles`

Tạo role tùy chỉnh. Role mới luôn có `isSystem=false` và `isActive=true`.

### Request

```json
{
  "code": "RBAC_MANAGER",
  "name": "Quản trị phân quyền",
  "description": "Quản lý vai trò và quyền trong hệ thống"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `code` | string | ✅ | Tối đa 50 ký tự; bắt đầu bằng `A-Z`; chỉ gồm `A-Z`, `0-9`, `_`; không bắt đầu bằng `ROLE_` |
| `name` | string | ✅ | Không rỗng, tối đa 150 ký tự |
| `description` | string | ❌ | Mô tả role, có thể bỏ qua hoặc để `null` |

`code` là duy nhất và không thể thay đổi sau khi tạo. Client dùng code không có prefix `ROLE_`; Spring Security tự chuyển role trong JWT thành authority có prefix này.

### Response `201 Created`

```json
{
  "success": true,
  "data": {
    "id": 7,
    "code": "RBAC_MANAGER",
    "name": "Quản trị phân quyền",
    "description": "Quản lý vai trò và quyền trong hệ thống",
    "isSystem": false,
    "isActive": true
  },
  "error": null
}
```

Tạo role không tự gán role đó cho account và không tự thêm role vào danh sách được phép quản trị RBAC.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Body hoặc field không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có role được phép quản trị RBAC |
| 409 | `CONFLICT` | `code` đã tồn tại, kể cả code của role đã xóa mềm |

---

## GET `/api/roles`

Lấy danh sách role chưa bị xóa mềm, bao gồm cả role active và inactive.

### Query parameters

```http
GET /api/roles?page=0&size=20&sort=code,asc
```

Endpoint sử dụng các tham số [phân trang](#phân-trang) chung.

### Response `200 OK`

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "code": "SYSTEM_ADMIN",
        "name": "System Administrator",
        "description": "Manages accounts, roles, and permissions",
        "isSystem": true,
        "isActive": true
      },
      {
        "id": 7,
        "code": "RBAC_MANAGER",
        "name": "Quản trị phân quyền",
        "description": "Quản lý vai trò và quyền trong hệ thống",
        "isSystem": false,
        "isActive": true
      }
    ],
    "totalElements": 2,
    "totalPages": 1,
    "number": 0,
    "size": 20
  },
  "error": null
}
```

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Tham số query không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có role được phép quản trị RBAC |

---

## GET `/api/roles/{id}`

Lấy chi tiết một role chưa bị xóa mềm.

### Response `200 OK`

```json
{
  "success": true,
  "data": {
    "id": 7,
    "code": "RBAC_MANAGER",
    "name": "Quản trị phân quyền",
    "description": "Quản lý vai trò và quyền trong hệ thống",
    "isSystem": false,
    "isActive": true
  },
  "error": null
}
```

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | `id` không đúng kiểu số |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có role được phép quản trị RBAC |
| 404 | `ROLE_NOT_FOUND` | Role không tồn tại hoặc đã bị xóa mềm |

---

## PUT `/api/roles/{id}`

Cập nhật tên, mô tả và trạng thái của role. `code` và `isSystem` không thể thay đổi qua API này.

### Request

```json
{
  "name": "Quản trị role và permission",
  "description": "Mô tả mới",
  "isActive": true
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `name` | string | ✅ | Không rỗng, tối đa 150 ký tự |
| `description` | string | ❌ | Mô tả mới, có thể là `null` |
| `isActive` | boolean | ✅ | Role hệ thống không thể chuyển thành `false` |

### Response `200 OK`

```json
{
  "success": true,
  "data": {
    "id": 7,
    "code": "RBAC_MANAGER",
    "name": "Quản trị role và permission",
    "description": "Mô tả mới",
    "isSystem": false,
    "isActive": true
  },
  "error": null
}
```

Vô hiệu hóa role tùy chỉnh làm role đó không còn được đưa vào authorization snapshot khi đăng nhập hoặc refresh token.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Body, `id` hoặc field không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có role được phép quản trị RBAC |
| 404 | `ROLE_NOT_FOUND` | Role không tồn tại hoặc đã bị xóa mềm |
| 409 | `CONFLICT` | Cố vô hiệu hóa role hệ thống |

---

## DELETE `/api/roles/{id}`

Xóa mềm một role tùy chỉnh. Hệ thống đặt `deletedAt`, cập nhật `updatedAt` và chuyển `isActive=false`; dữ liệu role và lịch sử phân quyền vẫn được giữ trong database.

### Response `200 OK`

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

Role đã xóa không còn xuất hiện trong danh sách hoặc endpoint chi tiết. Code của role vẫn được giữ và không thể dùng lại.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | `id` không đúng kiểu số |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có role được phép quản trị RBAC |
| 404 | `ROLE_NOT_FOUND` | Role không tồn tại hoặc đã bị xóa mềm |
| 409 | `CONFLICT` | Cố xóa role hệ thống |

---

## GET `/api/roles/{roleId}/permissions`

Lấy toàn bộ permission đã gán cho một role. Danh sách không phân trang và có thể chứa permission inactive.

### Response `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "code": "rbac.manage",
      "module": "RBAC",
      "description": "Manage accounts, roles, and permissions",
      "isActive": true
    }
  ],
  "error": null
}
```

Role chưa có permission trả `data: []`.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | `roleId` không đúng kiểu số |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có role được phép quản trị RBAC |
| 404 | `ROLE_NOT_FOUND` | Role không tồn tại hoặc đã bị xóa mềm |

---

## POST `/api/roles/{roleId}/permissions`

Gán một permission cho role.

### Request

```json
{
  "permissionId": 12
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `permissionId` | integer | ✅ | Số nguyên dương, permission phải tồn tại |

Account thực hiện được lấy từ claim `accountId` trong JWT. Client không truyền ID người cấp quyền.

### Response `200 OK`

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

API lưu quan hệ ngay cả khi role hoặc permission đang inactive. Chỉ role và permission active mới được đưa vào authorization snapshot mới.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Body, `roleId` hoặc `permissionId` không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có role được phép quản trị RBAC |
| 404 | `ROLE_NOT_FOUND` | Role không tồn tại hoặc đã bị xóa mềm |
| 404 | `PERMISSION_NOT_FOUND` | Permission không tồn tại |
| 404 | `RESOURCE_NOT_FOUND` | Account trong claim `accountId` không còn tồn tại |
| 409 | `CONFLICT` | Permission đã được gán cho role |

---

## DELETE `/api/roles/{roleId}/permissions/{permissionId}`

Gỡ một permission khỏi role. Endpoint không cần request body.

### Response `200 OK`

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | `roleId` hoặc `permissionId` không đúng kiểu số |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có role được phép quản trị RBAC |
| 404 | `ROLE_NOT_FOUND` | Role không tồn tại hoặc đã bị xóa mềm |
| 404 | `PERMISSION_NOT_FOUND` | Role không có mapping với permission này |

---

## POST `/api/permissions`

Tạo permission trong danh mục quyền. Permission mới luôn có `isActive=true`.

### Request

```json
{
  "code": "employee.export",
  "module": "EMPLOYEE",
  "description": "Xuất danh sách nhân viên"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `code` | string | ✅ | Tối đa 100 ký tự; gồm ít nhất hai phần chữ thường ngăn bởi dấu chấm |
| `module` | string | ✅ | Một trong `EMPLOYEE`, `ACCOUNT`, `ORGANIZATION`, `REQUEST`, `ATTENDANCE`, `PAYROLL`, `RBAC`, `REPORT` |
| `description` | string | ✅ | Không rỗng |

Mỗi phần của `code` phải bắt đầu bằng `a-z` và chỉ chứa `a-z`, `0-9`, `_`. Ví dụ hợp lệ: `employee.export`, `payroll.mark_paid`.

`code` và `module` không thể thay đổi sau khi tạo. Tạo permission chỉ thêm quyền vào danh mục; API nghiệp vụ tương ứng vẫn phải kiểm tra permission và scope khi triển khai tính năng.

### Response `201 Created`

```json
{
  "success": true,
  "data": {
    "id": 12,
    "code": "employee.export",
    "module": "EMPLOYEE",
    "description": "Xuất danh sách nhân viên",
    "isActive": true
  },
  "error": null
}
```

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Body hoặc field không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có role được phép quản trị RBAC |
| 409 | `CONFLICT` | `code` đã tồn tại |

---

## GET `/api/permissions`

Lấy danh sách permission, bao gồm cả permission active và inactive. Có thể lọc theo module.

### Query parameters

```http
GET /api/permissions?module=RBAC&page=0&size=20&sort=code,asc
```

| Parameter | Type | Bắt buộc | Ý nghĩa |
|:----------|:-----|:--------:|:--------|
| `module` | string | ❌ | Một trong `EMPLOYEE`, `ACCOUNT`, `ORGANIZATION`, `REQUEST`, `ATTENDANCE`, `PAYROLL`, `RBAC`, `REPORT` |
| `page` | integer | ❌ | Số trang, mặc định `0` |
| `size` | integer | ❌ | Số phần tử mỗi trang, mặc định `20` |
| `sort` | string | ❌ | Sắp xếp, mặc định `id,asc` |

### Response `200 OK`

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "code": "rbac.manage",
        "module": "RBAC",
        "description": "Manage accounts, roles, and permissions",
        "isActive": true
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 20
  },
  "error": null
}
```

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | `module` hoặc tham số phân trang không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có role được phép quản trị RBAC |

---

## GET `/api/permissions/{id}`

Lấy chi tiết một permission.

### Response `200 OK`

```json
{
  "success": true,
  "data": {
    "id": 12,
    "code": "employee.export",
    "module": "EMPLOYEE",
    "description": "Xuất danh sách nhân viên",
    "isActive": true
  },
  "error": null
}
```

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | `id` không đúng kiểu số |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có role được phép quản trị RBAC |
| 404 | `PERMISSION_NOT_FOUND` | Permission không tồn tại |

---

## PUT `/api/permissions/{id}`

Cập nhật mô tả và trạng thái của permission. `code` và `module` không thể thay đổi qua API này.

### Request

```json
{
  "description": "Xuất báo cáo danh sách nhân viên",
  "isActive": true
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `description` | string | ✅ | Không rỗng |
| `isActive` | boolean | ✅ | Permission `rbac.manage` không thể chuyển thành `false` |

### Response `200 OK`

```json
{
  "success": true,
  "data": {
    "id": 12,
    "code": "employee.export",
    "module": "EMPLOYEE",
    "description": "Xuất báo cáo danh sách nhân viên",
    "isActive": true
  },
  "error": null
}
```

Permission inactive vẫn còn trong danh mục và các mapping đã tạo, nhưng không được đưa vào authorization snapshot mới.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Body, `id` hoặc field không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có role được phép quản trị RBAC |
| 404 | `PERMISSION_NOT_FOUND` | Permission không tồn tại |
| 409 | `CONFLICT` | Cố vô hiệu hóa permission `rbac.manage` |

---

## DELETE `/api/permissions/{id}`

Xóa vĩnh viễn một permission chưa được sử dụng. Endpoint không cần request body.

### Response `200 OK`

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

Permission không thể xóa nếu đang được bất kỳ role hoặc permission override nào tham chiếu. Override đã hết hiệu lực vẫn được xem là dữ liệu lịch sử tham chiếu permission; trong trường hợp đó nên vô hiệu hóa permission thay vì xóa.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | `id` không đúng kiểu số |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có role được phép quản trị RBAC |
| 404 | `PERMISSION_NOT_FOUND` | Permission không tồn tại |
| 409 | `CONFLICT` | Permission đang được tham chiếu hoặc là `rbac.manage` |

---

## Hiệu lực của thay đổi phân quyền

- JWT lưu role và permission tại thời điểm phát token. Thay đổi role, permission hoặc mapping chỉ xuất hiện trong access token mới sau khi account đăng nhập hoặc refresh token.
- Access token đã phát giữ nguyên claims đến khi hết hạn; vô hiệu hóa hoặc gỡ quyền không thu hồi ngay token đó.
- Chỉ role chưa xóa, đang active và còn hiệu lực gán cho account mới được đưa vào authorization snapshot.
- Chỉ permission active mới được đưa vào authorization snapshot, dù mapping role–permission vẫn tồn tại.
- Role có scope `SELF`, `ORG_UNIT` hoặc `LOCATION` vẫn cần kiểm tra scope tại authorization/service layer của API nghiệp vụ.

---

## Cấu hình quản trị RBAC

| Biến môi trường | Mặc định | Ý nghĩa |
|:----------------|:---------|:--------|
| `RBAC_MANAGEMENT_ALLOWED_ROLES` | `SYSTEM_ADMIN` | Danh sách role code được phép gọi API RBAC, phân tách bằng dấu phẩy |
| `RBAC_SEED_ENABLED` | `true` | Seed role, permission và mapping mặc định khi khởi động |
| `ADMIN_SEED_ENABLED` | `true` | Seed system admin và bảo đảm quyền bootstrap `rbac.manage` |

Ví dụ cho phép thêm role `RBAC_MANAGER` quản trị RBAC:

```powershell
$env:RBAC_MANAGEMENT_ALLOWED_ROLES = "SYSTEM_ADMIN,RBAC_MANAGER"
./mvnw.cmd spring-boot:run
```

Dùng role code không có prefix `ROLE_`. Giá trị cấu hình thay thế toàn bộ danh sách mặc định, vì vậy cần giữ `SYSTEM_ADMIN` nếu system admin vẫn phải có quyền truy cập. Danh sách rỗng từ chối mọi role.

Sau khi tạo `RBAC_MANAGER`, cần thực hiện cả hai việc sau:

1. Thêm `RBAC_MANAGER` vào `RBAC_MANAGEMENT_ALLOWED_ROLES` và khởi động lại API.
2. Gán role cho account qua nghiệp vụ account, sau đó đăng nhập hoặc refresh để nhận JWT mới.

Nếu seeder còn bật, role, permission hoặc mapping mặc định bị thiếu có thể được tạo lại khi API khởi động. Sau giai đoạn bootstrap, đặt `RBAC_SEED_ENABLED=false` và `ADMIN_SEED_ENABLED=false` nếu muốn quản lý lâu dài các mapping mặc định hoàn toàn qua API.
