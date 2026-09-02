#!/usr/bin/env bash
# =====================================================================
# Lightmark MSA 全部微服务 Kubernetes 一键部署脚本
# （msa-develop 分支流水线使用，也可在服务器手动执行）
#
# 完成内容：
#   1) 命名空间 / ghcr 拉取凭据 / TLS 证书
#   2) 确保 k8s 内 MySQL 存在（缺失时按 deploy/k8s/mysql.yaml 创建并初始化）
#   3) 数据库拆分：把单体 lightmark 的 20 张表拆分导入 4 个 MSA schema，
#      并给应用账号授权（幂等，可重复执行；复用 scripts/db/split-mysql.sh）
#   4) 为 4 个服务生成独立 Secret（只从服务器配置文件读取，不落盘到 Git）
#   5) 渲染并部署 user/product/order/content 4 个服务 + MSA 独立 Ingress
#   6) 等待全部 Deployment 就绪，逐服务端口转发健康检查
#
# 必需环境变量：TAG、REPO
# 可选环境变量：REGISTRY、NAMESPACE、DEPLOY_DIR、KUBECTL、ENV_FILE、
#              SERVER_ENV_BASE64、MSA_INGRESS_HOST、CERT_DIR、GHCR_USERNAME、GHCR_PAT
#
# 服务器配置文件（ENV_FILE，默认 $DEPLOY_DIR/.env.k8s）必需键：
#   MYSQL_ROOT_PASSWORD  DB_USER  DB_PASSWORD  JWT_SECRET
# 可选键：DB_HOST（默认 mysql，k8s Service 名）、DB_PORT（默认 3306）、
#   以及各服务的 <SVC>_DB_HOST/<SVC>_DB_PORT/<SVC>_DB_NAME/<SVC>_DB_USER/<SVC>_DB_PASSWORD
#   覆盖默认值（未覆盖时按 DB_* 推导，schema 名默认 lightmark_user/product/order/content）
# =====================================================================
set -euo pipefail

TAG="${TAG:?必须设置 TAG（例如 1.0.123）}"
REPO="${REPO:?必须设置 REPO（例如 se-buaa/lightmark）}"
REPO="${REPO,,}"
REGISTRY="${REGISTRY:-ghcr.io}"
NAMESPACE="${NAMESPACE:-lightmark}"
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/lightmark}"
ENV_FILE="${ENV_FILE:-$DEPLOY_DIR/.env.k8s}"
MSA_INGRESS_HOST="${MSA_INGRESS_HOST:-msa.lightmark.ortus.top}"
CERT_DIR="${CERT_DIR:-$HOME/certs}"
COMMIT_SHA="${COMMIT_SHA:-unknown}"

SERVICES=(user product order content)
declare -A SVC_PORTS=([user]=8081 [product]=8082 [order]=8083 [content]=8084)
declare -A SVC_DB_NAMES=([user]=lightmark_user [product]=lightmark_product [order]=lightmark_order [content]=lightmark_content)

mkdir -p "$DEPLOY_DIR"

# ---- kubectl 探测 ----
if [ -z "${KUBECTL:-}" ]; then
  if command -v kubectl >/dev/null 2>&1; then
    KUBECTL=kubectl
  elif command -v k3s >/dev/null 2>&1; then
    KUBECTL="k3s kubectl"
  else
    echo "[FATAL] 未找到 kubectl 或 k3s kubectl" >&2
    exit 1
  fi
fi

# ---- 服务器配置文件：优先本地文件，其次 SERVER_ENV_BASE64 解码 ----
if [ ! -f "$ENV_FILE" ] && [ -n "${SERVER_ENV_BASE64:-}" ]; then
  B64_CLEAN="$(printf '%s' "$SERVER_ENV_BASE64" | grep -vE '^[[:space:]]*-----' | tr -d '[:space:]' || true)"
  if [ -z "$B64_CLEAN" ] || ! printf '%s' "$B64_CLEAN" | base64 -d > "$ENV_FILE" 2>/dev/null || [ ! -s "$ENV_FILE" ]; then
    echo "[FATAL] SERVER_ENV_BASE64 解码失败：Secret 内容不是合法 base64。" >&2
    exit 1
  fi
  chmod 600 "$ENV_FILE"
fi
if [ ! -f "$ENV_FILE" ]; then
  echo "[FATAL] 找不到服务器配置文件：$ENV_FILE" >&2
  echo "       请在部署服务器维护该文件（或提供 SERVER_ENV_BASE64），不要将密码提交到 Git。" >&2
  exit 1
fi
sed -i 's/\r$//' "$ENV_FILE"
chmod 600 "$ENV_FILE"

