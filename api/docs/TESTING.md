# Kiểm thử

Tài liệu mô tả bộ test của API (`api/src/test/java`) và các bước kiểm tra của web (`apps/web`):
cách chạy, các loại test, quy ước khi viết test mới và danh mục test theo module.

Số liệu tính tại lần chạy đầy đủ ngày 10/11/2026: **91 lớp test, 608 test, tất cả pass**, chạy hết
khoảng 5–6 phút. Web có 16 test E2E Playwright.

## 1. Chạy test API

### Yêu cầu

- JDK 25 (giống CI).
- PostgreSQL đang chạy: `docker compose up -d database` ở thư mục gốc repo. Lỗi `Connection refused`
  tại `flywayInitializer` nghĩa là database chưa bật, không phải lỗi code.

### Lệnh

```bash
cd api
./mvnw test                                   # toàn bộ (Windows: ./mvnw.cmd test)
./mvnw test -Dtest=LeaveRequestServiceTest    # một lớp
./mvnw test -Dtest='*Payroll*,*Attendance*'   # theo mẫu tên
./mvnw test -Dtest=AttendanceServiceTest#holidayOvertimeIsPaidAtThreeTimes   # một test
```

Kết quả từng lớp nằm trong `api/target/surefire-reports/`.

### Database dùng cho test

Test dùng `DB_URL` giống ứng dụng; mặc định là database dev `hrm`. Flyway chạy migration lên database
đó trước khi test, còn các seeder chạy như khi khởi động API.

Nên chạy trên một **database tạm** khi:

- vừa sửa một migration gốc (`V1`–`V8`): database `hrm` cũ không còn khớp checksum, và chạy
  `docker compose down -v` sẽ xoá dữ liệu dev;
- database dev đang có dữ liệu tự nhập, hoặc API dev đang chạy trên nó.

```bash
docker exec hrm-database-1 psql -U hrm-user -d postgres -c "CREATE DATABASE hrm_test_tmp"
cd api && DB_URL=jdbc:postgresql://localhost:5432/hrm_test_tmp ./mvnw test
docker exec hrm-database-1 psql -U hrm-user -d postgres -c "DROP DATABASE hrm_test_tmp WITH (FORCE)"
```

CI (`.github/workflows/ci.yml`, job `api`) chạy `./mvnw -B test` trên một Postgres 16 trống, nên mọi
test phải pass cả trên database trống lẫn database đã seed.

### Cấu hình riêng khi test

| Ở đâu | Nội dung |
| --- | --- |
| `pom.xml`, `maven-surefire-plugin` | `rate-limit.enabled=false`: test đăng nhập nhiều hơn người thật nên rate-limit tắt cho cả bộ; các test rate-limit tự bật lại. Chạy cả bộ trong IDE (không qua `mvnw`) thì thêm `-Drate-limit.enabled=false` vào VM options, xem [RATE_LIMIT.md §10](concepts/RATE_LIMIT.md#10-kiểm-thử) |
| `src/test/resources/config/application.properties` | Pool kết nối nhỏ (tối đa 3, đóng kết nối rảnh sau 10 giây). Mỗi cấu hình `@SpringBootTest` khác nhau là một context được cache và giữ pool riêng; pool mặc định làm cả bộ vượt `max_connections` của Postgres |

## 2. Các loại test

| Loại | Cách viết | Số lớp | Dùng cho |
| --- | --- | ---: | --- |
| Unit | JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), hoặc JUnit thuần | 45 | Quy tắc nghiệp vụ trong một service, công thức tính (lương, thuế, phép năm), validation pattern. Repository và service khác là mock |
| Tích hợp service | `@SpringBootTest` + `@Transactional` | 24 | Luồng nhiều bảng chạy trên Postgres thật: chuỗi duyệt, nghỉ việc, tính lương, phân quyền. Mỗi test rollback khi kết thúc |
| Tích hợp HTTP | `@SpringBootTest` + `@AutoConfigureMockMvc` (phần lớn kèm `@Transactional`) | 18 | Đi qua filter, security, validation và `GlobalExceptionHandler`: mã HTTP, envelope lỗi, permission ở endpoint, rate-limit |
| Tích hợp không rollback | `@SpringBootTest` không có `@Transactional` | 4 | Khởi động ứng dụng (`HrmApplicationTests`), CHECK của cột enum, thông điệp validation, rate-limit với đường dẫn mã hoá |

