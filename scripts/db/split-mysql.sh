#!/usr/bin/env bash
set -euo pipefail

# 脚本可被单独上传到服务器任意目录运行：配套文件按本脚本所在目录解析
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
MONOLITH_DB="${MONOLITH_DB:-lightmark}"

USER_SCHEMA="${USER_SCHEMA:-lightmark_user}"
PRODUCT_SCHEMA="${PRODUCT_SCHEMA:-lightmark_product}"
ORDER_SCHEMA="${ORDER_SCHEMA:-lightmark_order}"
CONTENT_SCHEMA="${CONTENT_SCHEMA:-lightmark_content}"

EXPORT_DIR="${EXPORT_DIR:-artifacts/db-split}"

USER_TABLES=(user role user_role traveler points_log user_login_log auth_verification_code admin_log)
PRODUCT_TABLES=(product room_type product_view_log)

# 注:hotel_order_detail / invoice_application 不列入拆分范围——
# 单体 lightmark 中不存在这两张表(lightmark.sql 与运行库均无),mysqldump 显式导出会报
# "Couldn't find table" 并使脚本中断;它们由 order-service 的 Flyway 基线
# (V20260829__order_schema_baseline.sql)在 lightmark_order 中自动创建(空表),无需从单体迁移。

ORDER_TABLES=(orders payment_record flight_order_detail review)
CONTENT_TABLES=(travel_plan post post_like comment question)

if [[ -z "$MYSQL_PASSWORD" ]]; then
  echo "[FATAL] MYSQL_PASSWORD must be provided through the environment" >&2
  exit 1
fi

mkdir -p "$EXPORT_DIR"

mysql_exec() {
  MYSQL_PWD="$MYSQL_PASSWORD" mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" "$@"
}

require_source_tables() {
  local missing=()
  local table
  local count
  for table in "${USER_TABLES[@]}" "${PRODUCT_TABLES[@]}" "${ORDER_TABLES[@]}" "${CONTENT_TABLES[@]}"; do
    count="$(mysql_exec --batch --skip-column-names -e \
      "select count(*) from information_schema.tables where table_schema = '${MONOLITH_DB}' and table_name = '${table}'")"
    if [[ "$count" != "1" ]]; then
      missing+=("$table")
    fi
  done
  if (( ${#missing[@]} > 0 )); then
    echo "[FATAL] monolith database '${MONOLITH_DB}' is missing required tables: ${missing[*]}" >&2
    echo "       Check MYSQL_HOST/MYSQL_PORT/MYSQL_USER/MONOLITH_DB before retrying." >&2
    exit 1
  fi
}

dump_tables() {
  local target_schema="$1"
  local output_file="$2"
  shift 2
  local tables=("$@")

  MYSQL_PWD="$MYSQL_PASSWORD" mysqldump \
    --host="$MYSQL_HOST" \
    --port="$MYSQL_PORT" \
    --user="$MYSQL_USER" \
    --default-character-set=utf8mb4 \
    --single-transaction \
    --set-gtid-purged=OFF \
    --skip-lock-tables \
    --no-create-db \
    "$MONOLITH_DB" "${tables[@]}" > "$output_file"

  mysql_exec "$target_schema" < "$output_file"
}


require_source_tables
mysql_exec < "scripts/db/create-msa-schemas.sql"


dump_tables "$USER_SCHEMA" "$EXPORT_DIR/${USER_SCHEMA}.sql" "${USER_TABLES[@]}"
dump_tables "$PRODUCT_SCHEMA" "$EXPORT_DIR/${PRODUCT_SCHEMA}.sql" "${PRODUCT_TABLES[@]}"
dump_tables "$ORDER_SCHEMA" "$EXPORT_DIR/${ORDER_SCHEMA}.sql" "${ORDER_TABLES[@]}"
dump_tables "$CONTENT_SCHEMA" "$EXPORT_DIR/${CONTENT_SCHEMA}.sql" "${CONTENT_TABLES[@]}"

echo "[OK] split export completed under $EXPORT_DIR"
echo "[INFO] monolith database $MONOLITH_DB is kept intact for later performance comparison."
