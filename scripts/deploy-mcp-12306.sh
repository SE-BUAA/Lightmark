#!/usr/bin/env bash
# =====================================================================
# 12306 MCP 服务一键部署（宿主机 Docker，供火车票查询业务调用）
#
# 说明：项目后端（单体 + MSA product-service）通过 MCP Streamable HTTP
# 调用本服务（默认地址 http://<host>:9000/mcp，容器端口映射 8000）。
# 本脚本保证：无论服务器/本地，只要执行一次即可获得可用的 12306 MCP 服务，
# 不再依赖任何外部维护的服务。
#
# 用法：
#   bash scripts/deploy-mcp-12306.sh
#
# 可选环境变量：
#   MCP_IMAGE     镜像（默认 ghcr.io/supikatsujikura/mcp-12306-server:latest）
#   MCP_PORT      宿主机端口（默认 9000，映射容器 8000）
#   MCP_NAME      容器名（默认 lightmark-mcp-12306）
# =====================================================================
set -euo pipefail

MCP_IMAGE="${MCP_IMAGE:-ghcr.io/supikatsujikura/mcp-12306-server:latest}"
MCP_PORT="${MCP_PORT:-9000}"
MCP_NAME="${MCP_NAME:-lightmark-mcp-12306}"

if ! command -v docker >/dev/null 2>&1; then
  echo "[FATAL] 未找到 docker，无法部署 12306 MCP 服务" >&2
  exit 1
fi

echo "[1/4] 拉取镜像 ${MCP_IMAGE}"
docker pull "$MCP_IMAGE"

echo "[2/4] 检查端口 ${MCP_PORT} 占用"
if docker ps --format '{{.Ports}}' | grep -q "${MCP_PORT}->" && ! docker inspect "$MCP_NAME" >/dev/null 2>&1; then
  echo "[FATAL] 宿主机端口 ${MCP_PORT} 已被其他容器占用（可能是旧版 12306 MCP），请先停掉旧容器或设置 MCP_PORT" >&2
  exit 1
fi

echo "[3/4] 启动容器 ${MCP_NAME}（宿主 ${MCP_PORT} -> 容器 8000）"
if docker inspect "$MCP_NAME" >/dev/null 2>&1; then
  # 已存在：删除重建，确保使用最新镜像
  docker rm -f "$MCP_NAME" >/dev/null
fi
docker run -d --name "$MCP_NAME" --restart unless-stopped \
  -p "${MCP_PORT}:8000" \
  "$MCP_IMAGE" >/dev/null

echo "[4/4] 等待健康检查 http://127.0.0.1:${MCP_PORT}/health"
UP=0
for _ in $(seq 1 30); do
  if curl -fsS --max-time 3 "http://127.0.0.1:${MCP_PORT}/health" >/dev/null 2>&1; then
    UP=1
    break
  fi
  sleep 2
done
if [ "$UP" = 1 ]; then
  echo "[OK] 12306 MCP 服务就绪：http://127.0.0.1:${MCP_PORT}/mcp（容器 ${MCP_NAME}）"
  echo "     项目调用地址保持 http://<本机IP>:${MCP_PORT}/mcp 不变"
else
  echo "[ERROR] 12306 MCP 服务健康检查未通过，查看日志：docker logs ${MCP_NAME}" >&2
  exit 1
fi
