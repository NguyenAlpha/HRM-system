# Report API

API báo cáo tổng hợp nhân sự. Mọi truy vấn đều áp dụng phạm vi của role assignment đang có
hiệu lực; dữ liệu nhân viên và phân công được xét tại `asOfDate`.

`HR_MANAGER` được seed cả `report.hr.read` và `report.payroll.read`, vì vậy có thể xem đầy đủ
thống kê trình độ, thâm niên và lương cơ bản trong phạm vi toàn công ty. Các role payroll vẫn
chỉ được seed `report.payroll.read`.

## Bộ lọc chung

| Query parameter | Bắt buộc | Ý nghĩa |
|---|---|---|
| `asOfDate` | Không | Ngày thống kê, mặc định hôm nay và không được ở tương lai |
| `organizationUnitId` | Không | Chỉ lấy nhân viên thuộc đơn vị tại ngày thống kê |
| `workLocationId` | Không | Chỉ lấy nhân viên thuộc địa điểm tại ngày thống kê |
| `employmentStatus` | Không | Lọc theo trạng thái hồ sơ hiện tại |

## Trình độ và thâm niên

```http
GET /api/reports/hr/workforce-distribution
Authorization: Bearer <access-token>
```

Yêu cầu permission `report.hr.read`. Response gồm tổng nhân viên, thâm niên trung bình,
phân bố trình độ và phân bố thâm niên. Hồ sơ chưa có trình độ vẫn xuất hiện trong nhóm
`NOT_UPDATED`.

## Mức lương cơ bản

```http
GET /api/reports/payroll/salary-distribution
Authorization: Bearer <access-token>
```

Yêu cầu permission `report.payroll.read`. API chỉ trả dữ liệu tổng hợp, không trả lương của
từng cá nhân. Mức lương được lấy từ bản ghi `employee_salary_history` có hiệu lực tại
`asOfDate`; nhân viên thiếu cấu hình vẫn xuất hiện trong nhóm `NOT_CONFIGURED`.

Các khoảng lương MVP:

- Dưới 10 triệu.
- 10 đến dưới 15 triệu.
- 15 đến dưới 20 triệu.
- 20 đến dưới 30 triệu.
- Từ 30 triệu.

## Biến động nhân sự

```http
GET /api/reports/hr/turnover?year=2026
Authorization: Bearer <access-token>
```

Yêu cầu `report.hr.read`. `year` bắt buộc (2000–9999) và không được ở tương lai; năm hiện tại chỉ tính
đến tháng hiện tại. Response gồm:

- `headcountStart`, `headcountEnd`: quân số cuối ngày trước kỳ và cuối ngày cuối kỳ.
- `hires`, `terminations`, `turnoverRate`: số vào làm, số nghỉ việc và tỷ lệ nghỉ việc (%), bằng số
  nghỉ việc chia quân số bình quân `(đầu kỳ + cuối kỳ) / 2`.
- `months`: các chỉ số trên theo từng tháng.
- `events`: danh sách từng lần vào làm (`HIRE`) và nghỉ việc (`TERMINATION`, kèm trạng thái kết thúc và lý do).
  Người đã được duyệt nghỉ mà chưa tới ngày làm việc cuối (trong tháng hiện tại) mang trạng thái kết thúc đã lên lịch.

Người nghỉ việc ngày *d* không còn tính vào quân số cuối ngày *d*, nên luôn có
`đầu kỳ + vào − nghỉ = cuối kỳ`. Với role theo đơn vị hoặc địa điểm, phạm vi được xét vào ngày làm việc
cuối cùng của từng người trong kỳ, vì phân công của người đã nghỉ đã đóng; nhờ vậy người rời đơn vị vẫn
được đếm. Riêng với các role này, điều chuyển giữa các đơn vị/địa điểm không được tính là vào hay nghỉ:
người chuyển đến hoặc chuyển đi chỉ làm quân số đầu/cuối kỳ thay đổi, nên đẳng thức trên có thể lệch đúng
bằng số người điều chuyển. Với phạm vi toàn công ty đẳng thức luôn đúng. Hồ sơ xóa mềm (tạo nhầm) không được tính.

