# API Reference — Account Role Assignments

Gán role nghiệp vụ cho account theo phạm vi và thời gian hiệu lực. API này không tạo role, không chỉnh permission của role và không cấp permission trực tiếp cho account.

---

## Endpoint access

| Endpoint | Permission |
|:---------|:-----------|
| `GET /api/admin/accounts/{accountId}/role-assignments` | `account.read` |
| `POST /api/admin/accounts/{accountId}/role-assignments` | `account.role.assign` |
| `POST /api/admin/accounts/{accountId}/role-assignments/{assignmentId}/revoke` | `account.role.assign` |

Tất cả endpoint yêu cầu Bearer token. `COMPANY_OWNER` được seed sẵn các permission trên. `SYSTEM_ADMIN` không được gán hoặc thu hồi role nghiệp vụ. Actor cấp hoặc thu hồi role luôn được lấy từ JWT/Security Context, không nhận từ request.

---

## Chính sách system role

| Role | Scope được phép | Ghi chú |
|:-----|:----------------|:--------|
| `EMPLOYEE` | `SELF` | Hệ thống tự gán khi tạo account; API này không được gán/thu hồi |
| `TEAM_LEAD` | `ORG_UNIT` | Bắt buộc `organizationUnitId` |
| `WAREHOUSE_SUPERVISOR` | `LOCATION` | Bắt buộc `workLocationId` |
| `BRANCH_MANAGER` | `LOCATION` | Bắt buộc `workLocationId` |
| `HR_STAFF` | `COMPANY` | Không truyền ID phạm vi |
| `PAYROLL_ACCOUNTANT` | `COMPANY` | Không truyền ID phạm vi |
| `PAYROLL_APPROVER` | `COMPANY` | Không truyền ID phạm vi |
| `DIRECTOR` | `COMPANY` | Chỉ một assignment được phép hiệu lực trong cùng khoảng thời gian trên toàn doanh nghiệp |
| `COMPANY_OWNER` | `COMPANY` | Không được gán/thu hồi qua API này; phải dùng workflow quản lý quyền sở hữu riêng |
| `SYSTEM_ADMIN` | — | Không được quản lý qua API role nghiệp vụ này |

Role tùy chỉnh không bị giới hạn bởi bảng policy trên nhưng vẫn phải tuân thủ cấu trúc scope. Account nhận role nghiệp vụ phải liên kết với employee và không được ở trạng thái `DISABLED`.

Workflow bootstrap được phép gọi đường gán role nội bộ cho `DIRECTOR` hoặc `COMPANY_OWNER` sau khi chính workflow đã kiểm tra permission chuyên biệt. Đường nội bộ này không phải endpoint và không làm cho API role assignment tổng quát được phép gán `COMPANY_OWNER`.

Để khởi tạo đồng thời employee, account và role `DIRECTOR`, xem [Organization Director Administration](./ORGANIZATION_DIRECTOR_ADMIN.md).

### Quy tắc scope

| Scope | `organizationUnitId` | `workLocationId` |
|:------|:---------------------|:-----------------|
| `SELF` | Phải null | Phải null |
| `COMPANY` | Phải null | Phải null |
| `ORG_UNIT` | Bắt buộc, đơn vị phải active | Phải null |
| `LOCATION` | Phải null | Bắt buộc, địa điểm phải active |

---

## GET `/api/admin/accounts/{accountId}/role-assignments`

Lấy toàn bộ lịch sử role assignment của account, gồm assignment hiện hành, tương lai, hết hạn và đã bị thu hồi. Kết quả sắp xếp theo thời điểm tạo giảm dần.

