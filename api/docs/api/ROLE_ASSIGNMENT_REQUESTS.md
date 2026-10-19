# API Reference — Role Assignment Requests

Workflow đề xuất và phê duyệt cấp hoặc thu hồi role nghiệp vụ cho account. Mỗi request có `requestType`:

- `GRANT`: chỉ tạo `AccountRoleAssignment` khi được phê duyệt; bản ghi `PENDING`, `REJECTED` hoặc `CANCELLED` không tham gia tính quyền.
- `REVOKE`: chỉ thu hồi assignment đích (`targetAssignment`) khi được phê duyệt; trước đó role vẫn có hiệu lực.

---

## Endpoint access

| Endpoint | Permission | Mô tả |
|:---------|:-----------|:------|
| `GET /api/role-assignment-requests/available-roles` | `role.assignment.request` | Lấy danh sách role HR được phép đề xuất cùng chính sách duyệt và scope hợp lệ |
| `POST /api/role-assignment-requests` | `role.assignment.request` | Gửi đề xuất cấp role; tự duyệt role `HR_ASSIGNABLE` hoặc tạo `PENDING` với `OWNER_APPROVAL` |
| `GET /api/role-assignment-requests/revocable-assignments?accountId=` | `role.assignment.request` | Lấy các role assignment của một account mà đề xuất thu hồi có thể chọn |
| `POST /api/role-assignment-requests/revocations` | `role.assignment.request` | Gửi đề xuất thu hồi một role assignment; tự thu hồi role `HR_ASSIGNABLE` hoặc tạo `PENDING` với `OWNER_APPROVAL` |
| `GET /api/role-assignment-requests` | `role.assignment.request` hoặc `role.assignment.approve` | Lấy danh sách đề xuất có phân trang và có thể lọc theo trạng thái |
| `GET /api/role-assignment-requests/{requestId}` | `role.assignment.request` hoặc `role.assignment.approve` | Lấy chi tiết một đề xuất cấp role trong phạm vi được phép xem |
| `POST /api/role-assignment-requests/{requestId}/approve` | `role.assignment.approve` | Phê duyệt đề xuất `PENDING`: tạo role assignment (`GRANT`) hoặc thu hồi assignment đích (`REVOKE`) |
| `POST /api/role-assignment-requests/{requestId}/reject` | `role.assignment.approve` | Từ chối đề xuất `PENDING` kèm ghi chú bắt buộc |
| `POST /api/role-assignment-requests/{requestId}/cancel` | `role.assignment.request` | Người gửi hủy đề xuất của chính mình khi đề xuất còn `PENDING` |

Tất cả endpoint yêu cầu Bearer token. `HR_MANAGER` được seed `role.assignment.request`; `COMPANY_OWNER` được seed `role.assignment.approve`.

- Người có quyền đề xuất chỉ xem và hủy request do chính account của mình tạo.
- Người có quyền phê duyệt xem được toàn bộ request.
- Chỉ người tạo mới được hủy request và chỉ Company Owner mới được duyệt hoặc từ chối.
- Mọi thao tác xử lý chỉ áp dụng với request đang `PENDING`.

---

## GET `/api/role-assignment-requests/available-roles`

Trả danh sách không phân trang để HR chọn role khi lập đề xuất. Endpoint chỉ trả role chưa bị xóa mềm có `grantPolicy=HR_ASSIGNABLE` hoặc `OWNER_APPROVAL`; các role `AUTO` và `SYSTEM_ONLY` không xuất hiện vì workflow đề xuất không thể cấp chúng.

Kết quả sắp xếp theo tên role, sau đó theo code. Đây là danh mục nghiệp vụ chỉ đọc, không yêu cầu `rbac.manage` và không cho phép HR sửa role hoặc permission.

