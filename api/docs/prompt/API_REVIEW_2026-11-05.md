# Đánh giá API HRM — 2026-11-05

Chạy theo [API_REVIEW_PROMPT.md](./API_REVIEW_PROMPT.md).

## 1. Thông tin lần chạy

| Mục | Giá trị |
|---|---|
| Ngày | 2026-11-05 |
| Commit | `9e83a5e7` fix: cap dependent names at 40 characters and bound every free-text input |
| Test | 584 test, 0 lỗi (`./mvnw clean test` trên DB tạm `hrm_rev_tmp`, đã xóa sau khi chạy) |
| Người chạy | Claude Code |
| Lần trước | [API_REVIEW_2026-11-03.md](./API_REVIEW_2026-11-03.md) (commit `17df8519`) |
| Kiểm chứng chạy thật | API chạy trên DB tạm mới seed (cổng 18080) để tái hiện `API-001`, `API-002`, `API-007`, `API-008`, `API-009`; DB đã xóa |

## 2. Điểm

**Điểm tổng: 7,0 / 10 — Khá.** Trung bình có trọng số là 8,7 (870,5 / 100, đủ 11 tiêu chí) nhưng `API-001` (Cao)
vẫn chưa sửa nên áp trần 7,0.

| # | Tiêu chí | Trọng số | Điểm | Lỗi đã trừ | Bằng chứng |
|---|---|---|---|---|---|
| 1 | Phân quyền và phạm vi dữ liệu | 15% | 10 | — | Mọi lời gọi service trong 32 controller đi tới method có `@PreAuthorize` (trên method hoặc class qua `@CanManageRbac`, `@CanManagePermissionOverrides`), trừ endpoint công khai (đăng nhập, refresh, kích hoạt, branding) và method kiểm tra riêng (`LeaveEntitlementService.getBalance` chính chủ hoặc phạm vi; `CompanyProfileService.get` chỉ cần đăng nhập). Thao tác theo nhân viên gọi `requireEmployeeAccess`/`accessibleEmployees`; kỳ lương, danh mục tổ chức, ngày lễ, phụ cấp, chuẩn bị công tháng gọi `requireCompanyWide`. Permission `companyWide`: override `GRANT` ngoài `COMPANY` bị chặn (`AccountPermissionOverrideService.java:275-285`), thêm permission toàn công ty vào role đang gán hẹp bị chặn (`RoleService.java:199-200, 282-283`). `PermissionCatalogueTest` pass (47 permission). |
| 2 | Bảo vệ dữ liệu nhạy cảm | 10% | 10 | — | CCCD, mã số thuế, ngân hàng chỉ có trong `EmployeeSensitiveResponse` (`employee.sensitive.read` + phạm vi); lương chỉ ở lịch sử lương (`compensation.read`), phiếu lương và báo cáo lương. 10 câu log ngoài seeder không in mật khẩu, token, CCCD, lương; `show-sql=false`. |
| 3 | Quy trình duyệt đúng nghiệp vụ | 10% | 4 | `API-001` Cao −4, `API-002` TB −2 | Cả hai lỗi tái hiện được trên API chạy thật (xem từng lỗi). Trạng thái đơn chuyển hợp lệ: `requireStatus`/`requirePending` khóa dòng đơn trước khi đổi. |
| 4 | Toàn vẹn bảng lương | 15% | 10 | — | `PayrollPeriodInvalidator` được gọi khi đổi phân công, lương, phụ cấp chức vụ/thâm niên, hồ sơ thuế, người phụ thuộc, khoản cộng/trừ, ngày lễ, kết thúc làm việc lùi ngày. Duyệt chặn khi chấm công sửa sau lúc tính (`PayrollService.java:177-181`); tách người tính/người duyệt ở service (`:183-187`) và DB (`chk_payroll_approver_separation`); hủy và hủy duyệt xóa phiếu (`:141, :210, :295`). |
| 5 | Toàn vẹn dữ liệu và đồng thời | 10% | 9 | `API-006` Thấp −0,5, `API-010` Thấp −0,5 | Exclusion constraint cho phân công chính, lương, phụ cấp, hồ sơ thuế, người phụ thuộc, quy tắc thuế/bảo hiểm, override, đề xuất cấp role, đơn nghỉ đã duyệt; unique cho kỳ lương theo tháng, đơn nghỉ việc mở, ngày công. Hai bất biến chỉ nằm trong code: "một Giám đốc"/không trùng lần gán role (`API-006`) và "một kỳ cho mỗi tháng trả lương" (`API-010`). |
| 6 | Thời gian và hiệu lực | 8% | 7,5 | `API-005` Thấp −0,5, `API-007` TB −2 | `grep "\.now("` ngoài `config/seed`: mọi chỗ đều `now(clock)`. `EffectiveToIsNull` trong `service/` còn 4 chỗ: `ApproverResolver.java:64` (`API-002`), `LeaveEntitlementService.java:178` (`API-005`), `EmploymentLifecycleService.java:181, 217` (`API-007`). Các truy vấn `…OpenForUpdate` (lương, phụ cấp, phân công) dùng đúng cho mẫu đóng dòng cũ/mở dòng mới. |
| 7 | Validation và lỗi trả về | 7% | 9,5 | `API-009` Thấp −0,5 | `@RequestBody` thiếu `@Valid` chỉ có hai body tùy chọn không mang ràng buộc nào (`PayrollController.java:74, 91`). Mọi trường chuỗi của request DTO/command có `@Size` (trừ tham số tìm kiếm, không ghi DB), không trường nào vượt cột DB. 92/93 `ErrorCode` được dùng và đều có bản dịch trên web. Thiếu tham số bắt buộc hoặc sai `Content-Type` trả 500 (`API-009`). |
| 8 | Dữ liệu seed khớp quy tắc | 5% | 8 | `API-015` Thấp −0,5 | Kiểm một phần → tối đa 8. Seeder gọi `RoleAssignmentPolicy.validateScope`, `LeaveEntitlementService.requireSufficientBalance`, `LeaveAttendanceService.applyApprovedLeave` và `PayrollService` (tạo, tính, duyệt, trả, khóa); `SeedDataIntegrationTest` chạy mọi seeder trên DB trống. Truy vấn DB dev `hrm`: 0 đơn chờ duyệt có người duyệt đã nghỉ/khóa, 0 đơn chờ duyệt có người duyệt khác quản lý hiện tại, 0 phân công đang chạy có quản lý đã nghỉ. Người duyệt trong seed chọn bằng logic riêng (`API-015`). |
| 9 | Kiểm thử | 8% | 8 | `API-011` TB −2 | 584 test pass. Có test giữ các phần khớp nhau: `PermissionCatalogueTest` (permission ↔ code), `SeedDataIntegrationTest` (seed ↔ quy tắc), `OpenApiSnapshotTest` (DTO ↔ `openapi.json`; sinh lại `api-schema.ts` cho kết quả giống hệt file đã commit). Bốn service không có test nào, kể cả theo đường dẫn endpoint trong `src/test` và `apps/web/e2e` (`API-011`). |
| 10 | Bảo trì và tài liệu | 4% | 8,5 | `API-012`, `API-013`, `API-014` Thấp −0,5 mỗi lỗi | Không có TODO/FIXME. Số permission (47), module (8), system role (8), bảng (33) trong tài liệu khớp code. Lệch: 24 link và 4 tên method trong `concepts/` trỏ vào code trước khi tách package (`API-012`); vài mô tả và số liệu sai (`API-013`); một `ErrorCode` không dùng (`API-014`). |
| 11 | Vòng đời và tham chiếu | 8% | 9,5 | `API-008` Thấp −0,5 | Kết thúc làm việc: khóa account, thu hồi refresh token và role nghiệp vụ, hủy đề xuất cấp role đang chờ, hủy đơn nghỉ sau ngày cuối, đóng lương/hồ sơ thuế/người phụ thuộc (`EmploymentLifecycleService.java:206-228`, `AccountRoleAssignmentCommandService.java:123-130`). Xóa role tự tạo thu hồi lần gán và hủy đề xuất (`:143-151`). Xóa đơn vị, địa điểm, chức danh, ca bị chặn khi còn phân công hiện tại/tương lai, lần gán role, đề xuất chờ. Không kích hoạt lại được account của người đã nghỉ (`AccountAdminService.java:157-160`). Phân công còn trỏ tới quản lý đã nghỉ thì `ApproverResolver` bỏ qua và chuyển lên Giám đốc. Đơn chờ người duyệt đã nghỉ: xem `API-001` (tiêu chí 3). |

