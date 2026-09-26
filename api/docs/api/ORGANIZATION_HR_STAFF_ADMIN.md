# API Reference — Organization HR Staff Bootstrap

Khởi tạo HR Staff đầu tiên khi doanh nghiệp chưa có người làm nghiệp vụ nhân sự. Workflow do Company Owner thực hiện để phá vòng phụ thuộc: chưa có HR Staff thì chưa có ai tạo hồ sơ nhân viên đầu tiên cho bộ phận HR.

---

## Endpoint access

| Endpoint | Permission |
|:---------|:-----------|
| `POST /api/admin/organization/hr-staff` | `organization.hr_staff.bootstrap` |

Endpoint yêu cầu Bearer token. `COMPANY_OWNER` được seed permission này; `SYSTEM_ADMIN`, `DIRECTOR` và `HR_STAFF` không có quyền bootstrap HR Staff.

Đây là permission duy nhất cần cho workflow. Các bước tạo employee, account và role assignment gọi component nội bộ nên không yêu cầu đồng thời `employee.manage`, `account.manage` hoặc `account.role.assign`.

---

## POST `/api/admin/organization/hr-staff`

Tạo đồng thời employee tối thiểu, account đăng nhập và assignment `HR_STAFF/COMPANY` đầu tiên.

### Request

```json
{
  "employee": {
    "employeeCode": "HR001",
    "fullName": "Tran Thi Ha",
    "workEmail": "ha.tran@company.com",
    "phone": "0901234567",
    "hireDate": "2026-09-26"
  },
  "account": {
    "username": "ha.tran"
  },
  "effectiveFrom": "2026-09-26",
  "appointmentReason": "Appointed as the first HR staff member"
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
| `effectiveFrom` | date | ✅ | Ngày role `HR_STAFF` bắt đầu hiệu lực; không được trước `employee.hireDate` |
| `appointmentReason` | string | ✅ | Lý do bổ nhiệm, tối đa 500 ký tự |

Employee được tạo ở trạng thái `ACTIVE`. Account được tạo ở trạng thái `PENDING`, chưa có mật khẩu và sử dụng work email đã chuẩn hóa từ employee.

### Transaction và giới hạn bootstrap

```text
Employee ACTIVE
    → Account PENDING
    → EMPLOYEE / SELF
    → Activation token
    → HR_STAFF / COMPANY
```

Toàn bộ workflow chạy trong một transaction. Nếu một bước thất bại, employee, account, token và các role assignment vừa tạo đều được rollback.

Role `HR_STAFF` được pessimistic lock khi kiểm tra. Endpoint từ chối nếu đã có bất kỳ assignment `HR_STAFF` chưa bị thu hồi, kể cả assignment bắt đầu trong tương lai. Điều này chỉ giới hạn workflow bootstrap đầu tiên; sau đó doanh nghiệp vẫn có thể bổ nhiệm nhiều HR Staff qua quy trình thông thường:

1. HR Staff hiện tại tạo hồ sơ employee.
2. Company Owner tạo account cho employee.
3. Company Owner gán thêm role `HR_STAFF/COMPANY` qua API role assignment.

### Response `201 Created`

```json
{
  "success": true,
  "data": {
    "employeeId": 126,
    "employeeCode": "HR001",
    "fullName": "Tran Thi Ha",
    "accountProvisioning": {
      "account": {
        "id": 209,
        "employeeId": 126,
        "username": "ha.tran",
        "email": "ha.tran@company.com",
        "status": "PENDING",
        "failedLoginCount": 0,
        "lockedUntil": null,
        "lastLoginAt": null,
        "createdAt": "2026-09-26T10:00:00Z",
        "updatedAt": "2026-09-26T10:00:00Z"
      },
      "invitation": {
        "accountId": 209,
        "activationToken": "raw-token-returned-once",
        "expiresAt": "2026-09-27T10:00:00Z"
      }
    },
    "hrStaffRoleAssignment": {
      "id": 33,
      "accountId": 209,
      "roleId": 5,
      "roleCode": "HR_STAFF",
      "roleName": "Nhân viên nhân sự",
      "scopeType": "COMPANY",
      "organizationUnitId": null,
      "workLocationId": null,
      "effectiveFrom": "2026-09-26",
      "effectiveTo": null,
      "grantedByAccountId": 208,
      "reason": "Appointed as the first HR staff member",
      "createdAt": "2026-09-26T10:00:00Z",
      "revokedByAccountId": null,
      "revokedAt": null,
      "revocationReason": null
    }
  },
  "error": null
}
```

Raw activation token chỉ xuất hiện trong response. Company Owner phải chuyển token qua kênh an toàn để HR Staff tự đặt mật khẩu bằng [API activation](./AUTH.md).

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Request không hợp lệ hoặc `effectiveFrom` trước `employee.hireDate` |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có `organization.hr_staff.bootstrap` |
| 409 | `HR_STAFF_ALREADY_EXISTS` | Đã tồn tại assignment `HR_STAFF` chưa bị thu hồi |
| 409 | `EMPLOYEE_CODE_TAKEN` | Mã employee đã tồn tại |
| 409 | `EMAIL_TAKEN` | Work email đã tồn tại ở employee hoặc account |
| 409 | `USERNAME_TAKEN` | Username đã tồn tại |

---

## Dữ liệu demo

`UserSeeder` có một account demo mang role `HR_STAFF`. Cấu hình mặc định hiện là `USER_SEED_ENABLED=false` để không chặn workflow bootstrap trên database mới. Chỉ bật seeder này trong môi trường demo/test; khi bật, endpoint sẽ trả `HR_STAFF_ALREADY_EXISTS` theo đúng invariant.
