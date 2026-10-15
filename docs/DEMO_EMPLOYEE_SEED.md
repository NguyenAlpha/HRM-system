# Bộ dữ liệu seed nhân viên mẫu

> Đây là **đặc tả dữ liệu Markdown**, chưa phải seeder chạy vào database. Toàn bộ tên,
> email, giấy tờ và tài khoản bên dưới là dữ liệu giả lập. Mốc kiểm tra của bộ dữ liệu:
> **2026-10-11**, múi giờ `Asia/Ho_Chi_Minh`. Chỉ dùng trong môi trường demo/test.

## 1. Mục tiêu và phạm vi

- Có sơ đồ báo cáo từ giám đốc đến các phòng, chi nhánh, kho và nhân viên.
- Có người làm việc từ 2010–2011 để thử thâm niên, người mới thử việc và một thực tập sinh.
- Mỗi nhân viên có hồ sơ, phân công, lương cơ bản và hồ sơ thuế/bảo hiểm đủ để chuẩn bị
  kiểm thử kỳ lương 2026. Một số người có lịch sử thay đổi lương; một người có lịch sử đổi quản lý.
- Mục 8 đặc tả lịch sử chấm công và các đơn nghỉ liên quan; chưa có seeder thực thi, phiếu lương
  hoặc kỳ lương. Dữ liệu này chỉ được ghi vào database khi triển khai seeder nghiệp vụ.
- `WAREHOUSE-02` hiện để trống có chủ đích, để kiểm tra địa điểm không có nhân viên.
- Mục 9 bổ sung 24 nhân viên vào bộ 12 người ở mục 2–8. Các số kiểm tra trong mục 7 và 8.5
  chỉ áp dụng cho bộ gốc; số tổng sau mở rộng nằm ở mục 9.7.

### Danh mục tham chiếu

Seeder hiện có đã tạo `BOARD`, `HR`, `ACCOUNTING`, `OPERATIONS`; các địa điểm `HO`,
`BRANCH-01`, `BRANCH-02`, `WAREHOUSE-01`, `WAREHOUSE-02`; và ca `OFFICE_DAY`
(08:00–17:00, nghỉ 12:00–13:00, 480 phút công chuẩn). Bộ dữ liệu này dùng các code đó.

Để tên chức danh trên sơ đồ đúng nghĩa, bộ seed nhân viên **cần tạo thêm** hai `job_positions`
trước khi tạo phân công:

| Code | Chức danh | `is_managerial` | Ghi chú |
|---|---|---|---|
| `HR_MANAGER_DEMO` | Trưởng phòng Nhân sự | `true` | Chức danh, khác role RBAC `HR_MANAGER` |
| `INTERN_DEMO` | Thực tập sinh | `false` | Nhãn chức danh; hệ thống chưa có `EmploymentType.INTERN` |

Các chức danh còn lại đã có: `DIRECTOR`, `PAYROLL_ACCOUNTANT`, `OPERATIONS_MANAGER`,
`BRANCH_MANAGER`, `WAREHOUSE_SUPERVISOR`, `TEAM_LEAD`, `GENERAL_STAFF`.

## 2. Hồ sơ nhân viên

`employee_code` trong bảng là khóa tham chiếu **đề xuất cho seeder trực tiếp**, không phải
giá trị mà API tạo nhân viên hiện nay sẽ trả về (API tự sinh mã theo chức danh).
`seniority_start_date = hire_date` cho cả 12 người. `termination_date`, `termination_reason`,
`deleted_at`, `deleted_by_account_id`, `deletion_reason` đều `null`. ID do database cấp;
`created_at` và `updated_at` lấy thời điểm seed chạy. API tạo nhân viên hiện luôn khởi tạo
`PROBATION`, nên seeder trực tiếp phải đặt `ACTIVE` rõ ràng cho 10 người đã qua thử việc.

| STT | `employee_code` | `full_name` | `date_of_birth` | `gender` | `hire_date` | `employment_status` | Kịch bản |
|---:|---|---|---|---|---|---|---|
| 01 | `DEMO-DIR-001` | Nguyễn Minh An | 1979-04-12 | `MALE` | 2010-01-04 | `ACTIVE` | Giám đốc, hơn 15 năm thâm niên |
| 02 | `DEMO-HR-001` | Trần Thu Hà | 1985-08-09 | `FEMALE` | 2014-06-02 | `ACTIVE` | Trưởng phòng Nhân sự |
| 03 | `DEMO-PAY-001` | Lê Quốc Bảo | 1989-02-16 | `MALE` | 2018-04-02 | `ACTIVE` | Kế toán lương |
| 04 | `DEMO-OPS-001` | Phạm Hoàng Long | 1982-11-25 | `MALE` | 2012-09-03 | `ACTIVE` | Quản lý vận hành lâu năm |
| 05 | `DEMO-BRH-001` | Vũ Ngọc Mai | 1987-07-14 | `FEMALE` | 2016-07-04 | `ACTIVE` | Quản lý chi nhánh Hà Nội |
| 06 | `DEMO-BRD-001` | Võ Minh Khang | 1990-03-21 | `MALE` | 2019-03-04 | `ACTIVE` | Quản lý chi nhánh Đà Nẵng |
| 07 | `DEMO-WH-001` | Đặng Đức Phúc | 1988-10-03 | `MALE` | 2017-08-01 | `ACTIVE` | Giám sát kho |
| 08 | `DEMO-TL-001` | Nguyễn Thanh Tùng | 1991-12-17 | `MALE` | 2020-02-03 | `ACTIVE` | Trưởng nhóm vận hành |
| 09 | `DEMO-SR-001` | Hồ Văn Sơn | 1984-05-28 | `MALE` | 2011-05-02 | `ACTIVE` | Nhân viên lâu năm, có đổi quản lý |
| 10 | `DEMO-ST-001` | Bùi Quỳnh Anh | 1997-09-05 | `FEMALE` | 2023-07-03 | `ACTIVE` | Nhân viên chi nhánh |
| 11 | `DEMO-NEW-001` | Đỗ Gia Huy | 2002-06-18 | `MALE` | 2026-09-01 | `PROBATION` | Nhân viên mới thử việc |
| 12 | `DEMO-INT-001` | Lâm Phương Linh | 2005-07-12 | `FEMALE` | 2026-10-01 | `PROBATION` | Thực tập sinh mô phỏng |

### Học vấn và liên hệ

`work_email = lower(employee_code) + "@hrm-demo.test"`, ví dụ
`demo-dir-001@hrm-demo.test`; `phone = "00000000" + STT hai chữ số`, ví dụ
`0000000001`. Tên miền `.test` và số điện thoại này chỉ phục vụ demo.

