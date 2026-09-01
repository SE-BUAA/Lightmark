package top.ortus.lightmark.user.service;

import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import top.ortus.lightmark.user.converter.UserConverter;
import top.ortus.lightmark.user.dao.User;
import top.ortus.lightmark.user.dao.UserRepositoryImpl;
import top.ortus.lightmark.user.dto.UserDTO;
import top.ortus.lightmark.user.dto.user.UserCreateRequest;
import top.ortus.lightmark.user.dto.user.UserUpdateRequest;
import top.ortus.lightmark.common.exception.ApiException;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户服务实现类，提供用户相关的业务逻辑
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserRepositoryImpl userRepositoryImpl;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 构造函数
     * @param userRepositoryImpl 用户数据访问层实现
     */
    public UserServiceImpl(UserRepositoryImpl userRepositoryImpl) {
        this.userRepositoryImpl = userRepositoryImpl;
    }

    @Override
    public List<UserDTO> findAll() {
        List<User> users = userRepositoryImpl.findAll();
        return UserConverter.toDtoList(users);
    }

    @Override
    public UserDTO findById(String id) {
        User user = userRepositoryImpl.findById(id);
        return UserConverter.toDto(user);
    }

    @Override
    public UserDTO findByEmail(String email) {
        User user = userRepositoryImpl.findByEmail(email);
        return UserConverter.toDto(user);
    }

    @Override
    public UserDTO findByPhone(String phone) {
        User user = userRepositoryImpl.findByPhone(phone);
        return UserConverter.toDto(user);
    }

    @Override
    public UserDTO create(UserCreateRequest request) {
        validateCreateRequest(request);
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setPassword(request.getPassword());
        user.setNickname(valueOrDefault(request.getNickname(), ""));
        user.setAvatar(valueOrDefault(request.getAvatar(), ""));
        user.setGender(request.getGender());
        user.setBirth_date(request.getBirth_date());
        user.setPoints(valueOrDefault(request.getPoints(), 0));
        user.setLevel(valueOrDefault(request.getLevel(), (short) 0));
        user.setStatus(valueOrDefault(request.getStatus(), 0));
        user.setRegister_source(valueOrDefault(request.getRegister_source(), defaultRegisterSource(request)));
        user.setLast_login_time(request.getLast_login_time());
        user.setLast_login_ip(request.getLast_login_ip());
        user.setCreate_time(now);
        user.setUpdate_time(now);
        user.setDeleted(false);
        userRepositoryImpl.insert(user);
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            return UserConverter.toDto(userRepositoryImpl.findByEmail(request.getEmail()));
        }
        return UserConverter.toDto(userRepositoryImpl.findByPhone(request.getPhone()));
    }

    @Override
    public UserDTO update(String id, UserUpdateRequest request) {
        User user = userRepositoryImpl.findById(id);
        Set<String> nullableColumns = new LinkedHashSet<>();
        if (request.isPhoneSpecified()) {
            user.setPhone(request.getPhone());
            nullableColumns.add("phone");
        }
        if (request.isEmailSpecified()) {
            user.setEmail(request.getEmail());
            nullableColumns.add("email");
        }
        if (request.getPassword() != null) {
            user.setPassword(request.getPassword());
        }
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getBirth_date() != null) {
            user.setBirth_date(request.getBirth_date());
        }
        if (request.getPoints() != null) {
            user.setPoints(request.getPoints());
        }
        if (request.getLevel() != null) {
            user.setLevel(request.getLevel());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        if (request.getRegister_source() != null) {
            user.setRegister_source(request.getRegister_source());
        }
        if (request.getLast_login_time() != null) {
            user.setLast_login_time(request.getLast_login_time());
        }
        if (request.getLast_login_ip() != null) {
            user.setLast_login_ip(request.getLast_login_ip());
        }
        user.setUpdate_time(LocalDateTime.now());
        userRepositoryImpl.update(user, nullableColumns);
        return UserConverter.toDto(userRepositoryImpl.findById(id));
    }

    @Override
    public boolean delete(String id) {
        return userRepositoryImpl.softDeleteById(id) > 0;
    }

    @Override
    public boolean updatePassword(String id, String oldPassword, String newPassword) {
        if (id == null || id.isBlank() || "0".equals(id)) {
            throw new ApiException(401, "unauthorized");
        }
        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new ApiException(400, "invalid request");
        }
        User user;
        try {
            user = userRepositoryImpl.findById(id);
        } catch (Exception ex) {
            throw new ApiException(404, "user not found");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new ApiException(400, "old password incorrect");
        }
        UserUpdateRequest req = new UserUpdateRequest();
        req.setPassword(passwordEncoder.encode(newPassword));
        update(id, req);
        return true;
    }

    /**
     * 验证创建用户请求
     * @param request 创建请求
     * @throws IllegalArgumentException 如果请求无效
     */
    private void validateCreateRequest(UserCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if ((request.getPhone() == null || request.getPhone().isEmpty())
                && (request.getEmail() == null || request.getEmail().isEmpty())) {
            throw new IllegalArgumentException("phone or email is required");
        }
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new IllegalArgumentException("password is required");
        }
    }

    /**
     * 获取默认注册来源
     * @param request 创建请求
     * @return 注册来源
     */
    private String defaultRegisterSource(UserCreateRequest request) {
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            return "EMAIL";
        }
        return "PHONE";
    }

    /**
     * 获取字符串值或默认值
     * @param value 原始值
     * @param fallback 默认值
     * @return 值或默认值
     */
    private String valueOrDefault(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        return value;
    }

    /**
     * 获取整数值或默认值
     * @param value 原始值
     * @param fallback 默认值
     * @return 值或默认值
     */
    private int valueOrDefault(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return value;
    }

    /**
     * 获取短整数值或默认值
     * @param value 原始值
     * @param fallback 默认值
     * @return 值或默认值
     */
    private short valueOrDefault(Short value, short fallback) {
        if (value == null) {
            return fallback;
        }
        return value;
    }
}
