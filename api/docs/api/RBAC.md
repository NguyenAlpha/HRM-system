# API Reference — Role và Permission

Các API này quản lý role, permission và permission được gán cho từng role. Response dùng
`ApiResult` giống [API Auth](AUTH.md). Tất cả request cần `Authorization: Bearer <accessToken>`.

## Phân quyền và mở rộng

Mặc định chỉ JWT có role `SYSTEM_ADMIN` được sử dụng toàn bộ API bên dưới. Thiếu hoặc sai
token trả `401 UNAUTHORIZED`; đã xác thực nhưng không có role được phép trả `403 FORBIDDEN`.
Riêng permission `rbac.manage` trong token không thay thế yêu cầu về role này.

`@CanManageRbac` trên `RoleServiceImpl` và `PermissionServiceImpl` dùng chung `RbacAccessPolicy`.
Việc kiểm tra ở service cũng bảo vệ các lời gọi từ controller khác sau này.

Danh sách role được phép quản trị nằm tại một cấu hình:

```properties
rbac.management.allowed-roles=${RBAC_MANAGEMENT_ALLOWED_ROLES:SYSTEM_ADMIN}
```

Ví dụ cho phép thêm role `RBAC_MANAGER` bằng biến môi trường, rồi khởi động lại API:

```powershell
$env:RBAC_MANAGEMENT_ALLOWED_ROLES = "SYSTEM_ADMIN,RBAC_MANAGER"
./mvnw.cmd spring-boot:run
```

Dùng role code không có prefix `ROLE_`. Cấu hình này thay thế toàn bộ danh sách mặc định;
giữ `SYSTEM_ADMIN` trong danh sách nếu admin vẫn cần quyền truy cập. Danh sách rỗng từ chối
mọi role. Không cần sửa controller/service khi bổ sung role được phép.

Role mới được tạo qua API có `isSystem=false`, `isActive=true`. Tạo role không tự gán role đó
cho tài khoản và cũng không tự thêm role vào danh sách được phép quản trị. Cần gán role cho
tài khoản qua nghiệp vụ account, rồi đăng nhập hoặc refresh token để JWT có role mới.

## Endpoint

| Method | Path | Chức năng | Thành công |
| --- | --- | --- | --- |
| POST | `/api/roles` | Tạo role | 201 |
| GET | `/api/roles` | Danh sách role chưa xóa, phân trang | 200 |
| GET | `/api/roles/{id}` | Chi tiết role | 200 |
| PUT | `/api/roles/{id}` | Sửa tên, mô tả, trạng thái role | 200 |
| DELETE | `/api/roles/{id}` | Xóa mềm role | 200 |
| GET | `/api/roles/{roleId}/permissions` | Permission đã gán cho role | 200 |
| POST | `/api/roles/{roleId}/permissions` | Gán permission cho role | 200 |
| DELETE | `/api/roles/{roleId}/permissions/{permissionId}` | Gỡ permission khỏi role | 200 |
| POST | `/api/permissions` | Tạo permission | 201 |
| GET | `/api/permissions` | Danh sách permission, phân trang | 200 |
| GET | `/api/permissions/{id}` | Chi tiết permission | 200 |
| PUT | `/api/permissions/{id}` | Sửa mô tả, trạng thái permission | 200 |
| DELETE | `/api/permissions/{id}` | Xóa permission chưa được sử dụng | 200 |

Danh sách nhận `page` (từ 0), `size` (mặc định 20) và `sort` (mặc định `id,asc`).
`GET /api/permissions?module=RBAC&page=0&size=20&sort=code,asc` lọc thêm theo module.
Response phân trang nằm trong `data.content`, cùng `totalElements`, `totalPages`, `number`, `size`.

## Request body

Tạo role — `POST /api/roles`:

```json
{
  "code": "RBAC_MANAGER",
  "name": "Quản trị phân quyền",
  "description": "Quản lý vai trò và quyền trong hệ thống"
}
```