## 3. Danh sách lỗi

### API-001 — Đơn bị kẹt khi người duyệt nghỉ việc hoặc bị khóa tài khoản

- **Mức độ:** Cao
- **Thuộc tiêu chí:** 3
- **Vị trí:** `service/leave/LeaveRequestService.java:133-138, 321-325`,
  `service/employee/ResignationRequestService.java:72-83, 240-244`,
  `service/employee/EmploymentLifecycleService.java:206-228`, `service/account/AccountAdminService.java:131-148, 178-200`
- **Mô tả:** Đơn nghỉ phép và đơn nghỉ việc lưu `approverEmployee` lúc nộp và không bao giờ đổi; chỉ người đó duyệt
  được. Khi người duyệt kết thúc làm việc, bị tạm khóa hoặc xóa tài khoản, không bước nào chuyển đơn họ đang giữ
  cho người khác.
- **Tình huống (tái hiện 2026-11-05):** HR kết thúc làm việc của `branch-hn` (NV0005) ngày 2026-11-05 → đơn nghỉ
  PENDING #23 (NV0006) và #28 (NV0008) vẫn có người duyệt NV0005 (`RESIGNED`, account `DISABLED`); không ai duyệt
  được, nhân viên phải tự hủy rồi nộp lại. `APPROVAL_ROUTING.md` §2 ghi nhận đây là giới hạn đã biết.