| Code rút gọn | `highest_education_level` | `major` | `institution` | `graduation_year` |
|---|---|---|---|---:|
| DIR | `MASTER` | Quản trị kinh doanh | Đại học Demo TP.HCM | 2004 |
| HR | `MASTER` | Quản trị nhân lực | Đại học Demo Hà Nội | 2010 |
| PAY | `BACHELOR` | Kế toán | Đại học Demo TP.HCM | 2011 |
| OPS | `BACHELOR` | Quản trị vận hành | Đại học Demo TP.HCM | 2004 |
| BRH | `BACHELOR` | Quản trị kinh doanh | Đại học Demo Hà Nội | 2009 |
| BRD | `BACHELOR` | Quản trị kinh doanh | Đại học Demo Đà Nẵng | 2012 |
| WH | `COLLEGE` | Quản lý kho | Cao đẳng Demo TP.HCM | 2008 |
| TL | `BACHELOR` | Logistics | Đại học Demo TP.HCM | 2013 |
| SR | `COLLEGE` | Vận hành | Cao đẳng Demo TP.HCM | 2004 |
| ST | `BACHELOR` | Logistics | Đại học Demo Hà Nội | 2019 |
| NEW | `BACHELOR` | Quản trị kinh doanh | Đại học Demo TP.HCM | 2024 |
| INT | `HIGH_SCHOOL` | Công nghệ thông tin (đang học) | Đại học Demo TP.HCM | `null` |

`Code rút gọn` là phần giữa của `DEMO-<code>-001`, không phải cột trong database.
Với INT, `HIGH_SCHOOL` là bậc **đã hoàn thành**; model hiện chưa có trạng thái “đang học đại học”.

### Dữ liệu nhạy cảm giả lập

Để đủ tất cả cột hồ sơ mà không dùng giấy tờ hay tài khoản ngân hàng thật, áp dụng quy tắc sau
cho từng dòng của bảng nhân viên:

| Trường | Giá trị seed |
|---|---|
| `national_id` | `"TEST-ID-" + employee_code` |
| `personal_email` | `lower(employee_code) + "@example.test"` |
| `address` | `"Địa chỉ giả lập - " + tên thành phố của work_location` |
| `tax_code` | `"TEST-TAX-" + employee_code` |
| `bank_name` | `NGÂN HÀNG DEMO` |
| `bank_account_number` | `"TEST-ACC-" + employee_code` |
| `bank_account_holder` | `full_name` |

Các chuỗi `TEST-*` là định danh giả, chỉ hợp lệ cho kiểm thử lưu trữ và hiển thị; không dùng
để gửi hồ sơ thuế, bảo hiểm hay thanh toán. Chỉ role có quyền `employee.sensitive.*` được đọc/sửa.

## 3. Phân công và sơ đồ báo cáo

Mỗi dòng là một phân công chính (`is_primary=true`, `effective_to=null`,
`shift=OFFICE_DAY`). `manager` trỏ đến `employee_code`, không trỏ đến chức danh hoặc role RBAC.
Tất cả là `FULL_TIME` trừ INT dùng `TEMPORARY`. Ngày hiệu lực bằng `hire_date`, trừ SR có lịch sử
đổi quản lý ở bảng bên dưới.

| Nhân viên | Đơn vị | Địa điểm | Chức danh | Manager | `employment_type` | `effective_from` |
|---|---|---|---|---|---|---|
| DIR | `BOARD` | `HO` | `DIRECTOR` | `null` | `FULL_TIME` | 2010-01-04 |
| HR | `HR` | `HO` | `HR_MANAGER_DEMO` | DIR | `FULL_TIME` | 2014-06-02 |
| PAY | `ACCOUNTING` | `HO` | `PAYROLL_ACCOUNTANT` | DIR | `FULL_TIME` | 2018-04-02 |
| OPS | `OPERATIONS` | `HO` | `OPERATIONS_MANAGER` | DIR | `FULL_TIME` | 2012-09-03 |
| BRH | `OPERATIONS` | `BRANCH-01` | `BRANCH_MANAGER` | OPS | `FULL_TIME` | 2016-07-04 |
| BRD | `OPERATIONS` | `BRANCH-02` | `BRANCH_MANAGER` | OPS | `FULL_TIME` | 2019-03-04 |
| WH | `OPERATIONS` | `WAREHOUSE-01` | `WAREHOUSE_SUPERVISOR` | OPS | `FULL_TIME` | 2017-08-01 |
| TL | `OPERATIONS` | `HO` | `TEAM_LEAD` | OPS | `FULL_TIME` | 2020-02-03 |
| SR | `OPERATIONS` | `HO` | `GENERAL_STAFF` | TL | `FULL_TIME` | 2020-02-03 |
| ST | `OPERATIONS` | `BRANCH-01` | `GENERAL_STAFF` | BRH | `FULL_TIME` | 2023-07-03 |
| NEW | `OPERATIONS` | `HO` | `GENERAL_STAFF` | TL | `FULL_TIME` | 2026-09-01 |
| INT | `HR` | `HO` | `INTERN_DEMO` | HR | `TEMPORARY` | 2026-10-01 |

Lịch sử riêng của SR: từ **2011-05-02 đến 2020-02-02** có phân công chính ở
`OPERATIONS`/`HO`/`GENERAL_STAFF`, ca `OFFICE_DAY`, manager DIR; từ 2020-02-03
chuyển sang manager TL. Hai khoảng không chồng lấn. Lý do dòng mới: “Đổi người quản lý”.

Sơ đồ mong đợi:

```text
DIR
├── HR
│   └── INT
├── PAY
└── OPS
    ├── BRH
    │   └── ST
    ├── BRD
    ├── WH
    └── TL
        ├── SR
        └── NEW
```

## 4. Lịch sử lương cơ bản

Tiền là **VND/tháng**, chỉ là số giả lập. Mỗi dòng mới đóng dòng trước vào ngày hôm trước;
`effective_to` của dòng cuối là `null`. `approved_by_account_id` trỏ đến account seed có quyền
quản lý lương sau khi kích hoạt, hoặc account audit được seeder chọn khi ghi trực tiếp;
`reason` lần đầu là `Khởi tạo dữ liệu demo`, lần tăng là
`Điều chỉnh lương demo 2026`. `note` ghi `Chỉ dùng kiểm thử`.

| Nhân viên | Mức lúc vào làm (`effective_from = hire_date`) | Mức từ 2026-01-01 | Mức dùng trong 10/2026 |
|---|---:|---:|---:|
| DIR | 25.000.000 | 45.000.000 | 45.000.000 |
| HR | 12.000.000 | 25.000.000 | 25.000.000 |
| PAY | 12.000.000 | 20.000.000 | 20.000.000 |
| OPS | 15.000.000 | 28.000.000 | 28.000.000 |
| BRH | 12.000.000 | 22.000.000 | 22.000.000 |
| BRD | 13.000.000 | 21.000.000 | 21.000.000 |
| WH | 10.000.000 | 18.000.000 | 18.000.000 |
| TL | 10.000.000 | 19.000.000 | 19.000.000 |
| SR | 8.000.000 | 17.000.000 | 17.000.000 |
| ST | 9.000.000 | 13.000.000 | 13.000.000 |
| NEW | 10.000.000 (2026-09-01) | — | 10.000.000 |
| INT | 5.000.000 (2026-10-01) | — | 5.000.000 |

Với 10 người vào làm trước 2026, dòng cũ có `effective_to=2025-12-31`; dòng
2026 bắt đầu `2026-01-01`. Không seed phụ cấp chức vụ/thâm niên vì các bảng
`position_allowance_rules` và `seniority_allowance_rules` hiện chưa có quy tắc mặc định.
Nhân viên lâu năm vẫn dùng được để kiểm thử **số năm thâm niên và phép năm**, nhưng phụ cấp
thâm niên bằng 0 cho đến khi doanh nghiệp xác định chính sách và nhập quy tắc riêng.

