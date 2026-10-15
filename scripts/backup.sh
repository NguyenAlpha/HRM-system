#!/usr/bin/env sh
# Sao lưu database HRM ra một file SQL (cấu trúc + dữ liệu), dùng lại được cho restore.sh.
# Cách dùng (container database đang chạy):
#   scripts/backup.sh              -> backups/hrm-YYYYmmdd-HHMMSS.sql
#   scripts/backup.sh hrm.sql      -> hrm.sql
# Biến môi trường tùy chọn: HRM_DB (mặc định hrm), HRM_DB_USER (mặc định hrm-user).
set -eu
# Git Bash trên Windows: giữ nguyên đường dẫn /tmp/... trong container, không đổi thành đường dẫn Windows.
export MSYS2_ARG_CONV_EXCL="/tmp/"
output="${1:-}"
database="${HRM_DB:-hrm}"
user="${HRM_DB_USER:-hrm-user}"
if [ -n "$output" ]; then
  output="$(cd "$(dirname "$output")" && pwd)/$(basename "$output")"
fi
cd "$(dirname "$0")/.."

if [ -z "$output" ]; then
  mkdir -p backups
  output="backups/hrm-$(date +%Y%m%d-%H%M%S).sql"
fi

remote=/tmp/hrm-backup.sql
docker compose exec -T database pg_dump -U "$user" -d "$database" --clean --if-exists --no-owner --no-privileges -f "$remote"
docker compose cp "database:$remote" "$output"
docker compose exec -T database rm -f "$remote"

echo "Đã sao lưu database '$database' vào $output"
