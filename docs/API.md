**#** **API** **文档（新版路由）**

  

**## 1. 统一响应格式**

  

所有接口统一返回：

  

```JSON
{
  "code": 0,
  "msg": "success",
  "data": {}
}
```

  

- `code`: `0` 表示成功，非 `0` 表示失败。
    
- `msg`: 状态描述，成功时通常为 `success`。
    
- `data`: 业务数据，可以是对象、数组、分页结构或 `true/false`。
    

**### 分页响应格式**

  

```JSON
{
  "code": 0,
  "msg": "success",
  "data": {
    "total": 0,
    "page": 1,
    "size": 10,
    "list": []
  }
}
```

  

---

  

**## 2. 健康检查**

  

**### GET `/api/health`**

- 说明：健康检查
    
- 返回：`HealthDTO`
    

```JSON
{ "status": "UP" }
```

  

---

  

**## 3. 认证接口**

  

**### GET `/api/auth/captcha`**

- 说明：获取登录/注册所需图形验证码图片（PNG），后端会将验证码文本写入 Session。

**### POST `/api/auth/verification/email/send`**

- 说明：发送注册邮箱验证码（发送前需先通过图形验证码校验）
- 邮件发送改为 QQ 邮箱 SMTP 服务，请在后端配置中填写 QQ 邮箱账号、SMTP 授权码和发件人邮箱。
- 其中 QQ 邮箱授权码对应字段为 `lightmark.auth.mail.password`，也就是环境变量 `AUTH_MAIL_PASSWORD`。

- 请求体：

```JSON
{ "email": "user@qq.com", "captchaCode": "ABCD" }
```

- 返回：`true`

**### POST `/api/auth/register`**

- 说明：注册
    
- 请求体：
    

```JSON
{
  "email": "user@qq.com",
  "nickname": "用户名",
  "countryCode": "+86",
  "phone": "13800138000",
  "password": "Password!1",
  "verificationCode": "123456",
  "captchaCode": "ABCD"
}
```

- `email` 必填，并且注册前必须通过邮箱验证码验证
- `nickname` 必填且唯一，最长 30 字
- `phone` / `countryCode` 选填，仅作为资料保存，不参与登录
- 密码必须为 8-20 位，且包含大小写字母和特殊字符 `!#@$%^&*()_`
- 邮箱仅允许常用邮箱域名及 `.edu.cn`

- 返回：`UserDTO`
    

**### POST `/api/auth/login`**

- 说明：登录
    
- 请求体：
    

```JSON
{ "account": "邮箱或昵称", "password": "Password!1", "captchaCode": "ABCD" }
```

- 登录账号支持邮箱或昵称
- 登录使用“邮箱/昵称 + 密码 + 图形验证码”
- 手机号不参与登录

  

- 返回：
    

```JSON
{
  "token": "JWT",
  "userId": "1",
  "nickname": "张三",
  "avatar": "",
  "identity": "ADMIN",
  "roles": ["ADMIN"]
}
```

  

- `identity` 为单一身份枚举，当前仅支持：`ADMIN`、`USER`。
    
- `roles` 为兼容字段，当前只会包含一个与 `identity` 一致的元素。
    

**### POST `/api/auth/logout`**

- 说明：登出
    
- 返回：`true`
    

---

  

**## 4. 用户中心**

  

> 除登录/注册/健康检查外，建议携带 `Authorization: Bearer <token>`。

  

**### GET `/api/user/current`**

- 说明：当前登录用户信息
    
- 返回：`UserCurrentDTO`
    

```JSON
{
  "id": "1",
  "phone": "13800000000",
  "email": "admin@example.com",
  "nickname": "系统管理员",
  "avatar": "",
  "points": 0,
  "level": 3,
  "identity": "ADMIN",
  "roles": ["ADMIN"],
  "permissions": []
}
```

  

- `identity` 为单一身份枚举，当前仅支持：`ADMIN`、`USER`。
    
- `roles` 为兼容字段，值与 `identity` 保持一致。
    

**### PUT `/api/user/current`**

- 说明：更新当前用户个人信息
    
- 请求体：`UserUpdateRequest`
    
- 返回：`UserDTO`
    

**### POST `/api/user/avatar`**

- 说明：更新头像
    
- 请求体：
    

```JSON
{ "avatarUrl": "https://..." }
```

  

- 返回：
    

```JSON
{ "avatarUrl": "https://..." }
```

  

**### PUT `/api/user/password`**

- 说明：修改密码
    
- 请求体：
    

```JSON
{ "oldPassword": "旧密码", "newPassword": "新密码" }
```

  

