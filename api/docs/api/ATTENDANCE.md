# API Reference — Attendance

Chấm công, ngày lễ công ty, đơn nghỉ và hạn mức phép năm. Đây là dữ liệu đầu vào của kỳ lương; cách tính `grossPay`, bảo hiểm, thuế TNCN và `netPay` nằm trong [PAYROLL.md](PAYROLL.md).

Lịch làm việc là **thứ 2 đến thứ 7**, trừ các ngày trong `company_holidays`. Ca được lấy từ phân công chính có hiệu lực vào từng ngày (xem [WORK_SHIFT.md](WORK_SHIFT.md)). `workDate` của ca qua đêm là ngày bắt đầu ca. Thời điểm lưu bằng UTC; ngày công được xác định theo `Asia/Ho_Chi_Minh`.

Quy trình một tháng:

1. Người có `organization.manage` khai báo ngày lễ qua `POST /api/company-holidays`.
2. Bảng công tháng được chuẩn bị tự động (xem [Tự động chuẩn bị](#tự-động-chuẩn-bị)); nhân viên mới vào hoặc vừa được bổ sung phân công có bảng công chậm nhất vào sáng hôm sau. HR chỉ cần gọi `POST /api/attendance/prepare` khi cần ngay trong ngày hoặc để chuẩn bị tháng khác.
3. Giờ vào/ra được ghi qua `POST /api/attendance/punches` với thời điểm cụ thể. Sau này máy chấm công của công ty sẽ gửi dữ liệu vào đây; phần tích hợp máy **chưa triển khai**, hiện HR nhập qua API này (cũng là cách tạo dữ liệu test cho ngày đã qua). Nhân viên chỉ xem lịch sử chấm công trên web.
4. Nhân viên nộp đơn nghỉ qua `/api/leave-requests`; [người duyệt](#người-duyệt) duyệt đơn và hệ thống tự gắn đơn vào bảng công.
5. HR đối soát các ngày `MISSING_PUNCH`: nhân viên có đi làm thì nhập giờ vào/ra thực tế qua `POST /api/attendance/punches`, vắng không báo thì ghi `UNAUTHORIZED_ABSENCE` qua `PUT /api/attendance/{id}`. Người duyệt của nhân viên quyết định tăng ca; HR kiểm tra và tổng hợp, không duyệt.
6. Kế toán tính kỳ lương. Xem [Hoàn thiện dữ liệu trước khi tính lương](#hoàn-thiện-dữ-liệu-trước-khi-tính-lương).

---

## Endpoint access

| Endpoint | Permission | Mô tả |
|:---------|:-----------|:------|
| `GET /api/company-holidays` | `holiday.read` (không kiểm tra scope) | Danh sách ngày lễ trong khoảng ngày |
| `POST /api/company-holidays` | `organization.manage`, phạm vi `COMPANY` | Khai báo ngày lễ |
| `DELETE /api/company-holidays/{id}` | `organization.manage`, phạm vi `COMPANY` | Xóa ngày lễ |
| `POST /api/attendance/prepare` | `attendance.manage`, phạm vi `COMPANY` | Tạo bản ghi `MISSING_PUNCH` cho các ngày làm việc của tháng |
| `POST /api/attendance/punches` | `attendance.manage` (trong scope) | Ghi hoặc sửa giờ vào/ra của một ngày công |
| `PUT /api/attendance/{id}` | `attendance.manage` | Điều chỉnh / đối soát bản ghi công |
| `POST /api/attendance/{id}/overtime-approval` | [Người duyệt](#người-duyệt) của nhân viên | Quyết định tăng ca của một ngày |
| `GET /api/attendance/overtime/pending` | Đăng nhập | Các ngày có giờ làm vượt công thường đang chờ người gọi quyết định |
| `GET /api/attendance/{id}` | `attendance.self.read` (chính mình) hoặc `attendance.read` (trong scope) | Chi tiết một bản ghi công |
| `GET /api/attendance/employees/{employeeId}` | `attendance.self.read` (chính mình) hoặc `attendance.read` (trong scope) | Bảng công của nhân viên trong khoảng ngày, có phân trang |
| `GET /api/attendance/timesheet` | `attendance.read` (trong scope) | Bảng công tháng của mọi nhân viên trong scope |
| `POST /api/leave-requests` | `request.self.create` (chính mình) | Tạo đơn nghỉ `DRAFT` |
| `POST /api/leave-requests/{id}/submit` | `request.self.create` (chủ đơn) | Gửi đơn `DRAFT` → `PENDING` |
| `POST /api/leave-requests/{id}/cancel` | `request.self.cancel` (chủ đơn) | Hủy đơn `DRAFT`/`PENDING` |
| `POST /api/leave-requests/{id}/approve` | [Người duyệt](#người-duyệt) đã chốt của đơn | Duyệt đơn `PENDING` và gắn vào bảng công |
| `POST /api/leave-requests/{id}/reject` | [Người duyệt](#người-duyệt) đã chốt của đơn | Từ chối đơn `PENDING` |
| `GET /api/leave-requests/{id}` | `request.self.read` (chính mình), người duyệt của đơn, hoặc `request.read` (trong scope) | Chi tiết đơn nghỉ |
| `GET /api/leave-requests` | `request.read` (trong scope) | Đơn nghỉ của mọi nhân viên trong scope, có lọc và phân trang |
| `GET /api/leave-requests/employees/{employeeId}` | `request.self.read` (chính mình) hoặc `request.read` (trong scope) | Đơn nghỉ của một nhân viên, có phân trang |
| `GET /api/leave-requests/pending` | Đăng nhập | Đơn `PENDING` đang chờ người gọi duyệt |
| `GET /api/leave-entitlements/employees/{employeeId}` | Chính nhân viên đó, hoặc `request.read` (trong scope) | Xem số dư phép năm |

Tất cả endpoint yêu cầu Bearer token. Permission `*.self.*` chỉ cho thao tác trên hồ sơ nhân viên gắn với account đang đăng nhập; permission còn lại được kiểm tra thêm theo scope của role (phân công hiện hành của nhân viên). Phần kiểm tra chính mình/scope nằm ở service, nên request ngoài scope trả `403 FORBIDDEN`.

Từ khi kỳ lương chứa ngày đó ở `APPROVED`, `PAID` hoặc `LOCKED`, mọi thao tác sửa dữ liệu công và ngày lễ trả `409 PAYROLL_PERIOD_LOCKED`.

### Người duyệt

Đơn nghỉ và tăng ca không duyệt theo permission mà theo quan hệ quản lý. Người duyệt của một nhân viên là người đầu tiên đủ điều kiện trong chuỗi:

1. **Quản lý trực tiếp**: `managerEmployeeId` của phân công chính hiện hành.
2. **Giám đốc**: người đang giữ role `DIRECTOR`.
3. **Chủ sở hữu**: người đang giữ role `COMPANY_OWNER`.

Một người bị bỏ qua khi đó là chính nhân viên, đã nghỉ việc/bị xóa, hoặc không có tài khoản `ACTIVE`. Vì vậy không ai duyệt được đơn hoặc tăng ca của chính mình. Chủ sở hữu ở đỉnh sơ đồ nên không có người duyệt.

- **Đơn nghỉ**: người duyệt được chốt khi gửi đơn (`approverEmployeeId`). Đơn của người giữ `DIRECTOR` hoặc `COMPANY_OWNER` không cần duyệt: khi gửi, đơn chuyển thẳng sang `APPROVED` với ghi chú `Tự ghi nhận…` và được gắn vào bảng công như đơn đã duyệt.
- **Tăng ca**: người duyệt được xác định lúc quyết định. Tăng ca của Giám đốc do Chủ sở hữu quyết định; Chủ sở hữu tự xác nhận tăng ca của mình.
- **HR** (`request.read`, `request.manage`, `attendance.read`, `attendance.manage`) xem, đối soát và tổng hợp đơn, nhưng không tạo/gửi/hủy đơn thay nhân viên hoặc duyệt thay người được chốt.

---

## GET `/api/company-holidays`

Danh sách ngày lễ, sắp theo ngày. Cần `holiday.read`; mọi nhân viên có quyền này qua role `EMPLOYEE` để thấy ngày lễ trên lịch chấm công. Lịch lễ chung cho cả công ty nên không kiểm tra scope.

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

Khai báo một ngày lễ. Lịch nghỉ lễ, Tết hưởng nguyên lương năm 2025 và 2026 (Điều 112 Bộ luật Lao động 2019, 11 ngày mỗi năm) được nạp sẵn bởi `CompanyHolidaySeeder` khi `company.seed.enabled=true`; Tết Âm lịch và ngày liền kề Quốc khánh lấy theo lịch của khối nhà nước, lễ rơi vào Chủ nhật được nghỉ bù ngày làm việc kế tiếp. Năm đã có ngày lễ nào thì seeder bỏ qua cả năm, nên HR sửa hoặc xóa không bị ghi đè; các năm sau HR tự khai báo.

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

### Tự động chuẩn bị

Backend tự làm việc tương tự cho **tháng hiện tại** (giờ `Asia/Ho_Chi_Minh`) lúc khởi động và lúc 00:05 **mỗi đêm**, nên nhân viên mới vào hoặc vừa được bổ sung phân công giữa tháng có bảng công vào sáng hôm sau. Khác với gọi API:

- Mỗi nhân viên chạy trong transaction riêng; nhân viên thiếu phân công hoặc ca bị bỏ qua và ghi log `WARN` (kèm mã nhân viên và ngày), các nhân viên khác vẫn được tạo.
- Nếu tháng thuộc kỳ lương đã duyệt, đã trả hoặc đã khóa thì bỏ qua cả tháng.
- Tắt bằng `attendance.auto-prepare.enabled=false` (biến môi trường `ATTENDANCE_AUTO_PREPARE_ENABLED`).

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Tham số sai hoặc nhân viên thiếu phân công/ca |
| 403 | `FORBIDDEN` | Không có `attendance.manage` phạm vi `COMPANY` |
| 409 | `PAYROLL_PERIOD_LOCKED` | Tháng thuộc kỳ lương đã duyệt, đã trả hoặc đã khóa |

---

## POST `/api/attendance/punches`

Ghi giờ vào và giờ ra của một ngày công bằng thời điểm cụ thể, như máy chấm công báo về. Hệ thống tự tính `lateMinutes`, `earlyLeaveMinutes`, `workedMinutes`, `payableMinutes` theo [Quy tắc tính công](#quy-tắc-tính-công) và đặt `status = PRESENT`.

Đây cũng là cách HR đối soát một ngày nhân viên có đi làm: quên chấm công, máy chấm sai giờ, đi công tác, hoặc đi làm Chủ nhật/ngày lễ. HR nhập giờ thực tế thay vì tự tính số phút.

- Ngày đã có bản ghi thì bản ghi đó được ghi đè, kể cả khi đã có giờ vào/ra hoặc đã được đối soát (ví dụ đã ghi `UNAUTHORIZED_ABSENCE` rồi dữ liệu máy chấm mới về). Riêng ngày nghỉ cả ngày theo đơn đã duyệt thì bị từ chối.
- Ngày chưa có bản ghi thì được tạo từ ca của phân công chính hiệu lực ngày đó, kể cả Chủ nhật và ngày lễ. Ngày không thuộc lịch làm việc có `payableMinutes = 0`; giờ làm được trả qua duyệt tăng ca.
- Tăng ca đã duyệt của ngày đó bị xóa (về 0 phút, hệ số 1) vì đã duyệt theo giờ cũ; người duyệt phải duyệt lại.
- Người gọi được lưu vào người cập nhật bản ghi, `note` lưu lý do.

### Request

```json
{
  "employeeId": 2,
  "workDate": "2026-01-15",
  "checkInAt": "2026-01-15T01:20:00Z",
  "checkOutAt": "2026-01-15T10:00:00Z",
  "note": "Quên chấm công, trưởng phòng xác nhận"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `employeeId` | long | ✅ | Nhân viên chưa bị xóa, đang làm việc vào `workDate` và có phân công chính ngày đó |
| `workDate` | date | ✅ | Ngày công; với ca qua đêm là ngày bắt đầu ca |
| `checkInAt` | instant | ✅ | Không ở tương lai; theo giờ `Asia/Ho_Chi_Minh` phải rơi vào đúng `workDate` |
| `checkOutAt` | instant | ✅ | Không ở tương lai; sau `checkInAt` và cách không quá 24 giờ |
| `note` | string | | Lý do nhập hoặc sửa, tối đa 1000 ký tự; để trống khi dữ liệu đến từ máy chấm |

Ví dụ trên là ca `08:00–17:00` nghỉ trưa 60 phút, ân hạn đi muộn 15 phút: vào lúc 08:20 giờ Việt Nam cho `lateMinutes = 5`, `workedMinutes = payableMinutes = 460`.

### Response `200 OK`

Trả `AttendanceRecordResponse` (xem [GET `/api/attendance/{id}`](#get-apiattendanceid)).

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Vi phạm ràng buộc ở bảng trên, nhân viên chưa vào làm/đã nghỉ việc, hoặc không có phân công chính ngày đó |
| 403 | `FORBIDDEN` | Không có `attendance.manage` hoặc nhân viên ngoài scope |
| 404 | `EMPLOYEE_NOT_FOUND` | Nhân viên không tồn tại |
| 409 | `CONFLICT` | Ngày nghỉ cả ngày theo đơn đã duyệt, hoặc giờ làm trùng đơn nghỉ nửa ngày đã duyệt |
| 409 | `PAYROLL_PERIOD_LOCKED` | Ngày thuộc kỳ lương đã duyệt, đã trả hoặc đã khóa |

---

## PUT `/api/attendance/{id}`

HR ghi đè trực tiếp số phút và trạng thái của một bản ghi công. Web chỉ dùng API này để ghi **vắng không phép**; ngày nhân viên có đi làm thì nên nhập giờ vào/ra qua [`POST /api/attendance/punches`](#post-apiattendancepunches) để hệ thống tự tính phút và duyệt được tăng ca. Nghỉ có lương phải đi qua quy trình duyệt đơn nghỉ, không nhập tay ở đây.

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

[Người duyệt](#người-duyệt) của nhân viên quyết định tăng ca cho một ngày đã chấm ra. `overtimeMinutes` bằng 0 nghĩa là phần giờ dư không tính tăng ca. Quyết định nào cũng ghi người và thời điểm quyết định, nên ngày đó rời khỏi danh sách chờ; sửa giờ vào/ra sau đó xóa quyết định và đưa ngày về danh sách chờ.

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
| 403 | `FORBIDDEN` | Người gọi không phải người duyệt của nhân viên |
| 404 | `ATTENDANCE_NOT_FOUND` | Bản ghi công không tồn tại |

---

## GET `/api/attendance/overtime/pending`

Các ngày đã chấm ra, có giờ làm vượt phần công được trả (`workedMinutes > payableMinutes`) và chưa có quyết định tăng ca, của những nhân viên mà người gọi là [người duyệt](#người-duyệt). Chủ sở hữu thấy cả ngày của chính mình. Ngày mới nhất trước.

```json
{
  "success": true,
  "data": [
    {
      "record": { "id": 812, "employeeId": 25, "workDate": "2026-03-03", "workedMinutes": 600, "payableMinutes": 480, "overtimeMinutes": 0 },
      "employeeCode": "NV0025",
      "employeeName": "Nguyễn Văn A"
    }
  ],
  "error": null
}
```

`record` là `AttendanceRecordResponse` đầy đủ (ví dụ rút gọn).

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

## GET `/api/attendance/timesheet`

Bảng công tổng hợp một tháng: mỗi nhân viên trong scope `attendance.read` có làm việc trong tháng (vào làm
trước cuối tháng, chưa nghỉ việc trước đầu tháng) là một dòng, xếp theo mã nhân viên, kèm mọi bản ghi công
của tháng đó. Web dựng lưới nhân viên × ngày và tính tổng công, muộn/sớm, nghỉ, tăng ca từ các bản ghi này.

| Query | Bắt buộc | Ý nghĩa |
|:------|:--------:|:--------|
| `year`, `month` | ✅ | Tháng cần xem (`year` 2000–9999, `month` 1–12) |
| `keyword` | | Một phần mã nhân viên, họ tên hoặc email công việc |
| `organizationUnitId` | | Nhân viên có phân công chính hiện tại thuộc đơn vị này |

Response `200 OK`:

```json
{
  "success": true,
  "data": [
    {
      "employeeId": 3,
      "employeeCode": "NV0003",
      "employeeName": "Lê Văn Cường",
      "records": [ { "id": 120, "workDate": "2026-10-01", "status": "PRESENT", "payableMinutes": 480, "...": "..." } ]
    }
  ],
  "error": null
}
```

Mỗi phần tử của `records` có cùng dạng với `GET /api/attendance/{id}`. Ngày chưa được chuẩn bị bảng công
thì không có bản ghi.

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
  "reason": "Việc gia đình",
  "attachmentUrl": null
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `employeeId` | long | ✅ | Phải là ID nhân viên gắn với account đang đăng nhập; mã nhân viên không nhập trong form |
| `leaveType` | enum | ✅ | `ANNUAL`, `SICK`, `MATERNITY`, `UNPAID` hoặc `OTHER` |
| `salaryTreatment` | enum | | `EMPLOYER_PAID`, `SOCIAL_INSURANCE` hoặc `UNPAID`; xem bên dưới |
| `startAt` | instant | ✅ | |
| `endAt` | instant | ✅ | Sau `startAt` |
| `reason` | string | ✅ | Không rỗng |
| `attachmentUrl` | string | | |

Cách trả lương của đơn **do server suy ra từ loại nghỉ**, không nhận từ client: `ANNUAL` → `EMPLOYER_PAID`, `SICK` và `MATERNITY` → `SOCIAL_INSURANCE`, `UNPAID` và `OTHER` → `UNPAID`. Nếu client gửi `salaryTreatment` khác giá trị suy ra, API trả `400` trừ khi người gọi có `request.manage`. Không có ràng buộc này thì nhân viên chỉ cần nộp đơn `OTHER` kèm `EMPLOYER_PAID` là được trả lương đủ mà không trừ ngày phép nào. HR vẫn ghi đè được cho các trường hợp như công ty trả lương mấy ngày ốm đầu tiên hoặc nghỉ việc riêng có lương theo Điều 115.

`requestedMinutes` của đơn **do server tính**, client không gửi: tổng phút công theo ca của phân công chính trên mỗi ngày làm việc nằm trong `startAt`–`endAt`, không gồm giờ nghỉ giữa ca, Chủ nhật và ngày lễ công ty. Ví dụ ca 08:00–17:00 nghỉ trưa 12:00–13:00, nghỉ từ 13:00 thứ Hai đến 12:00 thứ Tư là 240 + 480 + 240 = 960 phút. Nhờ vậy khoảng thời gian và số phút trừ phép không thể lệch nhau. Với `ANNUAL`, đơn vượt [số dư phép năm](#hạn-mức-phép-năm) bị từ chối ngay khi tạo.

### Response `201 Created`

```json
{
  "success": true,
  "data": {
    "id": 31,
    "employeeId": 1,
    "employeeCode": "NV0001",
    "employeeName": "Nguyễn Văn An",
    "leaveType": "ANNUAL",
    "salaryTreatment": "EMPLOYER_PAID",
    "startAt": "2026-01-15T01:00:00Z",
    "endAt": "2026-01-15T05:00:00Z",
    "requestedMinutes": 240,
    "reason": "Việc gia đình",
    "attachmentUrl": null,
    "status": "DRAFT",
    "submittedAt": null,
    "approverEmployeeId": null,
    "approverEmployeeName": null,
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
| 400 | `LEAVE_OUTSIDE_WORK_SCHEDULE` | Khoảng thời gian không có phút công nào theo ca (ví dụ chỉ gồm Chủ nhật, ngày lễ hoặc giờ ngoài ca) |
| 400 | `LEAVE_OUTSIDE_EMPLOYMENT` | Khoảng nghỉ có ngày trước ngày vào làm hoặc sau ngày làm việc cuối (kể cả ngày cuối đã hẹn nhưng chưa tới) |
| 403 | `FORBIDDEN` | `employeeId` không thuộc account đang đăng nhập |
| 404 | `EMPLOYEE_NOT_FOUND` | Nhân viên không tồn tại hoặc đã bị xóa mềm |
| 409 | `INSUFFICIENT_LEAVE_BALANCE` | Đơn `ANNUAL` vượt số dư phép năm còn lại |

---

## POST `/api/leave-requests/{id}/submit` và `/cancel`

- `submit` ghi `submittedAt`, chốt [người duyệt](#người-duyệt) vào `approverEmployeeId` và chuyển đơn `DRAFT` sang `PENDING`. Đơn của Giám đốc hoặc Chủ sở hữu chuyển thẳng sang `APPROVED` (tự ghi nhận, kiểm tra như khi duyệt). Không tìm được người duyệt trả `409 APPROVER_NOT_FOUND`. Đơn `ANNUAL` được kiểm tra lại số dư phép năm, vì các đơn khác có thể đã gửi hoặc được duyệt sau khi lưu nháp; vượt số dư trả `409 INSUFFICIENT_LEAVE_BALANCE`.
- `cancel` chuyển đơn `DRAFT` hoặc `PENDING` sang `CANCELLED`.

Cả hai không có body và trả `LeaveRequestView`. Chỉ chủ đơn được gửi hoặc hủy; quyền `request.manage`
không cho phép làm thay. Đơn ở trạng thái khác trả `409 REQUEST_ALREADY_PROCESSED`; đơn không tồn tại trả `404 REQUEST_NOT_FOUND`.

---

## POST `/api/leave-requests/{id}/approve` và `/reject`

Chỉ người duyệt đã chốt của đơn (`approverEmployeeId`) duyệt hoặc từ chối đơn `PENDING`. Body không bắt buộc:

```json
{
  "comment": "Đồng ý"
}
```

Khi duyệt, hệ thống:

1. Kiểm tra nhân viên chưa có đơn đã duyệt trùng thời gian.
2. Kiểm tra khoảng nghỉ vẫn nằm trong thời gian làm việc: ngày làm việc cuối có thể được hẹn sau khi tạo đơn.
3. Với `ANNUAL`, kiểm tra số dư phép năm (xem [Hạn mức phép năm](#hạn-mức-phép-năm)).
4. Gắn đơn và số phút nghỉ vào bảng công từng ngày. Đơn nghỉ có lương cộng vào phút được trả lương; đơn nghỉ không lương hoặc do bảo hiểm chi trả không cộng.

Nghỉ một phần ngày rồi chấm công phần còn lại không cần đối soát: đơn được duyệt trước hay sau khi chấm đều cho ra đủ công. Chỉ khi nhân viên không chấm công cho phần còn lại thì ngày đó mới ở `MISSING_PUNCH` chờ HR.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Người gọi không phải người duyệt của đơn |
| 400 | `LEAVE_OUTSIDE_EMPLOYMENT` | Khoảng nghỉ có ngày sau ngày làm việc cuối |
| 404 | `REQUEST_NOT_FOUND` | Đơn không tồn tại |
| 409 | `REQUEST_ALREADY_PROCESSED` | Đơn không ở `PENDING` |
| 409 | `CONFLICT` | Trùng đơn đã duyệt, ngày đã gắn đơn khác, ngày đang chấm dở, hoặc đơn trùng giờ đã làm |
| 409 | `INSUFFICIENT_LEAVE_BALANCE` | Đơn `ANNUAL` vượt số dư phép năm còn lại |
| 409 | `PAYROLL_PERIOD_LOCKED` | Ngày nghỉ thuộc kỳ lương đã duyệt, đã trả hoặc đã khóa |

---

## GET `/api/leave-requests/...`

| Endpoint | Ý nghĩa |
|:---------|:--------|
| `GET /api/leave-requests` | Đơn của mọi nhân viên trong scope `request.read`, mặc định xếp `startAt` giảm dần |
| `GET /api/leave-requests/{id}` | Chi tiết đơn; `404 REQUEST_NOT_FOUND` nếu không tồn tại |
| `GET /api/leave-requests/employees/{employeeId}` | Đơn của một nhân viên, mới nhất trước; `404 EMPLOYEE_NOT_FOUND` nếu nhân viên không tồn tại |
| `GET /api/leave-requests/pending` | Đơn `PENDING` có `approverEmployeeId` là nhân viên của người gọi |

Các endpoint danh sách hỗ trợ `page`, `size` (mặc định 20) và trả `PagedResult<LeaveRequestView>`.

Bộ lọc của `GET /api/leave-requests` (đều tùy chọn):

| Query | Ý nghĩa |
|:------|:--------|
| `keyword` | Một phần mã nhân viên, họ tên hoặc email công việc |
| `organizationUnitId` | Nhân viên có phân công chính hiện tại thuộc đơn vị này |
| `status`, `leaveType` | Trạng thái và loại đơn |
| `from`, `to` | Ngày (`YYYY-MM-DD`, giờ Việt Nam); giữ các đơn có giao với khoảng này |
| `sort` | `startAt`, `createdAt`, `submittedAt` hoặc `status`; trường khác trả `400` |

Đơn `DRAFT` là của riêng người tạo nên không có trong danh sách này.

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
    "grantedMinutes": 5760,
    "standardDayMinutes": 480,
    "usedMinutes": 960,
    "pendingMinutes": 240,
    "remainingMinutes": 4560
  },
  "error": null
}
```

| Field | Ý nghĩa |
|:------|:--------|
| `grantedMinutes` | Hạn mức của năm, tính theo [Hạn mức phép năm](#hạn-mức-phép-năm) |
| `usedMinutes`, `pendingMinutes` | Đơn `ANNUAL` đã duyệt và đang chờ |
| `remainingMinutes` | `grantedMinutes − usedMinutes − pendingMinutes` |
| `standardDayMinutes` | Số phút một ngày phép, để client quy ra ngày |

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không phải chính nhân viên đó, và không có `request.read` hoặc nhân viên ngoài scope |
| 404 | `EMPLOYEE_NOT_FOUND` | Nhân viên không tồn tại hoặc đã bị xóa mềm |

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

Mỗi nhân viên được **12 ngày phép năm** (Bộ luật Lao động 2019, Điều 113). Đơn vượt số dư còn lại bị từ chối với `409 INSUFFICIENT_LEAVE_BALANCE` khi tạo, khi gửi và khi duyệt. Chỉ có hai quy tắc:

- Làm cả năm: 12 ngày.
- Vào làm hoặc nghỉ việc giữa năm: 12 × số tháng làm việc / 12; tháng vào làm và tháng có ngày làm việc cuối đều tính trọn tháng. Vào làm tháng 9 được 4 ngày, tháng 10 được 3 ngày; nghỉ việc tháng 3 được 3 ngày.

Không cộng phép theo thâm niên, không chuyển phép dư sang năm sau và HR không điều chỉnh tay. Hạn mức **không lưu trong database** mà được tính mỗi lần từ `employees.hire_date` và `employees.termination_date` (kể cả ngày cuối đã hẹn); số ngày quy ra phút theo `standard_work_minutes` của ca trong phân công chính đang mở, không có ca thì dùng 480 phút.

Chỉ `ANNUAL` trừ vào số dư. `SICK`, `MATERNITY`, `UNPAID` và `OTHER` đi theo quy trình riêng và không ảnh hưởng hạn mức.

Khi kiểm tra, các đơn `PENDING` khác được tính như đã tiêu. Nếu không làm vậy thì ba đơn cùng chờ duyệt sẽ cùng qua được kiểm tra rồi mới vượt hạn mức sau khi duyệt hết.

Số đã dùng và đang chờ được tính trực tiếp từ `leave_requests`, nên không thể lệch với đơn thật.

---

## Hoàn thiện dữ liệu trước khi tính lương

1. Tạo phân công chính và chọn ca có hiệu lực từ **ngày làm việc đầu tiên** của nhân viên. `POST /api/attendance/prepare` báo mã nhân viên và ngày nếu thiếu phân công hoặc ca; sau khi sửa phân công, gọi lại để tạo các bản ghi `MISSING_PUNCH`.
2. HR đối soát từng `MISSING_PUNCH`: có đi làm thì nhập giờ vào/ra thực tế (`POST /api/attendance/punches`), vắng không báo thì ghi `UNAUTHORIZED_ABSENCE` (`PUT /api/attendance/{id}`); cả hai cần lý do. Nghỉ có lương cần đi qua quy trình duyệt đơn nghỉ.
3. Người có `compensation.manage` nhập mức lương tháng; ngày hiệu lực phải phủ tất cả ngày làm việc cần tính lương. Endpoint, ràng buộc và lỗi xem [Lịch sử lương cơ bản](PAYROLL.md#lịch-sử-lương-cơ-bản).
   Vai trò `HR_MANAGER` có quyền này. Từ hồ sơ nhân viên, HR chọn **Nhập và xem lương** để mở màn hình lương với nhân viên và ngày vào làm được điền sẵn.
4. HR nhập [hồ sơ bảo hiểm/thuế](PAYROLL.md#hồ-sơ-bảo-hiểm-và-thuế) và [người phụ thuộc](PAYROLL.md#người-phụ-thuộc). Nếu thiếu hồ sơ trong kỳ, hệ thống trả lỗi theo nhân viên thay vì giả định không tham gia bảo hiểm.
5. Tính kỳ lương chỉ thành công khi **mọi nhân viên được tuyển dụng trong kỳ** có phân công, ca, công đã xử lý, lịch sử lương và hồ sơ khấu trừ. API trả lỗi kèm mã nhân viên và dữ liệu thiếu; kỳ vẫn ở trạng thái trước đó để sửa rồi tính lại.

Endpoint kỳ lương: `POST /api/payroll/periods`, `GET /api/payroll/periods`, `GET /api/payroll/periods/{id}`, `POST /api/payroll/periods/{id}/calculate|approve|mark-paid|lock|cancel`; xem phiếu qua `GET /api/payroll/employees/{employeeId}/payslips` hoặc `GET /api/payroll/payslips/{id}`. Nhân viên chỉ xem phiếu từ kỳ đã duyệt trở đi. Chi tiết trong [PAYROLL.md](PAYROLL.md#kỳ-lương).

---

## Ghi chú

- Giờ vào/ra sẽ đến từ máy chấm công qua `POST /api/attendance/punches`; web không có thao tác tự chấm công, chỉ HR nhập hoặc sửa giờ khi đối soát.
- Actor duyệt đơn, điều chỉnh công và duyệt tăng ca luôn lấy từ JWT/Security Context.
- Cổng HRM có `/attendance`, `/leave-requests`, `/payslips`; tất cả yêu cầu đi qua BFF allowlist và API kiểm tra quyền cùng phạm vi truy cập.
- Giới hạn hiện tại: mỗi ca có một khoảng nghỉ. Mô hình chỉ lưu một cặp chấm vào/ra và một đơn nghỉ cho mỗi nhân viên mỗi ngày, nên không có hai đơn khác loại trong cùng một ngày (ví dụ sáng nghỉ phép năm, chiều nghỉ ốm). Lịch lễ chỉ nạp sẵn cho 2025 và 2026; từ 2027 HR tự khai báo.
