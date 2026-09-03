package top.ortus.lightmark.user.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserLoginLogRepository {
    private final JdbcTemplate jdbcTemplate;

    /** 与建表 DDL 保持一致：login_ip varchar(45)，device varchar(100) */
    private static final int MAX_IP_LEN = 45;
    private static final int MAX_DEVICE_LEN = 100;

    public UserLoginLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int append(long userId, String ip, String device) {
        return jdbcTemplate.update("insert into user_login_log (user_id, login_ip, device, login_time) values (?, ?, ?, now())",
                userId, clip(ip, MAX_IP_LEN), clip(device, MAX_DEVICE_LEN));
    }

    /**
     * 浏览器 User-Agent 等外部输入可能超过列宽，超长会导致
     * Data truncation 异常并让整个登录请求返回 500 internal error，
     * 这里按列宽截断（varchar(n) 按字符计，substring 安全）。
     */
    private static String clip(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