- 返回：`true`
    

**### GET `/api/user/travelers`**

- 说明：当前用户出行人列表
    
- 返回：`List<TravelerDTO>`
    

**### POST `/api/user/travelers`**

- 说明：新增出行人
    
- 请求体：`TravelerDTO`
    
- 返回：`TravelerDTO`
    

**### PUT `/api/user/travelers/{id}`**

- 说明：修改出行人
    
- 请求体：`TravelerDTO`
    
- 返回：`TravelerDTO`
    

**### DELETE `/api/user/travelers/{id}`**

- 说明：删除出行人
    
- 返回：`true/false`
    

**### GET `/api/user/points/logs`**

- 说明：积分明细
    
- 返回：`PageResponse<PointsLogDTO>`
    

**### GET `/api/user/level/upgrade-info`**

- 说明：等级升级信息
    
- 返回：`UserLevelUpgradeInfoDTO`
    

**### GET `/api/user/orders`**

- 说明：我的订单
    
- 返回：`PageResponse<OrderDTO>`
    

**### GET `/api/user/orders/{orderNo}`**

- 说明：订单详情
    
- 返回：`OrderDTO`
    

---

  

**## 5. 公共产品与搜索接口**

  

**### GET `/api/products`**

- 说明：全局产品搜索
    
- 返回：`PageResponse<ProductDTO>`
    

**### GET `/api/products/{id}`**

- 说明：产品详情
    
- 返回：`ProductDTO`
    

**### GET `/api/flights/search`**

**### GET `/api/hotels/search`**

**### GET `/api/trains/search`**

**### GET `/api/vacations/search`**

- 说明：各产品类型搜索
    
- 返回：`PageResponse<ProductDTO>`
    

**### GET `/api/flights/price-calendar`**

- 说明：价格日历
    
- 返回：对象
    

**### GET `/api/flights/{productId}`**

**### GET `/api/hotels/{hotelId}/rooms`**

**### GET `/api/hotels/{hotelId}/reviews`**

**### GET `/api/trains/{productId}`**

**### GET `/api/vacations/{productId}`**

- 说明：详情接口
    

**### POST `/api/flights/order/preview`**

**### POST `/api/flights/order`**

**### POST `/api/hotels/order`**

**### POST `/api/trains/order`**

**### POST `/api/vacations/order`**

- 说明：下单/预览
    

---

  

**## 6. 订单与支付**

  

> 该部分当前为占位实现。

  

**### POST `/api/orders/{orderNo}/pay` `【待实现】`**

- 说明：支付
    
- 请求体：
    

```JSON
{ "paymentMethod": "WECHAT" }
```

  

**### POST `/api/orders/{orderNo}/cancel` `【待实现】`**

- 说明：取消订单
    

**### POST `/api/orders/{orderNo}/refund` `【待实现】`**

- 说明：退款
    

**### GET `/api/orders/{orderNo}/status` `【待实现】`**

- 说明：订单状态
    

**### POST `/api/payment/callback` `【待实现】`**

- 说明：支付回调
    

---

  

**## 7. 行程、社区与评价**

> 本节接口均返回统一结构：`{ "code": 0, "msg": "success", "data": ... }`。除公开列表查询外，接口需要在请求头携带 `Authorization: Bearer <token>`。

### 7.1 智能行程

**### GET `/api/itinerary/my-plans` `【已实现】`**

- 说明：分页查询当前登录用户的行程。
- Query 参数：
  - `page`：页码，默认 `1`
  - `size`：每页数量，默认 `10`
- 响应 `data` 示例：

```json
{
  "list": [
    {
      "id": "1",
      "user_id": "2",
      "title": "杭州三日慢游",
      "destination": "杭州",
      "start_date": "2026-07-01",
      "end_date": "2026-07-03",
      "plan_data": "[{\"day\":1,\"theme\":\"西湖初见\",\"items\":[\"抵达杭州\"]}]",
      "is_public": 1,
      "create_time": "2026-06-01T10:00:00"
    }
  ],
  "total": 1,
  "page": 1,
  "size": 10
}
```

**### POST `/api/itinerary/plans` `【已实现】`**

- 说明：保存手动创建或 AI 生成后的行程。
- 请求体：

```json
{
  "title": "杭州两日智能行程",
  "destination": "杭州",
  "start_date": "2026-07-01",
  "end_date": "2026-07-02",
  "plan_data": "[{\"day\":1,\"theme\":\"西湖与湖滨\",\"items\":[\"西湖步行\",\"湖滨晚餐\"],\"tips\":[\"注意防晒\"]}]",
  "is_public": 0
}
```

