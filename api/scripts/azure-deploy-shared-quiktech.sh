#!/usr/bin/env bash
# Deploy bản demo HRM (có seed) lên Azure App Service (Web App Java 25), dùng chung hạ tầng của quiktech-pos:
# resource group, App Service Plan và PostgreSQL Flexible Server (thêm database "hrm", dùng chung user admin).
# Chạy bằng Git Bash, sau khi đã chạy scripts/azure-deploy.sh của quiktech-pos ít nhất một lần
# (cần hạ tầng của nó đã tạo xong). Muốn deploy không phụ thuộc quiktech-pos: dùng scripts/azure-deploy-standalone.sh.
#
#   az login                                                   # một lần
#   ./scripts/azure-deploy-shared-quiktech.sh                  # lần đầu: tạo DB + Web App + deploy; lần sau: build + deploy lại
#   FRONTEND_ORIGIN=https://<app>.vercel.app ./scripts/azure-deploy-shared-quiktech.sh   # đổi CORS
#   QUIKTECH_ENV=/path/to/quiktech-pos/api/.azure-demo.env ./scripts/azure-deploy-shared-quiktech.sh   # nếu quiktech ở chỗ khác
#
# Secret (JWT, admin) được sinh lần đầu và lưu ở api/.azure-shared-quiktech.env (gitignored), cùng tên Postgres server
# và mật khẩu admin DB chép từ file secret của quiktech-pos. Mất file này = mất mật khẩu admin HRM.
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash: không đổi "/home/..." thành đường dẫn Windows
command -v az >/dev/null || PATH="/c/Program Files/Microsoft SDKs/Azure/CLI2/wbin:$PATH"

cd "$(dirname "$0")/.."     # thư mục api

# Hạ tầng dùng chung — tạo bởi script của quiktech-pos
RG="${RG:-rg-quicktech-pos}"
PLAN=quiktech-pos-plan
DB_ADMIN=quiktechadmin
QUIKTECH_ENV="${QUIKTECH_ENV:-../../../../products/quiktech-pos/api/.azure-demo.env}"

DB_NAME=hrm
JAR=target/hrm-0.0.1-SNAPSHOT.jar
FRONTEND_ORIGIN="${FRONTEND_ORIGIN:-}"

az account show --query "{subscription:name, user:user.name}" -o table

# ── Secret: sinh một lần, lưu local ─────────────────────────────────────────
SECRETS_FILE=.azure-shared-quiktech.env
if [[ ! -f $SECRETS_FILE ]]; then
    [[ -f $QUIKTECH_ENV ]] || { echo "Không thấy $QUIKTECH_ENV — đặt QUIKTECH_ENV trỏ tới file secret của quiktech-pos." >&2; exit 1; }
    QT_SUFFIX=$(grep '^SUFFIX=' "$QUIKTECH_ENV" | cut -d= -f2-)
    QT_DB_PASSWORD=$(grep '^DB_PASSWORD=' "$QUIKTECH_ENV" | cut -d= -f2-)
    cat > "$SECRETS_FILE" <<EOF
SUFFIX=$(openssl rand -hex 3)
PG=quiktech-pos-db-$QT_SUFFIX
DB_PASSWORD=$QT_DB_PASSWORD
JWT_SECRET=$(openssl rand -base64 32)
ADMIN_SEED_PASSWORD=$(openssl rand -base64 12 | tr -d '/+=')Aa1!
FRONTEND_ORIGIN=
EOF
    echo "Đã tạo $SECRETS_FILE — giữ file này, không commit."
fi
# shellcheck disable=SC1090
source "$SECRETS_FILE"
if [[ -n $FRONTEND_ORIGIN ]]; then
    sed -i "s|^FRONTEND_ORIGIN=.*|FRONTEND_ORIGIN=$FRONTEND_ORIGIN|" "$SECRETS_FILE"
else
    FRONTEND_ORIGIN=$(grep '^FRONTEND_ORIGIN=' "$SECRETS_FILE" | cut -d= -f2-)
fi
CORS_ORIGINS="${FRONTEND_ORIGIN:-http://localhost:3000}"

APP="hrm-api-${SUFFIX}"

# ── 1. Hạ tầng: chỉ kiểm tra phần dùng chung, tạo DB + Web App nếu chưa có ──
echo "==> Kiểm tra hạ tầng dùng chung"
az appservice plan show -n "$PLAN" -g "$RG" -o none 2>/dev/null \
    || { echo "Không thấy App Service Plan $PLAN trong $RG — chạy script quiktech-pos trước." >&2; exit 1; }
az postgres flexible-server show -n "$PG" -g "$RG" -o none 2>/dev/null \
    || { echo "Không thấy PostgreSQL $PG trong $RG — chạy script quiktech-pos trước." >&2; exit 1; }

