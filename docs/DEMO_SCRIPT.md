# Kịch bản demo khi báo cáo

Chuỗi thao tác để trình bày hệ thống trên bản live `https://hrm.nhatnguyenhuynh.com` (hoặc máy local). Dữ liệu
"có sẵn" bên dưới đúng với bản live tại ngày 31/10/2026; DB được seed lại thì kỳ lương và các đơn chờ duyệt dịch
theo ngày seed.

Mật khẩu mọi tài khoản demo là `password`.

**Chuẩn bị:** mở 2 cửa sổ trình duyệt (thường + ẩn danh) để đăng nhập 2 người cùng lúc, ví dụ nhân viên gửi đơn ở
cửa sổ này, quản lý duyệt ở cửa sổ kia.

| Tài khoản | Vai trò | Quản lý trực tiếp |
|---|---|---|
| `owner` | Chủ doanh nghiệp, Giám đốc, người duyệt lương | — |
| `hr` | Quản lý nhân sự, toàn công ty | owner |
| `accountant` | Kế toán lương, toàn công ty | owner |
| `branch-hn` | Quản lý chi nhánh Hà Nội (phạm vi theo địa điểm) | owner |
| `employee` | Nhân viên | hr |
| `staff-hn1` | Nhân viên chi nhánh Hà Nội | branch-hn |

**Thứ tự gợi ý nếu ít thời gian:** 1 → 2 → 3 → 6, khoảng 12 phút. Phần 3 (lương) có nhiều quy tắc nghiệp vụ nhất.

## 1. Phân quyền theo vai trò và phạm vi dữ liệu (khoảng 2 phút)

1. Đăng nhập `employee`: menu chỉ có hồ sơ, đơn nghỉ, chấm công và phiếu lương của chính mình.
2. Đăng nhập `hr`, mở **Nhân viên**: thấy toàn bộ công ty.
3. Đăng nhập `branch-hn`, mở **Nhân viên**: chỉ thấy người của chi nhánh Hà Nội.

Điểm để nói: cùng một màn hình nhưng dữ liệu khác nhau theo phạm vi của vai trò (`SELF` / `LOCATION` / `COMPANY`).
API kiểm tra quyền cho từng request, không chỉ ẩn nút trên giao diện.

## 2. Nghỉ phép từ đầu đến cuối (khoảng 3 phút)

1. Với `employee`, mở **Đơn nghỉ**: xem số dư phép năm (12 ngày mỗi năm, tính theo số tháng làm việc). Tạo đơn
   nghỉ phép năm vào một ngày làm việc tuần sau rồi gửi.
2. Với `hr` (quản lý của employee), mở **Đơn nghỉ**, tìm đơn trong danh sách chờ duyệt và bấm duyệt.
3. Quay lại `employee`: đơn đã duyệt, số dư phép giảm.
4. Thử các trường hợp lỗi, hệ thống báo bằng câu tiếng Việt rõ ràng:
   - tạo đơn chỉ gồm Chủ nhật → báo khoảng thời gian không có giờ làm;
   - xin vượt số phép còn lại → báo không đủ phép;
   - xin trùng ngày đã có đơn → báo ngày đó đã có đơn.

Có sẵn: `branch-hn` có 3 đơn của nhân viên chi nhánh chờ duyệt, dùng để demo quản lý chi nhánh duyệt đơn.

## 3. Lương: tính, duyệt, trả, khoá (khoảng 5 phút)

Có sẵn: kỳ **09/2026** ở trạng thái **Đã tính**, do `accountant` tính.

1. Với `owner`, mở **Lương** → kỳ 09/2026, rồi mở một phiếu lương. Chỉ các phần: lương theo ngày công, phụ cấp
   chức vụ và thâm niên, tăng ca, BHXH/BHYT/BHTN, thuế TNCN và thực nhận.
2. **Nguyên tắc bốn mắt:** đăng nhập `accountant` và thử duyệt kỳ này → bị chặn, vì người tính lương không được
   tự duyệt.
3. Với `owner`: **Duyệt**, sau đó **Đánh dấu đã trả**, sau đó **Khoá kỳ**, rồi **Xuất Excel**.
4. Với `hr`, sửa chấm công của một ngày trong tháng 9 → bị chặn vì kỳ lương đã chốt.
5. Với `accountant`, **Tạo kỳ lương** 10/2026 rồi bấm **Tính lương** → hệ thống từ chối và nói rõ nhân viên nào,
   ngày nào còn thiếu chấm công. Điểm để nói: không bao giờ tính lương trên dữ liệu dở dang.

> ⚠️ Bước 3 thay đổi dữ liệu live và **không hoàn tác được** (kỳ đã khoá thì không mở lại được). Tập trước ở máy
> local; khi báo cáo thật thì làm trên bản live đúng một lần, hoặc reset DB trước bằng `api/scripts/azure-reset-db.sh`.

## 4. Cấp quyền cần chủ doanh nghiệp duyệt (khoảng 2 phút)

Có sẵn: một yêu cầu cấp vai trò **Quản lý nhân sự**, phạm vi một đơn vị, cho `employee` (NV0003).

1. Với `owner`, mở **Yêu cầu cấp quyền** rồi duyệt.
2. `employee` đăng xuất rồi đăng nhập lại: menu có thêm các chức năng nhân sự, nhưng chỉ cho nhân viên thuộc đơn vị
   đó.

Điểm để nói: có các chính sách cấp vai trò khác nhau (HR gán được, hoặc phải chủ doanh nghiệp duyệt). Quyền mới chỉ
có hiệu lực từ phiên đăng nhập mới.

## 5. Nghỉ việc (khoảng 2 phút)

1. Với `staff-hn1`, mở **Đơn nghỉ việc** và gửi đơn với ngày làm việc cuối khoảng 2 tuần sau.
2. Với `branch-hn`, duyệt đơn.
3. Với `hr`, mở hồ sơ `staff-hn1`: hồ sơ hiện "đã lên lịch nghỉ". Nhân viên vẫn làm tới ngày cuối, sau đó hệ thống
   tự khoá tài khoản và thu hồi vai trò.

## 6. Báo cáo (khoảng 2 phút)

1. Với `hr`, mở lần lượt: **Báo cáo → Cơ cấu nhân sự** (trình độ, thâm niên), **Biến động** (vào/nghỉ, tỉ lệ nghỉ
   việc), **Tình hình tháng**, **Lương**.
2. Mở cùng báo cáo bằng `branch-hn`: số liệu chỉ của chi nhánh Hà Nội.

## 7. Bảo mật đăng nhập và Admin Console (khoảng 1 phút)

1. Ở `/login`, nhập sai mật khẩu 6 lần với một tên **không tồn tại** (ví dụ `demo-sai`) → bị tạm chặn và nhận
   thông báo thử lại sau. Không thử bằng tài khoản demo: tài khoản đó sẽ bị chặn đăng nhập 15 phút.
2. Mở `/admin/login`, đăng nhập `admin` (bản live: mật khẩu `ADMIN_SEED_PASSWORD` trong
   `api/.azure-shared-quiktech.env`; máy local: `Admin@123`). Xem các trang **Rate limit** và **Traffic** để thấy
   lượt vừa bị chặn.
