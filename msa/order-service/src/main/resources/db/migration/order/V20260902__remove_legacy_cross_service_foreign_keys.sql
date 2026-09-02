-- =====================================================================
-- 移除从单体导入时带过来的跨服务外键（如 orders.fk_orders_user_id -> user 等）。
--
-- 背景：
--   - k8s 拆分流程中，split-mysql.sh 会把单体表的完整 DDL（含外键）导入
--     lightmark_order，其中引用 user/product 库的外键属于跨服务引用，应移除；
--   - 本地/全新 schema 由本服务 Flyway 基线创建，本来就没有这些外键。
--
-- 实现说明：
--   - MySQL 不支持 DROP FOREIGN KEY IF EXISTS；
--   - Flyway 不支持 DELIMITER 与存储过程（按分号切分语句）；
--   - 因此用 information_schema 判断 + SET/PREPARE/EXECUTE 动态执行，
--     全部为单语句，Flyway 兼容且幂等（外键不存在时执行 SELECT 1 空操作）。
-- =====================================================================

SET @drop_fk_sql = IF(EXISTS(
  SELECT 1 FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = 'orders'
    AND constraint_name = 'fk_orders_user_id' AND constraint_type = 'FOREIGN KEY'),
  'ALTER TABLE `orders` DROP FOREIGN KEY `fk_orders_user_id`', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql;
EXECUTE drop_fk_stmt;
DEALLOCATE PREPARE drop_fk_stmt;

SET @drop_fk_sql = IF(EXISTS(
  SELECT 1 FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = 'flight_order_detail'
    AND constraint_name = 'fk_flight_order_detail_product_id' AND constraint_type = 'FOREIGN KEY'),
  'ALTER TABLE `flight_order_detail` DROP FOREIGN KEY `fk_flight_order_detail_product_id`', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql;
EXECUTE drop_fk_stmt;
DEALLOCATE PREPARE drop_fk_stmt;

SET @drop_fk_sql = IF(EXISTS(
  SELECT 1 FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = 'review'
    AND constraint_name = 'fk_review_user_id' AND constraint_type = 'FOREIGN KEY'),
  'ALTER TABLE `review` DROP FOREIGN KEY `fk_review_user_id`', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql;
EXECUTE drop_fk_stmt;
DEALLOCATE PREPARE drop_fk_stmt;
