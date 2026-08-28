# API 集成测试报告

## 1. 测试概览

- 测试目标：完成度假、酒店、火车、机票、认证与权限、AI 六个模块的集成/API 测试落地，并验证其可进入 CI 的自动化执行链路。
- 测试时间：2026-08-28
- 测试环境：`SpringBootTest + MockMvc + H2(MODE=MYSQL)`，Java 17 目标编译，PowerShell 执行 `mvnw`
- 测试文件目录：`backend/src/test/api/top/ortus/lightmark/backend/`
- 执行命令：

```powershell
.\mvnw '-Dtest=VacationApiIntegrationTests,HotelApiIntegrationTests,TrainApiIntegrationTests,FlightSearchApiIntegrationTests,AuthPermissionIntegrationTests,AiApiIntegrationTests' test
```

- 执行结果：`BUILD SUCCESS`

## 1.1 测试文件路径

- 度假：`backend/src/test/api/top/ortus/lightmark/backend/VacationApiIntegrationTests.java`
- 酒店：`backend/src/test/api/top/ortus/lightmark/backend/HotelApiIntegrationTests.java`
- 火车：`backend/src/test/api/top/ortus/lightmark/backend/TrainApiIntegrationTests.java`
- 认证与权限：`backend/src/test/api/top/ortus/lightmark/backend/AuthPermissionIntegrationTests.java`
- AI：`backend/src/test/api/top/ortus/lightmark/backend/AiApiIntegrationTests.java`
- 机票：`backend/src/test/java/top/ortus/lightmark/backend/FlightSearchApiIntegrationTests.java`

## 2. 覆盖矩阵

| 模块 | 主流程 | 备选流程 | 异常流程 | 自动化方式 | 结果 |
| --- | --- | --- | --- | --- | --- |
| 度假 | 筛选 -> AI 详情 -> 下单 -> 支付 -> 助手 -> 退款 | 取消险、保费计算 | 下架产品、非法手机号 | JUnit + MockMvc + H2 | 通过 |
| 酒店 | 列表 -> 详情 -> 房型 -> 下单 -> 支付 -> 开票 | 完成入住后评价 | 未登录、非法日期 | JUnit + MockMvc + H2 | 通过 |
| 火车 | 站点选项 -> 下单 -> 支付 -> 退款 | 学生票折扣、待支付取消 | 非法手机号 | JUnit + MockMvc + H2 | 通过 |
| 机票 | 搜索 -> 价格日历 -> 预览 -> 下单 -> 支付 -> 退款 | 机场码搜索、直飞过滤、库存恢复 | 未登录、人数不匹配、非法支付方式 | JUnit + MockMvc + H2 | 通过 |
| 认证与权限 | 注册 -> 登录 -> 当前用户 | 登录回写 IP/时间 | 未登录访问管理员接口、普通用户越权 | JUnit + MockMvc + H2 | 通过 |
| AI | 酒店推荐、评论总结 | 无评论回退总结 | 未登录访问 AI 接口 | JUnit + MockMvc + H2 | 通过 |

## 3. 模块执行结果

### 3.1 度假产品预订 API 测试

- 用例数：4
- 结果：4 通过，0 失败
- 关键结论：
  - 多条件筛选返回正确产品
  - AI 详情文案返回有效长文本
  - 取消险、支付、助手与退款主链路可跑通
  - 订单副作用以 `orders.extra_info` 与 `product.sold_count` 校验通过

### 3.2 酒店预订 API 测试

- 用例数：4
- 结果：4 通过，0 失败
- 关键结论：
  - 酒店列表、详情、真实房型查询可跑通
  - 下单、支付、发票申请主链路通过
  - 完成入住后可评价并能查询评论
  - H2 环境下已消除 MySQL JSON 函数导致的 500 问题

### 3.3 火车票预订 API 测试

- 用例数：4
- 结果：4 通过，0 失败
- 关键结论：
  - 站点选项接口可正常返回
  - 本地火车产品已支持真实下单、学生票折扣、支付、退款
  - 取消待支付订单可恢复 `sold_count`
  - 手机号校验异常路径已覆盖

