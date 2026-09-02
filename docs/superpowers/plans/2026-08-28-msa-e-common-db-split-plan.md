# E 基础设施 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 Lightmark 微服务阶段的最小可开发基础设施，包括 `msa/` 多模块工程、`lightmark-common`、4 个服务骨架、Flyway 迁移归位和数据库拆分导出脚本。

**Architecture:** 以 `msa/pom.xml` 作为父 POM 管理依赖版本，各服务只依赖 `lightmark-common`。数据库按 `user/product/order/content` 四个 schema 拆分，微服务迁移文件独立存放在各服务目录中，单体保留为性能对比与迁移数据基线。

**Tech Stack:** Maven 多模块、Spring Boot 3.5、Java 17、Flyway、MySQL、JUnit 5。

---

### Task 1: 建立文档与分支基线

**Files:**
- Create: `docs/superpowers/specs/2026-08-28-msa-e-common-db-split-design.md`
- Create: `docs/superpowers/plans/2026-08-28-msa-e-common-db-split-plan.md`

- [ ] **Step 1: 创建合规分支**

Run: `git switch -c msa-develop`（若不存在）  
Expected: 当前分支切到 `msa-develop`

- [ ] **Step 2: 创建功能分支**

Run: `git switch -c test/e-common-db-split`  
Expected: 当前分支切到 `test/e-common-db-split`

- [ ] **Step 3: 写入设计文档与计划**

Expected: `docs/superpowers/specs/2026-08-28-msa-e-common-db-split-design.md` 和 `docs/superpowers/plans/2026-08-28-msa-e-common-db-split-plan.md` 存在且内容与 E 部分基础任务一致

### Task 2: 先写 common 模块测试

**Files:**
- Create: `msa/lightmark-common/src/test/java/top/ortus/lightmark/common/ApiResponseTest.java`
- Create: `msa/lightmark-common/src/test/java/top/ortus/lightmark/common/JwtTokenServiceTest.java`

- [ ] **Step 1: 写 `ApiResponse` 失败测试**

```java
package top.ortus.lightmark.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiResponseTest {

    @Test
    void okShouldCreateSuccessPayload() {
        ApiResponse<String> response = ApiResponse.ok("ok");
        assertEquals(0, response.getCode());
        assertEquals("success", response.getMsg());
        assertEquals("ok", response.getData());
    }

    @Test
    void errorShouldCreateErrorPayload() {
        ApiResponse<Void> response = ApiResponse.error(400, "bad request");
        assertEquals(400, response.getCode());
        assertEquals("bad request", response.getMsg());
        assertNull(response.getData());
    }
}
```

- [ ] **Step 2: 写 `JwtTokenService` 失败测试**

```java
package top.ortus.lightmark.common;

import org.junit.jupiter.api.Test;
import top.ortus.lightmark.common.security.JwtTokenService;
import top.ortus.lightmark.common.security.UserIdentity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtTokenServiceTest {

    @Test
    void createAndResolveTokenShouldRoundTrip() {
        JwtTokenService service = new JwtTokenService(
                "lightmark-secret-key-please-change-123456",
                "lightmark",
                120
        );

        String token = service.createToken(2L, "普通用户", List.of("USER"));

        assertEquals(2L, service.resolveUserId(token));
        assertEquals(List.of("USER"), service.resolveRoles(token));
        assertEquals(UserIdentity.USER, service.resolveIdentity(token));
    }
}
```

- [ ] **Step 3: 运行测试确认先失败**

Run: `mvn -f msa/pom.xml -pl lightmark-common test`  
Expected: FAIL，因为 `msa` 和 `lightmark-common` 还不存在

### Task 3: 搭建 `msa` 父工程和 `lightmark-common`

**Files:**
- Create: `msa/pom.xml`
- Create: `msa/lightmark-common/pom.xml`
- Create: `msa/lightmark-common/src/main/java/top/ortus/lightmark/common/ApiResponse.java`
- Create: `msa/lightmark-common/src/main/java/top/ortus/lightmark/common/PageResponse.java`
- Create: `msa/lightmark-common/src/main/java/top/ortus/lightmark/common/PageResult.java`
- Create: `msa/lightmark-common/src/main/java/top/ortus/lightmark/common/exception/ApiException.java`
- Create: `msa/lightmark-common/src/main/java/top/ortus/lightmark/common/exception/GlobalExceptionHandler.java`
- Create: `msa/lightmark-common/src/main/java/top/ortus/lightmark/common/security/JwtTokenService.java`
- Create: `msa/lightmark-common/src/main/java/top/ortus/lightmark/common/security/UserIdentity.java`

- [ ] **Step 1: 创建父 POM**

Expected: `msa/pom.xml` 定义模块 `lightmark-common`、`user-service`、`product-service`、`order-service`、`content-service`，统一 Spring Boot 版本和 Java 17

- [ ] **Step 2: 实现 `lightmark-common` 最小抽取集**

Expected: 复制并调整包名后的公共类在 `lightmark-common` 下可编译，通过 `ApiResponseTest` 与 `JwtTokenServiceTest`

