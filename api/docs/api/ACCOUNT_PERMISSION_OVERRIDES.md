# API Reference — Account Permission Overrides

Quản lý ngoại lệ permission trên một lần gán role cụ thể. Ngoại lệ kế thừa đúng scope của role assignment và không phải deny/grant toàn cục trên account.

---

## Endpoint access

| Endpoint | Permission | Mô tả |
|:---------|:-----------|:------|
| `GET /api/accounts/{accountId}/role-assignments/{assignmentId}/permission-overrides` | `account.permission.override.manage` | Lấy toàn bộ lịch sử ngoại lệ |
| `GET /api/accounts/{accountId}/role-assignments/{assignmentId}/permission-overrides/available-permissions` | `account.permission.override.manage` | Lấy permission `DELEGABLE` và hiệu ứng hợp lệ theo role |
| `POST /api/accounts/{accountId}/role-assignments/{assignmentId}/permission-overrides` | `account.permission.override.manage` | Tạo ngoại lệ mới |
| `POST /api/accounts/{accountId}/role-assignments/{assignmentId}/permission-overrides/{overrideId}/revoke` | `account.permission.override.manage` | Thu hồi ngoại lệ, giữ nguyên lịch sử |

`account.permission.override.manage` là permission `SYSTEM_ONLY` và chỉ được seed cho `COMPANY_OWNER`. Actor luôn lấy từ JWT/Security Context.

## Ngữ nghĩa

Authorization được tính riêng trên từng role assignment:

1. Lấy permission mặc định của role.
2. Áp dụng `GRANT` hoặc `REVOKE` đang hiệu lực trên assignment.
3. Hợp nhất permission từ tất cả assignment đang hiệu lực của account.

Vì vậy, `REVOKE` trên một assignment không loại permission do assignment khác cấp. Override dùng cùng scope `SELF`, `COMPANY`, `ORG_UNIT` hoặc `LOCATION` của assignment cha.

Chỉ permission active có `assignmentPolicy=DELEGABLE` được override. Permission `SYSTEM_ONLY` luôn bị từ chối.

---

## GET permission overrides

Response trả cả ngoại lệ hiện tại và lịch sử, sắp xếp theo thời điểm tạo giảm dần.

```json
{
  "success": true,
  "data": [
    {
      "id": 51,
      "accountRoleAssignmentId": 31,
      "accountId": 208,
      "permissionId": 12,
      "permissionCode": "attendance.overtime.approve",
      "permissionName": "Phê duyệt làm thêm giờ",
      "module": "ATTENDANCE",
      "effect": "GRANT",
      "effectiveFrom": "2026-10-01",
      "effectiveTo": "2026-10-31",
      "reason": "Tạm thời phụ trách duyệt chấm công",
      "grantedByAccountId": 1,
      "grantedByUsername": "owner",
      "createdAt": "2026-09-30T04:00:00Z",
      "revokedByAccountId": null,
      "revokedByUsername": null,
      "revokedAt": null,
      "revocationReason": null,
      "status": "SCHEDULED"
    }
  ],
  "error": null
}
```

`status` nhận `SCHEDULED`, `ACTIVE`, `EXPIRED` hoặc `REVOKED`.

---

## GET available permissions

Trả permission active `DELEGABLE`. Field `effect` được backend xác định từ mapping mặc định của role:

- Role chưa có permission: `GRANT`.
- Role đang có permission: `REVOKE`.

Client không tự chọn hiệu ứng ngược lại.

---

## POST permission override

```json
{
  "permissionId": 12,
  "effect": "GRANT",
  "effectiveFrom": "2026-10-01",
  "effectiveTo": "2026-10-31",
  "reason": "Tạm thời phụ trách duyệt chấm công"
}
```

Quy tắc:

- Assignment phải thuộc account trên URL, chưa bị thu hồi và role chưa bị xóa.
- Account đích không được ở trạng thái `DISABLED`.
- Khoảng override phải nằm trong khoảng assignment.
- Cùng assignment và permission không được có hai override chưa thu hồi bị chồng thời gian.
- `reason` bắt buộc, tối đa 500 ký tự.
- `GRANT` chỉ hợp lệ khi role chưa có permission; `REVOKE` chỉ hợp lệ khi role đang có permission.

Response `201 Created` trả `AccountPermissionOverrideResponse`.

---

## POST revoke override

```json
{
  "reason": "Đã kết thúc thời gian phụ trách"
}
```

Ghi nhận người thu hồi, thời điểm và lý do. Override mất hiệu lực ngay nhưng bản ghi không bị xóa. Gọi lại endpoint với override đã thu hồi trả trạng thái hiện tại và không ghi đè audit cũ.

---

## Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Body, ID hoặc khoảng hiệu lực không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có `account.permission.override.manage` |
| 404 | `ROLE_ASSIGNMENT_NOT_FOUND` | Assignment không tồn tại hoặc không thuộc account |
| 404 | `PERMISSION_NOT_FOUND` | Permission không tồn tại |
| 404 | `PERMISSION_OVERRIDE_NOT_FOUND` | Override không tồn tại hoặc không thuộc assignment |
| 409 | `PERMISSION_OVERRIDE_EXISTS` | Khoảng hiệu lực chồng ngoại lệ hiện có |
| 409 | `PERMISSION_OVERRIDE_NOT_ALLOWED` | Assignment, account, permission hoặc effect không cho phép override |

## Hiệu lực JWT

Khi tạo hoặc thu hồi override, refresh token của account đích bị thu hồi. Access token đã phát là stateless nên claims cũ có thể còn hiệu lực tối đa đến `exp` (mặc định 15 phút). Sau đó account phải đăng nhập lại để nhận authorization snapshot mới.
