package top.ortus.lightmark.order.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.ortus.lightmark.order.service.OrderService;
import top.ortus.lightmark.order.tools.ApiException;
import top.ortus.lightmark.order.tools.ApiResponse;
import top.ortus.lightmark.order.tools.security.JwtTokenService;
import top.ortus.lightmark.order.tools.security.UserIdentity;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;
    private final JwtTokenService jwtTokenService;

    public OrderController(OrderService orderService, JwtTokenService jwtTokenService) {
        this.orderService = orderService;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/flights/order/preview")
    public ApiResponse<Map<String, Object>> previewFlightOrder(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(orderService.previewFlightOrder(null, payload));
    }

    @PostMapping("/flights/order")
    public ApiResponse<Map<String, Object>> createFlightOrder(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                              @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(orderService.createFlightOrder(resolveUserId(authorization), payload));
    }

    @PostMapping("/hotel/order")
    public ApiResponse<Map<String, Object>> createHotelOrder(@RequestHeader("Authorization") String authorization,
                                                             @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(orderService.createHotelOrder(resolveUserId(authorization), payload));
    }

    @PostMapping("/hotel/order/{orderId}/pay")
    public ApiResponse<Map<String, Object>> payHotelOrder(@RequestHeader("Authorization") String authorization,
                                                          @PathVariable Long orderId,
                                                          @RequestBody(required = false) Map<String, Object> payload) {
        String paymentMethod = payload == null ? null : (String) payload.get("paymentMethod");
        return ApiResponse.ok(orderService.payHotelOrder(resolveUserId(authorization), orderId, paymentMethod));
    }

    @GetMapping("/hotel/orders")
    public ApiResponse<Map<String, Object>> listHotelOrders(@RequestHeader("Authorization") String authorization,
                                                            @RequestParam(required = false) Integer status,
                                                            @RequestParam(defaultValue = "1") Integer page,
                                                            @RequestParam(defaultValue = "10") Integer size) {
        return ApiResponse.ok(orderService.listHotelOrders(resolveUserId(authorization), status, page, size));
    }

    @GetMapping("/hotel/order/{orderId}")
    public ApiResponse<Map<String, Object>> getHotelOrderDetail(@RequestHeader("Authorization") String authorization,
                                                                @PathVariable Long orderId) {
        return ApiResponse.ok(orderService.getHotelOrderDetail(resolveUserId(authorization), orderId));
    }

    @PostMapping("/hotel/order/{orderId}/cancel")
    public ApiResponse<Void> cancelHotelOrder(@RequestHeader("Authorization") String authorization,
                                              @PathVariable Long orderId) {
        orderService.cancelHotelOrder(resolveUserId(authorization), orderId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/hotel/order/{orderId}/invoice")
    public ApiResponse<Void> applyInvoice(@RequestHeader("Authorization") String authorization,
                                          @PathVariable Long orderId,
                                          @RequestBody Map<String, Object> payload) {
        orderService.applyInvoice(resolveUserId(authorization), orderId, payload);
        return ApiResponse.ok(null);
    }

    @GetMapping("/hotel/{hotelId}/reviews")
    public ApiResponse<List<Map<String, Object>>> listHotelReviews(@PathVariable Long hotelId,
                                                                   @RequestParam(defaultValue = "1") Integer page,
                                                                   @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.ok(orderService.listHotelReviews(hotelId, page, size));
    }

    @PostMapping("/hotel/order/{orderId}/review")
    public ApiResponse<Map<String, Object>> createHotelReview(@RequestHeader("Authorization") String authorization,
                                                              @PathVariable Long orderId,
                                                              @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(orderService.createHotelReview(resolveUserId(authorization), orderId, payload));
    }

    @PostMapping("/trains/order")
    public ApiResponse<Map<String, Object>> createTrainOrder(@RequestHeader("Authorization") String authorization,
                                                             @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(orderService.createTrainOrder(resolveUserId(authorization), payload));
    }

    @PostMapping("/vacations/order")
    public ApiResponse<Map<String, Object>> createVacationOrder(@RequestHeader("Authorization") String authorization,
                                                                @RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(orderService.createVacationOrder(resolveUserId(authorization), payload));
    }

    @PostMapping("/orders/{orderNo}/pay")
    public ApiResponse<Map<String, Object>> payOrder(@PathVariable String orderNo,
                                                     @RequestBody(required = false) Map<String, Object> payload) {
        return ApiResponse.ok(orderService.payOrder(orderNo, payload));
    }

    @PostMapping("/orders/{orderNo}/refund")
    public ApiResponse<Map<String, Object>> refundOrder(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.refundOrder(orderNo));
    }

    @GetMapping("/orders/{orderNo}/change")
    public ApiResponse<Map<String, Object>> previewTrainChange(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.previewTrainChange(orderNo));
    }

    @PostMapping("/orders/{orderNo}/change")
    public ApiResponse<Map<String, Object>> changeOrder(@PathVariable String orderNo,
                                                        @RequestBody Map<String, Object> payload) {
        String targetProductId = payload == null ? null : String.valueOf(payload.get("targetProductId"));
        if (targetProductId == null || targetProductId.isBlank()) {
            targetProductId = payload == null ? null : String.valueOf(payload.get("productId"));
        }
        if (targetProductId == null || targetProductId.isBlank()) {
            throw new ApiException(400, "targetProductId is required");
        }
        if (payload != null && payload.containsKey("flight")) {
            return ApiResponse.ok(orderService.changeFlightOrder(orderNo, targetProductId));
        }
        return ApiResponse.ok(orderService.changeTrainOrder(orderNo, targetProductId));
    }

    @GetMapping("/orders/{orderNo}")
    public ApiResponse<Map<String, Object>> getOrderByNo(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getOrderByNo(orderNo));
    }

    @GetMapping("/orders")
    public ApiResponse<Map<String, Object>> listOrders(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                       @RequestParam(required = false) Long userId,
                                                       @RequestParam(required = false) String type,
                                                       @RequestParam(required = false) Integer status,
                                                       @RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "10") Integer size) {
        Long currentUserId = userId != null ? userId : resolveOptionalUserId(authorization);
        return ApiResponse.ok(orderService.listOrders(currentUserId, type, status, page, size));
    }

    /** 用户中心"我的订单"(单体遗留入口,由订单域直接提供,返回结构同 /api/orders) */
    @GetMapping("/user/orders")
    public ApiResponse<Map<String, Object>> listUserCenterOrders(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                 @RequestParam(required = false) Long userId,
                                                                 @RequestParam(required = false) String type,
                                                                 @RequestParam(required = false) Integer status,
                                                                 @RequestParam(defaultValue = "1") Integer page,
                                                                 @RequestParam(defaultValue = "10") Integer size) {
        return listOrders(authorization, userId, type, status, page, size);
    }

    @PostMapping("/orders/{orderNo}/cancel")
    public ApiResponse<Void> cancelOrder(@PathVariable String orderNo) {
        orderService.cancelOrder(orderNo);
        return ApiResponse.ok(null);
    }

    @GetMapping("/orders/{orderNo}/status")
    public ApiResponse<Map<String, Object>> orderStatus(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.orderStatus(orderNo));
    }

    @PostMapping("/orders/{orderNo}/payment/callback")
    public ApiResponse<Boolean> paymentCallback(@RequestBody Map<String, Object> payload) {
        return ApiResponse.ok(orderService.paymentCallback(payload));
    }

    @GetMapping("/admin/orders")
    public ApiResponse<Map<String, Object>> adminListOrders(@RequestHeader("Authorization") String authorization,
                                                            @RequestParam(required = false) Integer status,
                                                            @RequestParam(defaultValue = "1") Integer page) {
        resolveAdminId(authorization);
        return ApiResponse.ok(orderService.adminListOrders(status, page));
    }

    @PutMapping("/admin/orders/{orderNo}/status")
    public ApiResponse<Boolean> updateOrderStatus(@RequestHeader("Authorization") String authorization,
                                                  @PathVariable String orderNo,
                                                  @RequestBody Map<String, Object> payload) {
        Long adminId = resolveAdminId(authorization);
        int statusValue = payload == null ? 0 : Integer.parseInt(String.valueOf(payload.getOrDefault("status", 0)));
        String remark = payload == null ? null : String.valueOf(payload.getOrDefault("remark", ""));
        return ApiResponse.ok(orderService.updateOrderStatus(orderNo, statusValue, remark, adminId));
    }

    @PostMapping("/admin/orders/{orderNo}/refund")
    public ApiResponse<Boolean> refundOrder(@RequestHeader("Authorization") String authorization,
                                            @PathVariable String orderNo,
                                            @RequestBody(required = false) Map<String, Object> payload) {
        Long adminId = resolveAdminId(authorization);
        String remark = payload == null ? null : String.valueOf(payload.getOrDefault("remark", ""));
        return ApiResponse.ok(orderService.refundOrder(orderNo, remark, adminId));
    }

    private Long resolveUserId(String authorization) {
        Long userId = resolveOptionalUserId(authorization);
        if (userId == null || userId <= 0) {
            throw new ApiException(401, "login required");
        }
        return userId;
    }

    private Long resolveOptionalUserId(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String token = authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : authorization;
        return jwtTokenService.resolveUserId(token);
    }

    private Long resolveAdminId(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new ApiException(401, "admin required");
        }
        String token = authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : authorization;
        if (jwtTokenService.resolveIdentity(token) != UserIdentity.ADMIN) {
            throw new ApiException(403, "admin required");
        }
        Long adminId = jwtTokenService.resolveUserId(token);
        if (adminId == null) {
            throw new ApiException(401, "admin required");
        }
        return adminId;
    }
}
