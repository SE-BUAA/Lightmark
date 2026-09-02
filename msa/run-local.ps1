# =====================================================================
# Lightmark MSA 本地一键运行（Windows PowerShell）
#
# 不需要 Kubernetes：构建并启动 4 个微服务 Docker 容器（8081-8084）
# 和前端 SPA 容器（默认 8080，/api 反代到已部署的 MSA 入口），
# 数据库使用服务器现有 MySQL（默认 150.230.223.11:3306，可覆盖）。
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File msa/run-local.ps1
#
# 环境变量（优先读取仓库根目录 .env，其次当前环境，最后默认值）：
#   DB_HOST / DB_PORT / DB_USER / DB_PASSWORD / JWT_SECRET
#   DB_ADMIN_USER / DB_ADMIN_PASSWORD   建库引导用的管理员账号（默认 root，可跳过）
#   USER_DB_* / PRODUCT_DB_* / ORDER_DB_* / CONTENT_DB_*
#   SKIP_DB_BOOTSTRAP=1                 跳过建库引导
# =====================================================================
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

function Resolve-Var([string]$Name, [string]$Default) {
    $v = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrEmpty($v)) { $v = $Default }
    Set-Item -Path "Env:$Name" -Value $v
    return $v
}

# ---------- 1. 读取仓库根 .env（若存在） ----------
$EnvFile = Join-Path (Split-Path -Parent $ScriptDir) ".env"
if (Test-Path $EnvFile) {
    Write-Host "[INFO] 读取 $EnvFile"
    Get-Content $EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $idx = $line.IndexOf("=")
            $k = $line.Substring(0, $idx).Trim()
            $v = $line.Substring($idx + 1).Trim()
            Set-Item -Path "Env:$k" -Value $v
        }
    }
}

# ---------- 2. 默认值（compose 插值用） ----------
$dbHost      = Resolve-Var "DB_HOST" "150.230.223.11"
$dbPort      = Resolve-Var "DB_PORT" "3306"
$dbUser      = Resolve-Var "DB_USER" "se"
$dbPassword  = Resolve-Var "DB_PASSWORD" ""
Resolve-Var "JWT_SECRET" "local-msa-dev-secret-0123456789abcdef" | Out-Null
Resolve-Var "JWT_ISSUER" "lightmark" | Out-Null
Resolve-Var "JWT_EXPIRE_MINUTES" "120" | Out-Null
# 前端：local 模式（默认）路由到本地 4 个服务；remote 模式反代到已部署的 MSA 入口
Resolve-Var "FRONTEND_API_MODE" "local" | Out-Null
Resolve-Var "MSA_API_HOST" "msa.lightmark.ortus.top" | Out-Null
Resolve-Var "MSA_API_HOST_IP" "150.230.223.11" | Out-Null
Resolve-Var "FRONTEND_PORT" "8080" | Out-Null

$schemaMap = @{
    "USER"    = "lightmark_user"
    "PRODUCT" = "lightmark_product"
    "ORDER"   = "lightmark_order"
    "CONTENT" = "lightmark_content"
}
foreach ($svc in @("USER", "PRODUCT", "ORDER", "CONTENT")) {
    Resolve-Var "${svc}_DB_HOST"     $dbHost | Out-Null
    Resolve-Var "${svc}_DB_PORT"     $dbPort | Out-Null
    Resolve-Var "${svc}_DB_NAME"     $schemaMap[$svc] | Out-Null
    Resolve-Var "${svc}_DB_USER"     $dbUser | Out-Null
    Resolve-Var "${svc}_DB_PASSWORD" $dbPassword | Out-Null
}

Write-Host "[INFO] 目标数据库: ${dbHost}:${dbPort}  用户: ${dbUser}  各服务 schema: lightmark_user/product/order/content"

# ---------- 3. 建库引导（可选；无权限时给出管理员 SQL） ----------
$bootstrapSql = @"
CREATE DATABASE IF NOT EXISTS lightmark_user CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS lightmark_product CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS lightmark_order CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS lightmark_content CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON lightmark_user.* TO '${dbUser}'@'%';
GRANT ALL PRIVILEGES ON lightmark_product.* TO '${dbUser}'@'%';
GRANT ALL PRIVILEGES ON lightmark_order.* TO '${dbUser}'@'%';
GRANT ALL PRIVILEGES ON lightmark_content.* TO '${dbUser}'@'%';
FLUSH PRIVILEGES;
"@

