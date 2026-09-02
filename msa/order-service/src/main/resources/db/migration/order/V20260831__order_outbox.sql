CREATE TABLE IF NOT EXISTS `order_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_type` VARCHAR(40) NOT NULL,
  `aggregate_type` VARCHAR(40) NOT NULL,
  `aggregate_id` VARCHAR(64) NOT NULL,
  `payload` JSON NOT NULL,
  `status` TINYINT DEFAULT 0,
  `retry_count` INT DEFAULT 0,
  `next_retry_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `last_error` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_outbox_status_next` (`status`, `next_retry_time`),
  KEY `idx_order_outbox_aggregate` (`aggregate_type`, `aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
