# Lightmark MSA（微服务版）

微服务拆分后的代码仓库目录，包含 4 个业务微服务与公共模块：

| 模块 | 端口 | 职责 | 数据库 Schema |
| --- | --- | --- | --- |
| `lightmark-common` | - | 公共模块（ApiResponse、JWT、异常） | - |
| `user-service` | 8081 | 认证、用户、角色权限、出行人、积分会员、登录/管理员日志 | `lightmark_user` |
| `product-service` | 8082 | 机票/酒店/火车票/度假产品、房型、浏览记录 | `lightmark_product` |
| `order-service` | 8083 | 下单、支付、退款、改签、发票、评价、outbox 补偿 | `lightmark_order` |
| `content-service` | 8084 | 社区游记/点赞/评论/问答、智能行程、AI 对话 | `lightmark_content` |

每个服务可独立构建、测试、打包镜像，Flyway 独立管理自己的 schema。

## 一、本地一键运行（无需 Kubernetes）

**要求**：Docker（+ Docker Compose）、仓库根目录 `.env`（含 DB/JWT 配置，可缺省部分值）。

**数据库**：默认使用服务器 MySQL `150.230.223.11:3306`（可用 `DB_HOST`/`DB_USER`/`DB_PASSWORD` 等环境变量覆盖）。首次运行脚本会自动尝试创建 4 个 schema 并授权；无建库权限时脚本会打印一段管理员 SQL，手动执行一次即可。

```bash
# Ubuntu / macOS
bash msa/run-local.sh

# Windows PowerShell
powershell -ExecutionPolicy Bypass -File msa/run-local.ps1
```

脚本做的事情：读取 `.env` → 建库引导（可选）→ `docker compose` 构建并启动 4 个容器 → 轮询 4 个 `/api/health` 直到全部 UP。

常用命令：

```bash
cd msa
docker compose -f docker-compose.local.yml logs -f user-service   # 查看日志
docker compose -f docker-compose.local.yml down                   # 停止
```

常见环境变量（均可覆盖）：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` | `150.230.223.11` / `3306` | 服务器数据库地址 |
| `DB_USER` / `DB_PASSWORD` | `se` / 空 | 应用账号（需对 4 个 schema 有权限） |
| `JWT_SECRET` | `local-msa-dev-secret` | 4 个服务共享的 JWT 密钥（生产必须注入真实值） |
| `USER_DB_NAME` 等 | `lightmark_user` 等 | 各服务 schema 名 |
| `DB_ADMIN_USER` / `DB_ADMIN_PASSWORD` | `root` / 空 | 建库引导用的管理员账号 |
| `SKIP_DB_BOOTSTRAP=1` | - | 跳过建库引导 |

## 二、CI/CD（GitHub Actions）

- **`msa-deploy.yml`**：仅在 **`msa-develop`** 分支推送时触发（也可手动 `workflow_dispatch`）。
  流程：全模块测试 → matrix 构建 4 个服务镜像（GHCR，多架构）→ SSH 到服务器执行
  `scripts/deploy-msa-k8s.sh` 一键部署（含数据库拆分、4 服务 + MSA Ingress、健康检查）→ 公网入口验证。
  任何一步失败即停止，成功/失败均写入服务器 `deploy-history.log`。
- 既有单体流水线 **`deploy.yml`**（`develop` 分支）保持不变，两者互不影响。

## 三、Kubernetes 部署（由流水线自动完成，也可手动）

```bash
TAG=1.0.5 REPO=se-buaa/lightmark bash scripts/deploy-msa-k8s.sh
```

部署脚本完成：命名空间/拉取凭据/TLS → 确保 k8s 内 MySQL → 数据库拆分
（`scripts/db/split-mysql.sh`，单体 `lightmark` 20 张表 → 4 个 schema，幂等）→
4 个服务 Secret → 渲染应用 4 个 Deployment/Service/HPA + MSA Ingress
（默认域名 `msa.lightmark.ortus.top`，`MSA_INGRESS_HOST` 可覆盖）→ 滚动更新 → 健康检查。

镜像命名：`ghcr.io/<repo>/<svc>-service:<svc>-service-<TAG>`（如 `user-service-1.0.5`）。

## 四、说明

- `hotel_order_detail`、`invoice_application` 为 MSA 新增表，由 order-service Flyway 基线自动创建，
  不参与单体数据迁移（见 `scripts/db/split-mysql.sh` 注释与 `docs/微服务拆分方案.md` §4 注2）。
- 本地与 k8s 中，服务间通过容器网络服务名互调（如 `http://user-service:8081`、`http://product-service:8082`）。
