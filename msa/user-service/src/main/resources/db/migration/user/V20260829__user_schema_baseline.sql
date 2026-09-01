CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `phone` VARCHAR(20) DEFAULT NULL,
  `email` VARCHAR(100) DEFAULT NULL,
  `password` VARCHAR(255) NOT NULL,
  `nickname` VARCHAR(50) DEFAULT '',
  `avatar` VARCHAR(500) DEFAULT '',
  `points` INT DEFAULT 0,
  `level` TINYINT DEFAULT 0,
  `status` TINYINT DEFAULT 0,
  `register_source` VARCHAR(20) DEFAULT 'PHONE',
  `last_login_time` DATETIME DEFAULT NULL,
  `last_login_ip` VARCHAR(45) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT DEFAULT 0,
  `gender` TINYINT DEFAULT 0,
  `birth_date` DATE DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_phone` (`phone`),
  UNIQUE KEY `uk_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `role` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `role_name` VARCHAR(20) NOT NULL,
  `permission` VARCHAR(500) DEFAULT '',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_name` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_role` (
  `user_id` BIGINT NOT NULL,
  `role_id` INT NOT NULL,
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_user_role_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `traveler` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(50) NOT NULL,
  `id_card` VARCHAR(20) NOT NULL,
  `phone` VARCHAR(20) DEFAULT '',
  `id_type` TINYINT DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_traveler_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `points_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `type` TINYINT NOT NULL,
  `amount` INT NOT NULL,
  `source` VARCHAR(50) DEFAULT '',
  `order_id` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_points_log_user_id` (`user_id`),
  KEY `idx_points_log_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_login_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `login_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `login_ip` VARCHAR(45) DEFAULT NULL,
  `device` VARCHAR(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_login_log_user_id` (`user_id`),
  KEY `idx_user_login_log_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `auth_verification_code` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `target` VARCHAR(128) NOT NULL,
  `channel` VARCHAR(20) NOT NULL,
  `code` VARCHAR(20) NOT NULL,
  `expire_time` TIMESTAMP NOT NULL,
  `consumed_time` TIMESTAMP NULL DEFAULT NULL,
  `send_count` INT DEFAULT 0,
  `create_time` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `admin_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `admin_id` BIGINT NOT NULL,
  `operation` VARCHAR(50) NOT NULL,
  `params` TEXT,
  `result` VARCHAR(20) DEFAULT '',
  `ip` VARCHAR(45) DEFAULT '',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_log_admin_id` (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `role` (`id`, `role_name`, `permission`)
VALUES (1, 'ADMIN', '*'), (2, 'USER', '')
ON DUPLICATE KEY UPDATE
  `permission` = VALUES(`permission`);
