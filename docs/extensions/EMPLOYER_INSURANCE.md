# Mở rộng: phần bảo hiểm doanh nghiệp đóng

> Trạng thái: **chưa làm**, để ngoài phạm vi đồ án. Tài liệu này ghi lại thiết kế để sau này có thể làm tiếp.

## 1. Hiện trạng

Hệ thống chỉ tính **phần người lao động đóng**: BHXH 8%, BHYT 1,5%, BHTN 1%. Các khoản này khấu trừ vào
lương và hiện trên phiếu lương, bảng lương kỳ và báo cáo "Quỹ lương & thuế".

**Phần doanh nghiệp đóng** chưa được tính. Đây là chi phí cộng thêm ngoài lương, không trừ vào thu nhập
của nhân viên. Vì vậy hệ thống chưa trả lời được các câu hỏi:

- Tổng chi phí nhân sự thực tế của công ty mỗi tháng là bao nhiêu?
- Tháng này phải nộp cơ quan BHXH bao nhiêu (gồm cả phần người lao động và phần doanh nghiệp)?

## 2. Tỷ lệ doanh nghiệp đóng (tham khảo)

| Khoản | Tỷ lệ | Mức lương làm căn cứ |
|---|---|---|
| BHXH – hưu trí, tử tuất | 14% | Như phần người lao động, trần 20 lần lương cơ sở |
| BHXH – ốm đau, thai sản | 3% | Như trên |
| Tai nạn lao động – bệnh nghề nghiệp | 0,5% (hoặc 0,3% với ngành đủ điều kiện) | Như trên |
| BHYT | 3% | Như trên |
| BHTN | 1% | Trần 20 lần lương tối thiểu vùng |
| Kinh phí công đoàn (không phải bảo hiểm) | 2% | Mức lương đóng BHXH |

Như vậy doanh nghiệp đóng thêm khoảng **21,5%**, cộng 2% kinh phí công đoàn, so với 10,5% của người lao động.

> **Trước khi nạp dữ liệu**, phải đối chiếu lại các tỷ lệ với văn bản hiện hành: Luật BHXH 2024 (hiệu lực
> 01/07/2025), Luật BHYT, Luật Việc làm và Luật Công đoàn. Phải nạp theo **từng giai đoạn hiệu lực từ 2020**,
> giống quy tắc hiện có.

Cần chốt trước:

- Mức tai nạn lao động là 0,5% hay 0,3%?
- Có tính kinh phí công đoàn hay không?

## 3. Các task đề xuất

Làm theo thứ tự A → B → C → D. Mỗi task là một commit; sau mỗi task build và test đều phải xanh.

### Task A — Bảng tỷ lệ (DB + dữ liệu tham chiếu)

- Trong `api/src/main/resources/db/migration/V7__payroll.sql`, thêm vào bảng `payroll_insurance_rules`:
  - Các cột `employer_social_rate`, `employer_accident_rate`, `employer_health_rate`,
    `employer_unemployment_rate`, `trade_union_rate`.
  - Mở rộng CHECK `chk_payroll_insurance_rule_rates` cho các cột mới.
- Điền tỷ lệ cho mọi giai đoạn trong `V8__reference_data.sql`.
- Thêm field tương ứng vào entity `PayrollInsuranceRule`.
- Cả team chạy `docker compose down -v` (môi trường dev sửa thẳng V1–V8).

### Task B — Tính trên phiếu lương

- Thêm cột vào bảng `payslips` và entity `Payslip`: `employer_social_insurance`,
  `employer_accident_insurance`, `employer_health_insurance`, `employer_unemployment_insurance`,
  `trade_union_fee`.
- Trong `PayrollDeductionsService` / `PayrollService`, tính các khoản này trên đúng các mức lương làm căn cứ
  đã có: `insuranceSalaryBase` cho BHXH/BHYT và `unemploymentInsuranceBase` cho BHTN. Các khoản này
  **không** làm thay đổi lương thực nhận.
- Tổng chi phí doanh nghiệp = tổng thu nhập + phần doanh nghiệp đóng.
- Test trong `PayrollServiceTest` và integration test, gồm ca lương vượt trần BHXH và vượt trần BHTN.

### Task C — API

- `PayslipResponse`: thêm các khoản doanh nghiệp đóng.
- `PayrollPeriodResponse`: thêm tổng phần doanh nghiệp đóng và tổng chi phí của kỳ.
- `PayrollSummaryReportResponse.PayrollAmounts`: thêm các khoản tương ứng cho báo cáo năm.
- File Excel bảng lương kỳ (`/export`): thêm cột phần doanh nghiệp.
- Cập nhật OpenAPI snapshot (`-Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true`), chạy
  `npm run gen:api`, sửa `PAYROLL.md` và `REPORT.md`.

### Task D — Web

- Bảng lương kỳ (`components/payroll/payroll-period-page.tsx`): thêm ô tổng "Doanh nghiệp đóng" và
  "Tổng chi phí nhân sự", kèm cột trong bảng.
- Báo cáo "Quỹ lương & thuế" (`components/report/payroll-report-page.tsx`):
  - Ô "Doanh nghiệp đóng", "Tổng chi phí nhân sự", và "Phải nộp BHXH" (người lao động + doanh nghiệp).
  - Thêm cột tương ứng vào bảng và CSV.
  - Sửa câu mô tả "Bảo hiểm là phần người lao động đóng".
- Phiếu lương nhân viên tự xem (`my-payslips-page.tsx`): **giữ nguyên**, chỉ hiện phần người lao động.
- Cập nhật `docs/FEATURES.md` và `apps/web/docs/README.md`.
