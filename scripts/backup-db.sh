#!/usr/bin/env bash
set -euo pipefail
# =====================================================================
# 导出 MySQL 全量备份到 database/lightmark.sql
# （维护「数据库备份文件」，供新环境数据库容器首次启动自动读入）
#
# 用法:
#   bash scripts/backup-db.sh [输出路径]     # 默认 database/lightmark.sql
#
# 自动识别运行环境（按顺序）:
#   1) Kubernetes: kubectl exec deploy/lightmark-mysql
#   2) Docker Compose: docker exec lightmark-mysql
# =====================================================================
OUT="${1:-database/lightmark.sql}"
NAMESPACE="${NAMESPACE:-lightmark}"

DUMP_CMD='mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 --single-transaction --routines --triggers --set-gtid-purged=OFF lightmark'

if command -v kubectl >/dev/null 2>&1 && kubectl get deploy lightmark-mysql -n "$NAMESPACE" >/dev/null 2>&1; then
  echo "[k8s] 从 Pod 导出 ..."
  kubectl exec "deploy/lightmark-mysql" -n "$NAMESPACE" -- sh -c "$DUMP_CMD" > "$OUT.tmp"
elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^lightmark-mysql$'; then
  echo "[docker] 从容器导出 ..."
  docker exec lightmark-mysql sh -c "$DUMP_CMD" > "$OUT.tmp"
else
  echo "[ERROR] 未找到运行中的 lightmark MySQL（k8s 或 docker 均可）" >&2
  exit 1
fi

mv "$OUT.tmp" "$OUT"
echo "[OK] 备份已写入 $OUT"