$skipBootstrap = [Environment]::GetEnvironmentVariable("SKIP_DB_BOOTSTRAP")
if ($skipBootstrap -eq "1") {
    Write-Host "[INFO] SKIP_DB_BOOTSTRAP=1，跳过建库引导（请自行确认 4 个 schema 已存在且有权限）"
}
elseif (Get-Command mysql -ErrorAction SilentlyContinue) {
    $adminUser = Resolve-Var "DB_ADMIN_USER" "root"
    $adminPass = Resolve-Var "DB_ADMIN_PASSWORD" ""
    $env:MYSQL_PWD = $adminPass
    try {
        mysql -h $dbHost -P $dbPort -u $adminUser --connect-timeout=8 -e $bootstrapSql 2>$null | Out-Null
        Write-Host "[OK] 4 个 MSA schema 已就绪并已授权给 '$dbUser'"
    }
    catch {
        Write-Host "[WARN] 无法以 $adminUser 建库（权限不足或密码错误）。请用管理员账号手动执行一次："
        Write-Host ""
        Write-Host "  mysql -h $dbHost -P $dbPort -u root -p"
        Write-Host "  $bootstrapSql"
        Write-Host ""
    }
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
else {
    Write-Host "[WARN] 未找到 mysql 客户端，跳过建库引导。请用管理员账号手动执行："
    Write-Host "  mysql -h $dbHost -P $dbPort -u root -p -e `"$bootstrapSql`""
}

# ---------- 4. 构建并启动 4 个服务容器 ----------
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "[FATAL] 未找到 docker（请先安装 Docker Desktop）" -ForegroundColor Red
    exit 1
}
Write-Host "[INFO] 构建并启动 4 个微服务容器（首次构建需数分钟）..."
docker compose -f docker-compose.local.yml up -d --build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# ---------- 5. 健康检查（最多约 200 秒） ----------
Write-Host "[INFO] 等待服务就绪 ..."
$allUp = $true
foreach ($entry in @(@("user-service", 8081), @("product-service", 8082), @("order-service", 8083), @("content-service", 8084))) {
    $name = $entry[0]; $port = $entry[1]; $up = $false
    for ($i = 0; $i -lt 40; $i++) {
        try {
            $resp = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://127.0.0.1:${port}/api/health"
            if ($resp.Content -match '"UP"') { $up = $true; break }
        }
        catch { }
        Start-Sleep -Seconds 5
    }
    if ($up) {
        Write-Host "[OK] $name  http://127.0.0.1:${port}/api/health -> UP"
    }
    else {
        Write-Host "[FAIL] $name  http://127.0.0.1:${port}/api/health 未就绪"
        Write-Host "       查看日志：docker compose -f docker-compose.local.yml logs $name"
        $allUp = $false
    }
}

# 前端 SPA（检查 Vue 挂载点）
$frontendPort = [Environment]::GetEnvironmentVariable("FRONTEND_PORT")
$msaApiHost   = [Environment]::GetEnvironmentVariable("MSA_API_HOST")
$frontendUp = $false
for ($i = 0; $i -lt 40; $i++) {
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 "http://127.0.0.1:${frontendPort}/"
        if ($resp.Content -match 'id="app"') { $frontendUp = $true; break }
    }
    catch { }
    Start-Sleep -Seconds 5
}
if ($frontendUp) {
    Write-Host "[OK] frontend  http://127.0.0.1:${frontendPort}/ -> SPA 已就绪（/api 反代到 $msaApiHost）"
}
else {
    Write-Host "[FAIL] frontend  http://127.0.0.1:${frontendPort}/ 未就绪"
    Write-Host "       查看日志：docker compose -f docker-compose.local.yml logs frontend"
    $allUp = $false
}

if ($allUp) {
    Write-Host ""
    Write-Host "=========================================================="
    Write-Host " 本地 MSA 全部就绪"
    Write-Host "   user-service     http://127.0.0.1:8081/api/health"
    Write-Host "   product-service  http://127.0.0.1:8082/api/health"
    Write-Host "   order-service    http://127.0.0.1:8083/api/health"
    Write-Host "   content-service  http://127.0.0.1:8084/api/health"
    Write-Host "   frontend         http://127.0.0.1:${frontendPort}/  （/api 反代到 $msaApiHost）"
    Write-Host ""
    Write-Host " 停止：docker compose -f docker-compose.local.yml down"
    Write-Host " 日志：docker compose -f docker-compose.local.yml logs -f <service>"
    Write-Host "=========================================================="
}
else {
    Write-Host "[ERROR] 部分服务未就绪，请查看上面日志定位问题" -ForegroundColor Red
    exit 1
}