Quy ước khi viết test mới:

- Test tích hợp phải pass trên cả database dev đã seed lẫn database trống: tạo dữ liệu với **mã ngẫu
  nhiên** trong test `@Transactional`, không đếm tổng toàn bảng, không dùng lại mã của dữ liệu seed.
- Test cần cố định "hôm nay" thì truyền `Clock.fixed(...)` vào constructor của service
  ([EFFECTIVE_DATING.md](concepts/EFFECTIVE_DATING.md)).
- Tên test là một câu mô tả hành vi (`overtimeGoesToTheSameApprover`,
  `aPastLastDayInsideAnApprovedPeriodIsRefused`); danh mục ở mục 4 dựa vào các tên này.
- `@PreAuthorize` nằm ở service ([ADR 0002](../../docs/adr/0002-authorization-in-service-layer.md)), nên test
  permission của use case viết ở tầng service; test HTTP chỉ kiểm tra mã lỗi và việc request tới được
  service.

## 3. Test canh giữ (guard)

Các test này không kiểm tra một tính năng mà chặn lệch pha giữa code, schema, seed và tài liệu. Khi
chúng fail, thường là thiếu một bước cập nhật đi kèm:

| Test | Fail khi | Cách xử lý |
| --- | --- | --- |
| `OpenApiSnapshotTest` | DTO hoặc endpoint đổi mà `apps/web/openapi.json` chưa cập nhật | `./mvnw test -Dtest=OpenApiSnapshotTest -Dopenapi.snapshot.update=true`, rồi `pnpm gen:api` trong `apps/web` |
| `OpenApiDocsTest` | Field response có thể `null` mà không gắn `@Nullable`, hoặc spec/Swagger UI không công khai | Gắn `jakarta.annotation.Nullable` cho field đó |
| `PermissionCatalogueTest` | Permission có trong danh mục seed nhưng không được kiểm tra ở đâu, hoặc được kiểm tra mà không có trong danh mục | Thêm vào seeder hoặc bỏ permission thừa |
| `EnumColumnCheckIntegrationTest` | Thêm/bớt hằng của một enum Java mà CHECK của cột tương ứng trong migration không đổi theo | Sửa CHECK trong file migration gốc của domain |
| `SeedDataIntegrationTest` | Dữ liệu seed vi phạm quy tắc nghiệp vụ (ví dụ đơn chờ duyệt không có người duyệt hợp lệ). Test tự tạo một database mới, chạy mọi seeder, mất khoảng 2 phút | Sửa file seed (`src/main/resources/seed/`) hoặc seeder |
| `ValidationMessagesTest` | Ràng buộc Bean Validation chuẩn báo lỗi không phải tiếng Việt | Bổ sung `ValidationMessages.properties` |
| `HrmApplicationTests` | Ứng dụng không khởi động được (bean, cấu hình, migration) | Xem log khởi động |

## 4. Danh mục test API

Cột "Số test" là số test thực chạy; test tham số hoá (`@ParameterizedTest`) tính mỗi bộ tham số một lần.

### 4.1. Cấu hình, seed và schema (29 test)

