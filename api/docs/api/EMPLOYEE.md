# API Reference — Employee

Tạo và tra cứu hồ sơ nhân sự trong phạm vi được phân công. API danh sách trả thông tin tổng quan của employee và trạng thái account đăng nhập liên kết nếu employee đã được cấp tài khoản.

---

## Endpoint access

| Endpoint | Yêu cầu Bearer token | Permission yêu cầu |
|:---------|:--------------------:|:-------------------:|
| `POST /api/employees` | ✅ | `employee.manage` |
| `GET /api/employees` | ✅ | `employee.read` |
| `GET /api/employees/{employeeId}` | ✅ | `employee.read` |
| `PUT /api/employees/{employeeId}` | ✅ | `employee.manage` |

`HR_STAFF`, `BRANCH_MANAGER`, các vai trò giám sát và một số vai trò nghiệp vụ được seed `employee.read`. Kết quả còn bị giới hạn theo scope của role assignment: `SELF`, `ORG_UNIT`, `LOCATION` hoặc `COMPANY`.

`COMPANY_OWNER` được seed `employee.manage` với scope `COMPANY`, nên có thể gọi API cập nhật hồ sơ. Permission này không bao gồm `employee.read`; quyền đọc vẫn phải đến từ một role khác, chẳng hạn `DIRECTOR` trên account Company Owner đầu tiên.

---

## POST `/api/employees`

Tạo hồ sơ nhân sự và phân công chính ban đầu trong cùng một transaction. Nếu hồ sơ hoặc phân công không hợp lệ thì toàn bộ thao tác được rollback.

Endpoint này chỉ tạo `Employee` và `EmployeeAssignment`:

- Employee được khởi tạo với `employmentStatus=PROBATION`.
- Chưa tạo account đăng nhập.
- Chưa gán role `EMPLOYEE` hoặc role nghiệp vụ.
- Không phát activation token hay mật khẩu.
- Trạng thái nhân sự không phụ thuộc vào việc account được kích hoạt sau này.

### Request

```json
{
  "employee": {
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
    "hireDate": "2026-10-01"
  },
  "initialAssignment": {
    "organizationUnitId": 2,
    "workLocationId": 1,
    "positionId": 5,
    "shiftId": null,
    "managerEmployeeId": 50,
    "employmentType": "FULL_TIME",
    "effectiveFrom": "2026-10-01",
    "reason": "Phân công khi tiếp nhận nhân sự"
  }
}
```

#### Hồ sơ employee

| Field | Bắt buộc | Ràng buộc |
|:------|:--------:|:----------|
| `employeeCode` | ✅ | Không rỗng, tối đa 30 ký tự và duy nhất không phân biệt hoa thường |
| `fullName` | ✅ | Không rỗng, tối đa 200 ký tự |
| `dateOfBirth` | ❌ | Ngày ISO `YYYY-MM-DD` |
| `gender` | ❌ | `MALE`, `FEMALE`, `OTHER`, `UNDISCLOSED` |
| `highestEducationLevel` | ❌ | `HIGH_SCHOOL`, `COLLEGE`, `BACHELOR`, `MASTER`, `DOCTORATE` |
| `major` | ❌ | Tối đa 200 ký tự |
| `institution` | ❌ | Tối đa 200 ký tự |
| `graduationYear` | ❌ | Số nguyên 16-bit |
| `workEmail` | ❌ | Email hợp lệ, tối đa 100 ký tự và duy nhất; được chuẩn hóa chữ thường |
| `phone` | ❌ | Tối đa 20 ký tự |
| `hireDate` | ✅ | Ngày ISO `YYYY-MM-DD` |

Các dữ liệu nhạy cảm như CCCD, email cá nhân, địa chỉ, mã số thuế và thông tin ngân hàng không được nhận tại endpoint này. Chúng thuộc API riêng có permission `employee.sensitive.manage`.

#### Phân công ban đầu

| Field | Bắt buộc | Ràng buộc |
|:------|:--------:|:----------|
| `organizationUnitId` | ✅ | Đơn vị tổ chức đang hoạt động và nằm trong scope quản lý của người gọi |
| `workLocationId` | ✅ | Địa điểm làm việc đang hoạt động và nằm trong scope quản lý của người gọi |
| `positionId` | ✅ | Vị trí công việc đang hoạt động |
| `shiftId` | ❌ | Ca làm việc đang hoạt động nếu được truyền |
| `managerEmployeeId` | ❌ | Employee quản lý đang làm việc và nằm trong scope của người gọi |
| `employmentType` | ✅ | `FULL_TIME`, `PART_TIME`, `TEMPORARY` |
| `effectiveFrom` | ✅ | Không được trước `employee.hireDate` |
| `reason` | ❌ | Lý do phân công |

### Response `201 Created`

```json
{
  "success": true,
  "data": {
    "employee": {
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
      "hireDate": "2026-10-01",
      "employmentStatus": "PROBATION",
      "terminationDate": null,
      "currentAssignment": null,
      "account": null
    },
    "initialAssignment": {
      "id": 310,
      "employeeId": 125,
      "organizationUnitId": 2,
      "workLocationId": 1,
      "positionId": 5,
      "shiftId": null,
      "managerEmployeeId": 50,
      "employmentType": "FULL_TIME",
      "effectiveFrom": "2026-10-01",
      "effectiveTo": null,
      "isPrimary": true
    }
  },
  "error": null
}
```