`code` tối đa 50 ký tự, bắt đầu bằng chữ hoa, chỉ gồm `A-Z`, `0-9`, `_`, không bắt đầu bằng
`ROLE_`; `name` bắt buộc, tối đa 150 ký tự. `description` tùy chọn. Code giữ nguyên sau khi tạo.

Sửa role — `PUT /api/roles/{id}`:

```json
{
  "name": "Quản trị phân quyền",
  "description": "Mô tả mới",
  "isActive": true
}
```

Tạo permission — `POST /api/permissions`:

```json
{
  "code": "employee.export",
  "module": "EMPLOYEE",
  "description": "Xuất danh sách nhân viên"
}
```

Cả ba field đều bắt buộc; `code` tối đa 100 ký tự, gồm các phần chữ thường ngăn bởi dấu chấm
(ví dụ `employee.export`, `payroll.mark_paid`). Mỗi phần bắt đầu bằng `a-z` và chỉ chứa
`a-z`, `0-9`, `_`. Quy tắc này tách permission code khỏi authority có prefix `ROLE_`.
Module nhận một trong `EMPLOYEE`,
`REQUEST`, `ATTENDANCE`, `PAYROLL`, `RBAC`, `REPORT`. Code và module giữ nguyên sau khi tạo.

Sửa permission — `PUT /api/permissions/{id}`:

```json
{
  "description": "Xuất báo cáo danh sách nhân viên",
  "isActive": true
}
```

Gán permission — `POST /api/roles/{roleId}/permissions`:

```json
{
  "permissionId": 12
}
```

`permissionId` phải là số nguyên dương. Tài khoản thực hiện được lấy từ `accountId` trong
JWT; client không chỉ định người thực hiện. API gỡ permission và xóa role/permission không
cần body, trả `{"success":true,"data":null,"error":null}` khi thành công.

## Quy tắc dữ liệu và lỗi

- Role hệ thống (`isSystem=true`) không thể bị xóa hoặc vô hiệu hóa; vẫn sửa được tên/mô tả.
- Xóa role tùy chỉnh đặt `deletedAt`, cập nhật `updatedAt` và `isActive=false`, giữ dữ liệu
  lịch sử phân quyền. Role đã xóa không xuất hiện trong danh sách hoặc chi tiết; code vẫn
  được giữ theo unique constraint của database.
- Permission đang được role hoặc permission override tham chiếu không thể xóa. Có thể gỡ
  liên kết hoặc vô hiệu hóa permission qua PUT. Override lịch sử cũng được tính là tham chiếu.
- Permission `rbac.manage` được bảo vệ khỏi xóa hoặc vô hiệu hóa để giữ invariant của bootstrap.
- Nếu bật seeder, các role/permission mặc định và mapping còn thiếu có thể được tạo lại
  khi khởi động API. Khi muốn tự quản lý các mapping mặc định lâu dài, cấu hình
  `RBAC_SEED_ENABLED=false` và `ADMIN_SEED_ENABLED=false` sau khi bootstrap.
- JWT giữ role/permission tại thời điểm phát token; các thay đổi dữ liệu phân quyền có hiệu
  lực trong token mới sau khi đăng nhập/refresh. Access token đã phát giữ claims đến khi hết hạn.
- Tạo permission chỉ thêm vào danh mục quyền. API nghiệp vụ tương ứng vẫn cần kiểm tra
  permission (và scope nếu có) khi triển khai tính năng đó.

| HTTP | Error code | Nguyên nhân |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Body, field hoặc tham số không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Role không nằm trong danh sách được phép |
| 404 | `ROLE_NOT_FOUND` / `PERMISSION_NOT_FOUND` | Không có dữ liệu hoặc role đã xóa; gỡ mapping không tồn tại |
| 409 | `CONFLICT` | Trùng code/mapping, sửa role hệ thống trái quy tắc hoặc xóa permission đang được sử dụng |
