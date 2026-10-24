# Xử lý lỗi

Tài liệu giải thích lỗi đi từ service tới màn hình người dùng thế nào, và phải làm gì khi thêm một mã lỗi mới.

## 1. Envelope

Mọi response của API, thành công hay lỗi, đều có cùng một dạng (`ApiResult`):

```json
{ "success": false, "data": null, "error": { "code": "EMPLOYEE_NOT_FOUND", "message": "Employee not found: 42", "field": null } }
```

| Trường | Ý nghĩa |
|:-------|:--------|
| `error.code` | Tên một hằng trong enum `ErrorCode`. **Client dựa vào trường này**, không dựa vào `message` |
| `error.message` | Mô tả cho lập trình viên, thường bằng tiếng Anh. Riêng lỗi validation của DTO là tiếng Việt |
| `error.field` | Tên trường gây lỗi (có thể dạng đường dẫn `employee.workEmail`), hoặc `null` |

## 2. Từ exception tới HTTP status

Service ném exception; `GlobalExceptionHandler` đổi thành response:

| Exception | Status | Dùng khi |
|:----------|:-------|:---------|
| `BusinessException` | 400 | Dữ liệu vi phạm quy tắc nghiệp vụ |
| `UnauthorizedException`, `InvalidTokenException` | 401 | Sai mật khẩu, token không hợp lệ |
| `ForbiddenException` | 403 | Không có quyền, ngoài phạm vi dữ liệu |
| `ResourceNotFoundException` | 404 | Không tìm thấy bản ghi |
| `ConflictException` | 409 | Trạng thái không cho phép, trùng dữ liệu, đã bị khóa |
| `RateLimitExceededException` | 429 | Hết lượt sai mật khẩu khi đăng nhập; kèm header `Retry-After` và `RateLimit-*` |

Tất cả kế thừa `AppException` (mang `ErrorCode`, `message`, `field`, `status`). Các exception của framework cũng
được bắt:

