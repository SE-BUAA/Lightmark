package top.ortus.lightmark.order.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import top.ortus.lightmark.order.OrderServiceApplication;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = OrderServiceApplication.class)
@ActiveProfiles("integration")
@Transactional
@EnabledIfEnvironmentVariable(named = "ORDER_IT_DB_URL", matches = ".+")
class OrderRepositoryIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistOrderPaymentAndFlightDetail() {
        String suffix = uniqueSuffix();
        long orderId = insertOrder("IT-F-" + suffix, "FLIGHT", 0);
        LocalDateTime now = LocalDateTime.now().withNano(0);

        assertThat(orderRepository.markPaid("IT-F-" + suffix, "MOCK_PAY", now, null)).isEqualTo(1);
        assertThat(orderRepository.markPaid("IT-F-" + suffix, "MOCK_PAY", now, null)).isZero();
        orderRepository.insertPaymentRecord(orderId, "IT-PAY-" + suffix, "MOCK_PAY", new BigDecimal("128.50"), 1, now, now);
        orderRepository.insertFlightOrderDetail(orderId, "100001", "CA100", Date.valueOf(LocalDate.of(2026, 9, 10)),
                "[{\"name\":\"integration-test\"}]", "20kg", 1);

        OrderRepository.OrderRow order = orderRepository.findByOrderNo("IT-F-" + suffix);
        assertThat(order).isNotNull();
        assertThat(order.status()).isEqualTo(1);
        assertThat(order.payTime()).isEqualTo(now);
        assertThat(orderRepository.findFlightDetail(orderId)).containsEntry("flight_no", "CA100");
        assertThat(jdbcTemplate.queryForObject("select count(1) from payment_record where order_id = ?", Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void shouldPersistHotelDetailAndEnforceInvoiceAndReviewUniqueness() {
        String suffix = uniqueSuffix();
        long orderId = insertOrder("IT-H-" + suffix, "HOTEL", 1);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        orderRepository.insertHotelOrderDetail(orderId, 100002L, Date.valueOf(LocalDate.of(2026, 9, 10)),
                Date.valueOf(LocalDate.of(2026, 9, 12)), 1, "[{\"name\":\"integration-test\"}]",
                new BigDecimal("256.00"), 100, new BigDecimal("255.00"));
        orderRepository.insertInvoiceApplication(orderId, 900001L, "PERSONAL", "Integration Test", null, 0, now);
        orderRepository.insertReview(orderId, 100002L, "HOTEL", 900001L, 5, "integration test", "[]", 1, now);

        assertThat(orderRepository.findHotelDetail(orderId)).containsEntry("room_id", 100002L);
        assertThat(orderRepository.invoiceExists(orderId)).isTrue();
        assertThat(orderRepository.reviewExists(orderId)).isTrue();
        assertThatThrownBy(() -> orderRepository.insertInvoiceApplication(orderId, 900001L, "PERSONAL", "Integration Test", null, 0, now))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> orderRepository.insertReview(orderId, 100002L, "HOTEL", 900001L, 5, "duplicate", "[]", 1, now))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldQueryAndTransitionOutboxRecords() {
        String suffix = uniqueSuffix();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime dueTime = LocalDateTime.of(2000, 1, 1, 0, 0);
        orderRepository.insertOutbox("IT_EVENT", "ORDER", "IT-" + suffix, "{\"source\":\"integration\"}",
                0, 0, dueTime, null, now, now);

        OrderRepository.OutboxRow outbox = orderRepository.dueOutbox(100).stream()
                .filter(row -> row.aggregateId().equals("IT-" + suffix))
                .findFirst()
                .orElseThrow();
        orderRepository.markOutboxRetry(outbox.id(), 1, now.plusMinutes(1), "temporary failure");
        assertThat(orderRepository.dueOutbox(100)).noneMatch(row -> row.id() == outbox.id());

        jdbcTemplate.update("update order_outbox set next_retry_time = ? where id = ?", dueTime, outbox.id());
        assertThat(orderRepository.dueOutbox(100)).anyMatch(row -> row.id() == outbox.id() && row.status() == 2);
        orderRepository.markOutboxSent(outbox.id());
        Integer status = jdbcTemplate.queryForObject("select status from order_outbox where id = ?", Integer.class, outbox.id());
        assertThat(status).isEqualTo(1);
    }

    private long insertOrder(String orderNo, String type, int status) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        return orderRepository.insertOrder(new OrderRepository.OrderInsert(
                orderNo, 900001L, type, new BigDecimal("256.00"), 0, new BigDecimal("256.00"),
                status == 0 ? "UNPAID" : "MOCK_PAY", "INTEGRATION_TEST", status,
                status == 0 ? now.plusMinutes(15) : null, status == 0 ? null : now,
                null, null, "{\"integration\":true}", 0, null, now, now
        ));
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }
}
