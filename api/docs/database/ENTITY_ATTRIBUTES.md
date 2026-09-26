# Entity & Attributes — HRM

> Tài liệu mô tả từng entity (ánh xạ 1-1 với bảng trong [DATABASE_SCHEMA.md](./DATABASE_SCHEMA.md)) và thuộc tính ở mức Java, dùng làm cơ sở để viết JPA entity. Kiểu dữ liệu, ràng buộc và ý nghĩa nghiệp vụ giữ nguyên theo schema đã duyệt.
>
> Quy ước kiểu Java: `BIGSERIAL`→`Long`, `SMALLINT`→`Short`, `VARCHAR/TEXT`→`String`, `BOOLEAN`→`Boolean`, `DATE`→`LocalDate`, `TIME`→`LocalTime`, `TIMESTAMPTZ`→`Instant`, `NUMERIC`→`BigDecimal`, `INTEGER`→`Integer`. Cột giá trị tĩnh dùng enum tại `com.htttdn.hrm.entity.enums`. Thuộc tính tham chiếu khóa ngoại được viết dưới dạng quan hệ tới entity khác (không phải kiểu `Long` thô) vì đây là mức thiết kế entity, không phải cột DB.

---

## 1. Doanh nghiệp và cơ cấu tổ chức

### Entity `CompanyProfile` (bảng `company_profile`)