- **Đề xuất:** Tính lại người duyệt lúc duyệt như tăng ca (`OvertimeService` dùng `ApproverResolver`), hoặc khi người
  duyệt kết thúc làm việc/bị khóa thì chuyển các đơn PENDING của họ sang người duyệt mới. Thêm test tình huống trên.
- **Tìm ra nhờ:** ngoài tiêu chí (lần 2026-11-03)
- **Trạng thái:** Chưa sửa

### API-002 — Chuyển công tác hẹn ngày tương lai làm sai người duyệt từ hôm nay

- **Mức độ:** Trung bình
- **Thuộc tiêu chí:** 3
- **Vị trí:** `service/employee/ApproverResolver.java:63-66`
- **Mô tả:** Quản lý trực tiếp lấy từ phân công chính **không có ngày kết thúc**
  (`findFirstByEmployeeIdAndIsPrimaryTrueAndEffectiveToIsNull`), không phải phân công **đang hiệu lực hôm nay**.
- **Tình huống (tái hiện 2026-11-05):** HR hẹn chuyển NV0021 từ 2026-12-01 sang quản lý NV0002; phân công hôm nay
  vẫn có quản lý NV0004. NV0021 nộp đơn nghỉ hôm nay → đơn #55 có người duyệt NV0002.
- **Đề xuất:** Lấy phân công chính đang hiệu lực hôm nay (`findCurrentPrimary(employeeId, LocalDate.now(clock))`).
  Thêm test chuyển công tác hẹn ngày tương lai.
- **Tìm ra nhờ:** ngoài tiêu chí (lần 2026-11-03)
- **Trạng thái:** Chưa sửa

### API-003 — Không có test giữ các phần khớp nhau

