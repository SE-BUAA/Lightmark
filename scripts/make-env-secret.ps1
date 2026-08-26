# =====================================================================
# Generate SERVER_ENV_BASE64 for GitHub Actions Secret.
#
# Usage (run in project root, after filling .env per .env.example):
#   powershell -ExecutionPolicy Bypass -File scripts/make-env-secret.ps1
#   powershell -ExecutionPolicy Bypass -File scripts/make-env-secret.ps1 -EnvFile server.env
#
# Output:
#   - base64 string is copied to clipboard
#   - base64 string is printed to console; paste it into
#     GitHub > Settings > Secrets and variables > Actions >
#     New repository secret > SERVER_ENV_BASE64
#
# Notes:
#   - .env must use the NEW format (MYSQL_ROOT_PASSWORD / MYSQL_PASSWORD /
#     DB_USER / DB_PASSWORD / JWT_SECRET etc., see .env.example)
#   - The secret is written to the server only ONCE, when ~/lightmark/.env
#     does not exist yet. Afterwards edit .env on the server directly and
#     re-trigger the pipeline.
#   - ASCII-only on purpose: works on Windows PowerShell 5.1 and pwsh 7.
# =====================================================================
param(
    [string]$EnvFile = ".env"
)

if (-not (Test-Path $EnvFile)) {
    Write-Error "File not found: $EnvFile. Fill .env first (see .env.example)."
    exit 1
}

# Read as UTF-8 explicitly (handles BOM and no-BOM files)
$content = Get-Content $EnvFile -Raw -Encoding UTF8

# Normalize line endings to LF (literal replace, no regex)
$content = $content.Replace("`r`n", "`n")

$bytes = [System.Text.Encoding]::UTF8.GetBytes($content)
$b64 = [Convert]::ToBase64String($bytes)

Write-Host ""
Write-Host "=== SERVER_ENV_BASE64 (copied to clipboard) ==="
Set-Clipboard $b64
Write-Host $b64
Write-Host "=============================================="
