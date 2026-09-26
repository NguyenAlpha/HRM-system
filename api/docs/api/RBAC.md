# API Reference — RBAC

Quản lý role tùy chỉnh và quan hệ permission của role. Danh mục permission và các system role do ứng dụng định nghĩa; API chỉ cho phép đọc các dữ liệu này. Việc gán role cho account được mô tả riêng tại [Account Role Assignments](./ACCOUNT_ROLE_ASSIGNMENTS.md); API RBAC không trực tiếp thay đổi assignment của account.

---

## Endpoint access

| Endpoint | Yêu cầu Bearer token | Permission yêu cầu |
|:---------|:--------------------:|:-------------------:|
| `POST /api/roles` | ✅ | `rbac.manage` |
| `GET /api/roles` | ✅ | `rbac.manage` |
| `GET /api/roles/with-permissions` | ✅ | `rbac.manage` |
| `GET /api/roles/{id}` | ✅ | `rbac.manage` |
| `PUT /api/roles/{id}` | ✅ | `rbac.manage` |
| `DELETE /api/roles/{id}` | ✅ | `rbac.manage` |
| `GET /api/roles/{roleId}/permissions` | ✅ | `rbac.manage` |
| `POST /api/roles/{roleId}/permissions` | ✅ | `rbac.manage` |
| `DELETE /api/roles/{roleId}/permissions/{permissionId}` | ✅ | `rbac.manage` |
| `GET /api/permissions` | ✅ | `rbac.manage` |
| `GET /api/permissions/{id}` | ✅ | `rbac.manage` |

`COMPANY_OWNER` và, tạm thời, `SYSTEM_ADMIN` được seed permission `rbac.manage`. Có thể ủy quyền quản trị custom role cho một custom role khác bằng cách gán permission này; backend không hard-code role code khi kiểm tra truy cập RBAC.

### Ranh giới quản trị

- Permission được định nghĩa trong code và đồng bộ bằng `PermissionSeeder` hoặc database migration; không có API tạo, sửa hoặc xóa permission.
- Permission có `assignmentPolicy=DELEGABLE` mới được gán cho custom role; `SYSTEM_ONLY` chỉ được seeder gán cho system role.
- System role có `isSystem=true` là khuôn mẫu do ứng dụng sở hữu; API chỉ cho phép xem role và danh sách permission của role.
- Chỉ custom role có `isSystem=false` mới được cập nhật, xóa mềm hoặc thay đổi permission mapping.
- Mỗi role có `grantPolicy` xác định workflow được phép dùng để cấp role cho account; đây không phải cấp bậc và không tạo role hierarchy.
- Custom role mới luôn có `grantPolicy=OWNER_APPROVAL`; client không được tự hạ chính sách này qua API RBAC.
- `COMPANY_OWNER` chọn permission có sẵn để cấu hình custom role, không tự định nghĩa capability mới cho hệ thống.

### Chính sách cấp role

| `grantPolicy` | Ý nghĩa |
|:--------------|:--------|
| `AUTO` | Hệ thống tự gán qua workflow chuyên biệt; không nhận yêu cầu cấp role thủ công |
| `HR_ASSIGNABLE` | HR được đề xuất và hệ thống có thể cấp ngay nếu dữ liệu hợp lệ |
| `OWNER_APPROVAL` | Yêu cầu của HR phải được Company Owner phê duyệt trước khi có hiệu lực |
| `SYSTEM_ONLY` | Chỉ workflow nội bộ dành riêng cho hệ thống được phép cấp role |

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

Thiếu token, token hết hạn hoặc chữ ký không hợp lệ trả `401 UNAUTHORIZED`. Token hợp lệ nhưng không chứa permission `rbac.manage` trả `403 FORBIDDEN`.

Việc kiểm tra permission được áp dụng tại service bằng `@CanManageRbac`, vì vậy các lời gọi service từ controller khác cũng tuân theo cùng chính sách.

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

## GET `/api/roles/with-permissions`

Lấy toàn bộ role chưa bị xóa mềm cùng các permission đã gán cho từng role. Endpoint không phân trang, sắp xếp role theo `id` tăng dần và permission trong từng role theo `code` tăng dần.

Danh sách permission phản ánh đầy đủ mapping đã lưu, bao gồm cả permission đang có `isActive=false`. Role chưa được gán permission trả về `permissions: []`.

