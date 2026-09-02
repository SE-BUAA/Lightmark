DELIMITER $$

CREATE PROCEDURE drop_order_foreign_key_if_exists(IN table_name_param VARCHAR(64), IN constraint_name_param VARCHAR(64))
BEGIN
  DECLARE constraint_exists INT DEFAULT 0;

  SELECT COUNT(*) INTO constraint_exists
  FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE()
    AND table_name = table_name_param
    AND constraint_name = constraint_name_param
    AND constraint_type = 'FOREIGN KEY';

  IF constraint_exists > 0 THEN
    SET @drop_foreign_key_sql = CONCAT(
      'ALTER TABLE `', REPLACE(table_name_param, '`', '``'),
      '` DROP FOREIGN KEY `', REPLACE(constraint_name_param, '`', '``'), '`'
    );
    PREPARE drop_foreign_key_statement FROM @drop_foreign_key_sql;
    EXECUTE drop_foreign_key_statement;
    DEALLOCATE PREPARE drop_foreign_key_statement;
  END IF;
END$$

DELIMITER ;

CALL drop_order_foreign_key_if_exists('orders', 'fk_orders_user_id');
CALL drop_order_foreign_key_if_exists('flight_order_detail', 'fk_flight_order_detail_product_id');
CALL drop_order_foreign_key_if_exists('review', 'fk_review_user_id');
DROP PROCEDURE drop_order_foreign_key_if_exists;
