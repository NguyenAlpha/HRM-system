# API Reference — Account Administration

Quản trị vòng đời tài khoản đăng nhập. Các endpoint này không tạo hồ sơ nhân viên và không cho admin đặt mật khẩu thay người dùng.

Account mới phải liên kết với một employee đã tồn tại. Hệ thống lấy email đăng nhập từ `Employee.workEmail`, tự gán role nền `EMPLOYEE` với scope `SELF`, tạo account `PENDING` và phát token để người nhận tự đặt mật khẩu.

Role nghiệp vụ bổ sung được quản lý qua [Account Role Assignments](./ACCOUNT_ROLE_ASSIGNMENTS.md), không truyền trong request tạo account.

`HR_STAFF` và `COMPANY_OWNER` được seed `account.provision`, nên có thể cấp account cho employee trong scope được giao. Permission này không cho phép xem danh sách account, reset mật khẩu, khóa hoặc mở lại account.

Director tiếp theo đi qua quy trình thông thường: HR tạo employee, cấp account và gửi đề xuất `DIRECTOR`; Company Owner duyệt qua [Role Assignment Requests](./ROLE_ASSIGNMENT_REQUESTS.md).

Company Owner đầu tiên phải được `SYSTEM_ADMIN` tạo qua workflow [Organization Company Owner Bootstrap](./ORGANIZATION_COMPANY_OWNER_ADMIN.md), không dùng endpoint account tổng quát.

Company Owner tạo HR Staff đầu tiên và các HR Staff tiếp theo bằng quy trình thông thường: tạo hồ sơ qua `POST /api/employees`, cấp account qua `POST /api/admin/accounts`, sau đó gán trực tiếp `HR_STAFF/COMPANY` qua [Account Role Assignments](./ACCOUNT_ROLE_ASSIGNMENTS.md).

---

## Endpoint access

| Endpoint | Permission | Mô tả |
|:---------|:-----------|:------|
| `POST /api/admin/accounts` | `account.provision` | Tạo account `PENDING` cho employee đã có hồ sơ, tự gán role nền `EMPLOYEE/SELF` và phát token kích hoạt |
| `GET /api/admin/accounts` | `account.read` | Lấy danh sách account có phân trang |
| `GET /api/admin/accounts/{accountId}` | `account.read` | Lấy thông tin chi tiết của một account |
| `POST /api/admin/accounts/{accountId}/invitations/resend` | `account.activation.manage` | Thu hồi lời mời cũ và phát lại token kích hoạt cho account `PENDING` |
| `POST /api/admin/accounts/{accountId}/password-reset` | `account.activation.manage` | Đưa account `ACTIVE` hoặc `LOCKED` về `PENDING` và phát token để người dùng tự đặt lại mật khẩu |
| `POST /api/admin/accounts/{accountId}/suspend` | `account.manage` | Vô hiệu hóa account và thu hồi toàn bộ refresh token |
| `POST /api/admin/accounts/{accountId}/activate` | `account.manage` | Kích hoạt lại account `DISABLED` hoặc mở khóa account `LOCKED` đã có mật khẩu |

Tất cả endpoint yêu cầu Bearer token. `COMPANY_OWNER` được seed sẵn toàn bộ permission trên. `HR_STAFF` chỉ được seed `account.provision` trong nhóm API này; các thao tác quản trị vòng đời account vẫn thuộc Company Owner. `SYSTEM_ADMIN` không được quản trị tài khoản nội bộ doanh nghiệp.

Raw activation token là credential bí mật và chỉ xuất hiện trong response của thao tác tạo, gửi lại lời mời hoặc reset mật khẩu. API không lưu hoặc ghi log raw token. Khi có hạ tầng email, lớp gửi thông báo sẽ tiếp nhận token này và API không cần trả nó cho frontend quản trị.

---

## POST `/api/admin/accounts`

Tạo account cho employee đã tồn tại. Đây là provisioning tài khoản, không phải tuyển nhân sự.

### Request