- **Mức độ:** Trung bình
- **Thuộc tiêu chí:** 9
- **Vị trí:** `src/test`
- **Mô tả:** Không có test bảo đảm danh mục permission khớp code và dữ liệu seed theo đúng quy tắc.
- **Tìm ra nhờ:** tiêu chí 9
- **Trạng thái:** Đã sửa `06cd73dc` (`PermissionCatalogueTest`, `SeedDataIntegrationTest`)

### API-004 — Ý kiến duyệt/từ chối không giới hạn độ dài

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 7
- **Vị trí:** `controller/LeaveRequestController.java:97`, `controller/ResignationRequestController.java:88-90`
- **Tìm ra nhờ:** tiêu chí 7
- **Trạng thái:** Đã sửa `477a2b75` (`@Size(max = 100)`)

### API-005 — Số giờ một ngày nghỉ lấy theo ca mới nhất

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 6
- **Vị trí:** `service/leave/LeaveEntitlementService.java:176-183` (`standardDayMinutes`)
- **Mô tả:** Cùng loại với `API-002`: dùng ca của phân công mở, không phải ca đang hiệu lực. Chỉ lệch khi có đợt
  chuyển hẹn ngày tương lai sang ca có số giờ chuẩn khác.
- **Đề xuất:** Dùng phân công chính đang hiệu lực, sửa cùng `API-002`.
- **Tìm ra nhờ:** ngoài tiêu chí (lần 2026-11-03)
- **Trạng thái:** Chưa sửa

### API-006 — "Một Giám đốc tại một thời điểm" không có ràng buộc DB

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 5
- **Vị trí:** `service/rbac/AccountRoleAssignmentCommandService.java:617-641` (`ensureSingleDirector`,
  `ensureCompanyOwnerDoesNotExist`), `db/migration/V3__rbac.sql` (bảng `account_role_assignments`)
- **Mô tả:** Bảng `account_role_assignments` không có exclusion constraint; "một Giám đốc", "một Company Owner" và
  "không trùng lần gán cùng role và scope" chỉ dựa vào `SELECT … FOR UPDATE` trong service. Đúng khi mọi đường gán đi
  qua service này; ghi trực tiếp DB (seed, script) không bị chặn.
- **Đề xuất:** Chấp nhận được ở quy mô hiện tại; nếu cần chặt hơn, thêm exclusion constraint có điều kiện.
- **Tìm ra nhờ:** tiêu chí 5
- **Trạng thái:** Chưa sửa (có thể giữ nguyên)

### API-007 — Không kết thúc làm việc được trước ngày của đợt chuyển công tác đã hẹn

- **Mức độ:** Trung bình
- **Thuộc tiêu chí:** 6
- **Vị trí:** `service/employee/EmploymentLifecycleService.java:181-182, 217-218, 285-292`;
  `service/employee/EmployeeAssignmentService.java:89-98`; `controller/EmployeeAssignmentController.java` (chỉ có
  GET/POST)
- **Mô tả:** `scheduleEnd` và `endEmploymentAs` lấy phân công chính "mở" rồi đòi ngày làm việc cuối không trước ngày
  bắt đầu của nó. Khi đã hẹn chuyển công tác, phân công mở là phân công **tương lai**, nên mọi ngày cuối trước ngày
  chuyển đều bị từ chối. Không có API xóa hay sửa phân công tương lai, và phân công mới chỉ được bắt đầu sau ngày
  chuyển, nên không có cách gỡ.
- **Tình huống (tái hiện 2026-11-05):** HR hẹn chuyển NV0021 từ 2026-12-01. Sau đó:
  - HR kết thúc làm việc ngày 2026-11-20 (hẹn) hoặc 2026-11-05 (ngay) → `400 VALIDATION_ERROR` "terminationDate must
    not be before the current assignment start date".
  - NV0021 nộp đơn nghỉ việc, ngày cuối 2026-11-20; người duyệt bấm duyệt → cùng lỗi 400, `field` là
    `terminationDate`, trường không có trong form duyệt.

  Phải đợi tới ngày chuyển và chọn ngày cuối từ 2026-12-01 trở đi.
