package top.ortus.lightmark.backend.service;

import top.ortus.lightmark.backend.dto.module.TrainOrderRequest;
import top.ortus.lightmark.backend.dao.Order;
import top.ortus.lightmark.backend.dto.module.TrainChangeResponse;
import top.ortus.lightmark.backend.dto.module.TrainChangePreviewResponse;
import top.ortus.lightmark.backend.dto.module.TrainOrderResponse;
import top.ortus.lightmark.backend.dto.module.TrainRefundResponse;
import top.ortus.lightmark.backend.dto.module.VacationAssistantResponse;
import top.ortus.lightmark.backend.dto.module.VacationOrderRequest;
import top.ortus.lightmark.backend.dto.module.VacationRefundResponse;

public interface OrderService {
    TrainOrderResponse createTrainOrder(Long userId, TrainOrderRequest request);
    TrainOrderResponse createVacationOrder(Long userId, VacationOrderRequest request);
    TrainOrderResponse payOrder(String orderNo);
    TrainRefundResponse refundTrainOrder(String orderNo);
    VacationRefundResponse refundVacationOrder(String orderNo);
    VacationRefundResponse refundVacationOrderByPickupCode(String pickupCode);
    VacationAssistantResponse generateVacationAssistant(String orderNo);
    TrainChangePreviewResponse previewTrainChange(String orderNo);
    TrainChangeResponse changeTrainOrder(String orderNo, String targetProductId);
    Order getOrderByNo(String orderNo);
    void cancelOrder(String orderNo);
}
