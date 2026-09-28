# API Reference — Organization

Quản lý danh mục cơ cấu tổ chức, địa điểm làm việc và chức danh dùng bởi hồ sơ nhân sự và phạm vi phân quyền.

---

## Endpoint access

| Endpoint | Permission | Mô tả |
|:---------|:-----------|:------|
| `GET /api/organization-units` | `organization.read` | Lấy danh sách đơn vị tổ chức không phân trang và lọc theo trạng thái, loại hoặc parent |
| `GET /api/organization-units/tree` | `organization.read` | Lấy toàn bộ cơ cấu tổ chức dạng cây |
| `GET /api/organization-units/{unitId}` | `organization.read` | Lấy chi tiết một đơn vị tổ chức |
| `POST /api/organization-units` | `organization.manage` | Tạo một đơn vị tổ chức mới |
| `PUT /api/organization-units/{unitId}` | `organization.manage` | Đổi parent, tên hoặc trạng thái active của đơn vị |
| `DELETE /api/organization-units/{unitId}` | `organization.manage` | Xóa mềm đơn vị không còn được sử dụng |
| `GET /api/work-locations` | `organization.read` | Lấy danh sách địa điểm không phân trang và lọc theo trạng thái, loại hoặc parent |
| `GET /api/work-locations/tree` | `organization.read` | Lấy toàn bộ hệ thống địa điểm dạng cây |
| `GET /api/work-locations/{locationId}` | `organization.read` | Lấy chi tiết một địa điểm làm việc |
| `POST /api/work-locations` | `organization.manage` | Tạo một địa điểm làm việc mới |
| `PUT /api/work-locations/{locationId}` | `organization.manage` | Đổi parent, thông tin liên hệ hoặc trạng thái active của địa điểm |
| `DELETE /api/work-locations/{locationId}` | `organization.manage` | Xóa mềm địa điểm không còn được sử dụng |
| `GET /api/job-positions` | `organization.read` | Lấy danh sách chức danh không phân trang và lọc theo trạng thái hoặc loại quản lý |
| `GET /api/job-positions/{positionId}` | `organization.read` | Lấy chi tiết một chức danh |
| `POST /api/job-positions` | `organization.manage` | Tạo một chức danh mới |
| `PUT /api/job-positions/{positionId}` | `organization.manage` | Cập nhật nội dung và trạng thái chức danh |
| `DELETE /api/job-positions/{positionId}` | `organization.manage` | Xóa mềm chức danh không còn được sử dụng |

Tất cả endpoint yêu cầu Bearer token. `HR_MANAGER`, `DIRECTOR` và `COMPANY_OWNER` được seed `organization.read`; chỉ `COMPANY_OWNER` được seed `organization.manage`. `SYSTEM_ADMIN` không quản lý danh mục nội bộ doanh nghiệp.

---

## Quy tắc cơ cấu tổ chức

| `unitType` | Parent hợp lệ |
|:-----------|:--------------|
| `BOARD` | Phải là root, `parentUnitId=null` |
| `DEPARTMENT` | Bắt buộc thuộc một `BOARD` active |
| `TEAM` | Bắt buộc thuộc một `DEPARTMENT` active |

Quy tắc chung:

- Không cho phép cây chứa chu trình hoặc một đơn vị làm parent của chính nó.
- `code` là định danh kỹ thuật, viết hoa, tối đa 30 ký tự và duy nhất trong các bản ghi chưa bị xóa mềm.
- `code` và `unitType` không thay đổi sau khi tạo; `PUT` chỉ thay parent, tên và `isActive`.
- Không thể ngừng hoạt động một đơn vị khi còn child active.
- Danh sách và cây không trả đơn vị đã bị xóa mềm.

---

## GET `/api/organization-units`

Trả danh sách không phân trang, sắp xếp theo tên.

### Query params

| Param | Bắt buộc | Mô tả |
|:------|:--------:|:------|
| `active` | ❌ | `true` hoặc `false`; bỏ trống để lấy cả hai trạng thái |
| `unitType` | ❌ | `BOARD`, `DEPARTMENT` hoặc `TEAM` |
| `parentUnitId` | ❌ | Chỉ lấy các đơn vị trực thuộc parent này |

