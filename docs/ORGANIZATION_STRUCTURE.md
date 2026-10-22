# Cấu trúc doanh nghiệp, chức danh và phân quyền

## 1. Mục đích

Tài liệu này minh họa cách biểu diễn một doanh nghiệp trong HRM và làm rõ ba khái niệm dễ bị nhầm lẫn:

1. **Đơn vị tổ chức** cho biết nhân viên thuộc bộ phận nào.
2. **Vị trí công việc** cho biết nhân viên đang làm công việc gì và ở cấp bậc nào.
3. **Role phân quyền** cho biết tài khoản được phép thao tác gì, trong phạm vi nào.

Phần cơ cấu chi tiết bên dưới là **đề xuất dữ liệu demo** để kiểm thử. Seeder hiện tại mới có Ban giám đốc và ba phòng ban chính, chưa có toàn bộ nhóm, chức danh và tài khoản được mô tả ở đây.

---

## 2. Ba lớp dữ liệu độc lập

| Lớp | Bảng chính | Trả lời câu hỏi | Ví dụ |
|---|---|---|---|
| Cơ cấu tổ chức | `organization_units` | Nhân viên thuộc bộ phận nào? | Phòng Nhân sự, Phòng Kế toán |
| Địa điểm làm việc | `work_locations` | Nhân viên làm việc ở đâu? | Trụ sở chính, Chi nhánh Hà Nội |
| Chức danh | `job_positions` | Nhân viên làm công việc gì? | Trưởng phòng HR, Chuyên viên HR |
| Quan hệ công việc | `employee_assignments` | Nhân viên thuộc đơn vị nào, giữ vị trí gì và báo cáo cho ai? | An thuộc Phòng Nhân sự và báo cáo cho trưởng phòng |
| Phân quyền | `roles`, `account_role_assignments` | Tài khoản được làm gì và trên phạm vi nào? | Quản lý nhân viên trong Phòng Nhân sự |

Một nhân viên có chức danh quản lý không tự động có quyền quản lý trong hệ thống. Quyền chỉ phát sinh khi account có role assignment đang hiệu lực.

Ví dụ:

```text
Nguyễn Văn An
├── organization unit: HR
├── work location: HO
├── job position: HR_SPECIALIST
├── manager: HR_HEAD
└── role assignment: EMPLOYEE / SELF
```

---

## 3. Cơ cấu doanh nghiệp đề xuất

### 3.1. Cây đơn vị tổ chức

Hệ thống hiện hỗ trợ ba loại đơn vị: `BOARD`, `DEPARTMENT` và `TEAM`.

```text
Ban giám đốc (BOARD)
├── Phòng Nhân sự (HR - DEPARTMENT)
│
├── Phòng Kế toán (ACCOUNTING - DEPARTMENT)
│   ├── Nhóm Kế toán tổng hợp (ACC_GENERAL - TEAM)
│   └── Nhóm Kế toán tiền lương (ACC_PAYROLL - TEAM)
│
└── Phòng Vận hành (OPERATIONS - DEPARTMENT)
    ├── Nhóm Vận hành chi nhánh (OPS_BRANCH - TEAM)
    ├── Nhóm Vận hành kho (OPS_WAREHOUSE - TEAM)
    └── Nhóm Hỗ trợ vận hành (OPS_SUPPORT - TEAM)
```

`organization_units` là cây quản trị, không đại diện cho tỉnh thành hoặc nơi làm việc. Một nhóm có thể có nhân viên làm ở nhiều địa điểm khác nhau.

### 3.2. Cây địa điểm làm việc

```text
Trụ sở chính TP.HCM (HO - HEAD_OFFICE)
├── Chi nhánh Hà Nội (BRANCH-01 - BRANCH)
│   └── Kho chi nhánh Hà Nội (WAREHOUSE-02 - WAREHOUSE)
├── Chi nhánh Đà Nẵng (BRANCH-02 - BRANCH)
└── Kho trụ sở chính (WAREHOUSE-01 - WAREHOUSE)
```

Đơn vị và địa điểm là hai chiều độc lập. Ví dụ:

- Một nhân viên thuộc `HR` nhưng làm tại `BRANCH-01`.
- Một nhân viên thuộc `OPS_WAREHOUSE` nhưng làm tại `WAREHOUSE-02`.
- Một trưởng phòng thuộc `HR` và làm tại `HO`.