- **Đề xuất:** Khi kết thúc làm việc, xóa các phân công chính bắt đầu sau ngày cuối (chúng chưa từng có hiệu lực) và
  đóng phân công đang hiệu lực tại ngày cuối. Thêm test: hẹn chuyển rồi kết thúc/duyệt đơn nghỉ việc trước ngày chuyển.
- **Tìm ra nhờ:** tiêu chí 6 (`grep EffectiveToIsNull`)
- **Trạng thái:** Chưa sửa

### API-008 — Đơn nghỉ việc chờ duyệt vẫn treo sau khi HR kết thúc làm việc

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 11
- **Vị trí:** `service/employee/EmploymentLifecycleService.java:206-228`,
  `service/employee/ResignationRequestService.java:104-106, 147-156`
- **Mô tả:** `endEmploymentAs` hủy đơn nghỉ phép sau ngày cuối nhưng không đụng tới đơn nghỉ việc `PENDING` của chính
  người đó (chỉ `cancelScheduledEnd` xử lý đơn nghỉ việc, và chỉ đơn `APPROVED`).
- **Tình huống (tái hiện 2026-11-05):** NV0007 có đơn nghỉ việc PENDING (ngày cuối 2026-12-05). HR chấm dứt hợp đồng
  NV0007 ngày 2026-11-05 → đơn vẫn PENDING, vẫn nằm trong `GET /api/resignation-requests/pending` của người duyệt
  NV0005; bấm duyệt → `409 EMPLOYMENT_ALREADY_ENDED`. Chỉ gỡ được bằng cách "từ chối" (bắt buộc lý do) hoặc HR hủy.
- **Đề xuất:** Trong `endEmploymentAs`, chuyển đơn nghỉ việc PENDING sang `CANCELLED` kèm ý kiến, như
  `cancelOpenRequestsAfter` làm với đơn nghỉ phép.
- **Tìm ra nhờ:** tiêu chí 11
- **Trạng thái:** Chưa sửa

### API-009 — Thiếu tham số bắt buộc hoặc sai `Content-Type` trả 500

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 7
- **Vị trí:** `exception/GlobalExceptionHandler.java:191-200` (handler `Exception.class` bắt hết)
- **Mô tả:** `GlobalExceptionHandler` không xử lý `MissingServletRequestParameterException` và
  `HttpMediaTypeNotSupportedException`, nên handler bắt mọi exception trả `500 INTERNAL_ERROR` và ghi log `ERROR`
  "Unhandled exception". Ảnh hưởng mọi `@RequestParam` bắt buộc: `AttendanceController.java:46-47, 69-70, 87`,
  `CompanyHolidayController.java:33`, `EmployeeSalaryHistoryController.java:37`, `ReportController.java:58, 64-65, 72,
  93`, `RoleAssignmentRequestController.java:50`.
- **Tình huống (tái hiện 2026-11-05):** `GET /api/company-holidays` không có `from`/`to` → 500; `GET
  /api/reports/hr/turnover` không có `year` → 500; `POST /api/company-holidays` với `Content-Type: text/plain` → 500.
  Web luôn gửi đủ nên người dùng không gặp, nhưng lỗi phía client bị báo và ghi log như lỗi máy chủ.
- **Đề xuất:** Thêm handler: thiếu tham số → `400 VALIDATION_ERROR` với `field` là tên tham số; sai media type → 415
  (hoặc xử lý chung các exception cài `ErrorResponse` theo status của chúng). Thêm vào `GlobalExceptionHandlerTest`.
- **Tìm ra nhờ:** tiêu chí 7
- **Trạng thái:** Chưa sửa

