package top.ortus.lightmark.order.service;

import java.util.List;
import java.util.Map;

public interface OrderService {

    Map<String, Object> previewFlightOrder(Long userId, Map<String, Object> payload);

    Map<String, Object> createFlightOrder(Long userId, Map<String, Object> payload);

    Map<String, Object> createHotelOrder(Long userId, Map<String, Object> payload);

    Map<String, Object> payHotelOrder(Long userId, Long orderId, String paymentMethod);

    Map<String, Object> listHotelOrders(Long userId, Integer status, Integer page, Integer size);

    Map<String, Object> getHotelOrderDetail(Long userId, Long orderId);

    void cancelHotelOrder(Long userId, Long orderId);

    void applyInvoice(Long userId, Long orderId, Map<String, Object> request);

    List<Map<String, Object>> listHotelReviews(Long hotelId, Integer page, Integer size);

    Map<String, Object> createHotelReview(Long userId, Long orderId, Map<String, Object> request);

    Map<String, Object> createTrainOrder(Long userId, Map<String, Object> request);

    Map<String, Object> createVacationOrder(Long userId, Map<String, Object> request);

    Map<String, Object> payOrder(String orderNo, Map<String, Object> request);

    Map<String, Object> refundOrder(String orderNo);

    Map<String, Object> refundFlightOrder(String orderNo);

    Map<String, Object> refundHotelOrder(String orderNo);

    Map<String, Object> refundTrainOrder(String orderNo);

    Map<String, Object> refundVacationOrder(String orderNo);

    Map<String, Object> refundVacationOrderByPickupCode(String pickupCode);

    Map<String, Object> generateVacationAssistant(String orderNo);

    Map<String, Object> previewTrainChange(String orderNo);

    Map<String, Object> changeTrainOrder(String orderNo, String targetProductId);

    Map<String, Object> changeFlightOrder(String orderNo, String targetProductId);

    Map<String, Object> getOrderByNo(String orderNo);

    Map<String, Object> listOrders(Long userId, String orderType, Integer status, Integer page, Integer size);

    void cancelOrder(String orderNo);

    Map<String, Object> orderStatus(String orderNo);

    boolean paymentCallback(Map<String, Object> payload);

    Map<String, Object> adminListOrders(Integer status, Integer page);

    boolean updateOrderStatus(String orderNo, int status, String remark, Long adminId);

    boolean refundOrder(String orderNo, String remark, Long adminId);
}
