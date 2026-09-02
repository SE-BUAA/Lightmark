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

## 5. 逐条测试用例

| 编号 | 测试方法 | 输入/场景 | 预期结果 | 结果 |
| --- | --- | --- | --- | --- |
| TC-01 | `FlightProductServiceTest.searchFiltersAndPaginatesFlights` | 两条航班记录，出发城市 `BJS`，`page=1,size=1` | 过滤后总数为 2，仅返回 1 条；按价格升序返回价格 100 的航班 | 通过 |
| TC-02 | `FlightProductServiceTest.priceCalendarReturnsAvailability` | 起始日期 `2026-09-01`，查询 2 天；存在 9 月 1 日航班 | 返回 2 天日历；9 月 1 日可用，无航班日期不可用 | 通过 |
| TC-03 | `FlightProductServiceTest.deductsStockAtomically` | 商品 ID `1` 扣减数量 2 | 执行带库存条件的原子更新并返回 `true` | 通过 |
| TC-04 | `HotelProductServiceTest.mapsHotelSearchAndRooms` | 酒店 ID `2`、名称“上海酒店”、最低价 300、可退款 | 正确映射酒店名称、价格和取消政策 | 通过 |
| TC-05 | `TrainProductServiceTest.returnsOptionsAndRejectsMissingStations` | 查询缺少出发站和到达站；访问站点选项 | 选项包含 `stations`；缺少站点时搜索返回空列表 | 通过 |
| TC-06 | `VacationProductServiceTest.filtersByDestination` | 产品目的地为“三亚” | 查询“三亚”返回 1 条；查询“北京”返回空列表 | 通过 |
| TC-07 | `InternalProductControllerTest.mapsNegativeDeltaToStockDeduction` | 内部请求 `id=42, delta=-2` | 转换为 `adjustInventory(42,2,true)` | 通过 |
| TC-08 | `InternalProductControllerTest.mapsPositiveDeltaToStockRelease` | 内部请求 `id=42, delta=3` | 转换为 `adjustInventory(42,3,false)` | 通过 |
| TC-09 | `ProductControllerIntegrationTest.flightSearchAndDetailExposeCommonResponse` | GET 航班搜索和详情接口 | 返回统一响应、分页列表和详情；查询参数正确传递 | 通过 |
| TC-10 | `ProductControllerIntegrationTest.genericAndAdminProductEndpointsDelegate` | 通用产品列表/详情；管理员列表、新增、改价、删除 | 所有路由成功；改价调用参数正确 | 通过 |
| TC-11 | `ProductControllerIntegrationTest.hotelRoomAndVacationEndpointsSerializeDomainRecords` | 酒店列表、房型日期查询、度假列表 | DTO 字段正确序列化，日期参数正确绑定 | 通过 |
| TC-12 | `ProductControllerIntegrationTest.trainEndpointsAcceptJsonAndExposeOptions` | 火车搜索 JSON、站点选项、价格日历 | 请求体正确绑定，返回车次、站点和日历数据 | 通过 |
| TC-13 | `ProductControllerIntegrationTest.browsingAndStockInternalEndpointsUseExpectedPayloads` | 带 JWT 调用库存扣减/释放、浏览记录写入/查询 | 鉴权成功，库存 delta 映射正确，浏览记录参数完整 | 通过 |
| TC-14 | `ProductControllerIntegrationTest.invalidInternalStockPayloadReturnsBadRequest` | 商品 ID 为 `not-a-number` | 返回 HTTP 400 和错误码 400，不调用库存服务 | 通过 |

## 6. 测试覆盖范围

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

## 7. 测试数据与隔离

- Service 测试使用 Mockito 模拟 `JdbcTemplate`、`RestClient` 和外部数据。
- Controller 集成测试使用 `@WebMvcTest` + MockMvc，不启动真实数据库连接。
- 产品服务内部接口使用长度不少于 32 字节的测试 JWT 密钥。
- 每个测试不依赖执行顺序，不写入 `database/lightmark.sql`。

## 8. 测试产物

Maven Surefire 报告目录：

```text
msa/product-service/target/surefire-reports
```

主要测试代码：

- `msa/product-service/src/test/java/top/ortus/lightmark/product/service/`
- `msa/product-service/src/test/java/top/ortus/lightmark/product/controller/InternalProductControllerTest.java`
- `msa/product-service/src/test/java/top/ortus/lightmark/product/controller/ProductControllerIntegrationTest.java`

## 9. 尚未覆盖的现场验收项

以下内容需要在部署环境或真实依赖服务中补充验证，不能由本地 MockMvc 测试替代：

1. 产品服务连接实际 `lightmark_product` MySQL 并执行 Flyway 迁移。
2. 订单服务携带服务 JWT 调用产品详情、扣库存和释放库存的真实 HTTP 链路。
3. 真实数据库并发下的库存扣减、库存不足和事务回滚。
4. 火车票 MCP 服务可用时的直达、中转和价格日历数据。
5. Kubernetes 中产品服务的健康检查、Secret 注入和服务发现。

## 10. 相关提交

- `328844b`：订单库存接口适配
- `f7d6a35`：产品服务 API 集成测试
- `97e9344`：订单调用产品服务增加 JWT
- `a049ed4`：最新 MSA 部署配置合并

## 11. 结论

`product-service` 当前自动化测试全部通过，接口层和订单库存适配契约已验证。服务具备进入真实数据库和跨服务联调阶段的条件；部署验收和真实并发库存测试仍需在目标环境执行。
