# Chấm công và ngày lễ công ty

Lịch làm việc của bản này là **thứ 2 đến thứ 7**, trừ các ngày trong `company_holidays`. Ca được lấy từ phân công chính có hiệu lực vào từng ngày. `workDate` của ca qua đêm là ngày bắt đầu ca. Thời điểm lưu bằng UTC; ngày công được xác định theo `Asia/Ho_Chi_Minh`.

## Quy trình

1. Người có `organization.manage` đăng ký ngày lễ qua `POST /api/company-holidays` với body `{"date":"2026-01-01","name":"Tên ngày lễ"}`. `GET /api/company-holidays?from=...&to=...` dùng `organization.read`; `DELETE /api/company-holidays/{id}` dùng `organization.manage`. Thay đổi ngày lễ đưa kỳ lương `CALCULATED` về `DRAFT` để tính lại; từ `APPROVED` trở đi thì bị chặn.
2. HR có quyền `attendance.manage` và phạm vi `COMPANY` gọi `POST /api/attendance/prepare?year=2026&month=1`. API tạo bản ghi `MISSING_PUNCH` cho ngày làm việc chưa có dữ liệu và trả số bản ghi mới. Gọi lặp không tạo trùng. Nên chuẩn bị tháng trước khi nhân viên bắt đầu chấm công.
3. Nhân viên có `attendance.self.record` chấm vào bằng `POST /api/attendance/check-in` với `{"employeeId":1}` và chấm ra bằng `POST /api/attendance/check-out` với `{"employeeId":1,"workDate":"2026-01-15"}`. HR có `attendance.manage` cũng có thể thao tác trong phạm vi được giao. Giờ chấm do máy chủ ghi.
4. HR xử lý các ngày `MISSING_PUNCH` qua `PUT /api/attendance/{id}`. Ví dụ ngày vắng không phép: `{"workedMinutes":0,"payableMinutes":0,"lateMinutes":0,"earlyLeaveMinutes":0,"status":"UNAUTHORIZED_ABSENCE","note":"Đã đối soát"}`. Duyệt đơn nghỉ qua `POST /api/leave-requests/{id}/approve` sẽ tự gắn đơn và số phút nghỉ vào bảng công. Đơn nghỉ có lương cộng vào phút được trả lương; đơn nghỉ không lương hoặc do bảo hiểm chi trả không cộng. Đơn nửa ngày chưa có giờ chấm công vẫn cần HR đối soát `MISSING_PUNCH`.
5. Người có `attendance.overtime.approve` duyệt tăng ca qua `POST /api/attendance/{id}/overtime-approval` với `{"overtimeMinutes":60,"overtimeMultiplier":1.5,"taxExempt":false}`. Đặt `overtimeMinutes` bằng 0 để hủy duyệt. Chỉ chọn `taxExempt:true` khi HR đã xác nhận khoản tăng ca đủ điều kiện miễn thuế; mặc định là chịu thuế. Hệ thống kiểm tra số phút tăng ca không lấy từ phần công thường hoặc nghỉ có lương; hệ số tối thiểu là 1,5 ngày thường, 2 vào Chủ nhật và 3 vào ngày lễ đã khai báo.

Xem bản ghi qua `GET /api/attendance/{id}` hoặc `GET /api/attendance/employees/{employeeId}?from=...&to=...`; quyền đọc cá nhân và quyền đọc có phạm vi được kiểm tra tại service.

## Quy tắc tính

- `workedMinutes` là khoảng chấm vào/ra trừ `breakMinutes` của ca, không âm.
- `payableMinutes` là phần thời gian nằm trong ca dự kiến, trừ `breakMinutes`, tối đa bằng số phút công chuẩn; tăng ca đã duyệt được tính riêng. Giờ chấm vào Chủ nhật hoặc ngày lễ không thành phút công thường, chỉ được trả khi duyệt tăng ca.
- Kỳ lương dùng lịch thứ 2 đến thứ 7, trừ ngày lễ, và yêu cầu bản ghi công đã được xử lý cho từng ngày đó. Thiếu ngày hoặc còn `MISSING_PUNCH` thì không chuyển kỳ sang `CALCULATED`. Lương cơ bản áp dụng mức lương có hiệu lực theo từng ngày; số tiền mỗi ngày bằng lương tháng nhân phút được trả lương, chia cho số ngày làm việc chuẩn của cả tháng và số phút chuẩn của ca ngày đó. Phụ cấp chức vụ và thâm niên cũng chia theo phút được trả lương: nghỉ có lương được tính, nghỉ không lương không được tính. Cách này chia tỷ lệ khi vào làm giữa tháng.
- Từ `APPROVED`, mọi đường sửa dữ liệu công bị chặn. Nếu công thay đổi sau khi tính lương nhưng trước khi duyệt, phải tính lại trước khi duyệt.

