# Sao lưu và phục hồi cơ sở dữ liệu

Database HRM chạy trong container PostgreSQL `database` của `docker-compose.yaml` (database `hrm`, user
`hrm-user`). Bản sao lưu là **một file SQL thuần** do `pg_dump` tạo, gồm cả cấu trúc (bảng, ràng buộc,
trigger, lịch sử migration Flyway) lẫn dữ liệu. Cùng một file dùng để phục hồi, chuyển sang máy khác,
hoặc nộp kèm đồ án.

Các script nằm trong `scripts/`, chạy được từ bất kỳ thư mục nào, chỉ cần container `database` đang chạy
(`docker compose up -d database`):

| Việc | Windows (PowerShell) | Git Bash / macOS / Linux |
|---|---|---|
| Sao lưu | `.\scripts\backup.ps1` | `scripts/backup.sh` |
| Sao lưu ra file chỉ định | `.\scripts\backup.ps1 -Output hrm.sql` | `scripts/backup.sh hrm.sql` |
| Phục hồi | `.\scripts\restore.ps1 <file.sql>` | `scripts/restore.sh <file.sql>` |

Nếu PowerShell chặn chạy script, dùng `powershell -ExecutionPolicy Bypass -File .\scripts\backup.ps1`.

## Sao lưu

```powershell
.\scripts\backup.ps1
```

Tạo `backups/hrm-yyyyMMdd-HHmmss.sql` (thư mục `backups/` không được commit). Có thể sao lưu khi API
đang chạy: `pg_dump` đọc một ảnh chụp nhất quán của database tại thời điểm bắt đầu.

Script chạy `pg_dump` bên trong container rồi copy file ra ngoài, nên không cần cài PostgreSQL trên máy
và file luôn ở UTF-8. File có sẵn lệnh xóa đối tượng cũ (`--clean --if-exists`) và không gắn với tên
user tạo ra nó (`--no-owner --no-privileges`), nên phục hồi được vào database đang có dữ liệu lẫn database
trống, kể cả trên máy dùng user khác.

## Phục hồi

1. Dừng API (`Ctrl+C`), vì API giữ kết nối và khóa bảng.
2. Chạy:

   ```powershell
   .\scripts\restore.ps1 backups\hrm-20261015-090000.sql
   ```

3. Khởi động lại API. Flyway thấy lịch sử migration trong bản sao lưu nên không chạy lại migration;
   seeder chỉ thêm những gì còn thiếu.

**Toàn bộ dữ liệu hiện có bị thay bằng dữ liệu trong file.** Nên sao lưu trước khi phục hồi nếu còn cần
dữ liệu hiện tại. Việc phục hồi chạy trong **một transaction**: chỉ cần một câu lệnh lỗi là mọi thay đổi
bị hủy và database giữ nguyên như trước.

Phục hồi vào một database trống (ví dụ máy mới) cũng dùng đúng lệnh trên sau khi
`docker compose up -d database`, không cần chạy API trước.

## File `hrm.sql` để nộp

Sau khi chạy thử hệ thống với dữ liệu mong muốn:

```powershell
.\scripts\backup.ps1 -Output hrm.sql
```

Người nhận dựng lại hệ thống bằng:

```powershell
docker compose up -d database
.\scripts\restore.ps1 hrm.sql
```

rồi khởi động API và Web như [SETUP.md](SETUP.md). Không có Docker thì dùng `psql` của PostgreSQL 16:
tạo database trống rồi chạy `psql -U <user> -d <database> -v ON_ERROR_STOP=1 --single-transaction -f hrm.sql`.

Dữ liệu seed được tạo theo ngày chạy (lịch sử chấm công, kỳ lương lùi về trước), nên `hrm.sql` là ảnh
chụp tại lúc sao lưu. Phục hồi file cũ thì dữ liệu dừng ở ngày đó; các tháng sau không tự có kỳ lương.

## Sao lưu bằng lệnh trực tiếp

Các script chỉ gói các lệnh sau, có thể chạy tay khi cần:

```powershell
docker compose exec -T database pg_dump -U hrm-user -d hrm --clean --if-exists --no-owner --no-privileges -f /tmp/hrm.sql
docker compose cp database:/tmp/hrm.sql hrm.sql

docker compose cp hrm.sql database:/tmp/hrm.sql
docker compose exec -T database psql -U hrm-user -d hrm -v ON_ERROR_STOP=1 --single-transaction -q -o /dev/null -f /tmp/hrm.sql
```

Trên Windows PowerShell 5 không dùng `pg_dump ... > hrm.sql`: chuyển hướng `>` ghi file UTF-16, `psql`
không đọc được.
