#!/usr/bin/env bash
# =====================================================================
# Lightmark Kubernetes 部署脚本
# 在部署服务器上执行（GitHub Actions 通过 SSH 调用，也可手动执行）
#
# 必需环境变量：
#   TAG   镜像版本号（如 1.0.123）
#   REPO  GitHub 仓库（如 se-buaa/timemark），用于拼镜像名
# 可选环境变量：
#   REGISTRY            镜像仓库（默认 ghcr.io）
#   COMMIT_SHA          触发部署的提交号（写入部署记录）
#   GHCR_USERNAME        ghcr 用户名（私有镜像必需）
#   GHCR_PAT             ghcr 访问令牌（私有镜像必需）
#   SERVER_ENV_BASE64    服务器 .env 的 base64（首次部署时自动生成 .env）
#   INGRESS_HOST         域名（默认 lightmark.ortus.top）
#   DEPLOY_DIR           部署工作目录（默认 $HOME/lightmark）
#   CERT_DIR             证书目录（默认 $HOME/certs，含 origin.crt / origin.key）
#   NAMESPACE            k8s 命名空间（默认 lightmark）
#   KUBECTL              kubectl 命令（默认自动探测 kubectl / k3s kubectl）
#
# 部署记录：每次部署（成功/失败/回滚）追加写入 $DEPLOY_DIR/deploy-history.log
# =====================================================================
set -euo pipefail

TAG="${TAG:?必须设置 TAG（镜像版本号，如 1.0.123）}"
REPO="${REPO:?必须设置 REPO（如 se-buaa/lightmark）}"
# GHCR 镜像仓库名必须全小写（防御手动调用时传入大写）
REPO="${REPO,,}"
REGISTRY="${REGISTRY:-ghcr.io}"
COMMIT_SHA="${COMMIT_SHA:-unknown}"
INGRESS_HOST="${INGRESS_HOST:-lightmark.ortus.top}"
NAMESPACE="${NAMESPACE:-lightmark}"
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/lightmark}"
CERT_DIR="${CERT_DIR:-$HOME/certs}"

# ---- 探测 kubectl ----
if [ -z "${KUBECTL:-}" ]; then
  if command -v kubectl >/dev/null 2>&1; then
    KUBECTL=kubectl
  elif command -v k3s >/dev/null 2>&1; then
    KUBECTL="k3s kubectl"
  else
    echo "[FATAL] 未找到 kubectl，请先在服务器执行 scripts/server-bootstrap.sh" >&2
    exit 1
  fi
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
HISTORY_LOG="$DEPLOY_DIR/deploy-history.log"
STAGE="init"
BACKEND_IMG="$REGISTRY/$REPO/backend:$TAG"
FRONTEND_IMG="$REGISTRY/$REPO/frontend:$TAG"

# ---- 部署记录（成功/失败/回滚都保留）----
log_record() { # $1=status  $2=note
  local ts
  ts="$(date '+%Y-%m-%dT%H:%M:%S%z')"
  printf '%s|deploy|commit=%s|tag=%s|backend=%s|frontend=%s|status=%s|note=%s\n' \
    "$ts" "$COMMIT_SHA" "$TAG" "$BACKEND_IMG" "$FRONTEND_IMG" "$1" "${2:-}" >> "$HISTORY_LOG"
}

# ---- 失败处理：记录 FAILED 并回滚到上一次成功版本 ----
fail_and_rollback() {
  local rc=$?
  log_record "FAILED" "stage=${STAGE}"
  echo "[ERROR] 部署失败（stage=${STAGE}），尝试回滚到上一次成功版本 ..." >&2
  local prev_line prev_backend prev_frontend
  prev_line="$(grep '|status=SUCCESS' "$HISTORY_LOG" 2>/dev/null | tail -n1 || true)"
  if [ -n "$prev_line" ]; then
    prev_backend="$(echo "$prev_line" | sed -n 's/.*backend=\([^|]*\).*/\1/p')"
    prev_frontend="$(echo "$prev_line" | sed -n 's/.*frontend=\([^|]*\).*/\1/p')"
    if [ -n "$prev_backend" ] && [ "$prev_backend" != "$BACKEND_IMG" ]; then
      $KUBECTL set image "deployment/lightmark-backend" "backend=$prev_backend" -n "$NAMESPACE" 2>/dev/null || true
      $KUBECTL set image "deployment/lightmark-frontend" "frontend=$prev_frontend" -n "$NAMESPACE" 2>/dev/null || true
      $KUBECTL rollout status "deployment/lightmark-backend" -n "$NAMESPACE" --timeout=300s 2>/dev/null || true
      log_record "ROLLED_BACK" "from=${BACKEND_IMG} to=${prev_backend}"
      echo "[ROLLBACK] 已回滚到 $prev_backend" >&2
    fi
  fi
  exit "$rc"
}
trap fail_and_rollback ERR