- [ ] **Step 3: 运行 common 模块测试**

Run: `mvn -f msa/pom.xml -pl lightmark-common test`  
Expected: PASS

### Task 4: 创建 4 个服务骨架

**Files:**
- Create: `msa/user-service/pom.xml`
- Create: `msa/user-service/src/main/java/top/ortus/lightmark/user/UserServiceApplication.java`
- Create: `msa/user-service/src/main/java/top/ortus/lightmark/user/controller/HealthController.java`
- Create: `msa/user-service/src/main/resources/application.yaml`
- Create: `msa/product-service/pom.xml`
- Create: `msa/product-service/src/main/java/top/ortus/lightmark/product/ProductServiceApplication.java`
- Create: `msa/product-service/src/main/java/top/ortus/lightmark/product/controller/HealthController.java`
- Create: `msa/product-service/src/main/resources/application.yaml`
- Create: `msa/order-service/pom.xml`
- Create: `msa/order-service/src/main/java/top/ortus/lightmark/order/OrderServiceApplication.java`
- Create: `msa/order-service/src/main/java/top/ortus/lightmark/order/controller/HealthController.java`
- Create: `msa/order-service/src/main/resources/application.yaml`
- Create: `msa/content-service/pom.xml`
- Create: `msa/content-service/src/main/java/top/ortus/lightmark/content/ContentServiceApplication.java`
- Create: `msa/content-service/src/main/java/top/ortus/lightmark/content/controller/HealthController.java`
- Create: `msa/content-service/src/main/resources/application.yaml`

- [ ] **Step 1: 为四个服务创建独立 POM**

Expected: 每个服务只依赖 `lightmark-common` 和本服务需要的 Spring Boot/Flyway 组件

- [ ] **Step 2: 添加启动类与健康检查**

Expected: 每个服务都包含启动类和 `/api/health` 接口

- [ ] **Step 3: 配置独立 datasource 与 Flyway 路径**

Expected: `application.yaml` 指向各自 schema，Flyway 路径分别为 `classpath:db/migration/user|product|order|content`

- [ ] **Step 4: 编译骨架**

Run: `mvn -f msa/pom.xml test`  
Expected: PASS，至少完成父工程解析与所有模块编译

### Task 5: 归位 Flyway 迁移

**Files:**
- Create: `msa/user-service/src/main/resources/db/migration/user/*.sql`
- Create: `msa/product-service/src/main/resources/db/migration/product/*.sql`
- Create: `msa/order-service/src/main/resources/db/migration/order/*.sql`
- Create: `msa/content-service/src/main/resources/db/migration/content/*.sql`

- [ ] **Step 1: 归位 `user-service` 迁移**

Expected: `auth_verification_code` 等 user 域迁移落在 `user` 目录

- [ ] **Step 2: 归位 `product-service` 迁移**

Expected: `product`、`room_type`、酒店/火车/度假种子相关迁移落在 `product` 目录

- [ ] **Step 3: 归位 `order-service` 迁移**

Expected: `orders`、`invoice_application`、`review`、`flight_order_detail` 相关迁移落在 `order` 目录

- [ ] **Step 4: 归位 `content-service` 迁移**

Expected: `travel_plan`、`post`、`post_like`、`comment`、`question` 相关迁移落在 `content` 目录

### Task 6: 补齐数据库拆分与导出脚本

**Files:**
- Create: `scripts/db/create-msa-schemas.sql`
- Create: `scripts/db/export-monolith-baseline.sh`
- Create: `scripts/db/split-mysql.sh`
- Create: `scripts/db/README.md`

- [ ] **Step 1: 创建 schema 初始化脚本**

Expected: `create-msa-schemas.sql` 可创建 `lightmark_user`、`lightmark_product`、`lightmark_order`、`lightmark_content`

- [ ] **Step 2: 创建单体基线导出脚本**

Expected: 导出完整 `lightmark` 单体 SQL 到后续性能对比可复用目录

- [ ] **Step 3: 创建按表归属拆分脚本**

Expected: `split-mysql.sh` 根据表清单把单体库导出到四个 schema，且默认不删除单体基线

- [ ] **Step 4: 写脚本说明**

Expected: `scripts/db/README.md` 写明环境变量、用法、保留基线和暂不执行性能测试的约束

### Task 7: 验证并提交

**Files:**
- Modify: `docs/微服务拆分方案.md`
- Modify: `docs/开发规范.md`
- Modify: `docs/数据库设计.md`

- [ ] **Step 1: 补充文档说明**

Expected: 说明本次 E 基础部分已完成的交付物和后续性能对比入口

- [ ] **Step 2: 运行最终验证**

Run: `mvn -f msa/pom.xml test`  
Expected: PASS

- [ ] **Step 3: 提交功能分支**

Run:

```bash
git add msa scripts/db docs/superpowers/specs/2026-08-28-msa-e-common-db-split-design.md docs/superpowers/plans/2026-08-28-msa-e-common-db-split-plan.md docs/数据库设计.md
git commit -m "feat(msa): add common module and db split foundation"
```

Expected: 提交成功，停留在 `test/e-common-db-split`
