# Tính lương, bảo hiểm và thuế TNCN

Phạm vi đang triển khai: nhân viên **cư trú tại Việt Nam**, nhận lương bằng VND, kỳ lương tháng trong năm 2026. Ngày làm việc chuẩn là thứ 2–thứ 7 trừ ngày lễ do công ty khai báo. Quy tắc có ngày hiệu lực trong database; khi sang năm hoặc có thay đổi pháp luật phải thêm quy tắc mới rồi kiểm thử trước khi tính kỳ đó. Hệ thống báo lỗi nếu không tìm thấy quy tắc phù hợp.

## Công thức

- Lương cơ bản từng ngày = lương tháng có hiệu lực × `payableMinutes` / (số ngày làm việc chuẩn của tháng × phút chuẩn của ca ngày đó).
- Phụ cấp chức vụ và thâm niên áp dụng cùng tỷ lệ phút công được trả lương. Nghỉ có lương giữ phần phụ cấp tương ứng; nghỉ không lương, nghỉ do bảo hiểm chi trả hoặc vắng không phép không được trả phần đó.
- Tăng ca = phút tăng ca **đã duyệt** × đơn giá giờ từ lương tháng và ca của ngày đó × hệ số đã duyệt. Phút công thường và phút tăng ca được tách riêng. Hệ số tối thiểu: 150% ngày thường, 200% Chủ nhật, 300% ngày lễ công ty. Chưa tính cộng thêm cho ca đêm.
- `grossPay = baseSalaryPay + positionAllowancePay + seniorityAllowancePay + overtimePay + otherEarningsPay`, trong đó `otherEarningsPay` là tổng thưởng và phụ cấp khác nhập tay (xem [Khoản cộng/trừ nhập tay](#khoản-cộngtrừ-nhập-tay)).
- BHXH/BHYT/BHTN phần nhân viên dùng **lương đóng bảo hiểm do HR nhập**, theo trạng thái tham gia và trần có hiệu lực trong tháng làm việc. Căn cứ này tách khỏi `grossPay`; tăng ca không tự động trở thành căn cứ đóng.
- Thu nhập tính thuế của bản này = `max(0, grossPay − tăng ca đã xác nhận miễn thuế − phụ cấp khác miễn thuế − BHXH − BHYT − BHTN − giảm trừ bản thân − giảm trừ người phụ thuộc)`. Người duyệt tăng ca chỉ đánh dấu `taxExempt: true` sau khi đối soát điều kiện và giới hạn pháp luật; tăng ca chưa xác nhận vẫn chịu thuế. Thuế TNCN tính theo bậc lũy tiến tại **ngày trả lương dự kiến**. Nguồn: [hướng dẫn miễn thuế tiền tăng ca năm 2026](https://media.chinhphu.vn/cac-truong-hop-tien-luong-tien-cong-duoc-mien-thue-tncn-102260715163431228.htm).
- `netPay = grossPay − BHXH − BHYT − BHTN − thuế TNCN − khấu trừ khác`. Khấu trừ khác (tạm ứng, phạt) trừ sau thuế và không làm giảm thu nhập tính thuế.

Phiếu lương lưu số tiền bảo hiểm từng loại, hai căn cứ đóng sau khi áp trần, số tăng ca đã xác nhận miễn thuế, thu nhập tính thuế, thuế, quy tắc áp dụng và `netPay`.

## Dữ liệu HR cần nhập

1. Nhập mức lương và phân công/ca như trong [ATTENDANCE.md](ATTENDANCE.md), xử lý hết `MISSING_PUNCH`.
2. HR có `compensation.manage` tạo hồ sơ qua `POST /api/compensation/employees/{employeeId}/payroll-profiles`. Ví dụ:

   ```json
   {"effectiveFrom":"2026-10-01","taxResident":true,"socialInsurance":true,"healthInsurance":true,"unemploymentInsurance":true,"insuranceSalary":12000000,"wageRegion":1}
   ```

   `GET` cùng đường dẫn cần `compensation.read`. Hồ sơ mới có ngày hiệu lực sau hồ sơ đang mở; hệ thống đóng hồ sơ cũ vào ngày trước đó. Trường `insuranceSalary` là mức lương làm căn cứ đóng do HR xác nhận theo hợp đồng, không suy ra từ `grossPay`. Nhân viên không cư trú chưa được tính tự động và sẽ nhận lỗi rõ ràng.
3. HR đăng ký người phụ thuộc qua `POST /api/compensation/employees/{employeeId}/tax-dependents`, ví dụ `{"fullName":"Nguyễn A","identifier":"012345678901","effectiveFrom":"2026-01-01"}`. `GET` để xem; `PUT /api/compensation/employees/{employeeId}/tax-dependents/{dependentId}/end` với `{"effectiveTo":"2026-12-31"}` để kết thúc. Không cho cùng tên trùng thời gian hiệu lực.
4. Kế toán có `payroll.calculate` tạo kỳ với `{"year":2026,"month":10,"taxPaymentDate":"2026-11-05"}`. Kỳ cũ thiếu ngày này có thể gọi `POST /api/payroll/periods/{id}/calculate` với body `{"taxPaymentDate":"2026-11-05"}`. Ngày trả lương phải được kiểm tra trước khi duyệt vì thuế áp dụng theo ngày nhận thu nhập. Khi gọi `POST /api/payroll/periods/{id}/mark-paid`, gửi ngày trả thực tế trong body cùng tên trường; nếu khác **tháng thuế** đã duyệt, API chặn thanh toán để đối soát. Không tính đồng thời hai kỳ có ngày trả trong cùng một tháng thuế; cần tổng hợp riêng nếu có nhiều đợt trả.

HR nhìn thấy hồ sơ bảo hiểm và người phụ thuộc ở `/payslips`; kế toán nhìn thấy ngày trả lương và các khoản khấu trừ trên phiếu. Sửa hồ sơ/người phụ thuộc ảnh hưởng kỳ `CALCULATED` đưa kỳ về `DRAFT`; kỳ `APPROVED` trở đi bị khóa.

## Khoản cộng/trừ nhập tay

Kế toán có `payroll.calculate` (phạm vi `COMPANY`) nhập thêm khoản cho từng nhân viên trong kỳ:

| Endpoint | Mục đích |
|:---------|:---------|
| `GET /api/payroll/periods/{periodId}/adjustments` | Danh sách khoản của kỳ, kèm mã và tên nhân viên |
| `POST /api/payroll/periods/{periodId}/adjustments` | Thêm khoản, trả `201` |
| `DELETE /api/payroll/periods/{periodId}/adjustments/{adjustmentId}` | Xóa khoản |

Body `POST`: `{"employeeId":2,"type":"BONUS","description":"Thưởng Tết","amount":5000000}`. `amount`
dương, tối đa 2 chữ số thập phân; `description` không rỗng, tối đa 250 ký tự.

| `type` | Ý nghĩa | Tổng thu nhập | Thu nhập tính thuế | Thực nhận |
|:-------|:--------|:-------------:|:------------------:|:---------:|
| `BONUS` | Thưởng | Cộng | Cộng | Cộng |
| `TAXABLE_ALLOWANCE` | Phụ cấp khác chịu thuế | Cộng | Cộng | Cộng |
| `NON_TAXABLE_ALLOWANCE` | Phụ cấp khác miễn thuế; kế toán chịu trách nhiệm đối chiếu điều kiện miễn | Cộng | Không | Cộng |
| `DEDUCTION` | Khấu trừ sau thuế: tạm ứng, phạt | Không | Không | Trừ |

Không khoản nào làm đổi căn cứ đóng bảo hiểm (lấy từ hồ sơ bảo hiểm do HR nhập). Khoản chỉ đi vào phiếu
lương ở lần **tính lương** tiếp theo: thêm hoặc xóa khi kỳ đang `CALCULATED` đưa kỳ về `DRAFT`; từ
`APPROVED` trở đi trả `409 PAYROLL_PERIOD_LOCKED`. Nhân viên phải còn làm việc trong kỳ (`400` với
`field: employeeId` nếu không). Khấu trừ lớn hơn số còn lại sau thuế làm việc tính lương dừng với lỗi
yêu cầu đối soát, giống trường hợp khấu trừ bảo hiểm vượt thu nhập.

Phiếu lương có thêm `otherEarningsPay`, `taxExemptOtherEarnings`, `otherDeductions`, và mỗi khoản là một
dòng trong `items` với `componentType` trùng `type`. Trên web, nút **Khoản cộng/trừ** ở mỗi kỳ trong
`/payslips` mở danh sách và form nhập.

## Xuất bảng lương ra Excel

`GET /api/payroll/periods/{id}/export` trả file `bang-luong-<năm>-<tháng>.xlsx`
(`Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, tải về qua
`Content-Disposition: attachment`). Cần `report.payroll.read` với phạm vi `COMPANY`, cùng quyền xem
kỳ lương. Client nên gửi `Accept` gồm cả kiểu xlsx và `application/json`, vì lỗi (`403`, `404
PAYROLL_PERIOD_NOT_FOUND`) vẫn trả envelope JSON như các endpoint khác.

File có một sheet, mỗi dòng một phiếu lương của kỳ, sắp theo mã nhân viên: mã, họ tên, đơn vị, địa
điểm, lương hợp đồng, phút công chuẩn/được trả/tăng ca, lương theo công, phụ cấp, tiền tăng ca, tăng
ca miễn thuế, thưởng và phụ cấp khác, phụ cấp khác miễn thuế, tổng thu nhập, BHXH, BHYT, BHTN, thu nhập tính thuế, thuế TNCN, khấu trừ khác và thực nhận. Dòng cuối
là công thức `SUM` cho các cột tiền. Dữ liệu lấy từ snapshot trên phiếu lương, nên file của kỳ đã
duyệt không đổi theo hồ sơ hiện tại. Trên web, nút **Xuất Excel** hiện ở mỗi kỳ đã tính trở đi trong
`/payslips`.

## Quy tắc 2026 đã nạp

- Từ 01/01/2026, giảm trừ bản thân 15.500.000 đồng/tháng và mỗi người phụ thuộc 6.200.000 đồng/tháng; biểu thuế lũy tiến theo tháng: 0–10 triệu 5%, 10–30 triệu 10%, 30–60 triệu 20%, 60–100 triệu 30%, trên 100 triệu 35%. Áp dụng đến 31/12/2026 trong bộ quy tắc hiện tại. Nguồn: [Luật 109/2025/QH15](https://vanban.chinhphu.vn/?classid=1&docid=216495&pageid=27160), [giới thiệu và mức giảm trừ](https://xaydungchinhsach.chinhphu.vn/gioi-thieu-luat-thue-thu-nhap-ca-nhan-so-109-2025-qh15-119260123145437408.htm), [biểu thuế](https://xaydungchinhsach.chinhphu.vn/chi-tiet-bieu-thue-thu-nhap-ca-nhan-luy-tien-tung-phan-119260327091407544.htm).
- BHXH 8%, BHYT 1,5%, BHTN 1% phần nhân viên theo trạng thái tham gia; trần BHXH/BHYT 46,8 triệu đến 30/06/2026 và 50,6 triệu từ 01/07/2026. Trần BHTN = 20 lần lương tối thiểu vùng (vùng I/II/III/IV: 5,31/4,73/4,14/3,70 triệu từ 01/01/2026). Nguồn: [BHXH Việt Nam về tỷ lệ](https://baohiemxahoi.gov.vn/congkhai/Pages/lich-lam-viec-cua-lanh-dao-nganh.aspx?CateID=0&ItemID=24900), [trần BHXH/BHYT từ tháng 7](https://baohiemxahoi.gov.vn/chidaodieuhanh/Pages/thong-tin-bao-chi.aspx?CateID=0&ItemID=26780), [trần BHTN](https://baohiemxahoi.gov.vn/tintuc/Pages/kinh-te-xa-hoi.aspx?CateID=0&ItemID=25535&OtItem=date), [mức vùng](https://baohiemxahoi.gov.vn/tintuc/pages/linh-vuc-bao-hiem-xa-hoi.aspx?CateID=168&ItemID=26630).
- Nếu bảng công có từ 14 ngày làm việc nghỉ không lương hoặc vắng không phép, chương trình dừng tính khi hồ sơ vẫn bật tham gia bảo hiểm để HR đối soát trạng thái của tháng. Không âm thầm khấu trừ sai. Nguồn: [BHXH Việt Nam về 14 ngày nghỉ không lương](https://baohiemxahoi.gov.vn/tintuc/pages/linh-vuc-bao-hiem-xa-hoi.aspx?CateID=168&ItemID=26671&OtItem=date).

## Giới hạn cần xử lý thủ công

- Chưa hỗ trợ người không cư trú, hoa hồng tính tự động, khoản giảm trừ tự nguyện khác, truy thu, hoàn thuế và quyết toán năm. Không tự động gửi tờ khai thuế hay bảo hiểm.
- Chưa tách giờ làm đêm để cộng hệ số riêng. Tăng ca Chủ nhật/ngày lễ cần chấm vào/ra và duyệt, không tự duyệt. Việc kiểm tra trần giờ làm thêm trong ngày/năm và chứng từ miễn thuế vẫn do HR thực hiện trước khi chọn `taxExempt`.
- Tính thuế theo một lần trả lương trong một tháng. Nếu ngày trả thực tế đổi tháng hoặc có thêm đợt trả/thưởng cùng tháng, kế toán phải đối soát. Kỳ đã `APPROVED` hiện không có luồng mở lại tự động; cần xử lý điều chỉnh có kiểm soát trước khi ghi nhận `PAID`.
- Hồ sơ bảo hiểm được chọn theo ngày cuối nhân viên làm việc trong kỳ; trường hợp thay đổi chế độ trong cùng tháng và các ngoại lệ luật định cần đối soát bằng tay. Dữ liệu quy tắc nạp sẵn chỉ đến hết năm 2026.
- Nếu một nhân viên thuộc kỳ nhưng toàn bộ khoảng làm việc rơi vào ngày lễ/Chủ nhật, hệ thống báo lỗi yêu cầu đối soát thủ công thay vì bỏ qua nhân viên hoặc tiền tăng ca của họ.
