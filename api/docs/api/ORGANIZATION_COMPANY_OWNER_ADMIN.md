# API Reference — Organization Company Owner Bootstrap

Khởi tạo Chủ sở hữu doanh nghiệp đầu tiên từ tài khoản quản trị hệ thống. Đây là cầu nối duy nhất từ lớp vận hành nền tảng sang lớp quản trị nội bộ doanh nghiệp; `SYSTEM_ADMIN` không được dùng các API quản trị account, role hoặc nghiệp vụ của công ty.

---

## Endpoint access

| Endpoint | Permission |
|:---------|:-----------|
| `POST /api/admin/organization/company-owner` | `organization.company_owner.bootstrap` |

Endpoint yêu cầu Bearer token. Permission này có `assignmentPolicy = SYSTEM_ONLY` và chỉ được seed cho system role `SYSTEM_ADMIN`; Company Owner, Director và custom role không thể tự nhận permission này qua API RBAC.

Đây là permission duy nhất cần cho toàn bộ workflow. Các bước tạo employee, account, activation token và role assignment dùng component nội bộ nên `SYSTEM_ADMIN` không cần `account.manage` hoặc `account.role.assign`.

---

## POST `/api/admin/organization/company-owner`

Tạo đồng thời hồ sơ employee tối thiểu, account đăng nhập và assignment `COMPANY_OWNER/COMPANY`.

### Request

```json
{
  "employee": {
    "employeeCode": "OWNER001",
    "fullName": "Nguyen Van Chu",
    "workEmail": "owner@company.com",
    "phone": "0901234567",
    "hireDate": "2026-09-26"
  },
  "account": {
    "username": "company.owner"
  },
  "effectiveFrom": "2026-09-26",
  "ownershipReason": "Initial company owner appointed under the service agreement"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `employee.employeeCode` | string | ✅ | Tối đa 30 ký tự; chỉ `A-Z`, `0-9`, `_`, `-`; duy nhất không phân biệt hoa thường |
| `employee.fullName` | string | ✅ | Không rỗng, tối đa 200 ký tự |
| `employee.workEmail` | string | ✅ | Email hợp lệ, tối đa 100 ký tự, duy nhất không phân biệt hoa thường |
| `employee.phone` | string | ❌ | Tối đa 20 ký tự |
| `employee.hireDate` | date | ✅ | Ngày bắt đầu làm việc trong hồ sơ HRM |
| `account.username` | string | ✅ | 3–50 ký tự; chỉ chữ, số, `.`, `_`, `-`; duy nhất |
| `effectiveFrom` | date | ✅ | Ngày quyền `COMPANY_OWNER` bắt đầu hiệu lực; không được trước `employee.hireDate` |
| `ownershipReason` | string | ✅ | Lý do cấp quyền sở hữu, tối đa 500 ký tự |

Employee được tạo ở trạng thái `ACTIVE`; account ở trạng thái `PENDING` và chưa có mật khẩu. Email account lấy từ `employee.workEmail`, được trim và chuyển thành chữ thường.

### Transaction và invariant

Toàn bộ workflow nằm trong một transaction:

```text
Employee ACTIVE
    → Account PENDING
    → EMPLOYEE / SELF
    → Activation token
    → COMPANY_OWNER / COMPANY
```

Nếu bất kỳ bước nào thất bại, toàn bộ dữ liệu vừa tạo được rollback. Các component provisioning dùng chung yêu cầu transaction đã tồn tại (`MANDATORY`) nên không thể được gọi riêng lẻ và commit từng phần.

Role `COMPANY_OWNER` được pessimistic lock trong lúc kiểm tra và gán. Hệ thống từ chối bootstrap nếu đã tồn tại bất kỳ assignment `COMPANY_OWNER` chưa bị thu hồi, kể cả assignment đang có hiệu lực trong tương lai. Vì vậy hai request đồng thời không thể tạo hai owner đầu tiên.

### Response `201 Created`

```json
{
  "success": true,
  "data": {
    "employeeId": 125,
    "employeeCode": "OWNER001",
    "fullName": "Nguyen Van Chu",
    "accountProvisioning": {
      "account": {
        "id": 208,
        "employeeId": 125,
        "username": "company.owner",
        "email": "owner@company.com",
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
    "companyOwnerRoleAssignment": {
      "id": 31,
      "accountId": 208,
      "roleId": 9,
      "roleCode": "COMPANY_OWNER",
      "roleName": "Chủ sở hữu doanh nghiệp",
      "scopeType": "COMPANY",
      "organizationUnitId": null,
      "workLocationId": null,
      "effectiveFrom": "2026-09-26",
      "effectiveTo": null,
      "grantedByAccountId": 1,
      "reason": "Initial company owner appointed under the service agreement",
      "createdAt": "2026-09-26T09:00:00Z",
      "revokedByAccountId": null,
      "revokedAt": null,
      "revocationReason": null
    }
  },
  "error": null
}
```

Raw activation token chỉ xuất hiện trong response này. System admin phải chuyển token qua kênh an toàn để Company Owner tự đặt mật khẩu bằng [API activation](./AUTH.md). Sau khi đăng nhập, Company Owner có thể dùng workflow [khởi tạo Director](./ORGANIZATION_DIRECTOR_ADMIN.md) và các API quản trị nội bộ đã được cấp permission.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Request không hợp lệ hoặc `effectiveFrom` trước `employee.hireDate` |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có `organization.company_owner.bootstrap` |
| 409 | `COMPANY_OWNER_ALREADY_EXISTS` | Đã tồn tại assignment `COMPANY_OWNER` chưa bị thu hồi |
| 409 | `EMPLOYEE_CODE_TAKEN` | Mã employee đã tồn tại |
| 409 | `EMAIL_TAKEN` | Work email đã tồn tại ở employee hoặc account |
| 409 | `USERNAME_TAKEN` | Username đã tồn tại |

---

## Phạm vi không thực hiện

Workflow này không:

- Cấp cho `SYSTEM_ADMIN` quyền quản trị nội bộ doanh nghiệp.
- Cho phép chọn role hoặc permission tùy ý trong request.
- Đặt mật khẩu thay Company Owner.
- Tạo Director, HR Staff hoặc các nhân sự cấp dưới.
- Thực hiện chuyển giao hay thu hồi quyền sở hữu; các thao tác đó cần workflow riêng có audit và quy tắc chống mất owner.