- 响应 `data`：`TravelPlanDTO`

**### PUT `/api/itinerary/plans/{id}` `【已实现】`**

- 说明：修改当前用户自己的行程。
- 请求体：同创建行程，可传需要更新的字段。
- 响应 `data`：更新后的 `TravelPlanDTO`

**### DELETE `/api/itinerary/plans/{id}` `【已实现】`**

- 说明：删除当前用户自己的行程。
- 响应 `data`：`true`

**### POST `/api/itinerary/ai/generate` `【已实现】`**

- 说明：根据目的地、天数、预算和偏好生成结构化行程。AI 配置缺失或调用失败时会返回规则降级行程。
- AI 调用配置：后端通过环境变量 `AI_API_URL`、`AI_API_KEY`、`AI_MODEL` 调用 DeepSeek/OpenAI 兼容的 Chat Completions 接口；例如 `AI_API_URL=https://api.deepseek.com/chat/completions`，`AI_MODEL=deepseek-v4-flash`。当 `AI_API_KEY` 为空时，后端会复用 `DEEPSEEK_API_KEY`。
- 前端约束：目的地由省/市/区县级联选择器选择后提交，不再允许任意文本直接生成。
- 请求体：

```json
{
  "destination": "杭州",
  "days": 2,
  "startDate": "2026-07-01",
  "budget": "人均3000元",
  "preferences": "慢节奏和美食"
}
```

- 响应 `data`：未入库的 `TravelPlanDTO`，可继续调用 `POST /api/itinerary/plans` 保存。

**### GET `/api/itinerary/plans/{id}/share` `【已实现】`**

- 说明：将当前用户自己的行程设为公开并生成分享链接。
- 响应 `data` 示例：

```json
{
  "shortLink": "/itinerary?sharedPlanId=1"
}
```

**### GET `/api/itinerary/plans/{id}/export` `【已实现】`**

- 说明：返回行程导出地址。
- 响应 `data` 示例：

```json
{
  "fileUrl": "/api/itinerary/plans/1/export"
}
```

### 7.2 游记社区

**### GET `/api/posts` `【已实现】`**

- 说明：分页查询已发布游记。
- Query 参数：
  - `page`：页码，默认 `1`
  - `size`：每页数量，默认 `10`
  - `keyword`：按标题或内容搜索，可选
  - `sort`：`latest` 或 `hot`，默认 `latest`
- 响应 `data`：分页对象，`list` 内为 `PostDTO`

**### GET `/api/posts/{id}` `【已实现】`**

- 说明：查询游记详情。
- 响应 `data`：`PostDTO`

**### POST `/api/posts` `【已实现】`**

- 说明：发布游记。
- 请求体：

```json
{
  "title": "杭州三日慢游路线分享",
  "content": "西湖适合留出完整半天，龙井村建议上午去。",
  "images": "[\"https://picsum.photos/id/104/640/420\"]"
}
```

- 响应 `data`：新建的 `PostDTO`

**### PUT `/api/posts/{id}` `【已实现】`**

- 说明：编辑当前用户自己的游记。
- 请求体：同发布游记，可传需要更新的字段。
- 响应 `data`：更新后的 `PostDTO`

**### DELETE `/api/posts/{id}` `【已实现】`**

- 说明：软删除游记，仅游记发布者或管理员可操作。
- 响应 `data`：`true`

**### POST `/api/posts/{id}/like` `【已实现】`**

- 说明：点赞或取消点赞游记。
- 响应 `data` 示例：

```json
{
  "liked": true,
  "likes": 12
}
```

**### GET `/api/posts/{id}/comments` `【已实现】`**

- 说明：分页查询游记评论。
- Query 参数：
  - `page`：页码，默认 `1`
  - `size`：每页数量，默认 `10`
- 响应 `data`：分页对象，`list` 内为 `CommentDTO`

**### POST `/api/posts/{id}/comments` `【已实现】`**

- 说明：发表游记评论。
- 请求体：

```json
{
  "content": "这条路线很实用，已收藏。",
  "parent_id": null
}
```

- 响应 `data`：新建的 `CommentDTO`

**### DELETE `/api/posts/{postId}/comments/{commentId}` `【已实现】`**

- 说明：软删除游记评论或回复，仅评论/回复发布者或管理员可操作。
- 响应 `data`：`true`

### 7.3 问答社区

**### GET `/api/questions` `【已实现】`**

- 说明：分页查询问答列表。
- Query 参数：
  - `page`：页码，默认 `1`
  - `size`：每页数量，默认 `10`
  - `keyword`：按标题或内容搜索，可选
