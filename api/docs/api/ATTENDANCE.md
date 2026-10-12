# API Reference — Attendance

Chấm công, ngày lễ công ty, đơn nghỉ và hạn mức phép năm. Đây là dữ liệu đầu vào của kỳ lương; cách tính `grossPay`, bảo hiểm, thuế TNCN và `netPay` nằm trong [PAYROLL.md](PAYROLL.md).

Lịch làm việc là **thứ 2 đến thứ 7**, trừ các ngày trong `company_holidays`. Ca được lấy từ phân công chính có hiệu lực vào từng ngày (xem [WORK_SHIFT.md](WORK_SHIFT.md)). `workDate` của ca qua đêm là ngày bắt đầu ca. Thời điểm lưu bằng UTC; ngày công được xác định theo `Asia/Ho_Chi_Minh`.

Quy trình một tháng:

1. Người có `organization.manage` khai báo ngày lễ qua `POST /api/company-holidays`.
2. HR chuẩn bị bảng công tháng qua `POST /api/attendance/prepare`. Nên chuẩn bị trước khi nhân viên bắt đầu chấm công.
3. Giờ vào/ra được ghi qua `POST /api/attendance/punches` với thời điểm cụ thể. Sau này máy chấm công của công ty sẽ gửi dữ liệu vào đây; phần tích hợp máy **chưa triển khai**, hiện API này **tạm thời không cần xác thực** để tạo dữ liệu test (kể cả cho ngày đã qua). Nhân viên chỉ xem lịch sử chấm công trên web.
4. Nhân viên nộp đơn nghỉ qua `/api/leave-requests`; người duyệt duyệt đơn và hệ thống tự gắn đơn vào bảng công.
5. HR đối soát các ngày `MISSING_PUNCH` qua `PUT /api/attendance/{id}`; người có `attendance.overtime.approve` duyệt tăng ca.
6. Kế toán tính kỳ lương. Xem [Hoàn thiện dữ liệu trước khi tính lương](#hoàn-thiện-dữ-liệu-trước-khi-tính-lương).

---

## Endpoint access

| Endpoint | Permission | Mô tả |
|:---------|:-----------|:------|
| `GET /api/company-holidays` | `organization.read`, phạm vi `COMPANY` | Danh sách ngày lễ trong khoảng ngày |
| `POST /api/company-holidays` | `organization.manage`, phạm vi `COMPANY` | Khai báo ngày lễ |
| `DELETE /api/company-holidays/{id}` | `organization.manage`, phạm vi `COMPANY` | Xóa ngày lễ |
| `POST /api/attendance/prepare` | `attendance.manage`, phạm vi `COMPANY` | Tạo bản ghi `MISSING_PUNCH` cho các ngày làm việc của tháng |
| `POST /api/attendance/punches` | Không cần xác thực (tạm thời, chỉ để test) | Ghi giờ vào/ra của một ngày công |
| `PUT /api/attendance/{id}` | `attendance.manage` | Điều chỉnh / đối soát bản ghi công |
| `POST /api/attendance/{id}/overtime-approval` | `attendance.overtime.approve` | Duyệt hoặc hủy duyệt tăng ca |
| `GET /api/attendance/{id}` | `attendance.self.read` (chính mình) hoặc `attendance.read` (trong scope) | Chi tiết một bản ghi công |
| `GET /api/attendance/employees/{employeeId}` | `attendance.self.read` (chính mình) hoặc `attendance.read` (trong scope) | Bảng công của nhân viên trong khoảng ngày, có phân trang |
| `POST /api/leave-requests` | `request.self.create` (chính mình) hoặc `request.manage` (trong scope) | Tạo đơn nghỉ `DRAFT` |
| `POST /api/leave-requests/{id}/submit` | `request.self.create` hoặc `request.manage` | Gửi đơn `DRAFT` → `PENDING` |
| `POST /api/leave-requests/{id}/cancel` | `request.self.cancel` hoặc `request.manage` | Hủy đơn `DRAFT`/`PENDING` |
| `POST /api/leave-requests/{id}/approve` | `request.approve` (trong scope) | Duyệt đơn `PENDING` và gắn vào bảng công |
| `POST /api/leave-requests/{id}/reject` | `request.approve` (trong scope) | Từ chối đơn `PENDING` |
| `GET /api/leave-requests/{id}` | `request.self.read` (chính mình) hoặc `request.read` (trong scope) | Chi tiết đơn nghỉ |
| `GET /api/leave-requests/employees/{employeeId}` | `request.self.read` (chính mình) hoặc `request.read` (trong scope) | Đơn nghỉ của một nhân viên, có phân trang |
| `GET /api/leave-requests/pending` | `request.approve` | Đơn `PENDING` trong phạm vi người duyệt |
| `GET /api/leave-entitlements/employees/{employeeId}` | Chính nhân viên đó, hoặc `request.read` (trong scope) | Xem số dư phép năm |
| `PUT /api/leave-entitlements/employees/{employeeId}/adjustment` | `request.manage` (trong scope) | Cộng hoặc trừ hạn mức |
| `PUT /api/leave-entitlements/employees/{employeeId}/carried-over` | `request.manage` (trong scope) | Nhập tay số phút chuyển từ năm trước |
| `GET /api/leave-entitlements/carry-over` | `request.manage`, phạm vi `COMPANY` | Xem trước kết quả chốt phép năm |
| `POST /api/leave-entitlements/carry-over` | `request.manage`, phạm vi `COMPANY` | Chốt phép năm |

Tất cả endpoint yêu cầu Bearer token, trừ `POST /api/attendance/punches`. Permission `*.self.*` chỉ cho thao tác trên hồ sơ nhân viên gắn với account đang đăng nhập; permission còn lại được kiểm tra thêm theo scope của role (phân công hiện hành của nhân viên). Phần kiểm tra chính mình/scope nằm ở service, nên request ngoài scope trả `403 FORBIDDEN`.

Từ khi kỳ lương chứa ngày đó ở `APPROVED`, `PAID` hoặc `LOCKED`, mọi thao tác sửa dữ liệu công và ngày lễ trả `409 PAYROLL_PERIOD_LOCKED`.

---

## GET `/api/company-holidays`

Danh sách ngày lễ, sắp theo ngày.

```http
GET /api/company-holidays?from=2026-01-01&to=2026-12-31
```

| Parameter | Type | Bắt buộc | Ý nghĩa |
|:----------|:-----|:--------:|:--------|
| `from` | date | ✅ | Ngày đầu, tính cả ngày này |
| `to` | date | ✅ | Ngày cuối, tính cả ngày này |

### Response `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "holidayDate": "2026-01-01",
      "name": "Tết Dương lịch",
      "createdAt": "2025-12-15T02:00:00Z"
    }
  ],
  "error": null
}
```

---

## POST `/api/company-holidays`

Khai báo một ngày lễ. Chỉ có ngày lễ do công ty khai báo; hệ thống không tự nhập lịch lễ quốc gia.

### Request

```json
{
  "date": "2026-01-01",
  "name": "Tết Dương lịch"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `date` | date | ✅ | Chưa có ngày lễ khác cùng ngày |
| `name` | string | ✅ | Không rỗng, tối đa 150 ký tự |

### Response `201 Created`

Trả `CompanyHolidayResponse` như phần tử của `GET /api/company-holidays`.

Ngày lễ thay đổi lịch làm việc, nên nếu ngày đó thuộc kỳ lương `CALCULATED` thì kỳ được đưa về `DRAFT` để tính lại.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu `date` hoặc `name` sai |
| 403 | `FORBIDDEN` | Không có `organization.manage` phạm vi `COMPANY` |
| 409 | `CONFLICT` | Đã có ngày lễ vào ngày này |
| 409 | `PAYROLL_PERIOD_LOCKED` | Ngày thuộc kỳ lương đã duyệt, đã trả hoặc đã khóa |

---

## DELETE `/api/company-holidays/{id}`

Xóa ngày lễ. Giống khi tạo, kỳ lương `CALCULATED` chứa ngày đó được đưa về `DRAFT`.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không có `organization.manage` phạm vi `COMPANY` |
| 404 | `RESOURCE_NOT_FOUND` | Ngày lễ không tồn tại |
| 409 | `PAYROLL_PERIOD_LOCKED` | Ngày thuộc kỳ lương đã duyệt, đã trả hoặc đã khóa |

---

## POST `/api/attendance/prepare`

Tạo bản ghi `MISSING_PUNCH` cho mọi ngày làm việc trong tháng mà nhân viên chưa có dữ liệu.

```http
POST /api/attendance/prepare?year=2026&month=1
```

| Parameter | Type | Bắt buộc | Ràng buộc |
|:----------|:-----|:--------:|:----------|
| `year` | int | ✅ | 2000–9999 |
| `month` | int | ✅ | 1–12 |

### Response `200 OK`

`data` là số bản ghi mới được tạo. Gọi lặp không tạo trùng.

```json
{
  "success": true,
  "data": 25,
  "error": null
}
```

API kiểm tra mọi nhân viên có thời gian làm việc trong tháng. Nếu thiếu phân công chính hoặc ca ở ngày làm việc nào, API trả `400 VALIDATION_ERROR` kèm mã nhân viên và ngày đó; sửa phân công rồi gọi lại.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Tham số sai hoặc nhân viên thiếu phân công/ca |
| 403 | `FORBIDDEN` | Không có `attendance.manage` phạm vi `COMPANY` |
| 409 | `PAYROLL_PERIOD_LOCKED` | Tháng thuộc kỳ lương đã duyệt, đã trả hoặc đã khóa |

---

## POST `/api/attendance/punches`

Ghi giờ vào và giờ ra của một ngày công bằng thời điểm cụ thể, như máy chấm công báo về. Hệ thống tự tính `lateMinutes`, `earlyLeaveMinutes`, `workedMinutes`, `payableMinutes` theo [Quy tắc tính công](#quy-tắc-tính-công) và đặt `status = PRESENT`.

> **Tạm thời:** endpoint này mở không cần token và không kiểm tra permission/scope, để tạo dữ liệu test nhanh bằng Postman. Phải xóa (hoặc đóng lại) trước khi phát hành.

- Nếu ngày đó đã có bản ghi `MISSING_PUNCH` (từ `prepare` hoặc từ đơn nghỉ nửa ngày), bản ghi đó được cập nhật.
- Nếu chưa có, bản ghi được tạo từ ca của phân công chính hiệu lực ngày đó, kể cả chủ nhật và ngày lễ. Ngày không thuộc lịch làm việc có `payableMinutes = 0`; giờ làm được duyệt qua tăng ca.

### Request

```json
{
  "employeeId": 2,
  "workDate": "2026-01-15",
  "checkInAt": "2026-01-15T01:20:00Z",
  "checkOutAt": "2026-01-15T10:00:00Z"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `employeeId` | long | ✅ | Nhân viên chưa bị xóa, đang làm việc vào `workDate` và có phân công chính ngày đó |
| `workDate` | date | ✅ | Ngày công; với ca qua đêm là ngày bắt đầu ca |
| `checkInAt` | instant | ✅ | Không ở tương lai; theo giờ `Asia/Ho_Chi_Minh` phải rơi vào đúng `workDate` |
| `checkOutAt` | instant | ✅ | Không ở tương lai; sau `checkInAt` và cách không quá 24 giờ |

Ví dụ trên là ca `08:00–17:00` nghỉ trưa 60 phút, ân hạn đi muộn 15 phút: vào lúc 08:20 giờ Việt Nam cho `lateMinutes = 5`, `workedMinutes = payableMinutes = 460`.

### Response `200 OK`

Trả `AttendanceRecordResponse` (xem [GET `/api/attendance/{id}`](#get-apiattendanceid)).

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Vi phạm ràng buộc ở bảng trên, nhân viên chưa vào làm/đã nghỉ việc, hoặc không có phân công chính ngày đó |
| 404 | `EMPLOYEE_NOT_FOUND` | Nhân viên không tồn tại |
| 409 | `CONFLICT` | Ngày đó đã có giờ vào/ra hoặc đã được đối soát/nghỉ cả ngày; hoặc giờ làm trùng đơn nghỉ đã duyệt |
| 409 | `PAYROLL_PERIOD_LOCKED` | Ngày thuộc kỳ lương đã duyệt, đã trả hoặc đã khóa |

---

## PUT `/api/attendance/{id}`

HR đối soát một bản ghi công, chủ yếu để xử lý `MISSING_PUNCH`. Nghỉ có lương phải đi qua quy trình duyệt đơn nghỉ, không nhập tay ở đây. Không dùng số phút tính lương để tự suy ra nhân viên đã làm việc.

### Request

Ví dụ ngày vắng không phép:

```json
{
  "workedMinutes": 0,
  "payableMinutes": 0,
  "lateMinutes": 0,
  "earlyLeaveMinutes": 0,
  "status": "UNAUTHORIZED_ABSENCE",
  "note": "Đã đối soát"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `workedMinutes` | int | ✅ | ≥ 0; phải > 0 khi `status = PRESENT`; bằng 0 khi `UNAUTHORIZED_ABSENCE` |
| `payableMinutes` | int | ✅ | ≥ 0, không vượt `scheduledMinutes`; bằng 0 khi `UNAUTHORIZED_ABSENCE` |
| `lateMinutes` | int | ✅ | ≥ 0 |
| `earlyLeaveMinutes` | int | ✅ | ≥ 0 |
| `status` | enum | ✅ | Khác `MISSING_PUNCH` |
| `note` | string | ✅ | Lý do điều chỉnh, không rỗng |

### Response `200 OK`

Trả `AttendanceRecordResponse` sau điều chỉnh.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Vi phạm ràng buộc ở bảng trên |
| 403 | `FORBIDDEN` | Không có `attendance.manage` hoặc nhân viên ngoài scope |
| 404 | `ATTENDANCE_NOT_FOUND` | Bản ghi công không tồn tại |
| 409 | `PAYROLL_PERIOD_LOCKED` | Ngày thuộc kỳ lương đã duyệt, đã trả hoặc đã khóa |

---

## POST `/api/attendance/{id}/overtime-approval`

Duyệt tăng ca cho một ngày đã chấm ra. Đặt `overtimeMinutes` bằng 0 để hủy duyệt.

### Request

```json
{
  "overtimeMinutes": 60,
  "overtimeMultiplier": 1.5,
  "taxExempt": false
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `overtimeMinutes` | int | ✅ | ≥ 0; không lấy từ phần công thường hoặc nghỉ có lương |
| `overtimeMultiplier` | decimal | ✅ | > 0; khi `overtimeMinutes > 0` tối thiểu 1,5 ngày thường, 2 vào Chủ nhật, 3 vào ngày lễ đã khai báo |
| `taxExempt` | boolean | | Mặc định `false` (chịu thuế). Chỉ chọn `true` khi HR đã xác nhận khoản tăng ca đủ điều kiện miễn thuế |

### Response `200 OK`

Trả `AttendanceRecordResponse` với `overtimeMinutes`, `overtimeMultiplier` và `overtimeTaxExempt` mới.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Ngày chưa chấm ra, số phút vượt phần làm ngoài công thường, hoặc hệ số dưới mức tối thiểu |
| 403 | `FORBIDDEN` | Không có `attendance.overtime.approve` hoặc nhân viên ngoài scope |
| 404 | `ATTENDANCE_NOT_FOUND` | Bản ghi công không tồn tại |

---

## GET `/api/attendance/{id}`

Lấy chi tiết một bản ghi công.

### Response `200 OK`

```json
{
  "success": true,
  "data": {
    "id": 501,
    "employeeId": 1,
    "workDate": "2026-01-15",
    "shiftId": 1,
    "scheduledStartAt": "2026-01-15T01:00:00Z",
    "scheduledEndAt": "2026-01-15T10:00:00Z",
    "scheduledMinutes": 480,
    "checkInAt": "2026-01-15T01:05:00Z",
    "checkOutAt": "2026-01-15T10:00:00Z",
    "workedMinutes": 475,
    "payableMinutes": 475,
    "lateMinutes": 0,
    "earlyLeaveMinutes": 0,
    "overtimeMinutes": 0,
    "overtimeMultiplier": 1,
    "overtimeTaxExempt": false,
    "status": "PRESENT"
  },
  "error": null
}
```

`status` là một trong `PRESENT`, `PAID_LEAVE`, `UNPAID_LEAVE`, `MATERNITY_LEAVE`, `SICK_LEAVE`, `UNAUTHORIZED_ABSENCE`, `HOLIDAY`, `MISSING_PUNCH`. Các số phút được giải thích ở [Quy tắc tính công](#quy-tắc-tính-công).

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không phải chính mình và không có `attendance.read` trong scope |
| 404 | `ATTENDANCE_NOT_FOUND` | Bản ghi công không tồn tại |

---

## GET `/api/attendance/employees/{employeeId}`

Bảng công của một nhân viên trong khoảng ngày.

```http
GET /api/attendance/employees/1?from=2026-01-01&to=2026-01-31&page=0&size=20
```

| Parameter | Type | Bắt buộc | Ý nghĩa |
|:----------|:-----|:--------:|:--------|
| `from` | date | ✅ | Ngày công đầu |
| `to` | date | ✅ | Ngày công cuối |
| `page`, `size`, `sort` | | | Phân trang; mặc định `size=20`, sắp theo `workDate` |

Response `200 OK` dùng `PagedResult<AttendanceRecordResponse>` với các field `content`, `number`, `totalElements`, `totalPages` và `size`.

---

## POST `/api/leave-requests`

Tạo đơn nghỉ ở trạng thái `DRAFT`.

### Request

```json
{
  "employeeId": 1,
  "leaveType": "ANNUAL",
  "salaryTreatment": null,
  "startAt": "2026-01-15T01:00:00Z",
  "endAt": "2026-01-15T05:00:00Z",
  "requestedMinutes": 240,
  "reason": "Việc gia đình",
  "attachmentUrl": null
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `employeeId` | long | ✅ | Chính mình với `request.self.create`, hoặc trong scope với `request.manage` |
| `leaveType` | enum | ✅ | `ANNUAL`, `SICK`, `MATERNITY`, `UNPAID` hoặc `OTHER` |
| `salaryTreatment` | enum | | `EMPLOYER_PAID`, `SOCIAL_INSURANCE` hoặc `UNPAID`; xem bên dưới |
| `startAt` | instant | ✅ | |
| `endAt` | instant | ✅ | Sau `startAt` |
| `requestedMinutes` | int | ✅ | > 0 |
| `reason` | string | ✅ | Không rỗng |
| `attachmentUrl` | string | | |

Cách trả lương của đơn **do server suy ra từ loại nghỉ**, không nhận từ client: `ANNUAL` → `EMPLOYER_PAID`, `SICK` và `MATERNITY` → `SOCIAL_INSURANCE`, `UNPAID` và `OTHER` → `UNPAID`. Nếu client gửi `salaryTreatment` khác giá trị suy ra, API trả `400` trừ khi người gọi có `request.manage`. Không có ràng buộc này thì nhân viên chỉ cần nộp đơn `OTHER` kèm `EMPLOYER_PAID` là được trả lương đủ mà không trừ ngày phép nào. HR vẫn ghi đè được cho các trường hợp như công ty trả lương mấy ngày ốm đầu tiên hoặc nghỉ việc riêng có lương theo Điều 115.

### Response `201 Created`

```json
{
  "success": true,
  "data": {
    "id": 31,
    "employeeId": 1,
    "leaveType": "ANNUAL",
    "salaryTreatment": "EMPLOYER_PAID",
    "startAt": "2026-01-15T01:00:00Z",
    "endAt": "2026-01-15T05:00:00Z",
    "requestedMinutes": 240,
    "reason": "Việc gia đình",
    "attachmentUrl": null,
    "status": "DRAFT",
    "submittedAt": null,
    "reviewedByAccountId": null,
    "reviewComment": null,
    "reviewedAt": null,
    "createdAt": "2026-01-10T03:00:00Z",
    "updatedAt": "2026-01-10T03:00:00Z"
  },
  "error": null
}
```

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Request sai, `endAt` không sau `startAt`, hoặc `salaryTreatment` khác giá trị suy ra mà không có `request.manage` |
| 403 | `FORBIDDEN` | Không phải chính mình và không có `request.manage` trong scope |
| 404 | `EMPLOYEE_NOT_FOUND` | Nhân viên không tồn tại hoặc đã bị xóa mềm |

---

## POST `/api/leave-requests/{id}/submit` và `/cancel`

- `submit` chuyển đơn `DRAFT` sang `PENDING` và ghi `submittedAt`.
- `cancel` chuyển đơn `DRAFT` hoặc `PENDING` sang `CANCELLED`.

Cả hai không có body và trả `LeaveRequestView`. Đơn ở trạng thái khác trả `409 REQUEST_ALREADY_PROCESSED`; đơn không tồn tại trả `404 REQUEST_NOT_FOUND`.

---

## POST `/api/leave-requests/{id}/approve` và `/reject`

Duyệt hoặc từ chối đơn `PENDING`. Body không bắt buộc:

```json
{
  "comment": "Đồng ý"
}
```

Khi duyệt, hệ thống:

1. Kiểm tra nhân viên chưa có đơn đã duyệt trùng thời gian.
2. Với `ANNUAL`, kiểm tra số dư phép năm (xem [Hạn mức phép năm](#hạn-mức-phép-năm)).
3. Gắn đơn và số phút nghỉ vào bảng công từng ngày. Đơn nghỉ có lương cộng vào phút được trả lương; đơn nghỉ không lương hoặc do bảo hiểm chi trả không cộng.

Nghỉ một phần ngày rồi chấm công phần còn lại không cần đối soát: đơn được duyệt trước hay sau khi chấm đều cho ra đủ công. Chỉ khi nhân viên không chấm công cho phần còn lại thì ngày đó mới ở `MISSING_PUNCH` chờ HR.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không có `request.approve` hoặc nhân viên ngoài scope |
| 404 | `REQUEST_NOT_FOUND` | Đơn không tồn tại |
| 409 | `REQUEST_ALREADY_PROCESSED` | Đơn không ở `PENDING` |
| 409 | `CONFLICT` | Trùng đơn đã duyệt, vượt số dư phép năm, ngày đã gắn đơn khác, ngày đang chấm dở, hoặc đơn trùng giờ đã làm |
| 409 | `PAYROLL_PERIOD_LOCKED` | Ngày nghỉ thuộc kỳ lương đã duyệt, đã trả hoặc đã khóa |

---

## GET `/api/leave-requests/...`

| Endpoint | Ý nghĩa |
|:---------|:--------|
| `GET /api/leave-requests/{id}` | Chi tiết đơn; `404 REQUEST_NOT_FOUND` nếu không tồn tại |
| `GET /api/leave-requests/employees/{employeeId}` | Đơn của một nhân viên, mới nhất trước; `404 EMPLOYEE_NOT_FOUND` nếu nhân viên không tồn tại |
| `GET /api/leave-requests/pending` | Đơn `PENDING` của các nhân viên nằm trong scope `request.approve` của người gọi |

Hai endpoint danh sách hỗ trợ `page`, `size` (mặc định 20) và trả `PagedResult<LeaveRequestView>`.

---

## GET `/api/leave-entitlements/employees/{employeeId}`

Xem số dư phép năm. Thiếu `year` thì lấy năm hiện tại theo `Asia/Ho_Chi_Minh`.

```http
GET /api/leave-entitlements/employees/1?year=2026
```

### Response `200 OK`

```json
{
  "success": true,
  "data": {
    "employeeId": 1,
    "year": 2026,
    "grantedMinutes": 6240,
    "carriedOverMinutes": 480,
    "carriedOverExpiresOn": "2026-03-31",
    "expiredCarriedOverMinutes": 0,
    "adjustmentMinutes": 0,
    "adjustmentReason": null,
    "standardDayMinutes": 480,
    "usedMinutes": 960,
    "pendingMinutes": 240,
    "remainingMinutes": 5040
  },
  "error": null
}
```

| Field | Ý nghĩa |
|:------|:--------|
| `grantedMinutes` | Tổng hạn mức, đã gồm phần chuyển từ năm trước và điều chỉnh |
| `carriedOverMinutes`, `carriedOverExpiresOn` | Phần chuyển từ năm trước và hạn dùng |
| `expiredCarriedOverMinutes` | Phần chuyển sang chưa dùng đã hết hạn |
| `adjustmentMinutes`, `adjustmentReason` | Điều chỉnh của HR |
| `usedMinutes`, `pendingMinutes` | Đơn `ANNUAL` đã duyệt và đang chờ |
| `remainingMinutes` | Số dư còn lại, đã trừ phần hết hạn |
| `standardDayMinutes` | Số phút một ngày phép, để client quy ra ngày |

---

## PUT `/api/leave-entitlements/employees/{employeeId}/adjustment`

Cộng hoặc trừ hạn mức của năm (`?year=`, mặc định năm hiện tại). Giá trị gửi lên thay thế điều chỉnh trước đó.

### Request

```json
{
  "adjustmentMinutes": 480,
  "reason": "Thưởng thêm 1 ngày phép"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `adjustmentMinutes` | int | ✅ | Có thể âm, nhưng hạn mức năm sau điều chỉnh không được âm |
| `reason` | string | ✅ | Không rỗng |

Response `200 OK` trả `BalanceView` mới.

---

## PUT `/api/leave-entitlements/employees/{employeeId}/carried-over`

Nhập tay số phút chuyển từ năm trước vào năm `?year=`. Hạn dùng lấy theo quy tắc của năm đó; nhập 0 thì bỏ hạn.

### Request

```json
{
  "carriedOverMinutes": 960,
  "reason": "Chuyển phép từ hệ thống cũ"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `carriedOverMinutes` | int | ✅ | ≥ 0 |
| `reason` | string | | |

Response `200 OK` trả `BalanceView` mới.

### Lỗi chung của hạn mức phép

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu lý do, điều chỉnh làm hạn mức âm, hoặc số phút chuyển âm |
| 403 | `FORBIDDEN` | Không có permission hoặc nhân viên ngoài scope |
| 404 | `EMPLOYEE_NOT_FOUND` | Nhân viên không tồn tại hoặc đã bị xóa mềm |

---

## GET `/api/leave-entitlements/carry-over`

Xem trước kết quả chốt phép năm.

```http
GET /api/leave-entitlements/carry-over?fromYear=2026
```

### Response `200 OK`

```json
{
  "success": true,
  "data": {
    "fromYear": 2026,
    "toYear": 2027,
    "maxCarryOverDays": 5,
    "expiresOn": "2027-03-31",
    "items": [
      {
        "employeeId": 1,
        "employeeCode": "GD0001",
        "fullName": "Nguyễn Văn An",
        "remainingMinutes": 1440,
        "standardDayMinutes": 480,
        "currentCarriedOverMinutes": 0,
        "carriedOverMinutes": 1440
      }
    ]
  },
  "error": null
}
```

| Field | Ý nghĩa |
|:------|:--------|
| `expiresOn` | Ngày cuối của tháng hết hạn trong năm sau |
| `items[].remainingMinutes` | Số dư năm `fromYear` tính tại 31/12; đơn còn `PENDING` được tính như đã dùng |
| `items[].currentCarriedOverMinutes` | Số phút đang ghi cho năm sau |
| `items[].carriedOverMinutes` | Số phút sẽ ghi: bằng số dư nhưng không quá `maxCarryOverDays` ngày |

Danh sách gồm nhân viên còn làm việc ngày 01/01 năm sau và vào làm trước 31/12 năm chốt.

---

## POST `/api/leave-entitlements/carry-over`

Chốt phép năm: tính lại như bản xem trước rồi ghi `carried_over_minutes` và `carried_over_expires_on` vào hạn mức năm sau, tạo hạn mức nếu chưa có. Response giống `GET`.

Gọi lại sẽ tính lại và ghi đè, nên nếu sửa đơn của năm cũ sau khi chốt thì chốt lại một lần nữa.

---

## Quy tắc tính công

- `scheduledMinutes` là số phút công chuẩn của ngày theo ca (đã trừ `breakMinutes`), trả kèm mỗi bản ghi công; `payableMinutes` khi điều chỉnh không được vượt giá trị này.
- Giờ nghỉ của ca được đặt đúng vị trí bằng `breakStartTime` (xem [WORK_SHIFT.md](WORK_SHIFT.md)) và chỉ bị trừ ở phần thời gian thực sự trùng giờ nghỉ.
- `workedMinutes` là khoảng chấm vào/ra trừ phần trùng giờ nghỉ, không âm.
- `payableMinutes` là phút công trong ca dự kiến (không tính giờ nghỉ) nằm giữa giờ vào và giờ ra, cộng số phút nghỉ có lương của đơn gắn với ngày đó, tối đa bằng số phút công chuẩn; tăng ca đã duyệt được tính riêng. Giờ chấm vào Chủ nhật hoặc ngày lễ không thành phút công thường, chỉ được trả khi duyệt tăng ca.
- `lateMinutes` là phút công từ đầu ca tới giờ vào, `earlyLeaveMinutes` là phút công từ giờ ra tới cuối ca; cả hai không tính giờ nghỉ và phần thời gian đã có đơn nghỉ được duyệt. `lateMinutes` còn trừ `graceLateMinutes`. Ví dụ ca 08:00–17:00 nghỉ 12:00–13:00, đơn nghỉ 08:00–12:00 (hoặc 08:00–13:00) được 240 phút nghỉ; vào lúc 13:00 không bị tính muộn, ra lúc 17:00 thì `payableMinutes` = 240 + 240 = 480.
- Kỳ lương dùng lịch thứ 2 đến thứ 7, trừ ngày lễ, và yêu cầu bản ghi công đã được xử lý cho từng ngày đó. Thiếu ngày hoặc còn `MISSING_PUNCH` thì không chuyển kỳ sang `CALCULATED`. Lương cơ bản áp dụng mức lương có hiệu lực theo từng ngày; số tiền mỗi ngày bằng lương tháng nhân phút được trả lương, chia cho số ngày làm việc chuẩn của cả tháng và số phút chuẩn của ca ngày đó. Phụ cấp chức vụ và thâm niên cũng chia theo phút được trả lương: nghỉ có lương được tính, nghỉ không lương không được tính. Cách này chia tỷ lệ khi vào làm giữa tháng.
- Từ `APPROVED`, mọi đường sửa dữ liệu công bị chặn. Nếu công thay đổi sau khi tính lương nhưng trước khi duyệt, phải tính lại trước khi duyệt.

---

## Hạn mức phép năm

Mỗi nhân viên có một hạn mức phép năm cho mỗi năm và `POST /api/leave-requests/{id}/approve` sẽ trả `409` nếu đơn vượt số dư còn lại.

Hạn mức tính theo quy tắc trong `leave_entitlement_rules`, bộ nạp sẵn từ 01/01/2026 là **12 ngày cơ bản, cứ đủ 5 năm làm việc cộng thêm 1 ngày** (Bộ luật Lao động 2019, Điều 113 khoản 1 và Điều 114):

- Thâm niên đếm từ `employees.seniority_start_date`, thiếu thì lấy `hire_date`, và tính theo số năm **đã tròn tại ngày 01/01 của năm phép**. Người đủ 5 năm vào tháng 6 thì được cộng ngày từ năm kế tiếp.
- Vào làm giữa năm thì chia theo số tháng còn lại của năm. Vào làm tháng 10 được 3/12 của hạn mức.
- Số ngày quy ra phút theo `standard_work_minutes` của ca trong phân công chính đang mở; không có ca thì dùng 480 phút. Giá trị này được snapshot vào `employee_leave_entitlements.standard_day_minutes` nên đổi ca sau đó không viết lại năm đã cấp.

Chỉ `ANNUAL` trừ vào số dư. `SICK`, `MATERNITY`, `UNPAID` và `OTHER` đi theo quy trình riêng và không ảnh hưởng hạn mức.

Khi kiểm tra, các đơn `PENDING` khác được tính như đã tiêu. Nếu không làm vậy thì ba đơn cùng chờ duyệt sẽ cùng qua được kiểm tra rồi mới vượt hạn mức sau khi duyệt hết.

Bảng hạn mức **không lưu số phút đã dùng**; số đã dùng và đang chờ được tính trực tiếp từ `leave_requests`, nên không thể lệch với đơn thật.

### Chốt phép năm

Luật cho phép thỏa thuận chuyển phép chưa nghỉ sang năm sau; mức chuyển là chính sách công ty, lưu trong `leave_entitlement_rules`: `max_carry_over_days` (bộ nạp sẵn: 5 ngày) và `carry_over_expiry_month` (bộ nạp sẵn: tháng 3). Quy tắc được lấy theo ngày 01/01 của năm nhận phép.

Hết hạn: đơn `ANNUAL` bắt đầu đến hết `carriedOverExpiresOn` dùng phần chuyển sang trước. Sau ngày đó phần chuyển sang chưa dùng mất hiệu lực và được báo trong `expiredCarriedOverMinutes`; `remainingMinutes` đã trừ phần này. Kiểm tra số dư khi duyệt đơn dùng ngày bắt đầu của đơn, nên đơn tháng 4 không được dùng phần đã hết hạn dù được duyệt sớm.

---

## Hoàn thiện dữ liệu trước khi tính lương

1. Tạo phân công chính và chọn ca có hiệu lực từ **ngày làm việc đầu tiên** của nhân viên. `POST /api/attendance/prepare` báo mã nhân viên và ngày nếu thiếu phân công hoặc ca; sau khi sửa phân công, gọi lại để tạo các bản ghi `MISSING_PUNCH`.
2. HR đối soát từng `MISSING_PUNCH`: ghi nhận `PRESENT` với phút thực làm và phút tính lương, hoặc `UNAUTHORIZED_ABSENCE` với cả hai bằng 0; cần lý do điều chỉnh. Nghỉ có lương cần đi qua quy trình duyệt đơn nghỉ.
3. Người có `compensation.manage` nhập mức lương tháng qua `POST /api/compensation/employees/{employeeId}/salary-history`, ví dụ `{"baseSalary":12000000,"effectiveFrom":"2026-01-01","reason":"Mức lương ban đầu"}`. Xem lịch sử bằng `GET` cùng đường dẫn với quyền `compensation.read`. Ngày hiệu lực phải phủ tất cả ngày làm việc cần tính lương. Sửa mức lương ảnh hưởng kỳ `CALCULATED` sẽ đưa kỳ về `DRAFT` và xóa phiếu cũ; kỳ đã duyệt, trả hoặc khóa thì bị chặn.
   Vai trò `HR_MANAGER` có cả hai quyền này. Từ hồ sơ nhân viên, HR chọn **Nhập và xem lương** để mở màn hình lương với nhân viên và ngày vào làm được điền sẵn.
4. HR nhập hồ sơ bảo hiểm/thuế và người phụ thuộc theo hướng dẫn trong [PAYROLL.md](PAYROLL.md). Nếu thiếu hồ sơ trong kỳ, hệ thống trả lỗi theo nhân viên thay vì giả định không tham gia bảo hiểm.
5. Tính kỳ lương chỉ thành công khi **mọi nhân viên được tuyển dụng trong kỳ** có phân công, ca, công đã xử lý, lịch sử lương và hồ sơ khấu trừ. API trả lỗi kèm mã nhân viên và dữ liệu thiếu; kỳ vẫn ở trạng thái trước đó để sửa rồi tính lại.

Endpoint kỳ lương: `POST /api/payroll/periods`, `GET /api/payroll/periods`, `GET /api/payroll/periods/{id}`, `POST /api/payroll/periods/{id}/calculate|approve|mark-paid|lock|cancel`; xem phiếu qua `GET /api/payroll/employees/{employeeId}/payslips` hoặc `GET /api/payroll/payslips/{id}`. Nhân viên chỉ xem phiếu từ kỳ đã duyệt trở đi. Chi tiết trong [PAYROLL.md](PAYROLL.md).

---

## Ghi chú

- Giờ vào/ra sẽ đến từ máy chấm công qua `POST /api/attendance/punches`; web không có thao tác tự chấm công.
- Actor duyệt đơn, điều chỉnh công và duyệt tăng ca luôn lấy từ JWT/Security Context.
- Cổng HRM có `/attendance`, `/leave-requests`, `/payslips`; tất cả yêu cầu đi qua BFF allowlist và API kiểm tra quyền cùng phạm vi truy cập.
- Giới hạn hiện tại: mỗi ca có một khoảng nghỉ. Mô hình chỉ lưu một cặp chấm vào/ra và một đơn nghỉ cho mỗi nhân viên mỗi ngày, nên không có hai đơn khác loại trong cùng một ngày (ví dụ sáng nghỉ phép năm, chiều nghỉ ốm). Chỉ có ngày lễ do công ty khai báo, không tự nhập lịch lễ quốc gia.
