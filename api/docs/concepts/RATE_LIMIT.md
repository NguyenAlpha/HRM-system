# Giới hạn truy cập (Rate limiting)

Backend giới hạn số request bằng [Bucket4j](https://bucket4j.com/), bucket lưu **trong bộ nhớ** của tiến trình.
Mục tiêu:

- Chặn dò mật khẩu ở đăng nhập, cả khi dò một tài khoản từ nhiều địa chỉ.
- Không để một người hay một địa chỉ gửi quá nhiều request làm chậm cả hệ thống.
- Hạn chế riêng các thao tác tốn tài nguyên: xuất Excel, báo cáo, tính lương, chuẩn bị chấm công, tải logo.

## 1. Ba lớp kiểm tra

```text
Trình duyệt → route của Next (BFF, gửi kèm IP thật qua X-Forwarded-For) → backend
  CorsFilter
  IpRateLimitFilter              đếm theo IP, trước khi xác thực
  BearerTokenAuthenticationFilter
  UserRateLimitFilter            đếm theo username, sau khi JWT hợp lệ
  Controller → AuthService.login → LoginAttemptLimiter   đếm số lần sai mật khẩu theo tài khoản
```

| Lớp | Đếm theo | Áp dụng cho |
|:----|:---------|:------------|
| IP (`IpRateLimitFilter`) | IP của trình duyệt | Mọi request `/api/**`. Đăng nhập, làm mới phiên và kích hoạt tài khoản có hạn mức riêng; còn lại dùng chung trần `ip-ceiling` |
| Người dùng (`UserRateLimitFilter`) | `sub` của JWT (username) | Mọi request có access token hợp lệ. Mỗi người có hạn mức riêng, nên nhiều người dùng chung một mạng không làm hết lượt của nhau |
| Tài khoản (`LoginAttemptLimiter`) | Tài khoản đang đăng nhập | Chỉ các lần **sai mật khẩu** ở `POST /api/auth/login` |

Một request đã đăng nhập đi qua cả lớp IP lẫn lớp người dùng; bị chặn ở lớp nào thì dừng ở lớp đó. Request mang token
sai bị trả `401` trước lớp người dùng nên không tốn lượt của ai. Request preflight CORS (`OPTIONS`) không bị tính.

Các API `/api/system/rate-limit/**` (mục 7, 8) **không bị giới hạn** ở cả hai lớp, để quản trị viên lỡ đặt hạn mức
quá chặt (ví dụ `api` 1 request / 24 giờ) vẫn nới lại được. Các API này vẫn cần đăng nhập và permission `SYSTEM_ONLY`.

Hai filter được tạo bằng `new` trong `SecurityConfig`, không phải bean, để servlet container không đăng ký chúng thêm
lần nữa ngoài security chain.

## 2. Hạn mức mặc định

Mỗi mức cho phép `max-requests` request trong `window-seconds` giây. Bucket nạp lại dần đều (greedy refill): với
300 request / 60 giây, cứ 0,2 giây được thêm một lượt, không phải chờ hết phút mới được dùng lại cả 300.

| Mức (`policy`) | Lớp | Áp dụng cho | Mặc định |
|:---------------|:----|:------------|---------:|
| `login` | IP | `POST /api/auth/login` | 20 / 1 phút |
| `refresh` | IP | `POST /api/auth/refresh` | 30 / 1 phút |
| `activation` | IP | `POST /api/account-activations/{token}/complete` | 10 / 1 phút |
| `ip-ceiling` | IP | Mọi `/api/**` còn lại, gồm cả `/api/public/branding/**` | 1.200 / 1 phút |
| `login-failure` | Tài khoản | Lần sai mật khẩu ở `POST /api/auth/login` | 5 / 15 phút |
| `api` | Người dùng | Mọi API đã đăng nhập không thuộc luật riêng | 300 / 1 phút |
| `password` | Người dùng | `POST /api/auth/change-password`, `POST /api/accounts/{id}/password-reset` | 5 / 10 phút |
| `export` | Người dùng | `GET` và `HEAD /api/payroll/periods/{id}/export` (chung một bucket) | 10 / 10 phút |
| `report` | Người dùng | `GET /api/reports/**` | 60 / 1 phút |
| `heavy-write` | Người dùng | `POST /api/payroll/periods/{id}/calculate`, `POST /api/attendance/prepare`, `PUT /api/company/logo` | 10 / 1 phút |

Ở lớp người dùng, luật riêng **thay cho** mức `api` của request đó: xuất Excel chỉ trừ bucket `export`, không trừ
`api`. `HEAD` dùng chung bucket với `GET` vì Spring MVC chạy cùng controller `@GetMapping` cho cả hai.

Con số mặc định hợp với môi trường dev/demo: demo và kiểm thử bằng tay không chạm tới, nhưng vẫn chặn được việc
spam. Khi chạy thật, xem số liệu ở trang thống kê (mục 7) rồi chỉnh dần ngay trên giao diện (mục 8).

## 3. Cấu hình

Mọi giá trị nằm trong `application.properties` và đổi được bằng biến môi trường:

| Biến môi trường | Mặc định | Ý nghĩa |
|:----------------|:---------|:--------|
| `RATE_LIMIT_ENABLED` | `true` | Tắt thì cả ba lớp cho mọi request đi qua và không đếm. Chỉ đổi được ở đây, giao diện không có nút tắt toàn bộ |
| `RATE_LIMIT_TRUSTED_PROXIES` | `127.0.0.1,::1` | IP hoặc dải CIDR của proxy được tin `X-Forwarded-For` (mục 4) |
| `RATE_LIMIT_<MỨC>_MAX_REQUESTS` | theo bảng mục 2 | Số request tối đa trong một cửa sổ |
| `RATE_LIMIT_<MỨC>_WINDOW_SECONDS` | theo bảng mục 2 | Độ dài cửa sổ, tính bằng giây |

`<MỨC>` là tên mức viết hoa, gạch ngang đổi thành gạch dưới: `LOGIN`, `REFRESH`, `ACTIVATION`, `IP_CEILING`,
`LOGIN_FAILURE`, `API`, `PASSWORD`, `EXPORT`, `REPORT`, `HEAVY_WRITE`. Ví dụ:

```dotenv
RATE_LIMIT_API_MAX_REQUESTS=200
RATE_LIMIT_LOGIN_FAILURE_WINDOW_SECONDS=600
```

Thiếu một mức, hoặc có giá trị không dương, thì backend **không khởi động** (`RateLimitProperties` kiểm tra lúc
bind). Đổi giá trị ở đây cần khởi động lại backend, và việc khởi động lại cũng xoá toàn bộ bucket đang có.

Đây là giá trị **mặc định**. Hạn mức quản trị viên chỉnh trên giao diện (mục 8) được lưu trong database và ghi đè giá
trị ở đây cho tới khi bấm "Khôi phục mặc định".

## 4. IP của client và proxy tin cậy

Backend không nhận request trực tiếp từ trình duyệt mà qua route của Next (BFF), nên `remoteAddr` luôn là IP của
server Next. Để lớp IP đếm theo người dùng thật:

1. **Web** (`apps/web/lib/client-ip.ts`) gửi kèm `X-Forwarded-For` khi gọi API, lấy **địa chỉ cuối** trong header
   `x-forwarded-for` mà Next nhận được. Next tự điền header này bằng địa chỉ socket khi request chưa có.
2. **Backend** (`ClientIpResolver`) chỉ tin `X-Forwarded-For` khi `remoteAddr` nằm trong `RATE_LIMIT_TRUSTED_PROXIES`.
   Header được duyệt từ phải sang trái, bỏ qua các proxy tin cậy; địa chỉ đầu tiên không tin cậy là IP client. Không có
   header dùng được thì lấy `remoteAddr`.

Không bao giờ điền dải IP của client hay `0.0.0.0/0` vào `RATE_LIMIT_TRUSTED_PROXIES`, nếu không ai cũng tự chọn được
IP của mình. Nếu trình duyệt gọi thẳng backend (không qua Next), để biến này rỗng.

IPv6 được gom theo dải **/64** (nhà mạng thường cấp cả dải /64 cho một thuê bao, đổi địa chỉ trong dải sẽ né được
hạn mức). IPv4 và IPv4-mapped IPv6 giữ nguyên. Vì vậy khi chạy trên máy, `::1` hiện trên trang thống kê là
`0:0:0:0::/64`.

> **Giới hạn đã biết:** Next chỉ điền `x-forwarded-for` khi request **chưa có** header này. Nếu Next được mở thẳng ra
> Internet, client tự gửi `X-Forwarded-For` thì Next giữ nguyên, và client tự chọn được IP cho các hạn mức theo IP.
> Hạn mức theo người dùng và theo tài khoản không bị ảnh hưởng. Khi chạy thật, đặt Next sau một reverse proxy (Nginx,
> load balancer…) **ghi đè** `X-Forwarded-For` bằng địa chỉ thật của kết nối.

## 5. Chống dò mật khẩu

`LoginAttemptLimiter`, gọi trong `AuthService.login`:

- Trước khi kiểm tra mật khẩu chỉ **xem** bucket còn lượt hay không, chưa trừ. Hết lượt thì trả `429` ngay, kể cả khi
  lần này nhập đúng mật khẩu.
- Chỉ **sai mật khẩu** mới trừ một lượt. Đăng nhập đúng không trừ. Tài khoản `PENDING`, `LOCKED`, `DISABLED` bị từ
  chối trước bước kiểm tra mật khẩu nên cũng không trừ.
- Khoá đếm là `account:<id>` khi tài khoản tồn tại, nên username và email của cùng tài khoản dùng chung bucket. Tài
  khoản không tồn tại thì dùng `name:<sha256(chuỗi đã nhập, chữ thường)>`: vẫn chặn được việc dò tên không có thật, và
  chuỗi dài bao nhiêu cũng không làm phình bộ nhớ.
- **Không khoá hẳn tài khoản.** Hết thời gian chờ là đăng nhập lại được. Đánh đổi: người khác cố tình nhập sai có thể
  bắt chủ tài khoản chờ tối đa một cửa sổ (mặc định 15 phút), nhưng không khoá vĩnh viễn được ai.

`failedLoginCount` của tài khoản vẫn tăng mỗi lần sai mật khẩu như trước, chỉ để audit, không dùng để chặn. Trạng thái
`LOCKED` (mã lỗi `ACCOUNT_LOCKED`) chỉ do quản trị viên đặt.

## 6. Response khi bị chặn

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 42
RateLimit-Limit: 300
RateLimit-Remaining: 0
RateLimit-Reset: 42
Content-Type: application/json
```

```json
{
  "success": false,
  "data": null,
  "error": { "code": "RATE_LIMIT_EXCEEDED", "message": "Too many requests. Please try again later.", "field": null }
}
```

| Header | Ý nghĩa |
|:-------|:--------|
| `Retry-After` | Số giây cần chờ trước khi có lượt tiếp theo; chỉ có ở response `429` |
| `RateLimit-Limit` | `max-requests` của mức đã áp dụng |
| `RateLimit-Remaining` | Số lượt còn lại |
| `RateLimit-Reset` | Số giây cho tới khi bucket đầy lại |

Request được cho qua cũng có ba header `RateLimit-*`. Khi cả hai lớp IP và người dùng cùng chạy, header của lớp người
dùng ghi đè lớp IP. Bốn header này được expose qua CORS. `IpRateLimitFilter` chạy sau `CorsFilter`, nên response `429`
vẫn có header CORS cho origin được phép.

Mọi số giây đều làm tròn lên, để client không thử lại quá sớm.

**Phía web:** BFF đổi `message` của mọi response `429` thành câu tiếng Việt kèm thời gian chờ, ví dụ "Bạn thao tác quá
nhanh. Vui lòng thử lại sau 8 phút.", vì header `Retry-After` không đi qua BFF tới trình duyệt
(`apps/web/lib/rate-limit.ts`). Refresh bị `429` **không xoá** cookie phiên: gọi quá nhanh không phải là phiên hết hạn,
nên người dùng không bị đăng xuất. Client chỉ refresh lại một lần khi gặp `UNAUTHORIZED`, nên không có vòng lặp gửi
lại.

## 7. Thống kê

`GET /api/system/rate-limit/stats`, cần permission `system.rate_limit.read` (`SYSTEM_ONLY`, chỉ seed cho
`SYSTEM_ADMIN`). Admin Console hiển thị dữ liệu này ở trang **Giới hạn truy cập** (`/admin/rate-limit`).

```json
{
  "success": true,
  "data": {
    "startedAt": "2026-10-23T14:07:00Z",
    "enabled": true,
    "policies": [
      {
        "policy": "api", "scope": "USER", "enabled": true, "canDisable": true, "customized": true,
        "maxRequests": 200, "windowSeconds": 60, "defaultMaxRequests": 300, "defaultWindowSeconds": 60,
        "allowed": 1520, "blocked": 3
      }
    ],
    "topBlocked": [
      { "policy": "api", "scope": "USER", "key": "hr01", "blocked": 3 }
    ]
  },
  "error": null
}
```

| Trường | Ý nghĩa |
|:-------|:--------|
| `startedAt` | Lúc bắt đầu đếm, tức lần khởi động backend gần nhất |
| `policies[]` | Mọi mức, kèm hạn mức đang áp dụng và số request được cho qua / bị chặn |
| `policies[].scope` | `IP`, `USER` hoặc `ACCOUNT`: bucket của mức này đếm theo gì |
| `policies[].maxRequests`, `windowSeconds` | Hạn mức **đang áp dụng** |
| `policies[].defaultMaxRequests`, `defaultWindowSeconds` | Hạn mức trong `application.properties` |
| `policies[].enabled` | `false` khi quản trị viên đã tắt mức này (mục 8) |
| `policies[].customized` | `true` khi mức có thay đổi lưu trong database (đổi hạn mức hoặc tắt/bật) |
| `policies[].canDisable` | `false` với `login-failure`: chỉ đổi được hạn mức, không tắt được |
| `topBlocked[]` | Tối đa 20 khoá bị chặn nhiều nhất: IP, username, hoặc `account:<id>` / `name:<sha256>` với `login-failure` |

Cách đọc số liệu:

- Một request bị chặn chỉ được tính **một lần**, ở mức đã chặn nó, nên cộng `blocked` của mọi mức ra đúng tổng số
  request bị chặn.
- `allowed` thì **không cộng được** giữa các mức: một request đã đăng nhập được tính ở cả lớp IP lẫn lớp người dùng.
- `allowed` của `login-failure` là số lần đăng nhập đã qua bước kiểm tra, không phải số lần sai mật khẩu.
- Backend nhớ số lần bị chặn của tối đa 1.000 khoá; vượt quá thì Caffeine loại bớt khoá ít gặp, nên con số của
  một khoá hiếm khi bị chặn có thể bị đếm lại từ 0.

Danh sách `topBlocked` chứa username và IP, là dữ liệu nhạy cảm; vì vậy permission là `SYSTEM_ONLY`, không gán được
cho custom role.

### 7.1. Theo thời gian

`GET /api/system/rate-limit/timeline?range=HOUR&policy=api`, cùng permission `system.rate_limit.read`.

| Tham số | Giá trị | Mặc định |
|:--------|:--------|:---------|
| `range` | `HOUR` (60 phút, mỗi điểm 1 phút), `DAY` (24 giờ, mỗi điểm 15 phút), `WEEK` (7 ngày, mỗi điểm 1 giờ) | `HOUR` |
| `policy` | Tên mức ở bảng mục 2; bỏ trống là tất cả các mức | tất cả |

```json
{
  "success": true,
  "data": {
    "range": "HOUR",
    "policy": "api",
    "slotSeconds": 60,
    "startedAt": "2026-10-24T05:53:00Z",
    "points": [
      { "start": "2026-10-24T05:54:00Z", "requests": 83, "blocked": 2 }
    ]
  },
  "error": null
}
```

- `points` luôn đủ cả khoảng (60, 96 hoặc 168 điểm), cũ nhất trước, điểm cuối là khung đang chạy. Khung trước
  `startedAt` bằng 0 vì số liệu nằm trong bộ nhớ.
- Với một mức: `requests` là mọi request được mức đó đếm, gồm cả request bị chặn; `blocked` là số bị chặn.
- Với tất cả các mức: `requests` chỉ cộng các mức lớp IP, vì mỗi request `/api/**` đi qua đúng một mức IP (trừ các
  API của mục 7 và 8, không bị đếm); `blocked` cộng mọi mức, vì một request chỉ bị chặn một lần.
- Mức đang tắt không được đếm.
- Sai tham số: `range` lạ trả `400 VALIDATION_ERROR`, `policy` lạ trả `404 RATE_LIMIT_POLICY_NOT_FOUND`.

`RateLimitTimeline` giữ mỗi mức ba vòng đếm cố định (60 + 96 + 168 ô), ô cũ được xoá khi tới lượt dùng lại, nên bộ nhớ
không tăng dù backend chạy bao lâu.

Trang **Giới hạn truy cập** vẽ dữ liệu này thành hai biểu đồ chung trục thời gian, "Request" và "Bị chặn": số bị
chặn thường rất nhỏ so với số request nên vẽ chung một trục sẽ thành đường sát đáy. Vạch đứt đánh dấu lúc backend
khởi động. Trang tự tải lại mỗi 15 giây khi đang mở (tab bị ẩn thì bỏ qua lượt đó); nút "Tự động làm mới" trên đầu
trang tắt việc này. Các API của trang không bị rate-limit (mục 1), nên tự tải lại không tốn lượt của ai.

## 8. Chỉnh hạn mức trên giao diện

Người có permission `system.rate_limit.manage` (`SYSTEM_ONLY`, chỉ seed cho `SYSTEM_ADMIN`) chỉnh từng mức ở trang
**Giới hạn truy cập**, qua menu "…" của từng dòng: **Sửa hạn mức**, **Tắt mức này** / **Bật lại**, **Khôi phục
mặc định**.

| API | Body | Kết quả |
|:----|:-----|:--------|
| `PUT /api/system/rate-limit/policies/{policy}` | `{ "enabled": true, "maxRequests": 200, "windowSeconds": 60 }` | Lưu hạn mức và trạng thái bật/tắt của mức, áp dụng ngay |
| `DELETE /api/system/rate-limit/policies/{policy}` | không có | Xoá thay đổi đã lưu: mức về giá trị `application.properties` và được bật lại |

`{policy}` là tên mức ở bảng mục 2, ví dụ `heavy-write`. Cả hai API trả về nội dung giống `GET .../stats` (mục 7) với
thay đổi đã có hiệu lực, nên trang cập nhật bảng mà không cần tải lại.

| Trường | Ràng buộc |
|:-------|:----------|
| `enabled` | Bắt buộc. `false` với `login-failure` bị từ chối (`400 RATE_LIMIT_POLICY_REQUIRED`) |
| `maxRequests` | Bắt buộc, 1–100.000 |
| `windowSeconds` | Bắt buộc, 1–86.400 (tối đa 24 giờ) |

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 400 | `VALIDATION_ERROR` | Thiếu trường hoặc ngoài khoảng cho phép |
| 400 | `RATE_LIMIT_POLICY_REQUIRED` | Tắt mức `login-failure` |
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có `system.rate_limit.manage` |
| 404 | `RATE_LIMIT_POLICY_NOT_FOUND` | Tên mức không tồn tại |

Cách hoạt động (`RateLimitSettingsService`):

- Mỗi mức đã chỉnh là một dòng trong bảng `rate_limit_policy_settings` (migration `V11`), kèm người chỉnh và thời
  điểm. Mức không có dòng nào dùng giá trị trong `application.properties` và luôn bật. Database cũng có CHECK chặn
  giá trị ngoài khoảng và chặn tắt `login-failure`.
- Lưu xong, `RateLimitService` thay hạn mức của mức đó **ngay**. Mọi bucket của mức đó được tạo lại đầy, tức là mọi
  người được đếm lại từ đầu theo hạn mức mới. Số liệu `allowed` / `blocked` không bị xoá.
- Khi backend khởi động, các dòng đã lưu được nạp lại trước khi nhận request, nên thay đổi không mất khi khởi động
  lại.
- Mức bị tắt thì request thuộc mức đó đi qua lớp của nó mà không bị đếm, không có header `RateLimit-*` của lớp đó.
  Lớp còn lại vẫn áp dụng: tắt `api` thì trần `ip-ceiling` theo IP vẫn còn.
- Không có nút tắt toàn bộ: `RATE_LIMIT_ENABLED` (mục 3) chỉ đổi được bằng biến môi trường.

## 9. Giới hạn hiện tại

- **Mất khi khởi động lại:** bucket và số liệu thống kê, kể cả biểu đồ theo thời gian, nằm trong bộ nhớ; khởi động
  lại backend là về 0, nên biểu đồ 7 ngày chỉ có dữ liệu từ lần khởi động gần nhất. Hạn mức chỉnh trên giao diện thì
  không mất vì nằm trong database.
- **Một instance:** chạy nhiều instance backend thì mỗi instance có bucket riêng, hạn mức thực tế thành
  `hạn mức × số instance`. Khi cần, đổi chỗ lưu bucket trong `RateLimitService` sang Redis (Bucket4j có sẵn
  `LettuceBasedProxyManager`); hai filter, `LoginAttemptLimiter` và API thống kê giữ nguyên. Hạn mức chỉnh trên
  giao diện cũng chỉ áp dụng ngay cho instance nhận request; instance khác chỉ nạp lại khi khởi động.
- **Bộ nhớ có giới hạn:** mỗi mức giữ tối đa 10.000 bucket (Caffeine). Bucket không được dùng trong một cửa sổ sẽ bị
  xoá, việc này không làm mất gì vì bucket đó đã đầy lại.
- **Giả IP khi gọi thẳng vào Next:** xem mục 4.

## 10. Kiểm thử

Bộ test chung chạy với rate-limit **tắt**: `maven-surefire-plugin` đặt system property `rate-limit.enabled=false`, vì
test đăng nhập nhiều hơn người thật rất nhiều. Các test rate-limit tự bật lại bằng
`@SpringBootTest(properties = "rate-limit.enabled=true")` kèm hạn mức nhỏ.

Chạy riêng các test rate-limit trong IDE thì không sao. Chạy **cả bộ test** trong IDE (không qua `mvnw`) thì
rate-limit vẫn bật, các test đăng nhập có thể bị `429`; khi đó thêm `-Drate-limit.enabled=false` vào VM options.

| Test | Kiểm tra |
|:-----|:---------|
| `RateLimitServiceTest` | Hết lượt, nạp lại, khoá và mức đếm riêng, `check` không trừ lượt còn `penalize` thì trừ, thiếu cấu hình thì báo lỗi, đổi hạn mức có hiệu lực ngay, tắt/khôi phục mức, không tắt được `login-failure` |
| `ClientIpResolverTest` | Client gọi thẳng không giả được IP, proxy tin cậy truyền IP, bỏ qua các hop tin cậy, header hỏng thì lấy `remoteAddr`, gom IPv6 /64 |
| `IpRateLimitIntegrationTest` | `429` kèm header và CORS, trần chung theo IP, preflight không bị tính, proxy tin cậy và chống giả IP |
| `UserRateLimitIntegrationTest` | Mỗi người một hạn mức, luật riêng thay mức `api`, `GET`/`HEAD` chung bucket xuất Excel, token sai không bị tính |
| `LoginAttemptLimiterIntegrationTest` | Sai mật khẩu hết lượt thì cả mật khẩu đúng cũng chờ, đăng nhập đúng không trừ, username/email chung bucket, tên không tồn tại vẫn bị giới hạn, tài khoản bị khoá không trừ |
| `RateLimitTimelineTest` | Đủ điểm cho cả khoảng, đếm theo khung, cách cộng khi xem tất cả các mức, bỏ khung quá cũ |
| `RateLimitStatsIntegrationTest` | Phân quyền và nội dung API thống kê và API theo thời gian, tham số sai |
| `RateLimitSettingsIntegrationTest` | Phân quyền, hạn mức mới chặn ngay và được lưu, mức tắt cho mọi request qua, khôi phục xoá dòng đã lưu, nạp lại khi khởi động, API chỉnh vẫn gọi được khi hạn mức chặt nhất, không tắt được `login-failure`, giá trị sai |

Khi thêm một mức mới: thêm vào `RateLimitPolicy`, cấu hình trong `application.properties`, luật trong
`UserRateLimitFilter` hoặc `IpRateLimitFilter`, nhãn tiếng Việt trong `apps/web/lib/rate-limit-stats.ts`, bảng mục 2
của tài liệu này, migration mới mở rộng CHECK `chk_rate_limit_policy` của `rate_limit_policy_settings`, và test kiểm
tra mức đó đếm riêng và trả `429` khi hết lượt.

## Code

- [`ratelimit/`](../../src/main/java/com/htttdn/hrm/ratelimit): `RateLimitPolicy`, `RateLimitProperties`,
  `RateLimitService`, `RateLimitTimeline`, `ClientIpResolver`, `IpRateLimitFilter`, `UserRateLimitFilter`,
  `LoginAttemptLimiter`, `RateLimitResponses`
- [`SecurityConfig`](../../src/main/java/com/htttdn/hrm/config/SecurityConfig.java): thứ tự filter, header CORS
- [`AuthService.login`](../../src/main/java/com/htttdn/hrm/service/AuthService.java),
  [`RateLimitStatsController`](../../src/main/java/com/htttdn/hrm/controller/RateLimitStatsController.java),
  [`RateLimitSettingsService`](../../src/main/java/com/htttdn/hrm/service/RateLimitSettingsService.java),
  [`RateLimitPolicySetting`](../../src/main/java/com/htttdn/hrm/entity/RateLimitPolicySetting.java),
  [`V11__rate_limit_settings.sql`](../../src/main/resources/db/migration/V11__rate_limit_settings.sql)
- Web: [`client-ip.ts`](../../../apps/web/lib/client-ip.ts), [`rate-limit.ts`](../../../apps/web/lib/rate-limit.ts),
  [`rate-limit-page.tsx`](../../../apps/web/components/rate-limit/rate-limit-page.tsx),
  [`rate-limit-policy-dialogs.tsx`](../../../apps/web/components/rate-limit/rate-limit-policy-dialogs.tsx),
  [`rate-limit-timeline.tsx`](../../../apps/web/components/rate-limit/rate-limit-timeline.tsx)
