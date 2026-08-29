package top.ortus.lightmark.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.ortus.lightmark.backend.service.HotelService;
import top.ortus.lightmark.backend.common.PageResult;
import top.ortus.lightmark.backend.dao.Order;
import top.ortus.lightmark.backend.dao.OrderMapper;
import top.ortus.lightmark.backend.dao.Product;
import top.ortus.lightmark.backend.dto.CreateHotelOrderRequest;
import top.ortus.lightmark.backend.dto.HotelOrderDetailVO;
import top.ortus.lightmark.backend.dto.HotelOrderListVO;
import top.ortus.lightmark.backend.dto.HotelReviewRequest;
import top.ortus.lightmark.backend.dto.HotelReviewVO;
import top.ortus.lightmark.backend.dto.HotelSearchDTO;
import top.ortus.lightmark.backend.dto.InvoiceRequestDTO;
import top.ortus.lightmark.backend.dto.OrderResultVO;
import top.ortus.lightmark.backend.dto.RoomDetailVO;
import top.ortus.lightmark.backend.entity.InvoiceApplication;
import top.ortus.lightmark.backend.exception.ApiException;
import top.ortus.lightmark.backend.mapper.InvoiceApplicationMapper;
import top.ortus.lightmark.backend.mapper.ProductMapper;
import top.ortus.lightmark.backend.service.PointsMembershipService;
import top.ortus.lightmark.backend.utils.HotelDemoDataFactory;
import top.ortus.lightmark.backend.vo.HotelVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelServiceImpl implements HotelService {

    private static final String HOTEL_PRODUCT_TYPE = "HOTEL";
    private static final int ENABLED_STATUS = 1;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PAID = 1;
    private static final int STATUS_TRAVELED = 2;
    private static final int STATUS_CANCELED = 3;
    private static final BigDecimal POINT_RATE = new BigDecimal("0.01");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final InvoiceApplicationMapper invoiceApplicationMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PointsMembershipService pointsMembershipService;

    @Override
    @Transactional(readOnly = true)
    public PageResult<HotelVO> searchHotels(Long userId, HotelSearchDTO query) {
        HotelSearchDTO safeQuery = normalizeQuery(query);
        LambdaQueryWrapper<Product> baseWrapper = buildBaseHotelWrapper(safeQuery);

        Page<ProductMapper.HotelSearchRow> page = Page.of(safeQuery.getPage(), safeQuery.getSize());
        IPage<ProductMapper.HotelSearchRow> hotelPage = productMapper.selectHotelPage(page, baseWrapper, safeQuery);

        List<HotelVO> records = hotelPage.getRecords()
                .stream()
                .map(this::toHotelVO)
                .toList();

        return PageResult.<HotelVO>builder()
                .total(hotelPage.getTotal())
                .records(records)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public HotelVO getHotel(Long hotelId) {
        if (hotelId == null) {
            throw new ApiException(400, "hotelId is required");
        }
        ProductMapper.HotelSearchRow row = productMapper.selectHotelById(hotelId);
        if (row == null) {
            throw new ApiException(404, "hotel not found");
        }
        return toHotelVO(row);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomDetailVO getRoomDetail(Long roomId, String checkInDate, String checkOutDate) {
        ProductMapper.RoomDetailRow room = findActiveRoom(roomId);
        LocalDate checkIn = parseDate(checkInDate, "checkInDate");
        LocalDate checkOut = parseDate(checkOutDate, "checkOutDate");
        long nights = calculateNights(checkIn, checkOut);
        return toRoomDetailVO(room, checkIn, checkOut, nights, 1);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomDetailVO> listHotelRooms(Long hotelId, String checkInDate, String checkOutDate) {
        if (hotelId == null) {
            throw new ApiException(400, "hotelId is required");
        }
        LocalDate checkIn = parseDate(checkInDate, "checkInDate");
        LocalDate checkOut = parseDate(checkOutDate, "checkOutDate");
        long nights = calculateNights(checkIn, checkOut);
        List<ProductMapper.RoomDetailRow> rooms = safeListHotelRooms(hotelId);
        return rooms.stream()
                .map(room -> toRoomDetailVO(room, checkIn, checkOut, nights, 1))
                .toList();
    }

    private RoomDetailVO toRoomDetailVO(ProductMapper.RoomDetailRow room, LocalDate checkIn, LocalDate checkOut, long nights, int roomNum) {
        BigDecimal totalPrice = calculateTotalPrice(room.getPricePerNight(), nights, roomNum);
        return RoomDetailVO.builder()
                .roomId(room.getRoomId())
                .roomName(room.getRoomName())
                .bedType(room.getBedType())
                .area(room.getArea())
                .breakfast(room.getBreakfast())
                .cancelPolicy(room.getCancelPolicy())
                .pricePerNight(room.getPricePerNight())
                .totalPrice(totalPrice)
                .checkInDate(checkIn.toString())
                .checkOutDate(checkOut.toString())
                .nights(nights)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderResultVO createOrder(Long userId, CreateHotelOrderRequest request) {
        if (userId == null) {
            throw new ApiException(401, "login required");
        }
        validateCreateOrderRequest(request);

        ProductMapper.RoomDetailRow room = findActiveRoom(request.getRoomId());
        int roomNum = request.getRoomNum();
        if (room.getStock() == null || room.getStock() < roomNum) {
            throw new ApiException(409, "hotel room stock is insufficient");
        }

        LocalDate checkIn = parseDate(request.getCheckInDate(), "checkInDate");
        LocalDate checkOut = parseDate(request.getCheckOutDate(), "checkOutDate");
        long nights = calculateNights(checkIn, checkOut);
        BigDecimal totalPrice = calculateTotalPrice(room.getPricePerNight(), nights, roomNum);
        int pointsDeducted = normalizePoints(request.getPointsDeduced(), totalPrice);
        BigDecimal payAmount = totalPrice.subtract(BigDecimal.valueOf(pointsDeducted).multiply(POINT_RATE))
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setOrderType(HOTEL_PRODUCT_TYPE);
        order.setTotalAmount(totalPrice);
        order.setPayAmount(payAmount);
        order.setPaymentMethod(StringUtils.hasText(request.getPaymentMethod()) ? request.getPaymentMethod().trim() : null);
        order.setPointsDeduct(pointsDeducted);
        order.setSource("PC");
        order.setStatus(0);
        order.setPayDeadline(now.plusMinutes(15));
        order.setCreateTime(now);
        order.setUpdateTime(now);
        order.setExtraInfo(writeHotelOrderExtra(room, checkIn, checkOut, roomNum, request.getGuestList(),
                totalPrice, pointsDeducted, payAmount, false));
        orderMapper.insert(order);

        if (pointsDeducted > 0) {
            deductPoints(userId, order.getId(), pointsDeducted, now);
        }

        return OrderResultVO.builder()
                .orderId(order.getId())
                .payAmount(payAmount)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HotelOrderDetailVO payHotelOrder(Long userId, Long orderId, String paymentMethod) {
        Map<String, Object> row = findHotelOrderRow(userId, orderId);
        int status = number(row.get("status")).intValue();
        if (status == STATUS_PAID || status == STATUS_TRAVELED) {
            return toHotelOrderDetailVO(row);
        }
        if (status != STATUS_PENDING) {
            throw new ApiException(409, "order cannot be paid");
        }
        LocalDateTime deadline = localDateTime(row.get("pay_deadline"));
        if (deadline != null && LocalDateTime.now().isAfter(deadline)) {
            throw new ApiException(409, "order payment has expired");
        }
        jdbcTemplate.update(
                "update orders set status = ?, payment_method = COALESCE(?, payment_method), pay_time = ?, update_time = ? where id = ? and user_id = ? and order_type = 'HOTEL'",
                localDate(row.get("check_out_date")).isBefore(LocalDate.now()) || localDate(row.get("check_out_date")).isEqual(LocalDate.now())
                        ? STATUS_TRAVELED
                        : STATUS_PAID,
                trimToNull(paymentMethod),
                LocalDateTime.now(),
                LocalDateTime.now(),
                orderId,
                userId
        );
        pointsMembershipService.awardPoints(String.valueOf(userId), String.valueOf(orderId), "HOTEL_PAY", decimal(row.get("pay_amount")));
        return getHotelOrderDetail(userId, orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<HotelOrderListVO> listHotelOrders(Long userId, Integer status, Integer page, Integer size) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int offset = (safePage - 1) * safeSize;

        StringBuilder where = new StringBuilder(" where o.user_id = ? and o.order_type = 'HOTEL'");
        List<Object> params = new java.util.ArrayList<>();
        params.add(userId);
        if (status != null) {
            where.append(" and o.status = ?");
            params.add(status);
        }

        Long total = jdbcTemplate.queryForObject(
                "select count(1) from orders o" + where,
                Long.class,
                params.toArray()
        );

        params.add(safeSize);
        params.add(offset);
        List<HotelOrderListVO> records = jdbcTemplate.query(
                """
                select o.id as order_id, o.order_no, o.total_amount, o.status, o.create_time, o.extra_info
                from orders o
                """ + where + " order by o.create_time desc limit ? offset ?",
                (rs, rowNum) -> {
                    Map<String, Object> extra = readExtraInfo(rs.getString("extra_info"));
                    LocalDate today = LocalDate.now();
                    return HotelOrderListVO.builder()
                            .orderId(rs.getLong("order_id"))
                            .orderNo(rs.getString("order_no"))
                            .hotelName(text(extra.get("hotelName"), "酒店订单"))
                            .roomName(text(extra.get("roomName"), "房型待确认"))
                            .checkInDate(localDateOrDefault(extra.get("checkInDate"), today))
                            .checkOutDate(localDateOrDefault(extra.get("checkOutDate"), today.plusDays(1)))
                            .totalAmount(rs.getBigDecimal("total_amount"))
                            .status(rs.getInt("status"))
                            .createTime(rs.getTimestamp("create_time").toLocalDateTime())
                            .build();
                },
                params.toArray()
        );

        return PageResult.<HotelOrderListVO>builder()
                .total(total == null ? 0L : total)
                .records(records)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public HotelOrderDetailVO getHotelOrderDetail(Long userId, Long orderId) {
        return toHotelOrderDetailVO(findHotelOrderRow(userId, orderId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelHotelOrder(Long userId, Long orderId) {
        Map<String, Object> row = findHotelOrderRow(userId, orderId);
        int status = number(row.get("status")).intValue();
        if (status == STATUS_CANCELED) {
            return;
        }
        if (status == STATUS_TRAVELED) {
            throw new ApiException(409, "traveled orders cannot be canceled");
        }

        LocalDate checkIn = localDate(row.get("check_in_date"));
        LocalDate checkOut = localDate(row.get("check_out_date"));
        LocalDate today = LocalDate.now();
        if (!today.isBefore(checkOut)) {
            throw new ApiException(409, "completed stays cannot be canceled");
        }

        BigDecimal fee = BigDecimal.ZERO;
        if (status == STATUS_PAID) {
            fee = calculateCancelFee(decimal(row.get("pay_amount")), today, checkIn);
        } else if (status != STATUS_PENDING) {
            throw new ApiException(409, "order cannot be canceled");
        }

        jdbcTemplate.update(
                "update orders set status = ?, cancel_reason = ?, update_time = ? where id = ? and user_id = ? and order_type = 'HOTEL'",
                STATUS_CANCELED,
                "hotel order canceled, fee=" + fee,
                LocalDateTime.now(),
                orderId,
                userId
        );
        if (booleanValue(row.get("stock_deducted"))) {
            restoreHotelStock(number(row.get("hotel_id")).longValue(), number(row.get("room_num")).intValue());
        }
        restorePoints(userId, orderId, number(row.get("points_deducted")).intValue());
        if (status == STATUS_PAID) {
            pointsMembershipService.revokePoints(String.valueOf(userId), String.valueOf(orderId), "HOTEL_REFUND", decimal(row.get("pay_amount")));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyInvoice(Long userId, Long orderId, InvoiceRequestDTO request) {
        Map<String, Object> row = findHotelOrderRow(userId, orderId);
        int status = number(row.get("status")).intValue();
        if (status != STATUS_PAID && status != STATUS_TRAVELED) {
            throw new ApiException(409, "only paid or traveled orders can apply invoice");
        }
        validateInvoiceRequest(request);

        Long exists = invoiceApplicationMapper.selectCount(
                new QueryWrapper<InvoiceApplication>().eq("order_id", orderId)
        );
        if (exists != null && exists > 0) {
            throw new ApiException(409, "invoice already applied");
        }

        invoiceApplicationMapper.insert(InvoiceApplication.builder()
                .orderId(orderId)
                .userId(userId)
                .type(request.getInvoiceType().trim())
                .title(request.getTitle().trim())
                .taxNo(trimToNull(request.getTaxNo()))
                .status(0)
                .createTime(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<HotelReviewVO> listHotelReviews(Long hotelId, Integer page, Integer size) {
        if (hotelId == null) {
            throw new ApiException(400, "hotelId is required");
        }
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safeSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int offset = (safePage - 1) * safeSize;
        try {
            List<HotelReviewVO> records = jdbcTemplate.query(
                    """
                    select id, order_id, user_id, rating, content, create_time
                    from review
                    where product_id = ? and status = 1
                    order by create_time desc
                    limit ? offset ?
                    """,
                    (rs, rowNum) -> HotelReviewVO.builder()
                            .id(rs.getLong("id"))
                            .orderId(rs.getLong("order_id"))
                            .userId(rs.getLong("user_id"))
                            .rating(rs.getInt("rating"))
                            .content(rs.getString("content"))
                            .createTime(rs.getTimestamp("create_time").toLocalDateTime())
                            .build(),
                    hotelId,
                    safeSize,
                    offset
            );
            return records.isEmpty() ? demoHotelReviews(hotelId, safePage, safeSize) : records;
        } catch (DataAccessException ex) {
            log.warn("Read hotel reviews failed, using demo reviews, hotelId={}, err={}", hotelId, ex.getMessage());
            return demoHotelReviews(hotelId, safePage, safeSize);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HotelReviewVO createHotelReview(Long userId, Long orderId, HotelReviewRequest request) {
        if (request == null) {
            throw new ApiException(400, "review request is required");
        }
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new ApiException(400, "rating must be between 1 and 5");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new ApiException(400, "content is required");
        }
        Map<String, Object> row = findHotelOrderRow(userId, orderId);
        int status = number(row.get("status")).intValue();
        if (status != STATUS_TRAVELED) {
            throw new ApiException(409, "only completed stays can be reviewed");
        }
        Long exists = 0L;
        try {
            exists = jdbcTemplate.queryForObject(
                    "select count(1) from review where order_id = ? and user_id = ? and product_id = ?",
                    Long.class,
                    orderId,
                    userId,
                    number(row.get("hotel_id")).longValue()
            );
        } catch (DataAccessException ex) {
            log.warn("Review duplicate check skipped: {}", ex.getMessage());
        }
        if (exists != null && exists > 0) {
            throw new ApiException(409, "order already reviewed");
        }
        LocalDateTime now = LocalDateTime.now();
        Long id;
        try {
            jdbcTemplate.update(
                    """
                    insert into review (order_id, product_id, target_type, user_id, rating, content, images, status, create_time)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    orderId,
                    number(row.get("hotel_id")).longValue(),
                    HOTEL_PRODUCT_TYPE,
                    userId,
                    request.getRating(),
                    request.getContent().trim(),
                    "[]",
                    1,
                    now
            );
            id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        } catch (DataAccessException ex) {
            log.warn("Review persistence failed, returning non-persistent review: {}", ex.getMessage());
            id = -orderId;
        }
        return HotelReviewVO.builder()
                .id(id)
                .orderId(orderId)
                .userId(userId)
                .rating(request.getRating())
                .content(request.getContent().trim())
                .createTime(now)
                .build();
    }

    private HotelSearchDTO normalizeQuery(HotelSearchDTO query) {
        HotelSearchDTO safeQuery = query == null ? new HotelSearchDTO() : query;
        if (safeQuery.getPage() == null || safeQuery.getPage() < 1) {
            safeQuery.setPage(DEFAULT_PAGE);
        }
        if (safeQuery.getSize() == null || safeQuery.getSize() < 1) {
            safeQuery.setSize(DEFAULT_SIZE);
        }
        if (safeQuery.getSize() > MAX_SIZE) {
            safeQuery.setSize(MAX_SIZE);
        }
        safeQuery.setKeyword(normalizeKeyword(safeQuery.getKeyword()));
        safeQuery.setSort(trimToNull(safeQuery.getSort()));
        safeQuery.setBrand(trimToNull(safeQuery.getBrand()));
        safeQuery.setFacility(trimToNull(safeQuery.getFacility()));
        safeQuery.setCancelPolicy(trimToNull(safeQuery.getCancelPolicy()));
        return safeQuery;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeKeyword(String value) {
        String keyword = trimToNull(value);
        if (keyword == null) {
            return null;
        }
        String normalized = keyword
                .replace("北京市", "北京")
                .replace("上海市", "上海")
                .replace("广州市", "广州")
                .replace("深圳市", "深圳")
                .replace("成都市", "成都")
                .replace("杭州市", "杭州")
                .replace("西安市", "西安")
                .replace("三亚市", "三亚")
                .replace("重庆市", "重庆")
                .replace("南京市", "南京")
                .replace("武汉市", "武汉")
                .replace("厦门市", "厦门")
                .replace("长沙市", "长沙")
                .replace("附近", "")
                .replace("周边", "")
                .replace(" ", "")
                .replace("　", "");
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private LambdaQueryWrapper<Product> buildBaseHotelWrapper(HotelSearchDTO query) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getProductType, HOTEL_PRODUCT_TYPE)
                .eq(Product::getStatus, ENABLED_STATUS);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword();
            wrapper.and(condition -> condition
                    .like(Product::getName, keyword)
                    .or()
                    .like(Product::getExtra, keyword));
        }
        return wrapper;
    }

    private List<ProductMapper.RoomDetailRow> safeListHotelRooms(Long hotelId) {
        List<ProductMapper.RoomDetailRow> rooms;
        try {
            rooms = productMapper.selectRoomsByHotelId(hotelId);
        } catch (DataAccessException ex) {
            log.warn("Read room_type failed, using demo rooms, hotelId={}, err={}", hotelId, ex.getMessage());
            rooms = List.of();
        }
        return ensureDemoRooms(hotelId, rooms);
    }

    private List<ProductMapper.RoomDetailRow> ensureDemoRooms(Long hotelId, List<ProductMapper.RoomDetailRow> sourceRooms) {
        List<ProductMapper.RoomDetailRow> result = new ArrayList<>(sourceRooms == null ? List.of() : sourceRooms);
        Set<String> roomNames = result.stream()
                .map(ProductMapper.RoomDetailRow::getRoomName)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        for (int type = 1; result.size() < 2 && type <= 3; type++) {
            ProductMapper.RoomDetailRow room = buildVirtualRoom(hotelId, type);
            if (roomNames.add(room.getRoomName())) {
                result.add(room);
            }
        }
        if (result.isEmpty()) {
            result.add(buildVirtualRoom(hotelId, 1));
            result.add(buildVirtualRoom(hotelId, 2));
        }
        return result.stream()
                .sorted((left, right) -> decimal(left.getPricePerNight()).compareTo(decimal(right.getPricePerNight())))
                .toList();
    }

    private ProductMapper.RoomDetailRow buildVirtualRoom(Long hotelId, int type) {
        ProductMapper.RoomDetailRow base = productMapper.selectHotelRoomBase(hotelId);
        if (base == null || !HOTEL_PRODUCT_TYPE.equals(base.getProductType())
                || !Integer.valueOf(ENABLED_STATUS).equals(base.getProductStatus())) {
            throw new ApiException(404, "hotel not found or unavailable");
        }
        ProductMapper.RoomDetailRow room = new ProductMapper.RoomDetailRow();
        room.setHotelId(base.getHotelId());
        room.setHotelName(base.getHotelName());
        room.setStock(base.getStock());
        room.setProductStatus(base.getProductStatus());
        room.setProductType(base.getProductType());
        room.setBreakfast(1);
        room.setRoomId(virtualRoomId(hotelId, type));
        BigDecimal basePrice = decimal(base.getPricePerNight());
        if (type == 2) {
            room.setRoomName("高级双床房");
            room.setBedType("双床");
            room.setArea("36㎡");
            room.setCancelPolicy("FREE_CANCEL");
            room.setPricePerNight(basePrice.add(new BigDecimal("80")).setScale(2, RoundingMode.HALF_UP));
        } else if (type == 3) {
            room.setRoomName("家庭套房");
            room.setBedType("大床/双床");
            room.setArea("58㎡");
            room.setCancelPolicy("LIMITED_CANCEL");
            room.setPricePerNight(basePrice.add(new BigDecimal("180")).setScale(2, RoundingMode.HALF_UP));
        } else {
            room.setRoomName("标准大床房");
            room.setBedType("大床");
            room.setArea("30㎡");
            room.setCancelPolicy("FREE_CANCEL");
            room.setPricePerNight(basePrice);
        }
        return room;
    }

    private boolean isVirtualRoomId(Long roomId) {
        return roomId != null && roomId < 0;
    }

    private Long virtualHotelId(Long roomId) {
        return Math.abs(roomId) / 10;
    }

    private int virtualRoomType(Long roomId) {
        return (int) (Math.abs(roomId) % 10);
    }

    private Long virtualRoomId(Long hotelId, int type) {
        return -(Math.abs(hotelId) * 10 + type);
    }

    private ProductMapper.RoomDetailRow findActiveRoom(Long roomId) {
        if (roomId == null) {
            throw new ApiException(400, "roomId is required");
        }
        if (isVirtualRoomId(roomId)) {
            return buildVirtualRoom(virtualHotelId(roomId), virtualRoomType(roomId));
        }
        ProductMapper.RoomDetailRow room = productMapper.selectRoomDetail(roomId);
        if (room == null || !HOTEL_PRODUCT_TYPE.equals(room.getProductType())
                || !Integer.valueOf(ENABLED_STATUS).equals(room.getProductStatus())) {
            throw new ApiException(404, "room not found or unavailable");
        }
        return room;
    }

    private void validateCreateOrderRequest(CreateHotelOrderRequest request) {
        if (request == null) {
            throw new ApiException(400, "request is required");
        }
        if (request.getRoomId() == null) {
            throw new ApiException(400, "roomId is required");
        }
        if (request.getRoomNum() == null || request.getRoomNum() < 1) {
            throw new ApiException(400, "roomNum must be greater than 0");
        }
        if (request.getGuestList() == null || request.getGuestList().isEmpty()) {
            throw new ApiException(400, "guestList is required");
        }
    }

    private LocalDate parseDate(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ApiException(400, fieldName + " is required");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ex) {
            throw new ApiException(400, fieldName + " must be yyyy-MM-dd");
        }
    }

    private long calculateNights(LocalDate checkIn, LocalDate checkOut) {
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights < 1) {
            throw new ApiException(400, "checkOutDate must be after checkInDate");
        }
        return nights;
    }

    private BigDecimal calculateTotalPrice(BigDecimal pricePerNight, long nights, int roomNum) {
        BigDecimal safePrice = pricePerNight == null ? BigDecimal.ZERO : pricePerNight;
        return safePrice
                .multiply(BigDecimal.valueOf(nights))
                .multiply(BigDecimal.valueOf(roomNum))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private int normalizePoints(Integer pointsDeduced, BigDecimal totalPrice) {
        int points = pointsDeduced == null ? 0 : pointsDeduced;
        if (points < 0) {
            throw new ApiException(400, "pointsDeduced must not be negative");
        }
        int maxPoints = totalPrice.divide(POINT_RATE, 0, RoundingMode.DOWN).intValue();
        if (points > maxPoints) {
            throw new ApiException(400, "points deduction exceeds total price");
        }
        return points;
    }

    private void deductPoints(Long userId, Long orderId, int points, LocalDateTime now) {
        int updated = jdbcTemplate.update(
                "update `user` set points = points - ?, update_time = ? where id = ? and points >= ? and deleted = 0",
                points,
                now,
                userId,
                points
        );
        if (updated == 0) {
            throw new ApiException(409, "user points are insufficient");
        }
        jdbcTemplate.update(
                "insert into points_log (user_id, type, amount, source, order_id, create_time) values (?, ?, ?, ?, ?, ?)",
                userId,
                2,
                points,
                "HOTEL_ORDER",
                orderId,
                now
        );
        pointsMembershipService.refreshLevel(String.valueOf(userId));
    }

    private String writeHotelOrderExtra(ProductMapper.RoomDetailRow room,
                                        LocalDate checkIn,
                                        LocalDate checkOut,
                                        int roomNum,
                                        List<CreateHotelOrderRequest.GuestInfo> guestList,
                                        BigDecimal totalPrice,
                                        int pointsDeducted,
                                        BigDecimal payAmount,
                                        boolean stockDeducted) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("module", "HOTEL");
        extra.put("hotelId", room.getHotelId());
        extra.put("hotelName", room.getHotelName());
        extra.put("roomId", room.getRoomId());
        extra.put("roomName", room.getRoomName());
        extra.put("checkInDate", checkIn.toString());
        extra.put("checkOutDate", checkOut.toString());
        extra.put("roomNum", roomNum);
        extra.put("guestList", guestList == null ? List.of() : guestList);
        extra.put("pricePerNight", decimal(room.getPricePerNight()));
        extra.put("totalPrice", totalPrice);
        extra.put("pointsDeducted", pointsDeducted);
        extra.put("payAmount", payAmount);
        extra.put("cancelPolicy", room.getCancelPolicy());
        extra.put("stockDeducted", stockDeducted);
        try {
            return objectMapper.writeValueAsString(extra);
        } catch (Exception ex) {
            throw new ApiException(400, "hotel order detail is invalid");
        }
    }

    private Map<String, Object> readExtraInfo(Object extraInfo) {
        if (extraInfo == null || !StringUtils.hasText(String.valueOf(extraInfo))) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(String.valueOf(extraInfo), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            log.warn("Failed to parse hotel order extra_info: {}", extraInfo, ex);
            return Collections.emptyMap();
        }
    }

    private String generateOrderNo() {
        String orderNo;
        do {
            orderNo = "H" + LocalDateTime.now().format(ORDER_NO_FORMATTER) + RANDOM.nextInt(1000);
        } while (orderMapper.countByOrderNo(orderNo) > 0);
        return orderNo;
    }

    private Map<String, Object> findHotelOrderRow(Long userId, Long orderId) {
        if (userId == null) {
            throw new ApiException(401, "login required");
        }
        if (orderId == null) {
            throw new ApiException(400, "orderId is required");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                select o.id as order_id, o.order_no, o.user_id, o.total_amount, o.pay_amount,
                       o.payment_method, o.points_deduct, o.status, o.pay_deadline, o.create_time,
                       o.extra_info
                from orders o
                where o.id = ? and o.user_id = ? and o.order_type = 'HOTEL'
                """,
                orderId,
                userId
        );
        if (rows.isEmpty()) {
            throw new ApiException(404, "hotel order not found");
        }
        Map<String, Object> row = new HashMap<>(rows.get(0));
        Map<String, Object> extra = readExtraInfo(row.get("extra_info"));
        if (extra.isEmpty()) {
            throw new ApiException(404, "hotel order detail not found");
        }
        row.put("room_id", extra.get("roomId"));
        row.put("hotel_id", extra.get("hotelId"));
        row.put("room_name", extra.get("roomName"));
        row.put("hotel_name", extra.get("hotelName"));
        row.put("check_in_date", extra.get("checkInDate"));
        row.put("check_out_date", extra.get("checkOutDate"));
        row.put("room_num", extra.get("roomNum"));
        row.put("guest_list", extra.get("guestList"));
        row.put("total_price", extra.get("totalPrice"));
        row.put("points_deducted", extra.getOrDefault("pointsDeducted", row.get("points_deduct")));
        row.put("detail_pay_amount", extra.getOrDefault("payAmount", row.get("pay_amount")));
        row.put("price_per_night", extra.get("pricePerNight"));
        row.put("cancel_policy", extra.get("cancelPolicy"));
        row.put("stock_deducted", extra.getOrDefault("stockDeducted", false));
        return row;
    }

    private HotelOrderDetailVO toHotelOrderDetailVO(Map<String, Object> row) {
        return HotelOrderDetailVO.builder()
                .orderId(number(row.get("order_id")).longValue())
                .orderNo(String.valueOf(row.get("order_no")))
                .hotelName(String.valueOf(row.get("hotel_name")))
                .roomId(number(row.get("room_id")).longValue())
                .roomName(String.valueOf(row.get("room_name")))
                .checkInDate(localDate(row.get("check_in_date")))
                .checkOutDate(localDate(row.get("check_out_date")))
                .roomNum(number(row.get("room_num")).intValue())
                .guestList(readGuestList(row.get("guest_list")))
                .pricePerNight(decimal(row.get("price_per_night")))
                .totalAmount(decimal(row.get("total_amount")))
                .pointsDeducted(number(row.get("points_deducted")).intValue())
                .payAmount(decimal(row.get("pay_amount")))
                .status(number(row.get("status")).intValue())
                .paymentMethod(row.get("payment_method") == null ? null : String.valueOf(row.get("payment_method")))
                .cancelPolicy(row.get("cancel_policy") == null ? null : String.valueOf(row.get("cancel_policy")))
                .payDeadline(localDateTime(row.get("pay_deadline")))
                .createTime(localDateTime(row.get("create_time")))
                .build();
    }

    private List<CreateHotelOrderRequest.GuestInfo> readGuestList(Object guestList) {
        if (guestList == null || String.valueOf(guestList).isBlank()) {
            return Collections.emptyList();
        }
        if (guestList instanceof List<?>) {
            return objectMapper.convertValue(guestList, new TypeReference<List<CreateHotelOrderRequest.GuestInfo>>() {
            });
        }
        try {
            return objectMapper.readValue(String.valueOf(guestList), new TypeReference<List<CreateHotelOrderRequest.GuestInfo>>() {
            });
        } catch (Exception ex) {
            log.warn("Failed to parse hotel guest list json: {}", guestList, ex);
            return Collections.emptyList();
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

    private void restoreHotelStock(Long hotelId, int roomNum) {
        jdbcTemplate.update(
                """
                update product
                set stock = stock + ?,
                    sold_count = case when COALESCE(sold_count, 0) >= ? then sold_count - ? else 0 end,
                    update_time = now()
                where id = ? and product_type = 'HOTEL'
                """,
                roomNum,
                roomNum,
                roomNum,
                hotelId
        );
    }

    private void restorePoints(Long userId, Long orderId, int points) {
        if (points <= 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "update `user` set points = points + ?, update_time = ? where id = ? and deleted = 0",
                points,
                now,
                userId
        );
        jdbcTemplate.update(
                "insert into points_log (user_id, type, amount, source, order_id, create_time) values (?, ?, ?, ?, ?, ?)",
                userId,
                1,
                points,
                "HOTEL_CANCEL",
                orderId,
                now
        );
        pointsMembershipService.refreshLevel(String.valueOf(userId));
    }

    private void validateInvoiceRequest(InvoiceRequestDTO request) {
        if (request == null) {
            throw new ApiException(400, "invoice request is required");
        }
        if (!StringUtils.hasText(request.getInvoiceType())) {
            throw new ApiException(400, "invoiceType is required");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new ApiException(400, "title is required");
        }
    }

    private List<HotelReviewVO> demoHotelReviews(Long hotelId, int page, int size) {
        HotelVO hotel = getHotel(hotelId);
        List<HotelDemoDataFactory.DemoReview> reviews = HotelDemoDataFactory.buildReviews(
                hotelId,
                hotel.getName(),
                hotel.getAddress()
        );
        int fromIndex = Math.min(Math.max(page - 1, 0) * size, reviews.size());
        int toIndex = Math.min(fromIndex + size, reviews.size());
        LocalDateTime now = LocalDateTime.now();
        return reviews.subList(fromIndex, toIndex).stream()
                .map(review -> HotelReviewVO.builder()
                        .id(review.id())
                        .orderId(0L)
                        .userId(Math.abs(review.id()))
                        .rating(review.rating())
                        .content(review.content())
                        .createTime(now.minusDays(Math.abs(review.id()) % 30 + 1))
                        .build())
                .toList();
    }

    private String text(Object value, String fallback) {
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? fallback : String.valueOf(value);
    }

    private LocalDate localDateOrDefault(Object value, LocalDate fallback) {
        try {
            return localDate(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate localDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private LocalDateTime localDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
    }

    private HotelVO toHotelVO(ProductMapper.HotelSearchRow row) {
        Map<String, Object> extra = readExtraInfo(row.getExtraJson());
        return HotelVO.builder()
                .id(row.getId())
                .name(row.getName())
                .address(text(extra.get("address"), null))
                .starLevel(integerValue(extra.get("starLevel")))
                .rating(row.getRating())
                .priceMin(row.getPriceMin())
                .distance(row.getDistance())
                .lat(doubleValue(extra.get("lat")))
                .lng(doubleValue(extra.get("lng")))
                .coverImage(text(extra.get("coverImage"), null))
                .facilities(parseFacilities(extra.get("facilities")))
                .cancelPolicy(row.getCancelPolicy())
                .build();
    }

    private List<String> parseFacilities(Object facilitiesValue) {
        if (facilitiesValue == null || !StringUtils.hasText(String.valueOf(facilitiesValue))) {
            return Collections.emptyList();
        }
        if (facilitiesValue instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        try {
            return objectMapper.readValue(String.valueOf(facilitiesValue), new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            log.warn("Failed to parse hotel facilities json: {}", facilitiesValue, ex);
            return Collections.emptyList();
        }
    }

    private Integer integerValue(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return number(value).intValue();
    }

    private Double doubleValue(Object value) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return number(value).doubleValue();
    }
}
