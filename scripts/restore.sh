#!/usr/bin/env sh
# Phục hồi database HRM từ một file do backup.sh (hoặc backup.ps1) tạo ra. Dữ liệu hiện có bị thay
# toàn bộ bằng dữ liệu trong file. Dừng API trước khi chạy.
# Cách dùng (container database đang chạy):
#   scripts/restore.sh backups/hrm-20261015-090000.sql
# Biến môi trường tùy chọn: HRM_DB (mặc định hrm), HRM_DB_USER (mặc định hrm-user).
set -eu
# Git Bash trên Windows: giữ nguyên đường dẫn /tmp/... trong container, không đổi thành đường dẫn Windows.
export MSYS2_ARG_CONV_EXCL="/tmp/"
if [ $# -ne 1 ] || [ ! -f "$1" ]; then
  echo "Cách dùng: scripts/restore.sh <file.sql>" >&2
  exit 1
fi
file="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
database="${HRM_DB:-hrm}"
user="${HRM_DB_USER:-hrm-user}"
cd "$(dirname "$0")/.."

remote=/tmp/hrm-restore.sql
docker compose cp "$file" "database:$remote"
# Một transaction: lỗi ở bất kỳ câu nào thì database giữ nguyên như trước khi restore.
status=0
docker compose exec -T database psql -U "$user" -d "$database" -v ON_ERROR_STOP=1 --single-transaction -q -o /dev/null -f "$remote" || status=$?
docker compose exec -T database rm -f "$remote"
if [ "$status" -ne 0 ]; then
  echo "Restore thất bại, database không thay đổi" >&2
  exit "$status"
fi

echo "Đã phục hồi database '$database' từ $file. Khởi động lại API."