---

## 4. Cấp bậc trong một phòng ban

Ví dụ với Phòng Nhân sự:

```text
Giám đốc
└── Trưởng phòng Nhân sự
    ├── Chuyên viên Phòng Nhân sự
    └── Thực tập sinh Phòng Nhân sự
```

### 4.1. Danh mục vị trí đề xuất

| Code | Tên vị trí | `is_managerial` | Đơn vị sử dụng điển hình |
|---|---|:---:|---|
| `HR_HEAD` | Trưởng phòng Nhân sự | Có | `HR` |
| `HR_SPECIALIST` | Chuyên viên Phòng Nhân sự | Không | `HR` |
| `HR_INTERN` | Thực tập sinh Phòng Nhân sự | Không | `HR` |

`is_managerial=true` chỉ mô tả tính chất chức danh. Trường này không tự cấp permission.

### 4.2. Quan hệ báo cáo

Quan hệ cấp trên trực tiếp được lưu tại `employee_assignments.manager_employee_id`.

| Nhân viên | Vị trí | Đơn vị | Quản lý trực tiếp |
|---|---|---|---|
| HR Head | `HR_HEAD` | `HR` | Giám đốc |
| HR Specialist | `HR_SPECIALIST` | `HR` | HR Head |
| HR Intern | `HR_INTERN` | `HR` | HR Head |

Quan hệ báo cáo và quan hệ phân quyền không phải một. `manager_employee_id` không tự cho phép cấp trên đọc hoặc sửa dữ liệu của cấp dưới.

---

## 5. Mô hình phân quyền

### 5.1. Scope

| Scope | Phạm vi dữ liệu |
|---|---|
| `SELF` | Chỉ nhân viên liên kết với account hiện tại |
| `ORG_UNIT` | Đơn vị được gán và toàn bộ đơn vị con |
| `LOCATION` | Địa điểm được gán và toàn bộ địa điểm con |
| `COMPANY` | Toàn công ty |

Ví dụ phạm vi kế thừa:

```text
MANAGER @ HR
└── truy cập nhân viên thuộc HR

EMPLOYEE @ SELF
└── chỉ truy cập chính nhân viên liên kết với account
```

### 5.2. Role hệ thống và role đề xuất

| Role | Scope điển hình | Trạng thái | Mục đích |
|---|---|---|---|
| `EMPLOYEE` | `SELF` | System role đã seed | Tự xem hồ sơ, đơn từ, chấm công và phiếu lương |
| `HR_MANAGER` | `COMPANY` | System role đã seed | Quản trị nghiệp vụ nhân sự trên toàn công ty |
| `PAYROLL_ACCOUNTANT` | `COMPANY` | System role đã seed | Tính và kiểm tra bảng lương |
| `PAYROLL_APPROVER` | `COMPANY` | System role đã seed | Duyệt, xác nhận thanh toán và khóa kỳ lương |
| `DIRECTOR` | `COMPANY` | System role đã seed | Phê duyệt cuối các quyết định nghiệp vụ |
| `COMPANY_OWNER` | `COMPANY` | System role đã seed | Quản trị account, role và cấu hình công ty |
| `SYSTEM_ADMIN` | `COMPANY` | System role đã seed | Bootstrap Company Owner và quản trị hệ thống |
| `MANAGER` | `LOCATION` / `ORG_UNIT` | System role đã seed | Quản lý đơn vị: xem nhân viên, bảng công, đơn nghỉ và báo cáo nhân sự của một chi nhánh hoặc phòng ban |

`MANAGER` là một role dùng chung cho mọi cấp quản lý; phạm vi do scope của lần gán quyết định: `MANAGER @ LOCATION BRANCH-01` là quản lý chi nhánh Hà Nội (thấy cả kho con `WAREHOUSE-02`), `MANAGER @ ORG_UNIT OPERATIONS` là trưởng phòng Vận hành. Role chỉ đọc, không có lương hay dữ liệu nhạy cảm, và có chính sách `HR_ASSIGNABLE` nên HR gán được mà không cần Company Owner duyệt.

Không nên tạo một role cho mọi chức danh. Chuyên viên và thực tập sinh có thể cùng chỉ mang role `EMPLOYEE` nếu đều chỉ sử dụng chức năng tự phục vụ.

---

## 6. Bộ dữ liệu kiểm thử đề xuất

