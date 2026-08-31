package top.ortus.lightmark.order.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.ortus.lightmark.order.client.ProductClient;
import top.ortus.lightmark.order.client.UserClient;
import top.ortus.lightmark.order.repository.OrderRepository;
import top.ortus.lightmark.order.tools.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderServiceImpl implements OrderService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PAID = 1;
    private static final int STATUS_CANCELED = 2;
    private static final int STATUS_REFUNDED = 4;
    private static final int STATUS_CHANGED = 5;
    private static final BigDecimal POINT_RATE = new BigDecimal("0.01");
    private static final Set<String> PAYMENT_METHODS = Set.of("WECHAT", "ALIPAY", "POINTS", "MOCK_PAY", "UNIONPAY", "CASH");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final UserClient userClient;
    private final ObjectMapper objectMapper;
    private final int outboxBatchSize;
    private final int outboxRetryDelaySeconds;
    private Clock clock = Clock.systemDefaultZone();

    public OrderServiceImpl(OrderRepository orderRepository,
                            ProductClient productClient,
                            UserClient userClient,
                            ObjectMapper objectMapper,
                            @Value("${order.outbox.batch-size:20}") int outboxBatchSize,
                            @Value("${order.outbox.retry-delay-seconds:30}") int outboxRetryDelaySeconds) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.userClient = userClient;
        this.objectMapper = objectMapper;
        this.outboxBatchSize = outboxBatchSize;
        this.outboxRetryDelaySeconds = outboxRetryDelaySeconds;
    }

    void setClock(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    @Override
    public Map<String, Object> previewFlightOrder(Long userId, Map<String, Object> payload) {
        Map<String, Object> flight = getProduct(requiredText(payload, "productId"));
        int passengerCount = passengerCount(payload);
        ensureStockEnough(flight, passengerCount, "flight stock is insufficient");
        BigDecimal ticketAmount = priceOf(flight).multiply(BigDecimal.valueOf(passengerCount)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = decimal(payload == null ? null : payload.get("taxAmount"), BigDecimal.ZERO);
        BigDecimal serviceAmount = decimal(payload == null ? null : payload.get("serviceAmount"), BigDecimal.ZERO);
        BigDecimal pointsAmount = decimal(payload == null ? null : payload.get("pointsAmount"), BigDecimal.ZERO);
        BigDecimal totalAmount = ticketAmount.add(taxAmount).add(serviceAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal payAmount = totalAmount.subtract(pointsAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("productId", productId(flight));
        preview.put("flightName", textField(flight, "name", "flightName"));
        preview.put("passengerCount", passengerCount);
        preview.put("stockEnough", true);
        preview.put("cabin", text(payload == null ? null : payload.get("cabin")));
        preview.put("ticketAmount", ticketAmount);
        preview.put("taxAmount", taxAmount);
        preview.put("serviceAmount", serviceAmount);
        preview.put("pointsAmount", pointsAmount);
        preview.put("totalAmount", totalAmount);
        preview.put("payAmount", payAmount);
        preview.put("payDeadlineMinutes", 15);
        return preview;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createFlightOrder(Long userId, Map<String, Object> payload) {
        requireUser(userId);
        Map<String, Object> flight = getProduct(requiredText(payload, "productId"));
        int passengerCount = passengerCount(payload);
        ensureStockEnough(flight, passengerCount, "flight stock is insufficient");
        productClient.adjustStock(productId(flight), -passengerCount);
        try {
            Map<String, Object> preview = previewFlightOrder(userId, payload);
            LocalDateTime now = now();
            String orderNo = generateOrderNo();
            BigDecimal totalAmount = decimal(preview.get("totalAmount"), BigDecimal.ZERO);
            BigDecimal payAmount = decimal(preview.get("payAmount"), BigDecimal.ZERO);
            String passengerJson = toJson(passengerList(payload, passengerCount));
            Map<String, Object> extra = new LinkedHashMap<>(extraMap(flight));
            extra.put("productId", productId(flight));
            extra.put("flightName", textField(flight, "name", "flightName"));
            extra.put("passengerCount", passengerCount);
            extra.put("passengerList", passengerList(payload, passengerCount));
            extra.put("cabin", text(payload == null ? null : payload.get("cabin")));
            extra.put("insurance", truthy(payload == null ? null : payload.get("insurance")));
            extra.put("extraBaggage", truthy(payload == null ? null : payload.get("extraBaggage")));
            extra.put("seatSelection", truthy(payload == null ? null : payload.get("seatSelection")));
            extra.put("totalAmount", totalAmount);
            String extraInfo = toJson(extra);
            long orderId = orderRepository.insertOrder(new OrderRepository.OrderInsert(
                    orderNo, userId, "FLIGHT", totalAmount, parseNonNegativeInt(payload == null ? null : payload.get("pointsDeduct"), 0),
                    payAmount, "UNPAID", "WEB", STATUS_PENDING, now.plusMinutes(15), null, null, null,
                    extraInfo, 0, null, now, now
            ));
            orderRepository.insertFlightOrderDetail(orderId, productId(flight),
                    textField(flight, "flightNo", "flight_no", "flightNumber"),
                    sqlDate(first(extraMap(flight), "departureDate", "departure_date", "date"), LocalDate.now()),
                    passengerJson,
                    text(first(extraMap(flight), "baggage")),
                    truthy(payload == null ? null : payload.get("insurance")) ? 1 : 0);
            return toOrderResponse(orderRepository.findByOrderNo(orderNo));
        } catch (RuntimeException ex) {
            try {
                productClient.adjustStock(productId(flight), passengerCount);
            } catch (Exception ignore) {
            }
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createHotelOrder(Long userId, Map<String, Object> payload) {
        requireUser(userId);
        Map<String, Object> room = getProduct(requiredText(payload, "roomId"));
        int roomNum = parseNonNegativeInt(payload == null ? null : payload.get("roomNum"), 1);
        ensureStockEnough(room, roomNum, "hotel room stock is insufficient");
        LocalDate checkIn = parseDate(requiredText(payload, "checkInDate"));
        LocalDate checkOut = parseDate(requiredText(payload, "checkOutDate"));
        long nights = Math.max(1, ChronoUnit.DAYS.between(checkIn, checkOut));
        BigDecimal pricePerNight = priceOf(room);
        BigDecimal totalPrice = pricePerNight.multiply(BigDecimal.valueOf(nights)).multiply(BigDecimal.valueOf(roomNum)).setScale(2, RoundingMode.HALF_UP);
        int pointsDeducted = normalizePoints(parseNonNegativeInt(payload == null ? null : payload.get("pointsDeducted"), 0), totalPrice);
        BigDecimal payAmount = totalPrice.subtract(BigDecimal.valueOf(pointsDeducted).multiply(POINT_RATE)).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        productClient.adjustStock(productId(room), -roomNum);
        try {
            LocalDateTime now = now();
            String orderNo = generateOrderNo();
            String guestListJson = toJson(payload == null ? List.of() : payload.get("guestList"));
            Map<String, Object> extra = new LinkedHashMap<>(extraMap(room));
            extra.put("hotelId", textField(room, "hotelId", "hotel_id"));
            extra.put("hotelName", textField(room, "hotelName", "name"));
            extra.put("roomId", productId(room));
            extra.put("roomName", textField(room, "roomName", "name"));
            extra.put("checkInDate", checkIn.toString());
            extra.put("checkOutDate", checkOut.toString());
            extra.put("roomNum", roomNum);
            extra.put("guestList", payload == null ? List.of() : payload.get("guestList"));
            extra.put("totalPrice", totalPrice);
            extra.put("pointsDeducted", pointsDeducted);
            extra.put("payAmount", payAmount);
            long orderId = orderRepository.insertOrder(new OrderRepository.OrderInsert(
                    orderNo, userId, "HOTEL", totalPrice, pointsDeducted, payAmount,
                    text(payload == null ? null : payload.get("paymentMethod")), "PC",
                    STATUS_PENDING, now.plusMinutes(15), null, null, null, toJson(extra), 0, null, now, now
            ));
            orderRepository.insertHotelOrderDetail(orderId, parseLong(productId(room)), Date.valueOf(checkIn), Date.valueOf(checkOut), roomNum,
                    guestListJson, totalPrice, pointsDeducted, payAmount);
            if (pointsDeducted > 0) {
                enqueuePointsTask(userId, String.valueOf(orderId), "HOTEL_DEDUCT", payAmount.negate());
            }
            return Map.of("orderId", orderId, "payAmount", payAmount);
        } catch (RuntimeException ex) {
            try {
                productClient.adjustStock(productId(room), roomNum);
            } catch (Exception ignore) {
            }
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> payHotelOrder(Long userId, Long orderId, String paymentMethod) {
        Map<String, Object> row = requireHotelOrder(userId, orderId);
        int status = intValue(row.get("status"), STATUS_PENDING);
        if (status == STATUS_PAID || status == 2) {
            return toHotelOrderResponse(row);
        }
        if (status != STATUS_PENDING) {
            throw new ApiException(409, "order cannot be paid");
        }
        LocalDateTime deadline = localDateTime(row.get("pay_deadline"));
        if (deadline != null && now().isAfter(deadline)) {
            throw new ApiException(409, "order payment has expired");
        }
        int finalStatus = localDate(row.get("check_out_date")).isBefore(LocalDate.now(clock)) || localDate(row.get("check_out_date")).isEqual(LocalDate.now(clock))
                ? 2 : STATUS_PAID;
        int updated = orderRepository.markPaid(String.valueOf(row.get("order_no")), text(paymentMethod), now(), null, finalStatus);
        if (updated == 0) {
            throw new ApiException(409, "order cannot be paid");
        }
        enqueuePointsTask(userId, String.valueOf(orderId), "HOTEL_PAY", decimal(row.get("pay_amount"), BigDecimal.ZERO));
        return getHotelOrderDetail(userId, orderId);
    }

    @Override
    public Map<String, Object> listHotelOrders(Long userId, Integer status, Integer page, Integer size) {
        requireUser(userId);
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        int offset = (safePage - 1) * safeSize;
        List<OrderRepository.OrderRow> orders = orderRepository.listOrders(userId, "HOTEL", status, safeSize, offset);
        long total = orderRepository.countOrders(userId, "HOTEL", status);
        List<Map<String, Object>> records = new ArrayList<>();
        for (OrderRepository.OrderRow order : orders) {
            Map<String, Object> extra = readExtraInfo(order.extraInfo());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderId", order.id());
            item.put("orderNo", order.orderNo());
            item.put("hotelName", text(extra.get("hotelName"), "酒店订单"));
            item.put("roomName", text(extra.get("roomName"), "房型待确认"));
            item.put("checkInDate", text(extra.get("checkInDate"), LocalDate.now(clock).toString()));
            item.put("checkOutDate", text(extra.get("checkOutDate"), LocalDate.now(clock).plusDays(1).toString()));
            item.put("totalAmount", order.totalAmount());
            item.put("status", order.status());
            item.put("createTime", order.createTime());
            records.add(item);
        }
        return Map.of("total", total, "page", safePage, "size", safeSize, "records", records);
    }

    @Override
    public Map<String, Object> getHotelOrderDetail(Long userId, Long orderId) {
        Map<String, Object> row = requireHotelOrder(userId, orderId);
        Map<String, Object> detail = orderRepository.findHotelDetail(orderId);
        Map<String, Object> extra = readExtraInfo(String.valueOf(row.get("extra_info")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("orderNo", row.get("order_no"));
        result.put("hotelName", text(extra.get("hotelName"), "酒店订单"));
        result.put("roomName", text(extra.get("roomName"), "房型待确认"));
        result.put("checkInDate", extra.get("checkInDate"));
        result.put("checkOutDate", extra.get("checkOutDate"));
        result.put("roomNum", detail == null ? extra.get("roomNum") : detail.get("room_num"));
        result.put("totalAmount", row.get("total_amount"));
        result.put("payAmount", row.get("pay_amount"));
        result.put("status", row.get("status"));
        result.put("paymentMethod", row.get("payment_method"));
        result.put("createTime", row.get("create_time"));
        result.put("extraInfo", extra);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelHotelOrder(Long userId, Long orderId) {
        Map<String, Object> row = requireHotelOrder(userId, orderId);
        int status = intValue(row.get("status"), STATUS_PENDING);
        if (status == 3) {
            return;
        }
        if (status == 2) {
            throw new ApiException(409, "traveled orders cannot be canceled");
        }
        LocalDate checkIn = localDate(row.get("check_in_date"));
        LocalDate checkOut = localDate(row.get("check_out_date"));
        LocalDate today = LocalDate.now(clock);
        if (!today.isBefore(checkOut)) {
            throw new ApiException(409, "completed stays cannot be canceled");
        }
        BigDecimal fee = BigDecimal.ZERO;
        if (status == STATUS_PAID) {
            fee = calculateCancelFee(decimal(row.get("pay_amount"), BigDecimal.ZERO), today, checkIn);
        } else if (status != STATUS_PENDING) {
            throw new ApiException(409, "order cannot be canceled");
        }
        if (status == STATUS_PAID) {
            enqueuePointsTask(userId, String.valueOf(orderId), "HOTEL_REFUND", decimal(row.get("pay_amount"), BigDecimal.ZERO).negate());
        }
        orderRepository.updateStatus(String.valueOf(row.get("order_no")), 3, "hotel order canceled, fee=" + fee);
        Map<String, Object> detail = orderRepository.findHotelDetail(orderId);
        if (detail != null) {
            restoreStock(productIdFromHotelDetail(detail), intValue(detail.get("room_num"), 1));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyInvoice(Long userId, Long orderId, Map<String, Object> request) {
        Map<String, Object> row = requireHotelOrder(userId, orderId);
        int status = intValue(row.get("status"), STATUS_PENDING);
        if (status != STATUS_PAID && status != 2) {
            throw new ApiException(409, "only paid or traveled orders can apply invoice");
        }
        String invoiceType = requiredText(request, "invoiceType");
        String title = requiredText(request, "title");
        if (orderRepository.invoiceExists(orderId)) {
            throw new ApiException(409, "invoice already applied");
        }
        orderRepository.insertInvoiceApplication(orderId, userId, invoiceType.trim(), title.trim(), text(request == null ? null : request.get("taxNo")), 0, now());
    }

    @Override
    public List<Map<String, Object>> listHotelReviews(Long hotelId, Integer page, Integer size) {
        if (hotelId == null) {
            throw new ApiException(400, "hotelId is required");
        }
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        int offset = (safePage - 1) * safeSize;
        List<Map<String, Object>> records = orderRepository.listReviewsByProductId(hotelId, safeSize, offset);
        if (records.isEmpty()) {
            return List.of();
        }
        return records;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createHotelReview(Long userId, Long orderId, Map<String, Object> request) {
        requireUser(userId);
        if (request == null) {
            throw new ApiException(400, "review request is required");
        }
        int rating = parseNonNegativeInt(request.get("rating"), 0);
        if (rating < 1 || rating > 5) {
            throw new ApiException(400, "rating must be between 1 and 5");
        }
        String content = requiredText(request, "content");
        Map<String, Object> row = requireHotelOrder(userId, orderId);
        if (intValue(row.get("status"), STATUS_PENDING) != 2) {
            throw new ApiException(409, "only completed stays can be reviewed");
        }
        if (orderRepository.reviewExists(orderId)) {
            throw new ApiException(409, "order already reviewed");
        }
        Long productId = parseLong(row.get("product_id") == null ? productIdFromHotelDetail(orderRepository.findHotelDetail(orderId)) : row.get("product_id"));
        orderRepository.insertReview(orderId, productId, "HOTEL", userId, rating, content.trim(), toJson(request.getOrDefault("images", List.of())), 1, now());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", orderId);
        result.put("orderId", orderId);
        result.put("userId", userId);
        result.put("rating", rating);
        result.put("content", content.trim());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createTrainOrder(Long userId, Map<String, Object> request) {
        requireUser(userId);
        validateTrainRequest(request);
        Map<String, Object> product = getProduct(requiredText(request, "productId"));
        ensureStockEnough(product, 1, "余票不足，下单失败");
        productClient.adjustStock(productId(product), -1);
        try {
            LocalDateTime now = now();
            BigDecimal originalAmount = priceOf(product).setScale(2, RoundingMode.HALF_UP);
            BigDecimal payAmount = originalAmount.multiply(BigDecimal.valueOf(resolveDiscountRate(request))).setScale(2, RoundingMode.HALF_UP);
            String orderNo = generateOrderNo();
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("trainName", textField(product, "name", "trainName"));
            extra.put("startStation", valueFromExtra(product, "start_station"));
            extra.put("endStation", valueFromExtra(product, "end_station"));
            extra.put("date", valueFromExtra(product, "date"));
            extra.put("departTime", valueFromExtra(product, "depart_time"));
            extra.put("arriveTime", valueFromExtra(product, "arrive_time"));
            extra.put("seatType", requiredText(request, "seatType"));
            extra.put("ticketType", resolveTicketType(request));
            extra.put("discountRate", resolveDiscountRate(request));
            extra.put("originalPrice", originalAmount);
            extra.put("passengerName", requiredText(request, "passengerName"));
            extra.put("passengerPhone", requiredText(request, "passengerPhone"));
            extra.put("passengerAge", parseNonNegativeInt(request.get("passengerAge"), 0));
            extra.put("productId", productId(product));
            long orderId = orderRepository.insertOrder(new OrderRepository.OrderInsert(
                    orderNo, userId, "TRAIN", originalAmount, 0, payAmount, "UNPAID", "PC", STATUS_PENDING,
                    now.plusMinutes(10), null, null, null, toJson(extra), 0, null, now, now
            ));
            return toOrderResponse(orderRepository.findByOrderNo(orderNo));
        } catch (RuntimeException ex) {
            try {
                productClient.adjustStock(productId(product), 1);
            } catch (Exception ignore) {
            }
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createVacationOrder(Long userId, Map<String, Object> request) {
        requireUser(userId);
        validateVacationRequest(request);
        Map<String, Object> product = getProduct(requiredText(request, "productId"));
        int travelerCount = parseNonNegativeInt(request.get("travelerCount"), 1);
        ensureStockEnough(product, travelerCount, "库存不足，下单失败");
        productClient.adjustStock(productId(product), -travelerCount);
        try {
            LocalDateTime now = now();
            BigDecimal unitAmount = priceOf(product).setScale(2, RoundingMode.HALF_UP);
            BigDecimal baseAmount = unitAmount.multiply(BigDecimal.valueOf(travelerCount)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal insuranceAmount = truthy(request.get("cancellationInsurance"))
                    ? baseAmount.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            String orderNo = generateOrderNo();
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("productId", productId(product));
            extra.put("vacationName", textField(product, "name", "vacationName"));
            extra.put("destination", valueFromExtra(product, "destination"));
            extra.put("departCity", valueFromExtra(product, "depart_city"));
            extra.put("date", valueFromExtra(product, "date"));
            extra.put("days", valueFromExtra(product, "days"));
            extra.put("hotelLevel", valueFromExtra(product, "hotel_level"));
            extra.put("travelerName", requiredText(request, "travelerName"));
            extra.put("travelerPhone", requiredText(request, "travelerPhone"));
            extra.put("travelerCount", travelerCount);
            extra.put("cancellationInsurance", truthy(request.get("cancellationInsurance")));
            extra.put("insuranceAmount", insuranceAmount);
            extra.put("unitPrice", priceOf(product));
            long orderId = orderRepository.insertOrder(new OrderRepository.OrderInsert(
                    orderNo, userId, "VACATION", baseAmount, 0, baseAmount.add(insuranceAmount).setScale(2, RoundingMode.HALF_UP),
                    "UNPAID", "PC", STATUS_PENDING, now.plusMinutes(10), null, null, null, toJson(extra), 0, null, now, now
            ));
            return toOrderResponse(orderRepository.findByOrderNo(orderNo));
        } catch (RuntimeException ex) {
            try {
                productClient.adjustStock(productId(product), travelerCount);
            } catch (Exception ignore) {
            }
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> payOrder(String orderNo, Map<String, Object> request) {
        OrderRepository.OrderRow order = requireOrder(orderNo);
        if ("HOTEL".equalsIgnoreCase(order.orderType())) {
            throw new ApiException(400, "use hotel order payment endpoint");
        }
        if (order.status() == STATUS_CANCELED) {
            throw new ApiException(400, "订单已取消，无法支付");
        }
        if (order.status() == STATUS_PAID) {
            return toOrderResponse(order);
        }
        if (order.payDeadline() != null && now().isAfter(order.payDeadline())) {
            cancelOrder(orderNo);
            throw new ApiException(400, "订单已超时取消");
        }
        String paymentMethod = normalizePaymentMethod(text(request == null ? null : request.get("paymentMethod")), "MOCK_PAY");
        String pickupCode = needsPickupCode(order.orderType()) ? generatePickupCode() : null;
        int updated = orderRepository.markPaid(orderNo, paymentMethod, now(), pickupCode);
        if (updated == 0) {
            throw new ApiException(409, "order is not payable");
        }
        orderRepository.insertPaymentRecord(order.id(), generateTransactionId(), paymentMethod, order.payAmount(), 1, now(), now());
        enqueuePointsTask(order.userId(), String.valueOf(order.id()), paySource(order.orderType()), order.payAmount());
        return toOrderResponse(requireOrder(orderNo));
    }

    @Override
    public Map<String, Object> refundOrder(String orderNo) {
        OrderRepository.OrderRow order = requireOrder(orderNo);
        return switch (order.orderType().toUpperCase(Locale.ROOT)) {
            case "FLIGHT" -> refundFlightOrder(orderNo);
            case "HOTEL" -> refundHotelOrder(orderNo);
            case "TRAIN" -> refundTrainOrder(orderNo);
            case "VACATION" -> refundVacationOrder(orderNo);
            default -> throw new ApiException(400, "unsupported order type");
        };
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> refundFlightOrder(String orderNo) {
        OrderRepository.OrderRow order = requireOrder(orderNo);
        if (!"FLIGHT".equalsIgnoreCase(order.orderType())) {
            throw new ApiException(400, "只能退机票订单");
        }
        if (order.status() != STATUS_PAID) {
            throw new ApiException(400, "order is not refundable");
        }
        Map<String, Object> detail = requireFlightDetail(order.id());
        Map<String, Object> flight = getProduct(String.valueOf(detail.get("product_id")));
        Map<String, Object> refundInfo = buildFlightRefundInfo(order, flight);
        int updated = orderRepository.refundPaid(orderNo, "flight refund");
        if (updated == 0) {
            throw new ApiException(409, "order is not refundable");
        }
        restoreStock(productId(flight), parsePassengerCount(String.valueOf(detail.get("passenger_list"))));
        enqueuePointsTask(order.userId(), String.valueOf(order.id()), "FLIGHT_REFUND", order.payAmount().negate());
        Map<String, Object> result = new LinkedHashMap<>(refundInfo);
        result.put("orderNo", orderNo);
        result.put("status", STATUS_REFUNDED);
        result.put("statusText", "已退款");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> refundHotelOrder(String orderNo) {
        OrderRepository.OrderRow order = requireOrder(orderNo);
        if (!"HOTEL".equalsIgnoreCase(order.orderType())) {
            throw new ApiException(400, "只能退酒店订单");
        }
        if (order.status() == 3) {
            throw new ApiException(400, "订单已退订");
        }
        if (order.status() != STATUS_PAID) {
            throw new ApiException(400, "只有已支付订单可以退订");
        }
        Map<String, Object> detail = requireHotelDetail(order.id());
        LocalDate checkIn = localDate(detail.get("check_in_date"));
        LocalDate today = LocalDate.now(clock);
        BigDecimal fee = calculateCancelFee(decimal(order.payAmount(), BigDecimal.ZERO), today, checkIn);
        BigDecimal refundAmount = decimal(order.payAmount(), BigDecimal.ZERO).subtract(fee).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        int updated = orderRepository.refundPaid(orderNo, "hotel refund");
        if (updated == 0) {
            throw new ApiException(409, "order status changed");
        }
        restoreStock(String.valueOf(detail.get("room_id")), intValue(detail.get("room_num"), 1));
        enqueuePointsTask(order.userId(), String.valueOf(order.id()), "HOTEL_REFUND", order.payAmount().negate());
        return Map.of(
                "orderNo", orderNo,
                "status", STATUS_REFUNDED,
                "originalAmount", order.payAmount(),
                "refundAmount", refundAmount,
                "refundRule", fee.compareTo(BigDecimal.ZERO) == 0 ? "免费取消" : "按酒店取消规则扣除手续费"
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> refundTrainOrder(String orderNo) {
        OrderRepository.OrderRow order = requireOrder(orderNo);
        if (!"TRAIN".equalsIgnoreCase(order.orderType())) {
            throw new ApiException(400, "只能退火车票订单");
        }
        if (order.status() == STATUS_REFUNDED) {
            throw new ApiException(400, "订单已退票");
        }
        if (order.status() != STATUS_PAID) {
            throw new ApiException(400, "只有已支付订单可以退票");
        }
        Map<String, Object> extra = readExtraInfo(order.extraInfo());
        LocalDateTime departureTime = resolveDepartureTime(extra);
        long daysBeforeDeparture = ChronoUnit.DAYS.between(now(), departureTime);
        boolean fullRefund = daysBeforeDeparture >= 15;
        BigDecimal refundAmount = order.payAmount().multiply(fullRefund ? BigDecimal.ONE : BigDecimal.valueOf(0.5)).setScale(2, RoundingMode.HALF_UP);
        String rule = fullRefund ? "发车前十五天以上全额退还" : "发车前十五天以内退还50%";
        int changed = orderRepository.refundPaid(orderNo, rule + "，退款金额=" + refundAmount);
        if (changed == 0) {
            throw new ApiException(409, "订单状态已变化，请刷新后重试");
        }
        restoreStock(valueOf(extra.get("productId")), 1);
        enqueuePointsTask(order.userId(), String.valueOf(order.id()), "TRAIN_REFUND", order.payAmount().negate());
        return Map.of("orderNo", orderNo, "status", STATUS_REFUNDED, "originalAmount", order.payAmount(), "refundAmount", refundAmount, "refundRule", rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> refundVacationOrder(String orderNo) {
        OrderRepository.OrderRow order = requireOrder(orderNo);
        if (!"VACATION".equalsIgnoreCase(order.orderType())) {
            throw new ApiException(400, "只能退度假订单");
        }
        if (order.status() == STATUS_REFUNDED) {
            throw new ApiException(400, "订单已退订");
        }
        if (order.status() != STATUS_PAID) {
            throw new ApiException(400, "只有已支付订单可以退订");
        }
        Map<String, Object> extra = readExtraInfo(order.extraInfo());
        boolean insured = truthy(extra.get("cancellationInsurance"));
        BigDecimal refundAmount = insured ? order.payAmount() : order.payAmount().multiply(BigDecimal.valueOf(0.5)).setScale(2, RoundingMode.HALF_UP);
        String rule = insured ? "已购买取消险，全额退款" : "未购买取消险，退还50%";
        int changed = orderRepository.refundPaid(orderNo, rule + "，退款金额：" + refundAmount);
        if (changed == 0) {
            throw new ApiException(409, "订单状态已变化，请刷新后重试");
        }
        restoreStock(valueOf(extra.get("productId")), parseNonNegativeInt(extra.get("travelerCount"), 1));
        enqueuePointsTask(order.userId(), String.valueOf(order.id()), "VACATION_REFUND", order.payAmount().negate());
        return Map.of("orderNo", orderNo, "status", STATUS_REFUNDED, "originalAmount", order.payAmount(), "refundAmount", refundAmount, "refundRule", rule);
    }

    @Override
    public Map<String, Object> refundVacationOrderByPickupCode(String pickupCode) {
        if (pickupCode == null || !pickupCode.matches("^[A-Z0-9]{6}$")) {
            throw new ApiException(400, "请输入6位取票码");
        }
        OrderRepository.OrderRow order = orderRepository.findByOrderNo(findByPickupCode(pickupCode));
        if (order == null || !"VACATION".equalsIgnoreCase(order.orderType())) {
            throw new ApiException(400, "只能退度假订单");
        }
        return refundVacationOrder(order.orderNo());
    }

    @Override
    public Map<String, Object> generateVacationAssistant(String orderNo) {
        OrderRepository.OrderRow order = requireOrder(orderNo);
        if (!"VACATION".equalsIgnoreCase(order.orderType())) {
            throw new ApiException(400, "只支持度假订单生成智能行程助手建议");
        }
        if (order.status() != STATUS_PAID) {
            throw new ApiException(400, "请支付成功后再使用智能行程助手");
        }
        Map<String, Object> extra = readExtraInfo(order.extraInfo());
        String destination = text(extra.get("destination"), "目的地");
        String date = text(extra.get("date"), LocalDate.now(clock).toString());
        String content = "智能行程助手建议\n"
                + "1. 今日天气：" + destination + "在" + date + "的天气请以当地实时预报为准。\n"
                + "2. 穿衣建议：建议选择舒适透气的衣物和好走的鞋，随身带一件薄外套。\n"
                + "3. 美食推荐：优先尝试" + destination + "当地特色小吃、老字号餐厅和应季菜品。";
        return Map.of("orderNo", orderNo, "destination", destination, "date", date, "content", content);
    }

    @Override
    public Map<String, Object> previewTrainChange(String orderNo) {
        OrderRepository.OrderRow order = requireChangeableTrainOrder(orderNo);
        Map<String, Object> extra = readExtraInfo(order.extraInfo());
        String startStation = text(extra.get("startStation"), "");
        String endStation = text(extra.get("endStation"), "");
        String seatType = text(extra.get("seatType"), "");
        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(Map.of(
                "id", valueOf(extra.get("productId")),
                "name", text(extra.get("trainName"), "车次"),
                "startStation", startStation,
                "endStation", endStation,
                "seatType", seatType,
                "price", order.payAmount()
        ));
        return Map.of(
                "orderNo", orderNo,
                "trainName", text(extra.get("trainName"), ""),
                "startStation", startStation,
                "endStation", endStation,
                "seatType", seatType,
                "candidates", candidates
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> changeTrainOrder(String orderNo, String targetProductId) {
        OrderRepository.OrderRow oldOrder = requireChangeableTrainOrder(orderNo);
        if (targetProductId == null || targetProductId.isBlank()) {
            throw new ApiException(400, "请选择改签车次");
        }
        Map<String, Object> oldExtra = readExtraInfo(oldOrder.extraInfo());
        String oldProductId = valueOf(oldExtra.get("productId"));
        if (targetProductId.equals(oldProductId)) {
            throw new ApiException(400, "不能改签到同一车次");
        }
        Map<String, Object> target = getProduct(targetProductId);
        ensureStockEnough(target, 1, "新车次余票不足");
        productClient.adjustStock(targetProductId, -1);
        productClient.adjustStock(oldProductId, 1);
        try {
            LocalDateTime now = now();
            BigDecimal newPayAmount = priceOf(target).setScale(2, RoundingMode.HALF_UP);
            BigDecimal oldPayAmount = oldOrder.payAmount();
            BigDecimal difference = newPayAmount.subtract(oldPayAmount).setScale(2, RoundingMode.HALF_UP);
            int markChanged = orderRepository.markChanged(orderNo, "已改签");
            if (markChanged == 0) {
                throw new ApiException(409, "该订单已改签或状态已变化");
            }
            String newOrderNo = generateOrderNo();
            Map<String, Object> extra = new LinkedHashMap<>(oldExtra);
            extra.put("trainName", textField(target, "name", "trainName"));
            extra.put("productId", targetProductId);
            extra.put("changedFromOrderNo", orderNo);
            extra.put("changeDifference", difference);
            long newOrderId = orderRepository.insertOrder(new OrderRepository.OrderInsert(
                    newOrderNo, oldOrder.userId(), "TRAIN", newPayAmount, 0, newPayAmount, "CHANGE_PAY", oldOrder.source(),
                    STATUS_PAID, null, now, null, null, toJson(extra), 1, orderNo, now, now
            ));
            return Map.of(
                    "oldOrderNo", orderNo,
                    "newOrderNo", newOrderNo,
                    "pickupCode", generatePickupCode(),
                    "oldPayAmount", oldPayAmount,
                    "newPayAmount", newPayAmount,
                    "difference", difference.abs(),
                    "differenceType", difference.compareTo(BigDecimal.ZERO) > 0 ? "PAY" : difference.compareTo(BigDecimal.ZERO) < 0 ? "REFUND" : "NONE",
                    "message", difference.compareTo(BigDecimal.ZERO) > 0 ? "需要补差价：" + difference.abs() : difference.compareTo(BigDecimal.ZERO) < 0 ? "已退差价：" + difference.abs() : "无需补差价"
            );
        } catch (RuntimeException ex) {
            try {
                productClient.adjustStock(targetProductId, 1);
                if (oldProductId != null && !oldProductId.isBlank()) {
                    productClient.adjustStock(oldProductId, -1);
                }
            } catch (Exception ignore) {
            }
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> changeFlightOrder(String orderNo, String targetProductId) {
        OrderRepository.OrderRow oldOrder = requireFlightOrder(orderNo);
        Map<String, Object> detail = requireFlightDetail(oldOrder.id());
        Map<String, Object> oldFlight = getProduct(String.valueOf(detail.get("product_id")));
        Map<String, Object> target = getProduct(targetProductId);
        int passengerCount = parsePassengerCount(String.valueOf(detail.get("passenger_list")));
        ensureStockEnough(target, passengerCount, "target flight stock is insufficient");
        productClient.adjustStock(targetProductId, -passengerCount);
        productClient.adjustStock(productId(oldFlight), passengerCount);
        try {
            BigDecimal oldPayAmount = oldOrder.payAmount();
            BigDecimal newPrice = priceOf(target).multiply(BigDecimal.valueOf(passengerCount)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal difference = newPrice.subtract(oldPayAmount).setScale(2, RoundingMode.HALF_UP);
            String newOrderNo = generateOrderNo();
            orderRepository.markChanged(orderNo, "已改签至 " + newOrderNo);
            orderRepository.insertOrder(new OrderRepository.OrderInsert(
                    newOrderNo, oldOrder.userId(), "FLIGHT", newPrice, 0, newPrice, "CHANGE_PAY", "WEB",
                    STATUS_PAID, null, now(), null, null, toJson(Map.of("changedFromOrderNo", orderNo, "productId", targetProductId)), 1, orderNo, now(), now()
            ));
            return Map.of(
                    "oldOrderNo", orderNo,
                    "newOrderNo", newOrderNo,
                    "oldPayAmount", oldPayAmount,
                    "newPayAmount", newPrice,
                    "difference", difference.abs(),
                    "message", difference.compareTo(BigDecimal.ZERO) > 0 ? "改签成功，需补差价 ¥" + difference.abs()
                            : difference.compareTo(BigDecimal.ZERO) < 0 ? "改签成功，已退差价 ¥" + difference.abs()
                            : "改签成功，无需补差价"
            );
        } catch (RuntimeException ex) {
            try {
                productClient.adjustStock(targetProductId, passengerCount);
                productClient.adjustStock(productId(oldFlight), -passengerCount);
            } catch (Exception ignore) {
            }
            throw ex;
        }
    }

    @Override
    public Map<String, Object> getOrderByNo(String orderNo) {
        return toOrderResponse(requireOrder(orderNo));
    }

    @Override
    public Map<String, Object> listOrders(Long userId, String orderType, Integer status, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        int offset = (safePage - 1) * safeSize;
        List<OrderRepository.OrderRow> rows = orderRepository.listOrders(userId, orderType, status, safeSize, offset);
        long total = orderRepository.countOrders(userId, orderType, status);
        List<Map<String, Object>> records = new ArrayList<>();
        for (OrderRepository.OrderRow row : rows) {
            records.add(toOrderResponse(row));
        }
        return Map.of("total", total, "page", safePage, "size", safeSize, "records", records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo) {
        OrderRepository.OrderRow order = requireOrder(orderNo);
        if (order.status() == STATUS_CANCELED) {
            return;
        }
        if (order.status() != STATUS_PENDING) {
            throw new ApiException(400, "只能取消待支付订单");
        }
        if (orderRepository.cancelPending(orderNo, "超时或用户主动取消") == 0) {
            return;
        }
        if ("FLIGHT".equalsIgnoreCase(order.orderType())) {
            Map<String, Object> detail = requireFlightDetail(order.id());
            restoreStock(valueOf(detail.get("product_id")), parsePassengerCount(String.valueOf(detail.get("passenger_list"))));
        } else if ("HOTEL".equalsIgnoreCase(order.orderType())) {
            Map<String, Object> detail = requireHotelDetail(order.id());
            restoreStock(valueOf(detail.get("room_id")), intValue(detail.get("room_num"), 1));
        } else {
            Map<String, Object> extra = readExtraInfo(order.extraInfo());
            restoreStock(valueOf(extra.get("productId")), parseQuantityForOrder(order, extra));
        }
    }

    @Override
    public Map<String, Object> orderStatus(String orderNo) {
        OrderRepository.OrderRow order = requireOrder(orderNo);
        return Map.of(
                "orderNo", order.orderNo(),
                "orderType", order.orderType(),
                "payAmount", order.payAmount(),
                "paymentMethod", order.paymentMethod() == null ? "" : order.paymentMethod(),
                "status", order.status(),
                "statusText", statusText(order.status()),
                "payDeadline", order.payDeadline() == null ? "" : order.payDeadline().toString(),
                "payTime", order.payTime() == null ? "" : order.payTime().toString(),
                "cancelReason", order.cancelReason() == null ? "" : order.cancelReason()
        );
    }

    @Override
    public boolean paymentCallback(Map<String, Object> payload) {
        payOrder(requiredText(payload, "orderNo"), payload);
        return true;
    }

    @Override
    public Map<String, Object> adminListOrders(Integer status, Integer page) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int offset = (currentPage - 1) * 20;
        List<OrderRepository.OrderRow> rows = orderRepository.listOrders(null, null, status, 20, offset);
        long total = orderRepository.countOrders(null, null, status);
        return Map.of("total", total, "page", currentPage, "size", 20, "records", rows.stream().map(this::toOrderResponse).toList());
    }

    @Override
    public boolean updateOrderStatus(String orderNo, int status, String remark, Long adminId) {
        return orderRepository.updateStatus(orderNo, status, remark) > 0;
    }

    @Override
    public boolean refundOrder(String orderNo, String remark, Long adminId) {
        OrderRepository.OrderRow order = requireOrder(orderNo);
        if (order.status() != STATUS_PAID) {
            return false;
        }
        Map<String, Object> result = switch (order.orderType().toUpperCase(Locale.ROOT)) {
            case "FLIGHT" -> refundFlightOrder(orderNo);
            case "HOTEL" -> refundHotelOrder(orderNo);
            case "TRAIN" -> refundTrainOrder(orderNo);
            case "VACATION" -> refundVacationOrder(orderNo);
            default -> throw new ApiException(400, "unsupported order type");
        };
        return result != null;
    }

    @Scheduled(fixedDelayString = "${order.outbox.poll-delay-ms:5000}")
    public void dispatchOutboxBatch() {
        List<OrderRepository.OutboxRow> rows = orderRepository.dueOutbox(outboxBatchSize);
        for (OrderRepository.OutboxRow row : rows) {
            try {
                Map<String, Object> payload = readMap(row.payload());
                Long userId = parseLong(payload.get("userId"));
                String action = text(payload.get("action"), "award");
                String orderId = text(payload.get("orderId"), row.aggregateId());
                String source = text(payload.get("source"), row.eventType());
                BigDecimal paidAmount = decimal(payload.get("paidAmount"), BigDecimal.ZERO);
                if (userId != null) {
                    userClient.adjustPoints(userId, action, orderId, source, paidAmount);
                }
                orderRepository.markOutboxSent(row.id());
            } catch (Exception ex) {
                int retryCount = row.retryCount() + 1;
                LocalDateTime nextRetry = now().plusSeconds(outboxRetryDelaySeconds);
                orderRepository.markOutboxRetry(row.id(), retryCount, nextRetry, ex.getMessage());
            }
        }
    }

    private void enqueuePointsTask(Long userId, String orderId, String source, BigDecimal paidAmount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("action", paidAmount != null && paidAmount.signum() < 0 ? "revoke" : "award");
        payload.put("orderId", orderId);
        payload.put("source", source);
        payload.put("paidAmount", paidAmount == null ? BigDecimal.ZERO : paidAmount.abs());
        try {
            userClient.adjustPoints(userId, text(payload.get("action")), orderId, source, paidAmount == null ? BigDecimal.ZERO : paidAmount.abs());
        } catch (Exception ex) {
            orderRepository.insertOutbox(source, "POINTS", orderId, toJson(payload), 0, 0, now(), ex.getMessage(), now(), now());
        }
    }

    private void restoreStock(String productId, int delta) {
        if (productId == null || productId.isBlank() || delta <= 0) {
            return;
        }
        productClient.adjustStock(productId, delta);
    }

    private Map<String, Object> getProduct(String productId) {
        Map<String, Object> product = productClient.getProduct(productId);
        if (product == null || product.isEmpty()) {
            throw new ApiException(404, "product not found");
        }
        return product;
    }

    private OrderRepository.OrderRow requireOrder(String orderNo) {
        OrderRepository.OrderRow order = orderRepository.findByOrderNo(orderNo);
        if (order == null) {
            throw new ApiException(404, "订单不存在");
        }
        return order;
    }

    private OrderRepository.OrderRow requireOrderRow(String orderNo) {
        return requireOrder(orderNo);
    }

    private OrderRepository.OrderRow requireFlightOrder(String orderNo) {
        OrderRepository.OrderRow order = requireOrderRow(orderNo);
        if (!"FLIGHT".equalsIgnoreCase(order.orderType())) {
            throw new ApiException(400, "只能退机票订单");
        }
        return order;
    }

    private OrderRepository.OrderRow requireChangeableTrainOrder(String orderNo) {
        OrderRepository.OrderRow order = requireOrderRow(orderNo);
        if (!"TRAIN".equalsIgnoreCase(order.orderType())) {
            throw new ApiException(400, "只能改签火车票订单");
        }
        if (order.status() != STATUS_PAID) {
            throw new ApiException(400, "只有已支付订单可以改签");
        }
        if (order.changedOnce() == 1) {
            throw new ApiException(400, "该订单已改签过，不能再次改签");
        }
        return order;
    }

    private Map<String, Object> requireHotelOrder(Long userId, Long orderId) {
        Map<String, Object> row = orderRepository.findById(orderId) == null ? null : toOrderMap(orderRepository.findById(orderId));
        if (row == null) {
            throw new ApiException(404, "order not found");
        }
        if (!String.valueOf(userId).equals(String.valueOf(row.get("user_id")))) {
            throw new ApiException(403, "forbidden");
        }
        if (!"HOTEL".equalsIgnoreCase(text(row.get("order_type"), ""))) {
            throw new ApiException(400, "not hotel order");
        }
        return row;
    }

    private Map<String, Object> requireHotelDetail(long orderId) {
        Map<String, Object> detail = orderRepository.findHotelDetail(orderId);
        if (detail == null) {
            throw new ApiException(404, "hotel order detail not found");
        }
        return detail;
    }

    private Map<String, Object> requireFlightDetail(long orderId) {
        Map<String, Object> detail = orderRepository.findFlightDetail(orderId);
        if (detail == null) {
            throw new ApiException(404, "flight order detail not found");
        }
        return detail;
    }

    private Map<String, Object> toOrderResponse(OrderRepository.OrderRow order) {
        if (order == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", order.id());
        result.put("orderNo", order.orderNo());
        result.put("userId", order.userId());
        result.put("orderType", order.orderType());
        result.put("totalAmount", order.totalAmount());
        result.put("pointsDeduct", order.pointsDeduct());
        result.put("payAmount", order.payAmount());
        result.put("paymentMethod", order.paymentMethod());
        result.put("source", order.source());
        result.put("status", order.status());
        result.put("statusText", statusText(order.status()));
        result.put("payDeadline", order.payDeadline());
        result.put("payTime", order.payTime());
        result.put("cancelReason", order.cancelReason());
        result.put("pickupCode", order.pickupCode());
        result.put("changedOnce", order.changedOnce());
        result.put("originalOrderNo", order.originalOrderNo());
        result.put("extraInfo", readExtraInfo(order.extraInfo()));
        result.put("createTime", order.createTime());
        result.put("updateTime", order.updateTime());
        return result;
    }

    private Map<String, Object> toOrderMap(OrderRepository.OrderRow order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", order.id());
        map.put("order_no", order.orderNo());
        map.put("user_id", order.userId());
        map.put("order_type", order.orderType());
        map.put("total_amount", order.totalAmount());
        map.put("pay_amount", order.payAmount());
        map.put("payment_method", order.paymentMethod());
        map.put("points_deduct", order.pointsDeduct());
        map.put("source", order.source());
        map.put("status", order.status());
        map.put("pay_deadline", order.payDeadline());
        map.put("pay_time", order.payTime());
        map.put("cancel_reason", order.cancelReason());
        map.put("pickup_code", order.pickupCode());
        map.put("changed_once", order.changedOnce());
        map.put("original_order_no", order.originalOrderNo());
        map.put("extra_info", order.extraInfo());
        map.put("create_time", order.createTime());
        map.put("update_time", order.updateTime());
        return map;
    }

    private Map<String, Object> buildFlightRefundInfo(OrderRepository.OrderRow order, Map<String, Object> flight) {
        LocalDateTime departureAt = departureDateTime(flight);
        long hoursBeforeDeparture = departureAt == null ? 0 : Duration.between(now(), departureAt).toHours();
        BigDecimal rate = hoursBeforeDeparture >= 24 ? new BigDecimal("0.10") : new BigDecimal("0.30");
        BigDecimal fee = order.payAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal refundAmount = order.payAmount().subtract(fee).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("refundAmount", refundAmount);
        result.put("serviceFee", fee);
        result.put("feeRate", rate);
        result.put("hoursBeforeDeparture", hoursBeforeDeparture);
        result.put("rule", textField(flight, "refundRule", "refund_rule"));
        result.put("explanation", hoursBeforeDeparture >= 24 ? "距离起飞超过 24 小时，按较低手续费比例计算退款。" : "距离起飞不足 24 小时，按临近起飞手续费比例计算退款。");
        return result;
    }

    private String generateOrderNo() {
        String dateStr = now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String orderNo;
        do {
            orderNo = dateStr + String.format("%06d", RANDOM.nextInt(1_000_000));
        } while (orderRepository.countByOrderNo(orderNo) > 0);
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
        } while (orderRepository.countByPickupCode(code) > 0);
        return code;
    }

    private String generateTransactionId() {
        return "PAY" + now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    private String paySource(String orderType) {
        if ("HOTEL".equalsIgnoreCase(orderType)) {
            return "HOTEL_PAY";
        }
        if ("VACATION".equalsIgnoreCase(orderType)) {
            return "VACATION_PAY";
        }
        if ("FLIGHT".equalsIgnoreCase(orderType)) {
            return "FLIGHT_PAY";
        }
        return "TRAIN_PAY";
    }

    private boolean needsPickupCode(String orderType) {
        return "TRAIN".equalsIgnoreCase(orderType) || "VACATION".equalsIgnoreCase(orderType);
    }

    private String normalizePaymentMethod(String value, String fallback) {
        String method = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!PAYMENT_METHODS.contains(method)) {
            throw new ApiException(400, "unsupported payment method");
        }
        return method;
    }

    private void validateTrainRequest(Map<String, Object> request) {
        requiredText(request, "productId");
        requiredText(request, "passengerName");
        requiredText(request, "passengerPhone");
        if (parseNonNegativeInt(request.get("passengerAge"), 0) <= 0) {
            throw new ApiException(400, "年龄必须为1-120之间的正整数");
        }
        if (requiredText(request, "seatType").isBlank()) {
            throw new ApiException(400, "座位类型不能为空");
        }
    }

    private void validateVacationRequest(Map<String, Object> request) {
        requiredText(request, "productId");
        requiredText(request, "travelerName");
        requiredText(request, "travelerPhone");
        int travelerCount = parseNonNegativeInt(request.get("travelerCount"), 0);
        if (travelerCount < 1 || travelerCount > 20) {
            throw new ApiException(400, "出行人数必须为1-20之间的正整数");
        }
    }

    private void ensureStockEnough(Map<String, Object> product, int count, String message) {
        int stock = intValue(first(product, "stock", "STOCK"), 0);
        int soldCount = intValue(first(product, "soldCount", "sold_count"), 0);
        if (stock - soldCount < count) {
            throw new ApiException(409, message);
        }
    }

    private int normalizePoints(int pointsDeducted, BigDecimal totalPrice) {
        int points = Math.max(0, pointsDeducted);
        int maxPoints = totalPrice.divide(POINT_RATE, 0, RoundingMode.DOWN).intValue();
        return Math.min(points, maxPoints);
    }

    private int passengerCount(Map<String, Object> payload) {
        Object passengerList = payload == null ? null : payload.get("passengers");
        if (passengerList instanceof List<?> list && !list.isEmpty()) {
            return list.size();
        }
        return Math.max(1, parseNonNegativeInt(payload == null ? null : payload.get("passengerCount"), 1));
    }

    private List<Map<String, Object>> passengerList(Map<String, Object> payload, int fallbackCount) {
        Object passengers = payload == null ? null : payload.get("passengers");
        if (passengers instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                result.add(item instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : Map.of("value", item));
            }
            return result;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < fallbackCount; i++) {
            result.add(Map.of("name", text(payload == null ? null : payload.get("passengerName"), "乘客" + (i + 1))));
        }
        return result;
    }

    private int parsePassengerCount(String passengerList) {
        if (passengerList == null || passengerList.isBlank()) {
            return 1;
        }
        try {
            Object parsed = objectMapper.readValue(passengerList, Object.class);
            if (parsed instanceof List<?> list && !list.isEmpty()) {
                return list.size();
            }
            if (parsed instanceof Map<?, ?> map) {
                Object passengers = map.get("passengers");
                if (passengers instanceof List<?> list && !list.isEmpty()) {
                    return list.size();
                }
                return parseNonNegativeInt(map.get("passengerCount"), 1);
            }
        } catch (Exception ignored) {
        }
        return 1;
    }

    private String resolveTicketType(Map<String, Object> request) {
        if (truthy(request.get("isStudent"))) {
            return "STUDENT";
        }
        if (parseNonNegativeInt(request.get("passengerAge"), 0) > 0 && parseNonNegativeInt(request.get("passengerAge"), 0) < 18) {
            return "CHILD";
        }
        return "ADULT";
    }

    private double resolveDiscountRate(Map<String, Object> request) {
        if (truthy(request.get("isStudent"))) {
            return 0.6;
        }
        if (parseNonNegativeInt(request.get("passengerAge"), 0) > 0 && parseNonNegativeInt(request.get("passengerAge"), 0) < 18) {
            return 0.8;
        }
        return 1.0;
    }

    private Map<String, Object> readExtraInfo(String extraInfo) {
        if (extraInfo == null || extraInfo.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(extraInfo, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Map<String, Object> extraMap(Map<String, Object> product) {
        Object extra = first(product, "extra", "EXTRA");
        if (extra instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        if (extra instanceof String text && !text.isBlank()) {
            return readExtraInfo(text);
        }
        return Map.of();
    }

    private Object valueFromExtra(Map<String, Object> product, String key) {
        return extraMap(product).get(key);
    }

    private LocalDateTime resolveDepartureTime(Map<String, Object> extraInfo) {
        Object dateValue = extraInfo.get("date");
        if (dateValue == null || String.valueOf(dateValue).isBlank()) {
            throw new ApiException(400, "订单缺少发车日期，无法退票");
        }
        Object departTimeValue = extraInfo.get("departTime");
        String departTimeText = departTimeValue == null || String.valueOf(departTimeValue).isBlank()
                ? "00:00"
                : String.valueOf(departTimeValue);
        return LocalDateTime.of(LocalDate.parse(String.valueOf(dateValue)), LocalTime.parse(departTimeText));
    }

    private LocalDateTime departureDateTime(Map<String, Object> flight) {
        LocalDate date = parseDate(textField(flight, "departureDate", "departure_date", "date"));
        String timeText = text(first(flight, "departTime", "depart_time"), "00:00");
        try {
            return LocalDateTime.of(date, LocalTime.parse(timeText));
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal calculateCancelFee(BigDecimal payAmount, LocalDate today, LocalDate checkIn) {
        long daysBeforeCheckIn = ChronoUnit.DAYS.between(today, checkIn);
        if (daysBeforeCheckIn > 7) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (daysBeforeCheckIn >= 3) {
            return payAmount.multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);
        }
        if (daysBeforeCheckIn >= 1) {
            return payAmount.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
        }
        return payAmount.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate parseDate(String value) {
        return LocalDate.parse(value);
    }

    private Date sqlDate(Object value, LocalDate fallback) {
        LocalDate date = value == null || String.valueOf(value).isBlank() ? fallback : parseDate(String.valueOf(value));
        return Date.valueOf(date);
    }

    private LocalDate localDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return parseDate(String.valueOf(value));
    }

    private LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    private BigDecimal priceOf(Map<String, Object> product) {
        return decimal(first(product, "price", "PRICE"), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private String productId(Map<String, Object> product) {
        return text(first(product, "id", "ID"), "");
    }

    private String productIdFromHotelDetail(Map<String, Object> detail) {
        return text(first(detail, "room_id", "roomId"), "");
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String valueOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String text(Object value, String fallback) {
        String text = text(value);
        return text.isBlank() ? fallback : text;
    }

    private Object first(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return null;
    }

    private String textField(Map<String, Object> map, String... keys) {
        return text(first(map, keys));
    }

    private BigDecimal decimal(Object value, BigDecimal fallback) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return value == null ? fallback : new BigDecimal(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private int parseNonNegativeInt(Object value, int fallback) {
        int parsed = intValue(value, fallback);
        return Math.max(0, parsed);
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = text(value).trim().toLowerCase(Locale.ROOT);
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private String requiredText(Map<String, Object> payload, String key) {
        String value = text(payload == null ? null : payload.get(key));
        if (value.isBlank()) {
            throw new ApiException(400, key + " is required");
        }
        return value;
    }

    private String requiredText(Object value, String key) {
        String text = text(value);
        if (text.isBlank()) {
            throw new ApiException(400, key + " is required");
        }
        return text;
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApiException(401, "unauthorized");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String statusText(int status) {
        return switch (status) {
            case STATUS_PENDING -> "待支付";
            case STATUS_PAID -> "已支付";
            case STATUS_CANCELED -> "已取消";
            case 3 -> "已取消";
            case STATUS_REFUNDED -> "已退款";
            case STATUS_CHANGED -> "已改签";
            default -> "处理中";
        };
    }

    private String findByPickupCode(String pickupCode) {
        OrderRepository.OrderRow row = orderRepository.findByPickupCode(pickupCode);
        if (row != null) {
            return row.orderNo();
        }
        throw new ApiException(404, "订单不存在");
    }

    private Map<String, Object> toHotelOrderResponse(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", row.get("id"));
        result.put("orderNo", row.get("order_no"));
        result.put("totalAmount", row.get("total_amount"));
        result.put("payAmount", row.get("pay_amount"));
        result.put("status", row.get("status"));
        result.put("paymentMethod", row.get("payment_method"));
        result.put("payDeadline", row.get("pay_deadline"));
        result.put("createTime", row.get("create_time"));
        return result;
    }

    private Map<String, Object> toHotelOrderResponse(OrderRepository.OrderRow order) {
        return toHotelOrderResponse(toOrderMap(order));
    }

    private Map<String, Object> requireFlightOrder(String orderNo, boolean checkType) {
        OrderRepository.OrderRow order = requireOrderRow(orderNo);
        if (checkType && !"FLIGHT".equalsIgnoreCase(order.orderType())) {
            throw new ApiException(400, "只能退机票订单");
        }
        return toOrderResponse(order);
    }

    private int parseQuantityForOrder(OrderRepository.OrderRow order, Map<String, Object> extra) {
        if ("VACATION".equalsIgnoreCase(order.orderType())) {
            return parseNonNegativeInt(extra.get("travelerCount"), 1);
        }
        return 1;
    }

    private String buildExceptionMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null ? "服务繁忙，请稍后再试" : throwable.getMessage();
    }
}
