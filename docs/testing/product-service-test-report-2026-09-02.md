# product-service 测试报告

测试日期：2026-09-02  
分工角色：B（产品服务）  
当前代码版本：`a049ed4`

## 1. 测试结论

产品服务自动化测试全部通过，可进入真实数据库和跨服务联调阶段。

| 指标 | 结果 |
| --- | ---: |
| product-service 测试总数 | 14 |
| 通过 | 14 |
| 失败 | 0 |
| 跳过 | 0 |
| 构建结果 | `BUILD SUCCESS` |

## 2. 测试目标

验证 `product-service` 在微服务拆分后的接口路由、参数绑定、响应结构、产品查询逻辑、库存适配和 JWT 内部接口保护是否符合订单系统调用契约。

本轮测试不连接真实 MySQL，不修改产品数据库，也不依赖火车票 MCP 服务；数据库访问和外部服务均通过 Mock 隔离。

## 3. 测试环境

| 项目 | 配置 |
| --- | --- |
| JDK | 17 |
| Spring Boot | 3.5.14 |
| 构建工具 | Maven |
| 测试框架 | JUnit 5、Spring MockMvc、Mockito、AssertJ |
| 测试日期 | 2026-09-02 |
| 测试模块 | `lightmark-common`、`product-service` |

## 4. 执行方式与结果

在仓库根目录执行：

```powershell
cd D:\Code\Lightmark\msa
mvn -pl product-service -am test
```

结果：`BUILD SUCCESS`。

| 测试类 | 类型 | 用例数 | 通过 | 失败 | 跳过 |
| --- | --- | ---: | ---: | ---: | ---: |
| `FlightProductServiceTest` | Service 单元测试 | 3 | 3 | 0 | 0 |
| `HotelProductServiceTest` | Service 单元测试 | 1 | 1 | 0 | 0 |
| `TrainProductServiceTest` | Service 单元测试 | 1 | 1 | 0 | 0 |
| `VacationProductServiceTest` | Service 单元测试 | 1 | 1 | 0 | 0 |
| `InternalProductControllerTest` | Controller 单元测试 | 2 | 2 | 0 | 0 |
| `ProductControllerIntegrationTest` | MockMvc API 集成测试 | 6 | 6 | 0 | 0 |
| **product-service 合计** |  | **14** | **14** | **0** | **0** |

Maven reactor 同时执行了依赖模块 `lightmark-common` 的 4 个测试，结果为 4/4 通过。

## 5. 测试覆盖范围

### 4.1 航班产品

- 航班搜索：出发城市筛选、价格排序、分页。
- 航班详情查询。
- 价格日历可用性和最低价格响应。
- 库存原子扣减 SQL 条件（库存不足时不应成功）。

### 4.2 酒店与房型

- 酒店列表查询及分页响应。
- 房型查询参数 `checkIn`、`checkOut` 绑定。
- 房型价格、入住晚数和总价字段 JSON 序列化。

### 4.3 火车票

- JSON 请求体火车票搜索。
- 站点选项接口。
- 价格日历接口。
- 缺少站点参数时的服务层降级为空结果。

### 4.4 度假产品

- 目的地筛选。
- 度假产品列表及详情 DTO 响应。

### 4.5 通用产品与管理员接口

- `/api/products` 产品列表。
- `/api/products/{id}` 产品详情。
- `/api/admin/products` 列表和新增。
- 产品价格修改、删除。

### 4.6 浏览记录与订单库存适配

- 浏览记录写入和分页查询。
- 内部库存接口 `delta=-N` 映射为扣减库存。
- 内部库存接口 `delta=+N` 映射为释放库存。
- 非法产品 ID 返回 `400`。

### 4.7 JWT 内部接口保护

集成测试加载 `JwtConfig` 和 `ProductServiceSecurityConfig`，使用测试密钥生成 Bearer JWT，验证内部库存接口在鉴权上下文下可正常访问。测试密钥仅用于测试，不写入生产配置。

## 6. 测试数据与隔离

- Service 测试使用 Mockito 模拟 `JdbcTemplate`、`RestClient` 和外部数据。
- Controller 集成测试使用 `@WebMvcTest` + MockMvc，不启动真实数据库连接。
- 产品服务内部接口使用长度不少于 32 字节的测试 JWT 密钥。
- 每个测试不依赖执行顺序，不写入 `database/lightmark.sql`。

## 7. 测试产物

Maven Surefire 报告目录：

```text
msa/product-service/target/surefire-reports
```

主要测试代码：

- `msa/product-service/src/test/java/top/ortus/lightmark/product/service/`
- `msa/product-service/src/test/java/top/ortus/lightmark/product/controller/InternalProductControllerTest.java`
- `msa/product-service/src/test/java/top/ortus/lightmark/product/controller/ProductControllerIntegrationTest.java`

## 8. 尚未覆盖的现场验收项

以下内容需要在部署环境或真实依赖服务中补充验证，不能由本地 MockMvc 测试替代：

1. 产品服务连接实际 `lightmark_product` MySQL 并执行 Flyway 迁移。
2. 订单服务携带服务 JWT 调用产品详情、扣库存和释放库存的真实 HTTP 链路。
3. 真实数据库并发下的库存扣减、库存不足和事务回滚。
4. 火车票 MCP 服务可用时的直达、中转和价格日历数据。
5. Kubernetes 中产品服务的健康检查、Secret 注入和服务发现。

## 9. 相关提交

- `328844b`：订单库存接口适配
- `f7d6a35`：产品服务 API 集成测试
- `97e9344`：订单调用产品服务增加 JWT
- `a049ed4`：最新 MSA 部署配置合并

## 10. 结论

`product-service` 当前自动化测试全部通过，接口层和订单库存适配契约已验证。服务具备进入真实数据库和跨服务联调阶段的条件；部署验收和真实并发库存测试仍需在目标环境执行。