echo "=========================================================="
echo " Lightmark K8s 部署"
echo "  镜像版本 : $TAG"
echo "  提交     : $COMMIT_SHA"
echo "  镜像     : $BACKEND_IMG"
echo "           : $FRONTEND_IMG"
echo "  域名     : $INGRESS_HOST"
echo "  部署目录 : $DEPLOY_DIR"
echo "=========================================================="

mkdir -p "$DEPLOY_DIR"
log_record "STARTED"

# ---------- 1. 准备 .env（密钥） ----------
STAGE="prepare-env"
ENV_FILE="$DEPLOY_DIR/.env"
if [ ! -f "$ENV_FILE" ] && [ -n "${SERVER_ENV_BASE64:-}" ]; then
  # 兼容 certutil 等工具生成的内容：去掉 -----BEGIN/END----- 头尾行与全部空白后再解码
  B64_CLEAN="$(printf '%s' "$SERVER_ENV_BASE64" | grep -vE '^[[:space:]]*-----' | tr -d '[:space:]' || true)"
  if [ -z "$B64_CLEAN" ] || ! printf '%s' "$B64_CLEAN" | base64 -d > "$ENV_FILE" 2>/dev/null || [ ! -s "$ENV_FILE" ]; then
    echo "[FATAL] SERVER_ENV_BASE64 解码失败：Secret 内容不是合法 base64。" >&2
    echo "        请用 scripts/make-env-secret.ps1 -EnvFile server.env 重新生成后更新该 Secret。" >&2
    exit 1
  fi
  # 解码结果应为 KEY=VALUE 形式的 .env
  if ! grep -qE '^[A-Z_]+=' "$ENV_FILE"; then
    echo "[FATAL] SERVER_ENV_BASE64 解码结果不是 .env 格式（缺少 KEY=VALUE 行）。" >&2
    echo "        请确认生成时使用的是新格式 server.env（含 MYSQL_ROOT_PASSWORD 等）。" >&2
    exit 1
  fi
  echo "[OK] 已根据 SERVER_ENV_BASE64 生成 $ENV_FILE"
fi
if [ ! -f "$ENV_FILE" ]; then
  echo "[FATAL] 服务器缺少 $ENV_FILE，请任选其一："
  echo "        1) 在 GitHub Secrets 配置 SERVER_ENV_BASE64（.env 的 base64）"
  echo "        2) 手动上传 .env 到服务器 $DEPLOY_DIR/.env"
  exit 1
fi
sed -i 's/\r$//' "$ENV_FILE"
chmod 600 "$ENV_FILE"

REQUIRED_VARS=(MYSQL_ROOT_PASSWORD MYSQL_PASSWORD DB_USER DB_PASSWORD JWT_SECRET)
for v in "${REQUIRED_VARS[@]}"; do
  if ! grep -qE "^${v}=.+" "$ENV_FILE"; then
    echo "[FATAL] $ENV_FILE 缺少必需变量 $v" >&2
    exit 1
  fi
done
# DB_HOST 缺省为 mysql（k8s Service 名，与 docker-compose 一致）
grep -qE '^DB_HOST=' "$ENV_FILE" || echo 'DB_HOST=mysql' >> "$ENV_FILE"

# ---------- 2. 命名空间 + ghcr 拉取凭据（imagePullSecret） ----------
STAGE="namespace"
# namespace.yaml 不含占位符，可直接应用源文件；先建命名空间，后续 Secret/ConfigMap 才能创建
$KUBECTL apply -f "$ROOT_DIR/deploy/k8s/namespace.yaml"

STAGE="ghcr-secret"
if [ -n "${GHCR_USERNAME:-}" ] && [ -n "${GHCR_PAT:-}" ]; then
  $KUBECTL create secret docker-registry ghcr-secret \
    --docker-server=ghcr.io --docker-username="$GHCR_USERNAME" --docker-password="$GHCR_PAT" \
    -n "$NAMESPACE" --dry-run=client -o yaml | $KUBECTL apply -f -
  echo "[OK] ghcr-secret 已更新（$GHCR_USERNAME）"
else
  # 公共镜像可匿名拉取；私有仓库请配置 GHCR_USERNAME / GHCR_PAT
  $KUBECTL create secret docker-registry ghcr-secret \
    --docker-server=ghcr.io --docker-username=anonymous --docker-password=anonymous \
    -n "$NAMESPACE" --dry-run=client -o yaml 2>/dev/null | $KUBECTL apply -f - 2>/dev/null || true
  echo "[WARN] 未配置 GHCR_USERNAME/GHCR_PAT，假定镜像可匿名拉取"
