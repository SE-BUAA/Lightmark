package top.ortus.lightmark.user.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import top.ortus.lightmark.common.ApiResponse;
import top.ortus.lightmark.common.PageResponse;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.common.security.JwtTokenService;
import top.ortus.lightmark.common.security.UserIdentity;
import top.ortus.lightmark.user.dto.UserDTO;
import top.ortus.lightmark.user.dto.module.PointsLogDTO;
import top.ortus.lightmark.user.dto.module.TravelerDTO;
import top.ortus.lightmark.user.dto.user.UserAvatarUpdateRequest;
import top.ortus.lightmark.user.dto.user.UserCurrentDTO;
import top.ortus.lightmark.user.dto.user.UserLevelUpgradeInfoDTO;
import top.ortus.lightmark.user.dto.user.UserPasswordUpdateRequest;
import top.ortus.lightmark.user.dto.user.UserUpdateRequest;
import top.ortus.lightmark.user.service.MembershipService;
import top.ortus.lightmark.user.service.ObjectStorageService;
import top.ortus.lightmark.user.service.PointsService;
import top.ortus.lightmark.user.service.TravelerService;
import top.ortus.lightmark.user.service.UserService;
import top.ortus.lightmark.user.utils.UserIdFormatter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;
    private final TravelerService travelerService;
    private final PointsService pointsService;
    private final MembershipService membershipService;
    private final ObjectStorageService objectStorageService;
    private final JwtTokenService jwtTokenService;

    public UserController(UserService userService, TravelerService travelerService, PointsService pointsService,
                          MembershipService membershipService, ObjectStorageService objectStorageService,
                          JwtTokenService jwtTokenService) {
        this.userService = userService;
        this.travelerService = travelerService;
        this.pointsService = pointsService;
        this.membershipService = membershipService;
        this.objectStorageService = objectStorageService;
        this.jwtTokenService = jwtTokenService;
    }

    @GetMapping("/current")
    public ApiResponse<UserCurrentDTO> current(@RequestHeader("Authorization") String authorization) {
        String userId = userId(authorization);
        UserDTO user = userService.findById(userId);
        UserIdentity identity = identity(authorization);
        UserCurrentDTO result = new UserCurrentDTO(user, identity,
                identity == UserIdentity.ADMIN ? List.of("*") : List.of());
        result.setId(UserIdFormatter.format16(result.getId()));
        return ApiResponse.ok(result);
    }

    @PutMapping("/current")
    public ApiResponse<UserDTO> updateCurrent(@RequestHeader("Authorization") String authorization,
                                              @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(updateProfile(userId(authorization), request));
    }

    /** 与拆分方案中的路径参数接口兼容，仍要求调用者只能修改自己的资料。 */
    @PostMapping("/{id}/profile")
    public ApiResponse<UserDTO> updateProfileById(@PathVariable String id,
                                                  @RequestHeader("Authorization") String authorization,
                                                  @Valid @RequestBody UserUpdateRequest request) {
        ensureOwner(id, authorization);
        return ApiResponse.ok(updateProfile(id, request));
    }

    @PostMapping("/{id}/password")
    public ApiResponse<Boolean> updatePasswordById(@PathVariable String id,
                                                   @RequestHeader("Authorization") String authorization,
                                                   @Valid @RequestBody UserPasswordUpdateRequest request) {
        ensureOwner(id, authorization);
        return ApiResponse.ok(userService.updatePassword(id,
                request == null ? null : request.getOldPassword(),
                request == null ? null : request.getNewPassword()));
    }

    @PostMapping("/avatar")
    public ApiResponse<Map<String, String>> updateAvatar(@RequestHeader("Authorization") String authorization,
                                                         @Valid @RequestBody UserAvatarUpdateRequest request) {
        UserUpdateRequest update = new UserUpdateRequest();
        update.setAvatar(request == null ? null : request.getAvatarUrl());
        UserDTO user = userService.update(userId(authorization), update);
        return ApiResponse.ok(Map.of("avatarUrl", user.getAvatar() == null ? "" : user.getAvatar()));
    }

    @PostMapping(value = "/avatar/upload", consumes = "multipart/form-data")
    public ApiResponse<Map<String, String>> uploadAvatar(@RequestHeader("Authorization") String authorization,
                                                         @RequestPart("file") MultipartFile file) {
        String avatar = objectStorageService.uploadAvatar(userId(authorization), file);
        UserUpdateRequest update = new UserUpdateRequest();
        update.setAvatar(avatar);
        userService.update(userId(authorization), update);
        return ApiResponse.ok(Map.of("avatarUrl", avatar));
    }

    @PutMapping("/password")
    public ApiResponse<Boolean> updatePassword(@RequestHeader("Authorization") String authorization,
                                               @Valid @RequestBody UserPasswordUpdateRequest request) {
        return ApiResponse.ok(userService.updatePassword(userId(authorization),
                request == null ? null : request.getOldPassword(), request == null ? null : request.getNewPassword()));
    }

    @GetMapping("/travelers")
    public ApiResponse<List<TravelerDTO>> listTravelers(@RequestHeader("Authorization") String authorization) {
        return ApiResponse.ok(travelerService.list(userId(authorization)));
    }

    @GetMapping("/{id}/travelers")
    public ApiResponse<List<TravelerDTO>> listTravelersById(@PathVariable String id,
                                                            @RequestHeader("Authorization") String authorization) {
        ensureOwner(id, authorization);
        return ApiResponse.ok(travelerService.list(id));
    }

    @PostMapping("/travelers")
    public ApiResponse<TravelerDTO> createTraveler(@RequestHeader("Authorization") String authorization,
                                                   @Valid @RequestBody TravelerDTO request) {
        return ApiResponse.ok(travelerService.create(userId(authorization), request));
    }

    @PostMapping("/{id}/travelers")
    public ApiResponse<TravelerDTO> createTravelerById(@PathVariable String id,
                                                       @RequestHeader("Authorization") String authorization,
                                                       @Valid @RequestBody TravelerDTO request) {
        ensureOwner(id, authorization);
        return ApiResponse.ok(travelerService.create(id, request));
    }

    @PutMapping("/travelers/{id}")
    public ApiResponse<TravelerDTO> updateTraveler(@PathVariable String id,
                                                   @RequestHeader("Authorization") String authorization,
                                                   @Valid @RequestBody TravelerDTO request) {
        return ApiResponse.ok(travelerService.update(userId(authorization), id, request));
    }

    @PutMapping("/{userId}/travelers/{id}")
    public ApiResponse<TravelerDTO> updateTravelerByUserId(@PathVariable String userId,
                                                           @PathVariable String id,
                                                           @RequestHeader("Authorization") String authorization,
                                                           @Valid @RequestBody TravelerDTO request) {
        ensureOwner(userId, authorization);
        return ApiResponse.ok(travelerService.update(userId, id, request));
    }

    @DeleteMapping("/travelers/{id}")
    public ApiResponse<Boolean> deleteTraveler(@PathVariable String id,
                                               @RequestHeader("Authorization") String authorization) {
        return ApiResponse.ok(travelerService.delete(userId(authorization), id));
    }

    @DeleteMapping("/{userId}/travelers/{id}")
    public ApiResponse<Boolean> deleteTravelerByUserId(@PathVariable String userId,
                                                       @PathVariable String id,
                                                       @RequestHeader("Authorization") String authorization) {
        ensureOwner(userId, authorization);
        return ApiResponse.ok(travelerService.delete(userId, id));
    }

    @GetMapping("/points/logs")
    public ApiResponse<PageResponse<PointsLogDTO>> pointsLogs(@RequestHeader("Authorization") String authorization) {
        List<PointsLogDTO> items = pointsService.logs(userId(authorization));
        return ApiResponse.ok(new PageResponse<>(items.size(), 1, items.size(), items));
    }

    @GetMapping("/{id}/points")
    public ApiResponse<Map<String, Object>> pointsById(@PathVariable String id,
                                                       @RequestHeader("Authorization") String authorization) {
        ensureOwner(id, authorization);
        UserDTO user = userService.findById(id);
        return ApiResponse.ok(Map.of("points", user.getPoints(), "level", user.getLevel(),
                "logs", pointsService.logs(id)));
    }

    @GetMapping("/level/upgrade-info")
    public ApiResponse<UserLevelUpgradeInfoDTO> levelUpgradeInfo(@RequestHeader("Authorization") String authorization) {
        UserDTO user = userService.findById(userId(authorization));
        return ApiResponse.ok(membershipService.getUpgradeInfo(user));
    }

    private String userId(String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
        Long id = token == null ? null : jwtTokenService.resolveUserId(token);
        if (id == null) throw new ApiException(401, "unauthorized");
        return String.valueOf(id);
    }

    private UserDTO updateProfile(String id, UserUpdateRequest request) {
        if (request == null) {
            throw new ApiException(400, "invalid request");
        }
        UserUpdateRequest safe = new UserUpdateRequest();
        if (request.isPhoneSpecified()) safe.setPhone(request.getPhone());
        if (request.isEmailSpecified()) safe.setEmail(request.getEmail());
        safe.setNickname(request.getNickname());
        safe.setAvatar(request.getAvatar());
        safe.setGender(request.getGender());
        safe.setBirth_date(request.getBirth_date());
        return userService.update(id, safe);
    }

    private void ensureOwner(String id, String authorization) {
        if (id == null || id.isBlank() || !id.chars().allMatch(Character::isDigit)) {
            throw new ApiException(400, "invalid user id");
        }
        String tokenUserId = userId(authorization);
        boolean sameUser;
        try {
            sameUser = Long.parseLong(id) == Long.parseLong(tokenUserId);
        } catch (NumberFormatException ex) {
            sameUser = id.equals(tokenUserId);
        }
        if (!sameUser && identity(authorization) != UserIdentity.ADMIN) {
            throw new ApiException(403, "forbidden");
        }
    }

    private UserIdentity identity(String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
        return jwtTokenService.resolveIdentity(token);
    }

}
