# Ai duyệt đơn (approval routing)

Tài liệu giải thích hệ thống chọn người duyệt đơn nghỉ, đơn nghỉ việc và tăng ca thế nào. Điểm quan trọng nhất:
**người duyệt được chọn theo tuyến quản lý, không theo permission.** HR có `request.read` để xem và tổng hợp đơn,
nhưng không nằm trong tuyến duyệt.

## 1. Chuỗi người duyệt

`ApproverResolver.approverFor(employee)` chọn người **đầu tiên hợp lệ** trong chuỗi:

```text
1. Quản lý trực tiếp   (manager_employee_id của phân công chính đang mở)
2. Giám đốc            (người giữ role DIRECTOR)
3. Company Owner       (người giữ role COMPANY_OWNER)
```

Một ứng viên bị **bỏ qua** khi:

- là chính nhân viên đó (không ai tự duyệt đơn của mình);
- hồ sơ đã xóa mềm, hoặc đã `RESIGNED` / `TERMINATED` / `RETIRED`;
- không có account, hoặc account không ở trạng thái `ACTIVE`.

Company Owner là đỉnh của sơ đồ nên **không có ai duyệt cho Company Owner** (kết quả rỗng).

"Người giữ role" lấy lần gán role còn hiệu lực có `effective_from` sớm nhất. Nếu nhiều người cùng giữ `DIRECTOR`,
chỉ người được gán sớm nhất được chọn.

## 2. Áp dụng cho từng loại đơn

| Loại | Chọn người duyệt khi nào | Ai được duyệt | Trường hợp đặc biệt |
|:-----|:-------------------------|:--------------|:--------------------|
| Đơn nghỉ | Lúc **nộp** (`submit`), lưu vào `approver_employee_id` | Chỉ người đã được chốt | Giám đốc và Company Owner tự ghi nhận: đơn được duyệt ngay khi nộp, không có người duyệt |
| Đơn nghỉ việc | Lúc **tạo**, lưu vào `approver_employee_id` | Chỉ người đã được chốt | Không có ngoại lệ cho cấp cao: không tìm được người duyệt (ví dụ Company Owner) thì báo `APPROVER_NOT_FOUND` |
| Tăng ca | **Mỗi lần** kiểm tra, theo chuỗi hiện tại | Người duyệt hiện tại của nhân viên | Chỉ Company Owner (không có ai phía trên) tự xác nhận tăng ca của mình |

Hệ quả của việc **chốt người duyệt lúc nộp**: nếu quản lý đổi sau khi đơn đã nộp, đơn vẫn nằm ở người duyệt cũ.
Muốn chuyển cho người mới thì nhân viên hủy đơn rồi nộp lại. Tăng ca thì luôn đi theo tuyến quản lý hiện tại.

Không tìm được người duyệt nào thì báo `409 APPROVER_NOT_FOUND`. Cách xử lý: gán quản lý trực tiếp cho phân công
chính, hoặc bổ nhiệm Giám đốc / Company Owner.

## 3. Đơn nghỉ

```text
DRAFT ──submit──▶ PENDING ──approve──▶ APPROVED
  │                  ├────reject───▶ REJECTED
  └──cancel──┬───────┘
             ▼
         CANCELLED
```

- Chỉ **chính nhân viên** tạo, nộp và hủy đơn của mình (`request.self.*`). HR không tạo hộ được.
- Loại nghỉ quyết định cách trả lương mặc định; chỉ người có `request.manage` được chọn cách trả lương khác.
- Khi duyệt (`applyApproval`), hệ thống:
  1. chặn nếu đã có đơn được duyệt trùng thời gian (database cũng chặn bằng `EXCLUDE USING GIST` trên `tstzrange`);
  2. kiểm tra còn đủ phép năm;
  3. ghi ngày nghỉ vào chấm công (`AttendanceService.applyApprovedLeave`). Ngày thuộc kỳ lương đã duyệt thì bị chặn.

## 4. Đơn nghỉ việc

- Nhân viên tự tạo (`request.self.create`), hoặc người có `request.manage` tạo trong phạm vi của mình. Đơn vào
  `PENDING` ngay, mỗi nhân viên chỉ có một đơn đang mở.
- Ngày làm việc cuối phải từ hôm nay (giờ Việt Nam) trở đi và không trước ngày vào làm.
- Người duyệt có thể đổi ngày làm việc cuối khi duyệt (ví dụ để đủ thời gian báo trước). Từ chối bắt buộc có lý do.
- Job `ResignationCompletionJob` chạy 00:10 hằng ngày (và một lần khi khởi động): đơn đã duyệt có ngày làm việc
  cuối **trước hôm nay** thì kết thúc làm việc với trạng thái `RESIGNED`, ghi người duyệt là người thực hiện.
  Kết thúc làm việc sẽ khóa account và thu hồi role. Nếu HR đã kết thúc làm việc trước đó thì job bỏ qua.

## 5. Tăng ca

- Chấm công có giờ làm vượt ca thì xuất hiện trong danh sách chờ của người duyệt
  (`listOvertimeAwaitingDecision`, tab "Tăng ca chờ duyệt" trên web).
- Người duyệt nhập số phút được trả và hệ số. Hệ số tối thiểu: 1,5 ngày thường, 2 Chủ nhật, 3 ngày lễ công ty. Nhập
  0 phút nghĩa là không trả tăng ca; quyết định vẫn được ghi để ngày đó rời khỏi danh sách chờ.
- Đánh dấu tăng ca miễn thuế (`taxExempt`) là quyết định của người duyệt sau khi đối soát điều kiện pháp luật.

## Code

- [`ApproverResolver`](../../src/main/java/com/htttdn/hrm/service/ApproverResolver.java): `approverFor`, `decidedBy`, `isTopManagement`
- [`LeaveRequestService`](../../src/main/java/com/htttdn/hrm/service/LeaveRequestService.java): `submit`, `approve`, `applyApproval`
- [`ResignationRequestService`](../../src/main/java/com/htttdn/hrm/service/ResignationRequestService.java): `create`, `approve`, `complete`
- [`AttendanceService`](../../src/main/java/com/htttdn/hrm/service/AttendanceService.java): `approveOvertime`, `listOvertimeAwaitingDecision`
- Endpoint: [ATTENDANCE.md](../api/ATTENDANCE.md), [EMPLOYEE.md](../api/EMPLOYEE.md) (đơn nghỉ việc)