### API-010 — "Một kỳ cho mỗi tháng trả lương" chỉ kiểm trong code, không khóa chung

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 5
- **Vị trí:** `service/payroll/PayrollService.java:113-114, 128-137`; `db/migration/V7__payroll.sql:129-130`
- **Mô tả:** `calculate` chỉ khóa kỳ đang tính, rồi đọc (không khóa) xem kỳ khác cùng tháng trả lương đã tính chưa. DB
  chỉ có unique `(year, month)`, không có ràng buộc theo tháng trả lương.
- **Tình huống:** Hai người có `payroll.calculate` cùng lúc tính kỳ tháng 10 và kỳ tháng 11, cả hai đặt ngày trả trong
  tháng 11 → mỗi giao dịch không thấy kỳ kia `CALCULATED` → hai kỳ cùng tháng trả lương, mỗi kỳ được giảm trừ gia
  cảnh một lần, thuế TNCN tính thiếu (đúng điều `PAYROLL_LIFECYCLE.md` §2 muốn chặn).
- **Đề xuất:** Khóa `FOR UPDATE` các kỳ chưa hủy có ngày trả trong tháng đó trước khi kiểm tra, hoặc thêm cột tháng trả
  lương với unique index một phần `WHERE status IN ('CALCULATED', 'APPROVED', 'PAID', 'LOCKED')`.
- **Tìm ra nhờ:** tiêu chí 5
- **Trạng thái:** Chưa sửa

### API-011 — Bốn service không có test nào, trong đó có luồng kích hoạt tài khoản

- **Mức độ:** Trung bình
- **Thuộc tiêu chí:** 9
- **Vị trí:** `service/account/AccountActivationService.java`,
  `service/organization/OrganizationCompanyOwnerProvisioningService.java`, `service/organization/JobPositionService.java`,
  `service/rbac/RoleAssignmentOptionService.java`
- **Mô tả:** Không test nào nhắc tới tên class, đường dẫn endpoint (`/api/account-activations/{token}/complete`,
  `/api/system/organization/company-owner`, `/api/job-positions`, `/api/role-assignment-requests/available-roles`)
  trong `src/test`, và E2E không đi qua các luồng này. `apps/web/e2e/bff-guards.spec.ts:23` chỉ kiểm BFF chặn
  request khác nguồn tới đường dẫn kích hoạt, không kích hoạt thật.
- **Tình huống:** Endpoint kích hoạt là công khai (`SecurityConfig.java:67`) và xử lý token một lần cùng mật khẩu; sửa
  sai cách kiểm hạn hoặc đánh dấu đã dùng của token thì không test nào báo. Tương tự với chặn xóa chức danh còn phân
  công và khởi tạo Company Owner chỉ một lần.
- **Đề xuất:** Integration test kích hoạt: token đúng → `ACTIVE`; token hết hạn, đã dùng, sai → lỗi; mật khẩu xác nhận
  lệch → 400. Test khởi tạo Owner lần hai → 409; xóa chức danh đang dùng → 409; danh sách role đề xuất được.
- **Tìm ra nhờ:** tiêu chí 9
- **Trạng thái:** Chưa sửa

### API-012 — `concepts/` còn trỏ vào code trước khi tách package

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 10
- **Vị trí:** `api/docs/concepts/` (`API_TRAFFIC.md:117-125`, `APPROVAL_ROUTING.md:57, 79-82`, `DATA_SCOPE.md:110-112`,
  `EFFECTIVE_DATING.md:109, 118-120`, `PAYROLL_LIFECYCLE.md:76, 102-105`, `RATE_LIMIT.md:338-340`,
  `ROLE_GRANT_POLICY.md:88-91`, `TOKEN_LIFECYCLE.md:141-143`)
- **Mô tả:** Sau khi tách `service/` thành package theo domain, 24 link dạng `service/X.java` không còn tồn tại (đúng là
  `service/<domain>/X.java`). Bốn tên method đã đổi chỗ: `AttendanceService.applyApprovedLeave` → `LeaveAttendanceService`;
  `AttendanceService.approveOvertime`, `listOvertimeAwaitingDecision` → `OvertimeService`;
  `AttendanceService.assertAttendanceEditable` không còn (nay là `AttendanceEditGuard`); `PayrollDeductionsService.invalidate`
  → `PayrollPeriodInvalidator`. `EFFECTIVE_DATING.md:109` nói vài chỗ còn dùng `LocalDate.now(ZoneId.of(...))`, nhưng
  ngoài seeder không còn chỗ nào.
