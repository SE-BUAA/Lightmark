#!/usr/bin/env bash
# =====================================================================
# 云原生实验：自动扩缩容（HPA）演示脚本
#
# 流程：
#   阶段1 基线    短时小负载 + 记录当前副本数/资源
#   阶段2 加压    持续高并发打到目标服务 -> HPA 副本数上升（扩容）
#   阶段3 观察    记录扩容时间线（时间、副本数、CPU）
#   阶段4 降压    停止负载 -> 等待副本数回落（缩容，可能需要数分钟）
#
# 用法（在服务器上执行，需要 kubectl）：
#   bash scripts/loadtest/hpa-test.sh
#
# 可选环境变量：
#   LOAD_URL       压测 URL（默认 http://127.0.0.1/api/flights/search?page=1&size=10，
#                  经 Traefik 走 MSA 入口；如需指定域名改 LOAD_HOST）
#   LOAD_HOST      Host 头（默认 msa.lightmark.ortus.top）
#   SERVICE        目标服务（默认 product-service，HPA 对象同名）
#   NAMESPACE      k8s 命名空间（默认 lightmark）
#   CONC / REQ     加压并发与请求数（默认 100 / 30000，约可持续数分钟）
#   SCALE_UP_TIMEOUT  等待扩容秒数（默认 240）
#   SCALE_DOWN_TIMEOUT 等待缩容秒数（默认 600）
#
# 输出：artifacts/load/hpa-<时间>/ 下
#   timeline.log（时间|副本数|CPU|内存，2s 采样）、ab 原始报告、hpa 快照
# =====================================================================
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
. "$SCRIPT_DIR/lib.sh"

require_ab
require_kubectl

LOAD_URL="${LOAD_URL:-http://127.0.0.1/api/flights/search?page=1&size=10}"
LOAD_HOST="${LOAD_HOST:-msa.lightmark.ortus.top}"
SERVICE="${SERVICE:-product-service}"
NAMESPACE="${NAMESPACE:-lightmark}"
CONC="${CONC:-100}"
REQ="${REQ:-30000}"
SCALE_UP_TIMEOUT="${SCALE_UP_TIMEOUT:-240}"
SCALE_DOWN_TIMEOUT="${SCALE_DOWN_TIMEOUT:-600}"

OUT_DIR="$(new_out_dir hpa)"
echo "[INFO] 输出目录: $OUT_DIR"
echo "[INFO] 目标: $SERVICE (ns=$NAMESPACE) | 压测 $LOAD_URL (Host: $LOAD_HOST)"

KUBECTL="${KUBECTL:-kubectl}"
if ! $KUBECTL get hpa "$SERVICE" -n "$NAMESPACE" >/dev/null 2>&1; then
  echo "[FATAL] 找不到 HPA $SERVICE，请确认 MSA 已部署（deploy-msa-k8s.sh）" >&2
  exit 1
fi

hpa_replicas() { # 输出 currentReplicas/desiredReplicas
  $KUBECTL get hpa "$SERVICE" -n "$NAMESPACE" \
    -o jsonpath='{.status.currentReplicas}/{.status.desiredReplicas}' 2>/dev/null
}
MIN_REPLICAS="$($KUBECTL get hpa "$SERVICE" -n "$NAMESPACE" -o jsonpath='{.spec.minReplicas}')"
MAX_REPLICAS="$($KUBECTL get hpa "$SERVICE" -n "$NAMESPACE" -o jsonpath='{.spec.maxReplicas}')"
echo "[INFO] HPA $SERVICE: min=$MIN_REPLICAS max=$MAX_REPLICAS"

# 时间线采样（副本数 + CPU/内存）
timeline() {
  local ts replicas cpu mem
  ts="$(date '+%H:%M:%S')"
  replicas="$(hpa_replicas)"
  read -r cpu mem <<< "$($KUBECTL top pod -n "$NAMESPACE" -l "app=lightmark-${SERVICE}" --no-headers 2>/dev/null | awk '{print $2, $3}' | tr '\n' ' ')"
  printf '%s replicas=%s cpu=%s mem=%s\n' "$ts" "$replicas" "${cpu:-n/a}" "${mem:-n/a}" | tee -a "$OUT_DIR/timeline.log"
}
: > "$OUT_DIR/timeline.log"

echo ""
echo "========== 阶段1: 基线（预热 + 当前状态） =========="
ab -k -n 200 -c 5 -H "Host: $LOAD_HOST" "$LOAD_URL" > "$OUT_DIR/warmup-ab.txt" 2>&1 || true
timeline