### Response `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "id": 2,
      "code": "COMPANY_OWNER",
      "name": "Chủ sở hữu doanh nghiệp",
      "description": "Quản trị tài khoản, quyền truy cập và cấu hình trong phạm vi doanh nghiệp",
      "isSystem": true,
      "grantPolicy": "SYSTEM_ONLY",
      "permissions": [
        {
          "id": 8,
          "code": "account.role.assign",
          "name": "Phân quyền tài khoản",
          "module": "ACCOUNT",
          "description": "Gán và thu hồi vai trò của tài khoản",
          "assignmentPolicy": "DELEGABLE",
          "isActive": true
        },
        {
          "id": 15,
          "code": "rbac.manage",
          "name": "Quản lý phân quyền",
          "module": "RBAC",
          "description": "Xem danh mục quyền và quản lý vai trò tùy chỉnh cùng quan hệ phân quyền",
          "assignmentPolicy": "DELEGABLE",
          "isActive": true
        }
      ]
    }
  ],
  "error": null
}
```

Khi không có role, API trả `data: []`.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có permission `rbac.manage` |

---

## POST `/api/roles`

Tạo role tùy chỉnh. Role mới luôn có `isSystem=false`.

### Request

```json
{
  "code": "RBAC_MANAGER",
  "name": "Quản trị phân quyền",
  "description": "Quản lý vai trò tùy chỉnh trong doanh nghiệp"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `code` | string | ✅ | Tối đa 50 ký tự; bắt đầu bằng `A-Z`; chỉ gồm `A-Z`, `0-9`, `_`; không bắt đầu bằng `ROLE_` |
| `name` | string | ✅ | Không rỗng, tối đa 150 ký tự |
| `description` | string | ❌ | Mô tả role, có thể bỏ qua hoặc để `null` |

`code` là duy nhất và không thể thay đổi sau khi tạo. Client dùng code không có prefix `ROLE_`; Spring Security tự chuyển role trong JWT thành authority có prefix này.

Role mới luôn có `grantPolicy=OWNER_APPROVAL`; request không nhận field này.

### Response `201 Created`

```json
{
  "success": true,
  "data": {
    "id": 7,
    "code": "RBAC_MANAGER",
    "name": "Quản trị phân quyền",
    "description": "Quản lý vai trò tùy chỉnh trong doanh nghiệp",
    "isSystem": false,
    "grantPolicy": "OWNER_APPROVAL"
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
| 403 | `FORBIDDEN` | Account không có permission `rbac.manage` |
| 409 | `CONFLICT` | `code` đã tồn tại, kể cả code của role đã xóa mềm |

---

## GET `/api/roles`

Lấy danh sách role chưa bị xóa mềm.

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
        "name": "Quản trị viên hệ thống",
        "description": "Khởi tạo Chủ sở hữu doanh nghiệp đầu tiên, không tham gia nghiệp vụ nội bộ công ty",
        "isSystem": true,
        "grantPolicy": "SYSTEM_ONLY"
      },
      {
        "id": 7,
        "code": "RBAC_MANAGER",
        "name": "Quản trị phân quyền",
        "description": "Quản lý vai trò tùy chỉnh trong doanh nghiệp",
        "isSystem": false,
        "grantPolicy": "OWNER_APPROVAL"
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
| 403 | `FORBIDDEN` | Account không có permission `rbac.manage` |

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
    "description": "Quản lý vai trò tùy chỉnh trong doanh nghiệp",
    "isSystem": false,
    "grantPolicy": "OWNER_APPROVAL"
  },
  "error": null
}
```

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | `id` không đúng kiểu số |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có permission `rbac.manage` |
| 404 | `ROLE_NOT_FOUND` | Role không tồn tại hoặc đã bị xóa mềm |

---

## PUT `/api/roles/{id}`

Cập nhật tên và mô tả của custom role. System role không thể thay đổi qua API này.

### Request

```json
{
  "name": "Quản trị role và permission",
  "description": "Mô tả mới"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `name` | string | ✅ | Không rỗng, tối đa 150 ký tự |
| `description` | string | ❌ | Mô tả mới, có thể là `null` |

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
    "grantPolicy": "OWNER_APPROVAL"
  },
  "error": null
}
```

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Body, `id` hoặc field không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có permission `rbac.manage` |
| 404 | `ROLE_NOT_FOUND` | Role không tồn tại hoặc đã bị xóa mềm |
| 409 | `CONFLICT` | Cố cập nhật system role |

---

## DELETE `/api/roles/{id}`

Xóa mềm một role tùy chỉnh. Hệ thống đặt `deletedAt` và cập nhật `updatedAt`; dữ liệu role và lịch sử phân quyền vẫn được giữ trong database.

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
| 403 | `FORBIDDEN` | Account không có permission `rbac.manage` |
| 404 | `ROLE_NOT_FOUND` | Role không tồn tại hoặc đã bị xóa mềm |
| 409 | `CONFLICT` | Cố xóa role hệ thống |

---

## GET `/api/roles/{roleId}/permissions`

Lấy toàn bộ permission đã gán cho một role. Danh sách không phân trang và có thể chứa permission inactive. Endpoint đọc được dùng cho cả system role và custom role.

### Response `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "code": "rbac.manage",
      "name": "Quản lý phân quyền",
      "module": "RBAC",
      "description": "Xem danh mục quyền và quản lý vai trò tùy chỉnh cùng quan hệ phân quyền",
      "assignmentPolicy": "DELEGABLE",
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
| 403 | `FORBIDDEN` | Account không có permission `rbac.manage` |
| 404 | `ROLE_NOT_FOUND` | Role không tồn tại hoặc đã bị xóa mềm |

---

## POST `/api/roles/{roleId}/permissions`

Gán một permission có sẵn cho custom role. Permission và system role không thể được thay đổi qua endpoint này.

### Request

```json
{
  "permissionId": 12
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `permissionId` | integer | ✅ | Số nguyên dương; permission phải tồn tại và có `assignmentPolicy=DELEGABLE` |

Account thực hiện được lấy từ claim `accountId` trong JWT. Client không truyền ID người cấp quyền.

### Response `200 OK`

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

API lưu quan hệ ngay cả khi permission đang inactive. Role chưa bị xóa mềm và permission active mới được đưa vào authorization snapshot mới.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Body, `roleId` hoặc `permissionId` không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có permission `rbac.manage` |
| 404 | `ROLE_NOT_FOUND` | Role không tồn tại hoặc đã bị xóa mềm |
| 404 | `PERMISSION_NOT_FOUND` | Permission không tồn tại |
| 404 | `RESOURCE_NOT_FOUND` | Account trong claim `accountId` không còn tồn tại |
| 409 | `CONFLICT` | Permission đã được gán, permission là `SYSTEM_ONLY` hoặc role đích là system role |

---

## DELETE `/api/roles/{roleId}/permissions/{permissionId}`

Gỡ một permission khỏi custom role. Endpoint không cần request body; system role không thể thay đổi mapping.

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
| 403 | `FORBIDDEN` | Account không có permission `rbac.manage` |
| 404 | `ROLE_NOT_FOUND` | Role không tồn tại hoặc đã bị xóa mềm |
| 404 | `PERMISSION_NOT_FOUND` | Role không có mapping với permission này |
| 409 | `CONFLICT` | Role đích là system role |

---

## GET `/api/permissions`

Lấy danh mục permission do ứng dụng định nghĩa, bao gồm cả permission active và inactive. Có thể lọc theo module. Client chỉ hiển thị permission `DELEGABLE` trong lựa chọn gán cho custom role; permission `SYSTEM_ONLY` vẫn xuất hiện để người quản trị hiểu đầy đủ catalog.

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
        "name": "Quản lý phân quyền",
        "module": "RBAC",
        "description": "Xem danh mục quyền và quản lý vai trò tùy chỉnh cùng quan hệ phân quyền",
        "assignmentPolicy": "DELEGABLE",
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
| 403 | `FORBIDDEN` | Account không có permission `rbac.manage` |

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
    "name": "Xuất danh sách nhân viên",
    "module": "EMPLOYEE",
    "description": "Cho phép xuất danh sách nhân viên trong phạm vi được giao",
    "assignmentPolicy": "DELEGABLE",
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
| 403 | `FORBIDDEN` | Account không có permission `rbac.manage` |
| 404 | `PERMISSION_NOT_FOUND` | Permission không tồn tại |

`assignmentPolicy` nhận một trong hai giá trị:

| Giá trị | Ý nghĩa |
|:--------|:--------|
| `DELEGABLE` | Company Owner hoặc người có `rbac.manage` có thể gán permission cho custom role |
| `SYSTEM_ONLY` | Chỉ seeder được gán permission cho system role; API từ chối gán cho custom role |

Hiện tại `organization.company_owner.bootstrap` và `role.assignment.approve` là `SYSTEM_ONLY`; các permission còn lại là `DELEGABLE`.
Migration và `RolePermissionSeeder` tự thu hồi mapping `SYSTEM_ONLY` từng bị gán cho custom role trước khi chính sách này được áp dụng.

Ba permission chuẩn bị cho workflow cấp tài khoản và đề xuất role:

| Permission | Role hệ thống được seed | Mục đích |
|:-----------|:------------------------|:---------|
| `account.provision` | `HR_STAFF`, `COMPANY_OWNER` | Tạo account cho employee hợp lệ; endpoint sẽ dùng permission này khi workflow provisioning được triển khai |
| `role.assignment.request` | `HR_STAFF` | Gửi đề xuất cấp role nghiệp vụ |
| `role.assignment.approve` | `COMPANY_OWNER` | Duyệt hoặc từ chối đề xuất; là `SYSTEM_ONLY` nên không thể gán cho custom role qua API RBAC |

Việc seed permission không tự tạo endpoint và không tự cấp role cho account. Các API tương ứng được triển khai ở các nhóm workflow tiếp theo.

`SYSTEM_ADMIN` sử dụng riêng permission `organization.company_owner.bootstrap` tại `POST /api/admin/organization/company-owner` để tạo Company Owner đầu tiên. Workflow và giới hạn chống tạo trùng được mô tả trong [Organization Company Owner Bootstrap](./ORGANIZATION_COMPANY_OWNER_ADMIN.md).

---

## Hiệu lực của thay đổi phân quyền

- JWT lưu role và permission tại thời điểm phát token. Thay đổi role, permission hoặc mapping chỉ xuất hiện trong access token mới sau khi account đăng nhập hoặc refresh token.
- Access token đã phát giữ nguyên claims đến khi hết hạn; vô hiệu hóa hoặc gỡ quyền không thu hồi ngay token đó.
- Chỉ role chưa bị xóa mềm và còn hiệu lực gán cho account mới được đưa vào authorization snapshot.
- Chỉ permission active mới được đưa vào authorization snapshot, dù mapping role–permission vẫn tồn tại.
- Role có scope `SELF`, `ORG_UNIT` hoặc `LOCATION` vẫn cần kiểm tra scope tại authorization/service layer của API nghiệp vụ.

---

## Cấu hình seed và ủy quyền quản trị RBAC

| Biến môi trường | Mặc định | Ý nghĩa |
|:----------------|:---------|:--------|
| `RBAC_SEED_ENABLED` | `true` | Seed role, permission và mapping mặc định khi khởi động |
| `ADMIN_SEED_ENABLED` | `true` | Seed account admin và gán role `SYSTEM_ADMIN` đã được RBAC seed tạo trước |
| `USER_SEED_ENABLED` | `false` | Seed các account demo, gồm một `HR_STAFF`; chỉ nên bật ở môi trường demo/test |

Để ủy quyền quản trị custom role cho một người khác:

1. Tạo custom role, ví dụ `RBAC_MANAGER`.
2. Gán permission `rbac.manage` cho custom role đó.
3. Gán role cho account qua nghiệp vụ account.
4. Đăng nhập hoặc refresh để nhận JWT mới.

Không cần cấu hình allowlist hoặc khởi động lại API. Quyền truy cập được quyết định trực tiếp từ permission trong JWT.

Permission catalog, system role và permission mapping của system role thuộc sở hữu của code. `RoleSeeder`, `PermissionSeeder` và `RolePermissionSeeder` chạy trước `SystemAdminSeeder`; seeder admin chỉ tạo account và gán role đã tồn tại, không tự tạo hoặc sửa RBAC. Giữ `RBAC_SEED_ENABLED=true` để ứng dụng đồng bộ các định nghĩa này khi khởi động; nếu tắt RBAC seed thì database phải có sẵn role `SYSTEM_ADMIN` hợp lệ, nếu không admin seed sẽ fail-fast. Permission mới phải được bổ sung qua `PermissionSeeder` hoặc database migration, không qua API. `ADMIN_SEED_ENABLED` có thể tắt sau khi tài khoản bootstrap đã được bảo đảm bằng quy trình vận hành khác.
