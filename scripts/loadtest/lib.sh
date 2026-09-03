#!/usr/bin/env bash
# =====================================================================
# 压测公共函数库（run-load.sh / perf-compare.sh / hpa-test.sh 共用）
# =====================================================================

require_ab() {
  if ! command -v ab >/dev/null 2>&1; then
    echo "[FATAL] 需要 ApacheBench(ab)。请先安装：" >&2
    echo "        Ubuntu/Debian:  sudo apt-get install -y apache2-utils" >&2
    echo "        macOS:          brew install ab" >&2
    exit 1
  fi
}

require_kubectl() {
  if ! command -v kubectl >/dev/null 2>&1; then
    echo "[FATAL] 需要 kubectl（HPA/资源观察功能）" >&2
    exit 1
  fi
}

OUT_ROOT="${OUT_ROOT:-artifacts/load}"

new_out_dir() { # $1=实验名 -> 输出目录
  local name="$1"
  local d="$OUT_ROOT/${name}-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$d"
  echo "$d"
}

# 解析 ab 输出：输出 key=value 行（stdout）
parse_ab() { # $1=ab 原始输出文件
  local raw="$1"
  local complete failed rps tpr p95
  complete="$(sed -n 's/^Complete requests:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$raw" | head -1)"
  failed="$(sed -n 's/^Failed requests:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$raw" | head -1)"
  rps="$(sed -n 's/^Requests per second:[[:space:]]*\([0-9][0-9.]*\).*/\1/p' "$raw" | head -1)"
  # ab 会输出两行 Time per request，第二行 (mean, across all concurrent requests) 才是单请求平均延迟
  tpr="$(grep 'Time per request:' "$raw" | tail -1 | sed 's/.*:[[:space:]]*\([0-9][0-9.]*\).*/\1/')"
  p95="$(awk '/Percentage of the requests served within a certain time/{f=1;next} f&&/^  95%/{print $2;exit}' "$raw")"
  printf 'complete=%s\nfailed=%s\nrps=%s\navg_ms=%s\np95_ms=%s\n' \
    "${complete:-0}" "${failed:-0}" "${rps:-0}" "${tpr:-0}" "${p95:-0}"
}

# 运行一次 ab 并把原始输出存盘
ab_run() { # $1=输出目录 $2=原始文件前缀 $3..=ab 参数
  local dir="$1" prefix="$2"
  shift 2
  local raw="$dir/$prefix-ab.txt"
  echo "[INFO] ab $*" | tee "$dir/$prefix-cmd.txt"
  ab "$@" > "$raw" 2>&1
  parse_ab "$raw"
}

# 资源采样命令自动选择：优先 kubectl top（k8s 环境），否则 docker stats
metrics_cmd() {
  if command -v kubectl >/dev/null 2>&1 \
      && kubectl top pods -n "${NAMESPACE:-lightmark}" >/dev/null 2>&1; then
    echo "kubectl top pods -n ${NAMESPACE:-lightmark} --no-headers"
  else
    echo "docker stats --no-stream --format '{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}'"
  fi
}

# 后台采样：$1=采样命令字符串 $2=输出文件 $3=过滤模式 $4=采样间隔秒
start_sampler() {
  local cmd="$1" out="$2" pattern="$3" interval="${4:-2}"
  : > "$out"
  (
    while true; do
      { eval "$cmd" 2>/dev/null || true; } | grep -E "$pattern" >> "$out" || true
      printf '%s\n' "--- $(date '+%H:%M:%S') ---" >> "$out"
      sleep "$interval"
    done
  ) &
  SAMPLER_PID=$!
}

stop_sampler() {
  if [ -n "${SAMPLER_PID:-}" ]; then
    kill "$SAMPLER_PID" 2>/dev/null || true
    wait "$SAMPLER_PID" 2>/dev/null || true
    SAMPLER_PID=""
  fi
}
