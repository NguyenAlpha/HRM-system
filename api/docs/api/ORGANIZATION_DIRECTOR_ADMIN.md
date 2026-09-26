# API Reference — Organization Director Administration

Khởi tạo người đứng đầu doanh nghiệp khi người này chưa có hồ sơ employee trong HRM. Đây là workflow thiết lập quyền điều hành, không phải quy trình tuyển dụng hoặc onboarding nhân sự đầy đủ.

---

## Endpoint access

| Endpoint | Permission |
|:---------|:-----------|
| `POST /api/admin/organization/director` | `organization.director.provision` |

Endpoint yêu cầu Bearer token. `SYSTEM_ADMIN` và `COMPANY_OWNER` được seed permission `organization.director.provision` cùng các permission nội bộ `account.manage` và `account.role.assign` cần để hoàn thành workflow trong giai đoạn chuyển đổi quyền quản trị.

`DIRECTOR` không được cấp permission này nên không thể tự tạo người kế nhiệm.

---

## POST `/api/admin/organization/director`

Tạo đồng thời employee tối thiểu, account đăng nhập và quyền phê duyệt cấp điều hành.

### Request

```json
{
  "employee": {
    "employeeCode": "DIR001",
    "fullName": "Nguyen Van An",
    "workEmail": "director@company.com",
    "phone": "0901234567",
    "hireDate": "2026-10-01"
  },
  "account": {
    "username": "director"
  },
  "effectiveFrom": "2026-10-01",
  "appointmentReason": "Appointed as company director under decision 01/2026"
}
```

### Ràng buộc

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `employee.employeeCode` | string | ✅ | Tối đa 30 ký tự; chỉ `A-Z`, `0-9`, `_`, `-`; duy nhất không phân biệt hoa thường |
| `employee.fullName` | string | ✅ | Không rỗng, tối đa 200 ký tự |
| `employee.workEmail` | string | ✅ | Email hợp lệ, tối đa 100 ký tự, duy nhất không phân biệt hoa thường |
| `employee.phone` | string | ❌ | Tối đa 20 ký tự |
| `employee.hireDate` | date | ✅ | Ngày bắt đầu làm việc trong hồ sơ HRM |
| `account.username` | string | ✅ | 3–50 ký tự; chỉ chữ, số, `.`, `_`, `-`; duy nhất |
| `effectiveFrom` | date | ✅ | Ngày role `DIRECTOR` bắt đầu hiệu lực; không được trước `employee.hireDate` |
| `appointmentReason` | string | ✅ | Không rỗng, tối đa 500 ký tự |

Email được trim và chuẩn hóa về chữ thường. Employee được tạo ở trạng thái `ACTIVE` vì đây là người đang đứng đầu doanh nghiệp, không phải ứng viên hoặc nhân viên thử việc.

### Transaction

Toàn bộ workflow chạy trong một transaction:

```text
Employee ACTIVE
    → Account PENDING
    → EMPLOYEE / SELF
    → Activation token
    → DIRECTOR / COMPANY
```

Nếu bất kỳ bước nào thất bại, toàn bộ dữ liệu vừa tạo được rollback.

Hệ thống không cho hai assignment `DIRECTOR` chồng thời gian. Có thể chuẩn bị người kế nhiệm trong tương lai chỉ khi assignment hiện tại đã có `effectiveTo` không chồng với `effectiveFrom` mới.

### Response `201 Created`

```json
{
  "success": true,
  "data": {
    "employeeId": 125,
    "employeeCode": "DIR001",
    "fullName": "Nguyen Van An",
    "accountProvisioning": {
      "account": {
        "id": 208,
        "employeeId": 125,
        "username": "director",
        "email": "director@company.com",
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
    "directorRoleAssignment": {
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
      "reason": "Appointed as company director under decision 01/2026",
      "createdAt": "2026-09-26T09:00:00Z",
      "revokedByAccountId": null,
      "revokedAt": null,
      "revocationReason": null
    }
  },
  "error": null
}
```

Raw activation token chỉ xuất hiện một lần trong response để quản trị viên chuyển qua kênh an toàn. Người nhận dùng [API activation](./AUTH.md) để tự đặt mật khẩu.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Request không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có đủ permission provisioning/account/role assignment |
| 409 | `EMPLOYEE_CODE_TAKEN` | Mã employee đã tồn tại |
| 409 | `EMAIL_TAKEN` | Work email đã tồn tại ở employee hoặc account |
| 409 | `USERNAME_TAKEN` | Username đã tồn tại |
| 409 | `ROLE_ASSIGNMENT_EXISTS` | Đã có `DIRECTOR` chồng khoảng hiệu lực |

---

## Phạm vi không thực hiện

Workflow này không tạo:

- Phân công phòng ban, vị trí, địa điểm hoặc ca làm việc.
- Lương và phụ cấp.
- Hợp đồng lao động.
- Hồ sơ nhạy cảm như CCCD, thuế hoặc tài khoản ngân hàng.

Các dữ liệu trên thuộc controller nghiệp vụ nhân sự riêng và có thể bổ sung sau khi người đứng đầu đã được khởi tạo.
