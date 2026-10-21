# Phạm vi dữ liệu (Scoped RBAC)

Tài liệu giải thích cơ chế "account được xem/sửa **những nhân viên nào**". Danh sách role, permission và cây tổ
chức mẫu nằm ở [ORGANIZATION_STRUCTURE.md](../../../docs/ORGANIZATION_STRUCTURE.md#5-mô-hình-phân-quyền); tài liệu này
tập trung vào cách hệ thống tính và áp dụng phạm vi.

Tên gọi chung của mô hình: **scoped RBAC** (role được gán kèm phạm vi) hay **data-level / row-level authorization**.
Azure RBAC dùng đúng mô hình này: một role assignment gồm *ai* + *role* + *scope*, và scope kế thừa xuống cấp con.

## 1. Hai lớp kiểm tra

| Lớp | Câu hỏi | Nguồn dữ liệu | Code |
|:----|:--------|:--------------|:-----|
| Permission (chức năng) | Được gọi thao tác này không? | Claim `permissions` trong JWT, chốt lúc đăng nhập hoặc refresh | `@PreAuthorize("hasAuthority('…')")` |
| Phạm vi (dữ liệu) | Được thao tác trên **nhân viên nào**? | Đọc database ở **mỗi request** | `EmployeeAccessScopeService` |

Một request phải qua cả hai lớp. Ví dụ `GET /api/employees` cần permission `employee.list.read`, sau đó danh sách
được lọc theo phạm vi của chính permission đó.

## 2. Mô hình dữ liệu

Phạm vi nằm trên **từng lần gán role** (`account_role_assignments`), không nằm trên account hay nhân viên:

| `scope_type` | Cột đi kèm | Được thao tác trên |
|:-------------|:-----------|:-------------------|
| `SELF` | không | Nhân viên liên kết với chính account (`accounts.employee_id`) |
| `ORG_UNIT` | `organization_unit_id` | Nhân viên thuộc đơn vị đó **và mọi đơn vị con** |
| `LOCATION` | `work_location_id` | Nhân viên ở địa điểm đó **và mọi địa điểm con** |
| `COMPANY` | không | Mọi nhân viên |

Ràng buộc `chk_account_role_assignments_scope` trong
[`V3__rbac.sql`](../../src/main/resources/db/migration/V3__rbac.sql) bắt buộc mỗi dòng có đúng cột tương ứng với
loại scope. Một account có thể có nhiều lần gán, kể cả cùng role với các scope khác nhau.

## 3. Cách tính phạm vi cho một permission

`EmployeeAccessScopeService.resolve(accountId, permissionCode)`:

1. **Lấy các lần gán đang hiệu lực hôm nay** (`AccountAuthorizationService.loadContext`): `revoked_at IS NULL`,
   `effective_from <= hôm nay`, `effective_to` rỗng hoặc `>= hôm nay`, role chưa bị xóa.
2. **Tính permission hiệu lực của từng lần gán**: permission của role (chỉ permission `is_active`), cộng override
   `GRANT`, trừ override `REVOKE` đang hiệu lực. Override xếp theo thời điểm tạo.
3. **Chỉ giữ các lần gán có chứa permission đang xét.** Vì vậy phạm vi tính **theo từng permission**: cùng một
   account có thể xem danh sách nhân viên ở chi nhánh A nhưng xem lương ở phạm vi khác.
4. **Gộp các lần gán còn lại bằng OR** thành `EmployeeAccessScope`:
   - `companyWide`: có ít nhất một lần gán `COMPANY`;
   - `selfEmployeeId`: có lần gán `SELF` (lấy nhân viên liên kết với account);
   - `organizationUnitIds`: đơn vị được gán cộng toàn bộ đơn vị con (duyệt cây bằng BFS, bỏ qua đơn vị đã xóa mềm);
   - `workLocationIds`: tương tự cho địa điểm.

## 4. Áp phạm vi vào truy vấn

`EmployeeAccessScope.toSpecification(date)` sinh một JPA `Specification<Employee>`:

- `companyWide`: không thêm điều kiện.
- Ngược lại, nhân viên thỏa **ít nhất một** điều kiện:
  - `employee.id = selfEmployeeId`, hoặc
  - tồn tại **phân công chính** (`employee_assignments.is_primary = true`) **có hiệu lực tại `date`** với
    `organization_unit_id ∈ organizationUnitIds` **hoặc** `work_location_id ∈ workLocationIds`.
- Không có phạm vi nào: trả về điều kiện luôn sai, tức danh sách rỗng chứ không báo lỗi.

Bộ lọc người dùng chọn (từ khóa, trạng thái, đơn vị…) được nối thêm bằng **AND**, nên chỉ thu hẹp được, không
mở rộng ra ngoài phạm vi.

## 5. Các hàm dùng trong service

| Hàm | Dùng khi | Kết quả nếu không đạt |
|:----|:---------|:----------------------|
| `accessibleEmployees(permission)` | Truy vấn danh sách (nhân viên, bảng công, đơn…) | Danh sách bị lọc |
| `requireEmployeeAccess(employeeId, permission)` | Đọc hoặc sửa **một** nhân viên | `403 FORBIDDEN` |
| `requireCompanyWide(permission)` | Thao tác chỉ hợp lý ở cấp công ty: kỳ lương, xuất lương, báo cáo lương… | `403 FORBIDDEN` |
| `requireDestinationAccess(unitId, locationId, permission)` | Điều chuyển nhân viên: nơi đến cũng phải nằm trong phạm vi | `403 FORBIDDEN` |
| `visibleOnLastDay(permission, employees, date)` | Báo cáo kỳ cũ: xét mỗi người tại ngày làm việc cuối của họ (hoặc ngày vào làm nếu chưa bắt đầu) | Người đó bị loại khỏi báo cáo |
| `canAccountAccessEmployee(accountId, employeeId, permission)` | Kiểm tra cho một account khác account đang đăng nhập | `false` |

## 6. Ví dụ

Account `branch-hn` có hai lần gán:

| Role | Scope | Có `employee.list.read`? |
|:-----|:------|:-------------------------|
| `MANAGER` | `LOCATION = BRANCH-01` (Hà Nội) | Có |
| `EMPLOYEE` | `SELF` | Không |

Khi gọi `GET /api/employees`, chỉ lần gán thứ nhất được xét. `branch-hn` thấy mọi nhân viên có phân công chính
hôm nay ở `BRANCH-01` hoặc địa điểm con như kho `WAREHOUSE-02`. Hồ sơ của chính họ chỉ xuất hiện nếu họ cũng
được phân công tại địa điểm đó.

## 7. Những điểm dễ quên

- **Chỉ tính phân công chính.** Nhân viên kiêm nhiệm (phân công không chính) ở đơn vị khác không hiện với quản lý
  của đơn vị kiêm nhiệm.
- **Tính theo ngày hôm nay.** Người đã nghỉ việc hoặc chưa đến ngày vào làm không có phân công đang hiệu lực, nên
  chỉ phạm vi `COMPANY` (hoặc `SELF` của chính họ) thấy được. Báo cáo dùng `visibleOnLastDay` để tránh mất người
  trong kỳ cũ.
- **`ORG_UNIT` và `LOCATION` là hai trục độc lập, gộp bằng OR.** Không có kiểu phạm vi "phòng Kế toán *tại* chi
  nhánh Hà Nội".
- **Độ trễ của permission khác độ trễ của phạm vi.** Permission nằm trong JWT nên thay đổi chỉ có tác dụng sau lần
  refresh tiếp theo (access token sống 15 phút). Phạm vi đọc database ở mỗi request nên có tác dụng ngay. Khi thu
  hồi role hoặc override, hệ thống thu hồi luôn refresh token của account (xem
  [TOKEN_LIFECYCLE.md](./TOKEN_LIFECYCLE.md#6-khi-nào-mọi-phiên-bị-thu-hồi)). Trong lúc access token cũ còn hạn,
  `@PreAuthorize` có thể vẫn cho qua, nhưng lần gán đã thu hồi không còn đóng góp phạm vi nào nên dữ liệu bị lọc
  ngay.
- **Ngày "hôm nay"** lấy theo múi giờ của JVM, xem
  [EFFECTIVE_DATING.md](./EFFECTIVE_DATING.md#6-múi-giờ-của-hôm-nay).
- **Ẩn nút trên web chỉ là giao diện.** Mọi kiểm tra thật đều nằm ở backend.

## Code

- [`EmployeeAccessScopeService`](../../src/main/java/com/htttdn/hrm/service/EmployeeAccessScopeService.java):
  `resolve`, `toSpecification`, `visibleOnLastDay`
- [`AccountAuthorizationService`](../../src/main/java/com/htttdn/hrm/service/AccountAuthorizationService.java):
  `getScopes`, `loadContext`, `effectivePermissions`
- [`AccountRoleAssignmentRepository.findActiveWithRoleByAccountId`](../../src/main/java/com/htttdn/hrm/repository/AccountRoleAssignmentRepository.java)
