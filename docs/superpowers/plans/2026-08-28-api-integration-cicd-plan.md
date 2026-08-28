# API 集成测试与 CI/CD 落地计划 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Lightmark 补齐一套可纳入 CI/CD 的 API 集成测试体系，包括后端自动化测试、Postman / Newman 集合、测试看板和可归档的测试报告。

**Architecture:** 以后端 `SpringBootTest + MockMvc + H2` 作为主质量门禁，覆盖模块调用、数据库副作用和异常分支；同时新增 `Postman + Newman` 作为接口验收与报告输出层，负责流程演示、环境变量透传和 HTML / JUnit 风格报告生成。文档层统一沉淀到 `docs/testing/`，让测试看板、报告模板和覆盖矩阵成为团队共享资产。

**Tech Stack:** Java 17, Spring Boot Test, MockMvc, H2, Maven Wrapper, Postman, Newman, PowerShell, Markdown

---

### Task 1: 建立测试文档目录与基础模板

**Files:**
- Create: `docs/testing/api-test-board.md`
- Create: `docs/testing/api-test-report-template.md`
- Create: `docs/testing/coverage-matrix.md`
- Modify: `docs/测试文档.md`

- [ ] **Step 1: 写测试看板骨架文档**

```md
# API 测试看板

## 使用说明

- 每个模块必须包含：测试范围、主流程、备选流程、异常流程、验收标准、断言要求
- 断言要求必须覆盖接口响应和数据库副作用
- 主流程用于 PR 冒烟，异常流程用于主干和夜间回归

## 模板

### <业务名称> API 测试

#### 测试范围

覆盖……

#### 主流程

……

#### 备选流程

……

#### 异常流程

……

#### 验收标准（测试通过条件）

- ……

#### 断言要求

- ……
```

- [ ] **Step 2: 写测试报告模板文档**

```md
# API 测试报告模板

## 1. 测试概览
- 测试目标：
- 测试时间：
- 测试环境：
- 执行方式：

## 2. 覆盖矩阵
| 模块 | 主流程 | 备选流程 | 异常流程 | 自动化方式 | 结果 |
| --- | --- | --- | --- | --- | --- |

## 3. 模块执行结果
### 3.1 度假产品预订 API 测试
- 总用例数：
- 通过数：
- 失败数：
- 核心结论：

## 4. 风险与缺陷
- ……

## 5. 结论
- ……
```

- [ ] **Step 3: 写覆盖矩阵文档**

```md
# API 测试覆盖矩阵

| 模块 | 主流程 | 备选流程 | 异常流程 | JUnit/MockMvc | Postman/Newman |
| --- | --- | --- | --- | --- | --- |
| 认证 | 是 | 是 | 是 | 计划中 | 计划中 |
| 度假 | 是 | 是 | 是 | 计划中 | 计划中 |
| 酒店 | 是 | 是 | 是 | 计划中 | 计划中 |
| 火车 | 是 | 是 | 是 | 计划中 | 计划中 |
| 机票 | 是 | 是 | 是 | 已有基础 | 计划中 |
| AI | 是 | 是 | 是 | 计划中 | 计划中 |
```

- [ ] **Step 4: 在总测试文档中加入引用入口**

```md
## 附录：CI/CD 测试资产

- 测试看板：`docs/testing/api-test-board.md`
- 测试报告模板：`docs/testing/api-test-report-template.md`
- 覆盖矩阵：`docs/testing/coverage-matrix.md`
```

- [ ] **Step 5: 提交文档改动**

```bash
git add docs/testing/api-test-board.md docs/testing/api-test-report-template.md docs/testing/coverage-matrix.md docs/测试文档.md
git commit -m "docs: add api testing board and report templates"
```

### Task 2: 为后端集成测试建立业务场景命名规范

**Files:**
- Modify: `backend/src/test/java/top/ortus/lightmark/backend/BaseIntegrationTest.java`
- Create: `backend/src/test/java/top/ortus/lightmark/backend/integration/vacation/VacationBookingIntegrationTests.java`
- Create: `backend/src/test/java/top/ortus/lightmark/backend/integration/hotel/HotelBookingIntegrationTests.java`
- Create: `backend/src/test/java/top/ortus/lightmark/backend/integration/train/TrainTicketIntegrationTests.java`
- Create: `backend/src/test/api/top/ortus/lightmark/backend/AuthPermissionIntegrationTests.java`

