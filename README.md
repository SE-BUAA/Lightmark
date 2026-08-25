# 拾光旅行 Lightmark

> 面向软件工程课程大作业的一站式在线旅游平台，覆盖机票、酒店、火车票、度假产品、智能行程、社区互动与后台运营管理。项目采用前后端分离架构，支持本地开发、Docker 部署，以及 AI / 对象存储 / 邮件验证码等工程化能力。

[![Vue 3](https://img.shields.io/badge/Vue-3.x-42b883?logo=vue.js)](https://vuejs.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-6db33f?logo=springboot)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479a1?logo=mysql)](https://mysql.com)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178c6?logo=typescript)](https://www.typescriptlang.org/)

---

## 目录

- [项目概览](#项目概览)
- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [目录结构](#目录结构)
- [本地开发](#本地开发)
- [环境变量与配置](#环境变量与配置)
- [Docker 部署](#docker-部署)
- [对象存储与邮件说明](#对象存储与邮件说明)
- [测试与验证](#测试与验证)
- [相关文档](#相关文档)

---

## 项目概览

**拾光旅行（Lightmark）** 是一个课程项目化实现的旅游服务平台，包含：

- 用户注册 / 登录 / 权限控制
- 用户中心、常用出行人、积分与会员
- 机票、酒店、火车票、度假产品查询与下单
- 模拟支付、退款、改签、订单聚合管理
- 智能行程、AI 助手、社区游记 / 问答
- 管理后台、操作日志、仪表盘

项目强调的不只是“功能做出来”，还包括：

- 前后端分离
- 统一接口与权限控制
- 可部署性
- 自动化测试与回归验证
- AI / Agent / Skill 辅助开发实践

---

## 核心功能

### 1. 认证与权限

- 图形验证码
- 邮箱验证码注册
- 登录 / 注册均要求勾选隐私政策
- JWT 登录态
- 注册后自动写入 `user_role`，默认普通用户角色
- 管理员与普通用户权限分离

### 2. 用户中心

- 个人资料修改
- 头像上传（对象存储）
- 常用出行人管理
- 积分与会员等级展示
- 我的订单聚合（机票 / 酒店 / 火车票 / 度假）
- 订单分页展示
- 待支付订单继续支付
- 已支付订单退款
- 火车票订单从用户中心直接进入改签流程
- 自然语言修改昵称 / 邮箱 / 手机号

### 3. 机票

- 多条件搜索
- 价格预览
- 创建订单
- 模拟支付
- 取消 / 退款
- 改签（支持更换航班并计算差价）

### 4. 酒店

- 酒店列表 / 房型查询
- 订单创建
- 模拟支付
- 取消订单
- 发票申请
- 评价相关流程
- 预订时可复用常用出行人

### 5. 火车票

- 站点筛选
- 直达 / 中转查询
- 座位类型筛选
- 学生票 / 儿童票价格逻辑
- 创建订单
- 模拟支付
- 退款
- 改签
- 中转场景支持拆分订单，便于后续退改
- 改签基于订单号，不再依赖取票码

### 6. 度假产品

- 目的地 / 出发城市 / 日期 / 天数 / 标签筛选
- 产品详情与 AI 文案
- 订单创建
- 取消险
- 模拟支付
- 退款
- 智能行程助手
- 下单时可复用常用出行人

### 7. 智能行程与社区

- AI 生成行程
- 行程保存 / 编辑 / 分享
- 游记发布
- 评论、点赞、问答
- 图片上传（对象存储）

### 8. 管理后台

- 仪表盘
- 产品管理
- 订单管理
- 用户管理
- 操作日志
- 后台列表分页

---

## 技术栈

| 层次 | 技术选型 |
|---|---|
| 前端 | Vue 3、TypeScript、Vue Router、Pinia、Element Plus、Axios、ECharts |
| 构建（前端） | Vue CLI 5 |
| 后端 | Spring Boot 3.5、MyBatis-Plus、JDBC、JWT、BCrypt |
| 数据库 | MySQL 8.0 |
| 迁移 | Flyway |
| AI | DeepSeek / OpenAI 兼容接口、Spring AI（部分模块） |
| 文件存储 | Oracle Object Storage PAR URL |
| 邮件 | QQ SMTP 授权码登录 |
| 部署 | Docker Compose + Nginx |

---

## 目录结构

```text
lightmark/
├── backend/                 # Spring Boot 后端
├── frontend/                # Vue 前端
├── docs/                    # 课程文档 / 设计 / 测试 / 对象存储说明等
├── deploy/                  # Nginx 配置
├── docker-compose.yml       # Docker 部署编排
├── deploy-server.sh         # 服务器部署脚本
├── uninstall-server.sh      # 卸载脚本
├── package-project.ps1      # Windows 一键打包上传部署脚本
├── .env                     # 本地 / 部署环境变量（不入仓）
└── README.md
```

### 后端关键目录

```text
backend/src/main/java/top/ortus/lightmark/backend/
├── controller/              # REST 接口
├── service/                 # 业务逻辑
├── dao/                     # 实体 / Mapper / Repository
├── dto/                     # 请求 / 响应 DTO
├── config/                  # 配置项
├── common/                  # 通用响应、分页等
├── exception/               # 异常处理
├── security/                # JWT / 鉴权
└── converter/               # DTO / 实体转换
```

### 前端关键目录

```text
frontend/src/
├── api/                     # 接口封装
├── components/              # 公共组件
├── layouts/                 # 布局
├── router/                  # 路由与守卫
├── stores/                  # Pinia 状态
├── utils/                   # 请求封装 / auth 工具
└── views/                   # 页面视图
```

---

## 本地开发

### 前置要求

- Node.js >= 16
- npm >= 7
- JDK 17
- Maven >= 3.6
- MySQL 8.0

### 1）安装前端依赖

```bash
cd frontend
npm install
```

### 2）准备数据库

- 创建数据库：`lightmark`
- 使用项目中的建表 / migration 方案初始化数据
- 如已启用 Flyway，启动后端时会自动执行 migration

### 3）配置 `.env`

在项目根目录创建 / 修改：

```env
# ===== DB =====
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=lightmark
DB_USER=root
DB_PASSWORD=your_password

# ===== JWT =====
JWT_SECRET=replace-with-your-own-secret
JWT_ISSUER=lightmark
JWT_EXPIRE_MINUTES=120

# ===== AI =====
DEEPSEEK_API_KEY=your_deepseek_key
OPENAI_API_KEY=

# ===== QQ Mail SMTP =====
AUTH_MAIL_USERNAME=your_qq_mail@qq.com
AUTH_MAIL_PASSWORD=your_smtp_authorization_code
AUTH_MAIL_FROM_EMAIL=your_qq_mail@qq.com

# ===== Object Storage (Oracle PAR) =====
OBJECT_STORAGE_BASE_URL=https://objectstorage.xxx.oraclecloud.com/p/...
```

### 4）启动后端

```bash
cd backend
./mvnw spring-boot:run
```

或：

```bash
./mvnw clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

默认后端地址：

- `http://localhost:8080`

### 5）启动前端

```bash
cd frontend
npm run serve
```

默认前端地址：

- `http://localhost:8081`

开发环境中 `/api` 会自动代理到后端 `8080`。

---

## 环境变量与配置

### 核心环境变量

| 变量 | 用途 | 是否必填 |
|---|---|---|
| `DB_HOST` | 数据库地址 | 是 |
| `DB_PORT` | 数据库端口 | 否 |
| `DB_NAME` | 数据库名 | 是 |
| `DB_USER` | 数据库用户名 | 是 |
| `DB_PASSWORD` | 数据库密码 | 是 |
| `JWT_SECRET` | JWT 签名密钥 | 是 |
| `JWT_ISSUER` | JWT 发行者 | 否 |
| `JWT_EXPIRE_MINUTES` | JWT 过期时间 | 否 |
| `DEEPSEEK_API_KEY` | DeepSeek API Key | 是（启用 AI 时） |
| `AUTH_MAIL_USERNAME` | QQ 邮箱账号 | 是（启用邮箱验证码时） |
| `AUTH_MAIL_PASSWORD` | QQ SMTP 授权码 | 是 |
| `AUTH_MAIL_FROM_EMAIL` | 发件邮箱 | 是 |
| `OBJECT_STORAGE_BASE_URL` | Oracle 对象存储 PAR base URL | 是（启用图片 / 头像上传时） |

### AI 变量说明

项目中实际主要使用：

- `DEEPSEEK_API_KEY`

部署时 `docker-compose.yml` 会把它同时映射为：

- `DEEPSEEK_API_KEY`
- `SPRING_AI_DEEPSEEK_API_KEY`
- `LIGHTMARK_AI_API_KEY`（兼容链路）

所以你主要只要保证 `.env` 中的 `DEEPSEEK_API_KEY` 正确即可。

### 邮件说明

`AUTH_MAIL_PASSWORD` 不是 QQ 登录密码，而是 **SMTP 授权码**。

### 对象存储说明

`OBJECT_STORAGE_BASE_URL` 不是某个具体文件地址，而是 **PAR base URL**。最终上传路径由：

```text
OBJECT_STORAGE_BASE_URL + "/" + objectName
```

拼接得到。详细说明见：
- [docs/对象存储.md](docs/对象存储.md)

---

## Docker 部署

### 1）一键部署（Windows 本地执行）

```powershell
.\package-project.ps1 -UploadAndDeploy
```

这个脚本会：

1. 打包项目（排除 `node_modules`、`target`、`.git`、`.env`）
2. 单独上传：
   - `lightmark.tar.gz`
   - `deploy-server.sh`
   - `uninstall-server.sh`
   - `.env`
3. SSH 到服务器执行远程部署

### 2）服务器部署脚本行为

`deploy-server.sh` 会：

1. 准备目标目录
2. 复制 `.env` 到部署目录
3. 自动把 Windows CRLF 转成 Linux LF
4. 校验关键环境变量是否存在
5. 解压代码包
6. 执行：

```bash
docker compose up -d --build
```

### 3）docker-compose 对外端口

当前暴露：

- `80` → HTTP（自动跳转 HTTPS）
- `443` → HTTPS

Nginx 负责：

- 前端静态资源托管
- `/api/*` 代理到 backend:8080

### 4）部署后快速检查

```bash
docker compose ps
curl -k -I https://127.0.0.1/
curl -k -I https://127.0.0.1/api/auth/captcha
```

---

## 对象存储与邮件说明

### 对象存储

上传逻辑位于：
- `backend/src/main/java/top/ortus/lightmark/backend/service/ObjectStorageService.java`

它会：

- 读取 `OBJECT_STORAGE_BASE_URL`
- 生成 `objectName`
- 通过 HTTP PUT 直接上传到 Oracle PAR URL

存储方式：

- 头像：完整 URL 存在 `user.avatar`
- 游记图片：完整 URL 返回给前端，作为正文内容的一部分写入 `post.content`

### 邮件验证码

邮件配置来自 `.env`：

- `AUTH_MAIL_USERNAME`
- `AUTH_MAIL_PASSWORD`
- `AUTH_MAIL_FROM_EMAIL`

若配置错误，后端会报：
- `qq smtp send failed`
- 或 SMTP 认证失败信息

---

## 测试与验证

### 前端

```bash
cd frontend
npm run build
```

### 后端

```bash
cd backend
./mvnw -q -DskipTests compile
```

### 测试文档

详见：
- [docs/测试文档.md](docs/测试文档.md)

项目已覆盖的重点测试模块包括：

- 认证与权限
- 用户中心
- 机票 / 酒店 / 火车票 / 度假
- 智能行程与社区
- 管理后台

---

## 相关文档

- [docs/需求划分文档.md](docs/需求划分文档.md)
- [docs/软件详细设计说明书.md](docs/软件详细设计说明书.md)
- [docs/测试文档.md](docs/测试文档.md)
- [docs/API.md](docs/API.md)
- [docs/数据库设计.md](docs/数据库设计.md)
- [docs/开发规范.md](docs/开发规范.md)
- [docs/对象存储.md](docs/对象存储.md)
- [docs/12306.md](docs/12306.md)

---

## 部署常见问题 FAQ

### 1. 为什么前端首页能打开，但 `/api/*` 返回 502？

这通常说明 **nginx 已启动，但 backend 没正常启动**。

优先检查：

```bash
docker compose ps
docker logs lightmark-backend --tail 50
```

常见原因：

- 数据库连接变量没传进去
- `DEEPSEEK_API_KEY` 没配置
- `.env` 没被复制到部署目录
- `.env` 是 Windows CRLF 换行，docker compose 读取失败

---

### 2. 为什么日志里提示 `DeepSeek API key must be set`？

说明 backend 容器没有拿到 AI 相关环境变量。

请检查：

1. 部署目录是否存在 `.env`
2. `.env` 里是否有：

```env
DEEPSEEK_API_KEY=你的key
```

3. `docker-compose.yml` 是否已透传：

- `DEEPSEEK_API_KEY`
- `SPRING_AI_DEEPSEEK_API_KEY`
- `LIGHTMARK_AI_API_KEY`

重新部署后再看日志。

---

### 3. 为什么 docker compose 提示 `DB_HOST` / `JWT_SECRET` 等变量未设置？

这说明部署目录中的 `.env` 没有生效。

请确认：

```bash
cat /home/ubuntu/lightmark/.env
```

如果没有该文件：

```bash
cp /home/ubuntu/.env /home/ubuntu/lightmark/.env
```

如果文件存在但变量仍然不生效，通常是 CRLF 问题：

```bash
sed -i 's/\r$//' /home/ubuntu/lightmark/.env
```

---

### 4. 为什么对象存储上传失败？

对象存储依赖：

```env
OBJECT_STORAGE_BASE_URL=
```

这个值必须是 **Oracle Object Storage 的 PAR base URL**，不是某个单独文件地址。

如果上传失败，优先检查：

- `OBJECT_STORAGE_BASE_URL` 是否为空
- PAR URL 是否过期
- 路径是否是 `/o/` 结尾的 base URL

参考：

- [docs/对象存储.md](docs/对象存储.md)

---

### 5. 为什么邮箱验证码发送失败？

邮箱验证码依赖：

```env
AUTH_MAIL_USERNAME=
AUTH_MAIL_PASSWORD=
AUTH_MAIL_FROM_EMAIL=
```

注意：

- `AUTH_MAIL_PASSWORD` 不是 QQ 登录密码
- 而是 **QQ 邮箱 SMTP 授权码**

如果发送失败：

1. 确认 SMTP 已开启
2. 确认使用的是授权码
3. 确认授权码未失效
4. 确认 `AUTH_MAIL_USERNAME` 与 `AUTH_MAIL_FROM_EMAIL` 正确

---

### 6. 为什么部署后前端报 `ChunkLoadError`？

这是典型的 **浏览器缓存旧前端资源** 问题。

表现：

- `Loading chunk xxx failed`
- js/css 请求返回的是 html
- MIME type 错误

解决：

1. 浏览器强制刷新：`Ctrl + Shift + R`
2. 如仍不行，清除站点缓存
3. 重新构建并部署前端镜像

---

### 7. 为什么 `mvn clean compile` 会报一堆 Lombok getter/setter 找不到？

这是因为命令行 Maven 编译没有正确启用 Lombok annotation processor。

项目现在已经在 `backend/pom.xml` 中补了：

- `maven-compiler-plugin`
- `annotationProcessorPaths`
- `lombok`

如果你本地仍看到类似问题，先：

```bash
cd backend
./mvnw -q -DskipTests compile
```

并在 IDEA 中执行一次 Maven Reload。

---

### 8. 为什么 backend 启动后又马上重启？

这通常是容器进程启动后抛异常退出，Docker 会自动重启。

检查方式：

```bash
docker compose ps
docker logs lightmark-backend --tail 100
```

如果状态是：

```text
Restarting (1)
```

那就不要先看 nginx，先看 backend 的异常栈。

---

### 9. 为什么用户看到的订单时间或倒计时不对？

如果服务器部署在 UTC，而用户浏览器在东八区，直接传 `LocalDateTime` 容易造成：

- 下单时间少 8 小时
- 倒计时直接变成 0 秒

项目现在已经通过 **epoch 毫秒时间戳** 修正：

- 订单时间：优先使用 `createEpochMs`
- 倒计时：优先使用 `expireEpochMs`

如果你看到时间还是不对，说明前端部署的还是旧版本。

---

### 10. 如果我要重新完整部署，最推荐的命令是什么？

本地 Windows：

```powershell
.\package-project.ps1 -UploadAndDeploy
```

服务器手动：

```bash
cd /home/ubuntu/lightmark
docker compose down --remove-orphans
docker compose up -d --build
```

如果你怀疑前端缓存或镜像缓存：

```bash
docker compose build --no-cache nginx
docker compose up -d
```

## 当前实现亮点

- 登录 / 注册均强制勾选隐私政策
- 注册自动写入普通用户角色 `user_role(role_id = 2)`
- 用户中心支持常用出行人、积分、订单分页
- 用户订单支持继续支付、退款、火车票改签入口
- 火车票支持直达 / 中转 / 改签 / 退款 / 中转订单拆分
- 度假产品支持 AI 文案与智能助手
- 管理后台支持分页、日志和仪表盘
- 部署链路已打通 `.env`、对象存储、邮件和 Docker 配置

---

如需生成课程答辩 PPT、部署检查单或管理员用户手册，可继续基于 `docs/` 中现有文档扩展。