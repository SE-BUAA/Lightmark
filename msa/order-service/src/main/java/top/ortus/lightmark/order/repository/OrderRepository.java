package top.ortus.lightmark.order.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insertOrder(OrderInsert insert) {
        jdbcTemplate.update(
                """
                insert into `orders`
                (order_no, user_id, order_type, total_amount, points_deduct, pay_amount, payment_method,
                 source, status, pay_deadline, pay_time, cancel_reason, pickup_code, extra_info,
                 changed_once, original_order_no, create_time, update_time)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                insert.orderNo(),
                insert.userId(),
                insert.orderType(),
                insert.totalAmount(),
                insert.pointsDeduct(),
                insert.payAmount(),
                insert.paymentMethod(),
                insert.source(),
                insert.status(),
                insert.payDeadline(),
                insert.payTime(),
                insert.cancelReason(),
                insert.pickupCode(),
                insert.extraInfo(),
                insert.changedOnce(),
                insert.originalOrderNo(),
                insert.createTime(),
                insert.updateTime()
        );
        return jdbcTemplate.queryForObject("select id from `orders` where order_no = ?", Long.class, insert.orderNo());
    }

    public OrderRow findByOrderNo(String orderNo) {
        List<OrderRow> rows = jdbcTemplate.query(
                "select * from `orders` where order_no = ?",
                (rs, rowNum) -> new OrderRow(
                        rs.getLong("id"),
                        rs.getString("order_no"),
                        rs.getLong("user_id"),
                        rs.getString("order_type"),
                        rs.getBigDecimal("total_amount"),
                        rs.getBigDecimal("pay_amount"),
                        rs.getString("payment_method"),
                        rs.getInt("points_deduct"),
                        rs.getString("source"),
                        rs.getInt("status"),
                        rs.getTimestamp("pay_deadline") == null ? null : rs.getTimestamp("pay_deadline").toLocalDateTime(),
                        rs.getTimestamp("pay_time") == null ? null : rs.getTimestamp("pay_time").toLocalDateTime(),
                        rs.getString("cancel_reason"),
                        rs.getString("pickup_code"),
                        rs.getObject("changed_once") == null ? 0 : rs.getInt("changed_once"),
                        rs.getString("original_order_no"),
                        rs.getString("extra_info"),
                        rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime(),
                        rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime()
                ),
                orderNo
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public OrderRow findById(long id) {
        List<OrderRow> rows = jdbcTemplate.query(
                "select * from `orders` where id = ?",
                (rs, rowNum) -> new OrderRow(
                        rs.getLong("id"),
                        rs.getString("order_no"),
                        rs.getLong("user_id"),
                        rs.getString("order_type"),
                        rs.getBigDecimal("total_amount"),
                        rs.getBigDecimal("pay_amount"),
                        rs.getString("payment_method"),
                        rs.getInt("points_deduct"),
                        rs.getString("source"),
                        rs.getInt("status"),
                        rs.getTimestamp("pay_deadline") == null ? null : rs.getTimestamp("pay_deadline").toLocalDateTime(),
                        rs.getTimestamp("pay_time") == null ? null : rs.getTimestamp("pay_time").toLocalDateTime(),
                        rs.getString("cancel_reason"),
                        rs.getString("pickup_code"),
                        rs.getObject("changed_once") == null ? 0 : rs.getInt("changed_once"),
                        rs.getString("original_order_no"),
                        rs.getString("extra_info"),
                        rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime(),
                        rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime()
                ),
                id
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public OrderRow findByPickupCode(String pickupCode) {
        List<OrderRow> rows = jdbcTemplate.query(
                "select * from `orders` where pickup_code = ?",
                (rs, rowNum) -> new OrderRow(
                        rs.getLong("id"),
                        rs.getString("order_no"),
                        rs.getLong("user_id"),
                        rs.getString("order_type"),
                        rs.getBigDecimal("total_amount"),
                        rs.getBigDecimal("pay_amount"),
                        rs.getString("payment_method"),
                        rs.getInt("points_deduct"),
                        rs.getString("source"),
                        rs.getInt("status"),
                        rs.getTimestamp("pay_deadline") == null ? null : rs.getTimestamp("pay_deadline").toLocalDateTime(),
                        rs.getTimestamp("pay_time") == null ? null : rs.getTimestamp("pay_time").toLocalDateTime(),
                        rs.getString("cancel_reason"),
                        rs.getString("pickup_code"),
                        rs.getObject("changed_once") == null ? 0 : rs.getInt("changed_once"),
                        rs.getString("original_order_no"),
                        rs.getString("extra_info"),
                        rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime(),
                        rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime()
                ),
                pickupCode
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<OrderRow> listOrders(Long userId, String orderType, Integer status, int limit, int offset) {
        StringBuilder sql = new StringBuilder("select * from `orders` where 1 = 1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (userId != null) {
            sql.append(" and user_id = ?");
            args.add(userId);
        }
        if (orderType != null && !orderType.isBlank()) {
            sql.append(" and order_type = ?");
            args.add(orderType);
        }
        if (status != null) {
            sql.append(" and status = ?");
            args.add(status);
        }
        sql.append(" order by create_time desc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new OrderRow(
                rs.getLong("id"),
                rs.getString("order_no"),
                rs.getLong("user_id"),
                rs.getString("order_type"),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("pay_amount"),
                rs.getString("payment_method"),
                rs.getInt("points_deduct"),
                rs.getString("source"),
                rs.getInt("status"),
                rs.getTimestamp("pay_deadline") == null ? null : rs.getTimestamp("pay_deadline").toLocalDateTime(),
                rs.getTimestamp("pay_time") == null ? null : rs.getTimestamp("pay_time").toLocalDateTime(),
                rs.getString("cancel_reason"),
                rs.getString("pickup_code"),
                rs.getObject("changed_once") == null ? 0 : rs.getInt("changed_once"),
                rs.getString("original_order_no"),
                rs.getString("extra_info"),
                rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime(),
                rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime()
        ), args.toArray());
    }

    public long countOrders(Long userId, String orderType, Integer status) {
        StringBuilder sql = new StringBuilder("select count(1) from `orders` where 1 = 1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (userId != null) {
            sql.append(" and user_id = ?");
            args.add(userId);
        }
        if (orderType != null && !orderType.isBlank()) {
            sql.append(" and order_type = ?");
            args.add(orderType);
        }
        if (status != null) {
            sql.append(" and status = ?");
            args.add(status);
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    public int updateStatus(String orderNo, int status, String remark) {
        return jdbcTemplate.update(
                "update `orders` set status = ?, cancel_reason = ?, update_time = now() where order_no = ?",
                status,
                remark,
                orderNo
        );
    }

    public int markPaid(String orderNo, String paymentMethod, LocalDateTime payTime, String pickupCode) {
        return jdbcTemplate.update(
                "update `orders` set status = 1, payment_method = ?, pay_time = ?, pickup_code = COALESCE(?, pickup_code), update_time = ? where order_no = ? and status = 0",
                paymentMethod,
                payTime,
                pickupCode,
                payTime,
                orderNo
        );
    }

    public int markPaid(String orderNo, String paymentMethod, LocalDateTime payTime, String pickupCode, int status) {
        return jdbcTemplate.update(
                "update `orders` set status = ?, payment_method = ?, pay_time = ?, pickup_code = COALESCE(?, pickup_code), update_time = ? where order_no = ? and status = 0",
                status,
                paymentMethod,
                payTime,
                pickupCode,
                payTime,
                orderNo
        );
    }

    public int cancelPending(String orderNo, String reason) {
        return jdbcTemplate.update(
                "update `orders` set status = 2, cancel_reason = ?, update_time = now() where order_no = ? and status = 0",
                reason,
                orderNo
        );
    }

    public int refundPaid(String orderNo, String reason) {
        return jdbcTemplate.update(
                "update `orders` set status = 4, cancel_reason = ?, update_time = now() where order_no = ? and status = 1",
                reason,
                orderNo
        );
    }

    public int markChanged(String orderNo, String reason) {
        return jdbcTemplate.update(
                "update `orders` set status = 5, pickup_code = null, changed_once = 1, cancel_reason = ?, update_time = now() where order_no = ? and status = 1 and coalesce(changed_once, 0) = 0",
                reason,
                orderNo
        );
    }

    public int countByOrderNo(String orderNo) {
        Long count = jdbcTemplate.queryForObject("select count(1) from `orders` where order_no = ?", Long.class, orderNo);
        return count == null ? 0 : count.intValue();
    }

    public int countByPickupCode(String pickupCode) {
        Long count = jdbcTemplate.queryForObject("select count(1) from `orders` where pickup_code = ?", Long.class, pickupCode);
        return count == null ? 0 : count.intValue();
    }

    public void insertPaymentRecord(long orderId, String transactionId, String paymentMethod, BigDecimal amount, int status, LocalDateTime callbackTime, LocalDateTime createTime) {
        jdbcTemplate.update(
                """
                insert into payment_record
                (order_id, transaction_id, payment_method, amount, status, callback_time, create_time)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                orderId,
                transactionId,
                paymentMethod,
                amount,
                status,
                callbackTime,
                createTime
        );
    }

    public void insertFlightOrderDetail(long orderId, String productId, String flightNo, java.sql.Date departureDate,
                                       String passengerList, String baggage, int insurance) {
        jdbcTemplate.update(
                """
                insert into flight_order_detail
                (order_id, product_id, flight_no, departure_date, passenger_list, baggage, insurance)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                orderId,
                productId,
                flightNo,
                departureDate,
                passengerList,
                baggage,
                insurance
        );
    }

    public void insertHotelOrderDetail(long orderId, long roomId, java.sql.Date checkInDate, java.sql.Date checkOutDate,
                                       int roomNum, String guestList, BigDecimal totalPrice, int pointsDeducted, BigDecimal payAmount) {
        jdbcTemplate.update(
                """
                insert into hotel_order_detail
                (order_id, room_id, check_in_date, check_out_date, room_num, guest_list, total_price, points_deducted, pay_amount)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                orderId,
                roomId,
                checkInDate,
                checkOutDate,
                roomNum,
                guestList,
                totalPrice,
                pointsDeducted,
                payAmount
        );
    }

    public Map<String, Object> findFlightDetail(long orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from flight_order_detail where order_id = ?", orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> findHotelDetail(long orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from hotel_order_detail where order_id = ?", orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean invoiceExists(long orderId) {
        Long count = jdbcTemplate.queryForObject("select count(1) from invoice_application where order_id = ?", Long.class, orderId);
        return count != null && count > 0;
    }

    public void insertInvoiceApplication(long orderId, long userId, String type, String title, String taxNo, int status, LocalDateTime createTime) {
        jdbcTemplate.update(
                """
                insert into invoice_application
                (order_id, user_id, type, title, tax_no, status, create_time)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                orderId,
                userId,
                type,
                title,
                taxNo,
                status,
                createTime
        );
    }

    public boolean reviewExists(long orderId) {
        Long count = jdbcTemplate.queryForObject("select count(1) from review where order_id = ?", Long.class, orderId);
        return count != null && count > 0;
    }

    public void insertReview(long orderId, Long productId, String targetType, long userId, int rating, String content, String images, int status, LocalDateTime createTime) {
        jdbcTemplate.update(
                """
                insert into review
                (order_id, product_id, target_type, user_id, rating, content, images, status, create_time)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                orderId,
                productId,
                targetType,
                userId,
                rating,
                content,
                images,
                status,
                createTime
        );
    }

    public List<Map<String, Object>> listReviewsByProductId(long productId, int limit, int offset) {
        return jdbcTemplate.queryForList(
                """
                select id, order_id, product_id, target_type, user_id, rating, content, images, status, create_time
                from review
                where product_id = ? and status = 1
                order by create_time desc
                limit ? offset ?
                """,
                productId,
                limit,
                offset
        );
    }

    public void insertOutbox(String eventType, String aggregateType, String aggregateId, String payload, int status, int retryCount, LocalDateTime nextRetryTime, String lastError, LocalDateTime createTime, LocalDateTime updateTime) {
        jdbcTemplate.update(
                """
                insert into order_outbox
                (event_type, aggregate_type, aggregate_id, payload, status, retry_count, next_retry_time, last_error, create_time, update_time)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                eventType,
                aggregateType,
                aggregateId,
                payload,
                status,
                retryCount,
                nextRetryTime,
                lastError,
                createTime,
                updateTime
        );
    }

    public List<OutboxRow> dueOutbox(int batchSize) {
        return jdbcTemplate.query(
                """
                select * from order_outbox
                where status in (0, 2)
                  and next_retry_time <= now()
                order by id asc
                limit ?
                """,
                (rs, rowNum) -> new OutboxRow(
                        rs.getLong("id"),
                        rs.getString("event_type"),
                        rs.getString("aggregate_type"),
                        rs.getString("aggregate_id"),
                        rs.getString("payload"),
                        rs.getInt("status"),
                        rs.getInt("retry_count"),
                        rs.getTimestamp("next_retry_time") == null ? null : rs.getTimestamp("next_retry_time").toLocalDateTime(),
                        rs.getString("last_error"),
                        rs.getTimestamp("create_time") == null ? null : rs.getTimestamp("create_time").toLocalDateTime(),
                        rs.getTimestamp("update_time") == null ? null : rs.getTimestamp("update_time").toLocalDateTime()
                ),
                batchSize
        );
    }

    public void markOutboxSent(long id) {
        jdbcTemplate.update("update order_outbox set status = 1, last_error = null, update_time = now() where id = ?", id);
    }

    public void markOutboxRetry(long id, int retryCount, LocalDateTime nextRetryTime, String lastError) {
        jdbcTemplate.update(
                "update order_outbox set status = 2, retry_count = ?, next_retry_time = ?, last_error = ?, update_time = now() where id = ?",
                retryCount,
                nextRetryTime,
                lastError,
                id
        );
    }

    public record OrderInsert(String orderNo, long userId, String orderType, BigDecimal totalAmount, int pointsDeduct,
                              BigDecimal payAmount, String paymentMethod, String source, int status,
                              LocalDateTime payDeadline, LocalDateTime payTime, String cancelReason,
                              String pickupCode, String extraInfo, int changedOnce, String originalOrderNo,
                              LocalDateTime createTime, LocalDateTime updateTime) {}

    public record OrderRow(long id, String orderNo, long userId, String orderType, BigDecimal totalAmount,
                           BigDecimal payAmount, String paymentMethod, int pointsDeduct, String source, int status,
                           LocalDateTime payDeadline, LocalDateTime payTime, String cancelReason, String pickupCode,
                           int changedOnce, String originalOrderNo, String extraInfo,
                           LocalDateTime createTime, LocalDateTime updateTime) {}

    public record OutboxRow(long id, String eventType, String aggregateType, String aggregateId, String payload,
                            int status, int retryCount, LocalDateTime nextRetryTime, String lastError,
                            LocalDateTime createTime, LocalDateTime updateTime) {}
}
