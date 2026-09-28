# API Reference — Work Shift

Quản lý danh mục ca làm việc dùng trong phân công nhân sự, chấm công và tính lương. Ca làm việc là dữ liệu nghiệp vụ, không phải role hoặc permission trong RBAC.

---

## Endpoint access

| Endpoint | Permission | Mô tả |
|:---------|:-----------|:------|
| `GET /api/work-shifts` | `organization.read` | Lấy danh sách ca không phân trang và lọc theo trạng thái hoặc ca qua đêm |
| `GET /api/work-shifts/{shiftId}` | `organization.read` | Lấy chi tiết một ca làm việc |
| `POST /api/work-shifts` | `organization.manage` | Tạo ca làm việc mới |
| `PUT /api/work-shifts/{shiftId}` | `organization.manage` | Cập nhật nội dung và trạng thái ca làm việc |
| `DELETE /api/work-shifts/{shiftId}` | `organization.manage` | Xóa mềm ca không còn được dùng bởi phân công hiện tại hoặc tương lai |

Tất cả endpoint yêu cầu Bearer token. `HR_MANAGER`, `DIRECTOR` và `COMPANY_OWNER` được seed `organization.read`; chỉ `COMPANY_OWNER` được seed `organization.manage`.

---

## Quy tắc ca làm việc

- `code` viết hoa, tối đa 30 ký tự, duy nhất trong các bản ghi chưa bị xóa mềm và không thay đổi sau khi tạo.
- Ca trong ngày (`crossesMidnight=false`) phải có `endTime` sau `startTime`.
- Ca qua đêm (`crossesMidnight=true`) phải có `endTime` trước hoặc bằng `startTime`; hai giờ bằng nhau biểu diễn ca đủ 24 giờ.
- `breakMinutes` phải nhỏ hơn tổng thời lượng ca.
- `standardWorkMinutes` không được vượt thời lượng ca sau khi trừ thời gian nghỉ.
- `graceLateMinutes` không được vượt tổng thời lượng ca.
- Khi ca đã có dữ liệu chấm công, không thể sửa giờ, số phút nghỉ, số phút công chuẩn, thời gian đi trễ cho phép hoặc cờ qua đêm. Hãy tạo mã ca mới để giữ đúng dữ liệu lịch sử.
- Ca inactive không thể gán cho phân công mới nhưng phân công và chấm công lịch sử vẫn được giữ.

---

## GET `/api/work-shifts`

Trả danh sách không phân trang, sắp xếp theo tên ca.

### Query params

| Param | Bắt buộc | Mô tả |
|:------|:--------:|:------|
| `active` | ❌ | `true` hoặc `false`; bỏ trống để lấy cả hai trạng thái |
| `crossesMidnight` | ❌ | `true` để lấy ca qua đêm, `false` để lấy ca trong ngày |

### Response `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "code": "OFFICE_DAY",
      "name": "Ca hành chính",
      "startTime": "08:00:00",
      "endTime": "17:00:00",
      "breakMinutes": 60,
      "standardWorkMinutes": 480,
      "graceLateMinutes": 15,
      "crossesMidnight": false,
      "isActive": true,
      "createdAt": "2026-09-28T03:00:00Z",
      "updatedAt": "2026-09-28T03:00:00Z"
    }
  ],
  "error": null
}
```

---

## GET `/api/work-shifts/{shiftId}`

Trả cùng cấu trúc `WorkShiftResponse` của endpoint danh sách. ID đã bị xóa mềm được xem như không tồn tại.

---

## POST `/api/work-shifts`

```json
{
  "code": "OFFICE_DAY",
  "name": "Ca hành chính",
  "startTime": "08:00:00",
  "endTime": "17:00:00",
  "breakMinutes": 60,
  "standardWorkMinutes": 480,
  "graceLateMinutes": 15,
  "crossesMidnight": false
}
```

| Field | Bắt buộc | Ràng buộc |
|:------|:--------:|:----------|
| `code` | ✅ | Tối đa 30 ký tự; bắt đầu bằng chữ cái và chỉ chứa `A-Z`, `0-9`, `_`, `-` |
| `name` | ✅ | Không rỗng, tối đa 100 ký tự |
| `startTime` | ✅ | Giờ ISO `HH:mm:ss` |
| `endTime` | ✅ | Giờ ISO `HH:mm:ss`, phù hợp với `crossesMidnight` |
| `breakMinutes` | ✅ | Từ 0 đến 1439 và nhỏ hơn tổng thời lượng ca |
| `standardWorkMinutes` | ✅ | Từ 1 đến 1440 và không vượt thời lượng còn lại sau giờ nghỉ |
| `graceLateMinutes` | ✅ | Từ 0 đến 1440 và không vượt tổng thời lượng ca |
| `crossesMidnight` | ✅ | Ca có kết thúc vào ngày kế tiếp hay không |

Response `201 Created` trả `WorkShiftResponse`. Ca mới luôn có `isActive=true`.

---

## PUT `/api/work-shifts/{shiftId}`

Request là trạng thái đầy đủ của các field được phép cập nhật:

```json
{
  "name": "Ca hành chính",
  "startTime": "08:00:00",
  "endTime": "17:00:00",
  "breakMinutes": 60,
  "standardWorkMinutes": 480,
  "graceLateMinutes": 10,
  "crossesMidnight": false,
  "isActive": true
}
```

Không nhận `code`. Nếu ca đã có chấm công, chỉ có thể đổi `name` hoặc `isActive`; thay đổi lịch làm việc trả `409 ORGANIZATION_RESOURCE_IN_USE`.

---

## DELETE `/api/work-shifts/{shiftId}`

Thực hiện soft delete bằng cách đặt `isActive=false` và ghi `deletedAt`. Endpoint từ chối xóa nếu ca đang được dùng bởi phân công nhân sự hiện tại hoặc tương lai. Phân công và chấm công lịch sử vẫn giữ khóa ngoại tới ca đã xóa mềm.

---

## Lỗi chung

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Body, kiểu dữ liệu hoặc giới hạn số phút không hợp lệ |
| 400 | `WORK_SHIFT_SCHEDULE_INVALID` | Quan hệ giờ bắt đầu/kết thúc hoặc số phút không phù hợp |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có permission tương ứng |
| 404 | `WORK_SHIFT_NOT_FOUND` | Ca không tồn tại hoặc đã bị xóa mềm |
| 409 | `WORK_SHIFT_CODE_TAKEN` | Code đang được ca chưa bị xóa mềm sử dụng |
| 409 | `ORGANIZATION_RESOURCE_IN_USE` | Ca có lịch sử chấm công cần bảo toàn hoặc còn trong phân công hiện tại/tương lai |