`employee.currentAssignment` chỉ chứa phân công đang có hiệu lực tại ngày gọi API, nên có thể là `null` khi `effectiveFrom` nằm trong tương lai. `initialAssignment` luôn trả phân công vừa được tạo.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Body hoặc field không hợp lệ; ngày hiệu lực phân công trước ngày tuyển dụng |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Không có `employee.manage`, hoặc đơn vị, địa điểm hay manager nằm ngoài scope quản lý |
| 404 | `ORGANIZATION_UNIT_NOT_FOUND` | Không tìm thấy đơn vị tổ chức đang hoạt động |
| 404 | `LOCATION_NOT_FOUND` | Không tìm thấy địa điểm làm việc đang hoạt động |
| 404 | `EMPLOYEE_NOT_FOUND` | Không tìm thấy employee được chọn làm manager |
| 404 | `RESOURCE_NOT_FOUND` | Không tìm thấy vị trí, ca làm việc hoặc account của người thao tác |
| 409 | `EMPLOYEE_CODE_TAKEN` | Mã nhân viên đã tồn tại, không phân biệt hoa thường |
| 409 | `EMAIL_TAKEN` | Work email đã được employee hoặc account khác sử dụng |

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

---

## PUT `/api/employees/{employeeId}`

Cập nhật các thông tin hồ sơ thông thường được lưu trực tiếp trong entity `Employee`. Employee phải chưa bị xóa mềm và nằm trong scope `employee.manage` của account đang đăng nhập.

### Request

```json
{
  "fullName": "Nguyễn Văn An",
  "dateOfBirth": "1998-05-20",
  "gender": "MALE",
  "highestEducationLevel": "BACHELOR",
  "major": "Quản trị nhân lực",
  "institution": "Đại học Kinh tế",
  "graduationYear": 2020,
  "workEmail": "an.nguyen@company.com",
  "phone": "0901234567"
}
```

| Field | Cột trong `employees` | Bắt buộc | Ràng buộc |
|:------|:----------------------|:--------:|:----------|
| `fullName` | `full_name` | ✅ | Không rỗng, tối đa 200 ký tự |
| `dateOfBirth` | `date_of_birth` | ❌ | Ngày ISO `YYYY-MM-DD`; `null` để xóa |
| `gender` | `gender` | ❌ | `MALE`, `FEMALE`, `OTHER`, `UNDISCLOSED`; `null` để xóa |
| `highestEducationLevel` | `highest_education_level` | ❌ | `HIGH_SCHOOL`, `COLLEGE`, `BACHELOR`, `MASTER`, `DOCTORATE`; `null` để xóa |
| `major` | `major` | ❌ | Tối đa 200 ký tự; `null` để xóa |
| `institution` | `institution` | ❌ | Tối đa 200 ký tự; `null` để xóa |
| `graduationYear` | `graduation_year` | ❌ | Số nguyên 16-bit; `null` để xóa |
| `workEmail` | `work_email` | ❌ | Email hợp lệ, tối đa 100 ký tự và duy nhất; `null` để xóa nếu employee chưa có account |
| `phone` | `phone` | ❌ | Tối đa 20 ký tự; `null` để xóa |

Đây là API `PUT`, vì vậy client phải gửi `fullName`; các field tùy chọn không truyền hoặc truyền `null` sẽ được cập nhật thành `null`.

Nếu employee đã có account, thay đổi `workEmail` sẽ đồng thời cập nhật `Account.email`. Không thể xóa `workEmail` khi account còn tồn tại vì email đăng nhập của account là bắt buộc.

### Các field không cập nhật qua endpoint này

- `id`, `employeeCode`, `createdAt`, `updatedAt`, `deletedAt`, `deletedByAccount`, `deletionReason` do hệ thống quản lý.
- `employmentStatus`, `terminationDate`, `terminationReason` được thay đổi qua workflow vòng đời nhân sự, không sửa trực tiếp.
- `nationalId`, `personalEmail`, `address`, `taxCode`, `bankName`, `bankAccountNumber`, `bankAccountHolder` là dữ liệu nhạy cảm, yêu cầu `employee.sensitive.manage` và endpoint riêng.
- `hireDate` không được sửa qua API này để tránh làm sai lịch sử phân công và vòng đời nhân sự.

### Response `200 OK`

Trả `EmployeeDetailResponse` giống [API lấy chi tiết employee](#get-apiemployeesemployeeid), bao gồm `currentAssignment` và account tối thiểu nếu có.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | `employeeId`, JSON hoặc field không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Không có permission `employee.manage` hoặc employee nằm ngoài scope được giao |
| 404 | `EMPLOYEE_NOT_FOUND` | Employee không tồn tại hoặc đã bị xóa mềm |
| 409 | `CONFLICT` | `workEmail` trùng employee khác hoặc cố xóa email khi employee đã có account |
| 409 | `EMAIL_TAKEN` | `workEmail` đã được account khác sử dụng |