| Lớp | Số test | Kiểm tra |
| --- | ---: | --- |
| `HrmApplicationTests` | 1 | Ứng dụng khởi động được |
| `OpenApiDocsTest` | 3 | Spec và Swagger UI công khai, khai báo bearer auth; field response là `required`, chỉ field `@Nullable` nhận `null` |
| `OpenApiSnapshotTest` | 1 | Bản chụp `apps/web/openapi.json` khớp spec hiện tại |
| `ValidationMessagesTest` | 1 | Lỗi validation chuẩn trả tiếng Việt |
| `CompanyHolidaySeederTest` | 2 | Mỗi năm có đủ 11 ngày lễ luật định; năm HR đã tự khai báo thì không ghi đè |
| `CompanyProfileSeederTest` | 3 | Tạo hồ sơ doanh nghiệp duy nhất khi thiếu, giữ hồ sơ có sẵn, tắt được |
| `PermissionCatalogueTest` | 2 | Danh mục permission khớp với các chỗ kiểm tra quyền trong code |
| `RolePermissionSeederTest` | 3 | Quyền của role hệ thống: HR Manager có báo cáo nhân sự và lương; Manager không xem lương, dữ liệu nhạy cảm; System Admin quản lý tài khoản nhưng không có quyền nghiệp vụ |
| `SeedDataIntegrationTest` | 2 | Chạy mọi seeder trên database mới; đơn chờ duyệt nào cũng có người duyệt được |
| `SeedTimestampsIntegrationTest` | 2 | Auditing ghi thời điểm khi tạo/sửa; ngày của dữ liệu seed thay thế ngày auditing |
| `SystemAdminSeederTest` | 6 | Tạo admin hệ thống: bắt buộc mật khẩu đủ độ dài, cần role có sẵn, không tạo trùng |
| `WorkShiftSeederTest` | 3 | Tạo ca làm mặc định khi thiếu, giữ ca có sẵn, tắt được |

### 4.2. Tầng HTTP (115 test)

| Lớp | Số test | Kiểm tra |
| --- | ---: | --- |
| `RbacManagementTest` | 50 | CRUD role tùy chỉnh và quyền của role qua API; role hệ thống không sửa/xoá được; permission chỉ dành cho system role không gán được cho role tùy chỉnh; người thao tác lấy từ JWT; tài nguyên thiếu và body sai trả đúng mã lỗi |
| `EmployeeLifecycleAccessTest` | 15 | Kết thúc làm việc và xoá mềm hồ sơ: thiếu permission thì 403, đủ thì tới service; bắt buộc trạng thái, ngày, lý do |
| `WorkforceCommandValidationTest` | 15 | Command sai (chấm công, nghỉ phép, lương…) trả 400 kèm đúng `field` |
| `RbacPermissionAccessTest` | 12 | Quyền truy cập RBAC phụ thuộc permission, không phụ thuộc tên role |
| `EmployeeSensitiveDataAccessTest` | 8 | Xem/sửa dữ liệu nhạy cảm cần `employee.sensitive.read`/`.manage` |
| `ApiTrafficIntegrationTest` | 4 | Thống kê lưu lượng cần permission riêng, đếm request và lỗi theo route; actuator không lộ ra ngoài |
| `PayrollExportAccessTest` | 3 | Xuất Excel bị từ chối vẫn trả envelope JSON |
| `PublicBrandingAccessTest` | 3 | Ai cũng đọc được tên và logo nhưng không đọc được hồ sơ doanh nghiệp; tải logo cần `company.manage` |
| `YearParameterValidationTest` | 3 | Tham số năm ngoài khoảng trả 400 nêu tên tham số |
| `EmployeeSelfProfileAccessTest` | 2 | Xem hồ sơ của mình cần `profile.self.read` |

### 4.3. Validation, lỗi, bảo mật, truy vấn (114 test)

