# Thông báo trong HRM Workspace

Khi một đơn nghỉ phép chuyển từ `DRAFT` sang `PENDING`, hệ thống tạo thông báo cho người được
chốt duyệt và các account `HR_MANAGER` đang hoạt động, có quyền `request.read` đối với nhân viên
đó. Một người kiêm cả hai vai trò chỉ nhận một thông báo. Người gửi không tự nhận thông báo.
Việc tạo thông báo nằm cùng giao dịch gửi đơn; nếu gửi đơn thất bại thì không có thông báo.

Khi người duyệt chấp thuận hoặc từ chối đơn `PENDING`, account đang hoạt động của nhân viên
nộp đơn nhận `LEAVE_APPROVED` hoặc `LEAVE_REJECTED`. Thông báo kết quả cùng giao dịch duyệt;
nếu duyệt thất bại thì không có thông báo. Đơn của Giám đốc hoặc Chủ sở hữu tự ghi nhận
được duyệt ngay lúc gửi nên không phát thông báo kết quả cho chính họ.

| Endpoint | Ý nghĩa |
|---|---|
| `GET /api/notifications` | Trả số thông báo chưa đọc và tối đa 30 thông báo mới nhất của account hiện tại |
| `POST /api/notifications/{id}/read` | Đánh dấu thông báo của chính account hiện tại là đã đọc; gọi lặp vẫn giữ thời điểm đọc đầu tiên |

Mọi endpoint yêu cầu phiên đăng nhập. Không thể đọc hoặc đánh dấu thông báo của account khác.
Nút thông báo đang ẩn trong HRM Workspace. API vẫn tạo và cho phép đọc thông báo trong ứng dụng;
chưa gửi email hay push notification.

`requiresApproval=true` khi người nhận là người duyệt được chốt cho một đơn còn `PENDING`.
HR chỉ theo dõi đơn; thông báo không tự trao quyền duyệt. Chi tiết đơn nằm ở
`/leave-requests`. API vẫn trả thông báo đã đọc trong lịch sử gần đây.
