package top.ortus.lightmark.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.ortus.lightmark.backend.JwtTokenService;
import top.ortus.lightmark.backend.common.ApiResponse;
import top.ortus.lightmark.backend.dao.Order;
import top.ortus.lightmark.backend.dto.module.TrainChangeConfirmRequest;
import top.ortus.lightmark.backend.dto.module.TrainChangeResponse;
import top.ortus.lightmark.backend.dto.module.TrainChangePreviewResponse;
import top.ortus.lightmark.backend.dto.module.TrainOrderRequest;
import top.ortus.lightmark.backend.dto.module.TrainOrderResponse;
import top.ortus.lightmark.backend.dto.module.TrainRefundResponse;
import top.ortus.lightmark.backend.dto.module.VacationAssistantResponse;
import top.ortus.lightmark.backend.dto.module.VacationOrderRequest;
import top.ortus.lightmark.backend.dto.module.VacationRefundResponse;
import top.ortus.lightmark.backend.service.OrderService;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @PostMapping("/train")
    public ApiResponse<TrainOrderResponse> createTrainOrder(@RequestHeader("Authorization") String authorization,
                                                            @RequestBody TrainOrderRequest request) {
        Long userId = resolveUserId(authorization);
        return ApiResponse.ok(orderService.createTrainOrder(userId, request));
    }

    @PostMapping("/vacation")
    public ApiResponse<TrainOrderResponse> createVacationOrder(@RequestHeader("Authorization") String authorization,
                                                               @RequestBody VacationOrderRequest request) {
        Long userId = resolveUserId(authorization);
        return ApiResponse.ok(orderService.createVacationOrder(userId, request));
    }

    private Long resolveUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return 0L;
        }
        Long userId = jwtTokenService.resolveUserId(authorization.substring("Bearer ".length()));
        return userId == null ? 0L : userId;
    }

    @PostMapping("/train/{orderNo}/pay")
    public ApiResponse<TrainOrderResponse> payOrder(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.payOrder(orderNo));
    }

    @PostMapping("/train/{orderNo}/refund")
    public ApiResponse<TrainRefundResponse> refundTrainOrder(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.refundTrainOrder(orderNo));
    }

    @PostMapping("/vacation/{orderNo}/refund")
    public ApiResponse<VacationRefundResponse> refundVacationOrder(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.refundVacationOrder(orderNo));
    }

    @PostMapping("/vacation/refund")
    public ApiResponse<VacationRefundResponse> refundVacationOrderByPickupCode(@RequestParam String pickupCode) {
        return ApiResponse.ok(orderService.refundVacationOrderByPickupCode(pickupCode));
    }

    @GetMapping("/vacation/{orderNo}/assistant")
    public ApiResponse<VacationAssistantResponse> generateVacationAssistant(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.generateVacationAssistant(orderNo));
    }

    @GetMapping("/train/{orderNo}/change")
    public ApiResponse<TrainChangePreviewResponse> previewTrainChange(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.previewTrainChange(orderNo));
    }

    @PostMapping("/train/{orderNo}/change")
    public ApiResponse<TrainChangeResponse> changeTrainOrder(@PathVariable String orderNo,
                                                             @RequestBody TrainChangeConfirmRequest request) {
        return ApiResponse.ok(orderService.changeTrainOrder(orderNo, request.getTargetProductId()));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Order> getOrderByNo(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getOrderByNo(orderNo));
    }

    @PostMapping("/train/{orderNo}/cancel")
    public ApiResponse<Void> cancelOrder(@PathVariable String orderNo) {
        orderService.cancelOrder(orderNo);
        return ApiResponse.ok(null);
    }
}