- [ ] **Step 1: 在测试基类中补充常用断言辅助方法**

```java
protected String json(String content) {
    return content;
}

protected String userToken() {
    return bearerToken(2L, "普通用户", List.of("USER"));
}

protected String adminToken() {
    return bearerToken(1L, "管理员", List.of("ADMIN"));
}
```

- [ ] **Step 2: 新建度假预订集成测试类骨架**

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VacationBookingIntegrationTests extends BaseIntegrationTest {
}
```

- [ ] **Step 3: 新建酒店预订集成测试类骨架**

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HotelBookingIntegrationTests extends BaseIntegrationTest {
}
```

- [ ] **Step 4: 新建火车票集成测试类骨架**

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrainTicketIntegrationTests extends BaseIntegrationTest {
}
```

- [ ] **Step 5: 新建认证与权限集成测试类骨架**

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthPermissionIntegrationTests extends BaseIntegrationTest {
}
```

- [ ] **Step 6: 提交测试目录骨架**

```bash
git add backend/src/test/java/top/ortus/lightmark/backend/BaseIntegrationTest.java backend/src/test/java/top/ortus/lightmark/backend/integration
git commit -m "test: add integration test module skeletons"
```

### Task 3: 按 TDD 落地度假产品预订主链路测试

**Files:**
- Create: `backend/src/test/java/top/ortus/lightmark/backend/integration/vacation/VacationBookingIntegrationTests.java`
- Test: `backend/src/test/resources/application-test.yaml`

- [ ] **Step 1: 写失败的“多条件筛选”测试**

```java
@Test
void vacationSearchShouldFilterByDestinationDateDaysAndTag() throws Exception {
    mockMvc.perform(post("/api/vacations/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "destination": "三亚",
                              "travelDate": "2026-10-01",
                              "days": 5,
                              "tags": ["亲子", "海岛"]
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.list.length()").value(1));
}
```

- [ ] **Step 2: 运行单测确认失败原因正确**

Run: `.\mvnw -Dtest=VacationBookingIntegrationTests#vacationSearchShouldFilterByDestinationDateDaysAndTag test`

Expected: FAIL，原因是接口实现或测试数据尚未满足多条件筛选断言

- [ ] **Step 3: 写最小实现或补齐测试数据**

```java
// 如果接口已存在，则优先补齐测试数据初始化；不要先改业务逻辑。
// 如果接口返回字段名不同，则统一断言到当前项目真实字段。
```

- [ ] **Step 4: 再次运行确认测试通过**

Run: `.\mvnw -Dtest=VacationBookingIntegrationTests#vacationSearchShouldFilterByDestinationDateDaysAndTag test`

Expected: PASS

- [ ] **Step 5: 写失败的“详情 + AI 文案”测试**

```java
@Test
void vacationDetailShouldReturnNonEmptyAiDescription() throws Exception {
    mockMvc.perform(get("/api/vacations/1/detail-ai"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.description").isString())
            .andExpect(jsonPath("$.data.description.length()").value(org.hamcrest.Matchers.greaterThan(20)));
}
```

- [ ] **Step 6: 运行并修复直到通过**

Run: `.\mvnw -Dtest=VacationBookingIntegrationTests#vacationDetailShouldReturnNonEmptyAiDescription test`

Expected: PASS

- [ ] **Step 7: 写失败的“下单 + 取消险 + 支付”测试**

```java
@Test
void vacationOrderShouldCreateInsuranceAndMarkPaid() throws Exception {
    mockMvc.perform(post("/api/orders/vacation")
                    .header("Authorization", userToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "productId": "1",
                              "useSavedTraveler": true,
                              "buyCancellationInsurance": true
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.orderNo").isNotEmpty())
            .andExpect(jsonPath("$.data.insurancePremium").value(org.hamcrest.Matchers.greaterThan(0)));
}
```

- [ ] **Step 8: 运行并补齐最小实现**