# ---- 读取配置（不做 shell 求值，避免密码中的特殊字符被解释） ----
get_env() {
  grep -E "^${1}=" "$ENV_FILE" 2>/dev/null | tail -n 1 | cut -d= -f2- || true
}
MYSQL_ROOT_PASSWORD="$(get_env MYSQL_ROOT_PASSWORD)"
DB_HOST_FINAL="$(get_env DB_HOST)";       [ -n "$DB_HOST_FINAL" ]  || DB_HOST_FINAL="mysql"
DB_PORT_FINAL="$(get_env DB_PORT)";       [ -n "$DB_PORT_FINAL" ]  || DB_PORT_FINAL="3306"
DB_USER_FINAL="$(get_env DB_USER)";       [ -n "$DB_USER_FINAL" ]  || DB_USER_FINAL="lightmark"
DB_PASSWORD_FINAL="$(get_env DB_PASSWORD)"
JWT_SECRET="$(get_env JWT_SECRET)"
JWT_ISSUER="$(get_env JWT_ISSUER)";       [ -n "$JWT_ISSUER" ]     || JWT_ISSUER="lightmark"
JWT_EXPIRE_MINUTES="$(get_env JWT_EXPIRE_MINUTES)"; [ -n "$JWT_EXPIRE_MINUTES" ] || JWT_EXPIRE_MINUTES="120"
for v in MYSQL_ROOT_PASSWORD DB_PASSWORD_FINAL JWT_SECRET; do
  if [ -z "${!v}" ]; then
    echo "[FATAL] $ENV_FILE 缺少必需键 ${v%_FINAL}（拆分数据库与启动服务都需要）" >&2
    exit 1
  fi
done

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
RENDER_DIR="$(mktemp -d "$DEPLOY_DIR/msa-rendered.XXXXXX")"
HISTORY_LOG="$DEPLOY_DIR/deploy-history.log"

log_record() { # $1=status  $2=note
  local ts
  ts="$(date '+%Y-%m-%dT%H:%M:%S%z')"
  printf '%s|msa-deploy|commit=%s|tag=%s|status=%s|note=%s\n' \
    "$ts" "$COMMIT_SHA" "$TAG" "$1" "${2:-}" >> "$HISTORY_LOG"
}

on_error() {
  local rc=$?
  echo "[ERROR] MSA 部署失败（tag=$TAG），输出现场诊断信息（不会打印 Secret 内容）" >&2
  $KUBECTL get deployment,pod,hpa -n "$NAMESPACE" 2>/dev/null | grep -E "user-service|product-service|order-service|content-service|NAME" || true
  $KUBECTL describe deployment -n "$NAMESPACE" 2>/dev/null | grep -A6 -E "Name:|Conditions:" | head -80 || true
  log_record "FAILED" "stage=${STAGE:-unknown}"
  exit "$rc"
}

cleanup() {
  if [ -n "${PF_PID:-}" ]; then
    kill "$PF_PID" 2>/dev/null || true
  fi
  rm -rf "$RENDER_DIR"
}

trap on_error ERR
trap cleanup EXIT
log_record "STARTED" "msa_ingress=${MSA_INGRESS_HOST}"

# =====================================================================
# [1/8] 命名空间 + ghcr 拉取凭据 + TLS 证书
# =====================================================================
STAGE="namespace"
$KUBECTL apply -f "$ROOT_DIR/deploy/k8s/namespace.yaml"

STAGE="ghcr-secret"
if [ -n "${GHCR_USERNAME:-}" ] && [ -n "${GHCR_PAT:-}" ]; then
  $KUBECTL create secret docker-registry ghcr-secret \
    --docker-server=ghcr.io --docker-username="$GHCR_USERNAME" --docker-password="$GHCR_PAT" \
    -n "$NAMESPACE" --dry-run=client -o yaml | $KUBECTL apply -f -
  echo "[OK] ghcr-secret 已更新"
fi

STAGE="tls"
if [ -f "$CERT_DIR/origin.crt" ] && [ -f "$CERT_DIR/origin.key" ]; then
  $KUBECTL create secret tls lightmark-tls --cert="$CERT_DIR/origin.crt" --key="$CERT_DIR/origin.key" \
    -n "$NAMESPACE" --dry-run=client -o yaml | $KUBECTL apply -f -
