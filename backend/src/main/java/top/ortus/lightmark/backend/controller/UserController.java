package top.ortus.lightmark.backend.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import top.ortus.lightmark.backend.JwtTokenService;
import top.ortus.lightmark.backend.common.ApiResponse;
import top.ortus.lightmark.backend.common.PageResponse;
import top.ortus.lightmark.backend.dto.UserDTO;
import top.ortus.lightmark.backend.dto.module.OrderDTO;
import top.ortus.lightmark.backend.dto.module.PointsLogDTO;
import top.ortus.lightmark.backend.dto.module.TravelerDTO;
import top.ortus.lightmark.backend.dto.user.UserAvatarUpdateRequest;
import top.ortus.lightmark.backend.dto.user.UserCurrentDTO;
import top.ortus.lightmark.backend.dto.user.UserLevelUpgradeInfoDTO;
import top.ortus.lightmark.backend.dto.user.UserPasswordUpdateRequest;
import top.ortus.lightmark.backend.dto.user.UserUpdateRequest;
import top.ortus.lightmark.backend.exception.ApiException;
import top.ortus.lightmark.backend.service.FlightSearchService;
import top.ortus.lightmark.backend.service.GenericCrudService;
import top.ortus.lightmark.backend.service.MembershipService;
import top.ortus.lightmark.backend.service.ObjectStorageService;
import top.ortus.lightmark.backend.service.OrderService;
import top.ortus.lightmark.backend.service.UserService;
import top.ortus.lightmark.backend.security.UserIdentity;
import top.ortus.lightmark.backend.utils.UserIdFormatter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 用户控制器，提供用户相关的 API 接口
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final GenericCrudService genericCrudService;
    private final JwtTokenService jwtTokenService;
    private final MembershipService membershipService;
    private final ObjectStorageService objectStorageService;
    private final OrderService orderService;
    private final FlightSearchService flightSearchService;

    /**
     * 构造函数
     * @param userService 用户服务
     */
    public UserController(UserService userService,
                          GenericCrudService genericCrudService,
                          JwtTokenService jwtTokenService,
                          MembershipService membershipService,
                          ObjectStorageService objectStorageService,
                          OrderService orderService,
                          FlightSearchService flightSearchService) {
        this.userService = userService;
        this.genericCrudService = genericCrudService;
        this.jwtTokenService = jwtTokenService;
        this.membershipService = membershipService;
        this.objectStorageService = objectStorageService;
        this.orderService = orderService;
        this.flightSearchService = flightSearchService;
    }

    @GetMapping("/current")
    public ApiResponse<UserCurrentDTO> current(@RequestHeader("Authorization") String authorization) {
        UserDTO user = resolveCurrentUser(authorization);
        UserIdentity identity = resolveIdentity(authorization);
        UserCurrentDTO dto = new UserCurrentDTO(user, identity, Collections.emptyList());
        dto.setId(UserIdFormatter.format16(dto.getId()));
        return ApiResponse.ok(dto);
    }

    @PutMapping("/current")
    public ApiResponse<UserDTO> updateCurrent(@RequestHeader("Authorization") String authorization,
                                              @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(userService.update(resolveUserId(authorization), request));
    }

    @PostMapping("/avatar")
    public ApiResponse<Map<String, String>> uploadAvatar(@RequestHeader("Authorization") String authorization,
                                                         @RequestBody UserAvatarUpdateRequest request) {
        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setAvatar(request == null ? null : request.getAvatarUrl());
        userService.update(resolveUserId(authorization), updateRequest);
        return ApiResponse.ok(Map.of("avatarUrl", request == null ? "" : request.getAvatarUrl()));
    }

    @PostMapping(value = "/avatar/upload", consumes = "multipart/form-data")
    public ApiResponse<Map<String, String>> uploadAvatarFile(@RequestHeader("Authorization") String authorization,
                                                             @RequestPart("file") MultipartFile file) {
        String userId = resolveUserId(authorization);
        String normalizedUserId = UserIdFormatter.format16(userId);
        String url = objectStorageService.uploadAvatar(normalizedUserId, file);
        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setAvatar(url);
        userService.update(userId, updateRequest);
        return ApiResponse.ok(Map.of("avatarUrl", url));
    }

    @PutMapping("/password")
    public ApiResponse<Boolean> updatePassword(@RequestHeader("Authorization") String authorization,
                                               @RequestBody UserPasswordUpdateRequest request) {
        if (request == null) {
            return ApiResponse.error(400, "invalid request");
        }
        return ApiResponse.ok(userService.updatePassword(resolveUserId(authorization), request.getOldPassword(), request.getNewPassword()));
    }

    @GetMapping("/travelers")
    public ApiResponse<List<TravelerDTO>> listTravelers(@RequestHeader("Authorization") String authorization,
                                                        @RequestParam Map<String, String> params) {
        Map<String, String> filters = new java.util.HashMap<>(params);
        filters.put("user_id", resolveUserId(authorization));
        return ApiResponse.ok(genericCrudService.listTyped("traveler", filters, TravelerDTO.class));
    }

    @PostMapping("/travelers")
    public ApiResponse<TravelerDTO> createTraveler(@RequestHeader("Authorization") String authorization,
                                                   @RequestBody TravelerDTO payload) {
        payload.setUser_id(resolveUserId(authorization));
        return ApiResponse.ok(genericCrudService.createTyped("traveler", payload, TravelerDTO.class));
    }

    @PutMapping("/travelers/{id}")
    public ApiResponse<TravelerDTO> updateTraveler(@PathVariable String id,
                                                   @RequestHeader("Authorization") String authorization,
                                                   @RequestBody TravelerDTO payload) {
        payload.setId(id);
        payload.setUser_id(resolveUserId(authorization));
        return ApiResponse.ok(genericCrudService.updateTyped("traveler", payload, TravelerDTO.class));
    }

    @DeleteMapping("/travelers/{id}")
    public ApiResponse<Boolean> deleteTraveler(@PathVariable String id,
                                               @RequestHeader("Authorization") String authorization) {
        TravelerDTO payload = new TravelerDTO();
        payload.setId(id);
        payload.setUser_id(resolveUserId(authorization));
        return ApiResponse.ok(genericCrudService.deleteTyped("traveler", payload));
    }

    @GetMapping("/points/logs")
    public ApiResponse<PageResponse<PointsLogDTO>> pointsLogs(@RequestHeader("Authorization") String authorization,
                                                              @RequestParam Map<String, String> params) {
        Map<String, String> filters = new java.util.HashMap<>(params);
        filters.put("user_id", resolveUserId(authorization));
        List<PointsLogDTO> items = genericCrudService.listTyped("points_log", filters, PointsLogDTO.class);
        return ApiResponse.ok(toPage(items, params));
    }

    @GetMapping("/level/upgrade-info")
    public ApiResponse<UserLevelUpgradeInfoDTO> levelUpgradeInfo(@RequestHeader("Authorization") String authorization) {
        String userId = resolveUserId(authorization);
        if ("0".equals(userId)) {
            throw new ApiException(401, "unauthorized");
        }
        UserDTO user = userService.findById(userId);
        return ApiResponse.ok(membershipService.getUpgradeInfo(user));
    }

    @GetMapping("/orders")
    public ApiResponse<PageResponse<OrderDTO>> orders(@RequestHeader("Authorization") String authorization,
                                                      @RequestParam Map<String, String> params) {
        Map<String, String> pageParams = new java.util.HashMap<>();
        String pageStr = params.get("page");
        String sizeStr = params.get("size");
        if (pageStr != null) pageParams.put("page", pageStr);
        if (sizeStr != null) pageParams.put("size", sizeStr);

        Map<String, String> filters = new java.util.HashMap<>(params);
        filters.remove("page");
        filters.remove("size");
        filters.put("user_id", resolveUserId(authorization));
        List<OrderDTO> items = genericCrudService.listTyped("orders", filters, OrderDTO.class);
        items.sort((a, b) -> {
            String ta = a.getCreate_time() == null ? "" : a.getCreate_time().toString();
            String tb = b.getCreate_time() == null ? "" : b.getCreate_time().toString();
            return tb.compareTo(ta);
        });
        return ApiResponse.ok(toPage(items, pageParams));
    }

    @GetMapping("/orders/{orderNo}")
    public ApiResponse<OrderDTO> orderDetail(@PathVariable String orderNo,
                                             @RequestHeader("Authorization") String authorization) {
        Map<String, String> filters = Map.of("order_no", orderNo, "user_id", resolveUserId(authorization));
        List<OrderDTO> items = genericCrudService.listTyped("orders", filters, OrderDTO.class);
        return ApiResponse.ok(items.isEmpty() ? null : items.get(0));
    }

    @PostMapping("/orders/{orderNo}/refund")
    public ApiResponse<Object> refundOrder(@PathVariable String orderNo,
                                           @RequestHeader("Authorization") String authorization) {
        OrderDTO order = orderDetail(orderNo, authorization).getData();
        if (order == null) {
            throw new ApiException(404, "order not found");
        }
        String orderType = order.getOrder_type() == null ? "" : order.getOrder_type().toUpperCase();
        return switch (orderType) {
            case "TRAIN" -> ApiResponse.ok(orderService.refundTrainOrder(orderNo));
            case "VACATION" -> ApiResponse.ok(orderService.refundVacationOrder(orderNo));
            case "FLIGHT", "HOTEL" -> ApiResponse.ok(flightSearchService.refundOrder(orderNo));
            default -> throw new ApiException(400, "unsupported order type");
        };
    }

    private String resolveUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "0";
        }
        Long userId = jwtTokenService.resolveUserId(authorization.substring("Bearer ".length()));
        return userId == null ? "0" : String.valueOf(userId);
    }

    private UserDTO resolveCurrentUser(String authorization) {
        String userId = resolveUserId(authorization);
        return userService.findById(userId);
    }

    private UserIdentity resolveIdentity(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return UserIdentity.USER;
        }
        return jwtTokenService.resolveIdentity(authorization.substring("Bearer ".length()));
    }

    private <T> PageResponse<T> toPage(List<T> items, Map<String, String> params) {
        int page = parseInt(params.get("page"), 1);
        int size = parseInt(params.get("size"), 10);
        size = Math.max(1, Math.min(size, 100));
        long total = items == null ? 0 : items.size();
        List<T> sliced = items == null ? List.of() : items.stream()
            .skip((long) (page - 1) * size)
            .limit(size)
            .toList();
        return new PageResponse<>(total, page, size, sliced);
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
