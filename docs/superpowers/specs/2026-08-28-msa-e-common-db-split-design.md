# E 部分基础设施设计

## 目标

完成 `docs/微服务拆分方案.md` 中 E 项的基础部分，不执行性能对比，只交付后续微服务迁移所需的公共模块、多模块工程骨架、数据库拆分脚本、Flyway 迁移归位和单体数据导出基线。

## 范围

- 新建 `msa/` Maven 多模块父工程。
- 新建 `msa/lightmark-common` 公共模块。
- 新建 `msa/user-service`、`msa/product-service`、`msa/order-service`、`msa/content-service` 服务骨架。
- 新建 4 个 schema 创建脚本、单体数据导出脚本、按表归属拆分的库迁移脚本。
- 将现有单体 Flyway 迁移按服务边界归位到各服务目录。
- 保留后续性能对比输入，包括单体基线导出目录、说明文档与脚本入口。

## 明确不做

- 不执行 `monolith-start` vs `msa` 性能对比。
- 不在本次提交中迁移完整业务 Controller / Service / DAO。
- 不直接在 `develop` 分支开发或提交。

## 分支策略

按 `docs/开发规范.md` 执行：

- 创建 `msa-develop` 作为微服务开发基线分支。
- 从 `msa-develop` 切出 `test/e-common-db-split` 进行开发。
- 本次成果仅提交到功能分支。

## 工程结构

```text
msa/
├── pom.xml
├── lightmark-common/
├── user-service/
├── product-service/
├── order-service/
└── content-service/
```

### lightmark-common 最小抽取集

- `ApiResponse`
- `PageResponse`
- `PageResult`
- `ApiException`
- `GlobalExceptionHandler`
- `JwtTokenService`
- `UserIdentity`

以上内容都来自单体中已稳定使用的公共能力，优先抽取这部分，避免把 `CrudController`、`GenericCrudService`、`ModuleController` 和单体数据库兼容补丁逻辑带进微服务骨架。

## 服务骨架要求

每个服务先只提供：

- 独立 `pom.xml`
- 独立启动类
- 独立 `application.yaml`
- 独立 Flyway 目录
- 独立 `/api/health` 接口
- 依赖 `lightmark-common`，禁止依赖其他 service 模块

## 数据库拆分设计

### 目标 schema

- `lightmark_user`
- `lightmark_product`
- `lightmark_order`
- `lightmark_content`

### 脚本交付

- `scripts/db/create-msa-schemas.sql`
- `scripts/db/export-monolith-baseline.sh`
- `scripts/db/split-mysql.sh`

### 数据导出策略

- 以现有单体 `lightmark` 为导出源。
- 先导出完整基线 SQL 备份，供后续性能对比和数据回放使用。
- 再按表归属分别导出到各服务目标 schema。
- 默认保留单体原库，避免破坏后续 `monolith-start` 基线对比。

## Flyway 归位策略

每个服务使用独立 Flyway 目录：

- `user-service`: `classpath:db/migration/user`
- `product-service`: `classpath:db/migration/product`
- `order-service`: `classpath:db/migration/order`
- `content-service`: `classpath:db/migration/content`

### 归位规则

- `auth_verification_code` 相关迁移归 `user-service`
- `product` / `room_type` 相关迁移归 `product-service`
- `orders` / `payment_record` / `flight_order_detail` / `invoice_application` / `review` 相关迁移归 `order-service`
- `travel_plan` / `post` / `post_like` / `comment` / `question` 相关迁移归 `content-service`

对于原先混在一起的迁移文件，本次不直接修改单体迁移，而是在 `msa` 中生成按服务边界重排后的迁移文件，作为微服务环境的独立起点。

## 测试与验证

本次以基础验证为主：

- `lightmark-common` 编译与单元测试通过
- `msa` 父工程可完成模块解析与编译
- 各服务骨架可成功打包
- 数据库脚本具备可读用法说明且参数来自环境变量

## 风险与约束

- 单体现有迁移存在跨表、跨域混合问题，本次通过“微服务迁移重排”解决，不回写单体迁移历史。
- 远端还没有 `msa-develop`，本次只创建本地合规分支。
- 性能对比必须等其他微服务完成业务迁移后再执行，本次只保留基线和入口，不生成性能结论。
