# API Reference — Employee

Tạo và tra cứu hồ sơ nhân sự trong phạm vi được phân công. API danh sách trả thông tin tổng quan của employee và trạng thái account đăng nhập liên kết nếu employee đã được cấp tài khoản.

---

## Endpoint access

| Endpoint | Yêu cầu Bearer token | Permission yêu cầu | Mô tả |
|:---------|:--------------------:|:-------------------:|:------|
| `POST /api/employees` | ✅ | `employee.create` | Tạo hồ sơ nhân sự và phân công chính ban đầu, chưa tạo account đăng nhập |
| `GET /api/employees` | ✅ | `employee.list.read` | Lấy danh sách nhân sự có phân trang trong scope được giao |
| `GET /api/employees/{employeeId}` | ✅ | `employee.read` | Lấy chi tiết hồ sơ, phân công hiện tại và thông tin account nếu đã có |
| `PUT /api/employees/{employeeId}` | ✅ | `employee.update` | Cập nhật các thông tin hồ sơ được phép thay đổi trong entity employee |
| `POST /api/employees/{employeeId}/confirm` | ✅ | `employee.probation.confirm` | Xác nhận nhân sự thử việc trở thành nhân sự chính thức |
| `POST /api/employees/{employeeId}/resignation` | ✅ | `employee.lifecycle.manage` | Hoàn tất nghỉ việc, đóng phân công và vô hiệu hóa account |
| `GET /api/employees/{employeeId}/assignments` | ✅ | `employee.assignment.read` | Lấy toàn bộ lịch sử phân công của nhân sự |
| `GET /api/employees/{employeeId}/assignments/current` | ✅ | `employee.assignment.read` | Lấy phân công đang hiệu lực tại ngày gọi API |
| `POST /api/employees/{employeeId}/assignments` | ✅ | `employee.assignment.manage` | Điều chuyển, bổ nhiệm hoặc thay đổi phân công của nhân sự |
| `GET /api/employees/{employeeId}/sensitive` | ✅ | `employee.sensitive.read` | Đọc CCCD, email cá nhân, địa chỉ, mã số thuế và thông tin ngân hàng |
| `PUT /api/employees/{employeeId}/sensitive` | ✅ | `employee.sensitive.manage` | Cập nhật các trường dữ liệu nhạy cảm nói trên |

Permission chỉ quyết định account được thực hiện hành động nào; scope của role assignment (`SELF`, `ORG_UNIT`, `LOCATION`, `COMPANY`) tiếp tục quyết định hành động đó được áp dụng lên những employee nào. Ví dụ, account có `employee.update` ở scope `ORG_UNIT` chỉ sửa được hồ sơ thuộc đơn vị được giao.

`employee.read` chỉ còn dùng để xem chi tiết một employee. Quyền tổng quát `employee.manage` đã ngừng sử dụng và được thay bằng các quyền nguyên tử.

Phân quyền mặc định của các system role:

| Role | Permission Employee mặc định |
|:-----|:-----------------------------|
| `HR_MANAGER` | Toàn bộ quyền đọc, tạo, sửa, xác nhận thử việc, phân công, thực thi vòng đời và xóa mềm; thêm quyền dữ liệu nhạy cảm |
| `PAYROLL_ACCOUNTANT`, `PAYROLL_APPROVER` | `employee.list.read`, `employee.read`, `employee.assignment.read` |
| `DIRECTOR` | Toàn bộ quyền Employee thông thường và `employee.lifecycle.approve` |
| `COMPANY_OWNER` | Toàn bộ quyền Employee thông thường; không mặc định quyền dữ liệu nhạy cảm hoặc phê duyệt vòng đời |
| `SYSTEM_ADMIN` | `employee.list.read`, `employee.read`, `employee.assignment.read` |