## Tình hình nhân sự theo tháng

```http
GET /api/reports/hr/monthly-status?year=2026&month=10
Authorization: Bearer <access-token>
```

Yêu cầu `report.hr.read`. `year` (2000–9999) và `month` (1–12) bắt buộc, tháng không được ở tương lai.
Response gồm:

- `headcountStart`, `hires`, `terminations`, `headcountEnd`: như biến động nhân sự, cho một tháng.
- `employeesOnLeave`, `leaveDays`: số người có nghỉ theo đơn đã duyệt và tổng ngày nghỉ.
- `absentDays`: tổng ngày vắng không phép.
- `rows`: từng người làm ít nhất một ngày trong tháng (sắp theo mã nhân viên), gồm đơn vị và chức danh
  tại ngày cuối cùng họ làm trong tháng, `status` là `NEW_HIRE` (vào làm trong tháng), `TERMINATED`
  (nghỉ việc trong tháng, kèm `terminationDate` và `endStatus`; người chưa tới ngày làm việc cuối mang
  trạng thái kết thúc đã lên lịch) hoặc `ACTIVE`; số ngày nghỉ theo loại
  (`annualLeaveDays`, `sickLeaveDays`, `maternityLeaveDays`, `unpaidLeaveDays`, `otherLeaveDays`), tổng
  `leaveDays` và `absentDays`.

Ngày nghỉ lấy từ bảng công: phút nghỉ của đơn đã duyệt chia số phút của ca, nên nửa ngày là 0,5. Tháng
hiện tại tính cả đơn đã duyệt cho những ngày sắp tới. Phạm vi được xét như biến động nhân sự.

## Phép năm

```http
GET /api/reports/hr/leave-balances?year=2026&keyword=&organizationUnitId=
Authorization: Bearer <access-token>
```

Yêu cầu `report.hr.read`. Trả mỗi nhân viên trong phạm vi có làm việc trong năm một dòng
(`employeeCode`, `employeeName`, `balance`), xếp theo mã. `balance` cùng dạng với
`GET /api/leave-entitlements/employees/{employeeId}`: phút được cấp, đã dùng, đang chờ duyệt, còn lại và
số phút một ngày công. `keyword` lọc theo mã, họ tên hoặc email công việc; `organizationUnitId` theo đơn
vị của phân công chính vào ngày làm việc cuối của từng người trong năm (tính đến hôm nay), nên người đã nghỉ
hoặc đã chuyển đi trong năm vẫn nằm ở đơn vị cũ. Phạm vi được xét như báo cáo biến động nhân sự.

## Quỹ lương, bảo hiểm và thuế

```http
GET /api/reports/payroll/summary?year=2026
Authorization: Bearer <access-token>
```

Yêu cầu `report.payroll.read` với scope `COMPANY`, giống bảng lương. Cộng từ phiếu lương của các kỳ đã duyệt
(`APPROVED`, `PAID`, `LOCKED`) trong năm; kỳ mới tính có thể còn đổi nên chưa được cộng:

- `total`: cả năm.
- `months`: 12 tháng; `periodId` và `status` là `null` khi tháng chưa có kỳ lương, kỳ chưa duyệt
  (`DRAFT`, `CALCULATED`) thì `payslipCount = 0`.
- `units`: theo đơn vị ghi trên phiếu lương lúc tính, đơn vị có tổng thu nhập lớn nhất trước.

Mỗi nhóm có số phiếu lương, tổng thu nhập, phụ cấp, tăng ca, BHXH/BHYT/BHTN, thuế TNCN, khấu trừ khác và
thực nhận. Bảo hiểm ở đây là phần **người lao động** đóng (bị khấu trừ vào lương); hệ thống chưa lưu tỷ lệ
phần doanh nghiệp đóng nên báo cáo không có chi phí bảo hiểm của doanh nghiệp.