### 3.4 机票预订 API 测试

- 用例数：27
- 结果：27 通过，0 失败
- 关键结论：
  - 搜索、分页、机场码兼容、价格日历、下单预览、支付、取消、退款均通过
  - 订单与库存恢复、副作用明细、AI 机票检索与退款说明均已覆盖
  - 退款断言已改为与时间解耦的稳定校验，避免因航班日期跨期导致 CI 误报

### 3.5 认证与权限 API 测试

- 用例数：3
- 结果：3 通过，0 失败
- 关键结论：
  - 注册、登录、当前用户查询主链路通过
  - 登录后 `last_login_time` 与 `last_login_ip` 回写成功
  - 管理员接口未登录返回 401，普通用户访问返回 403

### 3.6 AI 与外部接口 API 测试

- 用例数：3
- 结果：3 通过，0 失败
- 关键结论：
  - 酒店推荐接口可在当前环境稳定返回推荐结果
  - 评论总结支持真实评论与无评论回退两条路径
  - AI 酒店接口未登录保护生效

## 4. 汇总结果

- 总用例数：45
- 通过数：45
- 失败数：0
- 阻塞数：0
- 总结论：本轮六模块集成/API 自动化测试已可在本地稳定执行，并具备纳入 CI 的基本条件

## 5. 本轮修复与调整

- 修复酒店模块 H2 兼容问题：
  - `ProductMapper` 不再依赖 `JSON_EXTRACT/JSON_CONTAINS`
  - `HotelServiceImpl` 改为 Java 层解析 `extra`
- 补齐酒店测试表与种子：
  - `room_type`
  - `invoice_application`
  - `review`
- 补齐火车测试种子：
  - 为 `product.id=3` 增加 `extra`
  - 增加 `category_tags` 以支持真实座位类型校验
- 新增三套测试：
  - `TrainApiIntegrationTests`
  - `AuthPermissionIntegrationTests`
  - `AiApiIntegrationTests`
- 稳定化已有机票退款测试：
  - 去除与当前日期强耦合的固定金额断言

## 5.1 CI/CD 报告产物

- `mvn test` 默认生成机器可读测试报告：
  - JUnit XML：`backend/target/surefire-reports/TEST-*.xml`
  - 控制台文本：`backend/target/surefire-reports/*.txt`
- `mvn verify` 额外生成可直接打开的 HTML 报告：
  - HTML：`backend/target/test-report-html/surefire.html`
- CI/CD 建议归档路径：
  - 必选：`backend/target/surefire-reports/**`
  - 可选：`backend/target/test-report-html/**`
- 查看方式：
  - XML：交给 Jenkins/GitLab CI/GitHub Actions 的 JUnit 报告收集器
  - HTML：浏览器直接打开 `backend/target/test-report-html/surefire.html`

## 6. 剩余风险与说明

- 本轮未执行 `Postman/Newman` 验收集；本次交付以六套后端自动化测试为准。
- 火车 `search / transfer / calendar` 仍依赖外部 MCP 能力，本轮为保证 CI 稳定性，优先覆盖本地下单与订单流转链路。
- AI 推荐与总结在测试环境可能走真实模型返回，也可能走降级路径；当前断言已按“结构正确、结果非空”设计，适合稳定回归。
- 机票退款规则与当前时间有关，现已将用例改为稳定断言，但如果后续业务要求固定比例校验，建议补充可控时钟或固定未来测试数据。

## 7. 附件

- Maven JUnit/XML 报告目录：`backend/target/surefire-reports/`
- Maven HTML 报告目录：`backend/target/test-report-html/`
- 最终执行命令输出：`backend` 终端最近一次 `mvnw test` 结果

## 8. 结论

- 六个目标模块均已完成实际测试执行。
- 本轮交付结果为：`45/45` 通过，`0` 失败。
- 当前版本可进入 CI 的后端自动化回归阶段。
