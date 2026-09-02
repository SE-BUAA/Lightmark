# user-service 非测试交付说明

## 已交付

- 可复现构建：`mvn -f msa/pom.xml -pl user-service -am package`；可执行包为
  `msa/user-service/target/user-service-*-boot.jar`。普通 JAR 保留为构建中间产物，避免 Windows
  重复打包时覆盖被占用的文件。
- 独立镜像：`msa/user-service/Dockerfile`
- 独立 Kubernetes 资源：`deploy/k8s/msa/user-service.yaml`
  - Deployment：1 个起始副本，资源 requests/limits，非 root 容器
  - Service：集群内 `user-service:8081`
  - HPA：CPU 60%，副本数 1~4
  - startup/liveness/readiness 探针
- 独立部署脚本：`scripts/deploy-user-service-k8s.sh`
  - 同步应用 `deploy/k8s/msa/user-service.yaml` 和用户域 Ingress 路由
- `develop` 发布流水线中的 user-service job：
  - Maven 编译门禁
  - 版本化多架构镜像
  - GHCR 推送
  - SSH 部署、滚动更新和健康检查

## 配置约束

部署服务器的 `.env.k8s` 或 `SERVER_ENV_BASE64` 必须包含：

```text
USER_DB_HOST=150.230.223.11
USER_DB_PORT=3306
USER_DB_NAME=lightmark
USER_DB_USER=se
USER_DB_PASSWORD=<服务器 Secret>
JWT_SECRET=<至少 32 个 UTF-8 字节>
```

脚本只创建/更新 Kubernetes Secret 和 user-service 应用资源，不会执行数据库初始化 SQL，不会应用 `database/lightmark.sql`。user-service 默认关闭 Flyway；只有目标独立 schema 已准备好并在 Secret 中设置 `USER_FLYWAY_ENABLED=true` 时才会执行该服务自己的迁移。

## 失败定位

部署脚本按 `Secret -> user-service/Ingress apply -> rollout status -> /api/health -> /api/ready -> /api/version` 顺序执行。任一步失败时自动输出 Deployment、Pod 描述和最近 120 行服务日志，便于区分镜像拉取失败、探针失败、JWT 配置缺失和远程数据库不可达。

常用现场命令：

```bash
kubectl get deployment,pod,hpa -n lightmark
kubectl describe deployment/user-service -n lightmark
kubectl logs deployment/user-service -n lightmark --tail=120
kubectl get deployment user-service -n lightmark -o jsonpath='{.spec.template.spec.containers[0].image}'
```
