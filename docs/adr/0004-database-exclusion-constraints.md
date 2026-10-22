# 0004. Chặn chồng lấn khoảng hiệu lực bằng ràng buộc trong database

- **Trạng thái:** Đang áp dụng
- **Áp dụng từ:** 2026-09-24 (`e5af542`, thiết kế schema có `btree_gist`); hiện nằm trong các migration V1–V7

## Bối cảnh

Lương, phân công chính, phụ cấp, hồ sơ bảo hiểm, quy tắc thuế, override quyền… đều là dữ liệu theo khoảng hiệu lực.
Hai khoảng chồng nhau của cùng một đối tượng làm phép tính lương và phân quyền cho ra kết quả không xác định. Chỉ
kiểm tra ở service thì hai transaction song song vẫn có thể cùng thấy "chưa có chồng lấn" rồi cùng ghi.

## Quyết định

- Mỗi bảng theo khoảng hiệu lực có ràng buộc
  `EXCLUDE USING GIST (<khóa> WITH =, DATERANGE(effective_from, effective_to + 1, '[)') WITH &&)`, có thể kèm `WHERE`
  (ví dụ chỉ phân công chính, chỉ đơn đã duyệt). Extension `btree_gist` bật trong `V1__organization.sql`.
- Service vẫn kiểm tra trước để trả mã lỗi cụ thể và khóa dòng cha bằng `SELECT … FOR UPDATE` để ghi tuần tự.
  Ràng buộc database là chốt chặn cuối.

## Hệ quả

- Không thể có dữ liệu chồng lấn, kể cả khi ghi qua script hay khi service có lỗi.
- Phải `flush()` câu đóng dòng cũ trước khi `INSERT` dòng mới, vì ràng buộc kiểm tra ngay ở từng câu lệnh.
- Vi phạm sinh `DataIntegrityViolationException`; nếu service không bắt để đổi mã, người dùng nhận `409 CONFLICT` chung.
- Gắn chặt với PostgreSQL (kiểu range và GiST không có ở mọi database).
- Bảng `account_role_assignments` hiện **chưa** có ràng buộc này; trùng lặp được chặn ở service
  (`ROLE_ASSIGNMENT_EXISTS`).

## Phương án đã cân nhắc

- **Chỉ kiểm tra ở service:** đủ khi có một người dùng, không đủ khi có request đồng thời.
- **Khóa bảng hoặc serializable isolation:** chặn được nhưng giảm khả năng chạy song song và phải xử lý retry.
- **Trigger kiểm tra chồng lấn:** làm được nhưng dài và dễ sai hơn một dòng `EXCLUDE`.

Chi tiết: [EFFECTIVE_DATING.md](../../api/docs/concepts/EFFECTIVE_DATING.md).
