package top.ortus.lightmark.user.converter;

import top.ortus.lightmark.user.dao.User;
import top.ortus.lightmark.user.dto.UserDTO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户转换器，用于在 User 实体和 UserDTO 之间进行转换
 */
public class UserConverter {

    private UserConverter() {
    }

    /**
     * 将 User 实体转换为 UserDTO
     * @param user 用户实体
     * @return 用户数据传输对象
     */
    public static UserDTO toDto(User user) {
        if (user == null) {
            return null;
        }
        return new UserDTO(
                user.getId(),
                user.getPhone(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatar(),
                user.getGender(),
                user.getBirth_date(),
                user.getPoints(),
                user.getLevel(),
                user.getStatus(),
                user.getRegister_source(),
                user.getLast_login_time(),
                user.getLast_login_ip(),
                user.getCreate_time(),
                user.getUpdate_time(),
                user.isDeleted()
        );
    }

    /**
     * 将 User 实体列表转换为 UserDTO 列表
     * @param users 用户实体列表
     * @return 用户数据传输对象列表
     */
    public static List<UserDTO> toDtoList(List<User> users) {
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }
        return users.stream()
                .map(UserConverter::toDto)
                .collect(Collectors.toList());
    }
}
