# Đánh giá API HRM — 2026-11-03

Chạy theo [API_REVIEW_PROMPT.md](./API_REVIEW_PROMPT.md).

## 1. Thông tin lần chạy

| Mục | Giá trị |
|---|---|
| Ngày | 2026-11-03 |
| Commit | `17df8519` refactor(rbac): drop the four permissions nothing checks |
| Test | 557 test, 0 lỗi (chạy trên DB tạm mới tạo) |
| Người chạy | Claude Code |
| Lần trước | Không có (lần chạy đầu tiên) |
| Ghi chú | Lần chạy dùng Đạt/Chưa đạt; cùng ngày đổi sang định dạng hiện tại của prompt (thang điểm 10, mã `API-<số>`), danh sách lỗi giữ nguyên |

Trước lần chạy này, cùng ngày đã sửa ba lỗi thuộc nhóm "giữa các phần": quy tắc phạm vi role theo tên role
(`90e9129e`), dữ liệu seed gán `HR_MANAGER` phạm vi `ORG_UNIT` trái quy tắc (`2275ed06`), bốn permission không có
code nào kiểm tra (`17df8519`).

## 2. Điểm

**Điểm tổng: 7,0 / 10 — Khá.** Trung bình có trọng số là 8,9 (821,5 / 92, bỏ tiêu chí 11 chưa kiểm) nhưng còn
`API-001` (Cao) chưa sửa nên áp trần 7,0.

| # | Tiêu chí | Trọng số | Điểm | Lỗi đã trừ | Bằng chứng |
|---|---|---|---|---|---|
| 1 | Phân quyền và phạm vi dữ liệu | 15% | 10 | — | Mọi method controller gọi được có `@PreAuthorize` hoặc kiểm tra riêng (`LeaveEntitlementService.getBalance` kiểm tra chính chủ hoặc phạm vi; hàng chờ duyệt lọc theo người duyệt). 47 permission trong `PermissionSeeder` khớp 1–1 với code. |
| 2 | Bảo vệ dữ liệu nhạy cảm | 10% | 10 | — | CCCD, ngân hàng chỉ có trong `EmployeeSensitiveResponse`; không có log in mật khẩu, token, CCCD, lương. |
| 3 | Quy trình duyệt đúng nghiệp vụ | 10% | 4 | `API-001` Cao −4, `API-002` TB −2 | Người duyệt lưu cứng lúc nộp đơn; `ApproverResolver` lấy phân công "mở" thay vì đang hiệu lực. |
| 4 | Toàn vẹn bảng lương | 15% | 10 | — | `PayrollPeriodInvalidator` được gọi khi đổi lương, phụ cấp, phân công, khấu trừ, ngày lễ, kết thúc làm việc; duyệt lương chặn khi chấm công sửa sau lúc tính (`PayrollService` `PAYROLL_RECALCULATION_REQUIRED`); hủy kỳ xóa phiếu lương (`PayrollService.cancel`); sửa giờ ca đã có chấm công bị chặn (`WorkShiftService.update`). Công thức thuế/bảo hiểm ngoài phạm vi tiêu chí. |
| 5 | Toàn vẹn dữ liệu và đồng thời | 10% | 9,5 | `API-006` Thấp −0,5 | Exclusion constraint cho phân công chính, lịch sử lương, phụ cấp, nghỉ phép đã duyệt, hồ sơ bảo hiểm/thuế, kỳ lương; "một Giám đốc" chỉ dựa vào khóa dòng. |
| 6 | Thời gian và hiệu lực | 8% | 9,5 | `API-005` Thấp −0,5 | Không còn `LocalDate.now()`/`Instant.now()` thiếu `Clock` ngoài seeder. Lúc chạy tiêu chí này mới chỉ kiểm `Clock` (tên cũ "Thời gian và múi giờ"). |
| 7 | Validation và lỗi trả về | 7% | 9,5 | `API-004` Thấp −0,5 | `@RequestBody` đều có `@Valid` trừ các body duyệt/từ chối tùy chọn; ý kiến duyệt không có `@Size`. |
| 8 | Dữ liệu seed khớp quy tắc | 5% | 8 | — | Kiểm một phần → tối đa 8: seeder ghi thẳng repository, mới riêng phạm vi role được kiểm (`StaffSeeder`, `SeedRoleRequestWriter` gọi `RoleAssignmentPolicy.validateScope`). |
| 9 | Kiểm thử | 8% | 8 | `API-003` TB −2 | 557 test pass. Không tìm thấy test tham chiếu theo tên: `AccountActivationService`, `EmployeeProvisioningService`, `JobPositionService`, `OrganizationCompanyOwnerProvisioningService`, `RoleAssignmentOptionService`, `ApiTrafficService` (có thể được phủ qua test endpoint hoặc E2E). |
| 10 | Bảo trì và tài liệu | 4% | 10 | — | Không có TODO/FIXME. Tài liệu ghi sai số permission (48/51 → 47) và tổng permission mỗi role đã sửa trong ngày. |
| 11 | Vòng đời và tham chiếu | 8% | Chưa kiểm | — | Tiêu chí được thêm sau lần chạy này. |