### Response `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "id": 31,
      "accountId": 208,
      "roleId": 8,
      "roleCode": "DIRECTOR",
      "roleName": "Giám đốc",
      "scopeType": "COMPANY",
      "organizationUnitId": null,
      "workLocationId": null,
      "effectiveFrom": "2026-10-01",
      "effectiveTo": null,
      "grantedByAccountId": 1,
      "reason": "Appointed as company director",
      "createdAt": "2026-09-26T09:00:00Z",
      "revokedByAccountId": null,
      "revokedAt": null,
      "revocationReason": null
    }
  ],
  "error": null
}
```

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có `account.read` |
| 404 | `ACCOUNT_NOT_FOUND` | Account không tồn tại |

---

## POST `/api/admin/accounts/{accountId}/role-assignments`

Gán thêm một role nghiệp vụ cho account.

### Ví dụ gán `DIRECTOR`

```json
{
  "roleCode": "DIRECTOR",
  "scopeType": "COMPANY",
  "organizationUnitId": null,
  "workLocationId": null,
  "effectiveFrom": "2026-10-01",
  "effectiveTo": null,
  "reason": "Appointed as company director"
}
```

### Ví dụ gán `TEAM_LEAD`

```json
{
  "roleCode": "TEAM_LEAD",
  "scopeType": "ORG_UNIT",
  "organizationUnitId": 12,
  "workLocationId": null,
  "effectiveFrom": "2026-10-01",
  "effectiveTo": null,
  "reason": "Team lead appointment decision 15/2026"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `roleCode` | string | ✅ | Tối đa 50 ký tự, định dạng `A-Z`, `0-9`, `_`; role phải active |
| `scopeType` | enum | ✅ | `SELF`, `COMPANY`, `ORG_UNIT`, `LOCATION` |
| `organizationUnitId` | long | Theo scope | Chỉ dùng với `ORG_UNIT` |
| `workLocationId` | long | Theo scope | Chỉ dùng với `LOCATION` |
| `effectiveFrom` | date | ✅ | Ngày bắt đầu hiệu lực |
| `effectiveTo` | date | ❌ | Không được trước `effectiveFrom` |
| `reason` | string | ✅ | Không rỗng, tối đa 500 ký tự |

### Response `201 Created`

Trả một `AccountRoleAssignmentResponse` như cấu trúc trong endpoint danh sách.

### Ràng buộc chống trùng

- Cùng account, role, scope và đối tượng phạm vi không được có khoảng hiệu lực chồng nhau.
- `DIRECTOR/COMPANY` không được chồng thời gian với assignment `DIRECTOR` của bất kỳ account nào khác.
- Kiểm tra được tuần tự hóa bằng pessimistic lock trên account và role để tránh hai request đồng thời cùng vượt qua validation.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Request, scope hoặc khoảng hiệu lực không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có `account.role.assign` |
| 404 | `ACCOUNT_NOT_FOUND` | Account không tồn tại |
| 404 | `ROLE_NOT_FOUND` | Role không tồn tại, đã xóa hoặc inactive |
| 404 | `ORGANIZATION_UNIT_NOT_FOUND` | Đơn vị không tồn tại hoặc inactive |
| 404 | `LOCATION_NOT_FOUND` | Địa điểm không tồn tại hoặc inactive |
| 409 | `ROLE_ASSIGNMENT_EXISTS` | Assignment bị trùng/chồng thời gian |
| 409 | `ROLE_ASSIGNMENT_NOT_ALLOWED` | Account hoặc role không được phép gán qua API này |

---

## POST `/api/admin/accounts/{accountId}/role-assignments/{assignmentId}/revoke`

Thu hồi role mà không xóa lịch sử assignment.

### Request

```json
{
  "reason": "Appointment decision was superseded"
}
```

`reason` bắt buộc, tối đa 500 ký tự. `revokedByAccountId` được lấy từ JWT.

### Xử lý

- Ghi `revokedAt`, `revokedByAccountId`, `revocationReason`.
- Assignment mất hiệu lực ngay trong authorization query.
- Nếu assignment đã bắt đầu và chưa kết thúc, `effectiveTo` được đóng bằng ngày hiện tại để lịch sử dễ đọc.
- Thu hồi toàn bộ refresh token của account để phiên tiếp theo phải xác thực lại quyền.
- Gọi lại với assignment đã thu hồi trả chính assignment hiện tại và không ghi đè audit cũ.

Role `EMPLOYEE` và `SYSTEM_ADMIN` không được thu hồi qua API này.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu lý do hoặc lý do quá dài |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có `account.role.assign` |
| 404 | `ROLE_ASSIGNMENT_NOT_FOUND` | Assignment không tồn tại hoặc không thuộc account trên URL |
| 409 | `ROLE_ASSIGNMENT_NOT_ALLOWED` | Role được hệ thống bảo vệ |

---

## Hiệu lực JWT

JWT access token đã phát là stateless nên quyền cũ có thể còn trong claims đến thời điểm `exp`. Khi thu hồi role, hệ thống revoke toàn bộ refresh token; sau khi access token hết hạn, người dùng phải đăng nhập lại và nhận authorization snapshot mới.
