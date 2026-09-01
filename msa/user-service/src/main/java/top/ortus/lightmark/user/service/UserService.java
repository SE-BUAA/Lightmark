package top.ortus.lightmark.user.service;

import top.ortus.lightmark.user.dto.user.UserCreateRequest;
import top.ortus.lightmark.user.dto.UserDTO;
import top.ortus.lightmark.user.dto.user.UserUpdateRequest;

import java.util.List;

/**
 * 用户服务接口，定义用户相关的业务操作
 */
public interface UserService {
    /**
     * 查询所有用户
     * @return 用户列表
     */
    List<UserDTO> findAll();

    /**
     * 根据 ID 查询用户
     * @param id 用户 ID
     * @return 用户信息
     */
    UserDTO findById(String id);

    /**
     * 根据邮箱查询用户
     * @param email 邮箱地址
     * @return 用户信息
     */
    UserDTO findByEmail(String email);

    /**
     * 根据手机号查询用户
     * @param phone 手机号
     * @return 用户信息
     */
    UserDTO findByPhone(String phone);

    /**
     * 创建新用户
     * @param request 创建请求
     * @return 创建的用户信息
     */
    UserDTO create(UserCreateRequest request);

    /**
     * 更新用户信息
     * @param id 用户 ID
     * @param request 更新请求
     * @return 更新后的用户信息
     */
    UserDTO update(String id, UserUpdateRequest request);

    /**
     * 删除用户（软删除）
     * @param id 用户 ID
     * @return 是否删除成功
     */
    boolean delete(String id);

    boolean updatePassword(String id, String oldPassword, String newPassword);
}
