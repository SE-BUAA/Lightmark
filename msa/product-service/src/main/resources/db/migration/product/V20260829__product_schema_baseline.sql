CREATE TABLE IF NOT EXISTS `product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `product_type` VARCHAR(20) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `price` DECIMAL(10, 2) NOT NULL,
  `stock` INT DEFAULT 0,
  `sold_count` INT DEFAULT 0,
  `status` TINYINT DEFAULT 1,
  `extra` JSON DEFAULT NULL,
  `category_tags` JSON DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_type_status` (`product_type`, `status`),
  KEY `idx_product_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `room_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `hotel_id` BIGINT NOT NULL,
  `room_name` VARCHAR(50) NOT NULL,
  `price` DECIMAL(10, 2) NOT NULL,
  `cancel_policy` VARCHAR(20) DEFAULT 'NON_REFUNDABLE',
  `breakfast` TINYINT DEFAULT 0,
  `bed_type` VARCHAR(50) DEFAULT NULL,
  `area` VARCHAR(50) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_room_type_hotel_id` (`hotel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `product_view_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT NOT NULL,
  `user_id` BIGINT DEFAULT NULL,
  `view_source` VARCHAR(20) DEFAULT 'WEB',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_product_view_log_product_id` (`product_id`),
  KEY `idx_product_view_log_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Monolith migration mapping:
-- V20260527/V20260530/V20260531/V20260601/V20260602/V20260603/V20260605/V20260610/V20260611