| Exception | Status | `code` |
|:----------|:-------|:-------|
| `MethodArgumentNotValidException` (Bean Validation trên DTO) | 400 | `VALIDATION_ERROR`, chỉ trả **lỗi của trường đầu tiên** |
| `HttpMessageNotReadableException` (JSON hỏng) | 400 | `VALIDATION_ERROR` |
| `MethodArgumentTypeMismatchException` (sai kiểu tham số) | 400 | `VALIDATION_ERROR` kèm tên tham số |
| `NoResourceFoundException` | 404 | `RESOURCE_NOT_FOUND` |
| `HttpRequestMethodNotSupportedException` | 405 | `METHOD_NOT_ALLOWED` |
| `BadCredentialsException` | 401 | `INVALID_CREDENTIALS` |
| `AccessDeniedException` (`@PreAuthorize` không qua) | 403 | `FORBIDDEN` |
| `OptimisticLockingFailureException` | 409 | `CONCURRENT_MODIFICATION` |
| `DataIntegrityViolationException` (vi phạm ràng buộc DB) | 409 | `CONFLICT`, ghi log mức WARN |
| Bị chặn ở `IpRateLimitFilter` / `UserRateLimitFilter` (trước controller) | 429 | `RATE_LIMIT_EXCEEDED`, xem [RATE_LIMIT.md](./RATE_LIMIT.md#6-response-khi-bị-chặn) |
| Mọi exception khác | 500 | `INTERNAL_ERROR`, ghi log mức ERROR kèm stack trace |

Lỗi xảy ra **trước khi vào controller** (thiếu token, token hết hạn, sai chữ ký) do `RestAuthenticationEntryPoint`
(401 `UNAUTHORIZED`) và `RestAccessDeniedHandler` (403 `FORBIDDEN`) trả về, cũng theo cùng envelope.

## 3. Thông báo validation tiếng Việt

Thông báo mặc định của các annotation Bean Validation (`@NotBlank`, `@Size`, `@Email`…) được dịch trong
`src/main/resources/ValidationMessages.properties`, ví dụ `không được để trống`. Thông báo chỉ nêu quy tắc, không
nêu tên trường; web tự ghép tên trường vào trước.

## 4. Web hiển thị lỗi thế nào

`apiErrorMessage` / `friendlyErrorMessage` trong `apps/web/lib/api-helpers.ts`:

1. `message` đã là tiếng Việt (lỗi validation DTO, lỗi do BFF tạo): hiện nguyên văn. Nếu là `VALIDATION_ERROR` có
   `field`, ghép tên trường tiếng Việt (`FIELD_LABELS`) vào trước, ví dụ "Họ tên không được để trống."
2. `VALIDATION_ERROR` có `field` nhưng `message` tiếng Anh: "<Tên trường> chưa hợp lệ. Vui lòng kiểm tra lại."
3. Còn lại: tra `ERROR_MESSAGES[code]`; không có thì dùng câu mặc định "Không thể hoàn tất thao tác…".

Người dùng **không bao giờ thấy** mã lỗi hay câu tiếng Anh của API. Hiện cả 62 mã trong `ErrorCode` đều có câu
tiếng Việt trong `ERROR_MESSAGES`. Riêng `RATE_LIMIT_EXCEEDED`, BFF thay `message` bằng câu tiếng Việt có thời gian
chờ lấy từ `Retry-After` ("thử lại sau 8 phút"), nên web hiện câu đó theo bước 1.

Lỗi do BFF tự tạo (không có cookie phiên, đường dẫn không nằm trong whitelist, không gọi được API…) được liệt kê ở
[apps/web/docs/AUTH.md](../../../apps/web/docs/AUTH.md#error-do-bff-tạo).

## 5. Lưu ý

- `VALIDATION_ERROR` được dùng cho cả nhiều quy tắc nghiệp vụ, ví dụ "thiếu dữ liệu để tính lương cho nhân viên X".
  `message` của các lỗi này là tiếng Anh nên web chỉ hiện câu chung "Dữ liệu nhập chưa hợp lệ". Chi tiết vẫn có trong
  response (xem ở tab Network hoặc Swagger).
- `DataIntegrityViolationException` là chốt chặn cuối. Service nên tự kiểm tra trước và ném mã cụ thể (ví dụ
  `ROLE_ASSIGNMENT_EXISTS`), hoặc bắt exception này để đổi sang mã cụ thể, thay vì để người dùng nhận `CONFLICT` chung.

## 6. Thêm một mã lỗi mới

1. Thêm hằng vào `ErrorCode` (`api/src/main/java/com/htttdn/hrm/dto/response/common/ErrorCode.java`).
2. Ném bằng exception có status phù hợp (bảng mục 2).
3. Thêm câu tiếng Việt vào `ERROR_MESSAGES` trong `apps/web/lib/api-helpers.ts`.
4. Ghi mã lỗi vào bảng "Lỗi" của endpoint trong `api/docs/api/*.md`.
5. Chạy `npm run gen:api` nếu response DTO thay đổi.

## Code

- [`GlobalExceptionHandler`](../../src/main/java/com/htttdn/hrm/exception/GlobalExceptionHandler.java)
- [`AppException`](../../src/main/java/com/htttdn/hrm/exception/AppException.java) và các lớp con trong cùng thư mục
- [`ErrorCode`](../../src/main/java/com/htttdn/hrm/dto/response/common/ErrorCode.java),
  [`ApiResult`](../../src/main/java/com/htttdn/hrm/dto/response/common/ApiResult.java)
- [`RestAuthenticationEntryPoint`](../../src/main/java/com/htttdn/hrm/security/RestAuthenticationEntryPoint.java),
  [`RestAccessDeniedHandler`](../../src/main/java/com/htttdn/hrm/security/RestAccessDeniedHandler.java)
- Web: [`apps/web/lib/api-helpers.ts`](../../../apps/web/lib/api-helpers.ts)
