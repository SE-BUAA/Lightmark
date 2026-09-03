[toc]

# product-service 集成测试报告

## 一、测试执行信息

测试日期：2026-09-02。代码版本：`a049ed4`。环境：Java 17、Spring Boot 3.5.14、JUnit 5、MockMvc、Mockito、AssertJ、Maven。

执行命令：`cd D:\Code\Lightmark\msa; mvn -pl product-service -am test`

结果：`BUILD SUCCESS`。共 14 个用例，14 个通过，0 个失败，0 个跳过，0 个阻塞。

## 二、测试用例明细

每个用例均按图片示例使用独立两列表格。

### P000
| 测试名称 | 航班搜索过滤与分页 |
| --- | --- |
| 测试编号 | `P000` |
| 测试函数 | `FlightProductServiceTest.searchFiltersAndPaginatesFlights` |
| 输入数据 | 两条 `BJS` 航班；第 1 页、每页 1 条。 |
| 前置条件 | Mockito 航班查询返回两条匹配记录。 |
| 预期输出 | 总数 2，当前页 1 条，按价格升序返回价格 100 的航班。 |
| 实际输出 | 总数 2、返回 1 条、首条价格 100，测试通过。 |

### P010
| 测试名称 | 航班价格日历可用性 |
| --- | --- |
| 测试编号 | `P010` |
| 测试函数 | `FlightProductServiceTest.priceCalendarReturnsAvailability` |
| 输入数据 | 起始日期 `2026-09-01`，查询 2 天；仅 9 月 1 日有航班。 |
| 前置条件 | 日期范围计算使用固定 Mock 数据。 |
| 预期输出 | 返回 2 天；有航班日期可用，无航班日期不可用。 |
| 实际输出 | 日期数量和可用性标志正确，测试通过。 |

### P020
| 测试名称 | 航班库存原子扣减 |
| --- | --- |
| 测试编号 | `P020` |
| 测试函数 | `FlightProductServiceTest.deductsStockAtomically` |
| 输入数据 | 商品 ID `1`，扣减数量 `2`。 |
| 前置条件 | Mock `JdbcTemplate` 返回原子更新成功行数。 |
| 预期输出 | 调用 `adjustInventory(1,2,true)` 并返回 `true`。 |
| 实际输出 | 参数、扣减方向和返回值均正确，测试通过。 |

### P030
| 测试名称 | 酒店搜索与房型字段映射 |
| --- | --- |
| 测试编号 | `P030` |
| 测试函数 | `HotelProductServiceTest.mapsHotelSearchAndRooms` |
| 输入数据 | 酒店 `2`，名称“上海酒店”，最低价 `300`，可退款。 |
| 前置条件 | Mock 返回完整酒店扩展信息和房型记录。 |
| 预期输出 | ID、名称、价格、取消政策正确映射到 DTO。 |
| 实际输出 | 所有字段与领域记录一致，测试通过。 |

### P040
| 测试名称 | 火车站点选项与缺少站点参数 |
| --- | --- |
| 测试编号 | `P040` |
| 测试函数 | `TrainProductServiceTest.returnsOptionsAndRejectsMissingStations` |
| 输入数据 | 站点选项请求；缺少出发站和到达站的搜索请求。 |
| 前置条件 | Mock 可返回站点选项。 |
| 预期输出 | 响应包含 `stations`；缺参搜索不抛异常并返回空列表。 |
| 实际输出 | 选项结构正确，缺参返回空列表，测试通过。 |

### P050
| 测试名称 | 度假产品目的地筛选 |
| --- | --- |
| 测试编号 | `P050` |
| 测试函数 | `VacationProductServiceTest.filtersByDestination` |
| 输入数据 | 产品目的地“三亚”；查询“三亚”和“北京”。 |
| 前置条件 | Mock 返回 1 条三亚产品。 |
| 预期输出 | 三亚返回 1 条，北京返回空列表。 |
| 实际输出 | 两种条件结果均符合预期，测试通过。 |

### P060
| 测试名称 | 内部库存负增量映射为扣减 |
| --- | --- |
| 测试编号 | `P060` |
| 测试函数 | `InternalProductControllerTest.mapsNegativeDeltaToStockDeduction` |
| 输入数据 | 有效 JWT；`id=42`、`delta=-2`。 |
| 前置条件 | JWT 密钥不少于 32 字节，库存服务为 Mock。 |
| 预期输出 | 请求成功并调用 `adjustInventory(42,2,true)`。 |
| 实际输出 | 鉴权成功，调用参数正确，测试通过。 |