# azure.extensions là tham số chung của cả server và bị ghi đè cả danh sách: giữ UNACCENT cho quiktech-pos,
# thêm BTREE_GIST cho các EXCLUDE constraint của HRM (migration V1). Script quiktech-pos hiện chỉ đặt UNACCENT —
# chạy lại nó sẽ bỏ BTREE_GIST khỏi danh sách cho tới khi sửa script đó.
az postgres flexible-server parameter set -g "$RG" --server-name "$PG" \
    --name azure.extensions --value UNACCENT,BTREE_GIST -o none
if ! az postgres flexible-server db show -g "$RG" --server-name "$PG" --name "$DB_NAME" -o none 2>/dev/null; then
    echo "==> Database $DB_NAME trên $PG"
    az postgres flexible-server db create -g "$RG" --server-name "$PG" --name "$DB_NAME" -o none
fi

if ! az webapp show -n "$APP" -g "$RG" -o none 2>/dev/null; then
    echo "==> Web App $APP (Java 25) trên $PLAN"
    az webapp create -n "$APP" -g "$RG" -p "$PLAN" --runtime "JAVA|25-java25" -o none
fi

# ── 2. Cấu hình (chạy lại mỗi lần để đồng bộ) ───────────────────────────────
echo "==> Cấu hình Web App"
# HRM chưa có endpoint health trả 200 → không đặt healthCheckPath.
# F1 không hỗ trợ Always On — giữ nguyên gói hiện tại của plan dùng chung.
ALWAYS_ON=true
[[ $(az appservice plan show -n "$PLAN" -g "$RG" --query sku.name -o tsv) == F1 ]] && ALWAYS_ON=false
az webapp config set -n "$APP" -g "$RG" --always-on "$ALWAYS_ON" --ftps-state Disabled -o none
az webapp update -n "$APP" -g "$RG" --https-only true -o none

HOST=$(az webapp show -n "$APP" -g "$RG" --query defaultHostName -o tsv)
DB_URL="jdbc:postgresql://$PG.postgres.database.azure.com:5432/$DB_NAME?sslmode=require"
az webapp config appsettings set -n "$APP" -g "$RG" -o none --settings \
    "DB_URL=$DB_URL" "DB_USERNAME=$DB_ADMIN" "DB_PASSWORD=$DB_PASSWORD" \
    "JWT_SECRET=$JWT_SECRET" "JWT_ISSUER=https://$HOST" "ADMIN_SEED_PASSWORD=$ADMIN_SEED_PASSWORD" \
    "CORS_ALLOWED_ORIGINS=$CORS_ORIGINS" "API_DOCS_ENABLED=true" \
    "WEBSITES_CONTAINER_START_TIME_LIMIT=600"
# Không đặt JAVA_OPTS: heap mặc định của JVM là 25% RAM, đủ an toàn khi chạy chung plan với quiktech-pos.
# Start time limit 600s: lần đầu Flyway + seed (6 năm chấm công, các kỳ lương) trên B1 có thể lâu hơn mặc định 230s.

# ── 3. Build JAR ────────────────────────────────────────────────────────────
# application.properties chỉ chứa giá trị mặc định cho dev, mọi giá trị đều ghi đè được bằng app settings ở trên.
echo "==> Build $JAR"
# mvnw cần Git Bash tự đổi đường dẫn /c/... cho java.exe → bỏ MSYS_NO_PATHCONV riêng cho lệnh này
env -u MSYS_NO_PATHCONV ./mvnw -B -q clean package -DskipTests

# ── 4. Upload JAR — Azure lưu thành /home/site/wwwroot/app.jar và restart ───
echo "==> Deploy JAR lên $APP"
az webapp deploy -n "$APP" -g "$RG" --src-path "$JAR" --type jar -o none

# Chưa có endpoint health: GET / trả 401 nghĩa là Spring Security đã trả lời → app đã chạy
# (trong lúc container khởi động, App Service trả 503).
echo "==> Chờ app khởi động (lần đầu gồm Flyway + seed, có thể vài phút)"
for _ in $(seq 1 120); do
    if [[ $(curl -s -o /dev/null -m 20 -w "%{http_code}" "https://$HOST/") =~ ^40[13]$ ]]; then
        echo
        echo "API:        https://$HOST"
        echo "Swagger:    https://$HOST/swagger-ui/index.html"
        echo "CORS:       $CORS_ORIGINS"
        echo "Admin:      admin / (ADMIN_SEED_PASSWORD trong $SECRETS_FILE)"
        echo "Owner:      owner / password"
        exit 0
    fi
    sleep 5
done
echo "API chưa sẵn sàng — xem log: az webapp log tail -n $APP -g $RG" >&2
exit 1
