# 0002. Kiểm tra quyền ở tầng service

- **Trạng thái:** Đang áp dụng
- **Áp dụng từ:** 2026-09-25 (`25ddab3`, áp phạm vi truy cập nhân viên)

## Bối cảnh

Một thao tác trong HRM thường cần hai kiểm tra: có permission không, và nhân viên đích có nằm trong phạm vi dữ liệu
không. Kiểm tra thứ hai cần dữ liệu chỉ có sau khi đọc database (phân công chính, cây tổ chức). Cùng một logic
nghiệp vụ còn được gọi từ nhiều nơi ngoài controller: job định kỳ, seeder, service khác.

## Quyết định

- `@PreAuthorize("hasAuthority('…')")` đặt trên **method của service**, không đặt trên controller. Controller chỉ
  nhận request và gọi service.
- Kiểm tra phạm vi gọi `EmployeeAccessScopeService` ngay trong service (`requireEmployeeAccess`,
  `accessibleEmployees`, `requireCompanyWide`…).
- Nhóm thao tác dùng chung một permission dùng annotation gộp ở mức class (`@CanManageRbac`,
  `@CanManagePermissionOverrides`).

## Hệ quả

- Controller không có `@PreAuthorize` là **bình thường**, không phải lỗ hổng. Khi review bảo mật, xem service.
- Mọi đường gọi tới logic nghiệp vụ qua Spring bean đều đi qua cùng một kiểm tra.
- Gọi service từ job hoặc seeder (không có người dùng đăng nhập) phải đi đường riêng không có `@PreAuthorize`, ví dụ
  `ResignationRequestService.complete`, hoặc ghi trực tiếp qua repository như seeder.
- Gọi một method khác **trong cùng class** không đi qua proxy nên không kích hoạt `@PreAuthorize` của method đó.
- Bean hạ tầng được gọi nội bộ (như `RateLimitService` của bộ lọc request) không gắn quyền; trang quản trị đọc nó qua
  một service có quyền (`RateLimitSettingsService.stats`, `.timeline`).

## Phương án đã cân nhắc

- **Đặt trên controller:** dễ nhìn thấy trong danh sách endpoint, nhưng phải lặp lại khi logic được gọi từ chỗ
  khác, và vẫn phải kiểm tra phạm vi ở service.
- **Cấu hình theo URL trong `SecurityFilterChain`:** chỉ hợp với quy tắc thô; không biểu diễn được phạm vi dữ liệu.

Chi tiết: [DATA_SCOPE.md](../../api/docs/concepts/DATA_SCOPE.md).