`employee.lifecycle.manage` là quyền thực thi thay đổi đã hợp lệ (ví dụ hoàn tất nghỉ việc), còn `employee.lifecycle.approve` là quyền phê duyệt cuối. Hai quyền này không thay thế nhau.

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
| `fullName` | ✅ | Không rỗng, tối đa 200 ký tự |
| `dateOfBirth` | ✅ | Ngày ISO `YYYY-MM-DD`; phải đủ 17 tuổi tính theo ngày hiện tại tại Việt Nam |
| `gender` | ❌ | `MALE`, `FEMALE`, `OTHER`, `UNDISCLOSED` |
| `highestEducationLevel` | ❌ | `HIGH_SCHOOL`, `COLLEGE`, `BACHELOR`, `MASTER`, `DOCTORATE` |
| `major` | ❌ | Tối đa 200 ký tự |
| `institution` | ❌ | Tối đa 200 ký tự |
| `graduationYear` | ❌ | Số nguyên 16-bit |
| `workEmail` | ❌ | Email hợp lệ, tối đa 100 ký tự và duy nhất; được chuẩn hóa chữ thường |
| `phone` | ❌ | Tối đa 20 ký tự |
| `hireDate` | ✅ | Ngày ISO `YYYY-MM-DD` |

Các dữ liệu nhạy cảm như CCCD, email cá nhân, địa chỉ, mã số thuế và thông tin ngân hàng không được nhận tại endpoint này. Chúng được nhập qua `PUT /api/employees/{employeeId}/sensitive` với permission `employee.sensitive.manage`.

`employeeCode` do server tạo theo vị trí trong `initialAssignment`: `GD` (Giám đốc), `NS` (Nhân sự), `KT` (Kế toán tiền lương), `VH` (Quản lý vận hành), `CN` (Quản lý chi nhánh), `KHO` (Giám sát kho), `TN` (Trưởng nhóm), `NV` (Nhân viên hoặc vị trí chưa cấu hình). Số thứ tự tăng riêng theo tiền tố, tối thiểu bốn chữ số. Mã không đổi khi nhân viên chuyển vị trí và không được tái sử dụng.

#### Phân công ban đầu

Client lấy `organizationUnitId`, `workLocationId` và `positionId` từ [Organization API](./ORGANIZATION.md); API employee không nhận tên phòng ban, địa điểm hoặc chức danh thay cho ID danh mục.

