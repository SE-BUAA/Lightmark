CREATE TABLE IF NOT EXISTS `orders` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_no` VARCHAR(32) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `order_type` VARCHAR(20) NOT NULL,
  `total_amount` DECIMAL(10, 2) NOT NULL,
  `points_deduct` INT DEFAULT 0,
  `pay_amount` DECIMAL(10, 2) NOT NULL,
  `payment_method` VARCHAR(20) DEFAULT NULL,
  `source` VARCHAR(20) DEFAULT 'PC',
  `status` TINYINT DEFAULT 0,
  `pay_deadline` DATETIME DEFAULT NULL,
  `pay_time` DATETIME DEFAULT NULL,
  `cancel_reason` VARCHAR(200) DEFAULT NULL,
  `pickup_code` VARCHAR(6) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `extra_info` TEXT,
  `changed_once` TINYINT DEFAULT 0,
  `original_order_no` VARCHAR(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_order_no` (`order_no`),
  UNIQUE KEY `uk_orders_pickup_code` (`pickup_code`),
  KEY `idx_orders_user_id` (`user_id`),
  KEY `idx_orders_type_status` (`order_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `payment_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `transaction_id` VARCHAR(64) NOT NULL,
  `payment_method` VARCHAR(20) NOT NULL,
  `amount` DECIMAL(10, 2) NOT NULL,
  `status` TINYINT DEFAULT 0,
  `callback_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_record_transaction_id` (`transaction_id`),
  KEY `idx_payment_record_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `flight_order_detail` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `flight_no` VARCHAR(20) NOT NULL,
  `departure_date` DATE NOT NULL,
  `passenger_list` JSON NOT NULL,
  `baggage` VARCHAR(50) DEFAULT '',
  `insurance` TINYINT DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_flight_order_detail_order_id` (`order_id`),
  KEY `idx_flight_order_detail_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `hotel_order_detail` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `room_id` BIGINT NOT NULL,
  `check_in_date` DATE NOT NULL,
  `check_out_date` DATE NOT NULL,
  `room_num` INT NOT NULL,
  `guest_list` JSON NOT NULL,
  `total_price` DECIMAL(10, 2) NOT NULL,
  `points_deducted` INT DEFAULT 0,
  `pay_amount` DECIMAL(10, 2) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_hotel_order_detail_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `invoice_application` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `type` VARCHAR(20) NOT NULL,
  `title` VARCHAR(100) NOT NULL,
  `tax_no` VARCHAR(50) DEFAULT NULL,
  `status` TINYINT DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_invoice_application_order_id` (`order_id`),
  KEY `idx_invoice_application_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `review` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT NOT NULL,
  `product_id` BIGINT DEFAULT NULL,
  `target_type` VARCHAR(20) DEFAULT NULL,
  `user_id` BIGINT NOT NULL,
  `rating` TINYINT NOT NULL,
  `content` VARCHAR(500) DEFAULT '',
  `images` JSON DEFAULT NULL,
  `status` TINYINT DEFAULT 1,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_order_id` (`order_id`),
  KEY `idx_review_user_id` (`user_id`),
  KEY `idx_review_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Monolith migration mapping:
-- V20260528/V20260529/V20260603/V20260604/V20260606/V20260607
