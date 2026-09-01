# DBeaver 连接远程数据库与 user-service 验收

## 1. 创建 DBeaver 连接

1. 打开 DBeaver，点击左上角 **新建数据库连接**，选择 **MySQL**。
2. 在 **主要（Main）** 页面填写：
   - 主机：`150.230.223.11`
   - 端口：`3306`
   - 用户名：`se`
   - 密码：从组内安全渠道获取，不写入 Git 或配置文件
   - 数据库：先留空，连接成功后再选择数据库
3. 在 **驱动属性（Driver properties）** 中确认：
   - `allowPublicKeyRetrieval` = `true`
   - `useSSL` = `false`
4. 点击 **测试连接**。出现“连接成功”后点击 **完成**。

如果连接失败：

- `Communications link failure` 或超时：服务器 3306 端口未对当前网络开放，需要让服务器管理员把你的公网 IP 加入白名单，或使用 SSH 隧道。
- `Access denied`：用户名或密码错误，或账号没有远程登录权限。
- `Unknown database`：数据库名填写错误；先留空连接，再在左侧展开数据库列表。

## 2. 检查现有单体库

在 DBeaver 的 SQL 编辑器中选择服务器连接，执行：

```sql
SHOW DATABASES;
USE lightmark;
SHOW TABLES;
SELECT COUNT(*) AS user_count FROM `user`;
```

应能看到 `lightmark` 和 `user` 表。`user_count` 能返回数字，说明远程单体数据可读。

## 3. 验证 user-service 所需表

本次迭代不执行建库、导入或 Flyway 迁移，只读取服务器已有的 `lightmark` 库。在 DBeaver 中执行：

```sql
USE lightmark;
SHOW TABLES;
SELECT COUNT(*) AS user_count FROM `user`;
SELECT COUNT(*) AS role_count FROM `role`;
SELECT COUNT(*) AS user_role_count FROM `user_role`;
SELECT COUNT(*) AS traveler_count FROM `traveler`;
SELECT COUNT(*) AS points_log_count FROM `points_log`;
SELECT COUNT(*) AS login_log_count FROM `user_login_log`;
SELECT COUNT(*) AS verification_count FROM `auth_verification_code`;
SELECT COUNT(*) AS admin_log_count FROM `admin_log`;
```

能看到 8 张用户域表并返回数量，即满足 user-service 的只读前置条件。不要执行 `scripts/db/split-mysql.sh`，该脚本属于其他阶段的数据库迁移工具。

## 4. 配置并启动 user-service

在 Git Bash 中进入项目根目录，设置服务器连接（不会修改数据库）：

```bash
cd /e/study/project/small_term/Lightmark
export USER_DB_HOST=150.230.223.11
export USER_DB_PORT=3306
export USER_DB_NAME=lightmark
export USER_DB_USER=se
read -r -s -p "MySQL password: " USER_DB_PASSWORD; echo
export USER_DB_PASSWORD
export JWT_SECRET='请设置至少 32 位随机字符串'
mvn -f msa/pom.xml -pl user-service -am clean package
java -jar msa/user-service/target/user-service-0.0.1-SNAPSHOT.jar
```

启动日志出现 `Started UserServiceApplication` 才算启动成功。

## 5. 验收命令

在另一个 PowerShell 窗口执行：

```powershell
Invoke-RestMethod http://localhost:8081/api/health
```

应返回 `service=user-service`、`status=UP`。登录、验证码、资料接口再使用项目 API 文档中的请求体进行测试；未携带或携带错误 JWT 访问 `/api/user/current` 应返回 HTTP 401。启动前后可在 DBeaver 对比上述 8 张表的数据量，确认服务启动没有额外建表或改表。

## 6. 当前完成度

- 阶段 1：代码边界和迁移清单已完成；远程库中的实际表归属需按第 2、3 步确认。
- 阶段 2：Maven、实体和 Repository 已完成；Flyway 默认关闭，服务不会修改远程数据库。
- 阶段 3：认证和用户资料代码已迁移并保持原 API 路径；需要在服务器网络可达时完成运行/API 验收。