Run: `.\mvnw -Dtest=VacationBookingIntegrationTests#vacationOrderShouldCreateInsuranceAndMarkPaid test`

Expected: FAIL -> 修复 -> PASS

- [ ] **Step 9: 写失败的“退款联动取消险”测试**

```java
@Test
void vacationRefundShouldAlsoRefundInsurance() throws Exception {
    String orderNo = "在测试中先创建并支付成功的订单号";

    mockMvc.perform(post("/api/orders/vacation/{orderNo}/refund", orderNo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.orderStatus").value("REFUNDED"))
            .andExpect(jsonPath("$.data.insuranceRefunded").value(true));
}
```

- [ ] **Step 10: 运行该测试类全量回归**

Run: `.\mvnw -Dtest=VacationBookingIntegrationTests test`

Expected: PASS

- [ ] **Step 11: 提交度假主链路测试**

```bash
git add backend/src/test/java/top/ortus/lightmark/backend/integration/vacation/VacationBookingIntegrationTests.java
git commit -m "test: add vacation booking integration scenarios"
```

### Task 4: 按 TDD 落地度假产品预订异常流程测试

**Files:**
- Modify: `backend/src/test/java/top/ortus/lightmark/backend/integration/vacation/VacationBookingIntegrationTests.java`

- [ ] **Step 1: 写失败的“产品已下架返回 410”测试**

```java
@Test
void vacationOrderShouldReturnGoneWhenProductOffline() throws Exception {
    mockMvc.perform(post("/api/orders/vacation")
                    .header("Authorization", userToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "productId": "999",
                              "buyCancellationInsurance": false
                            }
                            """))
            .andExpect(status().isGone());
}
```

- [ ] **Step 2: 运行并确认失败原因**

Run: `.\mvnw -Dtest=VacationBookingIntegrationTests#vacationOrderShouldReturnGoneWhenProductOffline test`

Expected: FAIL，当前实现尚未返回 410 或测试数据未标记下架

- [ ] **Step 3: 以最小改动修正状态码和返回体**

```java
// 优先通过业务异常映射或测试数据修正，让下架场景统一返回 410 Gone。
```

- [ ] **Step 4: 写失败的“日期不可用返回 400”测试**

```java
@Test
void vacationOrderShouldRejectUnavailableDate() throws Exception {
    mockMvc.perform(post("/api/orders/vacation")
                    .header("Authorization", userToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "productId": "1",
                              "travelDate": "2025-01-01"
                            }
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
}
```

- [ ] **Step 5: 运行该测试类确认全绿**

Run: `.\mvnw -Dtest=VacationBookingIntegrationTests test`

Expected: PASS

- [ ] **Step 6: 提交异常路径测试**

```bash
git add backend/src/test/java/top/ortus/lightmark/backend/integration/vacation/VacationBookingIntegrationTests.java
git commit -m "test: add vacation booking error scenarios"
```

### Task 5: 创建 Postman / Newman 冒烟集合

**Files:**
- Create: `tests/postman/collections/vacation.collection.json`
- Create: `tests/postman/collections/smoke.collection.json`
- Create: `tests/postman/environments/local.json`
- Create: `tests/postman/environments/ci.json`

- [ ] **Step 1: 创建本地环境变量文件**

```json
{
  "id": "lightmark-local",
  "name": "Lightmark Local",
  "values": [
    { "key": "baseUrl", "value": "http://localhost:8080", "type": "default", "enabled": true },
    { "key": "token", "value": "", "type": "default", "enabled": true }
  ]
}
```

- [ ] **Step 2: 创建登录请求并提取 token**

```js
pm.test("login status is 200", function () {
  pm.response.to.have.status(200);
});

const json = pm.response.json();
pm.environment.set("token", json.data.token);
```

- [ ] **Step 3: 创建度假筛选请求断言**

```js
pm.test("vacation search returns data", function () {
  const json = pm.response.json();
  pm.expect(json.code).to.eql(0);
  pm.expect(json.data.list.length).to.be.above(0);
});
```

- [ ] **Step 4: 创建度假详情 AI 断言**

