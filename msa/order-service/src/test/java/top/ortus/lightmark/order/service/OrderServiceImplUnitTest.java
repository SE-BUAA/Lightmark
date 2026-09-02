package top.ortus.lightmark.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.ortus.lightmark.order.client.ProductClient;
import top.ortus.lightmark.order.client.UserClient;
import top.ortus.lightmark.order.repository.OrderRepository;
import top.ortus.lightmark.order.tools.ApiException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplUnitTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 8, 31, 12, 0);

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductClient productClient;
    @Mock
    private UserClient userClient;

    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderRepository, productClient, userClient, new ObjectMapper(), 20, 30);
        service.setClock(Clock.fixed(FIXED_NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

    @Test
    void previewFlightOrderShouldCalculateAmountsAndPassengerCount() {
        when(productClient.getProduct("F-1")).thenReturn(product("F-1", "CA100", "300.00", 8, 1));

        Map<String, Object> result = service.previewFlightOrder(null, Map.of(
                "productId", "F-1",
                "passengers", List.of(Map.of("name", "张三"), Map.of("name", "李四")),
                "taxAmount", "50.00",
                "serviceAmount", "20.00",
                "pointsAmount", "30.00",
                "cabin", "ECONOMY"
        ));

        assertThat(result).containsEntry("productId", "F-1")
                .containsEntry("passengerCount", 2)
                .containsEntry("stockEnough", true)
                .containsEntry("cabin", "ECONOMY");
        assertThat(result.get("ticketAmount")).isEqualTo(new BigDecimal("600.00"));
        assertThat(result.get("totalAmount")).isEqualTo(new BigDecimal("670.00"));
        assertThat(result.get("payAmount")).isEqualTo(new BigDecimal("640.00"));
    }

    @Test
    void previewFlightOrderShouldRejectInsufficientStock() {
        when(productClient.getProduct("F-1")).thenReturn(product("F-1", "CA100", "300.00", 2, 1));

        assertThatThrownBy(() -> service.previewFlightOrder(null, Map.of(
                "productId", "F-1",
                "passengerCount", 2
        ))).isInstanceOf(ApiException.class)
                .hasMessage("flight stock is insufficient");
    }

    @Test
    void createTrainOrderShouldPersistAndReserveStock() {
        Map<String, Object> product = trainProduct("T-1", "G1次", "100.00", 5, 0, "2026-09-01", "08:00");
        when(productClient.getProduct("T-1")).thenReturn(product);
        doNothing().when(productClient).adjustStock("T-1", -1);
        when(orderRepository.countByOrderNo(any())).thenReturn(0);
        when(orderRepository.insertOrder(any())).thenReturn(101L);
        when(orderRepository.findByOrderNo(any())).thenReturn(order(101L, "202608310001", 2L, "TRAIN", "100.00", 0,
                "{\"productId\":\"T-1\"}"));

        Map<String, Object> result = service.createTrainOrder(2L, Map.of(
                "productId", "T-1",
                "passengerName", "张三",
                "passengerPhone", "13800000000",
                "passengerAge", 20,
                "seatType", "二等座"
        ));

        assertThat(result.get("orderNo")).isEqualTo("202608310001");
        verify(productClient).adjustStock("T-1", -1);
        verify(orderRepository).insertOrder(any());
    }

    @Test
    void createTrainOrderShouldRejectInvalidPassengerAgeBeforeStockChange() {
        assertThatThrownBy(() -> service.createTrainOrder(2L, Map.of(
                "productId", "T-1",
                "passengerName", "张三",
                "passengerPhone", "13800000000",
                "passengerAge", 0,
                "seatType", "二等座"
        ))).isInstanceOf(ApiException.class)
                .hasMessage("年龄必须为1-120之间的正整数");

        verify(productClient, never()).adjustStock(anyString(), anyInt());
        verify(orderRepository, never()).insertOrder(any());
    }

    @Test
    void createVacationOrderShouldRollbackStockWhenPersistFails() {
        when(productClient.getProduct("V-1")).thenReturn(product("V-1", "三亚度假", "1000.00", 10, 0));
        doNothing().when(productClient).adjustStock("V-1", -3);
        doNothing().when(productClient).adjustStock("V-1", 3);
        when(orderRepository.countByOrderNo(any())).thenReturn(0);
        when(orderRepository.insertOrder(any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.createVacationOrder(2L, Map.of(
                "productId", "V-1",
                "travelerName", "王五",
                "travelerPhone", "13900000000",
                "travelerCount", 3
        ))).isInstanceOf(IllegalStateException.class);

        verify(productClient).adjustStock("V-1", -3);
        verify(productClient).adjustStock("V-1", 3);
    }

    @Test
    void payOrderShouldBeIdempotentWhenAlreadyPaid() {
        OrderRepository.OrderRow paid = order(20L, "T-PAID", 2L, "TRAIN", "100.00", 1,
                "{\"productId\":\"T-1\"}");
        when(orderRepository.findByOrderNo("T-PAID")).thenReturn(paid);

        Map<String, Object> result = service.payOrder("T-PAID", Map.of("paymentMethod", "ALIPAY"));

        assertThat(result).containsEntry("orderNo", "T-PAID")
                .containsEntry("status", 1)
                .containsEntry("statusText", "已支付");
        verify(orderRepository, never()).markPaid(anyString(), anyString(), any(), any());
        verify(orderRepository, never()).insertPaymentRecord(anyLong(), anyString(), anyString(), any(), anyInt(), any(), any());
    }

    @Test
    void payOrderShouldCancelExpiredPendingOrder() {
        OrderRepository.OrderRow expired = new OrderRepository.OrderRow(
                30L, "T-EXPIRED", 2L, "TRAIN", new BigDecimal("100.00"), new BigDecimal("100.00"),
                "UNPAID", 0, "PC", 0, FIXED_NOW.minusMinutes(1), null, null, null, 0, null,
                "{\"productId\":\"T-1\"}", FIXED_NOW.minusMinutes(20), FIXED_NOW.minusMinutes(20)
        );
        when(orderRepository.findByOrderNo("T-EXPIRED")).thenReturn(expired);
        when(orderRepository.cancelPending("T-EXPIRED", "超时或用户主动取消")).thenReturn(1);

        assertThatThrownBy(() -> service.payOrder("T-EXPIRED", Map.of("paymentMethod", "ALIPAY")))
                .isInstanceOf(ApiException.class)
                .hasMessage("订单已超时取消");

        verify(productClient).adjustStock("T-1", 1);
        verify(orderRepository, never()).markPaid(anyString(), anyString(), any(), any());
    }

    @Test
    void payOrderShouldPersistPaymentRecordAndAwardPoints() {
        OrderRepository.OrderRow pending = order(40L, "T-PAY", 2L, "TRAIN", "120.00", 0,
                "{\"productId\":\"T-1\"}");
        OrderRepository.OrderRow paid = order(40L, "T-PAY", 2L, "TRAIN", "120.00", 1,
                "{\"productId\":\"T-1\"}");
        when(orderRepository.findByOrderNo("T-PAY")).thenReturn(pending, paid);
        when(orderRepository.markPaid(eq("T-PAY"), eq("ALIPAY"), any(), any())).thenReturn(1);
        doNothing().when(userClient).adjustPoints(eq(2L), eq("award"), eq("40"), eq("TRAIN_PAY"), eq(new BigDecimal("120.00")));

        Map<String, Object> result = service.payOrder("T-PAY", Map.of("paymentMethod", "ALIPAY"));

        assertThat(result).containsEntry("orderNo", "T-PAY")
                .containsEntry("status", 1);
        verify(orderRepository).insertPaymentRecord(eq(40L), anyString(), eq("ALIPAY"), eq(new BigDecimal("120.00")), eq(1), any(), any());
        verify(userClient).adjustPoints(2L, "award", "40", "TRAIN_PAY", new BigDecimal("120.00"));
    }

    @Test
    void refundTrainOrderShouldRefundHalfWithinFifteenDays() {
        OrderRepository.OrderRow train = order(50L, "T-REFUND", 2L, "TRAIN", "200.00", 1,
                "{\"productId\":\"T-1\",\"date\":\"2026-09-05\",\"departTime\":\"08:00\"}");
        when(orderRepository.findByOrderNo("T-REFUND")).thenReturn(train);
        when(orderRepository.refundPaid(eq("T-REFUND"), any())).thenReturn(1);
        doNothing().when(productClient).adjustStock("T-1", 1);
        doNothing().when(userClient).adjustPoints(eq(2L), eq("revoke"), eq("50"), eq("TRAIN_REFUND"), eq(new BigDecimal("200.00")));

        Map<String, Object> result = service.refundTrainOrder("T-REFUND");

        assertThat(result).containsEntry("orderNo", "T-REFUND")
                .containsEntry("status", 4)
                .containsEntry("refundRule", "发车前十五天以内退还50%");
        assertThat(result.get("refundAmount")).isEqualTo(new BigDecimal("100.00"));
        verify(productClient).adjustStock("T-1", 1);
    }

    @Test
    void refundVacationOrderShouldRefundHalfWithoutInsurance() {
        OrderRepository.OrderRow order = order(60L, "V-1", 2L, "VACATION", "2000.00", 1,
                "{\"productId\":\"P-1\",\"travelerCount\":2,\"cancellationInsurance\":false}");
        when(orderRepository.findByOrderNo("V-1")).thenReturn(order);
        when(orderRepository.refundPaid(eq("V-1"), any())).thenReturn(1);
        doNothing().when(productClient).adjustStock("P-1", 2);
        doNothing().when(userClient).adjustPoints(eq(2L), eq("revoke"), eq("60"), eq("VACATION_REFUND"), any());

        Map<String, Object> result = service.refundVacationOrder("V-1");

        assertThat(result.get("refundAmount")).isEqualTo(new BigDecimal("1000.00"));
        verify(productClient).adjustStock("P-1", 2);
    }

    @Test
    void refundVacationOrderByPickupCodeShouldValidateFormat() {
        assertThatThrownBy(() -> service.refundVacationOrderByPickupCode("bad"))
                .isInstanceOf(ApiException.class)
                .hasMessage("请输入6位取票码");

        verify(orderRepository, never()).findByPickupCode(anyString());
    }

    @Test
    void changeTrainOrderShouldCreateNewPaidOrderAndMoveStock() {
        OrderRepository.OrderRow oldOrder = order(70L, "T-OLD", 2L, "TRAIN", "100.00", 1,
                "{\"productId\":\"T-OLD-P\",\"trainName\":\"G1\",\"startStation\":\"北京南\",\"endStation\":\"上海虹桥\",\"seatType\":\"二等座\",\"date\":\"2026-09-10\",\"departTime\":\"08:00\"}");
        when(orderRepository.findByOrderNo("T-OLD")).thenReturn(oldOrder);
        when(productClient.getProduct("T-NEW-P")).thenReturn(trainProduct("T-NEW-P", "G2", "150.00", 5, 0, "2026-09-10", "09:00"));
        doNothing().when(productClient).adjustStock("T-NEW-P", -1);
        doNothing().when(productClient).adjustStock("T-OLD-P", 1);
        when(orderRepository.markChanged("T-OLD", "已改签")).thenReturn(1);
        when(orderRepository.countByOrderNo(any())).thenReturn(0);
        when(orderRepository.countByPickupCode(any())).thenReturn(0);
        when(orderRepository.insertOrder(any())).thenReturn(71L);

        Map<String, Object> result = service.changeTrainOrder("T-OLD", "T-NEW-P");

        assertThat(result).containsEntry("oldOrderNo", "T-OLD")
                .containsEntry("oldPayAmount", new BigDecimal("100.00"))
                .containsEntry("newPayAmount", new BigDecimal("150.00"))
                .containsEntry("difference", new BigDecimal("50.00"))
                .containsEntry("differenceType", "PAY");
        verify(orderRepository).insertOrder(any());
    }

    @Test
    void changeTrainOrderShouldRejectChangingToSameProduct() {
        OrderRepository.OrderRow oldOrder = order(72L, "T-SAME", 2L, "TRAIN", "100.00", 1,
                "{\"productId\":\"T-1\",\"date\":\"2026-09-10\",\"departTime\":\"08:00\"}");
        when(orderRepository.findByOrderNo("T-SAME")).thenReturn(oldOrder);

        assertThatThrownBy(() -> service.changeTrainOrder("T-SAME", "T-1"))
                .isInstanceOf(ApiException.class)
                .hasMessage("不能改签到同一车次");

        verify(productClient, never()).adjustStock(anyString(), anyInt());
    }

    @Test
    void applyInvoiceShouldRejectDuplicateApplication() {
        OrderRepository.OrderRow hotel = order(80L, "H-1", 2L, "HOTEL", "500.00", 1,
                "{\"roomId\":\"101\"}");
        when(orderRepository.findById(80L)).thenReturn(hotel);
        when(orderRepository.invoiceExists(80L)).thenReturn(true);

        assertThatThrownBy(() -> service.applyInvoice(2L, 80L, Map.of("invoiceType", "PERSONAL", "title", "张三")))
                .isInstanceOf(ApiException.class)
                .hasMessage("invoice already applied");

        verify(orderRepository, never()).insertInvoiceApplication(anyLong(), anyLong(), anyString(), anyString(), any(), anyInt(), any());
    }

    @Test
    void createHotelReviewShouldRejectRepeatedReview() {
        OrderRepository.OrderRow hotel = order(90L, "H-2", 2L, "HOTEL", "500.00", 2,
                "{\"roomId\":\"101\"}");
        when(orderRepository.findById(90L)).thenReturn(hotel);
        when(orderRepository.reviewExists(90L)).thenReturn(true);

        assertThatThrownBy(() -> service.createHotelReview(2L, 90L, Map.of("rating", 5, "content", "不错")))
                .isInstanceOf(ApiException.class)
                .hasMessage("order already reviewed");

        verify(orderRepository, never()).insertReview(anyLong(), any(), anyString(), anyLong(), anyInt(), anyString(), anyString(), anyInt(), any());
    }

    @Test
    void adminUpdateOrderStatusShouldDelegateToRepository() {
        when(orderRepository.updateStatus("O-1", 4, "人工退款")).thenReturn(1);

        assertThat(service.updateOrderStatus("O-1", 4, "人工退款", 1L)).isTrue();
        verify(orderRepository).updateStatus("O-1", 4, "人工退款");
    }

    @Test
    void dispatchOutboxBatchShouldSendPointsAndMarkSent() {
        String payload = "{\"userId\":2,\"action\":\"award\",\"orderId\":\"100\",\"source\":\"TRAIN_PAY\",\"paidAmount\":88.50}";
        when(orderRepository.dueOutbox(20)).thenReturn(List.of(new OrderRepository.OutboxRow(
                1L, "TRAIN_PAY", "POINTS", "100", payload, 0, 0, FIXED_NOW, null, FIXED_NOW, FIXED_NOW
        )));

        service.dispatchOutboxBatch();

        verify(userClient).adjustPoints(eq(2L), eq("award"), eq("100"), eq("TRAIN_PAY"),
                argThat(amount -> amount != null && amount.compareTo(new BigDecimal("88.50")) == 0));
        verify(orderRepository).markOutboxSent(1L);
    }

    @Test
    void dispatchOutboxBatchShouldScheduleRetryWhenPointsCallFails() {
        String payload = "{\"userId\":2,\"action\":\"award\",\"orderId\":\"100\",\"source\":\"TRAIN_PAY\",\"paidAmount\":88.50}";
        when(orderRepository.dueOutbox(20)).thenReturn(List.of(new OrderRepository.OutboxRow(
                1L, "TRAIN_PAY", "POINTS", "100", payload, 2, 3, FIXED_NOW, null, FIXED_NOW, FIXED_NOW
        )));
        doThrow(new IllegalStateException("user down")).when(userClient)
                .adjustPoints(eq(2L), eq("award"), eq("100"), eq("TRAIN_PAY"), any());

        service.dispatchOutboxBatch();

        ArgumentCaptor<LocalDateTime> nextRetryCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).markOutboxRetry(eq(1L), eq(4), nextRetryCaptor.capture(), anyString());
        assertThat(nextRetryCaptor.getValue()).isEqualTo(FIXED_NOW.plusSeconds(30));
    }

    private Map<String, Object> product(String id, String name, String price, int stock, int soldCount) {
        return Map.of(
                "id", id,
                "name", name,
                "price", new BigDecimal(price),
                "stock", stock,
                "soldCount", soldCount
        );
    }

    private Map<String, Object> trainProduct(String id, String name, String price, int stock, int soldCount, String date, String departTime) {
        return Map.of(
                "id", id,
                "name", name,
                "price", new BigDecimal(price),
                "stock", stock,
                "soldCount", soldCount,
                "extra", Map.of(
                        "start_station", "北京南",
                        "end_station", "上海虹桥",
                        "date", date,
                        "depart_time", departTime,
                        "arrive_time", "12:00"
                )
        );
    }

    private OrderRepository.OrderRow order(long id, String orderNo, long userId, String orderType, String payAmount, int status, String extraInfo) {
        return new OrderRepository.OrderRow(
                id,
                orderNo,
                userId,
                orderType,
                new BigDecimal(payAmount),
                new BigDecimal(payAmount),
                status == 0 ? "UNPAID" : "MOCK_PAY",
                0,
                "PC",
                status,
                status == 0 ? FIXED_NOW.plusMinutes(10) : null,
                status == 0 ? null : FIXED_NOW,
                null,
                null,
                0,
                null,
                extraInfo,
                FIXED_NOW,
                FIXED_NOW
        );
    }
}