| Field | Bắt buộc | Ràng buộc |
|:------|:--------:|:----------|
| `organizationUnitId` | ✅ | Đơn vị tổ chức đang hoạt động và nằm trong scope quản lý của người gọi |
| `workLocationId` | ✅ | Địa điểm làm việc đang hoạt động và nằm trong scope quản lý của người gọi |
| `positionId` | ✅ | Vị trí công việc đang hoạt động |
| `shiftId` | ❌ | Ca làm việc đang hoạt động từ [Work Shift API](./WORK_SHIFT.md) nếu được truyền |
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
      "employeeCode": "NS0001",
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
      "organizationUnitName": "Phòng Nhân sự",
      "workLocationId": 1,
      "workLocationName": "Trụ sở chính",
      "positionId": 5,
      "positionTitle": "Chuyên viên nhân sự",
      "shiftId": null,
      "shiftName": null,
      "managerEmployeeId": 50,
      "managerEmployeeName": "Trần Văn Bình",
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
| 403 | `FORBIDDEN` | Không có `employee.create`, hoặc đơn vị, địa điểm hay manager nằm ngoài scope quản lý |
| 404 | `ORGANIZATION_UNIT_NOT_FOUND` | Không tìm thấy đơn vị tổ chức đang hoạt động |
| 404 | `LOCATION_NOT_FOUND` | Không tìm thấy địa điểm làm việc đang hoạt động |
| 404 | `EMPLOYEE_NOT_FOUND` | Không tìm thấy employee được chọn làm manager |
| 404 | `JOB_POSITION_NOT_FOUND` | Không tìm thấy chức danh đang hoạt động |
| 404 | `WORK_SHIFT_NOT_FOUND` | Không tìm thấy ca làm việc đang hoạt động |
| 404 | `RESOURCE_NOT_FOUND` | Không tìm thấy account của người thao tác |
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
        "currentAssignment": {
          "id": 310,
          "employeeId": 125,
          "organizationUnitId": 2,
          "organizationUnitName": "Phòng Nhân sự",
          "workLocationId": 1,
          "workLocationName": "Trụ sở chính",
          "positionId": 5,
          "positionTitle": "Chuyên viên nhân sự",
          "shiftId": 1,
          "shiftName": "Ca hành chính",
          "managerEmployeeId": 50,
          "managerEmployeeName": "Trần Văn Bình",
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
      {
        "id": 126,
        "employeeCode": "EMP00126",
        "fullName": "Trần Thị Bình",
        "workEmail": "binh.tran@company.com",
        "phone": "0907654321",
        "hireDate": "2026-09-15",
        "employmentStatus": "PROBATION",
        "terminationDate": null,
        "currentAssignment": null,
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

`currentAssignment` là phân công chính đang hiệu lực tại ngày gọi API và có thể là `null` nếu nhân sự chưa có phân công hiện tại. `account=null` nghĩa là employee đã có hồ sơ nhân sự nhưng chưa được cấp tài khoản đăng nhập. Object account chỉ chứa thông tin nhận diện và trạng thái cần cho nghiệp vụ nhân sự; các dữ liệu quản trị bảo mật như `failedLoginCount`, `lockedUntil` và lịch sử kích hoạt không được trả về từ endpoint này.

### Giá trị trạng thái

- `employmentStatus`: `PROBATION`, `ACTIVE`, `RESIGNED`, `TERMINATED`, `RETIRED`.
- `account.status`: `PENDING`, `ACTIVE`, `LOCKED`, `DISABLED`.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Tham số phân trang hoặc sắp xếp không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Account không có permission `employee.list.read` hoặc không có scope hợp lệ |

---

## GET `/api/employees/{employeeId}`

Lấy chi tiết một employee chưa bị xóa mềm. Employee phải nằm trong scope `employee.read` của account đang đăng nhập.

Response gồm:

- Hồ sơ nhân sự không nhạy cảm và thông tin học vấn.
- Phân công chính đang hiệu lực tại `currentAssignment`; trả `null` nếu chưa có phân công hiện tại.
- Thông tin account tối thiểu tại `account`; trả `null` nếu employee chưa được cấp tài khoản.

Endpoint không trả CCCD, email cá nhân, địa chỉ, mã số thuế hoặc thông tin ngân hàng. Các trường đó được đọc qua `GET /api/employees/{employeeId}/sensitive` với permission `employee.sensitive.read`.

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
      "organizationUnitName": "Phòng Kinh doanh",
      "workLocationId": 3,
      "workLocationName": "Chi nhánh Hà Nội",
      "positionId": 8,
      "positionTitle": "Nhân viên",
      "shiftId": 2,
      "shiftName": "Ca hành chính",
      "managerEmployeeId": 50,
      "managerEmployeeName": "Trần Văn Bình",
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

Cập nhật các thông tin hồ sơ thông thường được lưu trực tiếp trong entity `Employee`. Employee phải chưa bị xóa mềm và nằm trong scope `employee.update` của account đang đăng nhập.

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
| `dateOfBirth` | `date_of_birth` | ❌ | Ngày ISO `YYYY-MM-DD`, nếu có phải đủ 17 tuổi tính theo ngày hiện tại tại Việt Nam; `null` để xóa |
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
- `employmentStatus`, `terminationDate`, `terminationReason` được thay đổi qua `POST /{employeeId}/confirm` và `POST /{employeeId}/resignation`, không sửa trực tiếp.
- `nationalId`, `personalEmail`, `address`, `taxCode`, `bankName`, `bankAccountNumber`, `bankAccountHolder` là dữ liệu nhạy cảm, cập nhật qua `PUT /api/employees/{employeeId}/sensitive`.
- `hireDate` không được sửa qua API này để tránh làm sai lịch sử phân công và vòng đời nhân sự.

### Response `200 OK`

Trả `EmployeeDetailResponse` giống [API lấy chi tiết employee](#get-apiemployeesemployeeid), bao gồm `currentAssignment` và account tối thiểu nếu có.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | `employeeId`, JSON hoặc field không hợp lệ |
| 401 | `UNAUTHORIZED` | Thiếu access token hoặc access token không hợp lệ |
| 403 | `FORBIDDEN` | Không có permission `employee.update` hoặc employee nằm ngoài scope được giao |
| 404 | `EMPLOYEE_NOT_FOUND` | Employee không tồn tại hoặc đã bị xóa mềm |
| 409 | `CONFLICT` | `workEmail` trùng employee khác hoặc cố xóa email khi employee đã có account |
| 409 | `EMAIL_TAKEN` | `workEmail` đã được account khác sử dụng |

---

## POST `/api/employees/{employeeId}/confirm`

Xác nhận nhân sự đã hoàn thành thử việc bằng cách chuyển `employmentStatus` từ `PROBATION` sang `ACTIVE`. Endpoint không nhận request body và không thay đổi phân công hoặc trạng thái account.

Response `200 OK` trả `EmployeeDetailResponse` sau khi cập nhật.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | `employeeId` không đúng kiểu số |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Thiếu `employee.probation.confirm` hoặc employee nằm ngoài scope được giao |
| 404 | `EMPLOYEE_NOT_FOUND` | Employee không tồn tại hoặc đã bị xóa mềm |
| 409 | `EMPLOYMENT_STATUS_TRANSITION_NOT_ALLOWED` | Employee không còn ở trạng thái `PROBATION` |

---

## POST `/api/employees/{employeeId}/resignation`

Hoàn tất nghỉ việc. Đây là bước **thực thi** một quyết định đã hợp lệ, không phải bước phê duyệt: endpoint không kiểm tra có đơn hay có người duyệt hay chưa.

Một lần gọi thay đổi ba thứ trong cùng transaction:

1. `employmentStatus` chuyển sang `RESIGNED`, ghi `terminationDate` và `terminationReason`.
2. Phân công chính đang mở được đóng lại tại `terminationDate`.
3. Account liên kết chuyển sang `DISABLED` và toàn bộ refresh token của account bị thu hồi, nên các phiên đang đăng nhập không refresh được nữa.

### Request

```json
{
  "terminationDate": "2026-10-31",
  "terminationReason": "Nghỉ theo nguyện vọng cá nhân"
}
```

| Field | Bắt buộc | Ràng buộc |
|:------|:--------:|:----------|
| `terminationDate` | ✅ | Ngày ISO `YYYY-MM-DD`, không được trước `hireDate` |
| `terminationReason` | ✅ | Không rỗng |

### Response `200 OK`

Trả `EmployeeDetailResponse` sau khi cập nhật, cùng cấu trúc với `POST /{employeeId}/confirm`.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu `terminationDate` hoặc `terminationReason`, hoặc `terminationDate` trước `hireDate` |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Thiếu `employee.lifecycle.manage` hoặc employee nằm ngoài scope được giao |
| 404 | `EMPLOYEE_NOT_FOUND` | Employee không tồn tại hoặc đã bị xóa mềm |
| 409 | `CONFLICT` | Employee đã kết thúc làm việc trước đó |

`TERMINATED` và `RETIRED` chưa có đường đi qua API; endpoint này chỉ đặt `RESIGNED`.

---

## GET `/api/employees/{employeeId}/assignments`

Lấy toàn bộ lịch sử phân công của employee, gồm phân công đã kết thúc, đang hiệu lực và đã lên lịch trong tương lai. Kết quả không phân trang và được sắp xếp theo `effectiveFrom` giảm dần.

Endpoint chỉ trả employee nằm trong scope `employee.assignment.read` của người gọi. Mỗi phần tử có cấu trúc `EmployeeAssignmentResponse`:

```json
{
  "success": true,
  "data": [
    {
      "id": 415,
      "employeeId": 125,
      "organizationUnitId": 8,
      "organizationUnitName": "Phòng Vận hành",
      "workLocationId": 2,
      "workLocationName": "Chi nhánh Hà Nội",
      "positionId": 4,
      "positionTitle": "Quản lý vận hành",
      "shiftId": 1,
      "shiftName": "Ca hành chính",
      "managerEmployeeId": 40,
      "managerEmployeeName": "Lê Văn Cường",
      "employmentType": "FULL_TIME",
      "effectiveFrom": "2026-10-01",
      "effectiveTo": null,
      "isPrimary": true
    },
    {
      "id": 310,
      "employeeId": 125,
      "organizationUnitId": 2,
      "organizationUnitName": "Phòng Nhân sự",
      "workLocationId": 1,
      "workLocationName": "Trụ sở chính",
      "positionId": 2,
      "positionTitle": "Chuyên viên nhân sự",
      "shiftId": 1,
      "shiftName": "Ca hành chính",
      "managerEmployeeId": 35,
      "managerEmployeeName": "Nguyễn Thị Hoa",
      "employmentType": "FULL_TIME",
      "effectiveFrom": "2026-09-01",
      "effectiveTo": "2026-09-30",
      "isPrimary": true
    }
  ],
  "error": null
}
```

Mỗi quan hệ danh mục trả cả ID kỹ thuật và tên hiển thị tại thời điểm đọc (`organizationUnitName`, `workLocationName`, `positionTitle`, `shiftName`, `managerEmployeeName`), nên client không cần gọi từng API chi tiết chỉ để dựng bảng lịch sử.

---

## GET `/api/employees/{employeeId}/assignments/current`

Lấy phân công chính đang hiệu lực tại ngày gọi API, dựa trên khoảng `effectiveFrom`–`effectiveTo`. Phân công được lên lịch trong tương lai không được xem là phân công hiện tại.

Response `200 OK` trả một `EmployeeAssignmentResponse`. Nếu employee tồn tại nhưng chưa có phân công hiệu lực, API trả `404 EMPLOYEE_ASSIGNMENT_NOT_FOUND`.

---

## POST `/api/employees/{employeeId}/assignments`

Tạo một lần phân công chính mới để điều chuyển phòng ban, địa điểm, chức danh, ca làm việc, quản lý trực tiếp hoặc loại hình làm việc.

```json
{
  "organizationUnitId": 8,
  "workLocationId": 2,
  "positionId": 4,
  "shiftId": 1,
  "managerEmployeeId": 40,
  "employmentType": "FULL_TIME",
  "effectiveFrom": "2026-10-01",
  "reason": "Điều chuyển sang bộ phận vận hành"
}
```

| Field | Bắt buộc | Ràng buộc |
|:------|:--------:|:----------|
| `organizationUnitId` | ✅ | Đơn vị đang active và thuộc scope quản lý của người gọi |
| `workLocationId` | ✅ | Địa điểm đang active và thuộc scope quản lý của người gọi |
| `positionId` | ✅ | Chức danh đang active |
| `shiftId` | ❌ | Ca làm việc đang active từ [Work Shift API](./WORK_SHIFT.md) nếu được truyền |
| `managerEmployeeId` | ❌ | Quản lý còn làm việc, thuộc scope của người gọi và không phải chính employee |
| `employmentType` | ✅ | `FULL_TIME`, `PART_TIME`, `TEMPORARY` |
| `effectiveFrom` | ✅ | Không trước ngày tuyển dụng và phải sau ngày bắt đầu của phân công mở hiện tại |
| `reason` | ❌ | Lý do điều chuyển, bổ nhiệm hoặc thay đổi phân công |

Trong cùng transaction, hệ thống:

1. Khóa hồ sơ employee để tuần tự hóa các yêu cầu điều chuyển đồng thời.
2. Đóng phân công chính đang mở bằng `effectiveTo = effectiveFrom mới - 1 ngày`.
3. Tạo phân công mới với `isPrimary=true`, `effectiveTo=null` và ghi nhận account thao tác.

Database không cho phép hai phân công chính của cùng employee có khoảng hiệu lực chồng lấn. Response `201 Created` trả phân công vừa tạo.

API này chỉ cập nhật lịch sử công việc trong `employee_assignments`. Nó không cấp, thu hồi hoặc thay đổi role trong `account_role_assignments`; role nghiệp vụ được quản lý bằng API role assignment riêng.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Ngày hiệu lực không hợp lệ, manager là chính employee hoặc body sai |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Thiếu `employee.assignment.manage`, employee nguồn hoặc địa điểm đích nằm ngoài scope quản lý |
| 404 | `EMPLOYEE_NOT_FOUND` | Employee hoặc manager không tồn tại |
| 404 | `ORGANIZATION_UNIT_NOT_FOUND` | Đơn vị không tồn tại hoặc không active |
| 404 | `LOCATION_NOT_FOUND` | Địa điểm không tồn tại hoặc không active |
| 404 | `JOB_POSITION_NOT_FOUND` | Chức danh không tồn tại hoặc không active |
| 404 | `WORK_SHIFT_NOT_FOUND` | Ca làm việc không tồn tại hoặc không active |
| 404 | `RESOURCE_NOT_FOUND` | Account thao tác không tồn tại |
| 409 | `CONFLICT` | Employee đã kết thúc làm việc hoặc dữ liệu phân công hiện tại không nhất quán |

---

## GET `/api/employees/{employeeId}/sensitive`

Đọc các trường nhân sự nhạy cảm. Các trường này cố tình không nằm trong `GET /api/employees/{employeeId}` để một quyền đọc hồ sơ thông thường không kéo theo quyền đọc CCCD hay tài khoản ngân hàng.

### Response `200 OK`

```json
{
  "success": true,
  "data": {
    "employeeId": 40,
    "nationalId": "079201001234",
    "personalEmail": "an.nguyen@gmail.com",
    "address": "12 Nguyễn Huệ, Quận 1, TP.HCM",
    "taxCode": "8412345678",
    "bankName": "Vietcombank",
    "bankAccountNumber": "0071000123456",
    "bankAccountHolder": "NGUYEN VAN AN"
  },
  "error": null
}
```

Trường chưa được nhập trả `null`.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Thiếu `employee.sensitive.read`, hoặc employee nằm ngoài scope được giao |
| 404 | `EMPLOYEE_NOT_FOUND` | Employee không tồn tại hoặc đã bị xóa mềm |

---

## PUT `/api/employees/{employeeId}/sensitive`

Cập nhật toàn bộ nhóm trường nhạy cảm. Đây là API **thay thế**, không phải vá từng phần: trường nào vắng trong body sẽ được ghi thành `null`. Muốn giữ giá trị cũ thì gửi lại đúng giá trị đó.

### Request

```json
{
  "nationalId": "079201001234",
  "personalEmail": "an.nguyen@gmail.com",
  "address": "12 Nguyễn Huệ, Quận 1, TP.HCM",
  "taxCode": "8412345678",
  "bankName": "Vietcombank",
  "bankAccountNumber": "0071000123456",
  "bankAccountHolder": "NGUYEN VAN AN"
}
```

| Field | Bắt buộc | Ràng buộc |
|:------|:--------:|:----------|
| `nationalId` | | Tối đa 30 ký tự, không trùng với employee khác |
| `personalEmail` | | Định dạng email, tối đa 100 ký tự |
| `address` | | Không giới hạn độ dài |
| `taxCode` | | Tối đa 30 ký tự |
| `bankName` | | Tối đa 150 ký tự |
| `bankAccountNumber` | | Tối đa 50 ký tự |
| `bankAccountHolder` | | Tối đa 200 ký tự |

`taxCode` là dữ liệu đầu vào của tính thuế TNCN; thiếu trường này không chặn tính lương nhưng khiến hồ sơ thuế không đầy đủ.

### Response `200 OK`

Trả về nguyên trạng thái sau khi cập nhật, cùng cấu trúc với `GET`.

### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Email sai định dạng hoặc chuỗi vượt độ dài cho phép |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Thiếu `employee.sensitive.manage`, hoặc employee nằm ngoài scope được giao |
| 404 | `EMPLOYEE_NOT_FOUND` | Employee không tồn tại hoặc đã bị xóa mềm |
| 409 | `CONFLICT` | `nationalId` đã thuộc về employee khác |