| Username | Đơn vị | Vị trí | Role assignment | Kết quả mong đợi |
|---|---|---|---|---|
| `hr_head` | `HR` | `HR_HEAD` | `MANAGER / ORG_UNIT / HR` | Truy cập nhân viên Phòng Nhân sự, không truy cập phòng khác |
| `hr_specialist` | `HR` | `HR_SPECIALIST` | `EMPLOYEE / SELF` | Chỉ truy cập bản thân |
| `hr_intern` | `HR` | `HR_INTERN` | `EMPLOYEE / SELF` | Chỉ truy cập bản thân |
| `hr_global` | `HR` | `HR_SPECIALIST` | `HR_MANAGER / COMPANY` | Truy cập nhân sự toàn công ty |
| `accounting_staff` | `ACC_GENERAL` | Nhân viên kế toán | `EMPLOYEE / SELF` | Dùng để kiểm tra truy cập chéo bị từ chối |

### 6.1. Các tình huống cần kiểm thử

1. `hr_head` xem được `hr_specialist` và `hr_intern` thuộc Phòng Nhân sự.
2. `hr_head` không xem được nhân viên thuộc `ACCOUNTING` hoặc `OPERATIONS`.
3. `hr_specialist` và `hr_intern` chỉ truy cập được dữ liệu bản thân.
4. Một nhân viên được đặt `manager_employee_id = hr_head` nhưng nằm ngoài scope `HR` vẫn không tự động thuộc phạm vi quyền của `hr_head`.
5. `hr_global` truy cập được nhân viên ở mọi phòng ban do có scope `COMPANY`.
6. Khi role assignment hết hạn hoặc bị thu hồi, quyền tương ứng không còn hiệu lực.

---

## 7. Seeder hiện có

Dữ liệu seed được chia theo trách nhiệm và chạy theo `@Order`. Mỗi nhóm có cờ bật/tắt riêng:

| Thứ tự | Seeder | Cờ cấu hình | Dữ liệu tạo |
|---|---|---|---|
| 50 | `CompanyProfileSeeder` | `COMPANY_SEED_ENABLED` (mặc định bật) | Hồ sơ doanh nghiệp |
| 60 | `OrganizationStructureSeeder` | `COMPANY_SEED_ENABLED` | 5 địa điểm `HO`, `BRANCH-01`, `BRANCH-02`, `WAREHOUSE-01`, `WAREHOUSE-02`; đơn vị `BOARD` và 3 phòng ban `HR`, `ACCOUNTING`, `OPERATIONS` |
| 70 | `JobPositionSeeder` | `COMPANY_SEED_ENABLED` | 7 chức danh |
| 80 | `WorkShiftSeeder` | `COMPANY_SEED_ENABLED` | Ca hành chính `OFFICE_DAY` 08:00–17:00 |
| 90 | `CompanyHolidaySeeder` | `COMPANY_SEED_ENABLED` | Ngày lễ, Tết hưởng nguyên lương năm 2025 và 2026 theo Điều 112 Bộ luật Lao động 2019 (lễ rơi vào Chủ nhật được nghỉ bù); năm đã có ngày lễ nào thì bỏ qua cả năm |
| 100 | `RoleSeeder` | `RBAC_SEED_ENABLED` (mặc định bật) | 8 system role |
| 200 | `PermissionSeeder` | `RBAC_SEED_ENABLED` | Danh mục 48 permission |
| 300 | `RolePermissionSeeder` | `RBAC_SEED_ENABLED` | Permission mặc định của từng system role |
| 400 | `SystemAdminSeeder` | `ADMIN_SEED_ENABLED` (mặc định bật) | Account bootstrap và gán role `SYSTEM_ADMIN` đã có sẵn |
| 500 | `CompanyOwnerSeeder` | `OWNER_SEED_ENABLED` (mặc định bật) | Company Owner `owner`/`password` (role `COMPANY_OWNER`, `DIRECTOR`, `PAYROLL_APPROVER`, `EMPLOYEE`), phân công tại `BOARD`, lương cơ bản 50.000.000, hồ sơ bảo hiểm/thuế (đóng trên 50.000.000, vùng 1), 2 con phụ thuộc, chấm công 2 năm |
| 505 | `AllowancePolicySeeder` | `COMPANY_SEED_ENABLED` | Phụ cấp chức vụ theo tháng (`DIRECTOR` 10 tr, `OPERATIONS_MANAGER` 5 tr, `BRANCH_MANAGER` 4 tr, `WAREHOUSE_SUPERVISOR` 3 tr, `TEAM_LEAD` 2 tr, `PAYROLL_ACCOUNTANT` và `HR_SPECIALIST` 1 tr) và phụ cấp thâm niên (1–3 năm 3%, 3–5 năm 5%, từ 5 năm 7% lương cơ bản); Company Owner duyệt, hiệu lực từ ngày Owner vào làm. Không có Owner thì bỏ qua |
| 510 | `StaffSeeder` | `STAFF_SEED_ENABLED` (mặc định bật), file `STAFF_SEED_FILE` | Nhân sự khai báo trong file JSON, mặc định `api/src/main/resources/seed/staff.json` |
| 520 | `PayrollSeeder` | `PAYROLL_SEED_ENABLED` (mặc định bật) | Kỳ lương mọi tháng từ tháng nhân viên đầu tiên vào làm đến tháng trước, tính bằng engine lương thật; chỉ chạy khi chưa có kỳ lương nào |

