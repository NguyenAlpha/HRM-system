# 0003. Lọc phạm vi dữ liệu ở tầng ứng dụng

- **Trạng thái:** Đang áp dụng
- **Áp dụng từ:** 2026-09-25 (`25ddab3`, áp phạm vi truy cập nhân viên)

## Bối cảnh

Mỗi lần gán role có phạm vi `SELF` / `ORG_UNIT` / `LOCATION` / `COMPANY`, và phạm vi tính theo **từng permission**.
Danh sách, chi tiết, báo cáo đều phải chỉ trả về nhân viên trong phạm vi. PostgreSQL có Row-Level Security (RLS) để
lọc dòng ngay trong database.

## Quyết định

Tính phạm vi trong Java (`EmployeeAccessScopeService.resolve`) và áp vào truy vấn dưới dạng JPA
`Specification<Employee>`: điều kiện `EXISTS` trên phân công chính đang hiệu lực, nối bằng AND với bộ lọc người dùng
chọn.

## Hệ quả

- Phạm vi phụ thuộc permission đang xét; điều này dễ biểu diễn trong Java, khó biểu diễn trong policy RLS (policy RLS
  không biết request hiện tại đang cần permission nào nếu không truyền thêm biến phiên).
- Kết nối database dùng một user chung, không cần đặt biến phiên PostgreSQL cho từng request, hợp với connection pool.
- Logic phạm vi kiểm thử được bằng test Java thông thường.
- **Rủi ro:** truy vấn nào quên ghép `Specification` phạm vi sẽ trả về dữ liệu ngoài phạm vi; database không chặn
  giúp. Mọi truy vấn danh sách nhân viên phải đi qua `accessibleEmployees` hoặc kiểm tra tương đương.
- Đọc database trực tiếp (công cụ quản trị, script) không bị lọc.

## Phương án đã cân nhắc

- **Row-Level Security của PostgreSQL:** chặn được cả truy vấn quên lọc, nhưng cần truyền account và permission vào
  phiên database ở mỗi request, policy viết bằng SQL khó đọc và khó test, và khó kết hợp với cây tổ chức đệ quy.
- **Lọc sau khi đọc (trong bộ nhớ):** đơn giản nhưng phân trang sai và tốn tài nguyên với danh sách lớn.

Chi tiết: [DATA_SCOPE.md](../../api/docs/concepts/DATA_SCOPE.md).