| Lớp | Số test | Kiểm tra |
| --- | ---: | --- |
| `PhoneValidationTest` | 84 | Số điện thoại hợp lệ được nhận; có ký tự khác chữ số thì bị từ chối |
| `ValidationPatternsTest` | 14 | Họ tên nhận chữ có dấu, khoảng trắng và dấu nối tên; từ chối chữ số, ký hiệu |
| `GlobalExceptionHandlerTest` | 8 | Ánh xạ exception sang mã HTTP: 404, 405 kèm header `Allow`, 409 khi xung đột phiên bản, 415, thiếu tham số là 400; lỗi không lường trước không lộ thông điệp nội bộ |
| `CurrentAccountProviderTest` | 4 | Lấy người thao tác từ claim JWT; thiếu đăng nhập hoặc tài khoản đã mất thì 401 |
| `JwtServiceTest` | 1 | Token sinh ra giải mã được và chứa claim phân quyền |
| `OptionalDateBoundQueryIntegrationTest` | 2 | Truy vấn có ngày kết thúc tùy chọn (kỳ lương bị ảnh hưởng, override chồng lấn) đúng cả khi có và không có ngày kết thúc |
| `EnumColumnCheckIntegrationTest` | 1 | CHECK của mọi cột enum khớp hằng Java |

### 4.4. Giới hạn truy cập (36 test)