Ngoài Company Owner và nhân sự trong file seed, mọi người dùng HRM được tạo qua API.

Chấm công seed (Owner và nhân sự trong file) phần lớn đúng giờ, có ngày đi muộn/về sớm 30 phút và
ngày tăng ca ngẫu nhiên (về muộn 1–3 giờ, khoảng 1/12 số ngày, 1/3 trong tháng hiện tại). Tăng ca
trước tháng hiện tại đã được người duyệt lúc đó quyết định (hệ số 150%); tăng ca tháng hiện tại để
chờ, nên thẻ "Tăng ca chờ bạn quyết định" có sẵn vài ngày để thử.

`StaffSeeder` đọc một mảng JSON; mỗi phần tử tạo nhân viên `ACTIVE`, account `ACTIVE`, role, lịch
sử phân công chính, lịch sử lương cơ bản, hồ sơ bảo hiểm/thuế, người phụ thuộc và chấm công `PRESENT` từ ngày vào làm đến hôm qua,
rồi ghi đơn nghỉ, ngày vắng, ngày thiếu chấm công và yêu cầu cấp role khai báo kèm. Username đã tồn tại thì bỏ
qua, nên thêm người vào file rồi khởi động lại chỉ tạo người mới.

```json
{
  "username": "employee",
  "password": "password",
  "fullName": "Lê Văn Cường",
  "email": "employee@hrm.local",
  "profile": { "gender": "MALE", "dateOfBirth": "1998-09-02", "highestEducationLevel": "COLLEGE",
    "major": "Công nghệ kỹ thuật điện", "institution": "Trường Cao đẳng Kỹ thuật Cao Thắng", "graduationYear": 2019 },
  "roles": { "EMPLOYEE": "SELF" },
  "assignments": [
    { "monthsAgo": 10, "organizationUnit": "OPERATIONS", "workLocation": "BRANCH-01", "position": "GENERAL_STAFF", "manager": "owner" },
    { "monthsAgo": 4, "organizationUnit": "HR", "position": "GENERAL_STAFF", "manager": "hr" }
  ],
  "salaries": [
    { "monthsAgo": 10, "baseSalary": 8000000, "reason": "Mức lương ban đầu" },
    { "monthsAgo": 1, "baseSalary": 8500000, "reason": "Điều chỉnh lương" }
  ],
  "payrollProfiles": [
    { "monthsAgo": 10, "insuranceSalary": 8000000, "wageRegion": 2 },
    { "monthsAgo": 4, "insuranceSalary": 8000000, "wageRegion": 1 },
    { "monthsAgo": 1, "insuranceSalary": 8500000, "wageRegion": 1 }
  ],
  "dependents": [],
  "leaves": [
    { "workDaysAgo": 150, "days": 3, "type": "UNPAID", "status": "APPROVED", "reason": "Về quê lo việc gia đình", "comment": "Đồng ý." },
    { "workDaysAgo": 30, "part": "AFTERNOON", "type": "ANNUAL", "status": "APPROVED", "reason": "Họp phụ huynh cho con" },
    { "workDaysAgo": -4, "type": "ANNUAL", "status": "PENDING", "reason": "Nghỉ phép giải quyết việc cá nhân" }
  ],
  "absences": [{ "workDaysAgo": 40, "note": "Không đến làm, gọi điện không liên lạc được" }],
  "missingPunches": [2],
  "roleRequests": [
    { "role": "HR_MANAGER", "scope": "ORG_UNIT", "requestedBy": "hr", "status": "PENDING", "daysAgo": 2,
      "effectiveFromDaysAgo": -14, "effectiveToDaysAgo": -30, "reason": "Phụ trách nhân sự phòng khi chị Bình nghỉ phép" }
  ]
}
```

