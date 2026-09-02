# user-service 用户服务边界

数据库连接默认指向课程组远程 MySQL `150.230.223.11:3306` 的现有 `lightmark` 库；密码只通过 `USER_DB_PASSWORD` 环境变量提供，不保存在代码中。当前分支默认关闭 Flyway 和 Spring SQL 初始化，不会自动创建或修改表；只有准备好独立 user schema 后才显式设置 `USER_FLYWAY_ENABLED=true`。

服务可独立构建和部署：`msa/user-service/Dockerfile` 生成独立镜像，`deploy/k8s/msa/user-service.yaml` 包含 Deployment、Service 和 HPA，`scripts/deploy-user-service-k8s.sh` 负责版本化部署、滚动更新和健康检查。部署所需的 `USER_DB_*`、`JWT_SECRET`、邮件配置等只从服务器 Secret 注入。

## 管理的数据表

以下 8 张表只允许 `user-service` 访问：

`user`、`role`、`user_role`、`traveler`、`points_log`、`user_login_log`、`auth_verification_code`、`admin_log`。

`points_log.order_id` 只是订单编号的引用值，不建立跨 Schema 外键。订单服务需要用户信息、出行人或积分操作时，只能调用 user-service 的内部接口，不能直接查询这些表。

当前服务器仍使用单体库时，隔离通过代码访问边界保证：user-service 只读写上面 8 张表；待部署环境提供独立 schema 后，通过 `USER_DB_NAME` 覆盖数据库名即可，无需改代码。

## 迁移范围

迁移到本服务的业务包括认证、验证码、用户资料、头像、密码、角色、出行人、积分/会员信息、登录日志和管理员用户管理。

订单、产品、社区、AI 以及对应后台接口不属于本服务。单体 `backend` 目录保留为改造前版本。

## 对外接口

- `GET /api/auth/captcha`
- `POST /api/auth/verification/email/send`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/admin/login`
- `POST /api/auth/logout`
- `GET/PUT /api/user/current`
- `POST /api/user/avatar`
- `POST /api/user/avatar/upload`
- `PUT /api/user/password`
- `GET/POST/PUT/DELETE /api/user/travelers`
- `GET /api/user/points/logs`
- `GET /api/user/level/upgrade-info`
- `GET /api/admin/users`
- `PUT /api/admin/users/{id}/status`
- `PUT /api/admin/users/{id}/level`
- `GET /api/admin/logs`

## 内部接口

- `GET /internal/user/{id}`：返回昵称、头像等脱敏信息。
- `GET /internal/user/{id}/travelers`：校验并读取该用户的出行人。
- `POST /internal/user/{id}/points`：增加或撤销积分；调用方应带订单号、来源和操作类型，重复请求不重复记账。

## 运行信息接口

- `GET /api/health`：存活检查，只表示进程正常。
- `GET /api/ready`（兼容 `/api/readiness`）：就绪检查，执行只读 `SELECT 1` 验证数据库连接。
- `GET /api/version`：返回 `APP_VERSION` 注入的镜像/发布版本。

## JWT 共享密钥

user-service 使用 `JWT_SECRET` 签发 token；product/order/content 服务校验 token 时必须注入同一个 `JWT_SECRET`。密钥至少 32 个 UTF-8 字节，只通过 GitHub Secret、Kubernetes Secret 或运行环境变量提供，仓库中不保存默认密钥。这样各服务可以独立校验用户身份，不需要访问 `user` 表。

校验代码位于公共模块 `top.ortus.lightmark.common.security.JwtAuthenticationInterceptor`；三个业务服务各自声明 `JwtTokenService` Bean，并将该校验器挂到 `/internal/**`。因此服务间调用携带 Bearer token 即可完成签名、签发者和过期时间校验，任何服务都不需要共享用户表。

## 入口路由

`deploy/k8s/ingress.yaml` 已将 `/api/auth`、`/api/user`、`/api/admin/users`、`/api/admin/logs` 路由到 `user-service:8081`；其他尚未迁移的 `/api` 路径继续路由到单体 `backend:8080`，避免迁移期间影响现有功能。