```json
{
  "employeeId": 125,
  "username": "an.nguyen"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `employeeId` | long | ✅ | Employee chưa bị xóa, đang `PROBATION` hoặc `ACTIVE`, chưa có account và đã có `workEmail` |
| `username` | string | ✅ | 3–50 ký tự; chỉ gồm chữ, số, `.`, `_`, `-`; duy nhất |

Email của account được lấy từ `Employee.workEmail`, trim và chuyển về chữ thường. Request không được tự truyền email hoặc role.

Employee phải nằm trong scope `account.provision` của account đang đăng nhập. Với `HR_STAFF` và `COMPANY_OWNER` mặc định, scope này là `COMPANY`; nếu permission được ủy quyền cho custom role có scope hẹp hơn, backend tiếp tục giới hạn theo phân công hiện hành của employee.

### Response `201 Created`

```json
{
  "success": true,
  "data": {
    "account": {
      "id": 208,
      "employeeId": 125,
      "username": "an.nguyen",
      "email": "an.nguyen@company.com",
      "status": "PENDING",
      "failedLoginCount": 0,
      "lockedUntil": null,
      "lastLoginAt": null,
      "createdAt": "2026-09-26T09:00:00Z",
      "updatedAt": "2026-09-26T09:00:00Z"
    },
    "invitation": {
      "accountId": 208,
      "activationToken": "raw-token-returned-once",
      "expiresAt": "2026-09-27T09:00:00Z"
    }
  },
  "error": null
}
```

Toàn bộ thao tác nằm trong một transaction:

1. Tạo account `PENDING` chưa có mật khẩu.
2. Gán role `EMPLOYEE` với scope `SELF`.
3. Tạo token kích hoạt dùng một lần.

Phần tạo account ở trên được triển khai bởi component provisioning nội bộ dùng chung. Endpoint này kiểm tra `account.provision` cùng scope của employee rồi chuyển `employeeId`, `username` và actor lấy từ Security Context vào component đó. Các workflow tổng hợp tái sử dụng cùng component, không gọi vòng qua `AccountAdminController` và không phải có thêm `account.provision` nếu permission chuyên biệt của workflow đã cho phép toàn bộ thao tác.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Request sai hoặc employee chưa có work email |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có `account.provision`, employee nằm ngoài scope được giao hoặc không tồn tại trong scope có thể truy cập |
| 404 | `EMPLOYEE_NOT_FOUND` | Employee không tồn tại hoặc đã bị xóa mềm |
| 409 | `EMPLOYEE_ACCOUNT_EXISTS` | Employee đã có account |
| 409 | `ACCOUNT_PROVISIONING_NOT_ALLOWED` | Employee đã nghỉ việc/chấm dứt/nghỉ hưu |
| 409 | `USERNAME_TAKEN` | Username đã tồn tại |
| 409 | `EMAIL_TAKEN` | Work email đã được account khác sử dụng |

---

## GET `/api/admin/accounts`

Lấy danh sách account có phân trang.

```http
GET /api/admin/accounts?page=0&size=20&sort=id,asc
```

Response `200 OK` dùng cấu trúc `Page<AccountResponse>` của Spring với các field `content`, `totalElements`, `totalPages`, `number` và `size`.

---

## GET `/api/admin/accounts/{accountId}`

Lấy chi tiết một account.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có `account.read` |
| 404 | `ACCOUNT_NOT_FOUND` | Account không tồn tại |

---

## POST `/api/admin/accounts/{accountId}/invitations/resend`

Phát token mới cho account `PENDING`. Token cũ chưa dùng sẽ bị thu hồi.

### Response `200 OK`

```json
{
  "success": true,
  "data": {
    "accountId": 208,
    "activationToken": "new-raw-token-returned-once",
    "expiresAt": "2026-09-27T10:00:00Z"
  },
  "error": null
}
```

Trả `409 ACCOUNT_ACTIVATION_NOT_ALLOWED` nếu account không còn ở trạng thái `PENDING`.

---

## POST `/api/admin/accounts/{accountId}/password-reset`

Khởi tạo reset mật khẩu cho account `ACTIVE` hoặc `LOCKED`:

- Đặt account về `PENDING` và xóa password hash cũ.
- Thu hồi toàn bộ refresh token.
- Phát activation token mới để người dùng tự đặt mật khẩu.

Admin không thể dùng endpoint này cho chính account đang đăng nhập; hãy dùng `POST /api/auth/change-password`.

Response giống endpoint gửi lại lời mời. Account `PENDING` phải dùng endpoint resend; account `DISABLED` phải được xem xét kích hoạt lại trước.

---

## POST `/api/admin/accounts/{accountId}/suspend`

Chuyển account `ACTIVE` hoặc `LOCKED` sang `DISABLED` và thu hồi toàn bộ refresh token. Thao tác có tính idempotent nếu account đã `DISABLED`.

Không được tự suspend account đang đăng nhập và không dùng endpoint này để hủy account `PENDING`.

### Response `200 OK`

Trả `AccountResponse` với `status = DISABLED`.

---

## POST `/api/admin/accounts/{accountId}/activate`

Kích hoạt lại account `DISABLED` hoặc `LOCKED` đã có mật khẩu, đồng thời xóa trạng thái khóa và bộ đếm đăng nhập sai. Nếu account đã `ACTIVE`, endpoint trả thành công mà không thay đổi dữ liệu.

Endpoint không được bỏ qua bước đặt mật khẩu của account `PENDING`. Account `PENDING` chỉ có thể chuyển sang `ACTIVE` qua:

```http
POST /api/account-activations/{token}/complete
```

### Lỗi trạng thái

Các chuyển trạng thái không hợp lệ trả:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ACCOUNT_STATUS_TRANSITION_NOT_ALLOWED",
    "message": "Mô tả chuyển trạng thái bị từ chối",
    "field": null
  }
}
```

---

## Ghi chú bảo mật

- Actor luôn lấy từ JWT/Security Context; request không có `createdByAccountId` hoặc `grantedByAccountId`.
- Service provisioning nội bộ không phải API công khai và không tự quyết định authorization; controller/workflow gọi nó phải được bảo vệ bằng permission phù hợp và giữ transaction bao trùm.
- Admin không nhập, xem hoặc lưu mật khẩu của người dùng.
- Role nền `EMPLOYEE/SELF` do hệ thống tự gán; role nghiệp vụ khác thuộc API role assignment riêng.
- Access token đã phát là JWT stateless và có thể còn hiệu lực đến `exp`; refresh token bị thu hồi ngay khi suspend/reset.
- Không đưa activation token vào log, analytics, ticket hoặc kênh chat công khai.