### Response `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "id": 2,
      "parentUnitId": 1,
      "parentUnitName": "Ban giám đốc",
      "code": "HR",
      "name": "Phòng Nhân sự",
      "unitType": "DEPARTMENT",
      "isActive": true,
      "createdAt": "2026-09-28T03:00:00Z",
      "updatedAt": "2026-09-28T03:00:00Z"
    }
  ],
  "error": null
}
```

---

## GET `/api/organization-units/tree`

Trả cơ cấu dạng cây. Mặc định chỉ lấy đơn vị active.

```http
GET /api/organization-units/tree?includeInactive=false
```

Mỗi node chứa `id`, `code`, `name`, `unitType`, `isActive` và mảng `children`. Đặt `includeInactive=true` để màn hình quản trị nhìn thấy cả đơn vị đã ngừng hoạt động.

---

## GET `/api/organization-units/{unitId}`

Trả cùng cấu trúc `OrganizationUnitResponse` của endpoint danh sách. ID đã bị xóa mềm được xem như không tồn tại.

---

## POST `/api/organization-units`

### Ví dụ tạo phòng ban

```json
{
  "parentUnitId": 1,
  "code": "SALES",
  "name": "Phòng Kinh doanh",
  "unitType": "DEPARTMENT"
}
```

### Ví dụ tạo nhóm

```json
{
  "parentUnitId": 5,
  "code": "SALES_HCM",
  "name": "Nhóm Kinh doanh Hồ Chí Minh",
  "unitType": "TEAM"
}
```

| Field | Bắt buộc | Ràng buộc |
|:------|:--------:|:----------|
| `parentUnitId` | Theo loại | Null với `BOARD`; bắt buộc với `DEPARTMENT` và `TEAM` |
| `code` | ✅ | Tối đa 30 ký tự; bắt đầu bằng chữ cái và chỉ chứa `A-Z`, `0-9`, `_`, `-` |
| `name` | ✅ | Không rỗng, tối đa 150 ký tự |
| `unitType` | ✅ | `BOARD`, `DEPARTMENT`, `TEAM` |

Response `201 Created` trả `OrganizationUnitResponse`. Đơn vị mới luôn có `isActive=true`.

---

## PUT `/api/organization-units/{unitId}`

Request là trạng thái đầy đủ của các field được phép cập nhật:

```json
{
  "parentUnitId": 1,
  "name": "Phòng Kinh doanh và Phát triển thị trường",
  "isActive": true
}
```

Không nhận `code` hoặc `unitType`. Khi đổi parent, parent mới phải active, đúng loại và không được tạo chu trình.

---

## DELETE `/api/organization-units/{unitId}`

Thực hiện soft delete: đặt `isActive=false` và ghi `deletedAt`. Endpoint từ chối xóa nếu đơn vị:

- Còn bất kỳ child nào chưa bị xóa mềm.
- Được dùng bởi phân công nhân sự hiện tại hoặc tương lai.
- Được dùng bởi role assignment chưa thu hồi ở hiện tại hoặc tương lai.
- Được dùng bởi role assignment request đang `PENDING`.

Dữ liệu lịch sử đã hết hiệu lực không bị xóa và vẫn giữ khóa ngoại tới đơn vị.

---

## Quy tắc địa điểm làm việc

| `locationType` | Parent hợp lệ |
|:---------------|:--------------|
| `HEAD_OFFICE` | Phải là root, `parentLocationId=null` |
| `BRANCH` | Bắt buộc thuộc một `HEAD_OFFICE` active |
| `WAREHOUSE` | Bắt buộc thuộc một `HEAD_OFFICE` hoặc `BRANCH` active |

Quy tắc chung:

- Không cho phép cây chứa chu trình hoặc một địa điểm làm parent của chính nó.
- `code` là định danh kỹ thuật, viết hoa, tối đa 30 ký tự và duy nhất trong các bản ghi chưa bị xóa mềm.
- `code` và `locationType` không thay đổi sau khi tạo; `PUT` chỉ thay parent, tên, địa chỉ, điện thoại và `isActive`.
- Một trụ sở hoặc chi nhánh có thể có nhiều kho; số lượng kho không bị giới hạn bởi database.
- Không thể ngừng hoạt động một địa điểm khi còn child active.
- Danh sách và cây không trả địa điểm đã bị xóa mềm.

---

## GET `/api/work-locations`

Trả danh sách không phân trang, sắp xếp theo tên.

### Query params

| Param | Bắt buộc | Mô tả |
|:------|:--------:|:------|
| `active` | ❌ | `true` hoặc `false`; bỏ trống để lấy cả hai trạng thái |
| `locationType` | ❌ | `HEAD_OFFICE`, `BRANCH` hoặc `WAREHOUSE` |
| `parentLocationId` | ❌ | Chỉ lấy các địa điểm trực thuộc parent này |

### Response `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "id": 4,
      "parentLocationId": 1,
      "parentLocationName": "Trụ sở chính",
      "code": "WAREHOUSE-01",
      "name": "Kho trụ sở chính",
      "locationType": "WAREHOUSE",
      "address": "Thành phố Hồ Chí Minh",
      "phone": null,
      "isActive": true,
      "createdAt": "2026-09-28T03:00:00Z",
      "updatedAt": "2026-09-28T03:00:00Z"
    }
  ],
  "error": null
}
```

---

## GET `/api/work-locations/tree`

Trả cây địa điểm. Mặc định chỉ lấy địa điểm active.

```http
GET /api/work-locations/tree?includeInactive=false
```

Mỗi node chứa `id`, `code`, `name`, `locationType`, `address`, `phone`, `isActive` và mảng `children`. Đặt `includeInactive=true` để màn hình quản trị nhìn thấy cả địa điểm đã ngừng hoạt động.

---

## GET `/api/work-locations/{locationId}`

Trả cùng cấu trúc `WorkLocationResponse` của endpoint danh sách. ID đã bị xóa mềm được xem như không tồn tại.

---

## POST `/api/work-locations`

### Ví dụ tạo chi nhánh

```json
{
  "parentLocationId": 1,
  "code": "BRANCH-03",
  "name": "Chi nhánh Cần Thơ",
  "locationType": "BRANCH",
  "address": "Cần Thơ",
  "phone": "02921234567"
}
```

| Field | Bắt buộc | Ràng buộc |
|:------|:--------:|:----------|
| `parentLocationId` | Theo loại | Null với `HEAD_OFFICE`; bắt buộc với `BRANCH` và `WAREHOUSE` |
| `code` | ✅ | Tối đa 30 ký tự; bắt đầu bằng chữ cái và chỉ chứa `A-Z`, `0-9`, `_`, `-` |
| `name` | ✅ | Không rỗng, tối đa 150 ký tự |
| `locationType` | ✅ | `HEAD_OFFICE`, `BRANCH`, `WAREHOUSE` |
| `address` | ✅ | Không rỗng |
| `phone` | ❌ | Tối đa 20 ký tự; chuỗi rỗng được chuẩn hóa thành null |

Response `201 Created` trả `WorkLocationResponse`. Địa điểm mới luôn có `isActive=true`.

---

## PUT `/api/work-locations/{locationId}`

Request là trạng thái đầy đủ của các field được phép cập nhật:

```json
{
  "parentLocationId": 1,
  "name": "Chi nhánh Cần Thơ",
  "address": "12 Nguyễn Trãi, Cần Thơ",
  "phone": "02921234567",
  "isActive": true
}
```

Không nhận `code` hoặc `locationType`. Khi đổi parent, parent mới phải active, đúng loại và không được tạo chu trình.

---

## DELETE `/api/work-locations/{locationId}`

Thực hiện soft delete: đặt `isActive=false` và ghi `deletedAt`. Endpoint từ chối xóa nếu địa điểm:

- Còn bất kỳ child nào chưa bị xóa mềm.
- Được dùng bởi phân công nhân sự hiện tại hoặc tương lai.
- Được dùng bởi role assignment chưa thu hồi ở hiện tại hoặc tương lai.
- Được dùng bởi role assignment request đang `PENDING`.

Dữ liệu lịch sử đã hết hiệu lực không bị xóa và vẫn giữ khóa ngoại tới địa điểm.

---

## Quy tắc chức danh

- Chức danh mô tả công việc của nhân sự, không phải role hoặc permission trong RBAC.
- `code` là định danh kỹ thuật, viết hoa, tối đa 30 ký tự và duy nhất trong các bản ghi chưa bị xóa mềm.
- `code` không thay đổi sau khi tạo; `PUT` chỉ thay tiêu đề, mô tả, cờ vị trí quản lý và `isActive`.
- Chức danh inactive không thể được dùng cho phân công nhân sự mới nhưng các phân công đã tồn tại vẫn được giữ.
- Danh sách không trả chức danh đã bị xóa mềm.

---

## GET `/api/job-positions`

Trả danh sách không phân trang, sắp xếp theo tiêu đề chức danh.

### Query params

| Param | Bắt buộc | Mô tả |
|:------|:--------:|:------|
| `active` | ❌ | `true` hoặc `false`; bỏ trống để lấy cả hai trạng thái |
| `managerial` | ❌ | `true` để lấy vị trí quản lý, `false` để lấy vị trí không quản lý |

### Response `200 OK`

```json
{
  "success": true,
  "data": [
    {
      "id": 2,
      "code": "HR_SPECIALIST",
      "title": "Chuyên viên nhân sự",
      "description": "Thực hiện các nghiệp vụ nhân sự",
      "isManagerial": false,
      "isActive": true,
      "createdAt": "2026-09-28T03:00:00Z",
      "updatedAt": "2026-09-28T03:00:00Z"
    }
  ],
  "error": null
}
```

---

## GET `/api/job-positions/{positionId}`

Trả cùng cấu trúc `JobPositionResponse` của endpoint danh sách. ID đã bị xóa mềm được xem như không tồn tại.

---

## POST `/api/job-positions`

```json
{
  "code": "SALES_SPECIALIST",
  "title": "Chuyên viên kinh doanh",
  "description": "Phụ trách hoạt động kinh doanh và chăm sóc khách hàng",
  "isManagerial": false
}
```

| Field | Bắt buộc | Ràng buộc |
|:------|:--------:|:----------|
| `code` | ✅ | Tối đa 30 ký tự; bắt đầu bằng chữ cái và chỉ chứa `A-Z`, `0-9`, `_`, `-` |
| `title` | ✅ | Không rỗng, tối đa 150 ký tự |
| `description` | ❌ | Chuỗi rỗng được chuẩn hóa thành null |
| `isManagerial` | ✅ | Có phải vị trí quản lý hay không |

Response `201 Created` trả `JobPositionResponse`. Chức danh mới luôn có `isActive=true`.

---

## PUT `/api/job-positions/{positionId}`

Request là trạng thái đầy đủ của các field được phép cập nhật:

```json
{
  "title": "Chuyên viên kinh doanh cao cấp",
  "description": "Phụ trách khách hàng doanh nghiệp",
  "isManagerial": false,
  "isActive": true
}
```

Không nhận `code`; mã kỹ thuật của chức danh không thay đổi sau khi tạo.

---

## DELETE `/api/job-positions/{positionId}`

Thực hiện soft delete: đặt `isActive=false` và ghi `deletedAt`. Endpoint từ chối xóa nếu chức danh đang được dùng bởi phân công nhân sự hiện tại hoặc tương lai. Phân công lịch sử đã hết hiệu lực vẫn được giữ khóa ngoại tới chức danh.

---

## Lỗi chung

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Body, query param hoặc kiểu enum không hợp lệ |
| 400 | `ORGANIZATION_HIERARCHY_INVALID` | Parent inactive, sai loại hoặc tạo chu trình |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có permission tương ứng |
| 404 | `ORGANIZATION_UNIT_NOT_FOUND` | Đơn vị không tồn tại hoặc đã bị xóa mềm |
| 404 | `LOCATION_NOT_FOUND` | Địa điểm không tồn tại hoặc đã bị xóa mềm |
| 404 | `JOB_POSITION_NOT_FOUND` | Chức danh không tồn tại hoặc đã bị xóa mềm |
| 409 | `ORGANIZATION_UNIT_CODE_TAKEN` | Code đang được đơn vị chưa bị xóa mềm sử dụng |
| 409 | `WORK_LOCATION_CODE_TAKEN` | Code đang được địa điểm chưa bị xóa mềm sử dụng |
| 409 | `JOB_POSITION_CODE_TAKEN` | Code đang được chức danh chưa bị xóa mềm sử dụng |
| 409 | `ORGANIZATION_RESOURCE_IN_USE` | Không thể deactivate khi còn child active hoặc không thể xóa vì còn child/dữ liệu đang sử dụng |

---

## Quan hệ với nghiệp vụ khác

- `employee_assignments.organization_unit_id` mô tả nơi employee đang làm việc trong cơ cấu.
- `employee_assignments.work_location_id` mô tả địa điểm employee được phân công làm việc.
- `employee_assignments.position_id` mô tả chức danh công việc của employee trong lần phân công.
- `account_role_assignments.organization_unit_id` mô tả phạm vi dữ liệu mà một role được phép thao tác.
- `account_role_assignments.work_location_id` mô tả phạm vi địa điểm mà một role được phép thao tác.
- API danh mục Organization không tự tạo, thay đổi hoặc thu hồi các assignment trên.
- Khi tạo hay điều chuyển nhân sự, client lấy ID hợp lệ từ danh mục này rồi gửi sang API employee tương ứng.
