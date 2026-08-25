package top.ortus.lightmark.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.ortus.lightmark.backend.dao.Order;
import top.ortus.lightmark.backend.dao.OrderMapper;
import top.ortus.lightmark.backend.dao.Product;
import top.ortus.lightmark.backend.dao.ProductMapper;
import top.ortus.lightmark.backend.dto.AiDTO;
import top.ortus.lightmark.backend.dto.module.TrainChangePreviewResponse;
import top.ortus.lightmark.backend.dto.module.TrainChangeResponse;
import top.ortus.lightmark.backend.dto.module.TrainOrderRequest;
import top.ortus.lightmark.backend.dto.module.TrainOrderResponse;
import top.ortus.lightmark.backend.dto.module.TrainSearchRequest;
import top.ortus.lightmark.backend.dto.module.TrainRefundResponse;
import top.ortus.lightmark.backend.dto.module.TrainTicketDTO;
import top.ortus.lightmark.backend.dto.module.VacationAssistantResponse;
import top.ortus.lightmark.backend.dto.module.VacationOrderRequest;
import top.ortus.lightmark.backend.dto.module.VacationRefundResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderServiceImpl implements OrderService {
    private static final int PENDING = 0;
    private static final int PAID = 1;
    private static final int CANCELED = 2;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private TrainService trainService;

    @Autowired
    private PointsMembershipService pointsMembershipService;

    private Clock clock = Clock.systemDefaultZone();

    void setClock(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrainOrderResponse createTrainOrder(Long userId, TrainOrderRequest request) {
        validateRequest(request);

        if (request.getProductId().startsWith("MCP:")) {
            return createRemoteTrainOrder(userId, request);
        }

        Product product = productMapper.selectById(request.getProductId());
        if (product == null || !"TRAIN".equals(product.getProductType()) || !Integer.valueOf(1).equals(product.getStatus())) {
            throw new IllegalArgumentException("火车票产品不存在或已下架");
        }
        if (product.getCategoryTags() == null || !product.getCategoryTags().contains(request.getSeatType())) {
            throw new IllegalArgumentException("所选座位类型不适用于该车次");
        }

        int updatedCount = orderMapper.decrementStock(request.getProductId(), 1);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("余票不足，下单失败");
        }

        LocalDateTime now = now();
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setOrderType("TRAIN");
        BigDecimal originalAmount = BigDecimal.valueOf(product.getPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal payAmount = calculatePayAmount(originalAmount, request);
        order.setTotalAmount(originalAmount);
        order.setPayAmount(payAmount);
        order.setPointsDeduct(0);
        order.setSource("PC");
        order.setStatus(PENDING);
        order.setPayDeadline(now.plusMinutes(10));
        order.setExtraInfo(writeExtraInfo(product, request));
        order.setCreateTime(now);
        order.setUpdateTime(now);
        orderMapper.insert(order);

        return toResponse(order);
    }

    private TrainOrderResponse createRemoteTrainOrder(Long userId, TrainOrderRequest request) {
        TrainTicketDTO ticket = trainService.searchById(request.getProductId());
        BigDecimal originalAmount;
        if (isTransferTicket(ticket)) {
            originalAmount = calculateTransferOriginalAmount(ticket, request);
        } else if (ticket.getSeats() == null || ticket.getSeats().getOrDefault(request.getSeatType(), 0) <= 0) {
            throw new IllegalArgumentException("所选座位类型余票不足");
        } else {
            originalAmount = BigDecimal.valueOf(
                ticket.getPrices() == null ? ticket.getPrice() : ticket.getPrices().getOrDefault(request.getSeatType(), ticket.getPrice())
            ).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal payAmount = calculatePayAmount(originalAmount, request);
        LocalDateTime now = now();

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setOrderType("TRAIN");
        order.setTotalAmount(originalAmount);
        order.setPayAmount(payAmount);
        order.setPointsDeduct(0);
        order.setSource("PC");
        order.setStatus(PENDING);
        order.setPayDeadline(now.plusMinutes(10));
        order.setExtraInfo(writeRemoteTrainExtraInfo(ticket, request, originalAmount));
        order.setCreateTime(now);
        order.setUpdateTime(now);
        orderMapper.insert(order);

        return toResponse(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrainOrderResponse createVacationOrder(Long userId, VacationOrderRequest request) {
        validateVacationRequest(request);

        Product product = productMapper.selectById(request.getProductId());
        if (product == null || !"VACATION".equals(product.getProductType()) || !Integer.valueOf(1).equals(product.getStatus())) {
            throw new IllegalArgumentException("度假产品不存在或已下架");
        }

        int travelerCount = request.getTravelerCount() == null ? 1 : request.getTravelerCount();
        int updatedCount = orderMapper.decrementStock(request.getProductId(), travelerCount);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("库存不足，下单失败");
        }

        LocalDateTime now = now();
        BigDecimal unitAmount = BigDecimal.valueOf(product.getPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal baseAmount = unitAmount.multiply(BigDecimal.valueOf(travelerCount)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal insuranceAmount = Boolean.TRUE.equals(request.getCancellationInsurance())
            ? baseAmount.multiply(BigDecimal.valueOf(0.05)).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setOrderType("VACATION");
        order.setTotalAmount(baseAmount);
        order.setPayAmount(baseAmount.add(insuranceAmount).setScale(2, RoundingMode.HALF_UP));
        order.setPointsDeduct(0);
        order.setSource("PC");
        order.setStatus(PENDING);
        order.setPayDeadline(now.plusMinutes(10));
        order.setExtraInfo(writeVacationExtraInfo(product, request, insuranceAmount));
        order.setCreateTime(now);
        order.setUpdateTime(now);
        orderMapper.insert(order);

        return toResponse(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrainOrderResponse payOrder(String orderNo) {
        Order order = getOrderByNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (order.getStatus() == CANCELED) {
            throw new IllegalArgumentException("订单已取消，无法支付");
        }
        if (order.getStatus() == PAID) {
            return toResponse(order);
        }
        if (order.getCreateTime().plusMinutes(10).isBefore(now())) {
            cancelOrder(orderNo);
            throw new IllegalArgumentException("订单已超时取消");
        }

        order.setStatus(PAID);
        order.setPaymentMethod("MOCK_PAY");
        order.setPayTime(now());
        order.setPickupCode(generatePickupCode());
        order.setUpdateTime(now());
        orderMapper.updateById(order);
        pointsMembershipService.awardPoints(String.valueOf(order.getUserId()), String.valueOf(order.getId()), paySource(order.getOrderType()), order.getPayAmount());
        return toResponse(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrainRefundResponse refundTrainOrder(String orderNo) {
        Order order = getOrderByNo(orderNo);
        return refundTrainOrder(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VacationRefundResponse refundVacationOrder(String orderNo) {
        Order order = getOrderByNo(orderNo);
        return refundVacationOrder(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VacationRefundResponse refundVacationOrderByPickupCode(String pickupCode) {
        if (pickupCode == null || !pickupCode.matches("^[A-Z0-9]{6}$")) {
            throw new IllegalArgumentException("请输入6位取票码");
        }
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getPickupCode, pickupCode);
        return refundVacationOrder(orderMapper.selectOne(wrapper));
    }

    @Override
    public VacationAssistantResponse generateVacationAssistant(String orderNo) {
        Order order = getOrderByNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!"VACATION".equals(order.getOrderType())) {
            throw new IllegalArgumentException("只支持度假订单生成智能行程助手建议");
        }
        if (order.getStatus() != PAID) {
            throw new IllegalArgumentException("请支付成功后再使用智能行程助手");
        }

        Map<String, Object> extraInfo = readExtraInfo(order);
        String destination = valueOf(extraInfo.get("destination"));
        String date = valueOf(extraInfo.get("date"));
        String vacationName = valueOf(extraInfo.get("vacationName"));
        String days = valueOf(extraInfo.get("days"));
        String departCity = valueOf(extraInfo.get("departCity"));
        if (destination == null || destination.isBlank()) {
            destination = "目的地";
        }
        if (date == null || date.isBlank()) {
            date = LocalDate.now(clock).toString();
        }

        String prompt = "请根据用户已购买的度假产品生成智能行程助手建议。产品为" + valueOrDefault(vacationName, "度假产品")
            + "，出发城市为" + valueOrDefault(departCity, "未填写")
            + "，目的地为" + destination
            + "，出行日期为" + date
            + "，行程天数为" + valueOrDefault(days, "未填写")
            + "。请提供今日天气、穿衣建议、美食推荐，中文输出，轻松友好，分点展示。";
        String content;
        try {
            AiDTO aiDTO = conversationService.chat(
                "vacation-assistant-" + order.getOrderNo() + "-" + System.nanoTime(),
                prompt,
                "你是智能行程助手。请直接给出目的地天气、穿衣建议和美食推荐。不要调用工具；如果没有实时天气数据，请基于出行日期和目的地给出合理提醒，并提示以当地实时天气预报为准。"
            );
            content = aiDTO == null ? "" : aiDTO.getContent();
        } catch (Exception ex) {
            content = "";
        }
        if (content == null || content.isBlank()) {
            content = fallbackVacationAssistant(destination, date);
        }
        return new VacationAssistantResponse(order.getOrderNo(), destination, date, content);
    }

    private VacationRefundResponse refundVacationOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!"VACATION".equals(order.getOrderType())) {
            throw new IllegalArgumentException("只能退度假订单");
        }
        if (order.getStatus() == 4) {
            throw new IllegalArgumentException("订单已退订");
        }
        if (order.getStatus() != PAID) {
            throw new IllegalArgumentException("只有已支付订单可以退订");
        }

        Map<String, Object> extraInfo = readExtraInfo(order);
        boolean insured = Boolean.parseBoolean(String.valueOf(extraInfo.get("cancellationInsurance")));
        BigDecimal refundAmount = insured
            ? order.getPayAmount()
            : order.getPayAmount().multiply(BigDecimal.valueOf(0.5)).setScale(2, RoundingMode.HALF_UP);
        String rule = insured ? "已购买取消险，全额退款" : "未购买取消险，退还50%";
        String reason = rule + "，退款金额：" + refundAmount;

        int changed = orderMapper.refundPaidOrder(order.getOrderNo(), reason);
        if (changed == 0) {
            throw new IllegalArgumentException("订单状态已变化，请刷新后重试");
        }
        String productId = valueOf(extraInfo.get("productId"));
        int travelerCount = parseInt(extraInfo.get("travelerCount"), 1);
        if (productId != null && !productId.isBlank()) {
            orderMapper.incrementStock(productId, travelerCount);
        }
        pointsMembershipService.revokePoints(String.valueOf(order.getUserId()), String.valueOf(order.getId()), "VACATION_REFUND", order.getPayAmount());
        return new VacationRefundResponse(order.getOrderNo(), 4, order.getPayAmount(), refundAmount, rule);
    }

    @Override
    public TrainChangePreviewResponse previewTrainChange(String orderNo) {
        Order order = validateChangeableOrder(orderNo);
        Map<String, Object> extraInfo = readExtraInfo(order);
        String productId = valueOf(extraInfo.get("productId"));
        String startStation = valueOf(extraInfo.get("startStation"));
        String endStation = valueOf(extraInfo.get("endStation"));
        String seatType = valueOf(extraInfo.get("seatType"));
        String origDate = valueOf(extraInfo.get("date"));
        String origTrain = valueOf(extraInfo.get("trainName"));

        // Get candidates from MCP/remote search for the same route
        List<Product> candidates = new ArrayList<>();
        try {
            TrainSearchRequest searchReq = new TrainSearchRequest();
            searchReq.setStartStation(startStation);
            searchReq.setEndStation(endStation);
            // Search the next 14 days for availability
            java.time.LocalDate today = java.time.LocalDate.now(clock);
            for (int i = 0; i < 14; i++) {
                String date = today.plusDays(i).toString();
                searchReq.setDate(date);
                try {
                    List<TrainTicketDTO> tickets = trainService.search(searchReq);
                    for (TrainTicketDTO ticket : tickets) {
                        if (ticket.getId() != null && !ticket.getId().equals(productId)) {
                            Product p = new Product();
                            p.setId(ticket.getId());
                            p.setName(ticket.getName());
                            p.setPrice(ticket.getPrice());
                            p.setStock(1);
                            p.setStatus(1);
                            // encode ticket into extra for the frontend
                            Map<String, Object> extra = new HashMap<>(ticket.getExtra() == null ? Map.of() : ticket.getExtra());
                            extra.put("date", date);
                            extra.put("seatType", seatType);
                            try {
                                p.setExtra(extra);
                            } catch (Exception ignored) {}
                            // Check seat availability
                            if (ticket.getSeats() != null && !ticket.getSeats().isEmpty()) {
                                Integer available = ticket.getSeats().get(seatType);
                                if (available != null && available > 0) {
                                    candidates.add(p);
                                }
                            } else {
                                candidates.add(p);
                            }
                        }
                    }
                } catch (Exception ignored) {}
                if (candidates.size() >= 50) break; // limit results
            }
        } catch (Exception ignored) {}

        // Also include DB products as fallback
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductType, "TRAIN")
            .eq(Product::getStatus, 1)
            .ne(Product::getId, productId)
            .orderByAsc(Product::getId);
        for (Product dbProduct : productMapper.selectList(wrapper)) {
            if (matchesExtra(dbProduct, "start_station", startStation) && matchesExtra(dbProduct, "end_station", endStation)
                && hasAvailableTicket(dbProduct) && hasSeatType(dbProduct, seatType)) {
                // deduplicate by name
                boolean dup = candidates.stream().anyMatch(c -> c.getName().equals(dbProduct.getName()) && c.getId().equals(dbProduct.getId()));
                if (!dup) candidates.add(dbProduct);
            }
        }

        return new TrainChangePreviewResponse(
            order.getOrderNo(),
            origTrain,
            startStation,
            endStation,
            seatType,
            candidates
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TrainChangeResponse changeTrainOrder(String orderNo, String targetProductId) {
        Order oldOrder = validateChangeableOrder(orderNo);
        if (targetProductId == null || targetProductId.isBlank()) {
            throw new IllegalArgumentException("请选择改签车次");
        }

        Map<String, Object> oldExtraInfo = readExtraInfo(oldOrder);
        String oldProductId = valueOf(oldExtraInfo.get("productId"));

        if (targetProductId.equals(oldProductId)) {
            throw new IllegalArgumentException("不能改签到同一车次");
        }

        LocalDateTime now = now();
        BigDecimal oldPayAmount = oldOrder.getPayAmount();
        BigDecimal newOriginalAmount;
        BigDecimal newPayAmount;
        Order newOrder = new Order();

        boolean isRemote = targetProductId.startsWith("MCP:");

        boolean needsPay;

        if (isRemote) {
            // --- Remote (MCP) change path ---
            TrainTicketDTO ticket = trainService.searchById(targetProductId);
            String seatType = valueOf(oldExtraInfo.get("seatType"));
            if (ticket.getSeats() != null && !ticket.getSeats().isEmpty()) {
                Integer available = ticket.getSeats().get(seatType);
                if (available == null || available <= 0) {
                    throw new IllegalArgumentException("新车次所选座位类型余票不足");
                }
            }
            newOriginalAmount = BigDecimal.valueOf(
                ticket.getPrices() == null ? ticket.getPrice() : ticket.getPrices().getOrDefault(seatType, ticket.getPrice())
            ).setScale(2, RoundingMode.HALF_UP);
            newPayAmount = newOriginalAmount
                .multiply(BigDecimal.valueOf(readDiscountRate(oldExtraInfo)))
                .setScale(2, RoundingMode.HALF_UP);

            needsPay = newPayAmount.compareTo(oldPayAmount) > 0;

            int markChanged = orderMapper.markChangedOrder(oldOrder.getOrderNo(), "已改签至远程车次");
            if (markChanged == 0) {
                throw new IllegalArgumentException("该订单已改签或状态已变化");
            }

            newOrder.setOrderNo(generateOrderNo());
            newOrder.setUserId(oldOrder.getUserId());
            newOrder.setOrderType("TRAIN");
            newOrder.setTotalAmount(newOriginalAmount);
            newOrder.setPayAmount(newPayAmount);
            newOrder.setPointsDeduct(0);
            newOrder.setPaymentMethod("CHANGE_PAY");
            newOrder.setSource(oldOrder.getSource());
            newOrder.setStatus(needsPay ? PENDING : PAID);
            if (!needsPay) newOrder.setPayTime(now);
            newOrder.setPayDeadline(needsPay ? now.plusMinutes(10) : null);
            newOrder.setPickupCode(generatePickupCode());
            newOrder.setChangedOnce(1);
            newOrder.setOriginalOrderNo(oldOrder.getOrderNo());

            Map<String, Object> newExtra = new LinkedHashMap<>(oldExtraInfo);
            newExtra.put("oldOrderNo", oldOrder.getOrderNo());
            newExtra.put("newTicketId", targetProductId);
            newExtra.put("newTrainName", ticket.getName());
            newExtra.put("newPrice", newPayAmount);
            java.util.Map<String, Object> ticketExtra = ticket.getExtra() == null ? Map.of() : ticket.getExtra();
            newExtra.put("newDate", ticketExtra.getOrDefault("date", ""));
            newExtra.put("newDepartTime", ticketExtra.getOrDefault("depart_time", ""));
            newExtra.put("newArriveTime", ticketExtra.getOrDefault("arrive_time", ""));
            try {
                newOrder.setExtraInfo(objectMapper.writeValueAsString(newExtra));
            } catch (Exception ex) {
                throw new IllegalArgumentException("改签信息序列化失败");
            }
            newOrder.setCreateTime(now);
            newOrder.setUpdateTime(now);
            orderMapper.insert(newOrder);
        } else {
            // --- Local DB product change path ---
            String startStation = valueOf(oldExtraInfo.get("startStation"));
            String endStation = valueOf(oldExtraInfo.get("endStation"));
            String seatType = valueOf(oldExtraInfo.get("seatType"));

            Product targetProduct = productMapper.selectById(targetProductId);
            if (targetProduct == null || !"TRAIN".equals(targetProduct.getProductType()) || !Integer.valueOf(1).equals(targetProduct.getStatus())) {
                throw new IllegalArgumentException("改签车次不存在或已下架");
            }
            if (!matchesExtra(targetProduct, "start_station", startStation) || !matchesExtra(targetProduct, "end_station", endStation)) {
                throw new IllegalArgumentException("只能改签出发站和到达站相同的车次");
            }
            if (!hasSeatType(targetProduct, seatType)) {
                throw new IllegalArgumentException("新车次无原座位类型");
            }

            newOriginalAmount = BigDecimal.valueOf(targetProduct.getPrice()).setScale(2, RoundingMode.HALF_UP);
            newPayAmount = newOriginalAmount
                .multiply(BigDecimal.valueOf(readDiscountRate(oldExtraInfo)))
                .setScale(2, RoundingMode.HALF_UP);

            needsPay = newPayAmount.compareTo(oldPayAmount) > 0;

            int markChanged = orderMapper.markChangedOrder(oldOrder.getOrderNo(), "已改签");
            if (markChanged == 0) {
                throw new IllegalArgumentException("该订单已改签或状态已变化");
            }
            int stockChanged = orderMapper.decrementStock(targetProductId, 1);
            if (stockChanged == 0) {
                throw new IllegalArgumentException("新车次余票不足");
            }
            if (oldProductId != null && !oldProductId.isBlank()) {
                orderMapper.incrementStock(oldProductId, 1);
            }

            newOrder.setOrderNo(generateOrderNo());
            newOrder.setUserId(oldOrder.getUserId());
            newOrder.setOrderType("TRAIN");
            newOrder.setTotalAmount(newOriginalAmount);
            newOrder.setPayAmount(newPayAmount);
            newOrder.setPointsDeduct(0);
            newOrder.setPaymentMethod("CHANGE_PAY");
            newOrder.setSource(oldOrder.getSource());
            newOrder.setStatus(needsPay ? PENDING : PAID);
            if (!needsPay) newOrder.setPayTime(now);
            newOrder.setPayDeadline(needsPay ? now.plusMinutes(10) : null);
            newOrder.setPickupCode(generatePickupCode());
            newOrder.setChangedOnce(1);
            newOrder.setOriginalOrderNo(oldOrder.getOrderNo());
            newOrder.setExtraInfo(writeChangedExtraInfo(targetProduct, oldExtraInfo, oldOrder.getOrderNo(), newPayAmount.subtract(oldPayAmount)));
            newOrder.setCreateTime(now);
            newOrder.setUpdateTime(now);
            orderMapper.insert(newOrder);
        }

        BigDecimal difference = newPayAmount.subtract(oldPayAmount).setScale(2, RoundingMode.HALF_UP);
        String differenceType = difference.compareTo(BigDecimal.ZERO) > 0 ? "PAY" : difference.compareTo(BigDecimal.ZERO) < 0 ? "REFUND" : "NONE";
        BigDecimal displayDifference = difference.abs();
        String message = switch (differenceType) {
            case "PAY" -> "需要补差价：" + displayDifference;
            case "REFUND" -> "已退差价：" + displayDifference;
            default -> "无需补差价";
        };
        return new TrainChangeResponse(
            oldOrder.getOrderNo(),
            newOrder.getOrderNo(),
            newOrder.getPickupCode(),
            oldPayAmount,
            newPayAmount,
            displayDifference,
            differenceType,
            message
        );
    }

    private TrainRefundResponse refundTrainOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!"TRAIN".equals(order.getOrderType())) {
            throw new IllegalArgumentException("只能退火车票订单");
        }
        if (order.getStatus() == 4) {
            throw new IllegalArgumentException("订单已退票");
        }
        if (order.getStatus() != PAID) {
            throw new IllegalArgumentException("只有已支付订单可以退票");
        }

        LocalDateTime departureTime = resolveDepartureTime(order);
        long daysBeforeDeparture = ChronoUnit.DAYS.between(now(), departureTime);
        boolean fullRefund = daysBeforeDeparture >= 15;
        BigDecimal refundAmount = order.getPayAmount()
            .multiply(fullRefund ? BigDecimal.ONE : BigDecimal.valueOf(0.5))
            .setScale(2, RoundingMode.HALF_UP);
        String rule = fullRefund ? "发车前十五天以上全额退还" : "发车前十五天以内退还50%";
        String reason = rule + "，退款金额=" + refundAmount;

        String productId = readProductId(order);
        int changed = orderMapper.refundPaidOrder(order.getOrderNo(), reason);
        if (changed == 0) {
            throw new IllegalArgumentException("订单状态已变化，请刷新后重试");
        }
        if (productId != null && !productId.isBlank()) {
            orderMapper.incrementStock(productId, 1);
        }
        pointsMembershipService.revokePoints(String.valueOf(order.getUserId()), String.valueOf(order.getId()), "TRAIN_REFUND", order.getPayAmount());
        return new TrainRefundResponse(order.getOrderNo(), 4, order.getPayAmount(), refundAmount, rule);
    }

    private String paySource(String orderType) {
        if ("VACATION".equals(orderType)) {
            return "VACATION_PAY";
        }
        return "TRAIN_PAY";
    }

    @Override
    public Order getOrderByNo(String orderNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        return orderMapper.selectOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo) {
        Order order = getOrderByNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (order.getStatus() == CANCELED) {
            return;
        }
        if (order.getStatus() != PENDING) {
            throw new IllegalArgumentException("只能取消待支付订单");
        }

        String productId = readProductId(order);
        int changed = orderMapper.cancelPendingOrder(orderNo, "超时或用户主动取消");
        if (changed == 0) {
            return;
        }
        if (productId != null && !productId.isBlank()) {
            orderMapper.incrementStock(productId, 1);
        }
    }

    private void validateRequest(TrainOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (request.getProductId() == null || request.getProductId().isBlank()) {
            throw new IllegalArgumentException("车次不能为空");
        }
        if (request.getPassengerName() == null || !request.getPassengerName().matches("^[\\u4e00-\\u9fa5]{2,20}$")) {
            throw new IllegalArgumentException("姓名必须为2-20个汉字");
        }
        if (request.getPassengerPhone() == null || !request.getPassengerPhone().matches("^1[3-9]\\d{9}$")) {
            throw new IllegalArgumentException("请输入正确的中国大陆手机号");
        }
        if (request.getPassengerAge() == null || request.getPassengerAge() < 1 || request.getPassengerAge() > 120) {
            throw new IllegalArgumentException("年龄必须为1-120之间的正整数");
        }
        boolean hasDirectSeatType = request.getSeatType() != null && !request.getSeatType().isBlank();
        boolean hasTransferSeatTypes = request.getTransferSeatTypes() != null
            && request.getTransferSeatTypes().size() >= 2
            && request.getTransferSeatTypes().stream().limit(2).allMatch(seat -> seat != null && !seat.isBlank());
        if (!hasDirectSeatType && !hasTransferSeatTypes) {
            throw new IllegalArgumentException("座位类型不能为空");
        }
    }

    private String writeExtraInfo(Product product, TrainOrderRequest request) {
        Map<String, Object> extraInfo = new LinkedHashMap<>();
        extraInfo.put("trainName", product.getName());
        extraInfo.put("startStation", product.getExtra() == null ? null : product.getExtra().get("start_station"));
        extraInfo.put("endStation", product.getExtra() == null ? null : product.getExtra().get("end_station"));
        extraInfo.put("date", product.getExtra() == null ? null : product.getExtra().get("date"));
        extraInfo.put("departTime", product.getExtra() == null ? null : product.getExtra().get("depart_time"));
        extraInfo.put("arriveTime", product.getExtra() == null ? null : product.getExtra().get("arrive_time"));
        extraInfo.put("seatType", request.getSeatType());
        extraInfo.put("ticketType", resolveTicketType(request));
        extraInfo.put("discountRate", resolveDiscountRate(request));
        extraInfo.put("originalPrice", product.getPrice());
        extraInfo.put("passengerName", request.getPassengerName());
        extraInfo.put("passengerPhone", request.getPassengerPhone());
        extraInfo.put("passengerAge", request.getPassengerAge());
        extraInfo.put("productId", request.getProductId());
        try {
            return objectMapper.writeValueAsString(extraInfo);
        } catch (Exception ex) {
            throw new IllegalArgumentException("订单信息序列化失败");
        }
    }

    private String writeRemoteTrainExtraInfo(TrainTicketDTO ticket, TrainOrderRequest request, BigDecimal originalAmount) {
        Map<String, Object> extraInfo = new LinkedHashMap<>();
        Map<String, Object> extra = ticket.getExtra() == null ? Map.of() : ticket.getExtra();
        extraInfo.put("trainName", ticket.getName());
        extraInfo.put("startStation", extra.get("start_station"));
        extraInfo.put("endStation", extra.get("end_station"));
        extraInfo.put("date", extra.get("date"));
        extraInfo.put("departTime", extra.get("depart_time"));
        extraInfo.put("arriveTime", extra.get("arrive_time"));
        extraInfo.put("duration", extra.get("duration"));
        extraInfo.put("trainType", extra.get("train_type"));
        extraInfo.put("transfer", Boolean.TRUE.equals(extra.get("transfer")));
        extraInfo.put("middleStation", extra.get("middle_station"));
        extraInfo.put("waitTime", extra.get("wait_time"));
        extraInfo.put("segments", extra.get("segments"));
        extraInfo.put("seatType", request.getSeatType());
        extraInfo.put("transferSeatTypes", request.getTransferSeatTypes());
        extraInfo.put("ticketType", resolveTicketType(request));
        extraInfo.put("discountRate", resolveDiscountRate(request));
        extraInfo.put("originalPrice", originalAmount);
        extraInfo.put("passengerName", request.getPassengerName());
        extraInfo.put("passengerPhone", request.getPassengerPhone());
        extraInfo.put("passengerAge", request.getPassengerAge());
        extraInfo.put("productId", null);
        extraInfo.put("remoteTicketId", request.getProductId());
        try {
            return objectMapper.writeValueAsString(extraInfo);
        } catch (Exception ex) {
            throw new IllegalArgumentException("订单信息序列化失败");
        }
    }

    private boolean isTransferTicket(TrainTicketDTO ticket) {
        return ticket != null
            && ticket.getExtra() != null
            && Boolean.TRUE.equals(ticket.getExtra().get("transfer"));
    }

    @SuppressWarnings("unchecked")
    private BigDecimal calculateTransferOriginalAmount(TrainTicketDTO ticket, TrainOrderRequest request) {
        List<String> seatTypes = request.getTransferSeatTypes();
        if (seatTypes == null || seatTypes.size() < 2) {
            throw new IllegalArgumentException("请选择两段车次的座位类型");
        }
        Object rawSegments = ticket.getExtra() == null ? null : ticket.getExtra().get("segments");
        if (!(rawSegments instanceof List<?> segments) || segments.size() < 2) {
            throw new IllegalArgumentException("中转车票信息无效");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < 2; i++) {
            Object rawSegment = segments.get(i);
            if (!(rawSegment instanceof Map<?, ?> segment)) {
                throw new IllegalArgumentException("中转车票信息无效");
            }
            String seatType = seatTypes.get(i);
            Map<String, Object> seats = segment.get("seats") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            int count = parseInt(seats.get(seatType), 0);
            if (count <= 0) {
                throw new IllegalArgumentException("第" + (i + 1) + "段所选座位类型余票不足");
            }
            Map<String, Object> prices = segment.get("prices") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            double price = parseDouble(prices.get(seatType), 0.0);
            if (price <= 0) {
                Object fallback = segment.get("price");
                price = parseDouble(fallback, 0.0);
            }
            total = total.add(BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateVacationRequest(VacationOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (request.getProductId() == null || request.getProductId().isBlank()) {
            throw new IllegalArgumentException("度假产品不能为空");
        }
        if (request.getTravelerName() == null || request.getTravelerName().isBlank() || request.getTravelerName().length() > 30) {
            throw new IllegalArgumentException("请填写1-30位出行人姓名");
        }
        if (request.getTravelerPhone() == null || !request.getTravelerPhone().matches("^1[3-9]\\d{9}$")) {
            throw new IllegalArgumentException("请输入正确的中国大陆手机号");
        }
        if (request.getTravelerCount() == null || request.getTravelerCount() < 1 || request.getTravelerCount() > 20) {
            throw new IllegalArgumentException("出行人数必须为1-20之间的正整数");
        }
    }

    private String writeVacationExtraInfo(Product product, VacationOrderRequest request, BigDecimal insuranceAmount) {
        Map<String, Object> extraInfo = new LinkedHashMap<>();
        extraInfo.put("productId", request.getProductId());
        extraInfo.put("vacationName", product.getName());
        extraInfo.put("destination", product.getExtra() == null ? null : product.getExtra().get("destination"));
        extraInfo.put("departCity", product.getExtra() == null ? null : product.getExtra().get("depart_city"));
        extraInfo.put("date", product.getExtra() == null ? null : product.getExtra().get("date"));
        extraInfo.put("days", product.getExtra() == null ? null : product.getExtra().get("days"));
        extraInfo.put("hotelLevel", product.getExtra() == null ? null : product.getExtra().get("hotel_level"));
        extraInfo.put("travelerName", request.getTravelerName());
        extraInfo.put("travelerPhone", request.getTravelerPhone());
        extraInfo.put("travelerCount", request.getTravelerCount());
        extraInfo.put("cancellationInsurance", Boolean.TRUE.equals(request.getCancellationInsurance()));
        extraInfo.put("insuranceAmount", insuranceAmount);
        extraInfo.put("unitPrice", product.getPrice());
        try {
            return objectMapper.writeValueAsString(extraInfo);
        } catch (Exception ex) {
            throw new IllegalArgumentException("度假订单信息序列化失败");
        }
    }

    private String readProductId(Order order) {
        if (order.getExtraInfo() == null || order.getExtraInfo().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> extraInfo = objectMapper.readValue(order.getExtraInfo(), new TypeReference<>() {});
            Object productId = extraInfo.get("productId");
            return productId == null ? null : String.valueOf(productId);
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDateTime resolveDepartureTime(Order order) {
        Map<String, Object> extraInfo = readExtraInfo(order);
        Object dateValue = extraInfo.get("date");
        if (dateValue == null || String.valueOf(dateValue).isBlank()) {
            throw new IllegalArgumentException("订单缺少发车日期，无法退票");
        }
        Object departTimeValue = extraInfo.get("departTime");
        String departTimeText = departTimeValue == null || String.valueOf(departTimeValue).isBlank()
            ? "00:00"
            : String.valueOf(departTimeValue);
        return LocalDateTime.of(LocalDate.parse(String.valueOf(dateValue)), LocalTime.parse(departTimeText));
    }

    private Map<String, Object> readExtraInfo(Order order) {
        if (order.getExtraInfo() == null || order.getExtraInfo().isBlank()) {
            throw new IllegalArgumentException("订单缺少车票信息，无法退票");
        }
        try {
            return objectMapper.readValue(order.getExtraInfo(), new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("订单车票信息解析失败，无法退票");
        }
    }

    private Order validateChangeableOrder(String orderNo) {
        Order order = getOrderByNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!"TRAIN".equals(order.getOrderType())) {
            throw new IllegalArgumentException("只能改签火车票订单");
        }
        if (order.getStatus() != PAID) {
            throw new IllegalArgumentException("只有已支付订单可以改签");
        }
        if (Integer.valueOf(1).equals(order.getChangedOnce())) {
            throw new IllegalArgumentException("该订单已改签过，不能再次改签");
        }
        return order;
    }

    private boolean matchesExtra(Product product, String key, String expected) {
        if (expected == null || expected.isBlank()) {
            return false;
        }
        Object actual = product.getExtra() == null ? null : product.getExtra().get(key);
        return expected.equals(String.valueOf(actual));
    }

    private boolean hasSeatType(Product product, String seatType) {
        return seatType != null
            && product.getCategoryTags() != null
            && product.getCategoryTags().contains(seatType);
    }

    private boolean hasAvailableTicket(Product product) {
        int stock = product.getStock() == null ? 0 : product.getStock();
        int soldCount = product.getSoldCount() == null ? 0 : product.getSoldCount();
        return stock - soldCount > 0;
    }

    private double readDiscountRate(Map<String, Object> extraInfo) {
        Object discountRate = extraInfo.get("discountRate");
        if (discountRate instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return discountRate == null ? 1.0 : Double.parseDouble(String.valueOf(discountRate));
        } catch (NumberFormatException ex) {
            return 1.0;
        }
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private double parseDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value).replace("¥", "").trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String writeChangedExtraInfo(Product product, Map<String, Object> oldExtraInfo, String oldOrderNo, BigDecimal difference) {
        Map<String, Object> extraInfo = new LinkedHashMap<>(oldExtraInfo);
        extraInfo.put("trainName", product.getName());
        extraInfo.put("startStation", product.getExtra() == null ? null : product.getExtra().get("start_station"));
        extraInfo.put("endStation", product.getExtra() == null ? null : product.getExtra().get("end_station"));
        extraInfo.put("date", product.getExtra() == null ? null : product.getExtra().get("date"));
        extraInfo.put("departTime", product.getExtra() == null ? null : product.getExtra().get("depart_time"));
        extraInfo.put("arriveTime", product.getExtra() == null ? null : product.getExtra().get("arrive_time"));
        extraInfo.put("originalPrice", product.getPrice());
        extraInfo.put("productId", product.getId());
        extraInfo.put("changedFromOrderNo", oldOrderNo);
        extraInfo.put("changeDifference", difference);
        try {
            return objectMapper.writeValueAsString(extraInfo);
        } catch (Exception ex) {
            throw new IllegalArgumentException("改签订单信息序列化失败");
        }
    }

    private String valueOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String fallbackVacationAssistant(String destination, String date) {
        return "智能行程助手建议\n"
            + "1. 今日天气：" + destination + "在" + date + "的天气请以当地实时预报为准，出门前留意气温、降雨和风力变化。\n"
            + "2. 穿衣建议：建议选择舒适透气的衣物和好走的鞋，随身带一件薄外套；如遇雨季或海边城市，可备雨具和防晒用品。\n"
            + "3. 美食推荐：优先尝试" + destination + "当地特色小吃、老字号餐厅和应季菜品，行程第一天可以安排轻松一点，把胃口留给夜市或商圈。";
    }

    private BigDecimal calculatePayAmount(BigDecimal originalAmount, TrainOrderRequest request) {
        return originalAmount.multiply(BigDecimal.valueOf(resolveDiscountRate(request))).setScale(2, RoundingMode.HALF_UP);
    }

    private double resolveDiscountRate(TrainOrderRequest request) {
        if (Boolean.TRUE.equals(request.getIsStudent())) {
            return 0.6;
        }
        if (request.getPassengerAge() != null && request.getPassengerAge() < 18) {
            return 0.8;
        }
        return 1.0;
    }

    private String resolveTicketType(TrainOrderRequest request) {
        if (Boolean.TRUE.equals(request.getIsStudent())) {
            return "STUDENT";
        }
        if (request.getPassengerAge() != null && request.getPassengerAge() < 18) {
            return "CHILD";
        }
        return "ADULT";
    }

    private TrainOrderResponse toResponse(Order order) {
        LocalDateTime createTime = order.getCreateTime();
        LocalDateTime deadline = order.getPayDeadline() == null ? (createTime == null ? null : createTime.plusMinutes(10)) : order.getPayDeadline();
        // Convert to epoch millis so countdown is timezone-agnostic
        Long expireEpochMs = null;
        if (deadline != null) {
            expireEpochMs = deadline.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        return new TrainOrderResponse(
            order.getOrderNo(),
            order.getStatus(),
            order.getPayAmount(),
            createTime,
            deadline,
            expireEpochMs,
            order.getPickupCode()
        );
    }

    private String generateOrderNo() {
        String dateStr = now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String orderNo;
        do {
            orderNo = dateStr + String.format("%06d", RANDOM.nextInt(1_000_000));
        } while (orderMapper.countByOrderNo(orderNo) > 0);
        return orderNo;
    }

    private String generatePickupCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
            }
            code = sb.toString();
        } while (orderMapper.countByPickupCode(code) > 0);
        return code;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