### P070
| 测试名称 | 内部库存正增量映射为释放 |
| --- | --- |
| 测试编号 | `P070` |
| 测试函数 | `InternalProductControllerTest.mapsPositiveDeltaToStockRelease` |
| 输入数据 | 有效 JWT；`id=42`、`delta=3`。 |
| 前置条件 | 内部接口鉴权通过，库存服务为 Mock。 |
| 预期输出 | 调用 `adjustInventory(42,3,false)`。 |
| 实际输出 | 正确映射为释放库存，测试通过。 |

### P080
| 测试名称 | 航班搜索与详情 API 统一响应 |
| --- | --- |
| 测试编号 | `P080` |
| 测试函数 | `ProductControllerIntegrationTest.flightSearchAndDetailExposeCommonResponse` |
| 输入数据 | MockMvc GET 搜索和详情请求，包含城市、日期、分页和详情 ID。 |
| 前置条件 | `@WebMvcTest` 加载控制器，服务层为 Mock。 |
| 预期输出 | HTTP 200、统一成功响应、分页完整、详情 ID 与路径一致。 |
| 实际输出 | 状态、响应结构、列表、详情和服务参数均正确，测试通过。 |

### P090
| 测试名称 | 通用产品与管理员产品接口 |
| --- | --- |
| 测试编号 | `P090` |
| 测试函数 | `ProductControllerIntegrationTest.genericAndAdminProductEndpointsDelegate` |
| 输入数据 | 通用列表/详情；管理员列表、新增、改价、删除请求。 |
| 前置条件 | 管理员安全上下文和产品服务 Mock 已配置。 |
| 预期输出 | 路由全部成功，改价 ID 和价格准确传递。 |
| 实际输出 | 所有路由委托正确，改价参数无变形，测试通过。 |

### P100
| 测试名称 | 酒店房型与度假 API 序列化 |
| --- | --- |
| 测试编号 | `P100` |
| 测试函数 | `ProductControllerIntegrationTest.hotelRoomAndVacationEndpointsSerializeDomainRecords` |
| 输入数据 | 酒店列表、酒店 `2` 房型查询（`checkIn`、`checkOut`）、度假列表。 |
| 前置条件 | Mock 返回酒店、房型和度假记录。 |
| 预期输出 | 三个接口成功，日期正确绑定，DTO 字段完整。 |
| 实际输出 | 三个接口均返回预期响应，测试通过。 |

### P110
| 测试名称 | 火车 JSON 搜索、站点选项与价格日历 API |
| --- | --- |
| 测试编号 | `P110` |
| 测试函数 | `ProductControllerIntegrationTest.trainEndpointsAcceptJsonAndExposeOptions` |
| 输入数据 | 火车搜索 JSON、站点选项请求、价格日历请求。 |
| 前置条件 | Mock 火车服务配置三类返回值。 |
| 预期输出 | JSON 正确绑定；三类接口 HTTP 200、成功 code、结构完整。 |
| 实际输出 | 请求解析和三类响应均正确，测试通过。 |

### P120
| 测试名称 | 浏览记录与库存内部接口负载 |
| --- | --- |
| 测试编号 | `P120` |
| 测试函数 | `ProductControllerIntegrationTest.browsingAndStockInternalEndpointsUseExpectedPayloads` |
| 输入数据 | Bearer JWT；浏览记录写入/查询；库存负、正增量请求。 |
| 前置条件 | JWT 签名有效，相关服务均为 Mock。 |
| 预期输出 | 鉴权成功，浏览记录字段完整，库存方向分别为扣减和释放。 |
| 实际输出 | 鉴权、参数校验和委托调用均正确，测试通过。 |

### P121
| 测试名称 | 非法内部库存负载返回 400 |
| --- | --- |
| 测试编号 | `P121` |
| 测试函数 | `ProductControllerIntegrationTest.invalidInternalStockPayloadReturnsBadRequest` |
| 输入数据 | 商品 ID 为 `not-a-number` 的内部库存请求。 |
| 前置条件 | 请求到达控制器，ID 类型校验在控制器层执行。 |
| 预期输出 | HTTP 400、错误码 400，库存服务不被调用。 |
| 实际输出 | 返回 HTTP 400、错误码 400，库存服务调用次数为 0，测试通过。 |

## 三、测试隔离与限制

Service 测试使用 Mockito 模拟 `JdbcTemplate`、`RestClient` 和外部数据，不连接真实 MySQL；Controller 测试使用 `@WebMvcTest + MockMvc`。真实数据库迁移、跨服务网络和并发库存压力仍需在 MSA 容器或目标环境验收。

## 四、自动化测试产物

JUnit XML：`msa/product-service/target/surefire-reports/TEST-*.xml`。Maven 文本报告：`msa/product-service/target/surefire-reports/*.txt`。

## 五、最终结论

product-service 本轮 14 个测试全部通过，构建成功。航班、酒店/房型、火车、度假、通用产品、管理员、浏览记录、库存适配及 JWT 内部接口契约均已验证。
