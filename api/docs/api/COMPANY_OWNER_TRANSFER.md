# API Reference — Company Owner Transfer

Company Owner hiện tại chuyển quyền sở hữu doanh nghiệp (role `COMPANY_OWNER`) cho một nhân viên khác. Chuyển giao có hiệu lực ngay, không cần người nhận xác nhận; Owner xác nhận bằng mật khẩu của chính mình.

---

## Endpoint access

| Endpoint | Điều kiện |
|:---------|:----------|
| `POST /api/company-owner/transfer` | Token mang role `COMPANY_OWNER` và account đang thật sự giữ assignment `COMPANY_OWNER` chưa thu hồi |

Không có permission riêng: chỉ người đang là Owner được chuyển. Token cấp trước khi chuyển giao vẫn mang `ROLE_COMPANY_OWNER` tới khi hết hạn, nên server kiểm tra lại assignment trong database; Owner cũ gọi lại sẽ nhận `403`.

---

## POST `/api/company-owner/transfer`

```json
{
  "accountId": 42,
  "password": "mật khẩu của Owner hiện tại",
  "reason": "Bàn giao điều hành"
}
```

| Field | Type | Required | Rule |
|:------|:-----|:--------:|:-----|
| `accountId` | number | ✅ | Account nhận quyền sở hữu, khác account đang đăng nhập |
| `password` | string | ✅ | Mật khẩu hiện tại của Owner, tối đa 100 ký tự |
| `reason` | string | ✅ | Không rỗng, tối đa 100 ký tự; ghi vào cả lần thu hồi và lần gán mới |

Khi thành công, trong cùng một transaction:

1. Assignment `COMPANY_OWNER` của Owner cũ bị thu hồi (`revokedAt`, người thu hồi là Owner cũ, `revocationReason` là `reason`) và đóng `effectiveTo` tại hôm nay.
2. Người nhận được gán `COMPANY_OWNER` scope `COMPANY` từ hôm nay, không thời hạn, `grantedByAccount` là Owner cũ.
3. Mọi refresh token của Owner cũ bị thu hồi: phiên hiện tại hết hiệu lực ở lần làm mới token kế tiếp.

Chỉ `COMPANY_OWNER` được chuyển. Owner cũ giữ nguyên các role khác (ví dụ `DIRECTOR`, `PAYROLL_APPROVER`); đổi Giám đốc làm riêng bằng cách thu hồi assignment `DIRECTOR` hiện tại rồi gán cho người mới ([Account Role Assignments](./ACCOUNT_ROLE_ASSIGNMENTS.md)). Người duyệt đơn nghỉ/tăng ca và người duyệt đề xuất role được tính theo người đang giữ role nên đổi theo ngay.

Người nhận phải là account `ACTIVE` (đã kích hoạt, đăng nhập được) gắn với nhân viên chưa xóa, đang `ACTIVE` hoặc `PROBATION` và không có lịch kết thúc làm việc, để doanh nghiệp không rơi vào cảnh có Owner mà không ai dùng được.

Response `200` là `AccountRoleAssignmentResponse` của assignment `COMPANY_OWNER` mới.

### Lỗi

| HTTP | Code | Khi nào |
|:----:|:-----|:--------|
| 400 | `VALIDATION_ERROR` | Thiếu field, quá độ dài, hoặc `accountId` là chính account đang đăng nhập (field `accountId`) |
| 400 | `INVALID_CREDENTIALS` | Sai mật khẩu (field `password`); không có gì thay đổi |
| 403 | `FORBIDDEN` | Token không mang `COMPANY_OWNER`, hoặc account không còn giữ `COMPANY_OWNER` |
| 404 | `ACCOUNT_NOT_FOUND` | Account nhận không tồn tại |
| 409 | `ROLE_ASSIGNMENT_NOT_ALLOWED` | Account nhận không `ACTIVE`, không gắn nhân viên, hoặc nhân viên đã/sắp nghỉ |