Giới hạn hiện tại: ca chỉ có tổng `breakMinutes`, chưa có mốc bắt đầu/kết thúc nghỉ; cách trừ giờ nghỉ là cố định cho toàn ca. Mô hình chỉ lưu một cặp chấm vào/ra và một đơn nghỉ cho mỗi nhân viên mỗi ngày; đơn nghỉ nửa ngày cần đối soát phần thời gian còn lại. Chỉ có ngày lễ do công ty khai báo, không tự nhập lịch lễ quốc gia.

Xem [PAYROLL.md](PAYROLL.md) cho cách tính `grossPay`, bảo hiểm, thuế TNCN và `netPay`.

## API và giao diện

- Đơn nghỉ: `POST /api/leave-requests` tạo nháp; `POST /{id}/submit`, `/cancel`, `/approve`, `/reject`; `GET /{id}`, `GET /employees/{employeeId}` và `GET /pending` (chỉ trả đơn trong phạm vi người duyệt). Các đường dẫn con ở đây có tiền tố `/api/leave-requests`.
- Hạn mức phép năm: `GET /api/leave-entitlements/employees/{employeeId}?year=2026` xem số dư; `PUT .../adjustment` và `PUT .../carried-over` cho HR có `request.manage` điều chỉnh.
- Kỳ lương: `POST /api/payroll/periods`, `GET /api/payroll/periods`, `GET /api/payroll/periods/{id}`, `POST /api/payroll/periods/{id}/calculate|approve|mark-paid|lock|cancel`; xem phiếu qua `GET /api/payroll/employees/{employeeId}/payslips` hoặc `GET /api/payroll/payslips/{id}`. Nhân viên chỉ xem phiếu từ kỳ đã duyệt trở đi.
- Cổng HRM có `/attendance`, `/leave-requests`, `/payslips`; tất cả yêu cầu đi qua BFF allowlist và API kiểm tra quyền cùng phạm vi truy cập.

## Hoàn thiện dữ liệu trước khi tính lương

1. Tạo phân công chính và chọn ca có hiệu lực từ **ngày làm việc đầu tiên** của nhân viên. `POST /api/attendance/prepare?year=...&month=...` kiểm tra mọi nhân viên có thời gian làm việc trong tháng; nếu thiếu phân công hoặc ca ở ngày làm việc nào, API báo mã nhân viên và ngày đó. Sau khi sửa phân công, gọi lại để tạo các bản ghi `MISSING_PUNCH`.
2. HR đối soát từng `MISSING_PUNCH`: ghi nhận `PRESENT` với phút thực làm và phút tính lương, hoặc `UNAUTHORIZED_ABSENCE` với cả hai bằng 0; cần lý do điều chỉnh. Nghỉ có lương cần đi qua quy trình duyệt đơn nghỉ. Không dùng số phút tính lương để tự suy ra nhân viên đã làm việc.
3. Người có `compensation.manage` nhập mức lương tháng qua `POST /api/compensation/employees/{employeeId}/salary-history`, ví dụ `{"baseSalary":12000000,"effectiveFrom":"2026-01-01","reason":"Mức lương ban đầu"}`. Xem lịch sử bằng `GET` cùng đường dẫn với quyền `compensation.read`. Ngày hiệu lực phải phủ tất cả ngày làm việc cần tính lương. Sửa mức lương ảnh hưởng kỳ `CALCULATED` sẽ đưa kỳ về `DRAFT` và xóa phiếu cũ; kỳ đã duyệt, trả hoặc khóa thì bị chặn.
   Vai trò `HR_MANAGER` có cả hai quyền này. Từ hồ sơ nhân viên, HR chọn **Nhập và xem lương** để mở màn hình lương với nhân viên và ngày vào làm được điền sẵn.
