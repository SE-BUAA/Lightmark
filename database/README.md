# 数据库备份 / 初始化脚本

本目录存放数据库全量备份（建表 + 测试数据），供数据库容器首次启动时自动读入。

## 文件说明

| 文件 | 说明 |
| --- | --- |
| `lightmark.sql` | MySQL 8.0 全量备份：全部表结构 + 测试数据（含 `flyway_schema_history`）。由 DBX 导出，UTF-8 编码，不含 `CREATE DATABASE`/`USE`，需在 `MYSQL_DATABASE=lightmark` 下执行 |

## 自动加载机制

- **Docker Compose**：`docker-compose.yml` 中 MySQL 服务把本目录挂载到
  `/docker-entrypoint-initdb.d`。MySQL 官方镜像在 **数据卷为空（首次启动）** 时
  会自动按文件名顺序执行其中的 `.sql` 脚本，之后通过命名卷 `lightmark-mysql-data`
  持久化，重复启动不会重复导入。
- **Kubernetes**：`scripts/deploy-k8s.sh` 会把 `lightmark.sql` 打成 ConfigMap
  `lightmark-init-sql` 并挂载到 MySQL Pod 的 `/docker-entrypoint-initdb.d`，
  同样只在数据卷为空时执行。

## 数据迁移

应用启动后由后端 Flyway 接管增量迁移，脚本位于：

```text
backend/src/main/resources/db/migration/V*.sql
```

备份导入时若库中已有 `flyway_schema_history`，Flyway 会从历史记录继续，
不会重复执行已应用的迁移。

## 重新生成备份（服务器上执行）

```bash
bash scripts/backup-db.sh          # 默认输出到 database/lightmark.sql
```

支持从 Kubernetes（kubectl）或 Docker Compose（docker exec）两种运行环境导出。