- 响应 `data`：分页对象，`list` 内为 `QuestionDTO`

**### POST `/api/questions` `【已实现】`**

- 说明：发布问题。
- 请求体：

```json
{
  "title": "第一次去成都住哪里方便？",
  "content": "想吃小吃、坐地铁方便，预算中等。"
}
```

- 响应 `data`：新建的 `QuestionDTO`

**### GET `/api/questions/{id}` `【已实现】`**

- 说明：查询问题详情。
- 响应 `data`：`QuestionDTO`

**### POST `/api/questions/{id}/answer` `【已实现】`**

- 说明：回答问题。
- 权限：未回答的问题允许登录用户回答；已有回答仅回答发布者或管理员可修改。
- 请求体：

```json
{
  "answer": "可以优先看春熙路、宽窄巷子或太古里周边。"
}
```

- 响应 `data`：更新后的 `QuestionDTO`

**### DELETE `/api/questions/{id}` `【已实现】`**

- 说明：删除问题。
- 权限：仅问题发布者或管理员可删除，其余用户返回 `403`。
- 响应 `data`：`true` / `false`

**### DELETE `/api/questions/{id}/answer` `【已实现】`**

- 说明：删除问题下的回答，删除后问题状态恢复为待回答。
- 权限：仅回答发布者或管理员可删除，其余用户返回 `403`。
- 响应 `data`：`true` / `false`


**### GET `/api/reviews/orders/{orderNo}` `【待实现】`**

**### POST `/api/reviews/orders/{orderNo}` `【待实现】`**

**### GET `/api/reviews/product/{productId}` `【待实现】`**

  

---

  

**## 8. 管理后台**

  

> 需要 `ADMIN` 角色。

  

**### GET `/api/admin/dashboard/summary`**

- 说明：核心指标
    
- 返回：`DashboardSummaryDTO`
    

**### GET `/api/admin/dashboard/trends`**

- 说明：近 7 天交易趋势
    
- 返回：`DashboardTrendDTO`
    

**### GET `/api/admin/dashboard/hot-products`**

- 说明：热门产品 Top10
    
- 返回：`List<HotProductDTO>`
    

**### GET `/api/admin/users`**

- 说明：用户列表
    
- 查询参数：`keyword`、`status`
    
- 返回：`PageResponse<UserDTO>`
    

**### PUT `/api/admin/users/{id}/status`**

- 说明：封禁/解封用户
    
- 请求体：
    

```JSON
{ "status": 0 }
```

  

- 返回：`true/false`
    

**### PUT `/api/admin/users/{id}/level`**

- 说明：调整会员等级
    
- 请求体：
    

```JSON
{ "level": 2 }
```

  

- 返回：`true/false`
    

**### GET `/api/admin/products`**

- 说明：产品列表
    
- 查询参数：`productType`、`name`、`status`
    
- 返回：`PageResponse<AdminProductDTO>`
    

**### PUT `/api/admin/products/{id}/status`**

- 说明：上架/下架
    
- 请求体：
    

```JSON
{ "status": 1 }
```

  

**### PUT `/api/admin/products/{id}/price`**

- 说明：调价
    
- 请求体：
    

```JSON
{ "price": 299.00 }
```

  

**### PUT `/api/admin/products/{id}/stock`**

- 说明：调库存
    
- 请求体：
    

```JSON
{ "stock": 8 }
```

  

**### POST `/api/admin/products`**

- 说明：新增产品
    
- 请求体：
    

```JSON
{
  "productType": "HOTEL",
  "name": "测试酒店",
  "price": 199.00,
  "stock": 11,
  "soldCount": 0,
  "status": 1,
  "categoryTags": "",
  "extra": ""
}
```

  

- 返回：`ProductDTO`
    

**### DELETE `/api/admin/products/{id}`**

- 说明：逻辑删除产品（下架 + 不可见）
    
- 返回：`true/false`
    

**### GET `/api/admin/orders`**

- 说明：订单列表
    
- 查询参数：`status`
    
- 返回：`PageResponse<AdminOrderDTO>`
    

**### PUT `/api/admin/orders/{orderNo}/status`**

- 说明：强制修改订单状态
    
- 请求体：
    

```JSON
{ "status": 3, "remark": "测试取消" }
```

  

- 返回：`true/false`
    

**### POST `/api/admin/orders/{orderNo}/refund`**

- 说明：强制退款
    
- 请求体：
    

```JSON
{ "remark": "refund reason" }
```

  

- 返回：`true/false`
    

**### GET `/api/admin/logs`**