- `profile` (tùy chọn, từng trường cũng tùy chọn): giới tính (`MALE`, `FEMALE`, `OTHER`,
  `UNDISCLOSED`), ngày sinh và học vấn (`highestEducationLevel` là `HIGH_SCHOOL`, `COLLEGE`, `BACHELOR`,
  `MASTER`, `DOCTORATE`), để hồ sơ và báo cáo cơ cấu học vấn có dữ liệu. Owner được seed sẵn Thạc sĩ.
- `roles`: mã role → scope (`SELF`, `COMPANY`, `ORG_UNIT`, `LOCATION`), hiệu lực từ ngày vào làm.
  `ORG_UNIT`/`LOCATION` lấy đơn vị/địa điểm của phân công hiện hành.
- `assignments`: các giai đoạn phân công chính, **từ cũ đến mới**. Mỗi giai đoạn bắt đầu `monthsAgo`
  tháng trước ngày chạy seed và kết thúc ngay trước giai đoạn sau; giai đoạn cuối là phân công hiện
  hành. Ngày vào làm là ngày bắt đầu giai đoạn đầu.
- `organizationUnit`, `position`, `workLocation`, `shift`: mã đã seed; `workLocation` mặc định `HO`,
  `shift` mặc định `OFFICE_DAY`. Chấm công mỗi ngày theo ca của giai đoạn chứa ngày đó.
- `manager`: username của quản lý trực tiếp trong giai đoạn đó (người duyệt đơn nghỉ và tăng ca theo
  phân công hiện hành); phải là `owner` hoặc người đứng trước trong file. Bỏ trống thì người duyệt
  là Giám đốc.
- `salaries`: lịch sử lương cơ bản, **từ cũ đến mới**, mỗi mức kết thúc ngay trước mức sau. Mức đầu
  phải cùng `monthsAgo` với phân công đầu (lương bắt đầu từ ngày vào làm). Điều chuyển kèm đổi lương
  thì dùng cùng `monthsAgo` với phân công; điều chỉnh lương không điều chuyển thì chỉ thêm mức lương;
  điều chuyển giữ nguyên lương thì không thêm gì. Người ghi nhận là Company Owner.
- `payrollProfiles`: hồ sơ bảo hiểm/thuế, cùng quy tắc thời gian như `salaries` và **bắt buộc** (thiếu
  thì tính lương báo lỗi). Mỗi hồ sơ có lương đóng bảo hiểm `insuranceSalary` và vùng lương tối thiểu
  `wageRegion` 1–4; nhân viên luôn là người cư trú và đóng đủ BHXH/BHYT/BHTN. Đổi lương hay chuyển địa
  điểm sang vùng khác thì thêm hồ sơ mới cùng `monthsAgo`.
- `dependents` (tùy chọn): người phụ thuộc giảm trừ thuế, mỗi người có `fullName`, `identifier` và
  `monthsAgo` bắt đầu (không trước ngày vào làm), còn hiệu lực đến nay.
- `workDaysAgo` (dùng trong `leaves`, `absences`, `missingPunches`): ngày làm việc thứ N tính lùi từ
  hôm qua (1 là ngày làm việc gần nhất); số âm là ngày làm việc sắp tới (-1 là ngày làm việc đầu tiên
  sau hôm nay, tối đa 90 ngày). Ngày luôn rơi vào ngày làm việc theo ca, không vào Chủ nhật hay ngày lễ.
  Hai mục không được trùng ngày.
