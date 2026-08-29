#!/usr/bin/env bash
set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-change-me}"
MONOLITH_DB="${MONOLITH_DB:-lightmark}"
OUT_DIR="${OUT_DIR:-artifacts/perf-baseline/monolith}"

mkdir -p "$OUT_DIR"

MYSQL_PWD="$MYSQL_PASSWORD" mysqldump \
  --host="$MYSQL_HOST" \
  --port="$MYSQL_PORT" \
  --user="$MYSQL_USER" \
  --default-character-set=utf8mb4 \
  --single-transaction \
  --routines \
  --triggers \
  --set-gtid-purged=OFF \
  "$MONOLITH_DB" > "$OUT_DIR/lightmark-monolith.sql.tmp"

mv "$OUT_DIR/lightmark-monolith.sql.tmp" "$OUT_DIR/lightmark-monolith.sql"
echo "[OK] monolith baseline exported to $OUT_DIR/lightmark-monolith.sql"
