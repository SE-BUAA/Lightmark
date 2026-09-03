#!/usr/bin/env bash
# =====================================================================
# user-service 独立 Kubernetes 部署脚本
#
# 必需环境变量：TAG、REPO
# 可选环境变量：REGISTRY、NAMESPACE、DEPLOY_DIR、KUBECTL、USER_SERVICE_ENV_FILE、SERVER_ENV_BASE64
#
# USER_SERVICE_ENV_FILE 默认读取 $DEPLOY_DIR/.env.k8s。该文件只存在于部署服务器，
# 由 GitHub Secret/服务器管理员维护，包含 USER_DB_* 和 JWT_SECRET 等敏感配置。
# 本脚本只部署 user-service，不创建、初始化或修改任何数据库对象。
# =====================================================================
set -euo pipefail

TAG="${TAG:?必须设置 TAG（例如 1.0.123）}"
REPO="${REPO:?必须设置 REPO（例如 se-buaa/lightmark）}"
REPO="${REPO,,}"
REGISTRY="${REGISTRY:-ghcr.io}"
NAMESPACE="${NAMESPACE:-lightmark}"
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/lightmark}"
ENV_FILE="${USER_SERVICE_ENV_FILE:-$DEPLOY_DIR/.env.k8s}"
INGRESS_HOST="${INGRESS_HOST:-lightmark.ortus.top}"
USER_IMAGE="${USER_IMAGE:-$REGISTRY/$REPO/user-service:user-service-$TAG}"
mkdir -p "$DEPLOY_DIR"

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

if [ ! -f "$ENV_FILE" ] && [ -n "${SERVER_ENV_BASE64:-}" ]; then
  B64_CLEAN="$(printf '%s' "$SERVER_ENV_BASE64" | grep -vE '^[[:space:]]*-----' | tr -d '[:space:]' || true)"
  if [ -z "$B64_CLEAN" ] || ! printf '%s' "$B64_CLEAN" | base64 -d > "$ENV_FILE" 2>/dev/null || [ ! -s "$ENV_FILE" ]; then
    echo "[FATAL] SERVER_ENV_BASE64 解码失败" >&2
    exit 1
  fi
  chmod 600 "$ENV_FILE"
fi

if [ ! -f "$ENV_FILE" ]; then
  echo "[FATAL] 找不到 user-service 配置文件：$ENV_FILE" >&2
  echo "       请在部署服务器维护该文件，不要将密码提交到 Git。" >&2
  exit 1
fi

for required_var in USER_DB_HOST USER_DB_PORT USER_DB_NAME USER_DB_USER USER_DB_PASSWORD JWT_SECRET; do
  if ! grep -qE "^${required_var}=.+" "$ENV_FILE"; then
    echo "[FATAL] $ENV_FILE 缺少 $required_var" >&2
    exit 1
  fi
done

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
MANIFEST="$ROOT_DIR/deploy/k8s/msa/user-service.yaml"
INGRESS_MANIFEST="$ROOT_DIR/deploy/k8s/ingress.yaml"

if [ ! -s "$MANIFEST" ]; then
  echo "[FATAL] 找不到 user-service Kubernetes 清单：$MANIFEST" >&2
  exit 1
fi
if [ ! -s "$INGRESS_MANIFEST" ]; then
  echo "[FATAL] 找不到 Ingress 清单：$INGRESS_MANIFEST" >&2
  exit 1
fi

RENDER_DIR="$(mktemp -d "$DEPLOY_DIR/msa-user-rendered.XXXXXX")"
PORT_FORWARD_LOG="$RENDER_DIR/port-forward.log"

on_error() {
  local rc=$?
  echo "[ERROR] user-service 部署失败，输出现场诊断信息（不会打印 Secret 内容）" >&2
  $KUBECTL get deployment/user-service,pod,hpa -n "$NAMESPACE" 2>/dev/null || true
  $KUBECTL describe deployment/user-service -n "$NAMESPACE" 2>/dev/null || true
  $KUBECTL logs deployment/user-service -n "$NAMESPACE" --tail=120 2>/dev/null || true
  exit "$rc"
}

cleanup() {
  if [ -n "${PORT_FORWARD_PID:-}" ]; then
    kill "$PORT_FORWARD_PID" 2>/dev/null || true
  fi
  rm -rf "$RENDER_DIR"
}

trap on_error ERR
trap cleanup EXIT

echo "[1/6] 应用命名空间和镜像拉取凭据"
$KUBECTL apply -f "$ROOT_DIR/deploy/k8s/namespace.yaml"
if [ -n "${GHCR_USERNAME:-}" ] && [ -n "${GHCR_PAT:-}" ]; then
  $KUBECTL create secret docker-registry ghcr-secret \
    --docker-server=ghcr.io --docker-username="$GHCR_USERNAME" --docker-password="$GHCR_PAT" \
    -n "$NAMESPACE" --dry-run=client -o yaml | $KUBECTL apply -f -
fi

echo "[2/6] 生成 user-service Secret（只从服务器配置文件读取）"
chmod 600 "$ENV_FILE"
$KUBECTL create secret generic user-service-secrets --from-env-file="$ENV_FILE" \
  -n "$NAMESPACE" --dry-run=client -o yaml | $KUBECTL apply -f -

echo "[3/6] 渲染版本化镜像清单：$USER_IMAGE"
sed -e "s|__IMAGE_USER__|${USER_IMAGE}|g" \
    -e "s|__APP_VERSION__|user-service-${TAG}|g" \
    "$MANIFEST" > "$RENDER_DIR/user-service.yaml"

echo "[4/7] 部署 user-service（不触碰数据库资源）"
$KUBECTL apply -f "$RENDER_DIR/user-service.yaml"

echo "[5/7] 更新用户域 Ingress 路由（不触碰数据库资源）"
sed "s|__INGRESS_HOST__|${INGRESS_HOST}|g" "$INGRESS_MANIFEST" > "$RENDER_DIR/ingress.yaml"
$KUBECTL apply -f "$RENDER_DIR/ingress.yaml"

echo "[6/7] 等待 Deployment 就绪"
$KUBECTL rollout status deployment/user-service -n "$NAMESPACE" --timeout=420s

echo "[7/7] 检查健康、就绪和版本接口"
if ! command -v curl >/dev/null 2>&1; then
  echo "[FATAL] 部署服务器缺少 curl，无法执行 HTTP 验收" >&2
  exit 1
fi
$KUBECTL port-forward -n "$NAMESPACE" service/user-service 18081:8081 >"$PORT_FORWARD_LOG" 2>&1 &
PORT_FORWARD_PID=$!
for _ in $(seq 1 20); do
  if curl -fsS --max-time 3 http://127.0.0.1:18081/api/health >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
curl -fsS --max-time 10 http://127.0.0.1:18081/api/health | grep -q '"UP"'
curl -fsS --max-time 10 http://127.0.0.1:18081/api/ready | grep -q '"UP"'
curl -fsS --max-time 10 http://127.0.0.1:18081/api/version

echo "[OK] user-service 部署成功"
echo "[INFO] 镜像：$USER_IMAGE"
echo "[INFO] 查看日志：$KUBECTL logs deployment/user-service -n $NAMESPACE -f"
echo "[INFO] 查看状态：$KUBECTL get deployment,pod,hpa -n $NAMESPACE"