- 说明：管理员操作日志
    
- 查询参数：`admin_id`、`operation`、`result`
    
- 返回：`PageResponse<AdminLogDTO>`
    

**### POST `/api/admin/questions/{id}/answer`**

- 说明：官方回答问题
    
- 请求体：
    

```JSON
{ "answer": "官方回复内容" }
```

  

- 返回：`true/false`
    

**### PUT `/api/admin/comments/{id}/approve`**

- 说明：审核评论
    
- 请求体：
    

```JSON
{ "isApproved": 1 }
```

  

- 返回：`true/false`
    

---

  

**## 9. AI 与辅助接口（按功能分组）**

  

该部分按照功能分组，重要接口已实现为：内联/区域补全、非流式聊天、流式聊天、会话管理与函数调用支持。

  

注意：所有接口统一返回 `ApiResponse` 包装结构：{ code, msg, data }

  

**### 9.1 聊天 — 非流式**

  

POST `/api/chat` — 非流式会话（保持上下文）

- 说明：向会话发送一条消息并返回 AI 的完整回复（阻塞直到模型返回）
    
- 请求体：
    

```JSON
{ "sessionId": "string", "message": "用户消息", "systemPrompt": "可选系统提示" }
```

  

- 返回：`ApiResponse<{ content: string, model: string }>`（已实现）
    

**### 9.2 区域补全（Inline / Region completion） — 非流式**

  

POST `/api/chat/region/complete` — 区域替换/补全

- 说明：为选定文本区域生成补全或替换文本，返回仅替换后的区域文本（用于编辑器内联补全场景）
    
- 请求体：
    

```JSON
{ "sessionId": "string", "regionText": "需要替换的片段", "surroundingText": "上下文（可选）" }
```

  

- 返回：`ApiResponse<{ content: string, model: string }>`（已实现）
    

**### 9.3 聊天 — 流式（SSE）**

  

POST `/api/chat/stream` — 简易流式接口（Server-Sent Events）

- 说明：客户端发起请求后以 SSE 接收分片事件，事件类型：`partial`(部分文本)、`complete`(结束)、`error`。
    
- 请求体：
    

```JSON
{ "sessionId": "string", "message": "用户消息" }
```

  

- 事件格式（SSE）：
    

```Plain
event: partial
data: "第一段文本"

event: partial
data: "第二段文本"

event: complete
data: "__complete__"
```

  

- 注意：当前实现为模型完整输出的分片推送（已实现）。后续可替换为模型原生流接口以获得更低延迟。
    

**### 9.4 会话管理**

  

GET `/api/chat/context/{sessionId}` — 获取会话历史（待实现：分页/裁剪） `【待实现】`

  

POST `/api/chat/context/{sessionId}/reset` — 重置会话历史（已实现为基础功能） `【待实现】`

  

**### 9.5 函数调用 / 工具执行（Function Calling）**

  

说明：系统使用 `org.springframework.ai.deepseek` 的函数调用/内部工具执行能力。在模型返回函数调用意图时，后端应：

  

- 验证目标函数是否合法并经由白名单映射到安全的内部执行器；
    
- 执行函数并将结果回写到会话历史，再交给模型做最终生成（若需要）；
    
- 对外返回时将函数执行结果纳入 `data` 或通过后续消息返回。
    
- 具体参考工具类 [tools](src/main/java/top/ortus/lightmark/backend/tools)实现，注册函数并追加到yaml配置
    

该能力的基础集成已添加（模型调用与上下文管理），但完整的函数映射与执行策略需要按业务补充（`【待实现】`）。

**---**

  

**## 10. 其它公共接口**

  

> 该部分当前为占位实现。

  

**### GET `/api/search/global` `【待实现】`**

**### GET `/api/destinations/hot` `【待实现】`**

**### GET `/api/destinations/{city}/weather` `【待实现】`**

**### POST `/api/upload/image` `【已实现】`**

- 说明：社区游记图片上传接口。后端将图片上传到对象存储并返回对象名称；游记的 `images` 字段只保存对象名称，展示时由前端拼接固定对象存储前缀。
- 请求头：`Authorization: Bearer <token>`
- Content-Type：`multipart/form-data`
- 表单字段：
  - `file`：图片文件，后端统一转存为 jpg。
- 文件命名：游记图片按 `post-{user_id}-{yyyyMMddHHmmss}-{random}.jpg` 写入对象存储；头像上传仍使用 `avatar-{user_id}.jpg`。
- 响应示例：

```json
{
  "objectName": "post-0000000000000002-20260605211300-a1b2c3d4.jpg"
}
```
