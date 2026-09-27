# API Reference — Role Assignment Requests

Workflow đề xuất và phê duyệt cấp role nghiệp vụ cho account. Request chỉ tạo `AccountRoleAssignment` khi được phê duyệt; bản ghi `PENDING`, `REJECTED` hoặc `CANCELLED` không tham gia tính quyền.

---

## Endpoint access

| Endpoint | Permission |
|:---------|:-----------|
| `POST /api/role-assignment-requests` | `role.assignment.request` |
| `GET /api/role-assignment-requests` | `role.assignment.request` hoặc `role.assignment.approve` |
| `GET /api/role-assignment-requests/{requestId}` | `role.assignment.request` hoặc `role.assignment.approve` |
| `POST /api/role-assignment-requests/{requestId}/approve` | `role.assignment.approve` |
| `POST /api/role-assignment-requests/{requestId}/reject` | `role.assignment.approve` |
| `POST /api/role-assignment-requests/{requestId}/cancel` | `role.assignment.request` |

Tất cả endpoint yêu cầu Bearer token. `HR_STAFF` được seed `role.assignment.request`; `COMPANY_OWNER` được seed `role.assignment.approve`.

- Người có quyền đề xuất chỉ xem và hủy request do chính account của mình tạo.
- Người có quyền phê duyệt xem được toàn bộ request.
- Chỉ người tạo mới được hủy request và chỉ Company Owner mới được duyệt hoặc từ chối.
- Mọi thao tác xử lý chỉ áp dụng với request đang `PENDING`.

---

## Luồng theo chính sách cấp role

| `grantPolicy` của role | Kết quả khi gửi đề xuất |
|:----------------------|:------------------------|
| `HR_ASSIGNABLE` | Hệ thống tạo assignment ngay và trả request `APPROVED` để giữ audit thống nhất |
| `OWNER_APPROVAL` | Tạo request `PENDING`; chỉ có assignment sau khi Company Owner duyệt |
| `AUTO` | Từ chối; role chỉ do workflow hệ thống tự gán |
| `SYSTEM_ONLY` | Từ chối; role chỉ được quản lý qua workflow chuyên biệt |

Việc phân nhánh nằm ở service theo `grantPolicy` được seed cho role, không dựa vào tên role do client tự suy đoán. Khi phê duyệt, hệ thống kiểm tra lại account, role, scope, thời gian và các assignment đang tồn tại để tránh dữ liệu đã thay đổi trong lúc request chờ xử lý.

---

## POST `/api/role-assignment-requests`

Gửi đề xuất cấp role cho account đã tồn tại và đã liên kết với employee.

### Request

```json
{
  "accountId": 208,
  "roleCode": "DIRECTOR",
  "scopeType": "COMPANY",
  "organizationUnitId": null,
  "workLocationId": null,
  "effectiveFrom": "2026-10-01",
  "effectiveTo": null,
  "reason": "Đề xuất bổ nhiệm Giám đốc"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `accountId` | long | ✅ | Account phải tồn tại, không `DISABLED` và đã liên kết employee |
| `roleCode` | string | ✅ | Tối đa 50 ký tự, định dạng `A-Z`, `0-9`, `_` |
| `scopeType` | enum | ✅ | `SELF`, `COMPANY`, `ORG_UNIT`, `LOCATION` |
| `organizationUnitId` | long | Theo scope | Bắt buộc với `ORG_UNIT`, phải null với scope khác |
| `workLocationId` | long | Theo scope | Bắt buộc với `LOCATION`, phải null với scope khác |
| `effectiveFrom` | date | ✅ | Không được trước ngày vào làm của employee |
| `effectiveTo` | date | ❌ | Không được trước `effectiveFrom` |
| `reason` | string | ✅ | Không rỗng, tối đa 500 ký tự |

Employee đích và phạm vi đích phải nằm trong scope `role.assignment.request` của người gửi. Các system role vẫn tuân theo scope cố định được mô tả trong [Account Role Assignments](./ACCOUNT_ROLE_ASSIGNMENTS.md).

### Response `201 Created`

Với role `OWNER_APPROVAL`, response có `status=PENDING` và `assignment=null`:

```json
{
  "success": true,
  "data": {
    "id": 41,
    "accountId": 208,
    "employeeId": 125,
    "employeeCode": "EMP001",
    "employeeName": "Nguyen Van A",
    "roleId": 8,
    "roleCode": "DIRECTOR",
    "roleName": "Giám đốc",
    "grantPolicy": "OWNER_APPROVAL",
    "scopeType": "COMPANY",
    "organizationUnitId": null,
    "organizationUnitName": null,
    "workLocationId": null,
    "workLocationName": null,
    "effectiveFrom": "2026-10-01",
    "effectiveTo": null,
    "reason": "Đề xuất bổ nhiệm Giám đốc",
    "status": "PENDING",
    "requestedByAccountId": 209,
    "requestedByUsername": "hr.staff",
    "requestedAt": "2026-09-27T03:00:00Z",
    "reviewedByAccountId": null,
    "reviewedByUsername": null,
    "reviewedAt": null,
    "reviewNote": null,
    "cancelledByAccountId": null,
    "cancelledByUsername": null,
    "cancelledAt": null,
    "cancellationReason": null,
    "updatedAt": "2026-09-27T03:00:00Z",
    "assignment": null
  },
  "error": null
}
```

Với role `HR_ASSIGNABLE`, cùng response sẽ có `status=APPROVED`, thông tin người/thời điểm duyệt tự động và `assignment` là `AccountRoleAssignmentResponse` vừa được tạo.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Body, scope hoặc khoảng hiệu lực không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có quyền đề xuất hoặc employee/phạm vi nằm ngoài scope được giao |
| 404 | `ACCOUNT_NOT_FOUND` | Account đích không tồn tại |
| 404 | `ROLE_NOT_FOUND` | Role không tồn tại |
| 404 | `ORGANIZATION_UNIT_NOT_FOUND` | Đơn vị không tồn tại hoặc không active |
| 404 | `LOCATION_NOT_FOUND` | Địa điểm không tồn tại hoặc không active |
| 409 | `ROLE_ASSIGNMENT_EXISTS` | Đã có assignment chồng thời gian |
| 409 | `ROLE_ASSIGNMENT_REQUEST_EXISTS` | Đã có request `PENDING` trùng account, role, scope và chồng thời gian |
| 409 | `ROLE_ASSIGNMENT_NOT_ALLOWED` | Account đích hoặc chính sách role không cho phép workflow này |

---

## GET `/api/role-assignment-requests`

Lấy danh sách request có phân trang. Company Owner thấy toàn bộ; HR Staff chỉ thấy request do chính mình tạo.

### Query params

| Param | Mặc định | Mô tả |
|:------|:---------|:------|
| `status` | Không lọc | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` |
| `page` | `0` | Trang bắt đầu từ 0 |
| `size` | `20` | Số phần tử mỗi trang |
| `sort` | `requestedAt,desc` | Thuộc tính và chiều sắp xếp theo chuẩn Spring Pageable |

