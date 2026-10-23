# Lưu lượng API

Backend đếm mọi request mà nó trả lời để quản trị viên xem API nào được gọi nhiều, chậm hay lỗi nhiều. Số liệu lấy từ
Micrometer, nằm **trong bộ nhớ** và tính từ lần khởi động backend gần nhất.

## 1. Cách đo

`spring-boot-starter-actuator` bật Micrometer. Với mỗi request, Spring Boot ghi vào timer `http.server.requests`, mỗi
tổ hợp tag có một timer riêng:

| Tag | Ví dụ | Ghi chú |
|:----|:------|:--------|
| `method` | `GET` | |
| `uri` | `/api/employees/{id}` | **Mẫu đường dẫn** của controller, không phải đường dẫn thật, nên mọi nhân viên chung một dòng |
| `status` | `200`, `403`, `429` | |

`ApiTrafficService` cộng các timer theo cặp `method` + `uri`: tổng số request, số response `4xx`, số `5xx` và tổng thời
gian, rồi chia ra thời gian trung bình.

Request được trả lời **trước khi chọn được controller** có `uri = UNKNOWN`: thiếu hoặc sai token (`401`), bị rate-limit
chặn (`429`, xem [RATE_LIMIT.md](./RATE_LIMIT.md)), đường dẫn không tồn tại (`404`). Web hiện dòng này là "Chưa tới
controller (401, 404, 429…)".

Không có endpoint `/actuator/*` nào được mở qua HTTP (`management.endpoints.web.exposure.exclude=*`); số liệu chỉ đọc
được qua API dưới đây, có kiểm tra quyền.

## 2. API

`GET /api/system/traffic/stats`, cần permission `system.traffic.read` (`SYSTEM_ONLY`, chỉ seed cho `SYSTEM_ADMIN`).
Admin Console hiển thị dữ liệu này ở trang **Lưu lượng API** (`/admin/traffic`).

```json
{
  "success": true,
  "data": {
    "startedAt": "2026-10-23T14:26:00Z",
    "totalRequests": 39,
    "clientErrors": 0,
    "serverErrors": 0,
    "endpoints": [
      { "method": "GET", "uri": "/api/auth/me", "requests": 14, "clientErrors": 0, "serverErrors": 0, "meanMillis": 15.2 }
    ]
  },
  "error": null
}
```

| Trường | Ý nghĩa |
|:-------|:--------|
| `startedAt` | Lúc bắt đầu đếm, tức lần khởi động backend gần nhất |
| `totalRequests`, `clientErrors`, `serverErrors` | Tổng của mọi dòng trong `endpoints` |
| `endpoints[]` | Mỗi cặp method + mẫu đường dẫn một dòng, xếp theo số request giảm dần |
| `endpoints[].meanMillis` | Thời gian xử lý trung bình (mili giây) của mọi request, kể cả request lỗi |

| HTTP | `error.code` | Nguyên nhân |
|:----:|:-------------|:-----------|
| 401 | `UNAUTHORIZED` | Thiếu hoặc sai access token |
| 403 | `FORBIDDEN` | Không có `system.traffic.read` |
| 429 | `RATE_LIMIT_EXCEEDED` | Hết lượt của mức `api` |

## 3. Cách đọc số liệu

- Request gọi API thống kê cũng được tính, và chỉ được ghi **sau khi** response trả về, nên lần tải trang ngay sau
  không thấy chính nó.
- Thời gian là thời gian backend xử lý (từ lúc nhận tới lúc trả response), không gồm mạng và thời gian của server Next.
- Đây là **trung bình**: một API vừa có request rất nhanh vừa có request rất chậm vẫn có thể có trung bình thấp. Không có
  số lâu nhất, vì Micrometer chỉ giữ giá trị lớn nhất của vài phút gần nhất, dễ gây hiểu nhầm.
- Lần gọi đầu tiên sau khi khởi động thường chậm hơn hẳn (JVM chưa tối ưu, kết nối DB mới mở), nên API ít được gọi dễ
  có trung bình cao.

## 4. Giới hạn hiện tại

- **Mất khi khởi động lại:** mọi timer nằm trong bộ nhớ.
- **Một instance:** chạy nhiều instance thì mỗi instance chỉ thấy request của chính nó.
- **Không có lịch sử theo thời gian:** chỉ có tổng từ lúc khởi động, không vẽ được biểu đồ theo giờ hay theo ngày.

Khi cần lịch sử lâu dài, thêm `micrometer-registry-prometheus` và một server Prometheus (thường kèm Grafana để vẽ
dashboard): Prometheus định kỳ lấy chính các timer này và lưu lại theo thời gian. API và trang hiện có giữ nguyên.

## Code

- [`ApiTrafficService`](../../src/main/java/com/htttdn/hrm/service/ApiTrafficService.java),
  [`ApiTrafficController`](../../src/main/java/com/htttdn/hrm/controller/ApiTrafficController.java),
  [`ApiTrafficResponse`](../../src/main/java/com/htttdn/hrm/dto/response/traffic/ApiTrafficResponse.java)
- Test: [`ApiTrafficIntegrationTest`](../../src/test/java/com/htttdn/hrm/controller/ApiTrafficIntegrationTest.java)
  (phân quyền, đếm request và lỗi theo route, `/actuator` không bị mở)
- Web: [`api-traffic-page.tsx`](../../../apps/web/components/traffic/api-traffic-page.tsx)
