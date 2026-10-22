# Architecture Decision Records

Mỗi file ghi **một** quyết định thiết kế: bối cảnh, quyết định, hệ quả và các phương án đã cân nhắc. Đọc ở đây khi
muốn biết *vì sao* hệ thống làm theo cách hiện tại, trước khi đổi nó.

Các ADR dưới đây được **ghi lại sau** khi quyết định đã được thực hiện, dựa trên code hiện tại. Ngày "áp dụng" là
commit đầu tiên có thay đổi tương ứng trong lịch sử git.

| ADR | Quyết định | Trạng thái |
|:----|:-----------|:-----------|
| [0001](./0001-bff-httponly-cookies.md) | Web giữ JWT trong cookie `HttpOnly` qua BFF, trình duyệt không cầm token | Đang áp dụng |
| [0002](./0002-authorization-in-service-layer.md) | Kiểm tra quyền ở tầng service, không ở controller | Đang áp dụng |
| [0003](./0003-data-scope-in-application.md) | Lọc phạm vi dữ liệu ở tầng ứng dụng bằng JPA Specification, không dùng Row-Level Security của PostgreSQL | Đang áp dụng |
| [0004](./0004-database-exclusion-constraints.md) | Chặn chồng lấn khoảng hiệu lực bằng `EXCLUDE USING GIST` trong database | Đang áp dụng |

Thêm ADR mới: tạo file `NNNN-ten-ngan.md` theo cùng cấu trúc, thêm một dòng vào bảng. ADR không sửa khi quyết định
thay đổi; viết ADR mới và đổi trạng thái ADR cũ thành "Được thay bởi NNNN".