- **Đề xuất:** Sửa link, tên class/method và bỏ câu về `ZoneId.of`.
- **Tìm ra nhờ:** tiêu chí 10
- **Trạng thái:** Chưa sửa

### API-013 — Mô tả và số liệu trong tài liệu lệch code

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 10
- **Vị trí:** `api/docs/api/README.md:3, 10-29`, `api/docs/api/ORGANIZATION.md:28-29`,
  `api/docs/concepts/ROLE_GRANT_POLICY.md:64-65`, `docs/FEATURES.md:29`, `api/docs/database/DATABASE_SCHEMA.md:62`
- **Mô tả:**
  - README nói chỉ đăng nhập, refresh và kích hoạt không cần JWT, nhưng `GET /api/public/branding` và `/logo` cũng công
    khai (`SecurityConfig.java:69`). Bốn endpoint `GET /api/public/branding`, `GET /api/public/branding/logo`,
    `PUT /api/company/logo`, `DELETE /api/company/logo` không có trong tài liệu API nào.
  - `ROLE_GRANT_POLICY.md` §4 nói `EMPLOYEE` mất khi nhân viên nghỉ việc; code giữ nguyên lần gán `AUTO`
    (`AccountRoleAssignmentCommandService.java:161-164`) và chỉ khóa account, đúng như `RBAC.md:220` mô tả.
  - `FEATURES.md` ghi 29 controller, thực tế 32.
  - `DATABASE_SCHEMA.md:62` ghi "33 bảng nghiệp vụ", trong khi `:43, :57` ghi 32 bảng nghiệp vụ cộng một bảng kỹ thuật.
- **Đề xuất:** Thêm mục branding/logo vào `ORGANIZATION.md` và README, sửa ba câu/số còn lại.
- **Tìm ra nhờ:** tiêu chí 10
- **Trạng thái:** Chưa sửa

### API-014 — `ErrorCode.EMPLOYEE_CODE_TAKEN` không còn dùng

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 10
- **Vị trí:** `dto/response/common/ErrorCode.java:24`, `api/docs/api/ORGANIZATION_COMPANY_OWNER_ADMIN.md:164`,
  `apps/web/lib/api-helpers.ts:30`
- **Mô tả:** Mã nhân viên do `EmployeeCodeGenerator` sinh, không code nào ném `EMPLOYEE_CODE_TAKEN`; mã chỉ còn làm ví
  dụ trong `GlobalExceptionHandlerTest.java:26`. Tài liệu khởi tạo Owner vẫn liệt kê `409 EMPLOYEE_CODE_TAKEN` và web
  vẫn giữ bản dịch.
- **Đề xuất:** Xóa mã, dòng tài liệu và bản dịch; đổi ví dụ trong test sang mã đang dùng.
- **Tìm ra nhờ:** tiêu chí 10
- **Trạng thái:** Chưa sửa

### API-015 — Seed chọn người duyệt bằng logic riêng, khác `ApproverResolver`

- **Mức độ:** Thấp
- **Thuộc tiêu chí:** 8
- **Vị trí:** `config/seed/StaffSeeder.java:430-442` (`overtimeApproverOn`), `config/seed/SeedLeaveWriter.java:141`,
  `config/seed/SeedResignationWriter.java:55`
- **Mô tả:** Người duyệt đơn nghỉ và đơn nghỉ việc trong seed là quản lý theo phân công tại ngày nộp, không có thì Giám
  đốc. Khác `ApproverResolver`: không bỏ qua quản lý đã nghỉ việc hoặc account không `ACTIVE`, không có Company Owner
  dự phòng, đơn của Giám đốc/Owner không tự ghi nhận. Dữ liệu hiện tại không vi phạm (truy vấn DB dev: 0 đơn).
