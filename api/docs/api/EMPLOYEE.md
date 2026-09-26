# API Reference — Employee

Tra cứu hồ sơ nhân sự trong phạm vi được phân công. API danh sách trả thông tin tổng quan của employee và trạng thái account đăng nhập liên kết nếu employee đã được cấp tài khoản.

---

## Endpoint access

| Endpoint | Yêu cầu Bearer token | Permission yêu cầu |
|:---------|:--------------------:|:-------------------:|
| `GET /api/employees` | ✅ | `employee.read` |
| `GET /api/employees/{employeeId}` | ✅ | `employee.read` |

`HR_STAFF`, `BRANCH_MANAGER`, các vai trò giám sát và một số vai trò nghiệp vụ được seed `employee.read`. Kết quả còn bị giới hạn theo scope của role assignment: `SELF`, `ORG_UNIT`, `LOCATION` hoặc `COMPANY`.

---

## GET `/api/employees`

Lấy danh sách employee chưa bị xóa mềm trong phạm vi mà account đang đăng nhập được phép xem.

Endpoint có phân trang:

```http
GET /api/employees?page=0&size=20&sort=id,asc
```

| Parameter | Type | Mặc định | Ý nghĩa |
|:----------|:-----|:--------:|:--------|
| `page` | integer | `0` | Số trang, bắt đầu từ 0 |
| `size` | integer | `20` | Số employee mỗi trang |
| `sort` | string | `id,asc` | Thuộc tính và chiều sắp xếp |

### Response `200 OK`

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 125,
        "employeeCode": "EMP00125",
        "fullName": "Nguyễn Văn An",
        "workEmail": "an.nguyen@company.com",
        "phone": "0901234567",
        "hireDate": "2026-09-01",
        "employmentStatus": "ACTIVE",
        "terminationDate": null,
        "account": {
          "id": 208,
          "username": "an.nguyen",
          "email": "an.nguyen@company.com",
          "status": "ACTIVE"
        }
      },
      {
        "id": 126,
        "employeeCode": "EMP00126",
        "fullName": "Trần Thị Bình",
        "workEmail": "binh.tran@company.com",
        "phone": "0907654321",
        "hireDate": "2026-09-15",
        "employmentStatus": "PROBATION",
        "terminationDate": null,
        "account": null
      }
    ],
    "totalElements": 2,
    "totalPages": 1,
    "number": 0,
    "size": 20
  },
  "error": null
}
```

`account=null` nghĩa là employee đã có hồ sơ nhân sự nhưng chưa được cấp tài khoản đăng nhập. Object account chỉ chứa thông tin nhận diện và trạng thái cần cho nghiệp vụ nhân sự; các dữ liệu quản trị bảo mật như `failedLoginCount`, `lockedUntil` và lịch sử kích hoạt không được trả về từ endpoint này.

### Giá trị trạng thái

- `employmentStatus`: `PROBATION`, `ACTIVE`, `RESIGNED`, `TERMINATED`, `RETIRED`.
- `account.status`: `PENDING`, `ACTIVE`, `LOCKED`, `DISABLED`.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Tham số phân trang hoặc sắp xếp không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có permission `employee.read` hoặc không có scope hợp lệ |

---

## GET `/api/employees/{employeeId}`

Lấy chi tiết một employee chưa bị xóa mềm. Employee phải nằm trong scope `employee.read` của account đang đăng nhập.

Response gồm:

- Hồ sơ nhân sự không nhạy cảm và thông tin học vấn.
- Phân công chính đang hiệu lực tại `currentAssignment`; trả `null` nếu chưa có phân công hiện tại.
- Thông tin account tối thiểu tại `account`; trả `null` nếu employee chưa được cấp tài khoản.

Endpoint không trả CCCD, email cá nhân, địa chỉ, mã số thuế hoặc thông tin ngân hàng. Các trường đó cần permission `employee.sensitive.read` và API dữ liệu nhạy cảm riêng.

### Response `200 OK`

```json
{
  "success": true,
  "data": {
    "id": 125,
    "employeeCode": "EMP00125",
    "fullName": "Nguyễn Văn An",
    "dateOfBirth": "1998-05-20",
    "gender": "MALE",
    "highestEducationLevel": "BACHELOR",
    "major": "Quản trị nhân lực",
    "institution": "Đại học Kinh tế",
    "graduationYear": 2020,
    "workEmail": "an.nguyen@company.com",
    "phone": "0901234567",
    "hireDate": "2026-09-01",
    "employmentStatus": "ACTIVE",
    "terminationDate": null,
    "currentAssignment": {
      "id": 310,
      "employeeId": 125,
      "organizationUnitId": 12,
      "workLocationId": 3,
      "positionId": 8,
      "shiftId": 2,
      "managerEmployeeId": 50,
      "employmentType": "FULL_TIME",
      "effectiveFrom": "2026-09-01",
      "effectiveTo": null,
      "isPrimary": true
    },
    "account": {
      "id": 208,
      "username": "an.nguyen",
      "email": "an.nguyen@company.com",
      "status": "ACTIVE"
    }
  },
  "error": null
}
```

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | `employeeId` không đúng kiểu số |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Không có permission `employee.read` hoặc employee nằm ngoài scope được giao |
| 404 | `EMPLOYEE_NOT_FOUND` | Employee không tồn tại hoặc đã bị xóa mềm |
