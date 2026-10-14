# API Reference — Allowance policy

Chính sách phụ cấp chức vụ và phụ cấp thâm niên dùng chung cho cả công ty. Kỳ lương đọc các quy tắc này khi tính `allowancePay`; công thức, kỳ lương và phiếu lương nằm trong [PAYROLL.md](PAYROLL.md#công-thức).

Phụ cấp chức vụ đặt theo chức danh, phụ cấp thâm niên đặt theo bậc số năm; cả hai dùng chung cho mọi nhân viên. Dữ liệu seed tạo sẵn chính sách mẫu (xem [ORGANIZATION_STRUCTURE.md](../../../docs/ORGANIZATION_STRUCTURE.md#7-seeder-hiện-có)).

- **Phụ cấp chức vụ** mỗi ngày công = mức tháng của chức danh trong phân công chính ngày đó, trả theo tỷ lệ phút công được trả lương như lương cơ bản.
- **Phụ cấp thâm niên** mỗi ngày công = lương cơ bản tháng × % của bậc ứng với số năm tròn tính từ `seniorityStartDate` của nhân viên, cũng theo tỷ lệ phút công.
- Không có quy tắc phù hợp thì khoản đó bằng 0.

Đổi chính sách **không** đưa kỳ `CALCULATED` về `DRAFT` và không bị chặn bởi kỳ đã duyệt; nếu ngày hiệu lực rơi vào kỳ đã tính thì kế toán phải tính lại kỳ đó. Phiếu đã duyệt giữ nguyên số tiền đã tính.

---

## Endpoint access

| Endpoint | Quyền | Mô tả |
|:---------|:------|:------|
| `GET /api/compensation/position-allowances` | `compensation.read` | Mọi chức danh kèm phụ cấp đang áp dụng hôm nay |
| `GET /api/compensation/job-positions/{jobPositionId}/allowances` | `compensation.read` | Lịch sử phụ cấp chức vụ của một chức danh |
| `POST /api/compensation/job-positions/{jobPositionId}/allowances` | `compensation.manage` | Đặt mức phụ cấp chức vụ mới |
| `GET /api/compensation/seniority-allowances` | `compensation.read` | Các bậc phụ cấp thâm niên |
| `POST /api/compensation/seniority-allowances` | `compensation.manage` | Đặt bậc phụ cấp thâm niên mới |

Chính sách áp dụng chung cho cả công ty nên chỉ xét permission, không xét scope. Role mặc định có `compensation.read` và `compensation.manage`: `HR_MANAGER`, `PAYROLL_ACCOUNTANT`. Thiếu token trả `401 UNAUTHORIZED`, thiếu permission trả `403 FORBIDDEN`.

---

## Đối tượng dùng chung

Tiền là số thập phân VND; ngày dạng `YYYY-MM-DD`; thời điểm là ISO-8601 UTC.

### `PositionAllowanceView` — phụ cấp chức vụ

```json
{
  "id": 2,
  "jobPositionId": 2,
  "monthlyAmount": 1000000.00,
  "effectiveFrom": "2024-10-14",
  "effectiveTo": null,
  "approvedByAccountId": 4,
  "note": "Chính sách mẫu",
  "createdAt": "2024-10-13T17:00:00Z"
}
```

| Field | Mô tả |
|:------|:------|
| `monthlyAmount` | Phụ cấp tháng cho người giữ chức danh, trả theo tỷ lệ công như lương cơ bản |
| `effectiveFrom` / `effectiveTo` | Khoảng hiệu lực; `effectiveTo` là `null` với mức đang áp dụng |
| `approvedByAccountId` | Account đã đặt mức phụ cấp |

### `SeniorityAllowanceView` — bậc phụ cấp thâm niên

```json
{
  "id": 1,
  "minYears": 1,
  "maxYears": 3,
  "percentage": 3.0000,
  "effectiveFrom": "2024-10-14",
  "effectiveTo": null,
  "approvedByAccountId": 4,
  "note": "Chính sách mẫu",
  "createdAt": "2024-10-13T17:00:00Z"
}
```

| Field | Mô tả |
|:------|:------|
| `minYears` / `maxYears` | Áp dụng khi số năm thâm niên tròn `≥ minYears` và `< maxYears`; `maxYears` là `null` nghĩa là không giới hạn trên |
| `percentage` | % lương cơ bản tháng, ví dụ `3` là 3% |
| `effectiveFrom` / `effectiveTo` | Khoảng hiệu lực; `effectiveTo` là `null` với bậc đang áp dụng |

---

## Phụ cấp chức vụ

### GET `/api/compensation/position-allowances`

Mọi chức danh chưa bị xóa, sắp theo tên, kèm mức phụ cấp có hiệu lực **hôm nay** (giờ Việt Nam). Dùng cho màn hình chính sách phụ cấp; chỉ cần `compensation.read`, không cần `organization.read`.

```json
[
  {
    "jobPositionId": 2,
    "code": "HR_SPECIALIST",
    "title": "Chuyên viên nhân sự",
    "active": true,
    "currentMonthlyAmount": 1000000.00,
    "currentEffectiveFrom": "2024-10-14"
  },
  {
    "jobPositionId": 8,
    "code": "GENERAL_STAFF",
    "title": "Nhân viên",
    "active": true,
    "currentMonthlyAmount": null,
    "currentEffectiveFrom": null
  }
]
```

| Field | Mô tả |
|:------|:------|
| `active` | Chức danh đang dùng; chức danh tạm tắt không đặt được mức mới |
| `currentMonthlyAmount`, `currentEffectiveFrom` | Mức đang áp dụng và ngày bắt đầu; `null` khi hôm nay chức danh không có phụ cấp. Mức đã đặt cho ngày sau không hiện ở đây, xem lịch sử của chức danh |

Lỗi: `403 FORBIDDEN` nếu không có `compensation.read`.

### GET `/api/compensation/job-positions/{jobPositionId}/allowances`

Lịch sử phụ cấp của chức danh, mới nhất trước. Mỗi phần tử là [`PositionAllowanceView`](#positionallowanceview--phụ-cấp-chức-vụ); chức danh chưa có phụ cấp trả mảng rỗng.

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 403 | `FORBIDDEN` | Không có `compensation.read` |
| 404 | `JOB_POSITION_NOT_FOUND` | Chức danh không tồn tại |

### POST `/api/compensation/job-positions/{jobPositionId}/allowances`

Đặt mức phụ cấp mới cho chức danh. Mức đang áp dụng (nếu có) tự kết thúc vào ngày trước `effectiveFrom`.

#### Request

```json
{
  "monthlyAmount": 1500000,
  "effectiveFrom": "2026-11-01",
  "note": "Tăng phụ cấp"
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `monthlyAmount` | number | ✅ | `≥ 0`; đặt `0` để bỏ phụ cấp từ ngày đó |
| `effectiveFrom` | date | ✅ | Sau ngày bắt đầu của mức đang áp dụng |
| `note` | string | | Chuỗi trắng lưu `null` |

#### Response `201 Created`

Trả [`PositionAllowanceView`](#positionallowanceview--phụ-cấp-chức-vụ) vừa tạo.

#### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu hoặc âm `monthlyAmount`, thiếu `effectiveFrom`, hoặc `effectiveFrom` không sau ngày bắt đầu của mức hiện tại |
| 403 | `FORBIDDEN` | Không có `compensation.manage` |
| 404 | `JOB_POSITION_NOT_FOUND` | Chức danh không tồn tại hoặc đã ngừng dùng |

---

## Phụ cấp thâm niên

### GET `/api/compensation/seniority-allowances`

Mọi bậc thâm niên, mới nhất trước rồi theo `minYears` tăng dần. Mỗi phần tử là [`SeniorityAllowanceView`](#seniorityallowanceview--bậc-phụ-cấp-thâm-niên). Lỗi: `403 FORBIDDEN` nếu không có `compensation.read`.

### POST `/api/compensation/seniority-allowances`

Đặt bậc thâm niên mới. Mọi bậc đang áp dụng có khoảng năm chồng với bậc mới tự kết thúc vào ngày trước `effectiveFrom`.

#### Request

```json
{
  "minYears": 5,
  "maxYears": null,
  "percentage": 8,
  "effectiveFrom": "2027-01-01",
  "note": null
}
```

| Field | Type | Bắt buộc | Ràng buộc |
|:------|:-----|:--------:|:----------|
| `minYears` | integer | ✅ | `≥ 0` |
| `maxYears` | integer | | `> minYears`; bỏ trống nếu không giới hạn trên |
| `percentage` | number | ✅ | `≥ 0` |
| `effectiveFrom` | date | ✅ | Sau ngày bắt đầu của mọi bậc đang áp dụng bị chồng |
| `note` | string | | Chuỗi trắng lưu `null` |

Bậc mới chỉ đóng các bậc chồng khoảng năm; muốn đổi cách chia bậc (ví dụ gộp `1–3` và `3–5`) thì gửi một bậc phủ cả hai khoảng.

#### Response `201 Created`

Trả [`SeniorityAllowanceView`](#seniorityallowanceview--bậc-phụ-cấp-thâm-niên) vừa tạo.

#### Lỗi

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu/âm `minYears` hoặc `percentage`, `maxYears ≤ minYears`, thiếu `effectiveFrom`, hoặc `effectiveFrom` không sau ngày bắt đầu của bậc bị chồng |
| 403 | `FORBIDDEN` | Không có `compensation.manage` |
