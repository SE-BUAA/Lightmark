package top.ortus.lightmark.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.ortus.lightmark.common.ApiResponse;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.user.dao.AdminLogRepository;
import top.ortus.lightmark.user.dto.UserDTO;
import top.ortus.lightmark.user.dto.module.AdminLogDTO;
import top.ortus.lightmark.user.dto.user.UserUpdateRequest;
import top.ortus.lightmark.user.service.UserService;

import java.util.List;
import java.util.Map;

/** 管理员用户与审计接口，只操作 user/admin_log 两张用户域表。 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserService userService;
    private final AdminLogRepository adminLogRepository;

    public AdminController(UserService userService, AdminLogRepository adminLogRepository) {
        this.userService = userService;
        this.adminLogRepository = adminLogRepository;
    }

    @GetMapping("/users")
    public ApiResponse<List<UserDTO>> users() {
        return ApiResponse.ok(userService.findAll());
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<UserDTO> updateStatus(@PathVariable String id, @RequestBody Map<String, Object> body,
                                             HttpServletRequest request) {
        Integer status = integerValue(body, "status");
        UserUpdateRequest update = new UserUpdateRequest();
        update.setStatus(status);
        UserDTO result = userService.update(id, update);
        appendAudit(request, "update_user_status", id);
        return ApiResponse.ok(result);
    }

    @PutMapping("/users/{id}/level")
    public ApiResponse<UserDTO> updateLevel(@PathVariable String id, @RequestBody Map<String, Object> body,
                                            HttpServletRequest request) {
        Integer level = integerValue(body, "level");
        UserUpdateRequest update = new UserUpdateRequest();
        update.setLevel(level.shortValue());
        UserDTO result = userService.update(id, update);
        appendAudit(request, "update_user_level", id);
        return ApiResponse.ok(result);
    }

    @GetMapping("/logs")
    public ApiResponse<List<AdminLogDTO>> logs() {
        return ApiResponse.ok(adminLogRepository.findAll());
    }

    private Integer integerValue(Map<String, Object> body, String name) {
        if (body == null || body.get(name) == null) {
            throw new ApiException(400, name + " is required");
        }
        try {
            return Integer.valueOf(String.valueOf(body.get(name)));
        } catch (NumberFormatException ex) {
            throw new ApiException(400, name + " must be a number");
        }
    }

    private void appendAudit(HttpServletRequest request, String operation, String target) {
        Object adminId = request.getAttribute("userId");
        long id = adminId == null ? 0L : Long.parseLong(String.valueOf(adminId));
        adminLogRepository.append(id, operation, target, "SUCCESS", request.getRemoteAddr());
    }
}
