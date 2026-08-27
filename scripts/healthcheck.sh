#!/usr/bin/env bash
set -euo pipefail
# =====================================================================
# 健康检查（正常测试形式）
#   1) 后端 /api/health 必须返回 UP（断言 JSON）
#   2) 前端首页必须 200 且包含 Vue 挂载点 <div id="app">（浏览器可打开）
#   3) 首页引用的首个静态资源（js/css）必须可加载
#   每个检查最多重试 $HC_RETRIES 次（默认 5），间隔 $HC_SLEEP 秒（默认 5），
#   容忍滚动更新窗口内的瞬时不可用。
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
RETRIES="${HC_RETRIES:-5}"
SLEEP="${HC_SLEEP:-5}"

declare -a CURL=(curl -k -fsS --max-time 20)
[ -n "$HOST_HEADER" ] && CURL+=(-H "Host: $HOST_HEADER")

fail() { echo "[FAIL] $*" >&2; exit 1; }

# ---------- [1/3] 后端：断言返回 UP ----------
echo "[1/3] 后端健康检查: $BASE_URL/api/health"
BODY=""
for i in $(seq 1 "$RETRIES"); do
  if BODY="$("${CURL[@]}" "$BASE_URL/api/health" 2>/dev/null)" && echo "$BODY" | grep -q '"UP"'; then
    echo "      响应: $BODY"
    break
  fi
  [ "$i" -lt "$RETRIES" ] && { echo "  [尝试 $i/$RETRIES] 后端未就绪，${SLEEP}s 后重试..." >&2; sleep "$SLEEP"; }
  [ "$i" -eq "$RETRIES" ] && fail "后端 ${RETRIES} 次尝试后仍未返回 UP（最后响应: ${BODY:-空}）"
done

# ---------- [2/3] 前端首页：200 + Vue 挂载点断言 ----------
echo "[2/3] 前端首页: $BASE_URL/"
INDEX="$(mktemp)"
for i in $(seq 1 "$RETRIES"); do
  if "${CURL[@]}" -o "$INDEX" "$BASE_URL/" 2>/dev/null; then
    break
  fi
  [ "$i" -lt "$RETRIES" ] && { echo "  [尝试 $i/$RETRIES] 前端未就绪，${SLEEP}s 后重试..." >&2; sleep "$SLEEP"; }
  [ "$i" -eq "$RETRIES" ] && fail "前端首页 ${RETRIES} 次尝试后仍不可访问"
done
grep -q 'id="app"' "$INDEX" || fail "首页 HTML 缺少 Vue 挂载点 <div id=\"app\">，浏览器无法挂载应用"
echo "      首页 200 且含 Vue 挂载点 ✓"

# ---------- [3/3] 静态资源抽样：断言可加载 ----------
ASSET="$(grep -oE '(src|href)="[^"]+\.(js|css)"' "$INDEX" | head -n1 | sed -E 's/^(src|href)="([^"]+)"/\2/' || true)"
if [ -n "$ASSET" ]; then
  echo "[3/3] 静态资源抽样: $ASSET"
  "${CURL[@]}" -o /dev/null "$BASE_URL$ASSET" || fail "静态资源 $ASSET 加载失败"
  echo "      静态资源 200 ✓"
else
  echo "[3/3] 未在首页找到 js/css 引用（跳过）"
fi
rm -f "$INDEX"

echo "[OK] 健康检查全部通过"