**Mô tả**: Thông tin duy nhất của doanh nghiệp. Không có quan hệ với entity khác.

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Short` | `id` | PK, luôn = 1 | Đảm bảo chỉ một bản ghi doanh nghiệp |
| `code` | `String` | `code` | NOT NULL, UNIQUE | Mã doanh nghiệp |
| `name` | `String` | `name` | NOT NULL | Tên doanh nghiệp |
| `taxCode` | `String` | `tax_code` | UNIQUE | Mã số thuế |
| `phone` | `String` | `phone` | | Số điện thoại |
| `email` | `String` | `email` | | Email liên hệ |
| `address` | `String` | `address` | | Địa chỉ đăng ký |
| `timezone` | `String` | `timezone` | NOT NULL, mặc định `Asia/Ho_Chi_Minh` | Múi giờ nghiệp vụ |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |
| `updatedAt` | `Instant` | `updated_at` | NOT NULL | Thời điểm cập nhật |

### Entity `WorkLocation` (bảng `work_locations`)

**Mô tả**: Trụ sở, chi nhánh hoặc kho — nơi làm việc/phạm vi quản lý nhân sự.

**Quan hệ**:
- Tự tham chiếu `parentLocation` (trụ sở/chi nhánh cha của kho).
- Một-nhiều tới `EmployeeAssignment.workLocation`.
- Một-nhiều tới `AccountRoleAssignment.workLocation` (khi `scopeType = LOCATION`).

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `parentLocation` | `WorkLocation` | `parent_location_id` | FK → work_locations, nullable | Trụ sở/chi nhánh cha của kho |
| `code` | `String` | `code` | NOT NULL, UNIQUE (khi chưa xóa) | Mã địa điểm |
| `name` | `String` | `name` | NOT NULL | Tên địa điểm |
| `locationType` | `LocationType` | `location_type` | NOT NULL | `HEAD_OFFICE` / `BRANCH` / `WAREHOUSE` |
| `address` | `String` | `address` | NOT NULL | Địa chỉ làm việc |
| `phone` | `String` | `phone` | | Số điện thoại |
| `isActive` | `Boolean` | `is_active` | NOT NULL, mặc định true | Trạng thái hoạt động |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |
| `updatedAt` | `Instant` | `updated_at` | NOT NULL | Thời điểm cập nhật |
| `deletedAt` | `Instant` | `deleted_at` | nullable | Xóa mềm |

### Entity `OrganizationUnit` (bảng `organization_units`)

**Mô tả**: Phòng ban, nhóm — cơ cấu quản trị, không đồng nhất với địa điểm làm việc.

**Quan hệ**:
- Tự tham chiếu `parentUnit`.
- Một-nhiều tới `EmployeeAssignment.organizationUnit`.
- Một-nhiều tới `AccountRoleAssignment.organizationUnit` (khi `scopeType = ORG_UNIT`).

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `parentUnit` | `OrganizationUnit` | `parent_unit_id` | FK → organization_units, nullable | Đơn vị cha |
| `code` | `String` | `code` | NOT NULL, UNIQUE (khi chưa xóa) | Mã đơn vị |
| `name` | `String` | `name` | NOT NULL | Tên phòng ban/nhóm |
| `unitType` | `OrganizationUnitType` | `unit_type` | NOT NULL | `BOARD` / `DEPARTMENT` / `TEAM` |
| `isActive` | `Boolean` | `is_active` | NOT NULL, mặc định true | Trạng thái hoạt động |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |
| `updatedAt` | `Instant` | `updated_at` | NOT NULL | Thời điểm cập nhật |
| `deletedAt` | `Instant` | `deleted_at` | nullable | Xóa mềm |

### Entity `JobPosition` (bảng `job_positions`)

**Mô tả**: Danh mục vị trí công việc.

**Quan hệ**: Một-nhiều tới `EmployeeAssignment.position`.

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `code` | `String` | `code` | NOT NULL, UNIQUE (khi chưa xóa) | Mã vị trí |
| `title` | `String` | `title` | NOT NULL | Nhân viên nhân sự, thủ kho, kế toán lương... |
| `description` | `String` | `description` | | Mô tả công việc |
| `isManagerial` | `Boolean` | `is_managerial` | NOT NULL, mặc định false | Có phải vị trí quản lý |
| `isActive` | `Boolean` | `is_active` | NOT NULL, mặc định true | Còn được sử dụng |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |
| `updatedAt` | `Instant` | `updated_at` | NOT NULL | Thời điểm cập nhật |
| `deletedAt` | `Instant` | `deleted_at` | nullable | Xóa mềm |

---

## 2. Hồ sơ và vòng đời nhân sự

### Entity `Employee` (bảng `employees`)

**Mô tả**: Hồ sơ nhân sự — trung tâm của toàn hệ thống.

**Quan hệ**:
- Một-một tới `Account.employee`.
- Một-nhiều tới `EmployeeAssignment.employee` (và `EmployeeAssignment.managerEmployee` — tự tham chiếu gián tiếp qua assignment).
- Một-nhiều tới `EmployeeCompensation.employee`.
- Một-nhiều tới `EmployeeRequest.employee`.
- Một-nhiều tới `AttendanceRecord.employee`.
- Một-nhiều tới `Payslip.employee`.
- Nhiều-một tới `Account` qua `deletedByAccount` (người xóa mềm).

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `employeeCode` | `String` | `employee_code` | NOT NULL, UNIQUE | Mã nhân viên, không tái sử dụng |
| `fullName` | `String` | `full_name` | NOT NULL | Họ tên |
| `dateOfBirth` | `LocalDate` | `date_of_birth` | | Ngày sinh |
| `gender` | `Gender` | `gender` | nullable | `MALE` / `FEMALE` / `OTHER` / `UNDISCLOSED` |
| `highestEducationLevel` | `EducationLevel` | `highest_education_level` | nullable | Trình độ cao nhất |
| `major` | `String` | `major` | | Chuyên ngành của trình độ cao nhất |
| `institution` | `String` | `institution` | | Cơ sở đào tạo |
| `graduationYear` | `Short` | `graduation_year` | | Năm tốt nghiệp |
| `nationalId` | `String` | `national_id` | UNIQUE | CCCD/hộ chiếu |
| `personalEmail` | `String` | `personal_email` | | Email cá nhân |
| `workEmail` | `String` | `work_email` | UNIQUE | Email công việc |
| `phone` | `String` | `phone` | | Số điện thoại |
| `address` | `String` | `address` | | Địa chỉ liên hệ |
| `taxCode` | `String` | `tax_code` | | Mã số thuế cá nhân |
| `bankName` | `String` | `bank_name` | | Ngân hàng nhận lương |
| `bankAccountNumber` | `String` | `bank_account_number` | | Số tài khoản |
| `bankAccountHolder` | `String` | `bank_account_holder` | | Tên chủ tài khoản |
| `hireDate` | `LocalDate` | `hire_date` | NOT NULL | Ngày vào làm |
| `employmentStatus` | `EmploymentStatus` | `employment_status` | NOT NULL | `PROBATION` / `ACTIVE` / `RESIGNED` / `TERMINATED` / `RETIRED` |
| `terminationDate` | `LocalDate` | `termination_date` | | Ngày làm việc cuối cùng |
| `terminationReason` | `String` | `termination_reason` | | Lý do kết thúc làm việc |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |
| `updatedAt` | `Instant` | `updated_at` | NOT NULL | Thời điểm cập nhật |
| `deletedAt` | `Instant` | `deleted_at` | nullable | Chỉ dùng cho hồ sơ tạo nhầm |
| `deletedByAccount` | `Account` | `deleted_by_account_id` | FK → accounts, nullable | Người thực hiện xóa mềm |
| `deletionReason` | `String` | `deletion_reason` | | Lý do xóa mềm |

### Entity `EmployeeAssignment` (bảng `employee_assignments`)

**Mô tả**: Lịch sử phân công (phòng ban, địa điểm, vị trí, ca, quản lý) theo thời gian.

**Quan hệ**: Nhiều-một tới `Employee` (2 chiều: `employee` và `managerEmployee`), `OrganizationUnit`, `WorkLocation`, `JobPosition`, `WorkShift`, `Account` (`createdByAccount`).

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `employee` | `Employee` | `employee_id` | FK, NOT NULL | Nhân viên |
| `organizationUnit` | `OrganizationUnit` | `organization_unit_id` | FK, NOT NULL | Phòng ban/nhóm |
| `workLocation` | `WorkLocation` | `work_location_id` | FK, NOT NULL | Địa điểm làm việc |
| `position` | `JobPosition` | `position_id` | FK, NOT NULL | Vị trí công việc |
| `shift` | `WorkShift` | `shift_id` | FK, nullable | Ca làm việc hiện hành của phân công |
| `managerEmployee` | `Employee` | `manager_employee_id` | FK, nullable | Quản lý trực tiếp |
| `employmentType` | `EmploymentType` | `employment_type` | NOT NULL | `FULL_TIME` / `PART_TIME` / `TEMPORARY` |
| `effectiveFrom` | `LocalDate` | `effective_from` | NOT NULL | Ngày bắt đầu |
| `effectiveTo` | `LocalDate` | `effective_to` | nullable | Ngày kết thúc |
| `isPrimary` | `Boolean` | `is_primary` | NOT NULL, mặc định true | Phân công chính |
| `reason` | `String` | `reason` | | Tuyển mới, điều chuyển, bổ nhiệm... |
| `createdByAccount` | `Account` | `created_by_account_id` | FK, NOT NULL | Người ghi nhận |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |

### Entity `EmployeeCompensation` (bảng `employee_compensations`)

**Mô tả**: Lương cơ bản và phụ cấp theo thời gian hiệu lực.

**Quan hệ**: Nhiều-một tới `Employee`, `Account` (`approvedByAccount`).

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `employee` | `Employee` | `employee_id` | FK, NOT NULL | Nhân viên |
| `componentType` | `CompensationType` | `component_type` | NOT NULL | `BASIC_SALARY` / `ALLOWANCE` |
| `componentCode` | `String` | `component_code` | NOT NULL | `BASE`, `LUNCH`, `PHONE`, `RESPONSIBILITY`... |
| `componentName` | `String` | `component_name` | NOT NULL | Tên khoản lương/phụ cấp |
| `monthlyAmount` | `BigDecimal` | `monthly_amount` | NOT NULL, >= 0 | Số tiền theo tháng |
| `effectiveFrom` | `LocalDate` | `effective_from` | NOT NULL | Bắt đầu áp dụng |
| `effectiveTo` | `LocalDate` | `effective_to` | nullable | Kết thúc áp dụng |
| `approvedByAccount` | `Account` | `approved_by_account_id` | FK, NOT NULL | Người duyệt |
| `note` | `String` | `note` | | Ghi chú |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |

---

## 3. Tài khoản và phân quyền RBAC

### Entity `Account` (bảng `accounts`)

**Mô tả**: Tài khoản đăng nhập hệ thống.

**Quan hệ**:
- Một-một tới `Employee` (nullable — null chỉ dành cho bootstrap admin).
- Một-nhiều tới `AccountActivationToken.account` và `AccountActivationToken.createdByAccount`.
- Một-nhiều tới `RefreshToken.account`.
- Một-nhiều tới `AccountRoleAssignment.account`.
- Được tham chiếu bởi nhiều entity khác qua các cột `*_account_id` (người thực hiện/duyệt).

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `employee` | `Employee` | `employee_id` | FK, UNIQUE, nullable | Hồ sơ liên kết |
| `username` | `String` | `username` | NOT NULL, UNIQUE | Tên đăng nhập, không tái sử dụng |
| `email` | `String` | `email` | NOT NULL, UNIQUE | Email đăng nhập |
| `passwordHash` | `String` | `password_hash` | nullable khi `PENDING` | Mật khẩu đã hash; chỉ được null trước khi kích hoạt |
| `status` | `AccountStatus` | `status` | NOT NULL | `PENDING` / `ACTIVE` / `LOCKED` / `DISABLED` |
| `failedLoginCount` | `Integer` | `failed_login_count` | NOT NULL, mặc định 0 | Số lần đăng nhập sai liên tiếp |
| `lockedUntil` | `Instant` | `locked_until` | | Khóa tạm đến thời điểm |
| `lastLoginAt` | `Instant` | `last_login_at` | | Lần đăng nhập gần nhất |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |
| `updatedAt` | `Instant` | `updated_at` | NOT NULL | Thời điểm cập nhật |

### Entity `AccountActivationToken` (bảng `account_activation_tokens`)

**Mô tả**: Credential dùng một lần để account `PENDING` tự đặt hoặc đặt lại mật khẩu và chuyển sang `ACTIVE`.

**Quan hệ**: Nhiều-một tới `Account` qua `account` và `createdByAccount`.

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `account` | `Account` | `account_id` | FK, NOT NULL | Account được kích hoạt |
| `tokenHash` | `String` | `token_hash` | NOT NULL, UNIQUE | SHA-256 hash của raw token |
| `expiresAt` | `Instant` | `expires_at` | NOT NULL | Thời điểm token hết hạn |
| `usedAt` | `Instant` | `used_at` | nullable | Thời điểm kích hoạt thành công |
| `revokedAt` | `Instant` | `revoked_at` | nullable | Thời điểm token bị thu hồi |
| `createdByAccount` | `Account` | `created_by_account_id` | FK, NOT NULL | Quản trị viên phát hành token |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |

### Entity `RefreshToken` (bảng `refresh_tokens`)

**Mô tả**: Credential dùng để đổi access token theo cơ chế rotation; database chỉ lưu SHA-256 hash.

**Quan hệ**: Nhiều-một tới `Account`.

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `tokenHash` | `String` | `token_hash` | NOT NULL, UNIQUE | SHA-256 hash của raw refresh token |
| `account` | `Account` | `account_id` | FK, NOT NULL | Chủ sở hữu phiên đăng nhập |
| `expiresAt` | `Instant` | `expires_at` | NOT NULL | Thời điểm token hết hạn |
| `revokedAt` | `Instant` | `revoked_at` | nullable | Thời điểm token bị thu hồi hoặc đã được rotate |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |

### Entity `Permission` (bảng `permissions`)

**Mô tả**: Danh mục quyền nguyên tử.

**Quan hệ**: Một-nhiều tới `RolePermission.permission`, `AccountPermissionOverride.permission`.

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `code` | `String` | `code` | NOT NULL, UNIQUE | Mã quyền, ví dụ `leave.approve` |
| `module` | `PermissionModule` | `module` | NOT NULL | `EMPLOYEE` / `ACCOUNT` / `ORGANIZATION` / `REQUEST` / `ATTENDANCE` / `PAYROLL` / `RBAC` / `REPORT` |
| `description` | `String` | `description` | NOT NULL | Mô tả quyền |
| `isActive` | `Boolean` | `is_active` | NOT NULL, mặc định true | Trạng thái |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |

### Entity `Role` (bảng `roles`)

**Mô tả**: Mẫu vai trò.

**Quan hệ**: Một-nhiều tới `RolePermission.role`, `AccountRoleAssignment.role`.

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `code` | `String` | `code` | NOT NULL, UNIQUE | Mã vai trò |
| `name` | `String` | `name` | NOT NULL | Tên hiển thị |
| `description` | `String` | `description` | | Mô tả |
| `isSystem` | `Boolean` | `is_system` | NOT NULL, mặc định false | Vai trò seed, không được xóa |
| `isActive` | `Boolean` | `is_active` | NOT NULL, mặc định true | Trạng thái |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |
| `updatedAt` | `Instant` | `updated_at` | NOT NULL | Thời điểm cập nhật |
| `deletedAt` | `Instant` | `deleted_at` | nullable | Chỉ áp dụng cho vai trò tùy chỉnh |

### Entity `RolePermission` (bảng `role_permissions`)

**Mô tả**: Quyền mặc định của vai trò. Khóa chính phức hợp (`role`, `permission`).

**Quan hệ**: Nhiều-một tới `Role`, `Permission`, `Account` (`createdByAccount`).

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `role` | `Role` | `role_id` | PK, FK | Vai trò |
| `permission` | `Permission` | `permission_id` | PK, FK | Quyền được cấp |
| `createdByAccount` | `Account` | `created_by_account_id` | FK, nullable | Người cấu hình; null với seed |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm cấp |

### Entity `AccountRoleAssignment` (bảng `account_role_assignments`)

**Mô tả**: Gán vai trò cho tài khoản kèm phạm vi hiệu lực.

**Quan hệ**: Nhiều-một tới `Account` (`account`, `grantedByAccount`, `revokedByAccount`), `Role`, `OrganizationUnit` (nullable), `WorkLocation` (nullable). Một-nhiều tới `AccountPermissionOverride`.

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `account` | `Account` | `account_id` | FK, NOT NULL | Tài khoản nhận vai trò |
| `role` | `Role` | `role_id` | FK, NOT NULL | Vai trò được gán |
| `scopeType` | `RoleScopeType` | `scope_type` | NOT NULL | `SELF` / `COMPANY` / `ORG_UNIT` / `LOCATION` |
| `organizationUnit` | `OrganizationUnit` | `organization_unit_id` | FK, nullable | Chỉ dùng với `ORG_UNIT` |
| `workLocation` | `WorkLocation` | `work_location_id` | FK, nullable | Chỉ dùng với `LOCATION` |
| `effectiveFrom` | `LocalDate` | `effective_from` | NOT NULL | Ngày bắt đầu |
| `effectiveTo` | `LocalDate` | `effective_to` | nullable | Ngày kết thúc |
| `grantedByAccount` | `Account` | `granted_by_account_id` | FK, NOT NULL | Người cấp |
| `reason` | `String` | `reason` | | Lý do cấp quyền |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |
| `revokedByAccount` | `Account` | `revoked_by_account_id` | FK, nullable | Người thu hồi role |
| `revokedAt` | `Instant` | `revoked_at` | nullable | Thời điểm thu hồi |
| `revocationReason` | `String` | `revocation_reason` | nullable | Lý do thu hồi |

### Entity `AccountPermissionOverride` (bảng `account_permission_overrides`)

**Mô tả**: Ngoại lệ quyền (grant/revoke) cho một lần gán vai trò cụ thể.

**Quan hệ**: Nhiều-một tới `AccountRoleAssignment`, `Permission`, `Account` (`grantedByAccount`).

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `accountRoleAssignment` | `AccountRoleAssignment` | `account_role_assignment_id` | FK, NOT NULL | Lần gán vai trò được tùy chỉnh |
| `permission` | `Permission` | `permission_id` | FK, NOT NULL | Quyền cần ghi đè |
| `effect` | `PermissionOverrideEffect` | `effect` | NOT NULL | `GRANT` / `REVOKE` |
| `effectiveFrom` | `LocalDate` | `effective_from` | NOT NULL | Bắt đầu ngoại lệ |
| `effectiveTo` | `LocalDate` | `effective_to` | nullable | Kết thúc ngoại lệ |
| `reason` | `String` | `reason` | NOT NULL | Lý do bắt buộc |
| `grantedByAccount` | `Account` | `granted_by_account_id` | FK, NOT NULL | Người thiết lập |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |

---

## 4. Đơn nghỉ phép và nghỉ việc

### Entity `EmployeeRequest` (bảng `employee_requests`)

**Mô tả**: Đơn nghỉ phép hoặc nghỉ việc. Trường sử dụng phụ thuộc `requestType` (xem `chk_employee_requests_fields` trong schema).

**Quan hệ**: Nhiều-một tới `Employee`, `Account` (`reviewedByAccount`, nullable).

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `employee` | `Employee` | `employee_id` | FK, NOT NULL | Người gửi đơn |
| `requestType` | `RequestType` | `request_type` | NOT NULL | `LEAVE` / `RESIGNATION` |
| `leaveType` | `LeaveType` | `leave_type` | chỉ dùng khi `LEAVE` | `ANNUAL` / `SICK` / `MATERNITY` / `UNPAID` / `OTHER` |
| `isPaidLeave` | `Boolean` | `is_paid_leave` | chỉ dùng khi `LEAVE` | Nghỉ có hưởng lương hay không |
| `startDate` | `LocalDate` | `start_date` | chỉ dùng khi `LEAVE` | Ngày bắt đầu nghỉ phép |
| `endDate` | `LocalDate` | `end_date` | chỉ dùng khi `LEAVE` | Ngày kết thúc nghỉ phép |
| `totalDays` | `BigDecimal` | `total_days` | chỉ dùng khi `LEAVE` | Tổng số ngày nghỉ |
| `requestedLastWorkingDate` | `LocalDate` | `requested_last_working_date` | chỉ dùng khi `RESIGNATION` | Ngày làm việc cuối mong muốn |
| `reason` | `String` | `reason` | NOT NULL | Lý do |
| `attachmentUrl` | `String` | `attachment_url` | | Minh chứng nếu có |
| `status` | `RequestStatus` | `status` | NOT NULL | `DRAFT` / `PENDING` / `APPROVED` / `REJECTED` / `CANCELLED` / `COMPLETED` |
| `submittedAt` | `Instant` | `submitted_at` | | Thời điểm nộp |
| `reviewedByAccount` | `Account` | `reviewed_by_account_id` | FK, nullable | Người duyệt |
| `reviewComment` | `String` | `review_comment` | | Ý kiến duyệt/từ chối |
| `reviewedAt` | `Instant` | `reviewed_at` | | Thời điểm duyệt |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |
| `updatedAt` | `Instant` | `updated_at` | NOT NULL | Thời điểm cập nhật |

---

## 5. Ca làm việc, chấm công và tăng ca

### Entity `WorkShift` (bảng `work_shifts`)

**Mô tả**: Danh mục ca làm việc.

**Quan hệ**: Một-nhiều tới `EmployeeAssignment.shift`, `AttendanceRecord.shift`.

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `code` | `String` | `code` | NOT NULL, UNIQUE (khi chưa xóa) | Mã ca |
| `name` | `String` | `name` | NOT NULL | Tên ca |
| `startTime` | `LocalTime` | `start_time` | NOT NULL | Giờ bắt đầu |
| `endTime` | `LocalTime` | `end_time` | NOT NULL | Giờ kết thúc |
| `breakMinutes` | `Integer` | `break_minutes` | NOT NULL, mặc định 0, >= 0 | Thời gian nghỉ |
| `standardWorkMinutes` | `Integer` | `standard_work_minutes` | NOT NULL, > 0 | Phút công chuẩn |
| `graceLateMinutes` | `Integer` | `grace_late_minutes` | NOT NULL, mặc định 0, >= 0 | Khoảng trễ cho phép |
| `crossesMidnight` | `Boolean` | `crosses_midnight` | NOT NULL, mặc định false | Ca qua ngày |
| `isActive` | `Boolean` | `is_active` | NOT NULL, mặc định true | Trạng thái |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |
| `updatedAt` | `Instant` | `updated_at` | NOT NULL | Thời điểm cập nhật |
| `deletedAt` | `Instant` | `deleted_at` | nullable | Xóa mềm |

### Entity `AttendanceRecord` (bảng `attendance_records`)

**Mô tả**: Chấm công hằng ngày, bao gồm tăng ca đã được duyệt trực tiếp vào bản ghi.

**Quan hệ**: Nhiều-một tới `Employee`, `WorkShift`, `Account` (`overtimeApprovedByAccount`, `updatedByAccount`, đều nullable).

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `employee` | `Employee` | `employee_id` | FK, NOT NULL | Nhân viên |
| `workDate` | `LocalDate` | `work_date` | NOT NULL | Ngày công |
| `shift` | `WorkShift` | `shift_id` | FK, NOT NULL | Ca áp dụng |
| `scheduledStartAt` | `Instant` | `scheduled_start_at` | NOT NULL | Giờ vào dự kiến được snapshot |
| `scheduledEndAt` | `Instant` | `scheduled_end_at` | NOT NULL | Giờ ra dự kiến được snapshot |
| `checkInAt` | `Instant` | `check_in_at` | | Giờ vào thực tế |
| `checkOutAt` | `Instant` | `check_out_at` | | Giờ ra thực tế |
| `workedMinutes` | `Integer` | `worked_minutes` | NOT NULL, mặc định 0, >= 0 | Phút làm thực tế |
| `payableMinutes` | `Integer` | `payable_minutes` | NOT NULL, mặc định 0, >= 0 | Phút được tính lương cơ bản |
| `lateMinutes` | `Integer` | `late_minutes` | NOT NULL, mặc định 0, >= 0 | Phút đi trễ |
| `earlyLeaveMinutes` | `Integer` | `early_leave_minutes` | NOT NULL, mặc định 0, >= 0 | Phút về sớm |
| `overtimeMinutes` | `Integer` | `overtime_minutes` | NOT NULL, mặc định 0, >= 0 | Phút tăng ca đã được duyệt để tính lương |
| `overtimeMultiplier` | `BigDecimal` | `overtime_multiplier` | NOT NULL, mặc định 1, > 0 | Hệ số tăng ca, ví dụ 1.5/2.0/3.0 |
| `overtimeApprovedByAccount` | `Account` | `overtime_approved_by_account_id` | FK, nullable | Người duyệt số phút tăng ca |
| `overtimeApprovedAt` | `Instant` | `overtime_approved_at` | | Thời điểm duyệt tăng ca |
| `status` | `AttendanceStatus` | `status` | NOT NULL | `PRESENT` / `ABSENT` / `PAID_LEAVE` / `UNPAID_LEAVE` / `HOLIDAY` / `MISSING_PUNCH` |
| `note` | `String` | `note` | | Lý do điều chỉnh thủ công |
| `updatedByAccount` | `Account` | `updated_by_account_id` | FK, nullable | Người sửa cuối |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |
| `updatedAt` | `Instant` | `updated_at` | NOT NULL | Thời điểm cập nhật |

---

## 6. Tính lương

### Entity `PayrollPeriod` (bảng `payroll_periods`)

**Mô tả**: Kỳ lương tháng, vòng đời `DRAFT → CALCULATED → APPROVED → PAID → LOCKED` (có thể `CANCELLED` trước khi duyệt).

**Quan hệ**: Một-nhiều tới `Payslip.payrollPeriod`. Nhiều-một tới `Account` qua 4 cột người thực hiện.

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `year` | `Short` | `year` | NOT NULL | Năm lương |
| `month` | `Short` | `month` | NOT NULL, 1..12 | Tháng lương |
| `periodStart` | `LocalDate` | `period_start` | NOT NULL | Ngày đầu kỳ |
| `periodEnd` | `LocalDate` | `period_end` | NOT NULL | Ngày cuối kỳ |
| `status` | `PayrollPeriodStatus` | `status` | NOT NULL | `DRAFT` / `CALCULATED` / `APPROVED` / `PAID` / `LOCKED` / `CANCELLED` |
| `calculatedByAccount` | `Account` | `calculated_by_account_id` | FK, nullable | Người tính |
| `calculatedAt` | `Instant` | `calculated_at` | | Thời điểm tính gần nhất |
| `approvedByAccount` | `Account` | `approved_by_account_id` | FK, nullable | Người duyệt |
| `approvedAt` | `Instant` | `approved_at` | | Thời điểm duyệt |
| `paidByAccount` | `Account` | `paid_by_account_id` | FK, nullable | Người xác nhận đã trả |
| `paidAt` | `Instant` | `paid_at` | | Thời điểm xác nhận |
| `lockedByAccount` | `Account` | `locked_by_account_id` | FK, nullable | Người khóa |
| `lockedAt` | `Instant` | `locked_at` | | Thời điểm khóa |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |
| `updatedAt` | `Instant` | `updated_at` | NOT NULL | Thời điểm cập nhật |

### Entity `Payslip` (bảng `payslips`)

**Mô tả**: Phiếu lương nhân viên cho một kỳ lương, chứa các trường snapshot để không đổi khi dữ liệu nguồn thay đổi.

**Quan hệ**: Nhiều-một tới `PayrollPeriod`, `Employee`. Một-nhiều tới `PayslipItem`.

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `payrollPeriod` | `PayrollPeriod` | `payroll_period_id` | FK, NOT NULL | Kỳ lương |
| `employee` | `Employee` | `employee_id` | FK, NOT NULL | Nhân viên |
| `employeeCodeSnapshot` | `String` | `employee_code_snapshot` | NOT NULL | Mã nhân viên lúc tính |
| `employeeNameSnapshot` | `String` | `employee_name_snapshot` | NOT NULL | Tên nhân viên lúc tính |
| `workLocationSnapshot` | `String` | `work_location_snapshot` | NOT NULL | Địa điểm lúc tính |
| `organizationUnitSnapshot` | `String` | `organization_unit_snapshot` | NOT NULL | Phòng ban lúc tính |
| `contractualBasicSalary` | `BigDecimal` | `contractual_basic_salary` | NOT NULL, >= 0 | Lương tháng cấu hình |
| `scheduledWorkMinutes` | `Integer` | `scheduled_work_minutes` | NOT NULL, > 0 | Phút công chuẩn |
| `payableWorkMinutes` | `Integer` | `payable_work_minutes` | NOT NULL, >= 0 | Phút được hưởng lương |
| `approvedOvertimeMinutes` | `Integer` | `approved_overtime_minutes` | NOT NULL, mặc định 0, >= 0 | Phút tăng ca đã duyệt |
| `basicSalaryPay` | `BigDecimal` | `basic_salary_pay` | NOT NULL, >= 0 | Lương cơ bản thực nhận |
| `allowancePay` | `BigDecimal` | `allowance_pay` | NOT NULL, mặc định 0, >= 0 | Tổng phụ cấp |
| `overtimePay` | `BigDecimal` | `overtime_pay` | NOT NULL, mặc định 0, >= 0 | Tổng tăng ca |
| `grossPay` | `BigDecimal` | `gross_pay` | NOT NULL, >= 0 | Tổng thu nhập |
| `netPay` | `BigDecimal` | `net_pay` | NOT NULL, >= 0 | Thực nhận; hiện bằng gross |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |
| `updatedAt` | `Instant` | `updated_at` | Chỉ cập nhật khi kỳ chưa duyệt | Thời điểm cập nhật |

### Entity `PayslipItem` (bảng `payslip_items`)

**Mô tả**: Chi tiết khoản lương trên phiếu lương, dữ liệu snapshot không giữ khóa ngoại về nguồn.

**Quan hệ**: Nhiều-một tới `Payslip`.

| Thuộc tính | Kiểu Java | Cột DB | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `Long` | `id` | PK | Khóa chính |
| `payslip` | `Payslip` | `payslip_id` | FK, NOT NULL | Phiếu lương |
| `componentType` | `PayslipItemType` | `component_type` | NOT NULL | `BASIC_SALARY` / `ALLOWANCE` / `OVERTIME` |
| `description` | `String` | `description` | NOT NULL | Mô tả được snapshot |
| `quantity` | `BigDecimal` | `quantity` | NOT NULL, mặc định 1 | Ngày/giờ/số lượng |
| `unitRate` | `BigDecimal` | `unit_rate` | NOT NULL, >= 0 | Đơn giá snapshot |
| `multiplier` | `BigDecimal` | `multiplier` | NOT NULL, mặc định 1, > 0 | Hệ số snapshot |
| `amount` | `BigDecimal` | `amount` | NOT NULL, >= 0 | Thành tiền |
| `createdAt` | `Instant` | `created_at` | NOT NULL | Thời điểm tạo |