- **Tình huống:** Thêm vào `staff.json` một nhân viên có quản lý là `staff-hn3` (đã nghỉ việc) kèm đơn PENDING → seed
  tạo đơn chờ một người không duyệt được (đúng `API-001`), `SeedDataIntegrationTest` vẫn pass.
- **Đề xuất:** Seeder dùng chung điều kiện "duyệt được" của `ApproverResolver`, hoặc thêm vào `SeedDataIntegrationTest`
  kiểm mọi đơn PENDING có người duyệt đang làm việc với account `ACTIVE`.
- **Tìm ra nhờ:** tiêu chí 8
- **Trạng thái:** Chưa sửa

## 4. So sánh với lần trước

Điểm tổng 7,0 → 7,0 (trung bình có trọng số 8,9 trên 92% → 8,7 trên 100%; cả hai lần đều bị trần 7,0 vì `API-001`).

| # | Tiêu chí | 2026-11-03 | 2026-11-05 | Thay đổi |
|---|---|---|---|---|
| 1 | Phân quyền và phạm vi dữ liệu | 10 | 10 | — |
| 2 | Bảo vệ dữ liệu nhạy cảm | 10 | 10 | — |
| 3 | Quy trình duyệt đúng nghiệp vụ | 4 | 4 | — |
| 4 | Toàn vẹn bảng lương | 10 | 10 | — |
| 5 | Toàn vẹn dữ liệu và đồng thời | 9,5 | 9 | `API-010` mới |
| 6 | Thời gian và hiệu lực | 9,5 | 7,5 | `API-007` mới (lần trước tiêu chí này mới chỉ kiểm `Clock`) |
| 7 | Validation và lỗi trả về | 9,5 | 9,5 | `API-004` đã sửa, `API-009` mới |
| 8 | Dữ liệu seed khớp quy tắc | 8 | 8 | `API-015` mới, vẫn kiểm một phần |
| 9 | Kiểm thử | 8 | 8 | `API-003` đã sửa, `API-011` mới |
| 10 | Bảo trì và tài liệu | 10 | 8,5 | `API-012`, `API-013`, `API-014` mới |
| 11 | Vòng đời và tham chiếu | Chưa kiểm | 9,5 | Lần đầu chấm; `API-008` mới |

- **Đã sửa:** `API-003` (`06cd73dc`), `API-004` (`477a2b75`).
- **Còn tồn:** `API-001` (Cao), `API-002`, `API-005`, `API-006`. Ba lỗi đầu tái hiện lại được trên API chạy thật.
- **Mới:** `API-007`, `API-011` (Trung bình); `API-008`, `API-009`, `API-010`, `API-012`, `API-013`, `API-014`,
  `API-015` (Thấp).

## 5. Phạm vi chưa kiểm

- Bước tùy chọn đối chiếu công thức thuế/bảo hiểm: không chạy vì `V8__reference_data.sql`, `PayslipCalculator`,
  `InsuranceTaxCalculator` không đổi từ lần trước.
- Tiêu chí 8 kiểm một phần: truy vấn chạy trên DB dev `hrm` hiện có, có thể được seed từ bản `staff.json` cũ hơn; chưa
  kiểm các đơn chờ duyệt trong seed có trùng ngày nhau không.
- Tiêu chí 5: hai tình huống đồng thời (`API-006`, `API-010`) kết luận bằng đọc code, chưa chạy thử hai giao dịch song
  song.
- Kiểm endpoint không dùng (tiêu chí 10) khớp đường dẫn trong `apps/web` bằng regex lỏng (`{id}` khớp mọi đoạn), chỉ là
  chỉ báo: không thấy endpoint nào thừa.
- Web (`apps/web`) ngoài phạm vi, trừ việc đối chiếu `ErrorCode` ↔ bản dịch và `openapi.json` ↔ `api-schema.ts`.