Response `200 OK` có `data` là Page; mỗi phần tử có cấu trúc giống response tạo request.

---

## GET `/api/role-assignment-requests/{requestId}`

Lấy chi tiết một request. Người đề xuất chỉ đọc được request của mình; người phê duyệt đọc được mọi request.

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Request nằm ngoài quyền xem |
| 404 | `ROLE_ASSIGNMENT_REQUEST_NOT_FOUND` | Request không tồn tại |

---

## POST `/api/role-assignment-requests/{requestId}/approve`

Company Owner phê duyệt request `OWNER_APPROVAL` đang `PENDING`.

```json
{
  "note": "Đồng ý theo quyết định bổ nhiệm số 15/2026"
}
```

`note` không bắt buộc, tối đa 500 ký tự. Khi thành công, hệ thống tạo `AccountRoleAssignment`, chuyển request sang `APPROVED` và trả response có `assignment`.

Ngoài lỗi xác thực/tài nguyên/validation giống endpoint tạo, endpoint có thể trả:

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 409 | `ROLE_ASSIGNMENT_REQUEST_ALREADY_PROCESSED` | Request không còn `PENDING` |
| 409 | `ROLE_ASSIGNMENT_EXISTS` | Dữ liệu hiện tại đã có assignment xung đột |
| 409 | `ROLE_ASSIGNMENT_NOT_ALLOWED` | Grant policy hiện tại không còn cho phép Owner phê duyệt |

---

## POST `/api/role-assignment-requests/{requestId}/reject`

Company Owner từ chối request `PENDING`.

```json
{
  "note": "Chưa đủ hồ sơ bổ nhiệm"
}
```

`note` bắt buộc, không rỗng, tối đa 500 ký tự. Request chuyển sang `REJECTED`; không tạo assignment.

---

## POST `/api/role-assignment-requests/{requestId}/cancel`

Người đã gửi hủy request của chính mình khi request còn `PENDING`.

```json
{
  "reason": "Thông tin đề xuất cần được điều chỉnh"
}
```

`reason` bắt buộc, không rỗng, tối đa 500 ký tự. Request chuyển sang `CANCELLED`; không tạo assignment. Account khác có quyền đề xuất nhưng không phải người tạo sẽ nhận `403 FORBIDDEN`.

---

## Đồng thời và hiệu lực quyền

Request được khóa khi approve, reject hoặc cancel nên chỉ thao tác đầu tiên trên một request `PENDING` thành công. Database đồng thời chặn các request pending trùng phạm vi và chồng thời gian.

Role mới chỉ xuất hiện trong authorization snapshot khi assignment đã có hiệu lực theo ngày. JWT đã phát không tự đổi claims; account nhận role cần đăng nhập lại hoặc refresh token để nhận role và permission mới.
