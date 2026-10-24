# API Reference — Payroll

Lương cơ bản, hồ sơ bảo hiểm/thuế, người phụ thuộc, kỳ lương, phiếu lương, khoản cộng/trừ nhập tay và xuất bảng lương. Dữ liệu công (chấm công, đơn nghỉ, tăng ca) là đầu vào của kỳ lương và nằm trong [ATTENDANCE.md](ATTENDANCE.md).

Phạm vi đang triển khai: nhân viên **cư trú tại Việt Nam**, nhận lương bằng VND, kỳ lương tháng từ 01/2020 đến 12/2026. Ngày làm việc chuẩn là thứ 2–thứ 7 trừ ngày lễ do công ty khai báo. Quy tắc thuế và bảo hiểm có ngày hiệu lực trong database; khi sang năm hoặc có thay đổi pháp luật phải thêm quy tắc mới rồi kiểm thử trước khi tính kỳ đó. Hệ thống báo lỗi nếu không tìm thấy quy tắc phù hợp.

Quy trình một tháng:

1. HR (`compensation.manage`) đặt [chính sách phụ cấp](ALLOWANCE.md) chung, nhập [mức lương](#lịch-sử-lương-cơ-bản), [hồ sơ bảo hiểm/thuế](#hồ-sơ-bảo-hiểm-và-thuế) và [người phụ thuộc](#người-phụ-thuộc) cho từng nhân viên; dữ liệu công được hoàn thiện theo [ATTENDANCE.md](ATTENDANCE.md#hoàn-thiện-dữ-liệu-trước-khi-tính-lương).
2. Kế toán (`payroll.calculate`) [tạo kỳ](#post-apipayrollperiods), nhập [khoản cộng/trừ](#khoản-cộngtrừ-nhập-tay) nếu có, rồi [tính lương](#post-apipayrollperiodsidcalculate).
3. Người duyệt lương (`payroll.approve`, khác người tính) [duyệt](#post-apipayrollperiodsidapprove), sau khi trả thì [ghi nhận đã trả](#post-apipayrollperiodsidmark-paid) và [khóa kỳ](#post-apipayrollperiodsidlock).
4. Nhân viên xem [phiếu lương](#phiếu-lương) của mình từ khi kỳ được duyệt.

---

## Endpoint access

| Endpoint | Quyền | Mô tả |
|:---------|:------|:------|
| `GET /api/compensation/employees/{employeeId}/salary-history` | `compensation.read` (trong scope) | Lịch sử lương cơ bản |
| `GET /api/compensation/employees/{employeeId}/salary-history/effective` | `compensation.read` (trong scope) | Mức lương có hiệu lực vào một ngày |
| `POST /api/compensation/employees/{employeeId}/salary-history` | `compensation.manage` (trong scope) | Đặt mức lương mới |
| `GET /api/compensation/employees/{employeeId}/payroll-profiles` | `compensation.read` (trong scope) | Lịch sử hồ sơ bảo hiểm/thuế |
| `POST /api/compensation/employees/{employeeId}/payroll-profiles` | `compensation.manage` (trong scope) | Tạo hồ sơ bảo hiểm/thuế mới |
| `GET /api/compensation/employees/{employeeId}/tax-dependents` | `compensation.read` (trong scope) | Người phụ thuộc |
| `POST /api/compensation/employees/{employeeId}/tax-dependents` | `compensation.manage` (trong scope) | Đăng ký người phụ thuộc |
| `PUT /api/compensation/employees/{employeeId}/tax-dependents/{dependentId}/end` | `compensation.manage` (trong scope) | Kết thúc người phụ thuộc |
| `POST /api/payroll/periods` | `payroll.calculate`, phạm vi `COMPANY` | Tạo kỳ lương |
| `GET /api/payroll/periods` | `report.payroll.read`, phạm vi `COMPANY` | Danh sách kỳ lương |
| `GET /api/payroll/periods/{id}` | `report.payroll.read`, phạm vi `COMPANY` | Chi tiết kỳ lương |
| `POST /api/payroll/periods/{id}/calculate` | `payroll.calculate`, phạm vi `COMPANY` | Tính (lại) phiếu lương |
| `POST /api/payroll/periods/{id}/approve` | `payroll.approve`, phạm vi `COMPANY` | Duyệt kỳ |
| `POST /api/payroll/periods/{id}/mark-paid` | `payroll.mark_paid`, phạm vi `COMPANY` | Ghi nhận đã trả |
| `POST /api/payroll/periods/{id}/lock` | `payroll.lock`, phạm vi `COMPANY` | Khóa kỳ |
| `POST /api/payroll/periods/{id}/cancel` | `payroll.calculate`, phạm vi `COMPANY` | Hủy kỳ chưa duyệt |
| `GET /api/payroll/periods/{id}/export` | `report.payroll.read`, phạm vi `COMPANY` | Tải bảng lương Excel |
| `GET /api/payroll/periods/{id}/payslips` | `report.payroll.read`, phạm vi `COMPANY` | Bảng lương: mọi phiếu của kỳ |
| `GET /api/payroll/periods/{periodId}/adjustments` | `payroll.calculate`, phạm vi `COMPANY` | Khoản cộng/trừ của kỳ |
| `POST /api/payroll/periods/{periodId}/adjustments` | `payroll.calculate`, phạm vi `COMPANY` | Thêm khoản cộng/trừ |
| `DELETE /api/payroll/periods/{periodId}/adjustments/{adjustmentId}` | `payroll.calculate`, phạm vi `COMPANY` | Xóa khoản cộng/trừ |
| `GET /api/payroll/payslips/{id}` | `payroll.self.read` (chính mình) hoặc `report.payroll.read` (trong scope) | Chi tiết phiếu lương |
| `GET /api/payroll/employees/{employeeId}/payslips` | `payroll.self.read` (chính mình) hoặc `report.payroll.read` (trong scope) | Phiếu lương của một nhân viên |

### Sau ngày làm việc cuối

Khi nhân viên kết thúc làm việc, hoặc HR đã lên lịch kết thúc, `terminationDate` là ngày làm việc cuối và
lương chỉ tính đến ngày đó. Vì vậy:

- Lương cơ bản, hồ sơ bảo hiểm/thuế và người phụ thuộc mới không được có `effectiveFrom` sau ngày đó
  (`400 AFTER_EMPLOYMENT_END`). Mục có hiệu lực đến ngày đó vẫn thêm được, để đính chính trước khi
  chốt lương tháng cuối; người phụ thuộc không ghi `effectiveTo`, hoặc ghi ngày sau ngày đó, được
  lưu với `effectiveTo` là ngày làm việc cuối.
- Lúc kết thúc, lương cơ bản, hồ sơ và người phụ thuộc còn chạy quá ngày làm việc cuối được đóng tại
  ngày đó. Mục bắt đầu sau ngày đó (nhập trước khi có quy tắc này) giữ nguyên và không bao giờ áp dụng.

Tất cả endpoint yêu cầu Bearer token; thiếu hoặc hết hạn trả `401 UNAUTHORIZED`. Không có permission trả `403 FORBIDDEN`. "Trong scope" nghĩa là nhân viên nằm trong phạm vi của role cấp permission đó (theo phân công hiện hành); "phạm vi `COMPANY`" nghĩa là role phải có scope toàn công ty, scope hẹp hơn trả `403 FORBIDDEN`.

Role hệ thống mặc định:

| Role | Permission liên quan |
|:-----|:---------------------|
| `EMPLOYEE` | `payroll.self.read` |
| `HR_MANAGER` | `compensation.read`, `compensation.manage`, `report.payroll.read` |
| `PAYROLL_ACCOUNTANT` | `compensation.read`, `compensation.manage`, `payroll.calculate`, `report.payroll.read` |
| `PAYROLL_APPROVER` | `payroll.approve`, `payroll.mark_paid`, `payroll.lock`, `report.payroll.read` |
| `DIRECTOR`, `COMPANY_OWNER` | `compensation.read` (chỉ xem) |

### Vòng đời kỳ lương

```
DRAFT ──calculate──▶ CALCULATED ──approve──▶ APPROVED ──mark-paid──▶ PAID ──lock──▶ LOCKED
  ▲                      │
  └──── dữ liệu đổi ─────┘        DRAFT / CALCULATED ──cancel──▶ CANCELLED
```

- `calculate` chạy được ở `DRAFT` và `CALCULATED` (tính lại xóa phiếu cũ và tạo lại).
- Khi kỳ đang `CALCULATED`, mọi thay đổi dữ liệu đầu vào đưa kỳ về `DRAFT` và xóa phiếu lương: mức lương, hồ sơ bảo hiểm/thuế, người phụ thuộc, khoản cộng/trừ, ngày lễ. Người dùng phải tính lại. Riêng dữ liệu công đổi sau lần tính không đưa kỳ về `DRAFT`, nhưng `approve` bị chặn cho tới khi tính lại (xem [PAYROLL_LIFECYCLE.md](../concepts/PAYROLL_LIFECYCLE.md#4-thay-đổi-dữ-liệu-hồi-tố)).
- Từ `APPROVED` trở đi, các thay đổi ảnh hưởng kỳ đó bị chặn với `409 PAYROLL_PERIOD_LOCKED`. Kỳ `APPROVED` hiện không có luồng mở lại.
- `CANCELLED` là trạng thái cuối; tháng đó có thể tạo kỳ mới.

---

## Công thức

- Lương cơ bản từng ngày = lương tháng có hiệu lực × `payableMinutes` / (số ngày làm việc chuẩn của tháng × phút chuẩn của ca ngày đó). Cộng các ngày được `basicSalaryPay`.
- Phụ cấp chức vụ = mức tháng của chức danh trong phân công chính ngày đó; phụ cấp thâm niên = lương tháng × % của bậc ứng với số năm tròn tính từ ngày bắt đầu thâm niên (xem [ALLOWANCE.md](ALLOWANCE.md)). Không có quy tắc thì khoản đó bằng 0. Cả hai áp dụng cùng tỷ lệ phút công được trả lương; tổng hai khoản là `allowancePay`. Nghỉ có lương giữ phần phụ cấp tương ứng; nghỉ không lương, nghỉ do bảo hiểm chi trả hoặc vắng không phép không được trả phần đó.
- Tăng ca (`overtimePay`) = giờ tăng ca **đã duyệt** × đơn giá giờ × hệ số đã duyệt, cộng theo từng ngày; đơn giá giờ = lương tháng có hiệu lực ngày đó ÷ (số ngày làm việc chuẩn của tháng × phút chuẩn của ca ngày đó) × 60. Phút công thường và phút tăng ca được tách riêng, nên tăng ca không làm tăng công tính lương. Hệ số tối thiểu: 150% ngày thường, 200% Chủ nhật, 300% ngày lễ công ty. Chưa tính cộng thêm cho ca đêm.
- `grossPay = basicSalaryPay + allowancePay + overtimePay + otherEarningsPay`, trong đó `otherEarningsPay` là tổng thưởng và phụ cấp khác nhập tay (xem [Khoản cộng/trừ nhập tay](#khoản-cộngtrừ-nhập-tay)).
- BHXH/BHYT/BHTN phần nhân viên dùng **lương đóng bảo hiểm do HR nhập** (`insuranceSalary` của hồ sơ), theo trạng thái tham gia và trần có hiệu lực trong tháng làm việc. Căn cứ này tách khỏi `grossPay`; tăng ca không tự động trở thành căn cứ đóng.
- Thu nhập tính thuế = `max(0, grossPay − taxExemptOvertimePay − taxExemptOtherEarnings − BHXH − BHYT − BHTN − giảm trừ bản thân − giảm trừ người phụ thuộc)`. Người duyệt tăng ca chỉ đánh dấu `taxExempt: true` sau khi đối soát điều kiện và giới hạn pháp luật; tăng ca chưa xác nhận vẫn chịu thuế. Thuế TNCN tính theo bậc lũy tiến tại **ngày trả lương dự kiến** (`taxPaymentDate`); số người phụ thuộc cũng lấy tại ngày đó. Nguồn: [hướng dẫn miễn thuế tiền tăng ca năm 2026](https://media.chinhphu.vn/cac-truong-hop-tien-luong-tien-cong-duoc-mien-thue-tncn-102260715163431228.htm).
- `netPay = grossPay − BHXH − BHYT − BHTN − thuế TNCN − otherDeductions`. Khấu trừ khác (tạm ứng, phạt) trừ sau thuế và không làm giảm thu nhập tính thuế.

Phiếu lương lưu snapshot mã, tên, đơn vị, địa điểm của nhân viên, số tiền bảo hiểm từng loại, hai căn cứ đóng sau khi áp trần, phần tăng ca miễn thuế, thu nhập tính thuế, thuế và `netPay`, nên phiếu đã tính không đổi khi hồ sơ nhân viên đổi sau đó.

---

## Đối tượng dùng chung

Tiền là số thập phân VND (`BigDecimal`, 2 chữ số thập phân). Ngày dạng `YYYY-MM-DD`; thời điểm là ISO-8601 UTC.

### `SalaryHistoryView`

```json
{
  "id": 7,
  "employeeId": 2,
  "baseSalary": 15000000.00,
  "effectiveFrom": "2026-01-01",
  "effectiveTo": null,
  "approvedByAccountId": 3,
  "reason": "Mức lương ban đầu",
  "note": null,
  "createdAt": "2026-01-02T03:00:00Z"
}
```

| Field | Mô tả |
|:------|:------|
| `baseSalary` | Lương cơ bản tháng |
| `effectiveFrom` / `effectiveTo` | Khoảng hiệu lực; `effectiveTo` là `null` với mức đang áp dụng |
| `approvedByAccountId` | Account đã nhập mức lương |
| `reason`, `note` | Lý do và ghi chú, `null` nếu bỏ trống |

### `Profile` — hồ sơ bảo hiểm/thuế

```json
{
  "id": 4,
  "employeeId": 2,
  "effectiveFrom": "2026-01-01",
  "effectiveTo": null,
  "taxResident": true,
  "socialInsurance": true,
  "healthInsurance": true,
  "unemploymentInsurance": true,
  "insuranceSalary": 12000000.00,
  "wageRegion": 1
}
```

| Field | Mô tả |
|:------|:------|
| `taxResident` | Cá nhân cư trú; hiện luôn `true` (không cư trú bị từ chối) |
| `socialInsurance`, `healthInsurance`, `unemploymentInsurance` | Có tham gia BHXH, BHYT, BHTN |
| `insuranceSalary` | Lương làm căn cứ đóng bảo hiểm theo hợp đồng, do HR xác nhận; không suy ra từ `grossPay` |
| `wageRegion` | Vùng lương tối thiểu 1–4, dùng cho mức sàn và trần BHTN |

### `Dependent` — người phụ thuộc

```json
{
  "id": 9,
  "employeeId": 2,
  "fullName": "Nguyễn A",
  "identifier": "012345678901",
  "effectiveFrom": "2026-01-01",
  "effectiveTo": null
}
```

`identifier` là số định danh, `null` nếu chưa có. `effectiveTo` là `null` khi còn giảm trừ.

### `PayrollPeriodResponse`

```json
{
  "id": 12,
  "year": 2026,
  "month": 10,
  "periodStart": "2026-10-01",
  "periodEnd": "2026-10-31",
  "taxPaymentDate": "2026-11-05",
  "status": "CALCULATED",
  "calculatedByAccountId": 5,
  "calculatedAt": "2026-11-01T02:00:00Z",
  "approvedByAccountId": null,
  "approvedAt": null,
  "paidByAccountId": null,
  "paidAt": null,
  "lockedByAccountId": null,
  "lockedAt": null,
  "payslipCount": 4,
  "totalGrossPay": 98348372.00,
  "totalNetPay": 87204935.00
}
```

| Field | Mô tả |
|:------|:------|
| `periodStart` / `periodEnd` | Ngày đầu và cuối tháng của kỳ |
| `taxPaymentDate` | Ngày trả lương dự kiến (sau `mark-paid` là ngày trả thực tế); quyết định quy tắc thuế và số người phụ thuộc |
| `status` | `DRAFT`, `CALCULATED`, `APPROVED`, `PAID`, `LOCKED`, `CANCELLED` (xem [Vòng đời kỳ lương](#vòng-đời-kỳ-lương)) |
| `*ByAccountId`, `*At` | Ai và khi nào tính, duyệt, ghi nhận trả, khóa; `null` khi chưa xảy ra. Tính lại sau khi dữ liệu đổi xóa thông tin tính trước đó |
| `payslipCount`, `totalGrossPay`, `totalNetPay` | Số phiếu lương hiện có của kỳ và tổng thu nhập, tổng thực nhận của các phiếu đó; `0` khi kỳ chưa tính |

### `PayslipResponse`

```json
{
  "id": 301,
  "payrollPeriodId": 12,
  "periodYear": 2026,
  "periodMonth": 10,
  "employeeId": 2,
  "employeeCodeSnapshot": "NV0002",
  "employeeNameSnapshot": "Trần Thị Bình",
  "positionSnapshot": "Chuyên viên nhân sự",
  "workLocationSnapshot": "Trụ sở chính",
  "organizationUnitSnapshot": "Phòng Nhân sự",
  "contractualBasicSalary": 15000000.00,
  "scheduledWorkMinutes": 12960,
  "payableWorkMinutes": 12900,
  "approvedOvertimeMinutes": 300,
  "basicSalaryPay": 14930555.56,
  "allowancePay": 0.00,
  "overtimePay": 520833.33,
  "taxExemptOvertimePay": 0.00,
  "otherEarningsPay": 0.00,
  "taxExemptOtherEarnings": 0.00,
  "grossPay": 15451388.89,
  "insuranceSalaryBase": 12000000.00,
  "unemploymentInsuranceBase": 12000000.00,
  "employeeSocialInsurance": 960000.00,
  "employeeHealthInsurance": 180000.00,
  "employeeUnemploymentInsurance": 120000.00,
  "taxableIncome": 0.00,
  "personalIncomeTax": 0.00,
  "otherDeductions": 0.00,
  "netPay": 14191388.89,
  "items": [
    {
      "id": 1201,
      "componentType": "BASE_SALARY",
      "description": "Lương cơ bản",
      "quantity": 12900,
      "unitRate": 15000000.00,
      "multiplier": 1,
      "amount": 14930555.56
    }
  ]
}
```

| Field | Mô tả |
|:------|:------|
| `periodYear`, `periodMonth` | Tháng của kỳ, để hiển thị phiếu mà không cần quyền đọc kỳ lương |
| `*Snapshot` | Mã, họ tên, chức danh, địa điểm, đơn vị của nhân viên lúc tính |
| `contractualBasicSalary` | Mức lương có hiệu lực vào ngày làm việc cuối của nhân viên trong kỳ |
| `scheduledWorkMinutes` / `payableWorkMinutes` | Phút công chuẩn theo lịch / phút được trả lương thường |
| `approvedOvertimeMinutes` | Tổng phút tăng ca đã duyệt |
| `basicSalaryPay`, `allowancePay`, `overtimePay`, `otherEarningsPay`, `grossPay` | Các khoản thu nhập theo [Công thức](#công-thức) |
| `taxExemptOvertimePay`, `taxExemptOtherEarnings` | Phần tăng ca và phụ cấp khác được miễn thuế |
| `insuranceSalaryBase`, `unemploymentInsuranceBase` | Căn cứ đóng BHXH/BHYT và BHTN sau khi áp trần |
| `employeeSocialInsurance`, `employeeHealthInsurance`, `employeeUnemploymentInsurance` | Bảo hiểm phần nhân viên |
| `taxableIncome`, `personalIncomeTax` | Thu nhập tính thuế và thuế TNCN |
| `otherDeductions` | Tổng khấu trừ nhập tay |
| `items` | Từng dòng của phiếu (`PayslipItemResponse`) |

`PayslipItemResponse.componentType`:

| Giá trị | Dòng |
|:--------|:-----|
| `BASE_SALARY` | Lương cơ bản |
| `POSITION_ALLOWANCE` | Phụ cấp chức vụ |
| `SENIORITY_ALLOWANCE` | Phụ cấp thâm niên |
| `OVERTIME` | Tăng ca, một dòng mỗi ngày (`quantity` là phút, `multiplier` là hệ số) |
| `BONUS`, `TAXABLE_ALLOWANCE`, `NON_TAXABLE_ALLOWANCE`, `DEDUCTION` | Khoản nhập tay, trùng `type` của [khoản cộng/trừ](#khoản-cộngtrừ-nhập-tay) |

### `PayrollAdjustmentResponse`

```json
{
  "id": 3,
  "payrollPeriodId": 12,
  "employeeId": 2,
  "employeeCode": "NV0002",
  "employeeName": "Trần Thị Bình",
  "type": "BONUS",
  "description": "Thưởng Tết",
  "amount": 5000000.00,
  "createdAt": "2026-10-28T03:00:00Z"
}
```

---

## Lịch sử lương cơ bản

Mỗi lần tăng hoặc giảm lương là một dòng mới có khoảng hiệu lực; mức cũ tự đóng vào ngày trước ngày hiệu lực của mức mới, và các khoảng không được chồng nhau. Mức lương **không** nhập khi tạo hồ sơ nhân viên, nhưng bắt buộc trước khi tính kỳ lương có ngày làm việc của nhân viên đó. Trên web, HR chọn **Nhập và xem lương** ở hồ sơ nhân viên để mở thẻ "Lương cơ bản" với mã nhân viên và ngày vào làm điền sẵn.

### GET `/api/compensation/employees/{employeeId}/salary-history`

Lịch sử lương của nhân viên, mới nhất trước.

#### Response `200 OK`

```json
{ "success": true, "data": [ { "id": 7, "employeeId": 2, "baseSalary": 15000000.00, "effectiveFrom": "2026-01-01", "effectiveTo": null, "approvedByAccountId": 3, "reason": "Mức lương ban đầu", "note": null, "createdAt": "2026-01-02T03:00:00Z" } ], "error": null }
```

Mỗi phần tử là [`SalaryHistoryView`](#salaryhistoryview).

#### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không có `compensation.read` hoặc nhân viên ngoài scope |
| 404 | `EMPLOYEE_NOT_FOUND` | Nhân viên không tồn tại hoặc đã xóa |

### GET `/api/compensation/employees/{employeeId}/salary-history/effective`

Mức lương có hiệu lực vào một ngày.

| Query | Type | Bắt buộc | Mô tả |
|:------|:-----|:--------:|:------|
| `date` | date | ✅ | Ngày cần tra |

#### Response `200 OK`

Trả một [`SalaryHistoryView`](#salaryhistoryview).

#### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | `date` sai định dạng |
| 403 | `FORBIDDEN` | Không có `compensation.read` hoặc nhân viên ngoài scope |
| 404 | `EMPLOYEE_NOT_FOUND` | Nhân viên không tồn tại hoặc đã xóa |
| 404 | `RESOURCE_NOT_FOUND` | Không có mức lương nào hiệu lực vào ngày đó |
| 500 | `INTERNAL_ERROR` | Thiếu hẳn tham số `date` (lỗi đã biết: lẽ ra là `400`) |

### POST `/api/compensation/employees/{employeeId}/salary-history`

Đặt mức lương mới. Nếu đang có mức mở, mức đó được đóng vào ngày `effectiveFrom − 1`.

#### Request

```json
{
  "baseSalary": 15000000,
  "effectiveFrom": "2026-01-01",
  "reason": "Mức lương ban đầu",
  "note": null
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `baseSalary` | number | ✅ | Lớn hơn 0 |
| `effectiveFrom` | date | ✅ | Không trước ngày vào làm; phải sau ngày bắt đầu của mức đang mở |
| `reason` | string | | Chuỗi trắng được lưu là `null` |
| `note` | string | | Chuỗi trắng được lưu là `null` |

#### Response `201 Created`

Trả [`SalaryHistoryView`](#salaryhistoryview) vừa tạo. Kỳ `CALCULATED` có ngày kết thúc từ `effectiveFrom` trở đi được đưa về `DRAFT` và xóa phiếu lương.

#### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu/sai field, `effectiveFrom` trước ngày vào làm hoặc không sau mức đang mở (`field: effectiveFrom`) |
| 400 | `AFTER_EMPLOYMENT_END` | `effectiveFrom` sau ngày làm việc cuối của nhân viên (`field: effectiveFrom`), xem [Sau ngày làm việc cuối](#sau-ngày-làm-việc-cuối) |
| 403 | `FORBIDDEN` | Không có `compensation.manage` hoặc nhân viên ngoài scope |
| 404 | `EMPLOYEE_NOT_FOUND` | Nhân viên không tồn tại |
| 409 | `PAYROLL_PERIOD_LOCKED` | Một kỳ kết thúc từ `effectiveFrom` trở đi đã `APPROVED`, `PAID` hoặc `LOCKED` |
| 409 | `CONFLICT` | Dữ liệu lỗi: nhân viên có nhiều hơn một mức lương đang mở |

---

## Hồ sơ bảo hiểm và thuế

Hồ sơ quyết định nhân viên có đóng BHXH/BHYT/BHTN không và căn cứ đóng. Kỳ lương dùng hồ sơ có hiệu lực vào **ngày cuối tháng làm việc**; thiếu hồ sơ thì tính lương báo lỗi theo nhân viên thay vì giả định không tham gia.

### GET `/api/compensation/employees/{employeeId}/payroll-profiles`

Các hồ sơ của nhân viên, mới nhất trước. Mỗi phần tử là [`Profile`](#profile--hồ-sơ-bảo-hiểmthuế).

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không có `compensation.read` hoặc nhân viên ngoài scope |
| 404 | `EMPLOYEE_NOT_FOUND` | Nhân viên không tồn tại hoặc đã xóa |

### POST `/api/compensation/employees/{employeeId}/payroll-profiles`

Tạo hồ sơ mới; hồ sơ đang mở được đóng vào ngày `effectiveFrom − 1`.

#### Request

```json
{
  "effectiveFrom": "2026-10-01",
  "taxResident": true,
  "socialInsurance": true,
  "healthInsurance": true,
  "unemploymentInsurance": true,
  "insuranceSalary": 12000000,
  "wageRegion": 1
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `effectiveFrom` | date | ✅ | Không trước ngày vào làm; phải sau ngày bắt đầu của hồ sơ đang mở |
| `taxResident` | boolean | ✅ | Phải là `true`; người không cư trú chưa được hỗ trợ |
| `socialInsurance` | boolean | ✅ | |
| `healthInsurance` | boolean | ✅ | |
| `unemploymentInsurance` | boolean | ✅ | |
| `insuranceSalary` | number | ✅ | ≥ 0; phải > 0 khi tham gia ít nhất một loại bảo hiểm. Khi tính lương phải không thấp hơn lương tối thiểu vùng |
| `wageRegion` | integer | ✅ | 1–4 |

#### Response `201 Created`

Trả [`Profile`](#profile--hồ-sơ-bảo-hiểmthuế) vừa tạo. Kỳ `CALCULATED` có ngày kết thúc từ `effectiveFrom` trở đi được đưa về `DRAFT`.

#### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu/sai field, ngày hiệu lực không hợp lệ, `taxResident: false`, hoặc tham gia bảo hiểm mà `insuranceSalary` bằng 0 |
| 400 | `AFTER_EMPLOYMENT_END` | `effectiveFrom` sau ngày làm việc cuối của nhân viên (`field: effectiveFrom`), xem [Sau ngày làm việc cuối](#sau-ngày-làm-việc-cuối) |
| 403 | `FORBIDDEN` | Không có `compensation.manage` hoặc nhân viên ngoài scope |
| 404 | `EMPLOYEE_NOT_FOUND` | Nhân viên không tồn tại hoặc đã xóa |
| 409 | `PAYROLL_PERIOD_LOCKED` | Một kỳ kết thúc từ `effectiveFrom` trở đi đã `APPROVED`, `PAID` hoặc `LOCKED` |

---

## Người phụ thuộc

Mỗi người phụ thuộc còn hiệu lực vào **ngày trả lương** của kỳ được giảm trừ một suất. Vì vậy thay đổi người phụ thuộc ảnh hưởng các kỳ có `taxPaymentDate` nằm trong khoảng thay đổi, không theo tháng làm việc.

### GET `/api/compensation/employees/{employeeId}/tax-dependents`

Người phụ thuộc của nhân viên, mới nhất trước. Mỗi phần tử là [`Dependent`](#dependent--người-phụ-thuộc). Lỗi như `GET …/payroll-profiles`.

### POST `/api/compensation/employees/{employeeId}/tax-dependents`

#### Request

```json
{
  "fullName": "Nguyễn A",
  "identifier": "012345678901",
  "effectiveFrom": "2026-01-01",
  "effectiveTo": null
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `fullName` | string | ✅ | Không rỗng; không trùng tên với người phụ thuộc khác của nhân viên có khoảng hiệu lực chồng lấn |
| `identifier` | string | | Số định danh; chuỗi trắng lưu `null` |
| `effectiveFrom` | date | ✅ | Không trước ngày vào làm |
| `effectiveTo` | date | | Không trước `effectiveFrom`; bỏ trống nếu còn giảm trừ |

#### Response `201 Created`

Trả [`Dependent`](#dependent--người-phụ-thuộc) vừa tạo. Kỳ `CALCULATED` có `taxPaymentDate` trong khoảng hiệu lực được đưa về `DRAFT`.

#### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu `fullName`/`effectiveFrom`, hoặc khoảng hiệu lực không hợp lệ |
| 400 | `AFTER_EMPLOYMENT_END` | `effectiveFrom` sau ngày làm việc cuối của nhân viên (`field: effectiveFrom`), xem [Sau ngày làm việc cuối](#sau-ngày-làm-việc-cuối) |
| 403 | `FORBIDDEN` | Không có `compensation.manage` hoặc nhân viên ngoài scope |
| 404 | `EMPLOYEE_NOT_FOUND` | Nhân viên không tồn tại hoặc đã xóa |
| 409 | `CONFLICT` | Đã có người phụ thuộc cùng tên trong khoảng hiệu lực chồng lấn |
| 409 | `PAYROLL_PERIOD_LOCKED` | Một kỳ có ngày trả trong khoảng hiệu lực đã `APPROVED`, `PAID` hoặc `LOCKED` |

### PUT `/api/compensation/employees/{employeeId}/tax-dependents/{dependentId}/end`

Kết thúc giảm trừ cho một người phụ thuộc.

#### Request

```json
{ "effectiveTo": "2026-12-31" }
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `effectiveTo` | date | ✅ | Không trước `effectiveFrom`; nếu đã có ngày kết thúc thì chỉ được rút ngắn |

#### Response `200 OK`

Trả [`Dependent`](#dependent--người-phụ-thuộc) sau khi cập nhật. Kỳ `CALCULATED` có ngày trả sau `effectiveTo` (trong khoảng cũ) được đưa về `DRAFT`.

#### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | `effectiveTo` thiếu hoặc không hợp lệ |
| 403 | `FORBIDDEN` | Không có `compensation.manage` hoặc nhân viên ngoài scope |
| 404 | `EMPLOYEE_NOT_FOUND` | Nhân viên không tồn tại hoặc đã xóa |
| 404 | `RESOURCE_NOT_FOUND` | Người phụ thuộc không tồn tại hoặc không thuộc nhân viên này |
| 409 | `PAYROLL_PERIOD_LOCKED` | Một kỳ bị ảnh hưởng đã `APPROVED`, `PAID` hoặc `LOCKED` |

---

## Kỳ lương

### POST `/api/payroll/periods`

Tạo kỳ lương `DRAFT` cho một tháng. Kỳ phủ từ ngày đầu đến ngày cuối tháng.

#### Request

```json
{ "year": 2026, "month": 10, "taxPaymentDate": "2026-11-05" }
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `year` | integer | ✅ | |
| `month` | integer | ✅ | 1–12; tháng chưa có kỳ nào khác `CANCELLED` |
| `taxPaymentDate` | date | | Ngày trả lương dự kiến; có thể bổ sung khi tính |

#### Response `200 OK`

Trả [`PayrollPeriodResponse`](#payrollperiodresponse) với `status: "DRAFT"`. Endpoint này trả `200`, không phải `201`.

#### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu `year`/`month` hoặc `month` ngoài 1–12 |
| 403 | `FORBIDDEN` | Không có `payroll.calculate` phạm vi `COMPANY` |
| 409 | `CONFLICT` | Tháng đã có kỳ chưa hủy |

### GET `/api/payroll/periods`

Danh sách kỳ lương, có phân trang. Query `page` (từ 0), `size` (mặc định 20) và `sort` (ví dụ `sort=periodStart,desc`; không truyền thì không có thứ tự cố định). Trả `PagedResult<PayrollPeriodResponse>`.

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không có `report.payroll.read` phạm vi `COMPANY` |

### GET `/api/payroll/periods/{id}`

Trả [`PayrollPeriodResponse`](#payrollperiodresponse).

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không có `report.payroll.read` phạm vi `COMPANY` |
| 404 | `PAYROLL_PERIOD_NOT_FOUND` | Kỳ không tồn tại |

### POST `/api/payroll/periods/{id}/calculate`

Tính phiếu lương cho **mọi nhân viên có thời gian làm việc trong tháng** (từ ngày vào làm đến ngày nghỉ việc). Phiếu cũ của kỳ bị xóa và tạo lại. Kỳ chuyển sang `CALCULATED`.

#### Request (không bắt buộc)

```json
{ "taxPaymentDate": "2026-11-05" }
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `taxPaymentDate` | date | | Ghi đè ngày trả dự kiến. Kỳ phải có ngày này (từ lúc tạo hoặc từ body) |

Thuế tính theo một lần trả trong một tháng thuế: không thể có hai kỳ đã tính trở đi cùng **tháng của ngày trả**.

#### Điều kiện dữ liệu

Với từng nhân viên, tính lương dừng và trả `400 VALIDATION_ERROR` kèm mã nhân viên và dữ liệu thiếu (ví dụ `Cannot calculate payroll for employee NV0002 (ID 2): missing salary history on 2026-10-31`) khi:

- Không có ngày làm việc nào của công ty trong khoảng làm việc của nhân viên (toàn bộ rơi vào Chủ nhật/ngày lễ).
- Thiếu phân công chính hoặc ca vào một ngày làm việc.
- Thiếu mức lương có hiệu lực vào một ngày làm việc.
- Thiếu bản ghi công, còn ngày `MISSING_PUNCH`, hoặc có ngày đã chấm vào chưa chấm ra.
- Thiếu hồ sơ bảo hiểm/thuế, hồ sơ không cư trú, `insuranceSalary` dưới lương tối thiểu vùng, hoặc thiếu quy tắc thuế/bảo hiểm cho ngày tương ứng.
- Từ 14 ngày làm việc nghỉ không lương/vắng không phép trở lên mà hồ sơ vẫn bật bảo hiểm.
- Khấu trừ vượt thu nhập (cần đối soát tay).

Khi lỗi, cả lần tính bị hủy và kỳ giữ trạng thái trước đó.

#### Response `200 OK`

Trả [`PayrollPeriodResponse`](#payrollperiodresponse) với `status: "CALCULATED"`, `calculatedByAccountId`, `calculatedAt`.

#### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu `taxPaymentDate`, trùng tháng thuế với kỳ khác, hoặc thiếu dữ liệu đầu vào (xem trên) |
| 403 | `FORBIDDEN` | Không có `payroll.calculate` phạm vi `COMPANY` |
| 404 | `PAYROLL_PERIOD_NOT_FOUND` | Kỳ không tồn tại |
| 409 | `PAYROLL_PERIOD_LOCKED` | Kỳ không ở `DRAFT` hoặc `CALCULATED` |

### POST `/api/payroll/periods/{id}/approve`

Duyệt kỳ đã tính. Không có body. Người duyệt phải khác người đã tính.

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Người duyệt chính là người tính |
| 403 | `FORBIDDEN` | Không có `payroll.approve` phạm vi `COMPANY` |
| 404 | `PAYROLL_PERIOD_NOT_FOUND` | Kỳ không tồn tại |
| 409 | `CONFLICT` | Kỳ không ở `CALCULATED`, hoặc dữ liệu công trong tháng đổi sau lần tính (phải tính lại) |

Trả [`PayrollPeriodResponse`](#payrollperiodresponse) với `status: "APPROVED"`. Từ đây nhân viên xem được phiếu lương của mình.

### POST `/api/payroll/periods/{id}/mark-paid`

Ghi nhận kỳ đã trả lương.

#### Request (không bắt buộc)

```json
{ "taxPaymentDate": "2026-11-05" }
```

`taxPaymentDate` là ngày trả **thực tế**; bỏ trống thì lấy hôm nay (giờ Việt Nam). Ngày này phải cùng tháng với ngày trả dự kiến đã duyệt, vì thuế đã tính theo tháng đó; khác tháng thì phải đối soát lại khấu trừ trước. Sau khi ghi nhận, `taxPaymentDate` của kỳ là ngày thực tế.

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không có `payroll.mark_paid` phạm vi `COMPANY` |
| 404 | `PAYROLL_PERIOD_NOT_FOUND` | Kỳ không tồn tại |
| 409 | `CONFLICT` | Kỳ không ở `APPROVED`, hoặc ngày trả thực tế khác tháng với ngày trả đã duyệt |

Trả [`PayrollPeriodResponse`](#payrollperiodresponse) với `status: "PAID"`.

### POST `/api/payroll/periods/{id}/lock`

Khóa kỳ đã trả. Không có body.

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không có `payroll.lock` phạm vi `COMPANY` |
| 404 | `PAYROLL_PERIOD_NOT_FOUND` | Kỳ không tồn tại |
| 409 | `CONFLICT` | Kỳ không ở `PAID` |

Trả [`PayrollPeriodResponse`](#payrollperiodresponse) với `status: "LOCKED"`.

### POST `/api/payroll/periods/{id}/cancel`

Hủy kỳ `DRAFT` hoặc `CALCULATED`. Không có body. Kỳ đã hủy không khôi phục được; phiếu lương đã tính được giữ lại, và tháng đó có thể tạo kỳ mới.

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không có `payroll.calculate` phạm vi `COMPANY` |
| 404 | `PAYROLL_PERIOD_NOT_FOUND` | Kỳ không tồn tại |
| 409 | `CONFLICT` | Kỳ không ở `DRAFT` hoặc `CALCULATED` |

Trả [`PayrollPeriodResponse`](#payrollperiodresponse) với `status: "CANCELLED"`.

### GET `/api/payroll/periods/{id}/export`

Tải bảng lương của kỳ dạng `bang-luong-<năm>-<tháng>.xlsx` (`Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `Content-Disposition: attachment`). Client nên gửi `Accept` gồm cả kiểu xlsx và `application/json`, vì lỗi vẫn trả envelope JSON như các endpoint khác.

File có một sheet, mỗi dòng một phiếu lương của kỳ, sắp theo mã nhân viên: mã, họ tên, đơn vị, địa điểm, lương hợp đồng, phút công chuẩn/được trả/tăng ca, lương theo công, phụ cấp, tiền tăng ca, tăng ca miễn thuế, thưởng và phụ cấp khác, phụ cấp khác miễn thuế, tổng thu nhập, BHXH, BHYT, BHTN, thu nhập tính thuế, thuế TNCN, khấu trừ khác và thực nhận. Dòng cuối là công thức `SUM` cho các cột tiền. Dữ liệu lấy từ snapshot trên phiếu lương, nên file của kỳ đã duyệt không đổi theo hồ sơ hiện tại. Trên web, nút **Xuất Excel** nằm trên trang bảng lương `/payroll/{id}` của mỗi kỳ đã tính trở đi.

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không có `report.payroll.read` phạm vi `COMPANY` |
| 404 | `PAYROLL_PERIOD_NOT_FOUND` | Kỳ không tồn tại |

### GET `/api/payroll/periods/{id}/payslips`

Bảng lương của kỳ: mọi phiếu lương, sắp theo mã nhân viên, mỗi phần tử là [`PayslipResponse`](#payslipresponse). Người duyệt dùng để kiểm tra trước khi duyệt; kỳ `DRAFT` chưa tính trả mảng rỗng.

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không có `report.payroll.read` phạm vi `COMPANY` |
| 404 | `PAYROLL_PERIOD_NOT_FOUND` | Kỳ không tồn tại |

---

## Phiếu lương

Người có `payroll.self.read` xem phiếu của chính mình, **chỉ từ kỳ `APPROVED` trở đi**. Người có `report.payroll.read` xem phiếu của nhân viên trong scope ở mọi trạng thái kỳ, kể cả `CALCULATED` để kiểm tra trước khi duyệt.

### GET `/api/payroll/payslips/{id}`

Trả [`PayslipResponse`](#payslipresponse).

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Phiếu của người khác mà không có `report.payroll.read` trong scope, hoặc nhân viên xem phiếu của kỳ chưa duyệt |
| 404 | `PAYSLIP_NOT_FOUND` | Phiếu không tồn tại |

### GET `/api/payroll/employees/{employeeId}/payslips`

Phiếu lương của một nhân viên, có phân trang (`page`, `size` mặc định 20, `sort`). Trả `PagedResult<PayslipResponse>`. Người không có `report.payroll.read` chỉ thấy phiếu của kỳ `APPROVED`, `PAID`, `LOCKED`.

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Nhân viên khác mà không có `report.payroll.read` trong scope |

---

## Khoản cộng/trừ nhập tay

Kế toán nhập thêm khoản cho từng nhân viên trong kỳ. Khoản chỉ đi vào phiếu lương ở lần **tính lương** tiếp theo: thêm hoặc xóa khi kỳ đang `CALCULATED` đưa kỳ về `DRAFT`; từ `APPROVED` trở đi bị chặn. Không khoản nào làm đổi căn cứ đóng bảo hiểm. Trên web, tab **Khoản cộng/trừ** của trang bảng lương `/payroll/{id}` mở danh sách và form nhập (nhập theo mã nhân viên).

| `type` | Ý nghĩa | Tổng thu nhập | Thu nhập tính thuế | Thực nhận |
|:-------|:--------|:-------------:|:------------------:|:---------:|
| `BONUS` | Thưởng | Cộng | Cộng | Cộng |
| `TAXABLE_ALLOWANCE` | Phụ cấp khác chịu thuế | Cộng | Cộng | Cộng |
| `NON_TAXABLE_ALLOWANCE` | Phụ cấp khác miễn thuế; kế toán chịu trách nhiệm đối chiếu điều kiện miễn | Cộng | Không | Cộng |
| `DEDUCTION` | Khấu trừ sau thuế: tạm ứng, phạt | Không | Không | Trừ |

Khấu trừ lớn hơn số còn lại sau thuế làm việc tính lương dừng với lỗi yêu cầu đối soát.

### GET `/api/payroll/periods/{periodId}/adjustments`

Các khoản của kỳ theo thứ tự nhập. Mỗi phần tử là [`PayrollAdjustmentResponse`](#payrolladjustmentresponse).

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không có `payroll.calculate` phạm vi `COMPANY` |
| 404 | `PAYROLL_PERIOD_NOT_FOUND` | Kỳ không tồn tại |

### POST `/api/payroll/periods/{periodId}/adjustments`

#### Request

```json
{
  "employeeId": 2,
  "type": "BONUS",
  "description": "Thưởng Tết",
  "amount": 5000000
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `employeeId` | integer | ✅ | Nhân viên chưa xóa, có thời gian làm việc trong kỳ |
| `type` | string | ✅ | `BONUS`, `TAXABLE_ALLOWANCE`, `NON_TAXABLE_ALLOWANCE`, `DEDUCTION` |
| `description` | string | ✅ | Không rỗng, tối đa 250 ký tự |
| `amount` | number | ✅ | Dương, tối đa 13 chữ số phần nguyên và 2 chữ số thập phân |

#### Response `201 Created`

Trả [`PayrollAdjustmentResponse`](#payrolladjustmentresponse) vừa tạo.

#### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu/sai field, hoặc nhân viên không làm việc trong kỳ (`field: employeeId`) |
| 403 | `FORBIDDEN` | Không có `payroll.calculate` phạm vi `COMPANY` |
| 404 | `PAYROLL_PERIOD_NOT_FOUND` | Kỳ không tồn tại |
| 404 | `EMPLOYEE_NOT_FOUND` | Nhân viên không tồn tại hoặc đã xóa |
| 409 | `PAYROLL_PERIOD_LOCKED` | Kỳ không ở `DRAFT` hoặc `CALCULATED` |

### DELETE `/api/payroll/periods/{periodId}/adjustments/{adjustmentId}`

Xóa một khoản. Trả `200 OK` với `data: null`.

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không có `payroll.calculate` phạm vi `COMPANY` |
| 404 | `PAYROLL_PERIOD_NOT_FOUND` | Kỳ không tồn tại |
| 404 | `RESOURCE_NOT_FOUND` | Khoản không tồn tại hoặc không thuộc kỳ này |
| 409 | `PAYROLL_PERIOD_LOCKED` | Kỳ không ở `DRAFT` hoặc `CALCULATED` |

---

## Quy tắc đã nạp (2020–2026)

Thuế TNCN chọn theo **ngày trả lương** (`taxPaymentDate`), bảo hiểm theo ngày cuối tháng làm việc, nên kỳ 12/2025 trả lương tháng 1/2026 dùng mức thuế 2026.

- 01/01/2020–31/12/2025: giảm trừ bản thân 11.000.000 đồng/tháng và mỗi người phụ thuộc 4.400.000 đồng/tháng (Nghị quyết 954/2020/UBTVQH14, áp dụng từ kỳ tính thuế 2020); biểu thuế lũy tiến 7 bậc theo tháng: 0–5 triệu 5%, 5–10 triệu 10%, 10–18 triệu 15%, 18–32 triệu 20%, 32–52 triệu 25%, 52–80 triệu 30%, trên 80 triệu 35% (Luật Thuế TNCN 2007 sửa đổi 2012). Nửa đầu năm 2020 thực tế đã khấu trừ theo mức 9.000.000/3.600.000 rồi quyết toán lại theo mức mới; hệ thống dùng thẳng mức của kỳ tính thuế 2020.
- Từ 01/01/2026, giảm trừ bản thân 15.500.000 đồng/tháng và mỗi người phụ thuộc 6.200.000 đồng/tháng; biểu thuế lũy tiến theo tháng: 0–10 triệu 5%, 10–30 triệu 10%, 30–60 triệu 20%, 60–100 triệu 30%, trên 100 triệu 35%. Áp dụng đến 31/12/2026 trong bộ quy tắc hiện tại. Nguồn: [Luật 109/2025/QH15](https://vanban.chinhphu.vn/?classid=1&docid=216495&pageid=27160), [giới thiệu và mức giảm trừ](https://xaydungchinhsach.chinhphu.vn/gioi-thieu-luat-thue-thu-nhap-ca-nhan-so-109-2025-qh15-119260123145437408.htm), [biểu thuế](https://xaydungchinhsach.chinhphu.vn/chi-tiet-bieu-thue-thu-nhap-ca-nhan-luy-tien-tung-phan-119260327091407544.htm).
- BHXH 8%, BHYT 1,5%, BHTN 1% phần nhân viên theo trạng thái tham gia, không đổi từ 2020. Trần BHXH/BHYT = 20 lần lương cơ sở: 29,8 triệu (lương cơ sở 1,49 triệu, 01/2020–06/2023), 36 triệu (1,8 triệu, 07/2023–06/2024), 46,8 triệu (2,34 triệu, 07/2024–06/2026) và 50,6 triệu từ 01/07/2026. Lương tối thiểu vùng I/II/III/IV trước 2026: 4,42/3,92/3,43/3,07 triệu (Nghị định 90/2019, 01/2020–06/2022), 4,68/4,16/3,64/3,25 triệu (Nghị định 38/2022, 07/2022–06/2024), 4,96/4,41/3,86/3,45 triệu (Nghị định 74/2024, 07/2024–12/2025). Trần BHTN = 20 lần lương tối thiểu vùng (vùng I/II/III/IV: 5,31/4,73/4,14/3,70 triệu từ 01/01/2026). Nguồn: [BHXH Việt Nam về tỷ lệ](https://baohiemxahoi.gov.vn/congkhai/Pages/lich-lam-viec-cua-lanh-dao-nganh.aspx?CateID=0&ItemID=24900), [trần BHXH/BHYT từ tháng 7](https://baohiemxahoi.gov.vn/chidaodieuhanh/Pages/thong-tin-bao-chi.aspx?CateID=0&ItemID=26780), [trần BHTN](https://baohiemxahoi.gov.vn/tintuc/Pages/kinh-te-xa-hoi.aspx?CateID=0&ItemID=25535&OtItem=date), [mức vùng](https://baohiemxahoi.gov.vn/tintuc/pages/linh-vuc-bao-hiem-xa-hoi.aspx?CateID=168&ItemID=26630).
- Nếu bảng công có từ 14 ngày làm việc nghỉ không lương hoặc vắng không phép, chương trình dừng tính khi hồ sơ vẫn bật tham gia bảo hiểm để HR đối soát trạng thái của tháng. Không âm thầm khấu trừ sai. Nguồn: [BHXH Việt Nam về 14 ngày nghỉ không lương](https://baohiemxahoi.gov.vn/tintuc/pages/linh-vuc-bao-hiem-xa-hoi.aspx?CateID=168&ItemID=26671&OtItem=date).

## Giới hạn cần xử lý thủ công

- Chưa hỗ trợ người không cư trú, hoa hồng tính tự động, khoản giảm trừ tự nguyện khác, truy thu, hoàn thuế và quyết toán năm. Không tự động gửi tờ khai thuế hay bảo hiểm.
- Chưa tách giờ làm đêm để cộng hệ số riêng. Tăng ca Chủ nhật/ngày lễ cần chấm vào/ra và duyệt, không tự duyệt. Việc kiểm tra trần giờ làm thêm trong ngày/năm và chứng từ miễn thuế vẫn do người duyệt tăng ca thực hiện trước khi chọn `taxExempt`.
- Tính thuế theo một lần trả lương trong một tháng. Nếu ngày trả thực tế đổi tháng hoặc có thêm đợt trả/thưởng cùng tháng, kế toán phải đối soát. Kỳ đã `APPROVED` hiện không có luồng mở lại tự động; cần xử lý điều chỉnh có kiểm soát trước khi ghi nhận `PAID`.
- Hồ sơ bảo hiểm được chọn theo ngày cuối nhân viên làm việc trong kỳ; trường hợp thay đổi chế độ trong cùng tháng và các ngoại lệ luật định cần đối soát bằng tay. Dữ liệu quy tắc nạp sẵn từ 01/01/2020 đến hết năm 2026.
- Nếu một nhân viên thuộc kỳ nhưng toàn bộ khoảng làm việc rơi vào ngày lễ/Chủ nhật, hệ thống báo lỗi yêu cầu đối soát thủ công thay vì bỏ qua nhân viên hoặc tiền tăng ca của họ.
