package top.ortus.lightmark.user.dao;

import top.ortus.lightmark.common.security.UserIdentity;

import java.util.List;

/**
 * 用户数据访问接口，定义用户相关的数据库操作
 */
public interface UserRepository {
    /**
     * 查询所有用户
     * @return 用户列表
     */
    List<User> findAll();

    /**
     * 根据 ID 查询用户
     * @param id 用户 ID
     * @return 用户信息
     */
    User findById(String id);

    /**
     * 根据手机号查询用户
     * @param phone 手机号
     * @return 用户信息
     */
    User findByPhone(String phone);

    User findByPhoneOrNull(String phone);

    /**
     * 根据邮箱查询用户
     * @param email 邮箱地址
     * @return 用户信息
     */
    User findByEmail(String email);

    User findByEmailOrNull(String email);

    /**
     * 根据账号（手机号或邮箱）查询用户
     * @param account 账号（手机号或邮箱）
     * @return 用户信息
     */
    User findByAccount(String account);

    User findByAccountOrNull(String account);

    User findByNickname(String nickname);

    User findByNicknameOrNull(String nickname);

    /**
     * 插入新用户
     * @param user 用户实体
     * @return 影响的行数
     */
    int insert(User user);

    int assignRole(String userId, int roleId);

    /**
     * 更新用户信息
     * @param user 用户实体
     * @return 影响的行数
     */
    int update(User user);

    /**
     * 根据 ID 软删除用户
     * @param id 用户 ID
     * @return 影响的行数
     */
    int softDeleteById(String id);

    /**
     * 根据用户 ID 查询身份（单一身份）。
     * @param userId 用户 ID
     * @return 用户身份
     */
    UserIdentity findIdentityByUserId(String userId);
}
