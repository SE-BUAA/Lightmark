# 拾光旅行 Lightmark —— 完整启动与部署指引

> 一站式在线旅游平台(课程大作业):机票/酒店/火车票/度假、下单支付退款改签、智能行程、AI 助手、
> 社区互动与管理后台。项目包含**两个可运行形态**:单体后端(`backend`,monolith-start 冻结,对照用)
> 与微服务版(`msa`,4 业务服务)。前端为同一套 Vue3 SPA。本文覆盖两形态的本地启动、服务器部署、
> 环境配置、CI/CD 与常见问题。

[![Vue 3](https://img.shields.io/badge/Vue-3.x-42b883?logo=vue.js)](https://vuejs.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-6db33f?logo=springboot)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479a1?logo=mysql)](https://mysql.com)
[![Kubernetes](https://img.shields.io/badge/k3s-1.x-326ce5?logo=kubernetes)](https://k3s.io/)

---

## 目录

- [0. 形态总览:我该跑哪一套?](#0-形态总览我该跑哪一套)
- [1. 功能与技术栈](#1-功能与技术栈)
- [2. 仓库结构](#2-仓库结构)
- [3. 环境变量与配置(.env 全解)](#3-环境变量与配置env-全解)
- [4. 数据库准备(单体库与 MSA 四 schema)](#4-数据库准备单体库与-msa-四-schema)
- [5. 单体版:本地与服务器](#5-单体版本地与服务器)
- [6. 微服务版:本地一键与服务器/CI 部署](#6-微服务版本地一键与服务器ci-部署)
- [7. CI/CD:两条流水线](#7-cicd两条流水线)
- [8. 测试、压测与云原生实验](#8-测试压测与云原生实验)
- [9. 常见问题 FAQ](#9-常见问题-faq)
- [10. 相关文档索引](#10-相关文档索引)

---

## 0. 形态总览:我该跑哪一套?

| 目的 | 用哪套 | 入口/说明 |
| --- | --- | --- |
| 课程第一阶段(单体还原/对照) | **单体** `backend` + `frontend` | develop 流水线部署到 `lightmark.ortus.top` |
| 第二阶段(微服务演示/答辩) | **微服务** `msa` | msa-develop 流水线部署到 `msa.lightmark.ortus.top` |
| 本地快速体验微服务 | 微服务本地一键 | `bash msa/run-local.sh`(6 个容器) |
| 本地开发单体前端/后端 | 单体源码 | `npm run serve` + `./mvnw spring-boot:run` |

> 双形态共用同一前端 SPA 与同一 `database/lightmark.sql` 数据源;单体保持 `monolith-start` 标签冻结用于性能对照。

---

## 1. 功能详解与技术栈

### 1.1 认证与权限

- 图形验证码
- 邮箱验证码注册
- 登录 / 注册均要求勾选隐私政策
- JWT 登录态
- 注册后自动写入 `user_role`,默认普通用户角色
- 管理员与普通用户权限分离(管理后台登录校验 ADMIN)

### 1.2 用户中心

- 个人资料修改、修改密码
- 头像上传(对象存储转 JPEG)
- 常用出行人管理
- 积分与会员等级展示
- 我的订单聚合(机票 / 酒店 / 火车票 / 度假)与分页
- 待支付订单继续支付、已支付订单退款
- 火车票订单从用户中心直接进入改签流程

### 1.3 机票

- 多条件搜索(出发/到达城市、日期、舱位、直飞)
- 价格预览、创建订单、模拟支付、取消 / 退款
- 改签(支持更换航班并计算差价)

### 1.4 酒店

- 酒店列表 / 房型查询(关键词、星级、设施等)
- 创建订单、模拟支付、取消订单
- 发票申请、评价
- 预订时可复用常用出行人

### 1.5 火车票

- 站点筛选(全量车站数据)
- 直达 / 中转查询、座位类型筛选
- 学生票 / 儿童票价格逻辑
- 创建订单、模拟支付、退款、改签
- 中转场景拆分订单,便于后续退改

### 1.6 度假产品

- 目的地 / 出发城市 / 日期 / 天数 / 标签筛选
- 产品详情与 AI 文案、智能行程助手
- 创建订单、取消险、模拟支付、退款
- 下单时可复用常用出行人

### 1.7 智能行程与社区

- AI 生成行程;行程保存 / 编辑 / 分享
- 游记发布、评论、点赞、问答
- 图片上传(对象存储)

### 1.8 管理后台

- 仪表盘
- 产品管理、订单管理、用户管理
- 操作日志、后台列表分页

### 1.9 技术栈

| 层次 | 选型 |
| --- | --- |
| 前端 | Vue 3 + TS + Pinia + Element Plus + Axios;Vue CLI 5 |
| 单体后端 | Spring Boot 3.5 + JDBC/Flyway + JWT/BCrypt |
| 微服务 | Spring Boot 3.5 × 4(user/product/order/content)+ lightmark-common(公共 JWT/响应/异常)+ Resilience4j |
| 数据库 | MySQL 8.0(单体 1 库;MSA 4 schema 同服务器) |
| AI / 存储 / 邮件 | DeepSeek API / Oracle 对象存储 PAR / QQ SMTP 授权码 |
| 部署 | Docker Compose + GitHub Actions + k3s(Traefik)+ GHCR 镜像 |

---

## 2. 仓库结构

```text
lightmark/
├── backend/                  # 单体后端(Spring Boot;Dockerfile、Flyway、测试)
├── frontend/                 # Vue3 SPA(Dockerfile.nginx / Dockerfile.runtime)
├── msa/                      # 微服务:lightmark-common + 4 服务(含各自 Dockerfile/Flyway/测试)
│   ├── docker-compose.local.yml   # 本地一键(4服务+前端+MCP)
│   ├── run-local.sh / run-local.ps1
│   └── nginx.msa.*.template       # 本地前端反代模板(local/remote 模式)
├── database/lightmark.sql    # 全量备份(建表+测试数据),空卷首启自动导入
├── scripts/
│   ├── db/                   # MSA 分库:split-mysql.sh、export-monolith-baseline.sh
│   ├── loadtest/             # 压测:HPA 扩缩容、单体vs微服务性能对比、单接口压测
│   ├── deploy-k8s.sh         # 单体 k8s 一键部署(develop 流水线用)
│   ├── deploy-msa-k8s.sh     # 微服务 k8s 一键部署(msa-develop 流水线用)
│   ├── deploy-mcp-12306.sh   # 12306 MCP 火车票服务(docker)
│   ├── deploy-user-service-k8s.sh / backup-db.sh / healthcheck.sh / make-env-secret.ps1
│   └── server-bootstrap.sh   # 服务器初始化(装 k3s)
├── deploy/k8s/               # k8s 清单:mysql/backend/frontend/ingress(单体)、msa/(4服务+前端+粘滞路由+Ingress)
├── docs/                     # 需求/设计/测试/拆分/微服务划分等交付文档
├── .github/workflows/        # deploy.yml(develop 单体)+ msa-deploy.yml(msa-develop 微服务)
├── docker-compose.yml(.mysql.yml)  # 单体 compose 部署
├── .env.example / .env       # 环境变量模板 / 实际配置(不入仓)
└── deploy-server.sh          # 单体 compose 服务器部署(legacy)
```

---

## 3. 环境变量与配置(.env 全解)

根目录 `.env` 是本地开发与服务器配置的事实源(已被 gitignore)。按 `cp .env.example .env` 创建。

### 3.1 配置层级(重要)

```text
本地/手动 compose  →  读取根目录 .env
CI/CD(k8s 流水线)  →  GitHub Secret SERVER_ENV_BASE64 或 服务器 ~/lightmark/.env.k8s
                     (服务器文件优先;改动后需删除该文件或直接编辑它再重跑流水线)
本地 MSA 一键脚本   →  读取根目录 .env + 可覆盖环境变量
```

`server.env`(仓库内模板,含 CI 所需完整键)不直接使用,用 `scripts/make-env-secret.ps1 -EnvFile server.env`
生成 base64 粘贴到 GitHub Secret `SERVER_ENV_BASE64`。

### 3.2 键清单

| 分组 | 变量 | 必填 | 说明 |
| --- | --- | --- | --- |
| 数据库 | `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | 是 | 应用连接库。服务器 `.env` 中 `DB_HOST=mysql`(k8s Service 名);本地连远程库填 IP |
| 容器库 | `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD` / `MYSQL_USER` / `MYSQL_DATABASE` | k8s/容器库时 | 官方 mysql 镜像初始化与分库授权(root 用于 split-mysql.sh) |
| JWT | `JWT_SECRET` | 是 | **≥32 字节**(所有服务 JwtTokenService 强校验),微服务间共享同一密钥 |
| JWT | `JWT_ISSUER` / `JWT_EXPIRE_MINUTES` | 否 | 默认 lightmark / 120 |
| AI | `DEEPSEEK_API_KEY`(及 `OPENAI_API_KEY`、`AI_API_URL`、`LIGHTMARK_AI_*` 可选) | AI 功能 | 单体后端与 content-service 读取;缺省时 AI 降级提示 |
| 邮件 | `AUTH_MAIL_USERNAME` / `AUTH_MAIL_PASSWORD` / `AUTH_MAIL_FROM_EMAIL` | 邮箱验证码 | **PASSWORD 是 QQ SMTP 授权码,不是登录密码** |
| 对象存储 | `OBJECT_STORAGE_BASE_URL` | 头像/图片上传 | Oracle PAR base URL,以 `/o/` 结尾,**有时效**,过期需在 OCI 重新生成并同步 `.env`/`server.env`/代码兜底三处 |
| MSA schema 覆盖 | `USER_DB_NAME`/`PRODUCT_DB_NAME`/`ORDER_DB_NAME`/`CONTENT_DB_NAME`(及 `*_DB_HOST/PORT/USER/PASSWORD`) | 微服务 | 默认 `lightmark_user/product/order/content`;`USER_FLYWAY_ENABLED`(默认 true,本地) |
| 本地 MSA 脚本 | `FRONTEND_PORT`(8080)、`FRONTEND_API_MODE`(local/remote)、`MSA_API_HOST`、`MSA_API_HOST_IP`、`MCP_PORT`(9000)、`DB_ADMIN_USER/PASSWORD`、`SKIP_DB_BOOTSTRAP` | 本地 | 见 §6.2 |
| CI/CD(Secret/Variable) | `SERVER_HOST`/`SERVER_USER`/`SERVER_SSH_KEY`/`SERVER_PORT`、`SERVER_ENV_BASE64`、`GHCR_USERNAME`/`GHCR_PAT`、`INGRESS_HOST`、`MSA_INGRESS_HOST`、`CERT_DIR`、`DEPLOY_DIR` | 流水线 | 见 §7.3 |
| 部署杂项 | `NGINX_CONF`(本地 http 配置)、`CERT_DIR`(TLS 证书目录 origin.crt/key) | 按需 | — |

---

## 4. 数据库准备(单体库与 MSA 四 schema)

1. **初始化**:`database/lightmark.sql` 在容器/k8s MySQL **数据卷为空时**自动导入(Compose 挂载
   `/docker-entrypoint-initdb.d`;k8s 打 ConfigMap);已含 20 张业务表与测试数据与 Flyway 历史。
2. **增量迁移**:单体后端 Flyway(`backend/.../db/migration/V*.sql`);微服务各自 Flyway(带 `flyway_schema_history`)。
3. **重新导出备份**:`bash scripts/backup-db.sh`(自动识别 k8s/docker 环境)。
4. **MSA 分库**(数据拆分到 4 schema):
   - 服务器(k8s pod 内 MySQL):部署流程自动执行 `scripts/db/split-mysql.sh`(幂等,20 表→4 schema,单体库保留);
   - 本地:MSA 脚本启动前自动引导创建 4 个 schema 并授权(无权限时打印管理员 SQL);
   - 说明:`hotel_order_detail`、`invoice_application` 为 MSA 新增表,由 order-service Flyway 自建,不参与迁移。

> 测试账号(实测有效):管理后台 `admin@lightmark.com / Qwe123456!`;普通管理员账号
> `23241023@buaa.edu.cn / Qwe123456!`(用户端登录需图形验证码,请按页面输入)。

---

## 5. 单体版:本地与服务器

### 5.1 本地开发(源码)

前置:JDK 17、Maven 3.6+、Node 16+、MySQL 8.0(或远程库,`.env` 指 `DB_HOST`)。

```bash
# 数据库:导入 database/lightmark.sql 到本地库 lightmark(或使用项目服务器库)
mysql -h <host> -u <user> -p lightmark < database/lightmark.sql

# 后端(8080)
cd backend && ./mvnw spring-boot:run

# 前端(8081,dev 代理 /api → 8080)
cd frontend && npm install && npm run serve
```

### 5.2 本地/单机 Docker(推荐快速验证)

```bash
cp .env.example .env      # 填 DB/JWT/AI/邮件/对象存储
docker compose up -d --build                       # 用外部/本地库(.env DB_HOST)
docker compose -f docker-compose.yml -f docker-compose.mysql.yml up -d --build   # 含容器化 MySQL(空卷自动导入 lightmark.sql)
curl -I http://127.0.0.1/api/health
```

### 5.3 服务器部署

- 手动(compose):上传包后 `bash deploy-server.sh`(或服务器目录内 `docker compose up -d --build`);
- 自动(推荐):push `develop` → 流水线部署到 k3s(见 §7),入口 `https://lightmark.ortus.top`。

---

## 6. 微服务版:本地一键与服务器/CI 部署

### 6.1 架构与端口速览

| 服务/组件 | 端口 | Schema / 说明 |
| --- | --- | --- |
| user-service | 8081 | lightmark_user(认证/用户/出行人/积分/日志;会话粘滞) |
| product-service | 8082 | lightmark_product(机票/酒店/火车/度假;火车经 12306 MCP) |
| order-service | 8083 | lightmark_order(下单/支付/退款/发票/评价/outbox) |
| content-service | 8084 | lightmark_content(社区/行程/AI) |
| msa-frontend | 8080(本地) | 静态 SPA,`/api` 前缀路由(本地 nginx 或远端 Ingress) |
| mcp-12306-server | 9000 | 火车票数据源(docker,`scripts/deploy-mcp-12306.sh`) |
| MySQL | 3306 | 同一服务器,4 个 schema |

详细设计见 `docs/微服务划分.md`(服务划分图/接口清单/表归属/跨服务调用)。

### 6.2 本地一键运行(无需 Kubernetes)

前置:Docker(+Compose);根目录 `.env`(数据库账号等,可缺省部分)。数据库默认连
服务器 MySQL `150.230.223.11:3306`(可用 `DB_HOST` 等覆盖)。

```bash
# Ubuntu / macOS
bash msa/run-local.sh
# Windows
powershell -ExecutionPolicy Bypass -File msa/run-local.ps1
```

脚本行为:读 `.env` → 建库引导(尝试创建 4 个 schema 并授权,无权限时打印管理员 SQL)→
`docker compose -f msa/docker-compose.local.yml up -d --build`(6 容器)→ 轮询各 `/api/health`、
前端首页(Vue 挂载点)与 MCP `/health` 直到就绪。

- 访问:`http://127.0.0.1:8080`(前端;`/api` 默认路由到本地 4 服务;
  设 `FRONTEND_API_MODE=remote` 可改为反代远端 `MSA_API_HOST`,默认 `msa.lightmark.ortus.top`);
- 服务直连:`http://127.0.0.1:8081~8084/api/health`;
- 停止:`docker compose -f msa/docker-compose.local.yml down`;
- 排障:先确认 `.env` 存在(脚本会注入 DB 密码与 JWT;直接 `docker compose up` 不带 `.env` 会启动失败)。

### 6.3 服务器/CI 部署(一键)

push `msa-develop` → 流水线(见 §7)在服务器执行 `scripts/deploy-msa-k8s.sh`,依次完成:

1. 12306 MCP 服务(宿主机 docker,幂等);
2. 命名空间/ghcr 凭据/TLS(`lightmark-msa-tls`,优先 `CERT_DIR/msa-origin.crt|key`,否则自签名含 SAN);
3. 确保 k8s MySQL(缺失则用 `database/lightmark.sql` 初始化);
4. 数据库拆分 + 授权(幂等);
5. 各服务 Secret(DB/JWT/AI/邮件等按服务最小集注入);
6. 部署 4 服务 + `msa-frontend` + MSA Ingress + **user-service 会话粘滞路由**(`/api/auth` sticky);
7. 滚动更新(maxSurge=0)+ 逐服务健康检查 + 部署记录(服务器 `deploy-history.log`)。

访问入口:

- 公网:`http://msa.lightmark.ortus.top`(灰云直连服务器;`https` 自签名需点"继续访问",
  或按 §9 FAQ 配置受信任证书);
- 域名解析:`msa.lightmark.ortus.top A → 服务器 IP`(DNS-only;Cloudflare 代理需要边缘证书覆盖两层子域);
- 健康检查:`http://msa.lightmark.ortus.top/api/health`。

---

## 7. CI/CD:两条流水线

| 流水线 | 触发分支 | 产物 | 说明 |
| --- | --- | --- | --- |
| `deploy.yml` | `develop` | 单体 backend/frontend 镜像 `1.0.<run>` | 部署单体 k8s(lightmark.ortus.top) |
| `msa-deploy.yml` | `msa-develop` | `<svc>-service-1.0.<run>` ×4 + `frontend-1.0.<run>` | 部署微服务 k8s(msa.lightmark.ortus.top) |

### 7.1 单体流水线阶段(门禁:任何一步失败即停)

| 阶段 | 内容 |
| --- | --- |
| ① test-backend | JDK17 `./mvnw test`(H2) |
| ② build-frontend | Node20 `npm ci && npm run build` |
| ③ build-push-images | ①②通过后 Buildx 多架构镜像推 GHCR(版本号+sha,不用 latest) |
| ④ deploy | SSH 上传部署文件,服务器 `scripts/deploy-k8s.sh`(Secret/ConfigMap/apply/滚动) |
| ⑤ health-check | 服务器端与公网浏览器路径双重健康断言 |

### 7.2 微服务流水线阶段

| 阶段 | 内容 |
| --- | --- |
| ① test-msa | `msa/` 全模块 `mvn test`(失败即停,不构建) |
| ② build-push-images | matrix 构建 4 服务镜像(GHCR,标签 `<svc>-service-1.0.<run>` + sha) |
| ②' build-frontend | 前端 SPA 镜像(`frontend-1.0.<run>`) |
| ③ deploy | 服务器 `scripts/deploy-msa-k8s.sh`(见 §6.3) |
| ④ health-check | 公网 `msa.lightmark.ortus.top`:`/api/health` UP + 首页 Vue 挂载点 |

### 7.3 GitHub Secrets / Variables 配置

仓库 → Settings → Secrets and variables → Actions:

| 类型 | 名称 | 填什么 |
| --- | --- | --- |
| Secret | `SERVER_HOST` / `SERVER_USER` / `SERVER_SSH_KEY` / `SERVER_PORT` | 服务器 IP/用户/SSH 私钥/端口(22) |
| Secret | `SERVER_ENV_BASE64` | `server.env` 的 base64(用 `scripts/make-env-secret.ps1` 生成;服务器已有 `~/lightmark/.env.k8s` 时以服务器文件为准) |
| Secret | `GHCR_USERNAME` / `GHCR_PAT` | 私有镜像拉取凭据(公开仓库可省) |
| Variable | `INGRESS_HOST` | 单体域名(默认 lightmark.ortus.top) |
| Variable | `MSA_INGRESS_HOST` | 微服务域名(默认 msa.lightmark.ortus.top) |
| Variable | `CERT_DIR` / `DEPLOY_DIR` | 服务器证书目录(默认 ~/certs)/部署目录(默认 ~/lightmark) |

服务器首次初始化:`sudo bash scripts/server-bootstrap.sh`(装 k3s,自带 Traefik);手动触发:Actions 页
Run workflow;手动部署:`TAG=1.0.x REPO=<owner>/<repo> bash scripts/deploy-k8s.sh`(单体)或
`... bash scripts/deploy-msa-k8s.sh`(微服务)。

成功/失败记录:GitHub Actions 历史 + Artifact(surefire 报告)+ 服务器 `deploy-history.log`
(单体失败自动回滚上一成功版本)。

---

## 8. 测试、压测与云原生实验

### 8.1 测试

```bash
# 单体(含单元+集成,H2)
cd backend && ./mvnw test
# 微服务全模块(依赖外部 DB 的集成用例由环境变量门控,未配置自动跳过)
cd msa && mvn -B -ntp test
```

报告:`docs/test/*.md`;CI Artifacts 保留 surefire 原始报告。

### 8.2 压测脚本(`scripts/loadtest/`,README 见该目录)

| 脚本 | 用途 | 运行位置 |
| --- | --- | --- |
| `hpa-test.sh` | HPA 演示:加压→Pod 增→停压→回落,输出 timeline.log | 服务器(kubectl) |
| `perf-compare.sh` | 单体 vs 微服务同条件各 3 轮,输出 summary.csv + ab 原文 | 服务器(同机对比) |
| `run-load.sh` | 单接口压测(ab) | 任意 |

前置:`sudo apt-get install -y apache2-utils`;HPA 演示前建议 `kubectl scale deploy lightmark-backend --replicas=0`
腾资源(结束后恢复为 1);原始数据在 `artifacts/load/`(gitignore,提交时归档到 04_tests)。

---

## 9. 常见问题 FAQ

### 单体/部署通用

1. **首页能开但 /api 502/503** → 后端没起来或副本为 0:`kubectl get deploy -n lightmark`(单体 backend 被压测缩容后要 `kubectl scale deploy lightmark-backend --replicas=1`)、`docker logs lightmark-backend --tail 50`。
2. **AI 报 "DeepSeek API key must be set"/降级文案** → 缺 `DEEPSEEK_API_KEY`,检查容器环境与 Secret 注入。
3. **头像上传 "object storage upload failed"** → `OBJECT_STORAGE_BASE_URL` 为空或 **PAR 过期**(OCI 控制台重新生成,同步 .env/server.env/代码兜底三处)。
4. **邮箱验证码发不出** → `AUTH_MAIL_PASSWORD` 需为 QQ SMTP **授权码**。
5. **前端报 ChunkLoadError** → 浏览器强刷 `Ctrl+Shift+R`。

### 微服务专属

6. **验证码图片不显示 / 返回 HTML** → `/api/auth` 未路由到 user-service(粘滞 IngressRoute 未生效),检查
   `kubectl get ingressroute -n lightmark`,重新应用 `deploy/k8s/msa/user-auth-sticky.yaml`(80/443 两条)。
7. **登录报 "captcha expired"** → 会话未连续:多副本下需粘滞路由(§6.3 已内置);pod 重启后 F5 重新拉验证码;
   user-service 崩溃则查 `kubectl logs deploy/user-service -n lightmark`。
8. **登录报 "internal error"(device 超长)** → 旧镜像;更新后 user_login_log 写入已按列宽截断(≥1.0.17)。
9. **产品页查不到数据(机票 0 条)** → 前端按机场码(PEK)搜索,数据存城市码(BJS):部署含
   "城市码归一化匹配"的 product-service 镜像后强刷;酒店空看下一项。
10. **酒店"暂无匹配酒店"但接口有数据** → 分页响应需含 `records` 字段(PageResponse 双字段别名,部署新镜像后强刷)。
11. **火车站点下拉为空** → `/api/trains/options` 需返回 `startStations/endStations/dates`(station.csv 全量);
    搜不到车次则检查 9000 端口 MCP(`docker ps | grep 9000`,旧容器占用需清理后重跑 `scripts/deploy-mcp-12306.sh`)。
12. **服务启动 CrashLoop** → `kubectl logs deploy/<svc> --tail=60`:常见为 YAML 重复键/多构造器无 @Autowired/
    Flyway 校验冲突(历史行与文件不一致时按报错删除或 repair)。
13. **主站能开但验证码挂** → 单体后端被缩到 0(见 FAQ 1)或 Ingress `/api/auth` 路由版本旧。

---

## 10. 相关文档索引

- 需求/设计/测试:`docs/软件需求规格说明书.md`、`docs/软件概要设计说明书.md`、`docs/软件详细设计说明书.md`、`docs/需求划分文档.md`、`docs/数据库设计.md`、`docs/test/*`
- 微服务:规划版 `docs/微服务拆分方案.md`;实现现状版 **`docs/微服务划分.md`**(服务划分图/接口清单/表归属/跨服务调用)
- 其他:`docs/API.md`、`docs/对象存储.md`、`docs/12306.md`、`docs/开发规范.md`、`scripts/loadtest/README.md`
