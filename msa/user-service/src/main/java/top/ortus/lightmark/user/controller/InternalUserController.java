package top.ortus.lightmark.user.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.ortus.lightmark.common.ApiResponse;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.user.dto.UserDTO;
import top.ortus.lightmark.user.dto.module.PointsChangeRequest;
import top.ortus.lightmark.user.dto.module.TravelerDTO;
import top.ortus.lightmark.user.service.PointsService;
import top.ortus.lightmark.user.service.TravelerService;
import top.ortus.lightmark.user.service.UserService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 供订单等业务服务调用的用户域接口。返回脱敏资料，避免跨服务暴露密码等字段。
 */
@RestController
@RequestMapping("/internal/user")
public class InternalUserController {
    private final UserService userService;
    private final TravelerService travelerService;
    private final PointsService pointsService;

    public InternalUserController(UserService userService, TravelerService travelerService, PointsService pointsService) {
        this.userService = userService;
        this.travelerService = travelerService;
        this.pointsService = pointsService;
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> profile(@PathVariable String id) {
        UserDTO user = userService.findById(requireId(id));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("level", user.getLevel());
        return ApiResponse.ok(result);
    }

    @GetMapping("/{id}/travelers")
    public ApiResponse<List<TravelerDTO>> travelers(@PathVariable String id) {
        return ApiResponse.ok(travelerService.list(requireId(id)));
    }

    @PostMapping("/{id}/points")
    public ApiResponse<Boolean> changePoints(@PathVariable String id, @Valid @RequestBody PointsChangeRequest request) {
        if (request == null || request.getAmount() == null || request.getType() == null) {
            throw new ApiException(400, "amount and type are required");
        }
        return ApiResponse.ok(pointsService.change(requireId(id), request.getAmount(), request.getType(),
                request.getSource(), request.getOrderId()));
    }

    private String requireId(String id) {
        if (id == null || id.isBlank() || !id.chars().allMatch(Character::isDigit)) {
            throw new ApiException(400, "invalid user id");
        }
        return id;
    }
}
