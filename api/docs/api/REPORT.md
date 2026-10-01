# Report API

API báo cáo tổng hợp nhân sự. Mọi truy vấn đều áp dụng phạm vi của role assignment đang có
hiệu lực; dữ liệu nhân viên và phân công được xét tại `asOfDate`.

## Bộ lọc chung

| Query parameter | Bắt buộc | Ý nghĩa |
|---|---|---|
| `asOfDate` | Không | Ngày thống kê, mặc định hôm nay và không được ở tương lai |
| `organizationUnitId` | Không | Chỉ lấy nhân viên thuộc đơn vị tại ngày thống kê |
| `workLocationId` | Không | Chỉ lấy nhân viên thuộc địa điểm tại ngày thống kê |
| `employmentStatus` | Không | Lọc theo trạng thái hồ sơ hiện tại |

## Trình độ và thâm niên

```http
GET /api/reports/hr/workforce-distribution
Authorization: Bearer <access-token>
```

Yêu cầu permission `report.hr.read`. Response gồm tổng nhân viên, thâm niên trung bình,
phân bố trình độ và phân bố thâm niên. Hồ sơ chưa có trình độ vẫn xuất hiện trong nhóm
`NOT_UPDATED`.

## Mức lương cơ bản

```http
GET /api/reports/payroll/salary-distribution
Authorization: Bearer <access-token>
```

Yêu cầu permission `report.payroll.read`. API chỉ trả dữ liệu tổng hợp, không trả lương của
từng cá nhân. Mức lương được lấy từ bản ghi `employee_salary_history` có hiệu lực tại
`asOfDate`; nhân viên thiếu cấu hình vẫn xuất hiện trong nhóm `NOT_CONFIGURED`.

Các khoảng lương MVP:

- Dưới 10 triệu.
- 10 đến dưới 15 triệu.
- 15 đến dưới 20 triệu.
- 20 đến dưới 30 triệu.
- Từ 30 triệu.