else
  TMP_TLS="$(mktemp -d)"
  openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
    -keyout "$TMP_TLS/tls.key" -out "$TMP_TLS/tls.crt" \
    -subj "/CN=$MSA_INGRESS_HOST" >/dev/null 2>&1
  $KUBECTL create secret tls lightmark-tls --cert="$TMP_TLS/tls.crt" --key="$TMP_TLS/tls.key" \
    -n "$NAMESPACE" --dry-run=client -o yaml | $KUBECTL apply -f -
  rm -rf "$TMP_TLS"
  echo "[WARN] 未找到证书，已生成自签名证书（浏览器会提示不安全）"
fi

# =====================================================================
# [2/8] 确保 k8s 内 MySQL 存在（缺失时创建并等待首次初始化完成）
# =====================================================================
STAGE="mysql"
if ! $KUBECTL get deployment lightmark-mysql -n "$NAMESPACE" >/dev/null 2>&1; then
  echo "[INFO] lightmark-mysql 不存在，创建并等待初始化（含 database/lightmark.sql 导入）"
  # lightmark-secrets 是 mysql.yaml 的 envFrom 来源；若不存在则先从服务器配置生成
  if ! $KUBECTL get secret lightmark-secrets -n "$NAMESPACE" >/dev/null 2>&1; then
    $KUBECTL create secret generic lightmark-secrets --from-env-file="$ENV_FILE" \
      -n "$NAMESPACE" --dry-run=client -o yaml | $KUBECTL apply -f -
  fi
  if ! $KUBECTL get configmap lightmark-init-sql -n "$NAMESPACE" >/dev/null 2>&1; then
    $KUBECTL create configmap lightmark-init-sql --from-file=lightmark.sql="$ROOT_DIR/database/lightmark.sql" \
      -n "$NAMESPACE" --dry-run=client -o yaml | $KUBECTL apply --server-side -f -
  fi
  $KUBECTL apply -f "$ROOT_DIR/deploy/k8s/mysql.yaml"
  $KUBECTL rollout status deployment/lightmark-mysql -n "$NAMESPACE" --timeout=420s
else
  echo "[SKIP] lightmark-mysql 已存在（数据持久化在 PVC，不重建）"
fi

# =====================================================================
# [3/8] 数据库拆分：单体 lightmark -> 4 个 MSA schema（幂等）+ 应用账号授权
# =====================================================================
STAGE="db-split"
mysql_pod_name() {
  $KUBECTL get pod -n "$NAMESPACE" -l app=lightmark-mysql \
    -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true
}

if command -v mysql >/dev/null 2>&1 && command -v mysqldump >/dev/null 2>&1; then
  echo "[3/8] 宿主机有 mysql 客户端：端口转发后执行拆分（产物保留在 $DEPLOY_DIR/artifacts/db-split）"
  $KUBECTL port-forward -n "$NAMESPACE" service/mysql 3307:3306 >"$RENDER_DIR/mysql-pf.log" 2>&1 &
  PF_PID=$!
  DB_READY=0
  for _ in $(seq 1 30); do
    if MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -P3307 -uroot --connect-timeout=3 -N -e "SELECT 1" >/dev/null 2>&1; then
      DB_READY=1; break
    fi
    sleep 1
  done
  [ "$DB_READY" = 1 ] || { echo "[FATAL] 端口转发后 MySQL 不可达" >&2; exit 1; }
  MYSQL_HOST=127.0.0.1 MYSQL_PORT=3307 MYSQL_USER=root MYSQL_PASSWORD="$MYSQL_ROOT_PASSWORD" \
    EXPORT_DIR="$DEPLOY_DIR/artifacts/db-split" \
    bash "$ROOT_DIR/scripts/db/split-mysql.sh"
  MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -P3307 -uroot \
    -e "GRANT ALL PRIVILEGES ON lightmark_user.* TO '$DB_USER_FINAL'@'%'; GRANT ALL PRIVILEGES ON lightmark_product.* TO '$DB_USER_FINAL'@'%'; GRANT ALL PRIVILEGES ON lightmark_order.* TO '$DB_USER_FINAL'@'%'; GRANT ALL PRIVILEGES ON lightmark_content.* TO '$DB_USER_FINAL'@'%'; FLUSH PRIVILEGES;"
  kill "$PF_PID" 2>/dev/null || true
  PF_PID=""
  echo "[OK] 拆分与授权完成（目标 schema: lightmark_user/product/order/content）"
