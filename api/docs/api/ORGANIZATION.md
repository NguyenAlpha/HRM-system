# API Reference — Organization

Quản lý danh mục cơ cấu tổ chức dùng bởi hồ sơ nhân sự và phạm vi phân quyền. Giai đoạn hiện tại cung cấp đầy đủ API cho `organization_units`; API cho địa điểm làm việc và chức danh sẽ được bổ sung theo cùng module.

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

Tất cả endpoint yêu cầu Bearer token. `HR_STAFF`, `DIRECTOR` và `COMPANY_OWNER` được seed `organization.read`; chỉ `COMPANY_OWNER` được seed `organization.manage`. `SYSTEM_ADMIN` không quản lý danh mục nội bộ doanh nghiệp.

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

## Lỗi chung

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Body, query param hoặc kiểu enum không hợp lệ |
| 400 | `ORGANIZATION_HIERARCHY_INVALID` | Parent inactive, sai loại hoặc tạo chu trình |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có permission tương ứng |
| 404 | `ORGANIZATION_UNIT_NOT_FOUND` | Đơn vị không tồn tại hoặc đã bị xóa mềm |
| 409 | `ORGANIZATION_UNIT_CODE_TAKEN` | Code đang được đơn vị chưa bị xóa mềm sử dụng |
| 409 | `ORGANIZATION_RESOURCE_IN_USE` | Không thể deactivate khi còn child active hoặc không thể xóa vì còn child/dữ liệu đang sử dụng |

---

## Quan hệ với nghiệp vụ khác

- `employee_assignments.organization_unit_id` mô tả nơi employee đang làm việc trong cơ cấu.
- `account_role_assignments.organization_unit_id` mô tả phạm vi dữ liệu mà một role được phép thao tác.
- API Organization Unit không tự tạo, thay đổi hoặc thu hồi hai loại assignment trên.
- Khi tạo hay điều chuyển nhân sự, client lấy ID hợp lệ từ danh mục này rồi gửi sang API employee tương ứng.
