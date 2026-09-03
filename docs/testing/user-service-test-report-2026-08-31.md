# A 部分 user-service 测试报告（2026-08-31）

## 1. 测试范围

本报告只记录 A 部分 `user-service` 及其依赖的 `lightmark-common` JWT 公共能力。测试使用 H2 内存数据库，不连接、不修改远程 MySQL，也不修改 `database/lightmark.sql`。

覆盖范围：认证参数校验、验证码限流/过期/消费、注册、登录、管理员登录、JWT 鉴权、当前用户资料、头像、密码、出行人、积分、会员等级、管理员用户接口、内部用户接口、健康/就绪/版本接口。

## 2. 执行命令与结果

在仓库根目录执行：

```bash
mvn -B -ntp -f msa/pom.xml -pl user-service -am test
```

结果：`BUILD SUCCESS`。

| 模块 | 测试类 | 用例数 | 通过 | 失败 | 数据库 |
| --- | --- | ---: | ---: | ---: | --- |
| lightmark-common | `JwtTokenServiceTest`、`ApiResponseTest` | 4 | 4 | 0 | 无 |
| user-service | 7 个单元测试类 | 16 | 16 | 0 | Mock |
| user-service | `UserServiceApiIntegrationTest` | 7 | 7 | 0 | H2 内存库 |
| **合计** |  | **23** | **23** | **0** |  |

> Maven 的 reactor 输出会同时统计公共模块，因此本轮完整 reactor 总数为 27 个；其中 user-service 自身为 23 个（16 个业务单测 + 7 个 API 集成场景）。

## 3.1 API 覆盖清单

`UserServiceApiIntegrationTest` 已对 user-service 的 Controller 路径做自动化验证：

| 类别 | 已覆盖路径 |
| --- | --- |
| 运维 | `/api/health`、`/api/ready`、`/api/version` |
| 认证 | `/api/auth/captcha`、`/api/auth/verification/email/send`、`/api/auth/register`、`/api/auth/login`、`/api/auth/admin/login`、`/api/auth/logout` |
| 用户资料 | `/api/user/current`、`/api/user/{id}/profile`、`/api/user/avatar`、`/api/user/avatar/upload`、`/api/user/password`、`/api/user/{id}/password` |
| 出行人与积分 | `/api/user/travelers`、`/api/user/{id}/travelers`、`/api/user/travelers/{id}`、`/api/user/{userId}/travelers/{id}`、`/api/user/points/logs`、`/api/user/{id}/points`、`/api/user/level/upgrade-info` |
| 管理员 | `/api/admin/users`、`/api/admin/users/{id}/status`、`/api/admin/users/{id}/level`、`/api/admin/logs` |
| 内部接口 | `/internal/user/{id}`、`/internal/user/{id}/travelers`、`/internal/user/{id}/points` |

同时验证了未登录 `401`、普通用户访问管理员接口 `403`、错误密码 `401`、错误旧密码 `400`、非法验证码和参数校验错误等异常路径。

## 3. 测试数据与隔离

- 测试配置：`msa/user-service/src/test/resources/application-test.yaml`。
- 测试表结构：`msa/user-service/src/test/resources/db/test-schema.sql`。
- 每个集成测试方法开始前清理 H2 表并插入普通用户、管理员和角色数据。
- JWT 测试密钥只存在于测试配置，生产密钥仍必须通过环境变量或 Kubernetes Secret 注入。
- 生产配置保持 `USER_FLYWAY_ENABLED=false`，因此执行测试不会触发生产迁移。

## 4. CI/CD 接入

`.github/workflows/deploy.yml` 的 `test-msa-user-service` job 执行同一条 Maven 命令，并上传 `msa/user-service/target/surefire-reports`。该 job 通过后才允许构建镜像和部署；测试失败会阻断后续 job。

## 5. 仍需现场验收的项目

以下项目依赖服务器或 Kubernetes 环境，不能用本地 H2 测试代替：

1. 在服务器配置的 `USER_DB_*` 环境变量下启动 user-service，确认能读取已有用户表。
2. 在 Kubernetes 中执行健康、就绪、版本检查，并查看 Pod 日志。
3. 现场执行一次部署失败诊断（查看 Deployment、Pod describe 和最近日志）。
4. 在真实网关入口使用测试账号执行注册/登录/用户资料等 API 回归；邮箱验证码发送需要有效 SMTP 配置。
