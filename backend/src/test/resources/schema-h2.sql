CREATE TABLE `user` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `phone` VARCHAR(20),
  `email` VARCHAR(100),
  `password` VARCHAR(255) NOT NULL,
  `nickname` VARCHAR(30) NOT NULL,
  `country_code` VARCHAR(8),
  `last_login_time` TIMESTAMP NULL,
  `last_login_ip` VARCHAR(45),
  `points` INT DEFAULT 0,
  `level` TINYINT DEFAULT 0,
  `status` TINYINT DEFAULT 0,
  `register_source` VARCHAR(20) DEFAULT 'PHONE',
  `deleted` TINYINT DEFAULT 0,
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_user_phone UNIQUE (`phone`),
  CONSTRAINT uq_user_email UNIQUE (`email`),
  CONSTRAINT uq_user_nickname UNIQUE (`nickname`)
);

CREATE TABLE auth_verification_code (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `target` VARCHAR(128) NOT NULL,
  `channel` VARCHAR(20) NOT NULL,
  `code` VARCHAR(20) NOT NULL,
  `expire_time` TIMESTAMP NOT NULL,
  `consumed_time` TIMESTAMP NULL,
  `send_count` INT DEFAULT 0,
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE role (
  id INT AUTO_INCREMENT PRIMARY KEY,
  role_name VARCHAR(20) NOT NULL,
  permission VARCHAR(255)
);

CREATE TABLE user_role (
  user_id BIGINT NOT NULL,
  role_id INT NOT NULL,
  PRIMARY KEY (user_id, role_id)
);

CREATE TABLE product (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_type VARCHAR(20) NOT NULL,
  name VARCHAR(100) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  stock INT DEFAULT 0,
  sold_count INT DEFAULT 0,
  category_tags VARCHAR(255),
  status TINYINT DEFAULT 1,
  extra CLOB,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(32) NOT NULL,
  user_id BIGINT NOT NULL,
  order_type VARCHAR(20) NOT NULL,
  total_amount DECIMAL(10,2),
  points_deduct INT DEFAULT 0,
  pay_amount DECIMAL(10,2) NOT NULL,
  payment_method VARCHAR(20),
  source VARCHAR(50),
  status TINYINT DEFAULT 0,
  pay_deadline TIMESTAMP,
  pay_time TIMESTAMP,
  cancel_reason VARCHAR(255),
  pickup_code VARCHAR(6),
  changed_once TINYINT DEFAULT 0,
  original_order_no VARCHAR(32),
  extra_info VARCHAR(2000),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE flight_order_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  flight_no VARCHAR(20) NOT NULL,
  departure_date DATE NOT NULL,
  passenger_list CLOB,
  baggage VARCHAR(100),
  insurance TINYINT DEFAULT 0
);

CREATE TABLE payment_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  transaction_id VARCHAR(64) NOT NULL,
  payment_method VARCHAR(20) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status TINYINT DEFAULT 0,
  callback_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE room_type (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hotel_id BIGINT NOT NULL,
  room_name VARCHAR(100) NOT NULL,
  breakfast INT DEFAULT 0,
  cancel_policy VARCHAR(50),
  price DECIMAL(10,2) NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE invoice_application (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL,
  title VARCHAR(100) NOT NULL,
  tax_no VARCHAR(50),
  status TINYINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE points_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type TINYINT NOT NULL,
  amount INT NOT NULL,
  source VARCHAR(50),
  order_id BIGINT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE admin_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  admin_id BIGINT NOT NULL,
  operation VARCHAR(50) NOT NULL,
  params VARCHAR(500),
  result VARCHAR(20),
  ip VARCHAR(45),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE travel_plan (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(100) DEFAULT '',
  destination VARCHAR(100) NOT NULL,
  start_date DATE,
  end_date DATE,
  plan_data CLOB,
  is_public TINYINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE post (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  content CLOB,
  images CLOB,
  likes INT DEFAULT 0,
  comments_count INT DEFAULT 0,
  status TINYINT DEFAULT 1,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE post_like (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (post_id, user_id)
);

CREATE TABLE comment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  target_type VARCHAR(20) NOT NULL,
  target_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  parent_id BIGINT,
  content VARCHAR(1000) NOT NULL,
  likes INT DEFAULT 0,
  is_approved TINYINT DEFAULT 1,
  ip VARCHAR(45),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE question (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  content VARCHAR(500) NOT NULL,
  answer VARCHAR(1000),
  answer_user_id BIGINT,
  status TINYINT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  answer_time TIMESTAMP
);

CREATE TABLE review (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  target_type VARCHAR(20),
  user_id BIGINT NOT NULL,
  rating INT NOT NULL,
  content VARCHAR(1000) NOT NULL,
  images CLOB,
  status TINYINT DEFAULT 1,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

