package top.ortus.lightmark.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.ortus.lightmark.backend.common.PageResponse;
import top.ortus.lightmark.backend.dto.module.OrderDTO;
import top.ortus.lightmark.backend.dto.module.ProductDTO;
import top.ortus.lightmark.backend.exception.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Clob;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FlightSearchService {

    // 分页参数设置边界，避免一次请求拉取过多数据。
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final BigDecimal AIRPORT_FEE = new BigDecimal("50.00");
    private static final BigDecimal FUEL_FEE = new BigDecimal("80.00");
    private static final BigDecimal INSURANCE_FEE = new BigDecimal("30.00");
    private static final BigDecimal EXTRA_BAGGAGE_FEE = new BigDecimal("120.00");
    private static final BigDecimal SEAT_FEE = new BigDecimal("40.00");
    private static final int DEFAULT_CALENDAR_DAYS = 30;
    private static final int MAX_CALENDAR_DAYS = 365;
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PAID = 1;
    private static final int STATUS_CANCELED = 2;
    private static final int STATUS_REFUNDED = 4;
    private static final List<String> PAYMENT_METHODS = List.of("WECHAT", "ALIPAY", "POINTS");
    private static final Map<String, String> AIRPORT_CITY_CODES = Map.ofEntries(
            Map.entry("PEK", "BJS"),
            Map.entry("PKX", "BJS"),
            Map.entry("NAY", "BJS"),
            Map.entry("SHA", "SHA"),
            Map.entry("PVG", "SHA"),
            Map.entry("CTU", "CTU"),
            Map.entry("TFU", "CTU")
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PointsMembershipService pointsMembershipService;

    public FlightSearchService(JdbcTemplate jdbcTemplate,
                               ObjectMapper objectMapper,
                               PointsMembershipService pointsMembershipService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.pointsMembershipService = pointsMembershipService;
    }

    /**
     * 执行机票搜索。
     *
     * 当前产品表只保存通用产品字段，航班专属字段放在 product.extra JSON 中，
     * 因此这里先查出 FLIGHT 产品，再在内存中解析 extra 完成业务筛选和排序。
     */
    public PageResponse<ProductDTO> search(Map<String, String> params) {
        List<ProductDTO> flights = jdbcTemplate.queryForList(
                        "select * from product where product_type = ? and status = 1 and stock > 0",
                        "FLIGHT"
                )
                .stream()
                .map(this::toProduct)
                .toList();

        List<ProductDTO> matched = flights.stream()
                .filter(this::isAvailable)
                .filter(flight -> matches(flight, params))
                .sorted(comparator(params == null ? null : params.get("sort")))
                .toList();

        int page = parsePositiveInt(value(params, "page"), DEFAULT_PAGE);
        int size = Math.min(parsePositiveInt(value(params, "size"), DEFAULT_SIZE), MAX_SIZE);
        int from = Math.min((page - 1) * size, matched.size());
        int to = Math.min(from + size, matched.size());

        return new PageResponse<>(matched.size(), page, size, new ArrayList<>(matched.subList(from, to)));
    }

    /**
     * 查询单个航班详情。
     *
     * 详情接口只允许读取机票产品；酒店、火车票等其他产品 ID 不会从该接口返回。
     */
    public ProductDTO getDetail(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new ApiException(400, "productId is required");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select * from product where id = ? and product_type = ?",
                productId,
                "FLIGHT"
        );
        if (rows.isEmpty()) {
            throw new ApiException(404, "flight not found");
        }
        ProductDTO flight = toProduct(rows.get(0));
        if (flight.getStatus() != 1) {
            throw new ApiException(404, "flight not found");
        }
        return flight;
    }

    /**
     * 返回指定航线从 startDate 开始的价格日历。
     *
     * 前端需要最多一年的趋势数据，因此这里按日期聚合最低价；没有航班的日期显式返回 available=false。
     */
    public Map<String, Object> priceCalendar(Map<String, String> params) {
        String departureCity = value(params, "departureCity");
        String arrivalCity = value(params, "arrivalCity");
        LocalDate startDate = parseDate(value(params, "startDate"), LocalDate.now());
        int rangeDays = Math.min(parsePositiveInt(value(params, "days"), DEFAULT_CALENDAR_DAYS), MAX_CALENDAR_DAYS);

        List<ProductDTO> flights = jdbcTemplate.queryForList(
                        "select * from product where product_type = ? and status = 1 and stock > 0",
                        "FLIGHT"
                )
                .stream()
                .map(this::toProduct)
                .filter(this::isAvailable)
                .filter(flight -> matchesCalendarFilters(flight, params, departureCity, arrivalCity))
                .toList();

        Map<String, BigDecimal> lowestPrices = new HashMap<>();
        for (ProductDTO flight : flights) {
            String departureDate = textField(flight, "departureDate", "departure_date", "date");
            if (departureDate == null || departureDate.isBlank()) {
                continue;
            }
            lowestPrices.merge(departureDate, flight.getPrice(), BigDecimal::min);
        }

        List<Map<String, Object>> days = new ArrayList<>();
        for (int i = 0; i < rangeDays; i++) {
            LocalDate day = startDate.plusDays(i);
            BigDecimal lowest = lowestPrices.get(day.toString());
            days.add(Map.of(
                    "date", day.toString(),
                    "lowestPrice", lowest == null ? 0 : lowest,
                    "available", lowest != null
            ));
        }
        return Map.of("route", Map.of(
                        "departureCity", departureCity == null ? "" : departureCity,
                        "arrivalCity", arrivalCity == null ? "" : arrivalCity
                ),
                "days", days);
    }

    /**
     * 下单预览只计算费用和库存，不落库，便于前端在乘机人信息变更时实时刷新价格。
     */
    public Map<String, Object> previewOrder(Map<String, Object> payload) {
        ProductDTO flight = getDetail(requiredText(payload, "productId"));
        int passengerCount = passengerCount(payload);
        if (flight.getStock() < passengerCount) {
            throw new ApiException(409, "flight stock is insufficient");
        }
        return buildPreview(flight, payload, passengerCount);
    }

    /**
     * 创建订单与扣减库存必须在同一事务内完成，避免并发下单导致超卖。
     */
    @Transactional
    public OrderDTO createOrder(Long userId, Map<String, Object> payload) {
        if (userId == null || userId <= 0) {
            throw new ApiException(401, "unauthorized");
        }
        ProductDTO flight = getDetail(requiredText(payload, "productId"));
        int passengerCount = passengerCount(payload);
        if (flight.getStock() < passengerCount) {
            throw new ApiException(409, "flight stock is insufficient");
        }

        Map<String, Object> preview = buildPreview(flight, payload, passengerCount);
        BigDecimal totalAmount = decimal(preview.get("totalAmount"));
        BigDecimal payAmount = decimal(preview.get("payAmount"));
        LocalDateTime now = LocalDateTime.now();
        String orderNo = "F" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int stockUpdated = jdbcTemplate.update(
                "update product set stock = stock - ?, sold_count = sold_count + ?, update_time = ? where id = ? and stock >= ?",
                passengerCount,
                passengerCount,
                now,
                flight.getId(),
                passengerCount
        );
        if (stockUpdated == 0) {
            throw new ApiException(409, "flight stock is insufficient");
        }
        jdbcTemplate.update(
                "insert into `orders` (order_no, user_id, order_type, total_amount, points_deduct, pay_amount, payment_method, source, status, pay_deadline, create_time, update_time) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                orderNo,
                userId,
                "FLIGHT",
                totalAmount,
                parseNonNegativeInt(asString(payload == null ? null : payload.get("pointsDeduct")), 0),
                payAmount,
                "UNPAID",
                "WEB",
                STATUS_PENDING,
                now.plusMinutes(15),
                now,
                now
        );
        Long orderId = jdbcTemplate.queryForObject("select id from `orders` where order_no = ?", Long.class, orderNo);
        Map<String, Object> extra = extra(flight);
        jdbcTemplate.update(
                "insert into flight_order_detail (order_id, product_id, flight_no, departure_date, passenger_list, baggage, insurance) values (?, ?, ?, ?, ?, ?, ?)",
                orderId,
                flight.getId(),
                textField(flight, "flightNo", "flight_no"),
                java.sql.Date.valueOf(parseDate(asString(first(extra, "departureDate", "departure_date", "date")), LocalDate.now())),
                orderDetailJson(payload, passengerCount),
                asString(first(extra, "baggage")),
                truthy(payload == null ? null : payload.get("insurance")) ? 1 : 0
        );
        return findOrder(orderNo);
    }

    @Transactional
    public Map<String, Object> payOrder(String orderNo, Map<String, Object> payload) {
        OrderDTO order = findOrder(orderNo);
        if (order.getStatus() == STATUS_PAID) {
            return paidResult(order.getOrder_no(), order.getPayment_method(), 0);
        }
        if (order.getStatus() != STATUS_PENDING) {
            throw new ApiException(409, "order is not payable");
        }
        LocalDateTime now = LocalDateTime.now();
        if (order.getPay_deadline() != null && now.isAfter(order.getPay_deadline())) {
            cancelOrder(orderNo, "支付超时自动取消");
            throw new ApiException(409, "payment deadline exceeded");
        }

        String method = normalizePaymentMethod(asString(payload == null ? null : payload.get("paymentMethod")));
        jdbcTemplate.update(
                "update `orders` set status = ?, payment_method = ?, pay_time = ?, update_time = ? where order_no = ?",
                STATUS_PAID,
                method,
                now,
                now,
                orderNo
        );
        jdbcTemplate.update(
                "insert into payment_record (order_id, transaction_id, payment_method, amount, status, callback_time, create_time) values (?, ?, ?, ?, ?, ?, ?)",
                Long.parseLong(order.getId()),
                "PAY" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")),
                method,
                order.getPay_amount(),
                1,
                now,
                now
        );
        int points = pointsMembershipService.calculateRewardPoints(order.getPay_amount());
        pointsMembershipService.awardPoints(order.getUser_id(), order.getId(), "FLIGHT_PAY", order.getPay_amount());
        return paidResult(orderNo, method, points);
    }

    @Transactional
    public boolean cancelOrder(String orderNo, String reason) {
        OrderDTO order = findOrder(orderNo);
        if (order.getStatus() != STATUS_PENDING) {
            throw new ApiException(409, "only pending orders can be canceled");
        }
        int passengers = restoreStock(order);
        jdbcTemplate.update(
                "update `orders` set status = ?, cancel_reason = ?, update_time = ? where order_no = ?",
                STATUS_CANCELED,
                reason == null || reason.isBlank() ? "用户取消" : reason,
                LocalDateTime.now(),
                orderNo
        );
        return passengers > 0;
    }

    @Transactional
    public Map<String, Object> changeFlightOrder(String orderNo, String newProductId) {
        OrderDTO order = findOrder(orderNo);
        if (order.getStatus() != STATUS_PAID) {
            throw new ApiException(409, "only paid orders can be changed");
        }

        ProductDTO newFlight = getDetail(newProductId);
        if (newFlight == null || newFlight.getStatus() != 1) {
            throw new ApiException(404, "target flight not available");
        }

        Map<String, Object> oldDetail = flightOrderDetail(Long.parseLong(order.getId()));
        ProductDTO oldFlight = getDetail(asString(oldDetail.get("product_id")));
        Map<String, Object> oldExtra = extra(oldFlight);
        String oldFlightNo = textField(oldFlight, "flightNo", "flight_no");
        String newFlightNo = textField(newFlight, "flightNo", "flight_no");

        // Restore stock for old order
        restoreStock(order);

        // Create a new order for the new flight
        int passengerCount = 1;
        LocalDateTime now = LocalDateTime.now();
        String newOrderNo = "F" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));

        int stockUpdated = jdbcTemplate.update(
                "update product set stock = stock - ?, sold_count = sold_count + ?, update_time = ? where id = ? and stock >= ?",
                passengerCount, passengerCount, now, newFlight.getId(), passengerCount
        );
        if (stockUpdated == 0) {
            throw new ApiException(409, "target flight stock is insufficient");
        }

        BigDecimal oldPayAmount = order.getPay_amount() == null ? BigDecimal.ZERO : order.getPay_amount();
        BigDecimal newPrice = newFlight.getPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal difference = newPrice.subtract(oldPayAmount).setScale(2, RoundingMode.HALF_UP);

        jdbcTemplate.update(
                "insert into `orders` (order_no, user_id, order_type, total_amount, points_deduct, pay_amount, payment_method, source, status, pay_time, create_time, update_time) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                newOrderNo,
                order.getUser_id(),
                "FLIGHT",
                newPrice,
                0,
                newPrice,
                "CHANGE_PAY",
                "WEB",
                STATUS_PAID,
                now, now, now
        );

        Long newOrderId = jdbcTemplate.queryForObject("select id from `orders` where order_no = ?", Long.class, newOrderNo);
        Map<String, Object> newExtra = extra(newFlight);
        jdbcTemplate.update(
                "insert into flight_order_detail (order_id, product_id, flight_no, departure_date, passenger_list, baggage, insurance) values (?, ?, ?, ?, ?, ?, ?)",
                newOrderId,
                newFlight.getId(),
                newFlightNo,
                java.sql.Date.valueOf(parseDate(asString(first(newExtra, "departureDate", "departure_date", "date")), LocalDate.now())),
                "[]",
                asString(first(newExtra, "baggage")),
                0
        );

        // Mark old order as changed
        jdbcTemplate.update(
                "update `orders` set status = ?, cancel_reason = ?, update_time = ? where order_no = ?",
                5, "已改签至 " + newOrderNo, now, orderNo
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("oldOrderNo", orderNo);
        result.put("newOrderNo", newOrderNo);
        result.put("oldPayAmount", oldPayAmount);
        result.put("newPayAmount", newPrice);
        result.put("difference", difference);
        result.put("oldFlightNo", oldFlightNo);
        result.put("newFlightNo", newFlightNo);
        result.put("message", difference.compareTo(BigDecimal.ZERO) > 0
                ? "改签成功，需补差价 ¥" + difference
                : difference.compareTo(BigDecimal.ZERO) < 0
                ? "改签成功，已退差价 ¥" + difference.abs()
                : "改签成功，无需补差价");
        return result;
    }

    @Transactional
    public Map<String, Object> refundOrder(String orderNo) {
        OrderDTO order = findOrder(orderNo);
        if (order.getStatus() != STATUS_PAID) {
            throw new ApiException(409, "order is not refundable");
        }
        Map<String, Object> detail = flightOrderDetail(Long.parseLong(order.getId()));
        ProductDTO flight = getDetail(asString(detail.get("product_id")));
        Map<String, Object> refundInfo = buildRefundInfo(order, flight);
        restoreStock(order);
        jdbcTemplate.update(
                "update `orders` set status = ?, update_time = ? where order_no = ?",
                STATUS_REFUNDED,
                LocalDateTime.now(),
                orderNo
        );
        pointsMembershipService.revokePoints(order.getUser_id(), order.getId(), "FLIGHT_REFUND", order.getPay_amount());
        Map<String, Object> result = new LinkedHashMap<>(refundInfo);
        result.putAll(Map.of(
                "orderNo", orderNo,
                "status", STATUS_REFUNDED,
                "statusText", "已退款"
        ));
        return result;
    }

    public Map<String, Object> explainRefund(String orderNo) {
        OrderDTO order = findOrder(orderNo);
        Map<String, Object> detail = flightOrderDetail(Long.parseLong(order.getId()));
        ProductDTO flight = getDetail(asString(detail.get("product_id")));
        Map<String, Object> result = new LinkedHashMap<>(buildRefundInfo(order, flight));
        result.put("orderNo", orderNo);
        result.put("status", order.getStatus());
        result.put("statusText", statusText(order.getStatus()));
        return result;
    }

    public Map<String, Object> orderStatus(String orderNo) {
        OrderDTO order = findOrder(orderNo);
        return Map.of(
                "orderNo", order.getOrder_no(),
                "orderType", order.getOrder_type(),
                "payAmount", order.getPay_amount(),
                "paymentMethod", order.getPayment_method() == null ? "" : order.getPayment_method(),
                "status", order.getStatus(),
                "statusText", statusText(order.getStatus()),
                "payDeadline", order.getPay_deadline() == null ? "" : order.getPay_deadline().toString(),
                "payTime", order.getPay_time() == null ? "" : order.getPay_time().toString(),
                "cancelReason", order.getCancel_reason() == null ? "" : order.getCancel_reason()
        );
    }

    public boolean paymentCallback(Map<String, Object> payload) {
        String orderNo = requiredText(payload, "orderNo");
        payOrder(orderNo, payload);
        return true;
    }

    // 只展示已上架且有余票的航班，库存为 0 的航班不会进入搜索结果。
    private boolean isAvailable(ProductDTO flight) {
        return flight != null && flight.getStatus() == 1 && flight.getStock() > 0;
    }

    // 汇总所有查询条件的匹配逻辑，后续新增搜索条件时优先扩展这里。
    private boolean matches(ProductDTO flight, Map<String, String> params) {
        Map<String, Object> extra = extra(flight);
        return matchesRouteField(extra, value(params, "departureCity"), "departureAirport", "departure_airport", "departureAirportCode", "departure_airport_code", "departureCity", "departure_city", "departure")
                && matchesRouteField(extra, value(params, "arrivalCity"), "arrivalAirport", "arrival_airport", "arrivalAirportCode", "arrival_airport_code", "arrivalCity", "arrival_city", "arrival")
                && matchesText(params, extra, "airline", "airline")
                && matchesText(params, extra, "flightNo", "flight_no", "flightNo")
                && matchesCabin(params, extra)
                && matchesDirectOnly(params, extra)
                && matchesDepartureDate(params, extra)
                && matchesPassengerCount(params, flight);
    }

    // 价格日历只关心航线、舱位、直飞和人数，不按具体出发日过滤，否则无法形成趋势。
    private boolean matchesCalendarFilters(ProductDTO flight, Map<String, String> params, String departureCity, String arrivalCity) {
        Map<String, Object> extra = extra(flight);
        return matchesRouteField(extra, departureCity, "departureAirport", "departure_airport", "departureAirportCode", "departure_airport_code", "departureCity", "departure_city", "departure")
                && matchesRouteField(extra, arrivalCity, "arrivalAirport", "arrival_airport", "arrivalAirportCode", "arrival_airport_code", "arrivalCity", "arrival_city", "arrival")
                && matchesCabin(params, extra)
                && matchesDirectOnly(params, extra)
                && matchesPassengerCount(params, flight);
    }

    // routeCode 支持逗号分隔的省级机场集合，例如 PEK,PKX,BJS，同时兼容城市码和机场码互配。
    private boolean matchesRouteField(Map<String, Object> extra, String routeCode, String... extraKeys) {
        String expected = normalize(routeCode);
        if (expected.isBlank()) {
            return true;
        }
        List<String> expectedCodes = java.util.Arrays.stream(expected.split(","))
                .map(this::normalize)
                .filter(code -> !code.isBlank())
                .toList();
        for (String extraKey : extraKeys) {
            String actual = normalize(asString(extra.get(extraKey)));
            for (String expectedCode : expectedCodes) {
                if (sameRouteCode(expectedCode, routeCityCode(expectedCode), actual)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean sameRouteCode(String expected, String expectedCity, String actual) {
        if (actual.isBlank()) {
            return false;
        }
        String actualCity = routeCityCode(actual);
        return actual.equals(expected)
                || actual.equals(expectedCity)
                || actualCity.equals(expected)
                || actualCity.equals(expectedCity);
    }

    // 多机场城市使用统一城市码归一化，解决北京/上海/成都等城市多机场互查问题。
    private String routeCityCode(String code) {
        String normalized = normalize(code);
        if (normalized.isBlank()) {
            return "";
        }
        return normalize(AIRPORT_CITY_CODES.getOrDefault(normalized.toUpperCase(Locale.ROOT), normalized));
    }

    // 文本条件使用 contains 匹配，兼容城市三字码、城市名或航司名称的模糊查询。
    private boolean matchesText(Map<String, String> params, Map<String, Object> extra, String requestKey, String... extraKeys) {
        String expected = normalize(value(params, requestKey));
        if (expected.isBlank()) {
            return true;
        }
        for (String extraKey : extraKeys) {
            String actual = normalize(asString(extra.get(extraKey)));
            if (!actual.isBlank() && actual.contains(expected)) {
                return true;
            }
        }
        return false;
    }

    // 舱位可能是字符串，也可能是包含 type/name 的数组对象，这里统一展开后匹配。
    private boolean matchesCabin(Map<String, String> params, Map<String, Object> extra) {
        String expected = normalize(value(params, "cabin"));
        if (expected.isBlank()) {
            return true;
        }
        List<String> cabinValues = new ArrayList<>();
        Object cabins = first(extra, "cabins", "cabin");
        if (cabins instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    cabinValues.add(asString(first(map, "type", "cabin")));
                    cabinValues.add(asString(first(map, "name")));
                } else {
                    cabinValues.add(asString(item));
                }
            }
        } else {
            cabinValues.add(asString(cabins));
        }
        return cabinValues.stream()
                .map(this::normalize)
                .anyMatch(value -> !value.isBlank() && value.contains(expected));
    }

    private boolean matchesDirectOnly(Map<String, String> params, Map<String, Object> extra) {
        if (!truthy(value(params, "directOnly"))) {
            return true;
        }
        int stops = parseNonNegativeInt(asString(first(extra, "stops", "stopCount", "stop_count")), 0);
        return stops == 0;
    }

    // 日期先做精确匹配，避免用户选择指定日期时返回相邻日期航班。
    private boolean matchesDepartureDate(Map<String, String> params, Map<String, Object> extra) {
        String expected = value(params, "departureDate");
        if (expected == null || expected.isBlank()) {
            return true;
        }
        String actual = asString(first(extra, "departureDate", "departure_date", "date"));
        return expected.equals(actual);
    }

    // 成人和儿童人数会占用库存；未传成人数时按 1 人处理。
    private boolean matchesPassengerCount(Map<String, String> params, ProductDTO flight) {
        int adultCount = parseNonNegativeInt(value(params, "adultCount"), 1);
        int childCount = parseNonNegativeInt(value(params, "childCount"), 0);
        return flight.getStock() >= adultCount + childCount;
    }

    // 支持 sort=price、sort=-price、sort=departureTime,price 等形式。
    private Comparator<ProductDTO> comparator(String sort) {
        List<Comparator<ProductDTO>> comparators = new ArrayList<>();
        if (sort != null && !sort.isBlank()) {
            for (String item : sort.split(",")) {
                String field = item.trim();
                if (field.isBlank()) {
                    continue;
                }
                boolean descending = field.startsWith("-");
                if (descending) {
                    field = field.substring(1);
                }
                Comparator<ProductDTO> comparator = comparatorFor(field);
                if (comparator != null) {
                    comparators.add(descending ? comparator.reversed() : comparator);
                }
            }
        }
        if (comparators.isEmpty()) {
            comparators.add(Comparator.comparing(ProductDTO::getPrice, Comparator.nullsLast(BigDecimal::compareTo)));
            comparators.add(Comparator.comparing(flight -> textField(flight, "departureTime", "departure_time")));
        }
        return comparators.stream().reduce(Comparator::thenComparing).orElse((left, right) -> 0);
    }

    // 将前端可传的排序字段映射为 ProductDTO 或 extra 字段上的比较器。
    private Comparator<ProductDTO> comparatorFor(String field) {
        String normalized = normalize(field);
        return switch (normalized) {
            case "price" -> Comparator.comparing(ProductDTO::getPrice, Comparator.nullsLast(BigDecimal::compareTo));
            case "departuretime", "departure_time" -> Comparator.comparing(this::departureTime, Comparator.nullsLast(Comparator.naturalOrder()));
            case "arrivaltime", "arrival_time" -> Comparator.comparing(this::arrivalTime, Comparator.nullsLast(Comparator.naturalOrder()));
            case "airline" -> Comparator.comparing(flight -> textField(flight, "airline"), Comparator.nullsLast(String::compareTo));
            case "stops", "stopcount", "stop_count" -> Comparator.comparingInt(flight -> intField(flight, "stops", "stopCount", "stop_count"));
            case "soldcount", "sold_count" -> Comparator.comparingInt(ProductDTO::getSold_count);
            default -> null;
        };
    }

    private LocalTime departureTime(ProductDTO flight) {
        return timeField(flight, "departureTime", "departure_time");
    }

    private LocalTime arrivalTime(ProductDTO flight) {
        return timeField(flight, "arrivalTime", "arrival_time");
    }

    private LocalTime timeField(ProductDTO flight, String... keys) {
        String value = textField(flight, keys);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            if (value.length() > 5) {
                return LocalTime.parse(value.substring(0, 5));
            }
            return LocalTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String textField(ProductDTO flight, String... keys) {
        return asString(first(extra(flight), keys));
    }

    private int intField(ProductDTO flight, String... keys) {
        return parseNonNegativeInt(asString(first(extra(flight), keys)), 0);
    }

    // 费用拆分保持和订单预览 UI 一致：票价、税费、附加服务、积分抵扣分别返回。
    private Map<String, Object> buildPreview(ProductDTO flight, Map<String, Object> payload, int passengerCount) {
        BigDecimal ticketAmount = flight.getPrice().multiply(BigDecimal.valueOf(passengerCount));
        BigDecimal taxAmount = AIRPORT_FEE.add(FUEL_FEE).multiply(BigDecimal.valueOf(passengerCount));
        BigDecimal serviceAmount = BigDecimal.ZERO;
        if (truthy(payload == null ? null : payload.get("insurance"))) {
            serviceAmount = serviceAmount.add(INSURANCE_FEE.multiply(BigDecimal.valueOf(passengerCount)));
        }
        if (truthy(payload == null ? null : payload.get("extraBaggage"))) {
            serviceAmount = serviceAmount.add(EXTRA_BAGGAGE_FEE);
        }
        if (truthy(payload == null ? null : payload.get("seatSelection"))) {
            serviceAmount = serviceAmount.add(SEAT_FEE.multiply(BigDecimal.valueOf(passengerCount)));
        }
        int pointsDeduct = parseNonNegativeInt(asString(payload == null ? null : payload.get("pointsDeduct")), 0);
        BigDecimal pointsAmount = BigDecimal.valueOf(pointsDeduct).divide(new BigDecimal("100"), 2, RoundingMode.DOWN);
        BigDecimal totalAmount = ticketAmount.add(taxAmount).add(serviceAmount);
        BigDecimal payAmount = totalAmount.subtract(pointsAmount).max(BigDecimal.ZERO);
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("productId", flight.getId());
        preview.put("flightName", flight.getName());
        preview.put("passengerCount", passengerCount);
        preview.put("stockEnough", true);
        preview.put("cabin", asString(payload == null ? null : payload.get("cabin")));
        preview.put("ticketAmount", ticketAmount);
        preview.put("taxAmount", taxAmount);
        preview.put("serviceAmount", serviceAmount);
        preview.put("pointsDeduct", pointsDeduct);
        preview.put("pointsAmount", pointsAmount);
        preview.put("totalAmount", totalAmount);
        preview.put("payAmount", payAmount);
        preview.put("payDeadlineMinutes", 15);
        return preview;
    }

    private int passengerCount(Map<String, Object> payload) {
        int listCount = passengerListCount(payload == null ? null : payload.get("passengers"));
        boolean hasAdultCount = payload != null && payload.containsKey("adultCount");
        boolean hasChildCount = payload != null && payload.containsKey("childCount");
        if (hasAdultCount || hasChildCount) {
            int adultCount = parseNonNegativeInt(asString(payload.get("adultCount")), hasAdultCount ? 0 : 1);
            int childCount = parseNonNegativeInt(asString(payload.get("childCount")), 0);
            int declaredCount = Math.max(1, adultCount + childCount);
            if (listCount > 0 && listCount != declaredCount) {
                throw new ApiException(400, "passenger list size does not match adultCount and childCount");
            }
            return declaredCount;
        }
        if (listCount > 0) {
            return listCount;
        }
        return 1;
    }

    private int passengerListCount(Object passengers) {
        if (passengers instanceof List<?> list && !list.isEmpty()) {
            return list.size();
        }
        return 0;
    }

    private String orderDetailJson(Map<String, Object> payload, int passengerCount) {
        Map<String, Object> detail = new LinkedHashMap<>();
        Object passengers = payload == null ? null : payload.get("passengers");
        detail.put("passengers", passengers instanceof List<?> ? passengers : List.of());
        detail.put("passengerCount", passengerCount);
        detail.put("adultCount", parseNonNegativeInt(asString(payload == null ? null : payload.get("adultCount")), passengerCount));
        detail.put("childCount", parseNonNegativeInt(asString(payload == null ? null : payload.get("childCount")), 0));
        detail.put("cabin", asString(payload == null ? null : payload.get("cabin")));
        detail.put("insurance", truthy(payload == null ? null : payload.get("insurance")));
        detail.put("extraBaggage", truthy(payload == null ? null : payload.get("extraBaggage")));
        detail.put("seatSelection", truthy(payload == null ? null : payload.get("seatSelection")));
        return toJson(detail);
    }

    private String normalizePaymentMethod(String value) {
        String method = value == null || value.isBlank() ? "WECHAT" : value.trim().toUpperCase(Locale.ROOT);
        if (!PAYMENT_METHODS.contains(method)) {
            throw new ApiException(400, "unsupported payment method");
        }
        return method;
    }

    private Map<String, Object> buildRefundInfo(OrderDTO order, ProductDTO flight) {
        LocalDateTime departureAt = departureDateTime(flight);
        long hoursBeforeDeparture = departureAt == null ? 0 : Duration.between(LocalDateTime.now(), departureAt).toHours();
        BigDecimal rate = hoursBeforeDeparture >= 24 ? new BigDecimal("0.10") : new BigDecimal("0.30");
        BigDecimal payAmount = order.getPay_amount() == null ? BigDecimal.ZERO : order.getPay_amount();
        BigDecimal fee = payAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal refundAmount = payAmount.subtract(fee).max(BigDecimal.ZERO);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("refundAmount", refundAmount);
        result.put("serviceFee", fee);
        result.put("feeRate", rate);
        result.put("hoursBeforeDeparture", hoursBeforeDeparture);
        result.put("rule", textField(flight, "refundRule", "refund_rule"));
        result.put("explanation", hoursBeforeDeparture >= 24
                ? "距离起飞超过 24 小时，按较低手续费比例计算退款。"
                : "距离起飞不足 24 小时，按临近起飞手续费比例计算退款。");
        return result;
    }

    private String requiredText(Map<String, Object> payload, String key) {
        String value = asString(payload == null ? null : payload.get(key));
        if (value.isBlank()) {
            throw new ApiException(400, key + " is required");
        }
        return value;
    }

    private OrderDTO findOrder(String orderNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from `orders` where order_no = ?", orderNo);
        if (rows.isEmpty()) {
            throw new ApiException(404, "order not found");
        }
        return toOrder(rows.get(0));
    }

    private OrderDTO toOrder(Map<String, Object> row) {
        OrderDTO order = new OrderDTO();
        order.setId(asString(first(row, "id", "ID")));
        order.setOrder_no(asString(first(row, "order_no", "ORDER_NO")));
        order.setUser_id(asString(first(row, "user_id", "USER_ID")));
        order.setOrder_type(asString(first(row, "order_type", "ORDER_TYPE")));
        order.setTotal_amount(decimal(first(row, "total_amount", "TOTAL_AMOUNT")));
        order.setPoints_deduct(parseNonNegativeInt(asString(first(row, "points_deduct", "POINTS_DEDUCT")), 0));
        order.setPay_amount(decimal(first(row, "pay_amount", "PAY_AMOUNT")));
        order.setPayment_method(asString(first(row, "payment_method", "PAYMENT_METHOD")));
        order.setSource(asString(first(row, "source", "SOURCE")));
        order.setStatus(parseNonNegativeInt(asString(first(row, "status", "STATUS")), 0));
        order.setPay_deadline(localDateTime(first(row, "pay_deadline", "PAY_DEADLINE")));
        order.setPay_time(localDateTime(first(row, "pay_time", "PAY_TIME")));
        order.setCancel_reason(asString(first(row, "cancel_reason", "CANCEL_REASON")));
        order.setCreate_time(localDateTime(first(row, "create_time", "CREATE_TIME")));
        order.setUpdate_time(localDateTime(first(row, "update_time", "UPDATE_TIME")));
        return order;
    }

    private int restoreStock(OrderDTO order) {
        Map<String, Object> detail = flightOrderDetail(Long.parseLong(order.getId()));
        int passengers = parsePassengerCount(asString(detail.get("passenger_list")));
        jdbcTemplate.update(
                "update product set stock = stock + ?, sold_count = case when sold_count >= ? then sold_count - ? else 0 end, update_time = ? where id = ?",
                passengers,
                passengers,
                passengers,
                LocalDateTime.now(),
                detail.get("product_id")
        );
        return passengers;
    }

    private Map<String, Object> flightOrderDetail(long orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from flight_order_detail where order_id = ?", orderId);
        if (rows.isEmpty()) {
            throw new ApiException(404, "flight order detail not found");
        }
        return rows.get(0);
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
                return parseNonNegativeInt(asString(map.get("passengerCount")), 1);
            }
        } catch (Exception ignored) {
            return 1;
        }
        return 1;
    }

    private String toJson(Object value) {
        try {
            if (value == null) {
                return "[]";
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private LocalDateTime departureDateTime(ProductDTO flight) {
        LocalDate date = parseDate(textField(flight, "departureDate", "departure_date", "date"), null);
        LocalTime time = departureTime(flight);
        if (date == null || time == null) {
            return null;
        }
        return LocalDateTime.of(date, time);
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return fallback;
        }
    }

    private String statusText(int status) {
        return switch (status) {
            case STATUS_PENDING -> "待支付";
            case STATUS_PAID -> "已出票";
            case STATUS_CANCELED -> "已取消";
            case STATUS_REFUNDED -> "已退款";
            default -> "处理中";
        };
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = normalize(asString(value));
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }

    // 解析 product.extra。无效 JSON 不阻断接口，只视为没有航班扩展字段。
    private Map<String, Object> extra(ProductDTO flight) {
        if (flight == null || flight.getExtra() == null || flight.getExtra().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(flight.getExtra(), new TypeReference<HashMap<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    // 直接从 JDBC 行数据构造 ProductDTO，避免通用 ObjectMapper 在时间字段上的跨数据库差异。
    private ProductDTO toProduct(Map<String, Object> row) {
        ProductDTO product = new ProductDTO();
        product.setId(asString(first(row, "id", "ID")));
        product.setProduct_type(asString(first(row, "product_type", "PRODUCT_TYPE")));
        product.setName(asString(first(row, "name", "NAME")));
        product.setPrice(decimal(first(row, "price", "PRICE")));
        product.setStock(parseNonNegativeInt(asString(first(row, "stock", "STOCK")), 0));
        product.setSold_count(parseNonNegativeInt(asString(first(row, "sold_count", "SOLD_COUNT")), 0));
        product.setCategory_tags(asString(first(row, "category_tags", "CATEGORY_TAGS")));
        product.setStatus(parseNonNegativeInt(asString(first(row, "status", "STATUS")), 0));
        product.setExtra(asString(first(row, "extra", "EXTRA")));
        product.setCreate_time(localDateTime(first(row, "create_time", "CREATE_TIME")));
        product.setUpdate_time(localDateTime(first(row, "update_time", "UPDATE_TIME")));
        return product;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value == null || asString(value).isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(asString(value));
    }

    private LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value == null || asString(value).isBlank()) {
            return null;
        }
        String text = asString(value);
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text).toLocalDateTime();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    // 按多个候选字段名取第一个存在值，兼容驼峰和下划线字段。
    private Object first(Map<?, ?> map, String... keys) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String value(Map<String, String> params, String key) {
        if (params == null) {
            return null;
        }
        return params.get(key);
    }

    private String asString(Object value) {
        if (value == null) {
            return "";
        }
        // H2 测试库会把 extra CLOB 作为 Clob 对象返回，需要手动取出文本。
        if (value instanceof Clob clob) {
            try {
                return clob.getSubString(1, Math.toIntExact(clob.length()));
            } catch (Exception ex) {
                return "";
            }
        }
        return String.valueOf(value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private int parsePositiveInt(String value, int fallback) {
        int parsed = parseNonNegativeInt(value, fallback);
        return parsed <= 0 ? fallback : parsed;
    }

    private int parseNonNegativeInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Map<String, Object> paidResult(String orderNo, String method, int points) {
        return Map.of(
                "orderNo", orderNo,
                "status", STATUS_PAID,
                "statusText", "已出票",
                "paymentMethod", method == null ? "" : method,
                "pointsAdded", points,
                "ticketNo", "TKT" + orderNo.substring(Math.max(0, orderNo.length() - 10))
        );
    }
}