```js
pm.test("ai description should be long enough", function () {
  const json = pm.response.json();
  pm.expect(json.data.description).to.be.a("string");
  pm.expect(json.data.description.length).to.be.above(20);
});
```

- [ ] **Step 5: 创建冒烟集合引用关键请求**

```json
{
  "info": {
    "name": "Lightmark Smoke",
    "_postman_id": "replace-with-generated-id",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    { "name": "Login" },
    { "name": "Vacation Search" },
    { "name": "Vacation Detail AI" }
  ]
}
```

- [ ] **Step 6: 运行 Newman 冒烟集合**

Run: `npx newman run tests/postman/collections/smoke.collection.json -e tests/postman/environments/local.json --reporters cli,junit --reporter-junit-export tests/postman/reports/newman-smoke.xml`

Expected: PASS，并输出 `tests/postman/reports/newman-smoke.xml`

- [ ] **Step 7: 提交 Postman / Newman 资产**

```bash
git add tests/postman/collections tests/postman/environments
git commit -m "test: add newman smoke collections"
```

### Task 6: 将自动化测试接入本地与 CI 执行脚本

**Files:**
- Create: `tests/postman/scripts/run-smoke.ps1`
- Create: `tests/postman/scripts/run-full.ps1`
- Modify: `README.md`

- [ ] **Step 1: 写本地冒烟脚本**

```powershell
Set-Location $PSScriptRoot\..\..\..
Set-Location backend
.\mvnw -Dtest=AuthFlowIntegrationTests,VacationBookingIntegrationTests test
Set-Location ..\
npx newman run tests/postman/collections/smoke.collection.json `
  -e tests/postman/environments/local.json `
  --reporters cli,junit `
  --reporter-junit-export tests/postman/reports/newman-smoke.xml
```

- [ ] **Step 2: 写全量执行脚本**

```powershell
Set-Location $PSScriptRoot\..\..\..
Set-Location backend
.\mvnw test
Set-Location ..\
npx newman run tests/postman/collections/smoke.collection.json `
  -e tests/postman/environments/ci.json `
  --reporters cli,junit,htmlextra `
  --reporter-junit-export tests/postman/reports/newman-full.xml `
  --reporter-htmlextra-export tests/postman/reports/newman-full.html
```

- [ ] **Step 3: 在 README 增加执行说明**

```md
## API 集成测试

### 本地冒烟

```powershell
powershell -ExecutionPolicy Bypass -File tests/postman/scripts/run-smoke.ps1
```

### 全量回归

```powershell
powershell -ExecutionPolicy Bypass -File tests/postman/scripts/run-full.ps1
```
```

- [ ] **Step 4: 验证脚本可执行**

Run: `powershell -ExecutionPolicy Bypass -File tests/postman/scripts/run-smoke.ps1`

Expected: Maven 测试通过，Newman 报告生成

- [ ] **Step 5: 提交执行脚本**

```bash
git add tests/postman/scripts README.md
git commit -m "build: add api test execution scripts"
```

### Task 7: 生成可交付测试报告

**Files:**
- Modify: `docs/testing/api-test-report-template.md`
- Create: `docs/testing/api-test-report-2026-xx-xx.md`

- [ ] **Step 1: 从自动化结果汇总统计信息**

```text
- 统计 Maven 测试通过数、失败数
- 统计 Newman 通过数、失败数
- 汇总失败模块、失败接口、失败原因
```

- [ ] **Step 2: 生成一次实际测试报告**

```md
## 4. 执行结果

- 总用例数：32
- 通过数：30
- 失败数：2
- 阻塞数：0

### 失败摘要

1. 度假订单下架场景未返回 410
2. 酒店重复支付仍重复加积分
```

- [ ] **Step 3: 归档报告附件路径**

```md
### 附件

- Maven 测试日志：`backend/target/surefire-reports/`
- Newman JUnit 报告：`tests/postman/reports/newman-full.xml`
- Newman HTML 报告：`tests/postman/reports/newman-full.html`
```

- [ ] **Step 4: 提交测试报告**

```bash
git add docs/testing/api-test-report-template.md docs/testing/api-test-report-2026-xx-xx.md
git commit -m "docs: add api integration test report"
```
