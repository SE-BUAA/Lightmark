package top.ortus.lightmark.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.ortus.lightmark.backend.dao.Order;
import top.ortus.lightmark.backend.dao.OrderMapper;
import top.ortus.lightmark.backend.dao.Product;
import top.ortus.lightmark.backend.dao.ProductMapper;
import top.ortus.lightmark.backend.dto.AiDTO;
import top.ortus.lightmark.backend.dto.module.TrainOrderRequest;
import top.ortus.lightmark.backend.dto.module.TrainOrderResponse;
import top.ortus.lightmark.backend.dto.module.TrainRefundResponse;
import top.ortus.lightmark.backend.dto.module.VacationAssistantResponse;
import top.ortus.lightmark.backend.dto.module.VacationOrderRequest;
import top.ortus.lightmark.backend.dto.module.VacationRefundResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplUnitTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 6, 1, 10, 0);

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ConversationService conversationService;
    @Mock
    private TrainService trainService;
    @Mock
    private PointsMembershipService pointsMembershipService;

    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        service.setClock(Clock.fixed(
            FIXED_NOW.atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault()
        ));
    }

    @Test
    void createTrainOrderShouldApplyStudentDiscount() {
        Order inserted = createTrainOrder(trainRequest(20, true), trainProduct("100.00"));

        assertThat(inserted.getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(inserted.getPayAmount()).isEqualByComparingTo("60.00");
        assertThat(inserted.getStatus()).isZero();
        assertThat(inserted.getPayDeadline()).isEqualTo(FIXED_NOW.plusMinutes(10));
        assertThat(inserted.getExtraInfo()).contains("\"ticketType\":\"STUDENT\"");
    }

    @Test
    void createTrainOrderShouldApplyChildDiscountWhenNotStudent() {
        Order inserted = createTrainOrder(trainRequest(12, false), trainProduct("100.00"));

        assertThat(inserted.getPayAmount()).isEqualByComparingTo("80.00");
        assertThat(inserted.getExtraInfo()).contains("\"ticketType\":\"CHILD\"");
    }

    @Test
    void createTrainOrderShouldUseFullPriceForAdult() {
        Order inserted = createTrainOrder(trainRequest(18, false), trainProduct("100.00"));

        assertThat(inserted.getPayAmount()).isEqualByComparingTo("100.00");
        assertThat(inserted.getExtraInfo()).contains("\"ticketType\":\"ADULT\"");
    }

    @Test
    void createTrainOrderShouldRejectUnsupportedSeatType() {
        Product product = trainProduct("100.00");
        product.setCategoryTags(List.of("硬座"));
        when(productMapper.selectById("3")).thenReturn(product);

        assertThatThrownBy(() -> service.createTrainOrder(2L, trainRequest(20, false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("所选座位类型不适用于该车次");

        verify(orderMapper, never()).decrementStock(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void createTrainOrderShouldRejectSoldOutProduct() {
        when(productMapper.selectById("3")).thenReturn(trainProduct("100.00"));
        when(orderMapper.decrementStock("3", 1)).thenReturn(0);

        assertThatThrownBy(() -> service.createTrainOrder(2L, trainRequest(20, false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("余票不足，下单失败");
    }

    @Test
    void createVacationOrderShouldUseBaseAmountWithoutInsurance() {
        Order inserted = createVacationOrder(false, 2);

        assertThat(inserted.getTotalAmount()).isEqualByComparingTo("2000.00");
        assertThat(inserted.getPayAmount()).isEqualByComparingTo("2000.00");
        assertThat(inserted.getExtraInfo()).contains("\"cancellationInsurance\":false");
    }

    @Test
    void createVacationOrderShouldAddFivePercentInsurance() {
        Order inserted = createVacationOrder(true, 3);

        assertThat(inserted.getTotalAmount()).isEqualByComparingTo("3000.00");
        assertThat(inserted.getPayAmount()).isEqualByComparingTo("3150.00");
        assertThat(inserted.getExtraInfo()).contains("\"insuranceAmount\":150.00");
    }

    @Test
    void payOrderShouldFailWhenOrderMissingOrCanceled() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThatThrownBy(() -> service.payOrder("NO_ORDER"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("订单不存在");

        Order canceled = paidOrder("T1", "TRAIN", "100.00");
        canceled.setStatus(2);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(canceled);
        assertThatThrownBy(() -> service.payOrder("T1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("订单已取消，无法支付");
    }

    @Test
    void payOrderShouldBeIdempotentForPaidOrder() {
        Order paid = paidOrder("T1", "TRAIN", "100.00");
        paid.setPickupCode("ABC123");
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(paid);

        TrainOrderResponse response = service.payOrder("T1");

        assertThat(response.getPickupCode()).isEqualTo("ABC123");
        verify(orderMapper, never()).updateById(any());
        verify(pointsMembershipService, never()).awardPoints(any(), any(), any(), any());
    }

    @Test
    void payOrderShouldCancelExpiredPendingOrder() {
        Order pending = pendingOrder("T1", "TRAIN", "100.00");
        pending.setCreateTime(FIXED_NOW.minusMinutes(11));
        pending.setExtraInfo("{\"productId\":\"3\"}");
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pending, pending);
        when(orderMapper.cancelPendingOrder(eq("T1"), any())).thenReturn(1);

        assertThatThrownBy(() -> service.payOrder("T1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("订单已超时取消");

        verify(orderMapper).incrementStock("3", 1);
    }

    @Test
    void refundTrainOrderShouldRefundAllMoreThanFifteenDaysBeforeDeparture() {
        Order order = trainPaidOrderWithDeparture("2026-06-17", "10:00");
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(orderMapper.refundPaidOrder(eq("T1"), any())).thenReturn(1);

        TrainRefundResponse response = service.refundTrainOrder("T1");

        assertThat(response.getRefundAmount()).isEqualByComparingTo("100.00");
        assertThat(response.getRefundRule()).isEqualTo("发车前十五天以上全额退还");
        verify(orderMapper).incrementStock("3", 1);
        verify(pointsMembershipService).revokePoints("2", "10", "TRAIN_REFUND", new BigDecimal("100.00"));
    }

    @Test
    void refundTrainOrderShouldRefundHalfWithinFifteenDaysBeforeDeparture() {
        Order order = trainPaidOrderWithDeparture("2026-06-10", "10:00");
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(orderMapper.refundPaidOrder(eq("T1"), any())).thenReturn(1);

        TrainRefundResponse response = service.refundTrainOrder("T1");

        assertThat(response.getRefundAmount()).isEqualByComparingTo("50.00");
        assertThat(response.getRefundRule()).isEqualTo("发车前十五天以内退还50%");
    }

    @Test
    void refundTrainOrderByPickupCodeShouldRejectInvalidCodeBeforeDatabaseQuery() {
        assertThatThrownBy(() -> service.refundVacationOrderByPickupCode("BAD"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("请输入6位取票码");

        verify(orderMapper, never()).selectOne(any());
    }

    @Test
    void refundVacationOrderShouldRefundAllWhenInsured() {
        Order order = vacationPaidOrder(true);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(orderMapper.refundPaidOrder(eq("V1"), any())).thenReturn(1);

        VacationRefundResponse response = service.refundVacationOrder("V1");

        assertThat(response.getRefundAmount()).isEqualByComparingTo("2100.00");
        assertThat(response.getRefundRule()).isEqualTo("已购买取消险，全额退款");
        verify(orderMapper).incrementStock("201", 2);
        verify(pointsMembershipService).revokePoints("2", "20", "VACATION_REFUND", new BigDecimal("2100.00"));
    }

    @Test
    void refundVacationOrderShouldRefundHalfWhenUninsured() {
        Order order = vacationPaidOrder(false);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(orderMapper.refundPaidOrder(eq("V1"), any())).thenReturn(1);

        VacationRefundResponse response = service.refundVacationOrder("V1");

        assertThat(response.getRefundAmount()).isEqualByComparingTo("1050.00");
        assertThat(response.getRefundRule()).isEqualTo("未购买取消险，退还50%");
    }

    @Test
    void refundVacationOrderShouldRejectUnpaidAndRepeatedRefunds() {
        Order pending = pendingOrder("V1", "VACATION", "100.00");
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pending);
        assertThatThrownBy(() -> service.refundVacationOrder("V1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("只有已支付订单可以退订");

        Order refunded = vacationPaidOrder(false);
        refunded.setStatus(4);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(refunded);
        assertThatThrownBy(() -> service.refundVacationOrder("V1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("订单已退订");
    }

    @Test
    void generateVacationAssistantShouldRequirePaidVacationOrder() {
        Order train = paidOrder("T1", "TRAIN", "100.00");
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(train);
        assertThatThrownBy(() -> service.generateVacationAssistant("T1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("只支持度假订单生成智能行程助手建议");

        Order pending = pendingOrder("V1", "VACATION", "100.00");
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pending);
        assertThatThrownBy(() -> service.generateVacationAssistant("V1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("请支付成功后再使用智能行程助手");
    }

    @Test
    void generateVacationAssistantShouldUseAiContentOrFallbackOnFailure() {
        Order order = vacationPaidOrder(false);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        AiDTO aiDTO = new AiDTO();
        aiDTO.setContent("AI 建议");
        when(conversationService.chat(any(), any(), any())).thenReturn(aiDTO);

        VacationAssistantResponse aiResponse = service.generateVacationAssistant("V1");
        assertThat(aiResponse.getContent()).isEqualTo("AI 建议");

        when(conversationService.chat(any(), any(), any())).thenThrow(new RuntimeException("ai down"));
        VacationAssistantResponse fallback = service.generateVacationAssistant("V1");
        assertThat(fallback.getContent()).contains("智能行程助手建议", "三亚", "美食推荐");
    }

    private Order createTrainOrder(TrainOrderRequest request, Product product) {
        when(productMapper.selectById("3")).thenReturn(product);
        when(orderMapper.decrementStock("3", 1)).thenReturn(1);
        when(orderMapper.countByOrderNo(any())).thenReturn(0);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        service.createTrainOrder(2L, request);
        verify(orderMapper).insert(captor.capture());
        return captor.getValue();
    }

    private Order createVacationOrder(boolean insured, int travelerCount) {
        Product product = vacationProduct("1000.00");
        when(productMapper.selectById("201")).thenReturn(product);
        when(orderMapper.decrementStock("201", travelerCount)).thenReturn(1);
        when(orderMapper.countByOrderNo(any())).thenReturn(0);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        service.createVacationOrder(2L, vacationRequest(insured, travelerCount));
        verify(orderMapper).insert(captor.capture());
        return captor.getValue();
    }

    private TrainOrderRequest trainRequest(int age, boolean student) {
        TrainOrderRequest request = new TrainOrderRequest();
        request.setProductId("3");
        request.setPassengerName("张三");
        request.setPassengerPhone("13800000000");
        request.setPassengerAge(age);
        request.setSeatType("二等座");
        request.setIsStudent(student);
        return request;
    }

    private VacationOrderRequest vacationRequest(boolean insured, int travelerCount) {
        VacationOrderRequest request = new VacationOrderRequest();
        request.setProductId("201");
        request.setTravelerName("李四");
        request.setTravelerPhone("13900000000");
        request.setTravelerCount(travelerCount);
        request.setCancellationInsurance(insured);
        return request;
    }

    private Product trainProduct(String price) {
        Product product = new Product();
        product.setId("3");
        product.setProductType("TRAIN");
        product.setName("G1次");
        product.setPrice(Double.valueOf(price));
        product.setStatus(1);
        product.setCategoryTags(List.of("二等座", "一等座"));
        product.setExtra(Map.of(
            "start_station", "北京南",
            "end_station", "上海虹桥",
            "date", "2026-06-20",
            "depart_time", "08:00",
            "arrive_time", "12:00"
        ));
        return product;
    }

    private Product vacationProduct(String price) {
        Product product = new Product();
        product.setId("201");
        product.setProductType("VACATION");
        product.setName("三亚自由行");
        product.setPrice(Double.valueOf(price));
        product.setStatus(1);
        product.setExtra(Map.of(
            "destination", "三亚",
            "depart_city", "北京",
            "date", "2026-06-20",
            "days", 5,
            "hotel_level", "五星"
        ));
        return product;
    }

    private Order trainPaidOrderWithDeparture(String date, String departTime) {
        Order order = paidOrder("T1", "TRAIN", "100.00");
        order.setExtraInfo("{\"productId\":\"3\",\"date\":\"" + date + "\",\"departTime\":\"" + departTime + "\"}");
        return order;
    }

    private Order vacationPaidOrder(boolean insured) {
        Order order = paidOrder("V1", "VACATION", "2100.00");
        order.setId(20L);
        order.setExtraInfo("{\"productId\":\"201\",\"travelerCount\":2,\"cancellationInsurance\":" + insured
            + ",\"destination\":\"三亚\",\"date\":\"2026-06-20\",\"vacationName\":\"三亚自由行\",\"days\":5,\"departCity\":\"北京\"}");
        return order;
    }

    private Order paidOrder(String orderNo, String orderType, String payAmount) {
        Order order = pendingOrder(orderNo, orderType, payAmount);
        order.setStatus(1);
        return order;
    }

    private Order pendingOrder(String orderNo, String orderType, String payAmount) {
        Order order = new Order();
        order.setId(10L);
        order.setOrderNo(orderNo);
        order.setUserId(2L);
        order.setOrderType(orderType);
        order.setTotalAmount(new BigDecimal(payAmount));
        order.setPayAmount(new BigDecimal(payAmount));
        order.setStatus(0);
        order.setCreateTime(FIXED_NOW);
        order.setPayDeadline(FIXED_NOW.plusMinutes(10));
        return order;
    }
}
