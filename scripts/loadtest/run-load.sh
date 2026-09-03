#!/usr/bin/env bash
# =====================================================================
# 单接口压测（ApacheBench）
#
# 用法：
#   bash scripts/loadtest/run-load.sh <完整URL> [并发数] [总请求数] [标签]
# 示例：
#   bash scripts/loadtest/run-load.sh "http://127.0.0.1:8082/api/flights/search?page=1&size=10" 50 5000 flights-search
#   # 带 Host 头（走服务器 Traefik 入口、按域名路由）：
#   bash scripts/loadtest/run-load.sh "http://127.0.0.1/api/hotel/list?page=1&size=10" 30 3000 hotel-list msa.lightmark.ortus.top
#
# 附加参数（可选环境变量）：
#   EXTRA_AB_ARGS   追加给 ab 的参数，如 '-H "Authorization: Bearer <token>"'
#   OUT_ROOT        结果目录（默认 artifacts/load）
#
# 输出：原始 ab 报告 + summary.tsv（complete/failed/rps/avg_ms/p95_ms）
# 需要：ab（sudo apt-get install -y apache2-utils）
# =====================================================================
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "$SCRIPT_DIR/lib.sh"

require_ab

URL="${1:?用法: run-load.sh <URL> [并发] [请求数] [标签] [Host头值]}"
CONC="${2:-50}"
REQ="${3:-5000}"
LABEL="${4:-load}"
HOST_HEADER="${5:-}"

OUT_DIR="$(new_out_dir "single-${LABEL}")"
echo "[INFO] 输出目录: $OUT_DIR"
echo "[INFO] 并发 $CONC, 总请求 $REQ, URL: $URL"

ARGS=(-k -n "$REQ" -c "$CONC")
if [ -n "$HOST_HEADER" ]; then
  ARGS+=(-H "Host: $HOST_HEADER")
fi
# shellcheck disable=SC2086
[ -n "${EXTRA_AB_ARGS:-}" ] && ARGS+=($EXTRA_AB_ARGS)
ARGS+=("$URL")

SAMPLER_CMD="$(metrics_cmd)"
PATTERN="product-service|user-service|backend|frontend|lightmark"
start_sampler "$SAMPLER_CMD" "$OUT_DIR/metrics.log" "$PATTERN" 2

ab_run "$OUT_DIR" "$LABEL" "${ARGS[@]}" | tee "$OUT_DIR/summary.tsv"

stop_sampler
echo "[OK] 完成：原始报告 $OUT_DIR/${LABEL}-ab.txt，指标采样 $OUT_DIR/metrics.log"
echo "    汇总：$(grep -E 'rps=|avg_ms=|p95_ms=|failed=' "$OUT_DIR/summary.tsv" | tr '\n' ' ')"