- `leaves` (tùy chọn): đơn nghỉ `days` ngày làm việc liên tiếp (mặc định 1), hoặc nửa ngày với `part`
  `MORNING`/`AFTERNOON` (nửa còn lại vẫn đi làm). `type` là `ANNUAL`, `SICK`, `MATERNITY`, `UNPAID`,
  `OTHER`; cách trả lương theo loại đơn. `status` là `DRAFT`, `PENDING`, `APPROVED`, `REJECTED`,
  `CANCELLED`; `comment` là ý kiến người duyệt, bắt buộc khi từ chối. Người duyệt là người duyệt của
  ngày gửi đơn; đơn gửi 7 ngày trước ngày nghỉ, riêng nghỉ ốm gửi sáng hôm đó. Đơn đã duyệt đổi ngày
  công như khi duyệt thật (`PAID_LEAVE`, `UNPAID_LEAVE`, `SICK_LEAVE`, `MATERNITY_LEAVE`); đơn phép năm
  đã duyệt hoặc đang chờ phải còn đủ phép.
- `absences` (tùy chọn): ngày vắng không phép HR đã đối soát (`UNAUTHORIZED_ABSENCE`, kèm `note`),
  chỉ trong quá khứ; người ghi là HR Manager.
- `missingPunches` (tùy chọn): ngày thiếu chấm công (`MISSING_PUNCH`) chờ HR đối soát. Chỉ ghi nếu
  ngày đó thuộc tháng hiện tại, để các tháng trước vẫn tính lương được.
- `roleRequests` (tùy chọn): yêu cầu cấp role (chỉ role cần Owner duyệt) cho account này, do
  `requestedBy` gửi `daysAgo` ngày trước và Company Owner quyết định ngày hôm sau. `effectiveFromDaysAgo`
  và `effectiveToDaysAgo` là hiệu lực (số âm là ngày tới); `note` là ghi chú của Owner, hoặc lý do
  hủy, bắt buộc khi từ chối hoặc hủy. Yêu cầu đã duyệt tạo luôn phân quyền tương ứng.

File mặc định có `hr` (Nhân viên rồi Chuyên viên nhân sự, HR_MANAGER, quản lý là `owner`; lương
9 → 12 triệu khi lên Chuyên viên → 13,5 triệu; 1 người phụ thuộc từ 12 tháng trước), `employee`
(chuyển từ Vận hành ở `BRANCH-01`, vùng 2, sang phòng Nhân sự ở trụ sở, vùng 1, quản lý là `hr`; lương
8 triệu giữ nguyên khi chuyển, 8,5 triệu từ 1 tháng trước; không có người phụ thuộc) và `accountant`
(Kế toán tiền lương ở phòng Kế toán từ 14 tháng trước, role `PAYROLL_ACCOUNTANT`, quản lý là `owner`;
lương 11 → 12,5 triệu từ 3 tháng trước; 1 con phụ thuộc từ 6 tháng trước), cùng mật khẩu
`password`. Lương đóng bảo hiểm bằng lương cơ bản.

Để thấy quản lý theo chi nhánh, file còn có hai chi nhánh, tất cả thuộc phòng Vận hành, vùng lương 1,
mật khẩu `password`:

| Username | Địa điểm | Chức danh | Role | Quản lý trực tiếp |
|---|---|---|---|---|
| `branch-hn` | `BRANCH-01` Hà Nội | Quản lý chi nhánh | `MANAGER @ LOCATION`, `EMPLOYEE` | `owner` |
| `staff-hn1`, `staff-hn2` | `BRANCH-01` | Nhân viên | `EMPLOYEE` | `branch-hn` |
| `warehouse-hn` | `WAREHOUSE-02` (kho con của Hà Nội) | Giám sát kho | `EMPLOYEE` | `branch-hn` |
| `branch-dn` | `BRANCH-02` Đà Nẵng (từ 12 tháng trước, trước đó ở trụ sở) | Quản lý chi nhánh | `MANAGER @ LOCATION`, `EMPLOYEE` | `owner` |
| `staff-dn1`, `staff-dn2` | `BRANCH-02` | Nhân viên | `EMPLOYEE` | `branch-dn` |

`branch-hn` chỉ thấy 4 người ở Hà Nội và kho Hà Nội, `branch-dn` chỉ thấy 3 người ở Đà Nẵng; mỗi
người có đơn nghỉ đã duyệt và đơn sắp tới chờ quản lý chi nhánh của mình duyệt.

Đơn từ và ngày công khác thường trong file mặc định:

