#!/usr/bin/env bash
set -euo pipefail
# =====================================================================
# 部署服务器一键初始化（安装 k3s，准备目录）
#
# 用法:
#   sudo bash scripts/server-bootstrap.sh
#   sudo bash scripts/server-bootstrap.sh --stop-legacy   # 同时停止旧 docker compose 栈
#
# 说明:
#   - k3s 自带 Traefik Ingress，占用宿主机 80/443
#   - 若旧的 docker compose 部署（lightmark-nginx 等）还在运行，需先停止
#   - 证书可选：把 origin.crt / origin.key 放到 /home/ubuntu/certs 或 $HOME/certs
# =====================================================================

STOP_LEGACY=0
for arg in "$@"; do
  [ "$arg" = "--stop-legacy" ] && STOP_LEGACY=1
done

if [ "$(id -u)" -ne 0 ]; then
  echo "[FATAL] 请用 root 或 sudo 执行（k3s 安装需要）" >&2
  exit 1
fi

echo "[1/5] 检测 Docker"
if command -v docker >/dev/null 2>&1; then
  echo "      Docker 已安装"
else
  echo "      [WARN] 未安装 Docker（仅 backup-db.sh 等可选功能需要）"
fi

echo "[2/5] 检测 / 安装 k3s"
if command -v k3s >/dev/null 2>&1; then
  echo "      k3s 已安装: $(k3s --version 2>/dev/null | head -n1)"
else
  echo "      正在安装 k3s ..."
  curl -sfL https://get.k3s.io | sh -s - --write-kubeconfig-mode 644
  echo "      k3s 安装完成"
fi

echo "[3/5] 等待集群就绪"
KUBECTL=kubectl
command -v kubectl >/dev/null 2>&1 || KUBECTL="k3s kubectl"
for _ in $(seq 1 30); do
  if $KUBECTL get nodes >/dev/null 2>&1; then break; fi
  sleep 2
done
$KUBECTL get nodes

echo "[4/5] 准备目录"
mkdir -p "$HOME/lightmark" "$HOME/certs" /home/ubuntu/certs 2>/dev/null || true
echo "      证书目录: /home/ubuntu/certs 或 $HOME/certs（可选，需含 origin.crt / origin.key）"

if [ "$STOP_LEGACY" = "1" ]; then
  echo "[5/5] 停止旧 docker compose 部署（释放 80/443 给 Traefik）"
  for d in /home/ubuntu/lightmark /root/lightmark; do
    if [ -d "$d" ]; then
      (cd "$d" && docker compose down --remove-orphans 2>/dev/null || true)
    fi
  done
  docker stop lightmark-nginx lightmark-backend lightmark-mysql 2>/dev/null || true
  echo "      旧栈已停止"
else
  echo "[5/5] 提示：旧 docker compose 栈若仍在运行会占用 80/443"
  echo "      确认切换后执行: sudo bash scripts/server-bootstrap.sh --stop-legacy"
fi

echo ""
echo "============================================================"
echo " 服务器初始化完成。接下来："
echo " 1) 在 GitHub 仓库配置 Secrets（见 README「GitHub Secrets 配置」）"
echo " 2) push 到 develop 分支，流水线自动执行："
echo "    测试 → 构建镜像 → 部署 Kubernetes → 健康检查"
echo " 3) 部署成功后访问 https://<INGRESS_HOST>"
echo "============================================================"
