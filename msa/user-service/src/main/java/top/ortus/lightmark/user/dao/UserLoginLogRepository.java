package top.ortus.lightmark.user.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserLoginLogRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserLoginLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int append(long userId, String ip, String device) {
        return jdbcTemplate.update("insert into user_login_log (user_id, login_ip, device, login_time) values (?, ?, ?, now())",
                userId, ip, device);
    }
}
