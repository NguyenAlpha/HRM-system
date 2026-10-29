# Mở rộng: HR đề nghị ngoại lệ quyền, Company Owner duyệt

> Trạng thái: **chưa làm**. Tài liệu này ghi lại thiết kế để sau này có thể làm tiếp.

## 1. Hiện trạng

[Ngoại lệ quyền](../../api/docs/api/ACCOUNT_PERMISSION_OVERRIDES.md) thêm (`GRANT`) hoặc bớt (`REVOKE`) một
permission trên một lần gán role, có thời hạn, mà không phải tạo role mới. Hiện chỉ **Company Owner** dùng được
(`account.permission.override.manage`, `SYSTEM_ONLY`), và ngoại lệ có hiệu lực ngay khi tạo.

HR không dùng được. Khi cần bớt hay thêm quyền cho ai đó, HR phải báo Owner tự làm.

## 2. Vì sao không cho HR tạo ngoại lệ trực tiếp

`GRANT` thêm được **bất kỳ permission `DELEGABLE` nào** vào một lần gán, có hiệu lực ngay. Nếu HR tự tạo được
thì quy trình `OWNER_APPROVAL` bị vòng qua. Ví dụ: thay vì đề nghị role `PAYROLL_ACCOUNTANT` và chờ Owner duyệt,
HR chỉ cần GRANT `payroll.calculate` vào lần gán `MANAGER` của người đó. Hệ thống chỉ chặn tự sửa quyền của
chính mình, không chặn HR cấp cho người khác.

`REVOKE` (bớt quyền) không giúp ai leo thang quyền, nhưng để quy tắc đơn giản, thiết kế dưới đây cho cả hai đi
qua bước duyệt (xem mục 6).

## 3. Thiết kế đề xuất

Giống workflow [đề nghị role](../../api/docs/api/ROLE_ASSIGNMENT_REQUESTS.md): HR gửi đề nghị, Company Owner
duyệt hoặc từ chối, HR hủy được khi còn chờ.

| Bước | Ai | Điều kiện |
|---|---|---|
| Gửi đề nghị | Người có `role.assignment.request` (HR) | Nhân viên của lần gán và đơn vị/địa điểm của lần gán nằm trong phạm vi người đề nghị; không đề nghị cho chính mình; guard Company Owner/System Admin; cùng kiểm tra như khi tạo ngoại lệ (permission `DELEGABLE` đang active, hiệu ứng thật sự đổi gì đó, thời hạn nằm trong lần gán, không chồng ngoại lệ khác) |
| Duyệt | Người có `role.assignment.approve` (Company Owner) | Đề nghị còn `PENDING`; kiểm tra lại mọi điều kiện tại lúc duyệt rồi tạo ngoại lệ thật (dùng lại `AccountPermissionOverrideService`), thu hồi refresh token của account |
| Từ chối | Company Owner | Bắt buộc ghi chú |
| Hủy | Người gửi | Đề nghị còn `PENDING` |

Khi lần gán bị thu hồi (thu hồi trực tiếp, nghỉ việc, xóa tài khoản, xóa role), đề nghị đang chờ trên lần gán đó
tự chuyển `CANCELLED`, như đề nghị role đang được hủy khi nghỉ việc, xóa tài khoản hoặc xóa role
(`AccountRoleAssignmentCommandService.revokeHeldRoles` / `revokeDeletedRole`).

## 4. Dữ liệu

Bảng mới `permission_override_requests`, thêm bằng một file migration `V…` mới:

| Cột | Ghi chú |
|---|---|
| `account_role_assignment_id` | FK, lần gán nhận ngoại lệ |
| `permission_id` | FK |
| `effect` | `GRANT` / `REVOKE` |
| `effective_from`, `effective_to` | Thời hạn của ngoại lệ |
| `reason` | Bắt buộc |
| `status` | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` |
| `requested_by_account_id`, `requested_at` | Người gửi |
| `reviewed_by_account_id`, `reviewed_at`, `review_note` | Người duyệt / từ chối |
| `cancelled_by_account_id`, `cancelled_at`, `cancellation_reason` | Người hủy |
| `account_permission_override_id` | FK, ngoại lệ được tạo khi duyệt |

Ràng buộc: check theo trạng thái giống `chk_role_assignment_requests_resolution`; partial unique index một đề nghị
`PENDING` cho mỗi (lần gán, permission).

## 5. API và giao diện

- `POST /api/permission-override-requests`; `GET` danh sách và chi tiết (Owner thấy tất cả, HR thấy của mình);
  `POST /{id}/approve`, `/reject`, `/cancel`.
- Trang `/role-requests` thêm tab "Ngoại lệ quyền": HR gửi và theo dõi, Owner duyệt hoặc từ chối cùng chỗ với
  đề nghị role.
- Sinh lại OpenAPI snapshot và `npm run gen:api` sau khi thêm DTO.

## 6. Câu hỏi cần chốt trước khi làm

1. `REVOKE` có cần Owner duyệt không? Đề xuất: **có**, một quy tắc chung cho cả hai. Phương án khác: `REVOKE` tự
   duyệt (như role `HR_ASSIGNABLE`), chỉ `GRANT` chờ duyệt.
2. Dùng chung `role.assignment.request` / `role.assignment.approve` hay thêm permission riêng? Đề xuất: **dùng
   chung**, không thêm seed.
3. Màn hình: tab trong `/role-requests` hay trang riêng? Đề xuất: **tab trong `/role-requests`**.