## 3. Danh sách lỗi

### API-001 — Đơn bị kẹt khi người duyệt nghỉ việc hoặc đổi quản lý

- **Mức độ:** Cao
- **Thuộc tiêu chí:** 3
- **Vị trí:** `service/leave/LeaveRequestService.java:137`, `service/employee/ResignationRequestService.java:82`,
  `service/employee/EmploymentLifecycleService.java` (đoạn khóa tài khoản khi kết thúc làm việc)
- **Mô tả:** Đơn nghỉ phép và đơn nghỉ việc lưu `approverEmployee` lúc nộp và không bao giờ đổi. Khi người duyệt
  nghỉ việc, tài khoản của họ bị khóa nhưng đơn họ đang phải duyệt không được chuyển cho người khác.
- **Tình huống:** Nhân viên A nộp đơn nghỉ, người duyệt là quản lý B. B nghỉ việc trước khi duyệt → đơn của A ở
  `PENDING` mãi; HR không có quyền duyệt thay; A phải tự hủy rồi nộp lại.
- **Đối chiếu:** Tăng ca không bị lỗi này vì người duyệt được tính lại mỗi lần (`OvertimeService` dùng
  `ApproverResolver.decidedBy`).
- **Đề xuất:** Tính lại người duyệt lúc duyệt (như tăng ca); cột `approverEmployee` chỉ để hiển thị/lọc hàng chờ và
  được cập nhật khi người duyệt cũ không còn hợp lệ. Thêm test cho tình huống quản lý nghỉ việc.
- **Tìm ra nhờ:** ngoài tiêu chí
- **Trạng thái:** Chưa sửa

### API-002 — Chuyển công tác hẹn ngày tương lai làm sai người duyệt từ hôm nay

- **Mức độ:** Trung bình
- **Thuộc tiêu chí:** 3
- **Vị trí:** `service/employee/ApproverResolver.java:64`
- **Mô tả:** `ApproverResolver` lấy quản lý từ phân công chính **không có ngày kết thúc**
  (`findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull`), không phải phân công **đang hiệu lực hôm nay**.
  `EmployeeAssignmentService.assign` cho phép hẹn ngày tương lai: phân công hiện tại đóng ở ngày hôm trước, phân
  công mới mở từ ngày tương lai.
- **Tình huống:** Hôm nay tạo đợt chuyển nhân viên sang phòng khác từ ngày 1 tháng sau → đơn nghỉ nộp hôm nay đi tới
  quản lý mới. Kiểm tra phạm vi (`EmployeeAccessScopeService`) lại xét theo ngày nên hai nơi không nhất quán.
- **Đề xuất:** Lấy phân công chính đang hiệu lực tại ngày hôm nay. Thêm test chuyển công tác hẹn ngày tương lai.
- **Tìm ra nhờ:** ngoài tiêu chí
- **Trạng thái:** Chưa sửa