## 5. Hồ sơ thuế, bảo hiểm và người phụ thuộc

Tạo một `employee_payroll_profiles` đang mở cho mỗi nhân viên. `tax_resident=true`,
`wage_region=1` trong **dữ liệu demo**; khi dùng thực tế HR phải xác định vùng theo địa điểm
làm việc hợp lệ. Với 10 người cũ, `effective_from=2026-01-01`; NEW và INT dùng `hire_date`.
Mọi `effective_to=null`. Ba cờ bảo hiểm là `true` cho 11 người đầu, `false` cho INT.

| Nhân viên | `insurance_salary` (VND/tháng) | BHXH/BHYT/BHTN | Người phụ thuộc có hiệu lực trong 10/2026 |
|---|---:|---|---:|
| DIR | 45.000.000 | Có | 2 |
| HR | 25.000.000 | Có | 1 |
| PAY | 20.000.000 | Có | 0 |
| OPS | 28.000.000 | Có | 0 |
| BRH | 22.000.000 | Có | 0 |
| BRD | 21.000.000 | Có | 0 |
| WH | 18.000.000 | Có | 0 |
| TL | 19.000.000 | Có | 0 |
| SR | 17.000.000 | Có | 1 |
| ST | 13.000.000 | Có | 0 |
| NEW | 10.000.000 | Có | 0 |
| INT | 0 | Không | 0 |

Người phụ thuộc (`effective_to=null`, `effective_from=2026-01-01`; `identifier`
chỉ là chuỗi giả):

| Nhân viên | `full_name` | `identifier` |
|---|---|---|
| DIR | Nguyễn Gia Hân | `TEST-DEP-DIR-01` |
| DIR | Nguyễn Gia Minh | `TEST-DEP-DIR-02` |
| HR | Trần An Nhiên | `TEST-DEP-HR-01` |
| SR | Hồ Anh Khoa | `TEST-DEP-SR-01` |

INT là **thực tập sinh mô phỏng trong model hiện tại**: `PROBATION` + `TEMPORARY` +
chức danh `INTERN_DEMO`. Các cờ bảo hiểm `false` và 5 triệu lương tháng chỉ là giả định
kiểm thử, **không phải kết luận về nghĩa vụ bảo hiểm hoặc thuế của thực tập sinh thật**.
Trước khi dùng cho trả lương thực, cần bổ sung phân loại hợp đồng/thực tập và rà soát chính sách.

## 6. Account và quyền truy cập dự kiến

Mỗi nhân viên có thể có một account `PENDING` với `username = lower(employee_code)`,
`email = work_email`, chưa có mật khẩu. Việc phát token kích hoạt và đặt mật khẩu phải theo
luồng account hiện tại; **không lưu mật khẩu hoặc token trong file seed**. Mỗi account nhận
`EMPLOYEE` ở phạm vi `SELF`. Chỉ thêm các role nghiệp vụ sau qua quy trình cấp quyền:

| Nhân viên | Role bổ sung | Phạm vi | Mục đích |
|---|---|---|
| DIR | `DIRECTOR`, `PAYROLL_APPROVER` | `COMPANY` | Quản lý và duyệt lương, không tự tính kỳ |
| HR | `HR_MANAGER` | `COMPANY` | Quản lý nhân sự |
| PAY | `PAYROLL_ACCOUNTANT` | `COMPANY` | Tính kỳ lương, không duyệt kỳ |

`COMPANY_OWNER` không được cấp trực tiếp bằng bộ dữ liệu này; dùng luồng bootstrap riêng
của System Admin. Seeder nhân viên mới cũng không chạy đồng thời để tạo **người trùng** với
ba account `employee01`, `hr01`, `payroll01` của `UserSeeder` cũ; nếu bật cả hai, chúng là
hai nhóm demo độc lập, không được coi là cùng một người.

## 7. Phép năm và thứ tự hiện thực seeder

Không ghi cứng số dư phép vào file nhân viên. `LeaveEntitlementService` tính hạn mức năm từ
ngày vào làm, thâm niên và quy tắc có hiệu lực. Bộ demo chưa có điều chỉnh thủ công hoặc
phép chuyển năm trước: coi `adjustment_minutes=0`, `carried_over_minutes=0` khi khởi tạo.

Thứ tự khi chuyển đặc tả này thành seeder chạy được:

1. Dùng các seed danh mục hiện có; tạo `HR_MANAGER_DEMO`, `INTERN_DEMO` nếu chưa có.
2. Tạo 12 `employees` theo `employee_code`; không ghi đè hồ sơ đã được chỉnh sửa.
3. Tạo phân công DIR trước, sau đó theo thứ tự manager đã tồn tại; thêm lịch sử SR rồi
   phân công hiện tại. Kiểm tra không có chu trình báo cáo hay chồng lấn phân công chính.
4. Tạo dữ liệu nhạy cảm và lương lịch sử; tra cứu FK bằng code, không giả định ID tự tăng.
5. Tạo hồ sơ thuế/bảo hiểm và bốn người phụ thuộc.
6. Tạo/provision account theo luồng kích hoạt, sau đó cấp role đúng phạm vi.
7. Kiểm tra: 12 nhân viên, 12 phân công hiện tại, 1 phân công cũ, 22 dòng lương,
   12 hồ sơ payroll, 4 người phụ thuộc; mỗi người có đúng một manager hiện tại trừ DIR.

8. Nếu triển khai thêm mục 8: tạo lịch ngày lễ công ty, đơn nghỉ đã duyệt và bản ghi chấm công
   theo đúng phạm vi ngày; tra cứu khóa ngoại bằng code hoặc khóa nghiệp vụ, không đoán ID.
9. Kiểm tra số bản ghi và các ca ngoại lệ ở cuối mục 8 trước khi tính kỳ lương.

Không tự tạo kỳ lương ngay sau seed nhân viên. Tháng 09/2026 chỉ sẵn sàng để tính lương khi
triển khai đủ mục 8; tháng 10/2026 mới có dữ liệu đến 10/10 và còn ca `MISSING_PUNCH`.

## 8. Lịch sử chấm công mẫu

Đây vẫn là **đặc tả trong Markdown**, chưa phải dữ liệu đã được nạp vào database. Phạm vi
bao gồm **01/09–30/09/2026** cho 11 người đã vào làm (DIR đến NEW) và **01/10–10/10/2026**
cho cả 12 người. INT không có bản ghi trước `hire_date=2026-10-01`; không tạo bản ghi sau
10/10/2026. Tất cả giờ bên dưới là giờ địa phương `Asia/Ho_Chi_Minh` (UTC+07:00);
`*_at` kiểu `TIMESTAMPTZ` phải được đổi thành instant tương ứng khi nạp.

### 8.1. Lịch công ty và cách xác định một bản ghi

