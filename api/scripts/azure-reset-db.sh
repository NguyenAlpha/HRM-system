#!/usr/bin/env bash
# Đặt lại database "hrm" của bản demo trên Azure, như `docker compose down -v` ở local: dừng Web App, xóa và tạo
# lại database trống, bật Web App — lúc khởi động Flyway tạo lại bảng và các seeder chạy lại từ đầu.
# MỌI DỮ LIỆU TRONG DATABASE "hrm" SẼ MẤT. Chỉ đụng tới database "hrm": database khác trên cùng Postgres server
# (ví dụ của quiktech-pos khi dùng chung hạ tầng) giữ nguyên. Chạy bằng Git Bash.
#
#   ./scripts/azure-reset-db.sh                      # hỏi xác nhận rồi đặt lại
#   ./scripts/azure-reset-db.sh --yes                # không hỏi
#   TARGET=standalone ./scripts/azure-reset-db.sh    # chọn bản deploy khi máy có cả hai file secret
#
# Script không build hay deploy code: JAR đang chạy trên Web App sẽ seed database mới. Khi vừa đổi code (nhất là
# seed), chạy script deploy trước rồi mới chạy script này, nếu không bản cũ sẽ seed database mới.
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash: không đổi "/home/..." thành đường dẫn Windows
command -v az >/dev/null || PATH="/c/Program Files/Microsoft SDKs/Azure/CLI2/wbin:$PATH"

cd "$(dirname "$0")/.."     # thư mục api

DB_NAME=hrm

# ── Bản deploy: theo file secret do script deploy tạo ra ─────────────────────
TARGET="${TARGET:-}"
if [[ -z $TARGET ]]; then
    if [[ -f .azure-shared-quiktech.env && -f .azure-standalone.env ]]; then
        echo "Có cả .azure-shared-quiktech.env và .azure-standalone.env — đặt TARGET=shared-quiktech hoặc TARGET=standalone." >&2
        exit 1
    elif [[ -f .azure-shared-quiktech.env ]]; then
        TARGET=shared-quiktech
    elif [[ -f .azure-standalone.env ]]; then
        TARGET=standalone
    else
        echo "Không thấy file secret của bản deploy nào — chạy script deploy trước." >&2
        exit 1
    fi
fi
case $TARGET in
    shared-quiktech)
        # shellcheck disable=SC1091
        source .azure-shared-quiktech.env
        RG="${RG:-rg-quicktech-pos}"
        ;;
    standalone)
        # shellcheck disable=SC1091
        source .azure-standalone.env
        RG="${RG:-rg-hrm}"
        PG="hrm-db-${SUFFIX}"
        ;;
    *)
        echo "TARGET phải là shared-quiktech hoặc standalone." >&2
        exit 1
        ;;
esac
APP="hrm-api-${SUFFIX}"

az account show --query "{subscription:name, user:user.name}" -o table
az webapp show -n "$APP" -g "$RG" -o none 2>/dev/null \
    || { echo "Không thấy Web App $APP trong $RG." >&2; exit 1; }

if [[ ${1:-} != --yes ]]; then
    echo "Sẽ XÓA TOÀN BỘ dữ liệu database '$DB_NAME' trên $PG ($TARGET, Web App $APP) rồi seed lại."
    read -r -p "Gõ 'reset' để tiếp tục: " answer
    [[ $answer == reset ]] || { echo "Đã hủy."; exit 1; }
fi

# ── 1. Dừng app: Postgres không xóa được database còn kết nối ───────────────
echo "==> Dừng Web App $APP"
az webapp stop -n "$APP" -g "$RG" -o none

# ── 2. Xóa và tạo lại database trống ────────────────────────────────────────
echo "==> Xóa database $DB_NAME trên $PG"
az postgres flexible-server db delete -g "$RG" --server-name "$PG" --name "$DB_NAME" --yes -o none
echo "==> Tạo lại database $DB_NAME"
az postgres flexible-server db create -g "$RG" --server-name "$PG" --name "$DB_NAME" -o none

# ── 3. Bật app: Flyway + seed chạy lại từ đầu ───────────────────────────────
echo "==> Bật Web App $APP"
az webapp start -n "$APP" -g "$RG" -o none

# Chưa có endpoint health: GET / trả 401 nghĩa là Spring Security đã trả lời → app đã chạy
# (trong lúc container khởi động, App Service trả 503).
HOST=$(az webapp show -n "$APP" -g "$RG" --query defaultHostName -o tsv)
echo "==> Chờ app khởi động (Flyway + seed, có thể vài phút)"
for _ in $(seq 1 120); do
    if [[ $(curl -s -o /dev/null -m 20 -w "%{http_code}" "https://$HOST/") =~ ^40[13]$ ]]; then
        echo
        echo "Đã đặt lại database. API: https://$HOST"
        echo "Owner:      owner / password"
        exit 0
    fi
    sleep 5
done
echo "API chưa sẵn sàng — xem log: az webapp log tail -n $APP -g $RG" >&2
exit 1
