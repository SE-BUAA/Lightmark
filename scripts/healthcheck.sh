#!/usr/bin/env bash
set -euo pipefail
# =====================================================================
# 健康检查脚本：后端 /api/health（返回 UP）+ 前端首页（200）
#
# 用法:
#   bash scripts/healthcheck.sh [BASE_URL] [HOST_HEADER]
#     BASE_URL    默认 https://127.0.0.1
#     HOST_HEADER 可选；Ingress 按域名路由时传域名（如 lightmark.ortus.top）
#
# 退出码 0 = 全部通过；非 0 = 存在故障
# =====================================================================
BASE_URL="${1:-https://127.0.0.1}"
HOST_HEADER="${2:-}"

declare -a CURL=(curl -k -fsS --max-time 20)
[ -n "$HOST_HEADER" ] && CURL+=(-H "Host: $HOST_HEADER")

echo "[1/2] 后端健康检查: $BASE_URL/api/health"
BACKEND_JSON="$("${CURL[@]}" "$BASE_URL/api/health")"
echo "      响应: $BACKEND_JSON"
echo "$BACKEND_JSON" | grep -q '"UP"' || { echo "[FAIL] 后端未返回 UP" >&2; exit 1; }

echo "[2/2] 前端首页: $BASE_URL/"
"${CURL[@]}" -o /dev/null "$BASE_URL/" || { echo "[FAIL] 前端首页不可访问" >&2; exit 1; }

echo "[OK] 健康检查全部通过"
