-- =====================================================================
-- 移除从单体导入时带过来的跨服务外键（content 各表 -> user 等）。
--
-- 背景：
--   - split-mysql.sh 把单体表的完整 DDL（含外键）导入 lightmark_content，
--     其中引用 user 表（现属 lightmark_user 域）的外键属于跨服务引用，
--     会在发布游记/点赞/评论/提问/行程时触发 child row 校验失败(1452)；
--   - 本地/全新 schema 由本服务 Flyway 基线创建，本来就没有这些外键；
--   - 同 schema 内引用（post_like->post、comment->comment）保留。
--
-- 实现说明（同 order V20260902）：
--   - MySQL 不支持 DROP FOREIGN KEY IF EXISTS；
--   - Flyway 不支持 DELIMITER 与存储过程；
--   - 用 information_schema 判断 + SET/PREPARE/EXECUTE 动态执行，
--     全部单语句、幂等（外键不存在时执行 SELECT 1 空操作）。
-- =====================================================================

-- post.user_id -> user
SET @drop_fk_sql = IF(EXISTS(
  SELECT 1 FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = 'post'
    AND constraint_name = 'fk_post_user_id' AND constraint_type = 'FOREIGN KEY'),
  'ALTER TABLE `post` DROP FOREIGN KEY `fk_post_user_id`', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql;
EXECUTE drop_fk_stmt;
DEALLOCATE PREPARE drop_fk_stmt;

-- post_like.user_id -> user
SET @drop_fk_sql = IF(EXISTS(
  SELECT 1 FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = 'post_like'
    AND constraint_name = 'fk_post_like_user_id' AND constraint_type = 'FOREIGN KEY'),
  'ALTER TABLE `post_like` DROP FOREIGN KEY `fk_post_like_user_id`', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql;
EXECUTE drop_fk_stmt;
DEALLOCATE PREPARE drop_fk_stmt;

-- comment.user_id -> user
SET @drop_fk_sql = IF(EXISTS(
  SELECT 1 FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = 'comment'
    AND constraint_name = 'fk_comment_user_id' AND constraint_type = 'FOREIGN KEY'),
  'ALTER TABLE `comment` DROP FOREIGN KEY `fk_comment_user_id`', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql;
EXECUTE drop_fk_stmt;
DEALLOCATE PREPARE drop_fk_stmt;

-- question.user_id -> user
SET @drop_fk_sql = IF(EXISTS(
  SELECT 1 FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = 'question'
    AND constraint_name = 'fk_question_user_id' AND constraint_type = 'FOREIGN KEY'),
  'ALTER TABLE `question` DROP FOREIGN KEY `fk_question_user_id`', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql;
EXECUTE drop_fk_stmt;
DEALLOCATE PREPARE drop_fk_stmt;

-- question.answer_user_id -> user
SET @drop_fk_sql = IF(EXISTS(
  SELECT 1 FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = 'question'
    AND constraint_name = 'fk_question_answer_user_id' AND constraint_type = 'FOREIGN KEY'),
  'ALTER TABLE `question` DROP FOREIGN KEY `fk_question_answer_user_id`', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql;
EXECUTE drop_fk_stmt;
DEALLOCATE PREPARE drop_fk_stmt;

-- travel_plan.user_id -> user
SET @drop_fk_sql = IF(EXISTS(
  SELECT 1 FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE() AND table_name = 'travel_plan'
    AND constraint_name = 'fk_travel_plan_user_id' AND constraint_type = 'FOREIGN KEY'),
  'ALTER TABLE `travel_plan` DROP FOREIGN KEY `fk_travel_plan_user_id`', 'SELECT 1');
PREPARE drop_fk_stmt FROM @drop_fk_sql;
EXECUTE drop_fk_stmt;
DEALLOCATE PREPARE drop_fk_stmt;
