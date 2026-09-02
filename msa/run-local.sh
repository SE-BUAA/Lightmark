#!/usr/bin/env bash
# =====================================================================
# Lightmark MSA 本地一键运行（Ubuntu / macOS）
#
# 不需要 Kubernetes：构建并启动 4 个微服务 Docker 容器（8081-8084），
# 数据库使用服务器现有 MySQL（默认 150.230.223.11:3306，可覆盖）。
#
# 用法：
#   bash msa/run-local.sh
#
# 环境变量（优先读取仓库根目录 .env，其次 shell 环境，最后默认值）：
#   DB_HOST / DB_PORT / DB_USER / DB_PASSWORD / JWT_SECRET
#   DB_ADMIN_USER / DB_ADMIN_PASSWORD   建库引导用的管理员账号（默认 root，可跳过）
#   USER_DB_* / PRODUCT_DB_* / ORDER_DB_* / CONTENT_DB_*
#   SKIP_DB_BOOTSTRAP=1                 跳过建库引导
# =====================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ---------- 1. 读取仓库根 .env（若存在） ----------
if [ -f "../.env" ]; then
  echo "[INFO] 读取 ../.env"
  set -a
  # shellcheck disable=SC1091
  . ../.env
  set +a
fi

# ---------- 2. 导出默认值（compose 插值用） ----------
export DB_HOST="${DB_HOST:-150.230.223.11}"
export DB_PORT="${DB_PORT:-3306}"
export DB_USER="${DB_USER:-se}"
export DB_PASSWORD="${DB_PASSWORD:-}"
export JWT_SECRET="${JWT_SECRET:-local-msa-dev-secret}"
export JWT_ISSUER="${JWT_ISSUER:-lightmark}"
export JWT_EXPIRE_MINUTES="${JWT_EXPIRE_MINUTES:-120}"

resolve() { # $1=目标变量名 $2=默认值
  local v="${!1:-}"
  if [ -z "$v" ]; then v="$2"; fi
  export "$1=$v"
}
resolve USER_DB_HOST "$DB_HOST"
resolve USER_DB_PORT "$DB_PORT"
resolve USER_DB_NAME "lightmark_user"
resolve USER_DB_USER "$DB_USER"
resolve USER_DB_PASSWORD "$DB_PASSWORD"
resolve PRODUCT_DB_HOST "$DB_HOST"
resolve PRODUCT_DB_PORT "$DB_PORT"
resolve PRODUCT_DB_NAME "lightmark_product"
resolve PRODUCT_DB_USER "$DB_USER"
resolve PRODUCT_DB_PASSWORD "$DB_PASSWORD"
resolve ORDER_DB_HOST "$DB_HOST"
resolve ORDER_DB_PORT "$DB_PORT"
resolve ORDER_DB_NAME "lightmark_order"
resolve ORDER_DB_USER "$DB_USER"
resolve ORDER_DB_PASSWORD "$DB_PASSWORD"
resolve CONTENT_DB_HOST "$DB_HOST"
resolve CONTENT_DB_PORT "$DB_PORT"
resolve CONTENT_DB_NAME "lightmark_content"
resolve CONTENT_DB_USER "$DB_USER"
resolve CONTENT_DB_PASSWORD "$DB_PASSWORD"

echo "[INFO] 目标数据库: ${DB_HOST}:${DB_PORT}  用户: ${DB_USER}  各服务 schema: lightmark_user/product/order/content"

# ---------- 3. 建库引导（可选；无权限时给出管理员 SQL） ----------
BOOTSTRAP_SQL="CREATE DATABASE IF NOT EXISTS lightmark_user CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS lightmark_product CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS lightmark_order CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS lightmark_content CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON lightmark_user.* TO '${DB_USER}'@'%';
GRANT ALL PRIVILEGES ON lightmark_product.* TO '${DB_USER}'@'%';
GRANT ALL PRIVILEGES ON lightmark_order.* TO '${DB_USER}'@'%';
GRANT ALL PRIVILEGES ON lightmark_content.* TO '${DB_USER}'@'%';
FLUSH PRIVILEGES;"

if [ "${SKIP_DB_BOOTSTRAP:-0}" = "1" ]; then
  echo "[INFO] SKIP_DB_BOOTSTRAP=1，跳过建库引导（请自行确认 4 个 schema 已存在且有权限）"
elif command -v mysql >/dev/null 2>&1; then
  ADMIN_USER="${DB_ADMIN_USER:-root}"
  ADMIN_PASS="${DB_ADMIN_PASSWORD:-}"
  if MYSQL_PWD="$ADMIN_PASS" mysql -h"$DB_HOST" -P"$DB_PORT" -u"$ADMIN_USER" --connect-timeout=8 -e "$BOOTSTRAP_SQL" 2>/dev/null; then
    echo "[OK] 4 个 MSA schema 已就绪并已授权给 '${DB_USER}'"
  else
    echo "[WARN] 无法以 ${ADMIN_USER} 建库（权限不足或密码错误）。请用管理员账号手动执行一次："
    echo ""
    echo "  mysql -h ${DB_HOST} -P ${DB_PORT} -u root -p"
    echo "  $BOOTSTRAP_SQL"
    echo ""
  fi
else
  echo "[WARN] 未找到 mysql 客户端，跳过建库引导。请用管理员账号手动执行："
  echo "  mysql -h ${DB_HOST} -P ${DB_PORT} -u root -p -e \"${BOOTSTRAP_SQL}\""
fi

# ---------- 4. 构建并启动 4 个服务容器 ----------
if ! command -v docker >/dev/null 2>&1; then
  echo "[FATAL] 未找到 docker" >&2
  exit 1
fi
echo "[INFO] 构建并启动 4 个微服务容器（首次构建需数分钟）..."
docker compose -f docker-compose.local.yml up -d --build

# ---------- 5. 健康检查（最多约 200 秒） ----------
echo "[INFO] 等待服务就绪 ..."
ALL_UP=1
for entry in "user-service 8081" "product-service 8082" "order-service 8083" "content-service 8084"; do
  set -- $entry
  NAME="$1"; PORT="$2"; UP=0
  for _ in $(seq 1 40); do
    if curl -fsS --max-time 3 "http://127.0.0.1:${PORT}/api/health" 2>/dev/null | grep -q '"UP"'; then
      UP=1; break
    fi
    sleep 5
  done
  if [ "$UP" = 1 ]; then
    echo "[OK] ${NAME}  http://127.0.0.1:${PORT}/api/health -> UP"
  else
    echo "[FAIL] ${NAME}  http://127.0.0.1:${PORT}/api/health 未就绪"
    echo "       查看日志：docker compose -f docker-compose.local.yml logs ${NAME}"
    ALL_UP=0
  fi
done

if [ "$ALL_UP" = 1 ]; then
  echo ""
  echo "=========================================================="
  echo " 4 个微服务全部就绪"
  echo "   user-service     http://127.0.0.1:8081/api/health"
  echo "   product-service  http://127.0.0.1:8082/api/health"
  echo "   order-service    http://127.0.0.1:8083/api/health"
  echo "   content-service  http://127.0.0.1:8084/api/health"
  echo ""
  echo " 停止：docker compose -f docker-compose.local.yml down"
  echo " 日志：docker compose -f docker-compose.local.yml logs -f <service>"
  echo "=========================================================="
else
  echo "[ERROR] 部分服务未就绪，请查看上面日志定位问题" >&2
  exit 1
fi