fi

# ---------- 3. 渲染清单（注入版本化镜像与域名）并应用 ----------
STAGE="render"
RENDER_DIR="$DEPLOY_DIR/k8s-rendered-$TAG"
mkdir -p "$RENDER_DIR"
for f in namespace.yaml mysql.yaml backend.yaml frontend.yaml ingress.yaml; do
  sed -e "s|__IMAGE_BACKEND__|${BACKEND_IMG}|g" \
      -e "s|__IMAGE_FRONTEND__|${FRONTEND_IMG}|g" \
      -e "s|__INGRESS_HOST__|${INGRESS_HOST}|g" \
      "$ROOT_DIR/deploy/k8s/$f" > "$RENDER_DIR/$f"
done
echo "[OK] 清单已渲染到 $RENDER_DIR（部署记录保留）"

STAGE="apply"
$KUBECTL apply -f "$RENDER_DIR/namespace.yaml"
# 密钥：由服务器 .env 生成（DB/JWT/AI 等），不入库
$KUBECTL create secret generic lightmark-secrets --from-env-file="$ENV_FILE" \
  -n "$NAMESPACE" --dry-run=client -o yaml | $KUBECTL apply -f -
# 数据库初始化脚本：database/lightmark.sql -> ConfigMap（首次启动自动执行）
$KUBECTL create configmap lightmark-init-sql --from-file=lightmark.sql="$ROOT_DIR/database/lightmark.sql" \
  -n "$NAMESPACE" --dry-run=client -o yaml | $KUBECTL apply -f -

# TLS 证书：优先使用服务器已有证书，否则生成自签名
STAGE="tls"
if [ -f "$CERT_DIR/origin.crt" ] && [ -f "$CERT_DIR/origin.key" ]; then
  $KUBECTL create secret tls lightmark-tls --cert="$CERT_DIR/origin.crt" --key="$CERT_DIR/origin.key" \
    -n "$NAMESPACE" --dry-run=client -o yaml | $KUBECTL apply -f -
  echo "[OK] TLS 使用已有证书（$CERT_DIR）"
else
  TMP_TLS="$(mktemp -d)"
  openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
    -keyout "$TMP_TLS/tls.key" -out "$TMP_TLS/tls.crt" \
    -subj "/CN=$INGRESS_HOST" >/dev/null 2>&1
  $KUBECTL create secret tls lightmark-tls --cert="$TMP_TLS/tls.crt" --key="$TMP_TLS/tls.key" \
    -n "$NAMESPACE" --dry-run=client -o yaml | $KUBECTL apply -f -
  rm -rf "$TMP_TLS"
  echo "[WARN] 未找到证书，已生成自签名证书（浏览器会提示不安全）"
fi

STAGE="apply-workloads"
$KUBECTL apply -f "$RENDER_DIR/mysql.yaml" -f "$RENDER_DIR/backend.yaml" -f "$RENDER_DIR/frontend.yaml" -f "$RENDER_DIR/ingress.yaml"

# 若本次只有 Secret（.env）变化而镜像 tag 未变，Pod 不会自动重建，
# 显式重启后端使其拿到最新环境变量
STAGE="restart-backend"
$KUBECTL rollout restart deployment/lightmark-backend -n "$NAMESPACE"

# ---------- 4. 等待滚动更新完成 ----------
STAGE="rollout-mysql"
$KUBECTL rollout status deployment/lightmark-mysql -n "$NAMESPACE" --timeout=420s
STAGE="rollout-backend"
$KUBECTL rollout status deployment/lightmark-backend -n "$NAMESPACE" --timeout=420s
STAGE="rollout-frontend"
$KUBECTL rollout status deployment/lightmark-frontend -n "$NAMESPACE" --timeout=180s

# ---------- 5. 健康检查 ----------
STAGE="healthcheck"
if bash "$SCRIPT_DIR/healthcheck.sh" "https://127.0.0.1" "$INGRESS_HOST"; then
  :
else
  # HTTPS 不通时退回 HTTP 再验一次
  bash "$SCRIPT_DIR/healthcheck.sh" "http://127.0.0.1" "$INGRESS_HOST"
fi

log_record "SUCCESS" "health=OK"
echo ""
echo "[OK] 部署成功：https://$INGRESS_HOST （tag=$TAG, commit=$COMMIT_SHA）"
$KUBECTL get deploy,pods -n "$NAMESPACE" 2>/dev/null | grep lightmark || true
echo "部署记录：$HISTORY_LOG"
