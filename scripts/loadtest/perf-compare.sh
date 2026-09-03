#!/usr/bin/env bash
# =====================================================================
# 单体 vs 微服务 性能对比压测（同一台机器、同一批数据、同一份脚本）
#
# 原理：两个版本都部署在同一 k3s 服务器上，经 Traefik 入口按 Host 头区分；
# 对每个选定接口分别对两个版本各跑 RUNS 次（默认 3 次），记录
# 并发数、吞吐、平均延迟、P95、错误率，并采样 CPU/内存。
#
# 用法（在服务器上执行）：
#   bash scripts/loadtest/perf-compare.sh
#
# 可选环境变量：
#   MONO_BASE    单体入口（默认 http://127.0.0.1，Traefik :80）
#   MSA_BASE     微服务入口（默认 http://127.0.0.1）
#   MONO_HOST    单体 Host 头（默认 lightmark.ortus.top）
#   MSA_HOST     微服务 Host 头（默认 msa.lightmark.ortus.top）
#   ENDPOINTS    接口列表（默认两个主要读接口，空格分隔）
#   RUNS         每版本每接口轮数（默认 3）
#   CONC / REQ   并发数 / 每轮请求数（默认 30 / 3000）
#
# 输出：artifacts/load/perf-compare-<时间>/ 下
#   summary.csv（endpoint,version,run,conc,requests,failed,rps,avg_ms,p95_ms）
#   每轮原始 ab 报告 + 资源采样日志
# =====================================================================
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "$SCRIPT_DIR/lib.sh"

require_ab

MONO_BASE="${MONO_BASE:-http://127.0.0.1}"
MSA_BASE="${MSA_BASE:-http://127.0.0.1}"
MONO_HOST="${MONO_HOST:-lightmark.ortus.top}"
MSA_HOST="${MSA_HOST:-msa.lightmark.ortus.top}"
RUNS="${RUNS:-3}"
CONC="${CONC:-30}"
REQ="${REQ:-3000}"
ENDPOINTS="${ENDPOINTS:-/api/flights/search?page=1&size=10 /api/hotel/list?page=1&size=10}"

OUT_DIR="$(new_out_dir perf-compare)"
CSV="$OUT_DIR/summary.csv"
echo "endpoint,version,run,conc,requests,failed,rps,avg_ms,p95_ms" > "$CSV"
echo "[INFO] 输出目录: $OUT_DIR"
echo "[INFO] 单体入口 $MONO_BASE (Host: $MONO_HOST) | MSA 入口 $MSA_BASE (Host: $MSA_HOST)"
echo "[INFO] 接口: $ENDPOINTS | 每接口每版本 $RUNS 轮, 并发 $CONC, 每轮 $REQ 请求"

run_pair() { # $1=version(mono|msa) $2=base $3=host $4=endpoint $5=run
  local version="$1" base="$2" host="$3" endpoint="$4" run="$5"
  local url="${base}${endpoint}"
  local prefix="${version}-run${run}"
  local row
  row="$(ab_run "$OUT_DIR" "$prefix" -k -n "$REQ" -c "$CONC" -H "Host: $host" "$url")"
  # ab_run 输出 key=value 行，转成 CSV 一行
  local failed rps avg p95
  failed="$(printf '%s\n' "$row" | sed -n 's/^failed=//p')"
  rps="$(printf '%s\n' "$row" | sed -n 's/^rps=//p')"
  avg="$(printf '%s\n' "$row" | sed -n 's/^avg_ms=//p')"
  p95="$(printf '%s\n' "$row" | sed -n 's/^p95_ms=//p')"
  printf '%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
    "$endpoint" "$version" "$run" "$CONC" "$REQ" "$failed" "$rps" "$avg" "$p95" >> "$CSV"
  echo "[OK] $version  $endpoint  run$run: rps=$rps avg=${avg}ms p95=${p95}ms failed=$failed"
}

SAMPLER_CMD="$(metrics_cmd)"
PATTERN="product-service|user-service|order-service|content-service|backend|frontend"
start_sampler "$SAMPLER_CMD" "$OUT_DIR/metrics.log" "$PATTERN" 2

for endpoint in $ENDPOINTS; do
  for run in $(seq 1 "$RUNS"); do
    run_pair mono "$MONO_BASE" "$MONO_HOST" "$endpoint" "$run"
    run_pair msa  "$MSA_BASE"  "$MSA_HOST"  "$endpoint" "$run"
  done
done

stop_sampler

echo ""
echo "=========================================================="
echo " 对比结果汇总: $CSV"
echo "----------------------------------------------------------"
column -s, -t "$CSV"
echo "=========================================================="
echo " 原始报告与资源采样: $OUT_DIR"
echo " 说明: 条件相同(同机/同数据/同脚本/同并发)，每版本各 $RUNS 次，"
echo "       结论请基于原始数据撰写（微服务更慢也允许，需解释原因）。"