Chi tiết từng lớp ở [RATE_LIMIT.md §10](concepts/RATE_LIMIT.md#10-kiểm-thử).

| Lớp | Số test |
| --- | ---: |
| `RateLimitServiceTest` | 8 |
| `ClientIpResolverTest` | 7 |
| `LoginAttemptLimiterIntegrationTest` | 5 |
| `IpRateLimitIntegrationTest` | 4 |
| `RateLimitTimelineTest` | 4 |
| `RateLimitStatsIntegrationTest` | 3 |
| `UserRateLimitIntegrationTest` | 3 |
| `EncodedPathRateLimitIntegrationTest` | 2 |

### 4.5. Xác thực và tài khoản (29 test)

| Lớp | Số test | Kiểm tra |
| --- | ---: | --- |
| `AuthServiceTest` | 7 | Đăng nhập trả bộ token và reset số lần sai; sai mật khẩu không tiết lộ tài khoản bị khoá; đổi mật khẩu thu hồi mọi refresh token |
| `RefreshTokenServiceTest` | 3 | Chỉ lưu hash; xoay vòng token; dùng lại token cũ thì thu hồi mọi phiên |
| `EmployeeCodeLoginIntegrationTest` | 3 | Đăng nhập bằng mã nhân viên không phân biệt hoa thường, chỉ vào tài khoản còn sống |
| `CompanyOwnerAccountGuardIntegrationTest` | 5 | Admin không quản lý được tài khoản Company Owner; không ai tự override quyền của mình |
| `AccountDeletionIntegrationTest` | 4 | Xoá tài khoản thu hồi phiên và role nghiệp vụ; không xoá tài khoản của mình hay của Company Owner |
| `AccountActivationIntegrationTest` | 3 | Link kích hoạt đặt mật khẩu và chỉ dùng được một lần; token lạ, hết hạn, bị thay thì bị từ chối |
| `AccountProvisioningIntegrationTest` | 2 | Role Employee bắt đầu từ ngày vào làm (hoặc hôm nay nếu đã đi làm) |
| `ListSearchIntegrationTest` | 2 | Tìm kiếm, lọc, sắp xếp danh sách nhân viên và tài khoản |

### 4.6. Phân quyền RBAC (45 test)

| Lớp | Số test | Kiểm tra |
| --- | ---: | --- |
| `RoleRevocationRequestIntegrationTest` | 10 | Đề xuất cấp/thu hồi role: role HR gán được thì thu hồi ngay, role cần Owner duyệt thì chờ duyệt; một đề xuất mở cho mỗi phân công; không tự đề xuất cho mình; chỉ một Giám đốc |
| `RoleServiceTest` | 8 | Thay toàn bộ quyền của role trong một thao tác; chặn permission chỉ dành cho system role và quyền toàn công ty khi role đang gán phạm vi hẹp; xoá role thu hồi các phân công |
| `SystemRoleScopeIntegrationTest` | 8 | Role có quyền toàn công ty chỉ gán phạm vi công ty; role làm việc theo từng nhân viên nhận mọi phạm vi |
| `AccountAuthorizationServiceTest` | 5 | Quyền hiệu lực theo ngày giờ Việt Nam; override áp theo từng phân công; đánh dấu phạm vi công ty |
| `AccountRoleAssignmentCommandServiceTest` | 3 | Thu hồi role kết thúc phân công đang chạy và huỷ đề xuất đang chờ |
| `RoleAssignmentPolicyTest` | 3 | Quy tắc phạm vi gán của từng loại role |
| `AccountPermissionOverrideServiceTest` | 2 | Không cấp quyền toàn công ty trên phân công theo đơn vị |
| `EmployeeAccessScopeServiceTest` | 2 | Phạm vi đơn vị mở rộng xuống đơn vị con |
| `EmployeePermissionMappingTest` | 2 | Use case nhân sự đòi đúng permission nguyên tử |
| `RoleAssignmentOptionIntegrationTest` | 2 | Chỉ liệt kê role được phép đề xuất, kèm phạm vi |

### 4.7. Tổ chức (36 test)

| Lớp | Số test | Kiểm tra |
| --- | ---: | --- |
| `WorkShiftServiceTest` | 8 | Giờ nghỉ phải nằm trong ca, kể cả ca qua đêm; không đổi giờ ca đã có chấm công |
| `CompanyProfileIntegrationTest` | 7 | Cập nhật hồ sơ, tải logo PNG (kiểu file lấy theo chữ ký file), từ chối SVG/rỗng/quá cỡ, xoá logo; cần `company.manage` |
| `CompanyHolidayServiceTest` | 6 | Đổi ngày lễ đưa kỳ lương đã tính về nháp, kỳ đã duyệt thì từ chối; không thêm/xoá ngày lễ đã có chấm công |
| `CompanyOwnerTransferIntegrationTest` | 4 | Chuyển quyền sở hữu ngay lập tức, cần đúng mật khẩu, người nhận là tài khoản đang hoạt động |
| `JobPositionServiceTest` | 4 | Mã trùng bị từ chối kể cả khi tạo đồng thời; vị trí đang dùng không xoá được |
| `CompanyOwnerProvisioningIntegrationTest` | 3 | Tạo Company Owner đầu tiên ở trạng thái chờ kích hoạt; không tạo người thứ hai |
| `OrganizationUnitServiceTest` | 2 | Không kích hoạt đơn vị dưới đơn vị cha đã tắt |
| `WorkLocationServiceTest` | 2 | Không kích hoạt địa điểm dưới địa điểm cha đã tắt |

### 4.8. Nhân sự (56 test)

| Lớp | Số test | Kiểm tra |
| --- | ---: | --- |
| `EmployeeServiceTest` | 26 | Mã nhân viên do server sinh; tuổi tối thiểu; email công việc không trùng; xoá mềm chỉ khi chưa điều chuyển; xác nhận hết thử việc; kết thúc làm việc ngay hoặc hẹn ngày, huỷ lịch, job hoàn tất lịch hẹn; Company Owner phải chuyển quyền trước khi nghỉ |
| `ScheduledEmploymentEndIntegrationTest` | 11 | Hẹn ngày nghỉ: vẫn làm tới ngày cuối, huỷ đơn nghỉ phép vượt ngày cuối, đóng lương/bảo hiểm/người phụ thuộc, đưa kỳ lương đã tính về nháp, từ chối khi rơi vào kỳ đã duyệt |
| `ResignationRequestIntegrationTest` | 9 | Đơn xin nghỉ: quản lý trực tiếp duyệt và có thể dời ngày cuối; một đơn mở mỗi lúc; kết thúc làm việc huỷ đơn đang chờ; tương tác với điều chuyển đã hẹn |
| `EmployeeSalaryHistoryServiceTest` | 3 | Đổi lương đóng bản ghi cũ, đưa kỳ đã tính về nháp, từ chối khi chạm kỳ đã duyệt |
| `EmployeeAssignmentServiceTest` | 2 | Đổi phân công đưa kỳ đã tính về nháp, từ chối khi chạm kỳ đã duyệt |
| `EmployeeSelfProfileIntegrationTest` | 2 | Nhân viên chỉ tự sửa liên hệ và học vấn |
| `SoftDeletedEmployeeIdentityIntegrationTest` | 2 | Hồ sơ xoá nhầm nhả CCCD và email cho hồ sơ đúng |
| `EmployeeCodeGeneratorIntegrationTest` | 1 | Mã kế tiếp tính cả nhân viên đã xoá |

### 4.9. Chấm công, nghỉ phép, tăng ca (76 test)

| Lớp | Số test | Kiểm tra |
| --- | ---: | --- |
| `LeaveRequestServiceTest` | 23 | Tạo đơn theo phút của lịch làm; HR không tạo/gửi/huỷ đơn thay nhân viên; trùng ngày, ngoài lịch, quá ngày nghỉ việc thì từ chối; phép năm qua năm mới tách theo năm; vượt số dư phép; chế độ lương theo loại nghỉ |
| `AttendanceServiceTest` | 22 | Phút công, phút nghỉ có lương, đi muộn/về sớm; chuẩn bị bảng công tháng; điều chỉnh ngày xoá quyết định tăng ca; ghi giờ chấm từ máy; tăng ca không lấy phần công thường, ngày lễ hệ số 3 |
| `ApprovalChainIntegrationTest` | 15 | Chuỗi duyệt: quản lý trực tiếp → Giám đốc → Company Owner; bỏ qua người đã nghỉ hoặc không đăng nhập được; đơn chờ chuyển người duyệt khi người duyệt rời đi; tăng ca đi cùng người duyệt; phạm vi xem đơn và bảng công |
| `LeaveEntitlementServiceTest` | 11 | Phép năm 12 ngày, chia theo tháng làm việc khi vào/ra giữa năm; đơn chờ duyệt cũng trừ số dư; loại nghỉ khác không đụng phép năm |
| `AttendanceAutoPrepareJobTest` | 4 | Job chuẩn bị bảng công hằng đêm; một người lỗi không chặn người khác; kỳ lương đã khoá thì dừng |
| `AttendanceCalendarServiceTest` | 1 | Lịch làm thứ 2–thứ 7, trừ ngày lễ |

### 4.10. Lương (54 test)

| Lớp | Số test | Kiểm tra |
| --- | ---: | --- |
| `PayrollServiceTest` | 20 | Tính phiếu lương: lương theo công, phụ cấp, tăng ca, khoản điều chỉnh, chia theo ngày khi đổi lương hoặc nghỉ giữa tháng; thiếu dữ liệu thì không đánh dấu đã tính; người tính không tự duyệt; huỷ, mở lại kỳ; ngày trả lương |
| `PayrollAdjustmentServiceTest` | 8 | Thêm/xoá khoản điều chỉnh đưa kỳ về nháp; khoá sau khi rời bước tính; không điều chỉnh cho người đã nghỉ trước kỳ |
| `PayrollQueryServiceTest` | 7 | Nhân viên chỉ xem phiếu của mình sau khi kỳ được duyệt; người đọc lương xem trong phạm vi |
| `AllowancePolicyServiceTest` | 6 | Phụ cấp chức vụ và bảng thâm niên: đưa kỳ đã tính về nháp, từ chối khi chạm kỳ đã duyệt, bảng thâm niên phủ mọi năm từ 0 |
| `PayrollDeductionsIntegrationTest` | 5 | BHXH/BHYT/BHTN với trần riêng, giảm trừ người phụ thuộc; thiếu quy tắc thuế thì dừng; nghỉ từ 14 ngày không đóng bảo hiểm |
| `PayrollExportServiceTest` | 3 | File Excel một dòng mỗi phiếu, sắp theo mã, có dòng tổng; cần phạm vi toàn công ty |
| `PayrollCalculationIntegrationTest` | 2 | Phiếu và dòng phiếu lưu được, thỏa CHECK của bảng; người phụ thuộc của người nghỉ lấy tại ngày làm cuối |
| `InsuranceTaxCalculatorTest` | 1 | Thuế lũy tiến tháng theo biểu 2026, kiểm tra tại các mốc bậc |
| `PayrollPeriodCancelIntegrationTest` | 1 | Huỷ kỳ thì tháng đó tạo được kỳ mới |
| `PayrollReportServiceTest` | 1 | Tổng hợp quỹ lương theo tháng và đơn vị, giữ tháng trống |

### 4.11. Báo cáo và hệ thống (18 test)

| Lớp | Số test | Kiểm tra |
| --- | ---: | --- |
| `HrReportIntegrationTest` | 4 | Biến động nhân sự theo tháng, số dư phép, tình hình nhân sự tháng, trạng thái sắp kết thúc làm việc |
| `ReportServicesTest` | 4 | Cơ cấu nhân sự theo học vấn, thâm niên; báo cáo lương theo lương hiệu lực; từ chối ngày tương lai |
| `RateLimitSettingsIntegrationTest` | 8 | Đổi hạn mức áp dụng ngay và được lưu; tắt/khôi phục; không tắt được chính sách đăng nhập sai |
| `ApiTrafficTimelineTest` | 2 | Đếm request, lỗi và thời gian trung bình theo khung |

## 5. Kiểm tra web (`apps/web`)

### Lint, kiểu dữ liệu, build

CI (job `web`) chạy:

```bash
pnpm lint
pnpm typecheck   # gồm check:api: lib/api-schema.ts phải khớp openapi.json
pnpm build
```

### E2E (Playwright)

Test trong `apps/web/e2e/`, chạy trên stack thật: API (cổng 8080) và Postgres phải đang chạy với dữ liệu seed;
dev server web tự bật nếu cổng 3000 chưa có. Không chạy trong CI.

```bash
cd apps/web
pnpm test:e2e
```

Tài khoản dùng trong test nằm ở `e2e/auth.ts` (`admin`/`Admin@123`, `owner`, `hr`, `employee` với mật
khẩu `password`).

| File | Số test | Kiểm tra |
| --- | ---: | --- |
| `login.spec.ts` | 8 | Bỏ trống, hiện/ẩn mật khẩu, sai tài khoản báo tiếng Việt, đăng nhập bằng mã nhân viên, tách cổng HRM và Admin, chặn trang khi chưa đăng nhập |
| `bff-guards.spec.ts` | 5 | Tài khoản thường bị chặn ở cổng Admin; request ghi từ trang khác bị 403; đăng nhập chỉ nhận JSON; đường dẫn ngoài whitelist 404, sai method 405; nhiều request hết hạn token chỉ làm mới phiên một lần |
| `annual-leave-attendance.spec.ts` | 1 | Nghỉ phép năm buổi sáng vào ngày thiếu chấm công, được duyệt thì trừ phép và cộng công |
| `session-expiry.spec.ts` | 1 | Hết phiên giữa chừng thì về trang đăng nhập |
| `account-status.spec.ts` | 1 | Owner vô hiệu hoá rồi kích hoạt lại tài khoản `employee`. Chạy riêng sau các test khác (project `account-status` trong `playwright.config.ts`) vì nó tạm khoá tài khoản dùng chung |

`annual-leave-attendance.spec.ts` cần một ngày "Thiếu chấm công" chưa có đơn nghỉ trong tháng hiện tại
của tài khoản `employee`; không có thì test tự bỏ qua (skip). Dữ liệu dev đã bị sửa nhiều (ví dụ sau khi
chạy kịch bản demo) có thể làm test này fail; khi đó chạy lại trên database vừa seed.
