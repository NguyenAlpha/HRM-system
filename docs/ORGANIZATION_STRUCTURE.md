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
DEPARTMENT_MANAGER @ HR
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
| `DEPARTMENT_MANAGER` | `ORG_UNIT` | Custom role đề xuất | Quản lý nhân viên, đơn từ và chấm công của một phòng ban |

`DEPARTMENT_MANAGER` không được seed sẵn. Người có quyền quản trị RBAC tạo role này, chỉ chọn các permission `DELEGABLE`, rồi gán với scope `ORG_UNIT` phù hợp.

Không nên tạo một role cho mọi chức danh. Chuyên viên và thực tập sinh có thể cùng chỉ mang role `EMPLOYEE` nếu đều chỉ sử dụng chức năng tự phục vụ.

---

## 6. Bộ dữ liệu kiểm thử đề xuất

| Username | Đơn vị | Vị trí | Role assignment | Kết quả mong đợi |
|---|---|---|---|---|
| `hr_head` | `HR` | `HR_HEAD` | `DEPARTMENT_MANAGER / ORG_UNIT / HR` | Truy cập nhân viên Phòng Nhân sự, không truy cập phòng khác |
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
| 100 | `RoleSeeder` | `RBAC_SEED_ENABLED` (mặc định bật) | 7 system role |
| 200 | `PermissionSeeder` | `RBAC_SEED_ENABLED` | Danh mục 49 permission |
| 300 | `RolePermissionSeeder` | `RBAC_SEED_ENABLED` | Permission mặc định của từng system role |
| 400 | `SystemAdminSeeder` | `ADMIN_SEED_ENABLED` (mặc định bật) | Account bootstrap và gán role `SYSTEM_ADMIN` đã có sẵn |
| 500 | `UserSeeder` | `USER_SEED_ENABLED` (mặc định **tắt**) | 3 account demo để kiểm thử phân quyền |

`UserSeeder` là seeder duy nhất tạo nhân viên và tài khoản giả, nên mặc định tắt. Ba account nó
tạo khi bật:

| Username | Nhân viên | Role assignment |
|---|---|---|
| `employee01` | `EMP001` | `EMPLOYEE` / `SELF` |
| `hr01` | `EMP002` | `EMPLOYEE` / `SELF` và `HR_MANAGER` / `COMPANY` |
| `payroll01` | `EMP003` | `EMPLOYEE` / `SELF` và `PAYROLL_ACCOUNTANT` / `COMPANY` |

Mọi account demo đều nhận `EMPLOYEE` / `SELF` trước, rồi mới nhận thêm role nghiệp vụ nếu có.
Đây là minh họa cho tính cộng dồn của role assignment: quyền tự phục vụ và quyền nghiệp vụ là
hai assignment riêng, thu hồi cái này không ảnh hưởng cái kia.

Đây là dữ liệu thật đang có, khác với bộ dữ liệu đề xuất ở mục 6: seeder chưa tạo các nhóm cấp
`TEAM`, các chức danh `HR_HEAD`/`HR_INTERN`, role `DEPARTMENT_MANAGER` hay các account theo phòng
ban. Muốn kiểm thử các tình huống ở mục 6.1 thì phải tạo tay qua API sau khi bootstrap.

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