### API-003 — Không có test giữ các phần khớp nhau

- **Mức độ:** Trung bình (là nguồn gốc của các lỗi đã sửa trong ngày)
- **Thuộc tiêu chí:** 9
- **Vị trí:** `src/test`
- **Mô tả:** Danh mục permission và code đang khớp (kiểm tay trong lần chạy này), nhưng không có test nào bảo đảm
  điều đó. Dữ liệu seed chỉ được kiểm phần phạm vi role.
- **Tình huống:** Thêm một permission vào `PermissionSeeder` mà quên dùng, hoặc dùng `hasAuthority('x.y')` mà quên
  khai báo → không test nào báo; sửa quy tắc nghiệp vụ mà `staff.json` vi phạm → app vẫn chạy với dữ liệu sai.
- **Đề xuất:** (a) Test so danh mục permission với mọi mã permission dùng trong code, hai chiều. (b) Test sau khi seed:
  mọi lần gán role hợp lệ theo `RoleAssignmentPolicy`, phép năm đã dùng không vượt hạn mức.
- **Tìm ra nhờ:** tiêu chí 9
- **Trạng thái:** Chưa sửa

### API-004 — Ý kiến duyệt/từ chối không giới hạn độ dài

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 7
- **Vị trí:** `controller/LeaveRequestController.java:96` (`ReviewBody`), `controller/ResignationRequestController.java:87-89`
- **Mô tả:** `comment` không có `@Size`; cột `review_comment` là `TEXT` nên không gây lỗi 500, nhưng không có giới hạn
  để web đối chiếu.
- **Đề xuất:** Thêm `@Size` và giới hạn tương ứng trên web.
- **Tìm ra nhờ:** tiêu chí 7
- **Trạng thái:** Chưa sửa

### API-005 — Số giờ một ngày nghỉ lấy theo ca mới nhất

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 6
- **Vị trí:** `service/leave/LeaveEntitlementService.java:176-183` (`standardDayMinutes`)
- **Mô tả:** Cùng loại với `API-002`: dùng ca của phân công mở, không phải ca đang hiệu lực. Chỉ lệch khi có đợt chuyển
  hẹn ngày tương lai sang ca có số giờ chuẩn khác.
- **Đề xuất:** Dùng phân công chính đang hiệu lực, sửa cùng `API-002`.
- **Tìm ra nhờ:** ngoài tiêu chí
- **Trạng thái:** Chưa sửa

### API-006 — "Một Giám đốc tại một thời điểm" không có ràng buộc DB

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 5
- **Vị trí:** `service/rbac/AccountRoleAssignmentCommandService.java` (`validateCandidate`, `ensureSingleDirector`)
- **Mô tả:** Bảng `account_role_assignments` không có exclusion constraint; bất biến chỉ dựa vào `SELECT … FOR UPDATE`
  trên account và role. Đúng khi mọi đường gán đi qua service này; ghi trực tiếp DB (seed, script) không được chặn.
- **Đề xuất:** Chấp nhận được ở quy mô hiện tại; nếu cần chặt hơn, thêm exclusion constraint có điều kiện cho role
  `DIRECTOR`.
- **Tìm ra nhờ:** tiêu chí 5
- **Trạng thái:** Chưa sửa (có thể giữ nguyên)

## 4. So sánh với lần trước

Lần chạy đầu tiên, chưa có dữ liệu so sánh.

## 5. Phạm vi chưa kiểm

- Công thức trong `PayslipCalculator` (thuế TNCN, bảo hiểm) chưa đối chiếu lại với quy định 2026; đã có test riêng.
- Dữ liệu `staff.json` chưa được kiểm toàn bộ theo mọi quy tắc (chỉ phạm vi role); cần `API-003`(b) để kiểm tự động.
- Web (`apps/web`) ngoài phạm vi; chỉ ghi nhận: dev server (`pnpm dev`) trả 404 cho mọi route BFF dạng
  `[...path]`, bản build (`next build` + `next start`) chạy đúng.