else
  echo "[3/8] 宿主机无 mysql 客户端：改用 MySQL Pod 内执行拆分（幂等）"
  POD="$(mysql_pod_name)"
  [ -n "$POD" ] || { echo "[FATAL] 找不到 lightmark-mysql Pod" >&2; exit 1; }
  $KUBECTL cp "$ROOT_DIR/scripts/db/split-mysql.sh" "$POD:/tmp/split-mysql.sh" -n "$NAMESPACE"
  $KUBECTL cp "$ROOT_DIR/scripts/db/create-msa-schemas.sql" "$POD:/tmp/create-msa-schemas.sql" -n "$NAMESPACE"
  $KUBECTL exec "$POD" -n "$NAMESPACE" -- bash -c \
    "MYSQL_HOST=127.0.0.1 MYSQL_PORT=3306 MYSQL_USER=root MYSQL_PASSWORD='$MYSQL_ROOT_PASSWORD' EXPORT_DIR=/tmp/db-split bash /tmp/split-mysql.sh"
  $KUBECTL exec "$POD" -n "$NAMESPACE" -- mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
    -e "GRANT ALL PRIVILEGES ON lightmark_user.* TO '$DB_USER_FINAL'@'%'; GRANT ALL PRIVILEGES ON lightmark_product.* TO '$DB_USER_FINAL'@'%'; GRANT ALL PRIVILEGES ON lightmark_order.* TO '$DB_USER_FINAL'@'%'; GRANT ALL PRIVILEGES ON lightmark_content.* TO '$DB_USER_FINAL'@'%'; FLUSH PRIVILEGES;"
  echo "[OK] Pod 内拆分与授权完成"
fi

# =====================================================================
# [4/8] 生成 4 个服务的 Secret（含 <SVC>_DB_* 解析与跨服务地址）
# =====================================================================
STAGE="secrets"
resolve_opt() { # $1=原始值（可为空） $2=回退值
  if [ -n "$1" ]; then printf '%s' "$1"; else printf '%s' "$2"; fi
}
for svc in "${SERVICES[@]}"; do
  SVC_UPPER="$(printf '%s' "$svc" | tr '[:lower:]' '[:upper:]')"
  DB_NAME="${SVC_DB_NAMES[$svc]}"
  H="$(get_env ${SVC_UPPER}_DB_HOST)";     [ -n "$H" ] || H="$DB_HOST_FINAL"
  P="$(get_env ${SVC_UPPER}_DB_PORT)";     [ -n "$P" ] || P="$DB_PORT_FINAL"
  N="$(get_env ${SVC_UPPER}_DB_NAME)";     [ -n "$N" ] || N="$DB_NAME"
  U="$(get_env ${SVC_UPPER}_DB_USER)";     [ -n "$U" ] || U="$DB_USER_FINAL"
  W="$(get_env ${SVC_UPPER}_DB_PASSWORD)"; [ -n "$W" ] || W="$DB_PASSWORD_FINAL"
  SECRET_FILE="$RENDER_DIR/${svc}-service.env"
  {
    printf '%s_DB_HOST=%s\n' "$SVC_UPPER" "$H"
    printf '%s_DB_PORT=%s\n' "$SVC_UPPER" "$P"
    printf '%s_DB_NAME=%s\n' "$SVC_UPPER" "$N"
    printf '%s_DB_USER=%s\n' "$SVC_UPPER" "$U"
    printf '%s_DB_PASSWORD=%s\n' "$SVC_UPPER" "$W"
    printf 'JWT_SECRET=%s\n' "$JWT_SECRET"
    printf 'JWT_ISSUER=%s\n' "$JWT_ISSUER"
    printf 'JWT_EXPIRE_MINUTES=%s\n' "$JWT_EXPIRE_MINUTES"
    printf 'APP_VERSION=%s-service-%s\n' "$svc" "$TAG"
  } > "$SECRET_FILE"
  if [ "$svc" = "user" ]; then
    FE="$(get_env USER_FLYWAY_ENABLED)"; [ -n "$FE" ] || FE="true"
    printf 'USER_FLYWAY_ENABLED=%s\n' "$FE" >> "$SECRET_FILE"
    for k in AUTH_MAIL_USERNAME AUTH_MAIL_PASSWORD AUTH_MAIL_FROM_EMAIL AUTH_MAIL_FROM_NAME OBJECT_STORAGE_BASE_URL AUTH_MAIL_HOST AUTH_MAIL_PORT AUTH_MAIL_SSL_ENABLED; do
      v="$(get_env "$k")"
      [ -n "$v" ] && printf '%s=%s\n' "$k" "$v" >> "$SECRET_FILE"
    done
  fi
  if [ "$svc" = "order" ]; then
    PU="$(get_env PRODUCT_SERVICE_BASE_URL)"; [ -n "$PU" ] || PU="http://product-service:8082"
    UU="$(get_env USER_SERVICE_BASE_URL)";     [ -n "$UU" ] || UU="http://user-service:8081"
    printf 'PRODUCT_SERVICE_BASE_URL=%s\n' "$PU" >> "$SECRET_FILE"
    printf 'USER_SERVICE_BASE_URL=%s\n' "$UU" >> "$SECRET_FILE"
  fi
  $KUBECTL create secret generic "${svc}-service-secrets" --from-env-file="$SECRET_FILE" \
    -n "$NAMESPACE" --dry-run=client -o yaml | $KUBECTL apply -f -
  echo "[OK] Secret ${svc}-service-secrets 已更新（DB: ${H}:${P}/${N}，用户 ${U}）"