Trong phạm vi trên, công ty demo làm **thứ Hai đến thứ Bảy**, nghỉ Chủ nhật. Công ty chọn
**02/09 và 03/09/2026** làm hai ngày nghỉ Quốc khánh: ngày 02/09 là ngày cố định, ngày
03/09 là ngày liền kề doanh nghiệp chọn cho bộ demo. [Thông báo lịch nghỉ 2026 của cơ quan
nhà nước](https://sav.gov.vn/vi/page/dangbokiemtoannhanuoc/bai-viet/thong-bao-lich-nghi-le-quoc-khanh-nam-2026)
cho biết doanh nghiệp có thể chọn 01/09 hoặc 03/09 bên cạnh 02/09. 01/09 vì thế là ngày
làm việc trong **lịch demo này**. Hệ thống hiện đọc `company_holidays` do người triển khai
nạp; file này không giả định API tự lấy lịch lễ Việt Nam.

| `holiday_date` | `name` | `created_at` |
|---|---|---|
| 2026-09-02 | Quốc khánh 2026 | `2026-08-28 09:00+07` |
| 2026-09-03 | Nghỉ Quốc khánh liền kề (lịch demo) | `2026-08-28 09:00+07` |

Mỗi ô trong bảng ở mục 8.2 tạo **đúng một** `attendance_records` cho cặp
`(employee_code, work_date)`; `—` nghĩa là không có bản ghi. Code rút gọn trỏ về nhân viên
ở mục 2. `P` là ngày công chuẩn; các ký hiệu khác **thay thế** bản ghi `P`, không thêm bản ghi
thứ hai. Chủ nhật chỉ có một ca tăng ca được ghi rõ ở bảng. Ngày lễ có bản ghi `HOLIDAY`
để xem lịch sử, nhưng không thuộc số ngày công chuẩn dùng tính lương.

Các trường chung cho **mọi bản ghi** (kể cả ngày lễ và Chủ nhật có tăng ca):

| Trường | Giá trị / quy tắc |
|---|---|
| `id` | Database tự cấp; không gán số cố định trong file Markdown |
| `employee_id` | Tra bằng `employee_code` trong mục 2 |
| `work_date` | Ngày ở cột đầu bảng 8.2 |
| `shift_id` | Tra ca `OFFICE_DAY` từ mục 1 |
| `scheduled_start_at`, `scheduled_end_at` | `work_date 08:00+07`, `work_date 17:00+07` |
| `scheduled_minutes` | `480` (nghỉ giữa ca 12:00–13:00; `grace_late_minutes=15`) |
| `leave_request_id` | `null`, trừ ô `LP`, `L`, `S`, `U` tham chiếu khóa đơn ở mục 8.3 |
| `overtime_minutes`, `overtime_multiplier`, `overtime_tax_exempt` | `0`, `1.0000`, `false`, trừ ô `OT`, `H+`, `CN+` |
| `overtime_approved_by_account_id`, `overtime_approved_at` | `null`, trừ ô `OT`, `H+`, `CN+` |
| `updated_by_account_id` | `null`, trừ ô `A` đã được HR điều chỉnh thủ công |
| `created_at` | `work_date 07:00+07` (snapshot demo, trước giờ vào ca) |
| `updated_at` | `work_date 20:00+07`, trừ ca tăng ca đã duyệt và ô `A` |

`P` có `check_in_at=08:00+07`, `check_out_at=17:00+07`, `worked_minutes=480`,
`payable_minutes=480`, `leave_minutes=late_minutes=early_leave_minutes=0`,
`status=PRESENT`, `note=null`. `H` có hai mốc chấm công `null`, các phút công/phép/đi
muộn/về sớm bằng 0, `status=HOLIDAY`; `note` lấy `name` của ngày lễ tương ứng.
Với `P`, `H` và mọi ngoại lệ, các trường không được ghi đè trong mục 8.4 giữ đúng giá trị
chung vừa nêu. Phút `worked_minutes` là thời gian từ vào đến ra **trừ giờ nghỉ giữa ca
nếu giao nhau**; phút `payable_minutes` không vượt 480.

### 8.2. Lịch từng ngày cho từng nhân viên

Chú giải: `P` = ngày công chuẩn; `H` = nghỉ lễ; `H+` = làm ngày lễ có tăng ca;
`CN+` = làm Chủ nhật có tăng ca; `OT` = tăng ca ngày thường; `L` = nghỉ phép năm cả
ngày; `LP` = làm nửa ngày và nghỉ phép năm nửa ngày; `S` = nghỉ ốm;
`U` = nghỉ không lương; `A` = vắng không phép; `T` = đi muộn;
`E` = về sớm; `M` = thiếu chấm ra; `—` = không có bản ghi. Giá trị đầy đủ của
các ô khác `P` và `H` ở mục 8.4.

| `work_date` | DIR | HR | PAY | OPS | BRH | BRD | WH | TL | SR | ST | NEW | INT |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 2026-09-01 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-02 | H | H | H | H | H | H | H+ | H | H | H | H | — |
| 2026-09-03 | H | H | H | H | H | H | H | H | H | H | H | — |
| 2026-09-04 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-05 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-06 | — | — | — | CN+ | — | — | — | — | — | — | — | — |
| 2026-09-07 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-08 | P | P | P | P | P | T | P | P | P | P | P | — |
| 2026-09-09 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-10 | P | P | P | P | P | P | P | P | L | P | P | — |
| 2026-09-11 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-12 | P | P | P | P | P | P | P | OT | P | P | P | — |
| 2026-09-14 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-15 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-16 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-17 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-18 | P | P | P | P | P | P | P | LP | P | P | P | — |
| 2026-09-19 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-21 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-22 | P | P | P | P | P | P | P | P | P | E | P | — |
| 2026-09-23 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-24 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-25 | P | P | P | P | P | P | P | P | P | P | U | — |
| 2026-09-26 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-28 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-29 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-09-30 | P | P | P | P | P | P | P | P | P | P | P | — |
| 2026-10-01 | P | P | P | P | P | P | P | P | P | P | P | P |
| 2026-10-02 | P | P | P | P | P | P | P | P | P | P | P | P |
| 2026-10-03 | P | P | P | P | P | P | P | P | P | P | P | P |
| 2026-10-05 | P | P | P | P | P | P | P | P | P | P | P | P |
| 2026-10-06 | P | P | P | P | P | P | P | P | P | S | P | P |
| 2026-10-07 | P | P | A | P | P | P | P | P | P | P | P | P |
| 2026-10-08 | P | P | P | P | P | P | P | P | P | P | P | P |
| 2026-10-09 | P | P | P | P | P | P | P | P | P | P | P | P |
| 2026-10-10 | P | P | P | P | P | P | P | P | P | P | P | M |

Không có bản ghi vào các Chủ nhật 13/09, 20/09, 27/09 và 04/10. Chủ nhật 06/09
chỉ có bản ghi `CN+` của OPS. Không tạo hàng cho 11–31/10 trong bộ lịch sử này.

### 8.3. Đơn nghỉ đã duyệt liên kết với chấm công

Khóa `DEMO-LR-*` dưới đây là khóa tham chiếu **trong tài liệu**, không phải cột trong
`leave_requests`. Seeder thực tế tạo đơn, lấy `id` đã sinh, rồi điền vào
`attendance_records.leave_request_id`. Mọi đơn có `status=APPROVED`,
`attachment_url=null`, `reviewed_by_account_id` trỏ account của HR đã kích hoạt và
được cấp `HR_MANAGER` phạm vi `COMPANY`, `review_comment="Đã duyệt dữ liệu demo"`.
Với ngày nghỉ `D`: `created_at=D-2 ngày 09:00+07`,
`submitted_at=D-2 ngày 09:05+07`, `reviewed_at=D-1 ngày 10:00+07`,
`updated_at=reviewed_at`. Đơn được duyệt trước ngày nghỉ và không trùng nhau.

| Khóa tài liệu | Nhân viên | `leave_type` | `salary_treatment` | `start_at` | `end_at` | `requested_minutes` | `reason` |
|---|---|---|---|---|---|---:|---|
| `DEMO-LR-SR-0910` | SR | `ANNUAL` | `EMPLOYER_PAID` | `2026-09-10 08:00+07` | `2026-09-10 17:00+07` | 480 | Nghỉ phép năm |
| `DEMO-LR-TL-0918` | TL | `ANNUAL` | `EMPLOYER_PAID` | `2026-09-18 13:00+07` | `2026-09-18 17:00+07` | 240 | Nghỉ phép nửa ngày |
| `DEMO-LR-NEW-0925` | NEW | `UNPAID` | `UNPAID` | `2026-09-25 08:00+07` | `2026-09-25 17:00+07` | 480 | Nghỉ việc riêng không lương |
| `DEMO-LR-ST-1006` | ST | `SICK` | `SOCIAL_INSURANCE` | `2026-10-06 08:00+07` | `2026-10-06 17:00+07` | 480 | Nghỉ ốm |

Khi triển khai qua luồng duyệt đơn, phải có hạn mức phép năm 2026 đủ cho hai đơn `ANNUAL`
(SR 480 phút, TL 240 phút) theo `LeaveEntitlementService` ở mục 7. Đơn `SICK` trong
model này **không** tạo `payable_minutes` từ người sử dụng lao động; xử lý trợ cấp bảo hiểm
nếu có nằm ngoài bản ghi chấm công.

### 8.4. Giá trị ghi đè cho từng ô ngoại lệ

Các mốc `in` và `out` bên dưới là giờ của chính `work_date` tại UTC+07:00; `∅` = `null`.
`worked`, `payable`, `leave`, `late`, `early`, `OT phút` đều là phút. Mỗi hàng ghi đè
**toàn bộ các trường này**, không chỉ trường khác 0. `late` đã trừ 15 phút cho phép của
ca. `OT hệ số` có bốn chữ số thập phân trong database.

| Ngày | Người | Ô | `in` | `out` | `worked` | `payable` | `leave` | `late` | `early` | `OT phút` | `OT hệ số` | `status` | `leave_request_id` |
|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| 2026-09-02 | WH | H+ | 13:00 | 17:00 | 240 | 0 | 0 | 0 | 0 | 240 | 3.0000 | `HOLIDAY` | ∅ |
| 2026-09-06 | OPS | CN+ | 08:00 | 12:00 | 240 | 0 | 0 | 0 | 0 | 240 | 2.0000 | `PRESENT` | ∅ |
| 2026-09-08 | BRD | T | 08:25 | 17:00 | 455 | 455 | 0 | 10 | 0 | 0 | 1.0000 | `PRESENT` | ∅ |
| 2026-09-10 | SR | L | ∅ | ∅ | 0 | 480 | 480 | 0 | 0 | 0 | 1.0000 | `PAID_LEAVE` | `DEMO-LR-SR-0910` |
| 2026-09-12 | TL | OT | 08:00 | 19:00 | 600 | 480 | 0 | 0 | 0 | 120 | 1.5000 | `PRESENT` | ∅ |
| 2026-09-18 | TL | LP | 08:00 | 12:00 | 240 | 480 | 240 | 0 | 0 | 0 | 1.0000 | `PRESENT` | `DEMO-LR-TL-0918` |
| 2026-09-22 | ST | E | 08:00 | 16:30 | 450 | 450 | 0 | 0 | 30 | 0 | 1.0000 | `PRESENT` | ∅ |
| 2026-09-25 | NEW | U | ∅ | ∅ | 0 | 0 | 480 | 0 | 0 | 0 | 1.0000 | `UNPAID_LEAVE` | `DEMO-LR-NEW-0925` |
| 2026-10-06 | ST | S | ∅ | ∅ | 0 | 0 | 480 | 0 | 0 | 0 | 1.0000 | `SICK_LEAVE` | `DEMO-LR-ST-1006` |
| 2026-10-07 | PAY | A | ∅ | ∅ | 0 | 0 | 0 | 0 | 0 | 0 | 1.0000 | `UNAUTHORIZED_ABSENCE` | ∅ |
| 2026-10-10 | INT | M | 08:05 | ∅ | 0 | 0 | 0 | 0 | 0 | 0 | 1.0000 | `MISSING_PUNCH` | ∅ |

`overtime_tax_exempt=false` cho cả ba ca tăng ca: đây là cờ nghiệp vụ demo, không tự suy
ra miễn thuế theo pháp luật. Với `H+`, `CN+`, `OT`, `overtime_approved_by_account_id`
trỏ account HR có quyền `attendance.overtime.approve`; `overtime_approved_at` và
`updated_at` đều là **09:00+07 ngày kế tiếp**. Phút tăng ca không cộng vào
`payable_minutes` của ngày công chuẩn. Các ô này có `note` lần lượt là
`Trực kho ngày lễ demo`, `Trực vận hành Chủ nhật demo`, `Tăng ca dự án demo`.

Các ô `T`, `L`, `LP`, `E`, `U`, `S`, `A`, `M` có `note` lần lượt là
`Đi muộn 25 phút, trừ 15 phút cho phép`, `Nghỉ phép năm`,
`Làm sáng, nghỉ phép chiều`, `Về sớm 30 phút`,
`Nghỉ không lương`, `Nghỉ ốm`, `Vắng không phép, HR đã xác nhận`,
`Thiếu chấm ra, chờ xử lý`. Ô `A` có `updated_by_account_id` là account HR và
`updated_at=2026-10-08 09:00+07`; đây là kết quả điều chỉnh từ `MISSING_PUNCH` sau
khi HR xác nhận vắng mặt. Ô `M` **cố ý chưa giải quyết** để thử luồng rà soát;
không tính kỳ lương tháng 10 khi vẫn còn ô này.

### 8.5. Kiểm tra khi chuyển thành seeder chạy được

- Có **2** `company_holidays`, **4** `leave_requests` đã duyệt, **395**
  `attendance_records`: tháng 9 có `11 × (24 ngày công + 2 ngày lễ) + 1 Chủ nhật tăng ca = 287`,
  từ 01–10/10 có `12 × 9 ngày công = 108`.
- Mỗi người có một bản ghi trên từng ngày công theo lịch trong phạm vi làm việc của mình:
  24 ngày công tháng 9 cho 11 người và 9 ngày công đầu tháng 10 cho 12 người. Không có cặp
  `(employee_id, work_date)` trùng.
- Tháng 9 không có `MISSING_PUNCH` trên ngày công; `payable_minutes` của ngày thường nằm
  trong `0..480`, còn ngày lễ và Chủ nhật bằng 0. Chỉ 3 bản ghi có `overtime_minutes>0`,
  đều có người và thời điểm duyệt, hệ số lần lượt `3.0000`, `2.0000`, `1.5000`.
- Tháng 10 có đúng một `MISSING_PUNCH` (INT, 10/10), để kiểm thử chặn tính lương;
  giải quyết bản ghi này và hoàn tất các ngày còn lại trong tháng trước khi tính kỳ 10/2026.

## 9. Bộ dữ liệu mở rộng: thêm 24 nhân viên

Phần này **bổ sung**, không thay thế 12 nhân viên ở mục 2–8. Mọi mã `DEMO-*` dưới đây là khóa
tham chiếu dành cho seeder trực tiếp. Ngày chốt và múi giờ giữ như đầu tài liệu. Toàn bộ 24 người
đã vào làm trước 2026, có trạng thái `ACTIVE`, `seniority_start_date=hire_date`; các trường
chấm dứt/đánh dấu xóa đều `null`. ID và thời điểm tạo/cập nhật hồ sơ do database và seeder cấp.
Không tạo thêm phòng ban, địa điểm, ca hay chức danh. `WAREHOUSE-02` tiếp tục không có nhân viên.

### 9.1. Hồ sơ và liên hệ

| STT | `employee_code` | `full_name` | `date_of_birth` | `gender` | `hire_date` | Kịch bản |
|---:|---|---|---|---|---|---|
| 13 | `DEMO-HR2-001` | Phan Thị Ngọc | 1988-05-19 | `FEMALE` | 2016-03-01 | Chuyên viên nhân sự lâu năm |
| 14 | `DEMO-HR2-002` | Nguyễn Thảo Vy | 1994-11-08 | `FEMALE` | 2020-08-03 | Nghỉ phép năm trong tháng 9 |
| 15 | `DEMO-HR2-003` | Trịnh Quốc Đạt | 1998-02-24 | `MALE` | 2023-06-05 | Chuyên viên tuyển dụng |
| 16 | `DEMO-ACC-001` | Đinh Thị Hương | 1986-09-15 | `FEMALE` | 2013-04-01 | Kế toán thâm niên |
| 17 | `DEMO-ACC-002` | Trần Gia Bảo | 1993-01-26 | `MALE` | 2019-07-01 | Vắng không phép trong tháng 10 |
| 18 | `DEMO-ACC-003` | Lê Minh Châu | 1999-12-02 | `FEMALE` | 2024-02-01 | Kế toán mới hơn |
| 19 | `DEMO-OPH-001` | Nguyễn Đức Tài | 1987-06-03 | `MALE` | 2011-09-05 | Trực vận hành Chủ nhật |
| 20 | `DEMO-OPH-002` | Phạm Hải Yến | 1990-10-11 | `FEMALE` | 2017-01-02 | Tăng ca ngày thường |
| 21 | `DEMO-OPH-003` | Võ Thành Nam | 1995-03-17 | `MALE` | 2021-04-01 | Nghỉ phép nửa ngày |
| 22 | `DEMO-OPH-004` | Đỗ Mỹ Linh | 1997-08-29 | `FEMALE` | 2022-05-02 | Nhân viên vận hành |
| 23 | `DEMO-OPH-005` | Lưu Văn Phát | 1983-07-07 | `MALE` | 2012-02-01 | Kiểm thử thâm niên |
| 24 | `DEMO-OPH-006` | Bùi Khánh Ngân | 2000-04-23 | `FEMALE` | 2025-01-02 | Nhân viên vận hành |
| 25 | `DEMO-HNI-001` | Nguyễn Thị Thu | 1989-01-30 | `FEMALE` | 2015-05-04 | Chi nhánh Hà Nội |
| 26 | `DEMO-HNI-002` | Hoàng Anh Tuấn | 1992-12-06 | `MALE` | 2018-10-01 | Nghỉ ốm trong tháng 10 |
| 27 | `DEMO-HNI-003` | Vũ Lan Anh | 1996-06-14 | `FEMALE` | 2021-08-02 | Về sớm trong tháng 9 |
| 28 | `DEMO-HNI-004` | Phạm Quang Huy | 1998-09-09 | `MALE` | 2023-03-01 | Chi nhánh Hà Nội |
| 29 | `DEMO-HNI-005` | Cao Ngọc Hà | 1994-04-18 | `FEMALE` | 2020-11-02 | Chi nhánh Hà Nội |
| 30 | `DEMO-DNG-001` | Đặng Minh Đức | 1985-11-22 | `MALE` | 2014-01-02 | Đi muộn trong tháng 9 |
| 31 | `DEMO-DNG-002` | Nguyễn Phương Thảo | 1991-05-05 | `FEMALE` | 2018-06-01 | Nghỉ không lương tháng 10 |
| 32 | `DEMO-DNG-003` | Hồ Ngọc Bích | 1997-02-12 | `FEMALE` | 2022-07-01 | Chi nhánh Đà Nẵng |
| 33 | `DEMO-DNG-004` | Trương Nhật Minh | 2000-10-20 | `MALE` | 2025-02-03 | Chi nhánh Đà Nẵng |
| 34 | `DEMO-KHO-001` | Nguyễn Hữu Lộc | 1984-03-10 | `MALE` | 2011-07-01 | Trực kho ngày lễ |
| 35 | `DEMO-KHO-002` | Lê Thanh Bình | 1992-08-31 | `MALE` | 2019-09-02 | Đi muộn trong tháng 10 |
| 36 | `DEMO-KHO-003` | Phạm Thị Sen | 1998-01-13 | `FEMALE` | 2023-10-02 | Nhân viên kho |

Áp dụng quy tắc liên hệ và dữ liệu nhạy cảm ở mục 2 cho **STT 13–36**: `work_email` và
`personal_email` sinh từ `lower(employee_code)` với miền tương ứng; `phone="00000000" + STT`
hai chữ số; `national_id`, `tax_code`, `bank_account_number` dùng tiền tố `TEST-*`,
`bank_account_holder=full_name`. `address` lấy thành phố của địa điểm trong mục 9.3.

| Nhóm mã | `highest_education_level` | `major` | `institution` | `graduation_year` |
|---|---|---|---|---:|
| HR2-001 | `MASTER` | Quản trị nhân lực | Đại học Demo TP.HCM | 2012 |
| HR2-002 | `BACHELOR` | Quản trị nhân lực | Đại học Demo TP.HCM | 2016 |
| HR2-003 | `BACHELOR` | Luật lao động | Đại học Demo TP.HCM | 2020 |
| ACC-001 | `BACHELOR` | Kế toán | Đại học Demo TP.HCM | 2008 |
| ACC-002 | `BACHELOR` | Tài chính | Đại học Demo TP.HCM | 2015 |
| ACC-003 | `BACHELOR` | Kế toán | Đại học Demo TP.HCM | 2021 |
| OPH-001 | `COLLEGE` | Vận hành | Cao đẳng Demo TP.HCM | 2008 |
| OPH-002 | `BACHELOR` | Logistics | Đại học Demo TP.HCM | 2012 |
| OPH-003 | `BACHELOR` | Quản trị vận hành | Đại học Demo TP.HCM | 2017 |
| OPH-004 | `BACHELOR` | Logistics | Đại học Demo TP.HCM | 2019 |
| OPH-005 | `COLLEGE` | Vận hành | Cao đẳng Demo TP.HCM | 2003 |
| OPH-006 | `BACHELOR` | Quản trị kinh doanh | Đại học Demo TP.HCM | 2022 |
| HNI-001 | `BACHELOR` | Quản trị kinh doanh | Đại học Demo Hà Nội | 2011 |
| HNI-002 | `COLLEGE` | Logistics | Cao đẳng Demo Hà Nội | 2013 |
| HNI-003 | `BACHELOR` | Quản trị kinh doanh | Đại học Demo Hà Nội | 2018 |
| HNI-004 | `BACHELOR` | Logistics | Đại học Demo Hà Nội | 2020 |
| HNI-005 | `BACHELOR` | Quản trị kinh doanh | Đại học Demo Hà Nội | 2016 |
| DNG-001 | `COLLEGE` | Vận hành | Cao đẳng Demo Đà Nẵng | 2006 |
| DNG-002 | `BACHELOR` | Logistics | Đại học Demo Đà Nẵng | 2013 |
| DNG-003 | `BACHELOR` | Quản trị kinh doanh | Đại học Demo Đà Nẵng | 2019 |
| DNG-004 | `COLLEGE` | Vận hành | Cao đẳng Demo Đà Nẵng | 2021 |
| KHO-001 | `COLLEGE` | Quản lý kho | Cao đẳng Demo TP.HCM | 2005 |
| KHO-002 | `COLLEGE` | Logistics | Cao đẳng Demo TP.HCM | 2013 |
| KHO-003 | `BACHELOR` | Logistics | Đại học Demo TP.HCM | 2020 |

### 9.2. Phân công và sơ đồ quản lý

Mỗi người mới có **một** phân công chính: `is_primary=true`, `effective_from=hire_date`,
`effective_to=null`, `employment_type=FULL_TIME`, `shift=OFFICE_DAY`. `manager` là mã rút gọn
trong mục 2; `reason="Khởi tạo dữ liệu demo"`. Các nhóm dùng đúng danh mục đã seed:

| Nhóm `employee_code` | Số người | Đơn vị | Địa điểm | Chức danh | Manager |
|---|---:|---|---|---|---|
| `DEMO-HR2-001..003` | 3 | `HR` | `HO` | `HR_SPECIALIST` | HR |
| `DEMO-ACC-001..003` | 3 | `ACCOUNTING` | `HO` | `PAYROLL_ACCOUNTANT` | PAY |
| `DEMO-OPH-001..006` | 6 | `OPERATIONS` | `HO` | `GENERAL_STAFF` | TL |
| `DEMO-HNI-001..005` | 5 | `OPERATIONS` | `BRANCH-01` | `GENERAL_STAFF` | BRH |
| `DEMO-DNG-001..004` | 4 | `OPERATIONS` | `BRANCH-02` | `GENERAL_STAFF` | BRD |
| `DEMO-KHO-001..003` | 3 | `OPERATIONS` | `WAREHOUSE-01` | `GENERAL_STAFF` | WH |

### 9.3. Lương cơ bản, thuế và bảo hiểm

Đơn vị tiền trong bảng là **triệu VND/tháng**. Mỗi người có hai dòng `employee_salary_histories`:
dòng đầu từ `hire_date` đến `2025-12-31`, dòng mới từ `2026-01-01` đến `null`. Nhân giá trị
trong bảng với 1.000.000 khi ghi vào database. Dùng `reason`, `note` và tài khoản duyệt như mục 4.
Mỗi người có một `employee_payroll_profiles` từ `2026-01-01` đến `null`, `tax_resident=true`,
`wage_region=1`, ba cờ bảo hiểm đều `true`; `insurance_salary` là cột cuối của bảng.

| Nhân viên | Lương khi vào làm | Lương từ 2026-01-01 | `insurance_salary` |
|---|---:|---:|---:|
| HR2-001 | 8 | 17 | 17 |
| HR2-002 | 9 | 15 | 15 |
| HR2-003 | 10 | 13 | 13 |
| ACC-001 | 9 | 19 | 19 |
| ACC-002 | 10 | 16 | 16 |
| ACC-003 | 9 | 12 | 12 |
| OPH-001 | 7 | 18 | 18 |
| OPH-002 | 9 | 16 | 16 |
| OPH-003 | 9 | 14 | 14 |
| OPH-004 | 9 | 13 | 13 |
| OPH-005 | 7 | 18 | 18 |
| OPH-006 | 10 | 11 | 11 |
| HNI-001 | 8 | 17 | 17 |
| HNI-002 | 9 | 15 | 15 |
| HNI-003 | 9 | 13 | 13 |
| HNI-004 | 10 | 12 | 12 |
| HNI-005 | 9 | 14 | 14 |
| DNG-001 | 8 | 17 | 17 |
| DNG-002 | 9 | 15 | 15 |
| DNG-003 | 9 | 13 | 13 |
| DNG-004 | 10 | 11 | 11 |
| KHO-001 | 8 | 17 | 17 |
| KHO-002 | 9 | 14 | 14 |
| KHO-003 | 10 | 12 | 12 |

Thêm sáu `employee_tax_dependents` giả lập, đều có `effective_from=2026-01-01`,
`effective_to=null`. Không dùng các định danh này ngoài demo:

| Nhân viên | `full_name` | `identifier` |
|---|---|---|
| HR2-001 | Phan Gia Hân | `TEST-DEP-HR2-001-01` |
| ACC-001 | Đinh Minh Khoa | `TEST-DEP-ACC-001-01` |
| OPH-001 | Nguyễn Anh Khang | `TEST-DEP-OPH-001-01` |
| OPH-001 | Nguyễn Gia Linh | `TEST-DEP-OPH-001-02` |
| HNI-001 | Nguyễn An Nhiên | `TEST-DEP-HNI-001-01` |
| KHO-001 | Nguyễn Minh Khôi | `TEST-DEP-KHO-001-01` |

### 9.4. Account dự kiến

Nếu provision account cho 24 người, dùng `username=lower(employee_code)`, `email=work_email`,
trạng thái `PENDING`, chưa có mật khẩu. Mỗi account chỉ nhận role `EMPLOYEE` phạm vi `SELF`.
Không tự cấp role nghiệp vụ hay lưu token/mật khẩu trong dữ liệu mẫu. Tài khoản dùng làm FK
`created_by_account_id`, `approved_by_account_id` và duyệt đơn phải được provision/kích hoạt
theo luồng ở mục 6–7 trước khi ghi các bản ghi yêu cầu FK.

### 9.5. Chấm công và đơn nghỉ bổ sung

Tạo một bản ghi cho **mỗi** người mới trên từng ngày Thứ Hai–Thứ Bảy trong
`2026-09-01..2026-09-30` và `2026-10-01..2026-10-10`; bao gồm hai ngày lễ 02–03/09.
Ngày lễ mặc định là `H`, các ngày khác mặc định là `P`, theo toàn bộ quy tắc trường ở mục 8.1.
Chủ nhật mặc định không có bản ghi. Bảng sau **thay thế** ô mặc định hoặc thêm ô Chủ nhật;
không tạo bản ghi thứ hai cho cùng `(employee_code, work_date)`.

Thêm bốn `leave_requests` với `status=APPROVED` và toàn bộ quy tắc người duyệt,
`created_at`, `submitted_at`, `reviewed_at`, `updated_at` ở mục 8.3. Hai đơn phép năm cần đủ
hạn mức từ `LeaveEntitlementService` trước khi duyệt.

| Khóa tài liệu | Nhân viên | `leave_type` | `salary_treatment` | `start_at` | `end_at` | `requested_minutes` | `reason` |
|---|---|---|---|---|---|---:|---|
| `DEMO-LR-HR2-002-0916` | HR2-002 | `ANNUAL` | `EMPLOYER_PAID` | `2026-09-16 08:00+07` | `2026-09-16 17:00+07` | 480 | Nghỉ phép năm |
| `DEMO-LR-OPH-003-0923` | OPH-003 | `ANNUAL` | `EMPLOYER_PAID` | `2026-09-23 13:00+07` | `2026-09-23 17:00+07` | 240 | Nghỉ phép nửa ngày |
| `DEMO-LR-HNI-002-1005` | HNI-002 | `SICK` | `SOCIAL_INSURANCE` | `2026-10-05 08:00+07` | `2026-10-05 17:00+07` | 480 | Nghỉ ốm |
| `DEMO-LR-DNG-002-1008` | DNG-002 | `UNPAID` | `UNPAID` | `2026-10-08 08:00+07` | `2026-10-08 17:00+07` | 480 | Nghỉ việc riêng không lương |

Các giá trị `in`/`out` là giờ địa phương của `work_date`, `∅` là `null`; các cột phút là
`worked`, `payable`, `leave`, `late`, `early`, `overtime_minutes`. Dùng các giá trị chung ở
mục 8.1 cho trường không ghi trong bảng. `leave_request_id` tra bằng khóa đơn ở trên.

| Ngày | Người | Ô | `in` | `out` | `worked` | `payable` | `leave` | `late` | `early` | `OT phút` | `OT hệ số` | `status` | `leave_request_id` |
|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| 2026-09-02 | KHO-001 | H+ | 13:00 | 17:00 | 240 | 0 | 0 | 0 | 0 | 240 | 3.0000 | `HOLIDAY` | ∅ |
| 2026-09-06 | OPH-001 | CN+ | 08:00 | 12:00 | 240 | 0 | 0 | 0 | 0 | 240 | 2.0000 | `PRESENT` | ∅ |
| 2026-09-08 | DNG-001 | T | 08:25 | 17:00 | 455 | 455 | 0 | 10 | 0 | 0 | 1.0000 | `PRESENT` | ∅ |
| 2026-09-12 | OPH-002 | OT | 08:00 | 19:00 | 600 | 480 | 0 | 0 | 0 | 120 | 1.5000 | `PRESENT` | ∅ |
| 2026-09-16 | HR2-002 | L | ∅ | ∅ | 0 | 480 | 480 | 0 | 0 | 0 | 1.0000 | `PAID_LEAVE` | `DEMO-LR-HR2-002-0916` |
| 2026-09-23 | OPH-003 | LP | 08:00 | 12:00 | 240 | 480 | 240 | 0 | 0 | 0 | 1.0000 | `PRESENT` | `DEMO-LR-OPH-003-0923` |
| 2026-09-25 | HNI-003 | E | 08:00 | 16:30 | 450 | 450 | 0 | 0 | 30 | 0 | 1.0000 | `PRESENT` | ∅ |
| 2026-10-05 | HNI-002 | S | ∅ | ∅ | 0 | 0 | 480 | 0 | 0 | 0 | 1.0000 | `SICK_LEAVE` | `DEMO-LR-HNI-002-1005` |
| 2026-10-07 | ACC-002 | A | ∅ | ∅ | 0 | 0 | 0 | 0 | 0 | 0 | 1.0000 | `UNAUTHORIZED_ABSENCE` | ∅ |
| 2026-10-08 | DNG-002 | U | ∅ | ∅ | 0 | 0 | 480 | 0 | 0 | 0 | 1.0000 | `UNPAID_LEAVE` | `DEMO-LR-DNG-002-1008` |
| 2026-10-09 | KHO-002 | T | 08:25 | 17:00 | 455 | 455 | 0 | 10 | 0 | 0 | 1.0000 | `PRESENT` | ∅ |

Với `H+`, `CN+`, `OT`, dùng account HR có quyền `attendance.overtime.approve` và đặt
`overtime_approved_at=updated_at` lúc `09:00+07` ngày kế tiếp;
`overtime_tax_exempt=false`. `note` tương ứng là `Trực kho ngày lễ demo`,
`Trực vận hành Chủ nhật demo`, `Tăng ca dự án demo`. Với `A`, account HR là
`updated_by_account_id`, `updated_at=2026-10-08 09:00+07`,
`note="Vắng không phép, HR đã xác nhận"`. Các ô `T`, `L`, `LP`, `E`, `S`, `U`
dùng note tương ứng ở mục 8.4. Không thêm `MISSING_PUNCH` mới; ô INT ngày 10/10 ở mục 8
vẫn là bản ghi cần giải quyết trước khi tính kỳ tháng 10.

### 9.6. Thứ tự nạp dữ liệu bổ sung

Sau bộ gốc, tạo 24 `employees`, tra khóa ngoại bằng code, rồi tạo phân công với manager đã có,
lương lịch sử, hồ sơ payroll, người phụ thuộc và account theo mục 9.1–9.4. Sau đó tạo bốn đơn nghỉ,
các bản ghi chấm công mặc định và áp dụng bảng ngoại lệ mục 9.5. Khi chạy lại, nhận diện bản ghi
theo khóa nghiệp vụ và không ghi đè hồ sơ đã được người dùng sửa.

### 9.7. Số lượng kiểm tra sau khi mở rộng

| Bảng/nhóm | Bộ gốc | Bổ sung | Tổng |
|---|---:|---:|---:|
| `employees` | 12 | 24 | 36 |
| `employee_assignments` hiện tại | 12 | 24 | 36 |
| `employee_assignments` lịch sử | 1 | 0 | 1 |
| `employee_salary_histories` | 22 | 48 | 70 |
| `employee_payroll_profiles` | 12 | 24 | 36 |
| `employee_tax_dependents` | 4 | 6 | 10 |
| `company_holidays` | 2 | 0 | 2 |
| `leave_requests` đã duyệt | 4 | 4 | 8 |
| `attendance_records` | 395 | 841 | 1.236 |

Phần bổ sung có `24 × 26 = 624` bản ghi tháng 9 (24 ngày công, 2 ngày lễ), cộng **1**
Chủ nhật 06/09; `24 × 9 = 216` bản ghi từ 01–10/10. Tổng `624 + 1 + 216 = 841`.
Sau mở rộng có sáu bản ghi tăng ca, không có cặp `(employee_id, work_date)` trùng và
vẫn chỉ có một `MISSING_PUNCH` trong phạm vi dữ liệu (INT, 10/10).