| Người | Đơn nghỉ | Ngày công khác | Yêu cầu cấp role |
|---|---|---|---|
| `hr` | Phép năm 3 ngày, ốm 2 ngày, phép nửa buổi sáng (đã duyệt); không lương bị từ chối; phép 2 ngày sắp tới chờ `owner` duyệt | — | — |
| `employee` | Không lương 3 ngày, phép 1 ngày, ốm 1 ngày, phép nửa buổi chiều (đã duyệt); phép sắp tới chờ `hr` duyệt; 1 đơn đã hủy; 1 đơn nháp | 2 ngày vắng không phép; 1 ngày thiếu chấm công tháng này | `HR_MANAGER` phòng Nhân sự: 1 đã duyệt (đã hết hạn), 1 đang chờ `owner`; `PAYROLL_ACCOUNTANT` đã hủy |
| `accountant` | Phép 2 ngày, ốm 1 ngày, 2 lần khám thai (đã duyệt); phép sắp tới bị từ chối; phép sắp tới chờ `owner` duyệt | 1 ngày vắng không phép; 1 ngày thiếu chấm công tháng này | `PAYROLL_APPROVER` bị từ chối |

Nhờ vậy `owner` và `hr` có sẵn đơn chờ duyệt trên trang Tổng quan, và Owner có yêu cầu cấp role chờ quyết định.

Vòng lương trên dữ liệu seed: `accountant` tạo kỳ và tính lương, `owner` (giữ `PAYROLL_APPROVER`)
duyệt, xác nhận đã trả và khóa kỳ; mỗi nhân viên xem phiếu của mình từ khi kỳ được duyệt.

`PayrollSeeder` chạy đúng vòng đó qua các service lương (đóng vai `accountant` rồi `owner`) cho mọi
tháng từ tháng nhân viên đầu tiên vào làm (Company Owner, khoảng 2 năm trước) đến tháng trước, rồi lùi mốc thời gian về lúc làm lương thật: lương tháng M tạo ngày 1,
tính ngày 2, duyệt ngày 4, trả và nộp thuế ngày 10, khóa ngày 20 của tháng M+1. Tháng chưa có quy
tắc thuế hoặc bảo hiểm (trước 2020, hoặc sau năm cuối nạp trong `V8__reference_data.sql`) được bỏ
qua kèm cảnh báo trong log, nên chạy seed nhiều năm sau vẫn không lỗi; muốn có các kỳ đó thì nạp thêm quy tắc.

| Kỳ | Trạng thái |
|---|---|
| Từ tháng đầu tiên đến 3 tháng trước | Đã khóa |
| 2 tháng trước | Đã trả, chưa khóa |
| Tháng trước | Đã tính, chờ `owner` duyệt (có trên trang Tổng quan) |

Có sẵn vài khoản điều chỉnh: thưởng cho `hr`, khấu trừ hoàn tạm ứng và phụ cấp kiêm nhiệm cho
`employee`, trợ cấp không chịu thuế và khấu trừ đồng phục cho `accountant`. Kỳ đã duyệt khóa chấm
công và đơn nghỉ của tháng đó, như khi dùng thật; muốn sửa công những tháng này thì tắt seed lương.

Đây là dữ liệu thật đang có, khác với bộ dữ liệu đề xuất ở mục 6: seeder chưa tạo các nhóm cấp
`TEAM`, các chức danh `HR_HEAD`/`HR_INTERN` hay các account theo phòng ban; quản lý theo chi nhánh
thì đã có sẵn (`branch-hn`, `branch-dn` giữ `MANAGER` @ `LOCATION`). Muốn kiểm thử các tình huống ở mục 6.1 thì phải tạo tay qua API sau khi bootstrap.

> `JobPositionSeeder` tạo các chức danh `BRANCH_MANAGER`, `WAREHOUSE_SUPERVISOR` và `TEAM_LEAD`.
> Đây chỉ là **chức danh**; các system role trùng tên đã bị loại khỏi danh mục role. Trùng tên
> không tạo ra quyền — đúng theo nguyên tắc ở mục 2.

---

## 8. Quy tắc ghi nhớ

```text
organization_units = thuộc bộ phận nào
work_locations     = làm việc ở đâu
job_positions      = làm công việc gì, cấp bậc nào
manager_employee_id = báo cáo cho ai
roles + scope      = được phép làm gì, trên dữ liệu nào
```

Một chức danh có thể đi cùng nhiều role khác nhau, và cùng một role có thể được dùng cho nhiều chức danh. Việc gán role cần dựa trên trách nhiệm thực tế, không dựa riêng vào tên chức danh.