done

# =====================================================================
# [5/8] 渲染并应用 4 个服务清单 + MSA Ingress
# =====================================================================
STAGE="apply"
for svc in "${SERVICES[@]}"; do
  IMG="$REGISTRY/$REPO/${svc}-service:${svc}-service-$TAG"
  PLACEHOLDER="__IMAGE_$(printf '%s' "$svc" | tr '[:lower:]' '[:upper:]')__"
  sed -e "s|${PLACEHOLDER}|${IMG}|g" \
      -e "s|__APP_VERSION__|${svc}-service-${TAG}|g" \
      "$ROOT_DIR/deploy/k8s/msa/${svc}-service.yaml" > "$RENDER_DIR/${svc}-service.yaml"
  $KUBECTL apply -f "$RENDER_DIR/${svc}-service.yaml"
  echo "[OK] ${svc}-service 已应用（镜像 ${IMG}）"
done

sed "s|__MSA_INGRESS_HOST__|${MSA_INGRESS_HOST}|g" \
  "$ROOT_DIR/deploy/k8s/msa/msa-ingress.yaml" > "$RENDER_DIR/msa-ingress.yaml"
$KUBECTL apply -f "$RENDER_DIR/msa-ingress.yaml"
echo "[OK] MSA Ingress 已应用（host=${MSA_INGRESS_HOST}）"

# =====================================================================
# [6/8] 等待全部 Deployment 就绪
# =====================================================================
STAGE="rollout"
for svc in "${SERVICES[@]}"; do
  $KUBECTL rollout status "deployment/${svc}-service" -n "$NAMESPACE" --timeout=420s
done

# =====================================================================
# [7/8] 逐服务端口转发健康检查（user 额外检查 ready/version）
# =====================================================================
STAGE="healthcheck"
if ! command -v curl >/dev/null 2>&1; then
  echo "[FATAL] 部署服务器缺少 curl，无法执行 HTTP 验收" >&2
  exit 1
fi
for svc in "${SERVICES[@]}"; do
  SVC_PORT="${SVC_PORTS[$svc]}"
  LOCAL_PORT="$((18000 + SVC_PORT))"
  $KUBECTL port-forward -n "$NAMESPACE" "service/${svc}-service" "${LOCAL_PORT}:${SVC_PORT}" >"$RENDER_DIR/${svc}-pf.log" 2>&1 &
  PF_PID=$!
  UP=0
  for _ in $(seq 1 30); do
    if curl -fsS --max-time 3 "http://127.0.0.1:${LOCAL_PORT}/api/health" 2>/dev/null | grep -q '"UP"'; then
      UP=1; break
    fi
    sleep 1
  done
  [ "$UP" = 1 ] || { echo "[FATAL] ${svc}-service /api/health 未就绪" >&2; exit 1; }
  if [ "$svc" = "user" ]; then
    curl -fsS --max-time 10 "http://127.0.0.1:${LOCAL_PORT}/api/ready" | grep -q '"UP"'
    curl -fsS --max-time 10 "http://127.0.0.1:${LOCAL_PORT}/api/version"
  fi
  echo "[OK] ${svc}-service 健康检查通过（port ${LOCAL_PORT} -> ${SVC_PORT}）"
  kill "$PF_PID" 2>/dev/null || true
  PF_PID=""
done

log_record "SUCCESS" "msa_ingress=${MSA_INGRESS_HOST}"
echo ""
echo "[OK] MSA 全部服务部署成功"
echo "    入口：https://${MSA_INGRESS_HOST}（/api/auth、/api/flights、/api/orders、/api/chat 等前缀路由）"
for svc in "${SERVICES[@]}"; do
  echo "    ${svc}-service: 镜像 $REGISTRY/$REPO/${svc}-service:${svc}-service-$TAG"
done
echo "    日志：$KUBECTL logs deployment/<svc>-service -n $NAMESPACE -f"
echo "    状态：$KUBECTL get deployment,pod,hpa -n $NAMESPACE"
echo "部署记录：$HISTORY_LOG"
