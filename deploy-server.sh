#!/usr/bin/env bash
set -euo pipefail

# One-click deploy on server
# Usage:
#   bash deploy-server.sh [archive_name] [target_dir] [archive_dir]
# Example:
#   bash deploy-server.sh lightmark.tar.gz /home/ubuntu/lightmark /home/ubuntu

ARCHIVE_NAME="${1:-lightmark.tar.gz}"
TARGET_DIR="${2:-/home/ubuntu/lightmark}"
ARCHIVE_DIR="${3:-/home/ubuntu}"
ARCHIVE_PATH="${ARCHIVE_DIR}/${ARCHIVE_NAME}"

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] docker is not installed"
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "[ERROR] docker compose plugin is not available"
  exit 1
fi

if [[ ! -f "$ARCHIVE_PATH" ]]; then
  echo "[ERROR] Archive not found: $ARCHIVE_PATH"
  echo "        Please upload it first, e.g.: scp lightmark.tar.gz user@server:${ARCHIVE_DIR}/"
  exit 1
fi

echo "[1/7] Preparing target directory: $TARGET_DIR"
mkdir -p "$TARGET_DIR"

echo "[2/7] Preparing .env"
# 环境变量统一在项目根目录的 .env 文件中修改
# 关键变量（部署前必须填写）：
#   DB_HOST / DB_USER / DB_PASSWORD  — 数据库连接
#   JWT_SECRET                        — JWT 签名密钥
#   DEEPSEEK_API_KEY                  — AI 服务 API Key（必填，否则后端无法启动）
#   AUTH_MAIL_USERNAME / AUTH_MAIL_PASSWORD / AUTH_MAIL_FROM_EMAIL  — QQ 邮箱凭据
# 其余变量有默认值，按需修改：
#   DB_PORT / DB_NAME / JWT_ISSUER / JWT_EXPIRE_MINUTES
#   SERVER_PORT / AI_MODEL / AUTH_MAIL_HOST / AUTH_MAIL_PORT

if [[ -f "$ARCHIVE_DIR/.env" && ! -f "$TARGET_DIR/.env" ]]; then
  cp "$ARCHIVE_DIR/.env" "$TARGET_DIR/.env"
elif [[ -f "$ARCHIVE_DIR/.env" && -f "$TARGET_DIR/.env" ]]; then
  # 远程 .env 更新时覆盖，但保留已有文件做备份
  cp "$TARGET_DIR/.env" "$TARGET_DIR/.env.bak" 2>/dev/null || true
  cp "$ARCHIVE_DIR/.env" "$TARGET_DIR/.env"
fi

# Fix CRLF + permissions — docker-compose 不兼容 Windows 换行符
if [[ -f "$TARGET_DIR/.env" ]]; then
  sed -i 's/\r$//' "$TARGET_DIR/.env" 2>/dev/null || sudo sed -i 's/\r$//' "$TARGET_DIR/.env"
  chmod 644 "$TARGET_DIR/.env" 2>/dev/null || sudo chmod 644 "$TARGET_DIR/.env"
  chown "$(whoami)" "$TARGET_DIR/.env" 2>/dev/null || sudo chown "$(whoami)" "$TARGET_DIR/.env" 2>/dev/null || true
fi

# Validate critical env vars exist
echo "[3/7] Validating .env"
if [[ ! -f "$TARGET_DIR/.env" ]]; then
  echo "[FATAL] .env not found at $TARGET_DIR/.env"
  echo "        Please upload it: scp .env user@server:${ARCHIVE_DIR}/"
  exit 1
fi

# shellcheck source=/dev/null
source "$TARGET_DIR/.env"

REQUIRED_VARS=("DB_HOST" "DB_USER" "DB_PASSWORD" "JWT_SECRET" "DEEPSEEK_API_KEY")
MISSING=false
for var in "${REQUIRED_VARS[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "  [FAIL] $var is not set in .env"
    MISSING=true
  fi
done

if $MISSING; then
  echo "[FATAL] Critical env vars missing. Fix .env and re-deploy."
  exit 1
fi
echo "  All critical env vars OK"

echo "[4/7] Cleaning old files (keeping .env)"
find "$TARGET_DIR" -mindepth 1 -maxdepth 1 ! -name '.env' -exec rm -rf {} +

echo "[5/7] Extracting archive: $ARCHIVE_PATH"
TMP_EXTRACT_DIR="$(mktemp -d "${ARCHIVE_DIR}/lightmark-extract.XXXXXX")"
cleanup() {
  rm -rf "$TMP_EXTRACT_DIR"
}
trap cleanup EXIT

tar -xzf "$ARCHIVE_PATH" -C "$TMP_EXTRACT_DIR"
cp -r "$TMP_EXTRACT_DIR"/. "$TARGET_DIR"/

cd "$TARGET_DIR"

echo "[6/7] Building and starting all services"
docker compose up -d --build

echo "[7/7] Done"
docker compose ps

echo ""
echo "Quick checks:"
echo "  curl -k -I https://127.0.0.1/"
echo "  curl -k -I https://127.0.0.1/api/auth/captcha"
echo ""
echo "Logs (optional):"
echo "  docker compose logs -f backend"
echo "  docker compose logs -f nginx"