4. HR nhập hồ sơ bảo hiểm/thuế và người phụ thuộc theo hướng dẫn trong [PAYROLL.md](PAYROLL.md). Nếu thiếu hồ sơ trong kỳ, hệ thống trả lỗi theo nhân viên thay vì giả định không tham gia bảo hiểm.
5. Tính kỳ lương chỉ thành công khi **mọi nhân viên được tuyển dụng trong kỳ** có phân công, ca, công đã xử lý, lịch sử lương và hồ sơ khấu trừ. API trả lỗi kèm mã nhân viên và dữ liệu thiếu; kỳ vẫn ở trạng thái trước đó để sửa rồi tính lại.

## Hạn mức phép năm

Trước đây đơn `ANNUAL` được duyệt không giới hạn và số phút chảy thẳng vào bảng lương. Từ V35, mỗi nhân viên có một hạn mức cho mỗi năm và `POST /api/leave-requests/{id}/approve` sẽ trả `409` nếu đơn vượt số dư còn lại.

Hạn mức tính theo quy tắc trong `leave_entitlement_rules`, bộ nạp sẵn từ 01/01/2026 là **12 ngày cơ bản, cứ đủ 5 năm làm việc cộng thêm 1 ngày** (Bộ luật Lao động 2019, Điều 113 khoản 1 và Điều 114):

- Thâm niên đếm từ `employees.seniority_start_date`, thiếu thì lấy `hire_date`, và tính theo số năm **đã tròn tại ngày 01/01 của năm phép**. Người đủ 5 năm vào tháng 6 thì được cộng ngày từ năm kế tiếp.
- Vào làm giữa năm thì chia theo số tháng còn lại của năm. Vào làm tháng 10 được 3/12 của hạn mức.
- Số ngày quy ra phút theo `standard_work_minutes` của ca trong phân công chính đang mở; không có ca thì dùng 480 phút. Giá trị này được snapshot vào `employee_leave_entitlements.standard_day_minutes` nên đổi ca sau đó không viết lại năm đã cấp.

Chỉ `ANNUAL` trừ vào số dư. `SICK`, `MATERNITY`, `UNPAID` và `OTHER` đi theo quy trình riêng và không ảnh hưởng hạn mức.

Vì vậy cách trả lương của đơn **do server suy ra từ loại nghỉ**, không nhận từ client: `ANNUAL` → `EMPLOYER_PAID`, `SICK` và `MATERNITY` → `SOCIAL_INSURANCE`, `UNPAID` và `OTHER` → `UNPAID`. Nếu client gửi `salaryTreatment` khác giá trị suy ra, API trả `400` trừ khi người gọi có `request.manage`. Không có ràng buộc này thì nhân viên chỉ cần nộp đơn `OTHER` kèm `EMPLOYER_PAID` là được trả lương đủ mà không trừ ngày phép nào. HR vẫn ghi đè được cho các trường hợp như công ty trả lương mấy ngày ốm đầu tiên hoặc nghỉ việc riêng có lương theo Điều 115.

Khi kiểm tra, các đơn `PENDING` khác được tính như đã tiêu. Nếu không làm vậy thì ba đơn cùng chờ duyệt sẽ cùng qua được kiểm tra rồi mới vượt hạn mức sau khi duyệt hết.

Bảng hạn mức **không lưu số phút đã dùng**; số đã dùng và đang chờ được tính trực tiếp từ `leave_requests`, nên không thể lệch với đơn thật.

### Điều chỉnh của HR

| Endpoint | Permission | Mục đích |
|:---------|:-----------|:---------|
| `GET /api/leave-entitlements/employees/{employeeId}` | `request.read`, hoặc chính nhân viên đó | Xem số dư; thiếu `year` thì lấy năm hiện tại |
| `PUT /api/leave-entitlements/employees/{employeeId}/adjustment` | `request.manage` | Cộng hoặc trừ hạn mức, bắt buộc có lý do |
| `PUT /api/leave-entitlements/employees/{employeeId}/carried-over` | `request.manage` | Nhập số phút chuyển từ năm trước |

Response trả `grantedMinutes` (đã gồm chuyển năm trước và điều chỉnh), `usedMinutes`, `pendingMinutes`, `remainingMinutes` và `standardDayMinutes` để client quy ra ngày.

Hệ thống **không tự chuyển số dư sang năm sau**. Luật cho phép thỏa thuận chuyển phép nên việc này do HR nhập tay qua `carried-over`.
