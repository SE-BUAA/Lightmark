# 数据库拆分脚本

本目录用于完成微服务拆分阶段的数据库基础工作，不执行性能对比，只保留后续对比所需的单体基线和拆分结果。

## 文件说明

- `create-msa-schemas.sql`
  - 创建 `lightmark_user`、`lightmark_product`、`lightmark_order`、`lightmark_content`
- `export-monolith-baseline.sh`
  - 导出当前单体库 `lightmark` 的完整 SQL 基线
  - 默认输出到 `artifacts/perf-baseline/monolith/lightmark-monolith.sql`
- `split-mysql.sh`
  - 按表归属把单体库中的数据导出并导入到 4 个 schema
  - 默认输出拆分 SQL 到 `artifacts/db-split/`

## 环境变量

- `MYSQL_HOST`
- `MYSQL_PORT`
- `MYSQL_USER`
- `MYSQL_PASSWORD`
- `MONOLITH_DB`
- `USER_SCHEMA`
- `PRODUCT_SCHEMA`
- `ORDER_SCHEMA`
- `CONTENT_SCHEMA`

## 用法

导出单体基线：

```bash
bash scripts/db/export-monolith-baseline.sh
```

创建目标 schema 并执行拆分：

```bash
bash scripts/db/split-mysql.sh
```

## 当前约束

- `lightmark` 单体库会被保留，不会被删除或覆盖。
- 本次提交只完成 E 部分基础设施，不执行 `monolith-start` vs `msa` 性能测试。
- 后续性能对比请使用 `artifacts/perf-baseline/monolith/lightmark-monolith.sql` 作为单体基线输入。
