# Cache dữ liệu phía trình duyệt (TanStack Query)

Web giữ lại dữ liệu đã lấy từ API trong **RAM của tab trình duyệt**. Khi người dùng quay lại dữ liệu đã xem,
trang hiện ngay từ bộ nhớ mà không gọi lại API, nên cũng không truy vấn lại database. Việc này do thư viện
[TanStack Query](https://tanstack.com/query/latest) (React Query v5) quản lý.

Hiện áp dụng cho:

| Dữ liệu | Khóa cache | `staleTime` | Mục |
|:--------|:-----------|:------------|:----|
| Danh sách kỳ lương (`/payroll`), theo năm | `["payroll-periods", năm]` | 5 phút | [§3](#3-danh-sách-kỳ-lương-hoạt-động-thế-nào) |
| Thông tin doanh nghiệp (`GET /workforce/company`) | `["company-profile"]` | 30 phút | [§4](#4-thông-tin-doanh-nghiệp) |
| Phụ cấp chức vụ và phụ cấp thâm niên (`/allowances`) | `["position-allowances"]`, `["seniority-allowances"]` | 10 phút | [§5](#5-chính-sách-phụ-cấp) |
| Danh bạ nhân sự cho ô chọn nhân sự (`EmployeePicker`) | `["employee-directory", portal]` | 0, làm mới ở nền | [§6](#6-ô-chọn-nhân-sự-employeepicker) |

Các trang khác vẫn tự gọi API bằng `useEffect` như cũ.

## 1. Cache nằm ở đâu

```
[Trình duyệt]  ──►  [Next.js server (BFF)]  ──►  [Spring Boot API]  ──►  [PostgreSQL]
  QueryClient
  (RAM của tab)
```

- **Tài nguyên:** RAM của tab trình duyệt trên máy người dùng. Không lưu xuống ổ đĩa (không dùng
  `localStorage`), nên F5 hoặc đóng tab là mất.
- **Phạm vi:** mỗi người dùng một bản riêng, không chia sẻ giữa người dùng hay giữa các tab.
- **Tiết kiệm được:** một lần đọc cache trúng bỏ qua cả 3 chặng: trình duyệt → BFF → API → database.

So sánh với tầng của backend:

| Backend (Spring) | Web |
|:-----------------|:----|
| Controller | Component trang trong `components/` |
| Service + cache (`@Cacheable`) | `QueryClient` + `useQuery` |
| `@CacheEvict` | `queryClient.invalidateQueries(...)` |
| Repository | Hàm gọi API trong `lib/<domain>.ts`, `workforceApi` |

## 2. Thành phần trong code

| Ở đâu | Vai trò |
|:------|:--------|
| [`components/portal/portal-shell.tsx`](../components/portal/portal-shell.tsx) | Tạo **một** `QueryClient` cho mỗi phiên đăng nhập và bọc mọi trang bằng `QueryClientProvider`. Đây là chỗ quản lý cache chung |
| [`components/payroll/payroll-shared.tsx`](../components/payroll/payroll-shared.tsx) | `PAYROLL_PERIODS_KEY`: tiền tố khóa cache của danh sách kỳ lương |
| [`components/payroll/payroll-periods-page.tsx`](../components/payroll/payroll-periods-page.tsx) | Đọc danh sách bằng `useQuery`, mỗi năm một khóa |
| [`components/payroll/payroll-period-page.tsx`](../components/payroll/payroll-period-page.tsx) | Đánh dấu danh sách hết hạn sau mỗi thay đổi trên kỳ lương |
| [`components/workforce/workforce-shared.tsx`](../components/workforce/workforce-shared.tsx) | `COMPANY_PROFILE_KEY` và hook `useCompanyProfile()` dùng chung cho thông tin doanh nghiệp |

### Vòng đời cache

- `QueryClient` sống cùng layout `PortalShell`. Đăng xuất hoặc hết phiên sẽ rời layout đó, nên cache bị bỏ.
  Người đăng nhập sau trên cùng tab **không thấy** dữ liệu của người trước.
- `retry: false`: lỗi hiện ngay như các trang khác, không tự gửi lại 3 lần. Việc làm mới access token đã có
  `authorizedRequest` lo.

## 3. Danh sách kỳ lương hoạt động thế nào

```ts
useQuery({
  queryKey: [...PAYROLL_PERIODS_KEY, year],      // ["payroll-periods", 2026]
  queryFn: () => workforceApi(`payroll/periods?year=${year}&size=100&sort=periodStart,desc`),
  staleTime: 5 * 60_000,
})
```

| Thao tác | Có gọi API không |
|:---------|:-----------------|
| Mở trang lần đầu (năm hiện tại) | Có |
| Bấm sang năm chưa xem | Có |
| Quay lại năm đã xem, trong vòng 5 phút | **Không**, lấy từ cache |
| Quay lại năm đã xem sau hơn 5 phút | Hiện ngay dữ liệu cũ, đồng thời tải bản mới ở nền |
| Bấm "Tải lại" | Có, chỉ năm đang xem |
| Mở một kỳ rồi quay lại danh sách, không thay đổi gì | **Không** |
| Tính lương, duyệt, trả, khóa, hủy, mở lại kỳ, thêm/xóa khoản cộng trừ, tạo kỳ mới | Danh sách bị đánh dấu hết hạn, quay lại sẽ tải lại |

Các thông số:

- **`staleTime` (5 phút):** thời gian dữ liệu còn "tươi". Trong khoảng này đọc cache không gọi API.
- **`gcTime` (mặc định 5 phút):** dữ liệu không còn trang nào dùng sẽ bị xóa khỏi RAM sau thời gian này.
- **Invalidation:** `payroll-period-page.tsx` gọi `invalidateQueries({ queryKey: PAYROLL_PERIODS_KEY })` trong
  `reload()`, hàm chạy sau mỗi thao tác thành công. Khóa là tiền tố, nên mọi năm đều bị đánh dấu.

Giới hạn: người **khác** đổi kỳ lương thì trang này không biết ngay. Dữ liệu có thể cũ tối đa 5 phút, hoặc đến
khi bấm "Tải lại".

## 4. Thông tin doanh nghiệp

Tên, mã số thuế, địa chỉ và logo công ty, in ở đầu phiếu lương và bảng lương năm. Trước đây **mỗi lần mở một
phiếu lương** lại gọi `GET /workforce/company` một lần. Giờ ba nơi dùng chung một bản trong cache qua
`useCompanyProfile()`:

| Nơi dùng | File |
|:---------|:-----|
| Đầu phiếu lương khi in (`PayslipDialog` → `CompanyPrintHeader`) | `components/payroll/payroll-shared.tsx` |
| Đầu bảng lương năm ở "Phiếu lương của tôi" | `components/payroll/my-payslips-page.tsx` |
| Trang "Thông tin doanh nghiệp" (`/company`) | `components/organization/company-profile-page.tsx` |

- **`staleTime` và `gcTime` 30 phút:** dữ liệu rất ít khi đổi.
- **Invalidation:** chỉ trang `/company` sửa được dữ liệu này. Sau khi lưu thông tin hoặc đổi/xóa logo, `reload()`
  gọi `invalidateQueries({ queryKey: COMPANY_PROFILE_KEY })`, nên mọi nơi khác thấy bản mới ngay.
- **Đã đo:** mở 4 phiếu lương liên tiếp chỉ phát sinh **1** request `/workforce/company`, trước đây là 4.

## 5. Chính sách phụ cấp

Trang `/allowances` có hai tab, mỗi tab một danh sách: `GET /workforce/compensation/position-allowances` và
`GET /workforce/compensation/seniority-allowances`. Tab không hiện thì bị gỡ khỏi trang, nên trước đây **mỗi lần
chuyển tab** lại gọi API của tab đó. Giờ mỗi danh sách có một khóa cache trong
[`components/workforce/allowance-policy-page.tsx`](../components/workforce/allowance-policy-page.tsx).

- **`staleTime` 10 phút:** chính sách phụ cấp ít khi đổi.
- **Invalidation:** đặt mức phụ cấp chức vụ (trong dialog lịch sử) hoặc sửa bảng thâm niên gọi
  `invalidateQueries` cho đúng khóa của danh sách đó, nên bảng tải lại ngay.
- **"Tải lại":** gọi `refetch()`, chỉ lấy lại danh sách của tab đang mở.
- **Đã đo:** chuyển qua lại giữa hai tab 3 lần chỉ phát sinh **2** request, mỗi danh sách một lần. Trước đây là 7.
- Lịch sử mức phụ cấp của một chức danh (dialog) vẫn gọi API mỗi lần mở, chưa cache.

## 6. Ô chọn nhân sự (`EmployeePicker`)

Đây là đúng ví dụ "tải danh sách một lần, tìm kiếm trên danh sách đã có" của đề bài.
[`components/employee/employee-picker.tsx`](../components/employee/employee-picker.tsx) gồm:

- **`useEmployeeDirectory(portal)`:** tải **toàn bộ** nhân sự trong phạm vi quyền của người dùng bằng một request
  (`GET /employees?size=2000&sort=employeeCode,asc`) và để trong cache.
- **`EmployeePicker`:** ô chọn có ô gõ tìm (Combobox của shadcn/Base UI). Gõ mã hoặc tên thì **lọc ngay trên RAM**,
  không gọi API. Tìm không cần dấu: gõ `tran` ra "Trần Thị Bình". Danh sách hiện tối đa 50 kết quả, gõ thêm để
  thu hẹp. Mỗi nơi dùng truyền `filter` riêng, ví dụ chỉ người có tài khoản đang hoạt động.

Cách làm mới khác các mục trên: `staleTime` để mặc định là 0. Nhân sự và tài khoản bị thay đổi ở rất nhiều chỗ
(tạo, sửa, phân công nhân sự, kích hoạt hoặc khóa tài khoản…), gắn `invalidateQueries` vào từng chỗ dễ sót. Vì
vậy mỗi lần một ô chọn xuất hiện, nó hiện ngay bản trong cache rồi **tải lại ở nền** (stale-while-revalidate).
Gõ tìm thì vẫn không bao giờ gọi API.

Giới hạn: 2000 là cỡ trang lớn nhất mặc định của Spring, đủ cho quy mô ứng dụng nội bộ một công ty. Công ty
lớn hơn thì đổi bên trong `EmployeePicker` sang tìm ở server (gửi `keyword`), nơi dùng không phải sửa.

Đang dùng ở:

| Trang | Ô chọn | Điều kiện lọc |
|:------|:-------|:--------------|
| `/company` | Chuyển giao Company Owner | Đang làm việc, tài khoản đang hoạt động, không phải chính mình |
| `/role-requests` | Đề xuất cấp role, Đề xuất thu hồi role | Có tài khoản |
| `/role-grant` | Cấp role trực tiếp | Mọi nhân sự (người chưa có tài khoản được tạo tài khoản khi cấp) |
| `/role-grant` | Ghi đè quyền | Có tài khoản |

Cấp role trực tiếp có thể tạo tài khoản mới, nên "Cấp tiếp" gọi `invalidateQueries` cho danh bạ.

## 7. Cách trình bày khi demo

Chạy `npm run dev` (Devtools chỉ có trong chế độ dev, bản build production không có).

1. Đăng nhập bằng tài khoản có quyền xem lương, ví dụ `accountant` / `password`, rồi mở **Kỳ lương**.
2. **Tab Network** (F12 → Network → Fetch/XHR, gõ `periods` vào ô lọc):
   - Mở trang: thấy 1 request `payroll/periods?year=2026`.
   - Bấm `‹` sang 2025: thêm 1 request `year=2025`.
   - Bấm `›` về 2026 rồi `‹` về 2025: **không có request mới**.
   - Bấm "Tải lại": thêm 1 request.
3. **Thông tin doanh nghiệp:** mở một kỳ lương, lọc Network bằng `company`, rồi mở và đóng liên tiếp vài phiếu
   lương. Chỉ phiếu đầu tiên tạo request, các phiếu sau lấy từ cache.
4. **Chính sách phụ cấp:** mở `/allowances`, lọc Network bằng `allowances`, chuyển qua lại giữa hai tab vài lần.
   Mỗi danh sách chỉ có một request ở lần đầu.
5. **Ô chọn nhân sự:** đăng nhập `owner`, mở `/company`, lọc Network bằng `employees`. Mở trang có đúng 1 request.
   Gõ vào ô "Người nhận" (ví dụ `tran`, `nv000`) thì danh sách thu hẹp ngay mà **không có request nào**.
6. **TanStack Query Devtools:** bấm biểu tượng TanStack ở **góc trên bên phải** (góc dưới phải là nút của
   Next.js). Bảng hiện các khóa `["payroll-periods",2025]`, `["payroll-periods",2026]` cùng trạng thái
   `fresh` / `stale` / `inactive`, và thời điểm lấy dữ liệu.

## 8. Áp dụng cho trang khác

1. Đặt hằng khóa cache cạnh code của domain, ví dụ `export const EMPLOYEES_KEY = ["employees"] as const`.
2. Trong component, thay cặp `useState` + `useEffect` gọi API bằng `useQuery({ queryKey: [...KEY, ...tham số],
   queryFn })`. Mọi tham số làm đổi kết quả (năm, trang, bộ lọc) phải nằm trong `queryKey`.
3. Chọn `staleTime` theo mức độ dữ liệu hay đổi.
4. Sau mỗi thao tác ghi làm đổi dữ liệu đó, gọi `queryClient.invalidateQueries({ queryKey: KEY })`.
5. **Không** đưa dữ liệu nhạy cảm vào nơi lưu bền như `localStorage`. Cache này chỉ nằm trong RAM là có chủ đích.

## Thuật ngữ để tra cứu

client-side caching, server state management, stale-while-revalidate, cache invalidation, query key,
`staleTime` / `gcTime`.