### Response `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "roleId": 8,
      "roleCode": "DIRECTOR",
      "roleName": "Giám đốc",
      "roleDescription": "Điều hành công ty và phê duyệt cuối các quyết định nghiệp vụ trên toàn công ty",
      "grantPolicy": "OWNER_APPROVAL",
      "requiresApproval": true,
      "allowedScopeTypes": ["COMPANY"]
    },
    {
      "roleId": 6,
      "roleCode": "PAYROLL_ACCOUNTANT",
      "roleName": "Kế toán tiền lương",
      "roleDescription": "Tính toán và kiểm tra bảng lương toàn công ty",
      "grantPolicy": "OWNER_APPROVAL",
      "requiresApproval": true,
      "allowedScopeTypes": ["COMPANY"]
    }
  ],
  "error": null
}
```

`allowedScopeTypes` được lấy từ cùng policy mà backend dùng để validate request:

- System role trả đúng scope cố định của role đó.
- Custom role đang hỗ trợ `SELF`, `COMPANY`, `ORG_UNIT` và `LOCATION`.
- `requiresApproval=true` tương ứng với `OWNER_APPROVAL`.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có `role.assignment.request` |

---

## Luồng theo chính sách cấp role

| `grantPolicy` của role | Đề xuất cấp (`GRANT`) | Đề xuất thu hồi (`REVOKE`) |
|:----------------------|:----------------------|:---------------------------|
| `HR_ASSIGNABLE` | Hệ thống tạo assignment ngay và trả request `APPROVED` để giữ audit thống nhất | Hệ thống thu hồi assignment ngay và trả request `APPROVED` |
| `OWNER_APPROVAL` | Tạo request `PENDING`; chỉ có assignment sau khi Company Owner duyệt | Tạo request `PENDING`; role chỉ bị thu hồi sau khi Company Owner duyệt |
| `AUTO` | Từ chối; role chỉ do workflow hệ thống tự gán | Từ chối |
| `SYSTEM_ONLY` | Từ chối; role chỉ được quản lý qua workflow chuyên biệt | Từ chối |

Các system role nghiệp vụ `HR_MANAGER`, `PAYROLL_ACCOUNTANT`, `PAYROLL_APPROVER` và `DIRECTOR` là `OWNER_APPROVAL`, nên đề xuất cấp hoặc thu hồi các role này luôn chờ Company Owner duyệt. Riêng `MANAGER` (Quản lý đơn vị, chỉ đọc trong scope `LOCATION` hoặc `ORG_UNIT`) là `HR_ASSIGNABLE`: đề xuất của HR được duyệt và tạo assignment ngay.

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

Mọi response request đều có thêm `requestType` (`GRANT`/`REVOKE`) và `targetAssignment` (assignment cần thu hồi, `null` với `GRANT`). Request `REVOKE` luôn có `assignment=null`.

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

## GET `/api/role-assignment-requests/revocable-assignments?accountId=`

Trả các assignment của account mà đề xuất thu hồi có thể chọn: chưa bị thu hồi, chưa hết hạn và thuộc role `HR_ASSIGNABLE` hoặc `OWNER_APPROVAL`. Người gửi đề xuất không cần `account.read`; employee của account phải nằm trong scope `role.assignment.request` của họ. Account không liên kết employee trả danh sách rỗng.

Response `200 OK` có `data` là mảng `AccountRoleAssignmentResponse` như trong [Account Role Assignments](./ACCOUNT_ROLE_ASSIGNMENTS.md#get-apiaccountsaccountidrole-assignments).

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có `role.assignment.request` hoặc employee nằm ngoài scope được giao |
| 404 | `ACCOUNT_NOT_FOUND` | Account không tồn tại |

---

## POST `/api/role-assignment-requests/revocations`

Gửi đề xuất thu hồi một role assignment, ví dụ khi nhân sự thôi kiêm nhiệm hoặc chuyển vị trí. Khi nhân sự nghỉ việc thì không cần đề xuất: [kết thúc làm việc](./EMPLOYEE.md#post-apiemployeesemployeeidemployment-end) tự thu hồi role.

### Request

```json
{
  "assignmentId": 31,
  "reason": "Thôi kiêm nhiệm Kế toán tiền lương từ tháng 11"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `assignmentId` | long | ✅ | Lấy từ `GET /api/role-assignment-requests/revocable-assignments`; assignment chưa bị thu hồi và chưa hết hạn |
| `reason` | string | ✅ | Không rỗng, tối đa 500 ký tự; khi role bị thu hồi, đây là `revocationReason` của assignment |

Request sao role, scope và thời gian hiệu lực từ assignment đích. Employee của account và phạm vi của assignment phải nằm trong scope `role.assignment.request` của người gửi. Account đang giữ `COMPANY_OWNER` hoặc `SYSTEM_ADMIN` chỉ người giữ cùng role mới được đề xuất thu hồi.

Khi role bị thu hồi (ngay với `HR_ASSIGNABLE`, hoặc khi Company Owner duyệt), hệ thống xử lý như [API thu hồi role](./ACCOUNT_ROLE_ASSIGNMENTS.md#post-apiaccountsaccountidrole-assignmentsassignmentidrevoke): ghi `revokedAt`, người thu hồi (người gửi khi tự duyệt, Company Owner khi duyệt), đóng `effectiveTo` về hôm nay nếu assignment đã bắt đầu và thu hồi toàn bộ refresh token của account. Khi duyệt, hệ thống kiểm tra lại assignment còn hiệu lực.

### Response `201 Created`

```json
{
  "success": true,
  "data": {
    "id": 42,
    "requestType": "REVOKE",
    "accountId": 208,
    "roleCode": "PAYROLL_ACCOUNTANT",
    "grantPolicy": "OWNER_APPROVAL",
    "status": "PENDING",
    "reason": "Thôi kiêm nhiệm Kế toán tiền lương từ tháng 11",
    "assignment": null,
    "targetAssignment": { "id": 31, "roleCode": "PAYROLL_ACCOUNTANT", "revokedAt": null }
  },
  "error": null
}
```

Ví dụ rút gọn; các field còn lại giống response tạo request ở trên.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu `assignmentId`, thiếu lý do hoặc lý do quá dài |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có quyền đề xuất, employee/phạm vi nằm ngoài scope được giao, hoặc account được bảo vệ |
| 404 | `ROLE_ASSIGNMENT_NOT_FOUND` | Assignment không tồn tại |
| 409 | `ROLE_ASSIGNMENT_NOT_ALLOWED` | Role `AUTO`/`SYSTEM_ONLY`, account không liên kết employee, hoặc assignment đã bị thu hồi/hết hạn |
| 409 | `ROLE_ASSIGNMENT_REQUEST_EXISTS` | Assignment đã có một đề xuất thu hồi đang `PENDING` |

---

## GET `/api/role-assignment-requests`

Lấy danh sách request có phân trang. Company Owner thấy toàn bộ; HR Manager chỉ thấy request do chính mình tạo.

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

`note` không bắt buộc, tối đa 500 ký tự. Khi thành công, request chuyển sang `APPROVED`:

- `GRANT`: hệ thống tạo `AccountRoleAssignment` và trả response có `assignment`.
- `REVOKE`: hệ thống thu hồi `targetAssignment` với lý do của request; response có `targetAssignment` đã thu hồi.

Ngoài lỗi xác thực/tài nguyên/validation giống endpoint tạo, endpoint có thể trả:

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 409 | `ROLE_ASSIGNMENT_REQUEST_ALREADY_PROCESSED` | Request không còn `PENDING` |
| 409 | `ROLE_ASSIGNMENT_EXISTS` | Dữ liệu hiện tại đã có assignment xung đột |
| 409 | `ROLE_ASSIGNMENT_NOT_ALLOWED` | Grant policy hiện tại không còn cho phép Owner phê duyệt, hoặc assignment đích của `REVOKE` đã bị thu hồi/hết hạn |

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

Request được khóa khi approve, reject hoặc cancel nên chỉ thao tác đầu tiên trên một request `PENDING` thành công. Database đồng thời chặn các request `GRANT` pending trùng phạm vi và chồng thời gian, và chặn hai request `REVOKE` pending cho cùng một assignment.

Role mới chỉ xuất hiện trong authorization snapshot khi assignment đã có hiệu lực theo ngày. JWT đã phát không tự đổi claims; account nhận role cần đăng nhập lại hoặc refresh token để nhận role và permission mới.