echo ""
echo "========== 阶段2: 加压（ab -n $REQ -c $CONC，后台运行） =========="
ab -k -n "$REQ" -c "$CONC" -H "Host: $LOAD_HOST" "$LOAD_URL" > "$OUT_DIR/load-ab.txt" 2>&1 &
AB_PID=$!

echo "[INFO] 等待扩容（最多 ${SCALE_UP_TIMEOUT}s，每 2s 采样一次）..."
SCALED_UP=0
for step in $(seq 1 $((SCALE_UP_TIMEOUT / 2))); do
  sleep 2
  timeline
  CUR="$(hpa_replicas | cut -d/ -f1)"
  if [ "${CUR:-0}" -gt "$MIN_REPLICAS" ]; then
    SCALED_UP=1
    echo "[OK] 已触发扩容: 副本数 $CUR > min=$MIN_REPLICAS"
    break
  fi
  if ! kill -0 "$AB_PID" 2>/dev/null; then
    echo "[WARN] 压测提前结束（可能负载不足或接口报错），检查 $OUT_DIR/load-ab.txt"
    break
  fi
done

if [ "$SCALED_UP" = 0 ]; then
  echo "[WARN] ${SCALE_UP_TIMEOUT}s 内未观察到扩容。请检查："
  echo "       1) metrics-server 是否可用: kubectl top nodes"
  echo "       2) 压测是否真的打到了 $SERVICE（看 $OUT_DIR/load-ab.txt 的 Failed requests）"
  echo "       3) HPA 状态: kubectl get hpa $SERVICE -n $NAMESPACE -o wide"
  echo "       可加大并发重试: CONC=200 REQ=60000 bash scripts/loadtest/hpa-test.sh"
fi

wait "$AB_PID" 2>/dev/null || true
echo ""
echo "========== 阶段3: 扩容期间压测结果 =========="
echo "--- 请求失败/错误数 ---"; grep -E 'Complete requests|Failed requests' "$OUT_DIR/load-ab.txt"
echo "--- 吞吐与延迟 ---"; grep -E 'Requests per second|Time per request' "$OUT_DIR/load-ab.txt" | head -3
echo "--- 95% 延迟 ---"; awk '/Percentage of the requests served within a certain time/{f=1;next} f&&/^  95%/{print;exit}' "$OUT_DIR/load-ab.txt"
$KUBECTL get hpa "$SERVICE" -n "$NAMESPACE" -o wide | tee "$OUT_DIR/hpa-after-load.txt"
$KUBECTL get pods -n "$NAMESPACE" -l "app=lightmark-${SERVICE}" -o wide | tee "$OUT_DIR/pods-after-load.txt"

echo ""
echo "========== 阶段4: 降压，等待缩容（最多 ${SCALE_DOWN_TIMEOUT}s） =========="
SCALED_DOWN=0
TICK=0
for step in $(seq 1 $((SCALE_DOWN_TIMEOUT / 5))); do
  sleep 5
  TICK=$((TICK + 1))
  CUR="$(hpa_replicas | cut -d/ -f1)"
  if [ "${CUR:-0}" -le "$MIN_REPLICAS" ]; then
    SCALED_DOWN=1
    timeline
    echo "[OK] 已缩容回 min=$MIN_REPLICAS"
    break
  fi
  # 每 60s 打一次心跳，避免等待期无输出（用独立计数器，避免部分 shell 对 $((_ % n)) 的兼容问题）
  if [ $((TICK % 12)) -eq 0 ]; then
    timeline
    echo "[INFO] 仍在等待缩容（当前 ${CUR:-?} 副本，已等 $((TICK * 5))s）..."
  fi
done
if [ "$SCALED_DOWN" = 0 ]; then
  echo "[WARN] ${SCALE_DOWN_TIMEOUT}s 内未完全缩容（缩容通常需要几分钟稳定期），"
  echo "       可稍后手动查看: kubectl get hpa $SERVICE -n $NAMESPACE"
fi

echo ""
echo "=========================================================="
echo " HPA 实验完成，结果目录: $OUT_DIR"
echo "   时间线: $OUT_DIR/timeline.log（副本数变化过程）"
echo "   压测原始: $OUT_DIR/load-ab.txt（吞吐/平均/P95/错误率）"
echo "   扩容后快照: $OUT_DIR/hpa-after-load.txt / pods-after-load.txt"
echo " 汇报要点: 压力升高 -> Pod 增加; 压力下降 -> Pod 减少;"
echo "           记录吞吐、平均/P95 响应时间与错误率。"
echo "=========================================================="
