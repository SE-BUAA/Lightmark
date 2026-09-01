package top.ortus.lightmark.user.dao;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import top.ortus.lightmark.common.security.UserIdentity;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户数据访问实现类，提供用户相关的数据库操作
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final DataSource dataSource;
    private final Set<String> columnsCache = ConcurrentHashMap.newKeySet();

    /**
     * 构造函数
     * @param jdbcTemplate JDBC 模板
     */
    public UserRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.dataSource = jdbcTemplate.getDataSource();
    }

    @Override
    public List<User> findAll() {
        String sql = "select * from `user` where deleted = 0";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(User.class));
    }

    @Override
    public User findById(String id) {
        String sql = "select * from `user` where id = ? and deleted = 0";
        return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(User.class), id);
    }

    @Override
    public User findByPhone(String phone) {
        User user = findByPhoneOrNull(phone);
        if (user == null) {
            throw new org.springframework.dao.EmptyResultDataAccessException(1);
        }
        return user;
    }

    @Override
    public User findByPhoneOrNull(String phone) {
        String sql = "select * from `user` where phone = ? and deleted = 0";
        List<User> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(User.class), phone);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public User findByEmail(String email) {
        User user = findByEmailOrNull(email);
        if (user == null) {
            throw new org.springframework.dao.EmptyResultDataAccessException(1);
        }
        return user;
    }

    @Override
    public User findByEmailOrNull(String email) {
        String sql = "select * from `user` where email = ? and deleted = 0";
        List<User> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(User.class), email);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public User findByAccount(String account) {
        User user = findByAccountOrNull(account);
        if (user == null) {
            throw new org.springframework.dao.EmptyResultDataAccessException(1);
        }
        return user;
    }

    @Override
    public User findByAccountOrNull(String account) {
        String sql = "select * from `user` where (phone = ? or email = ?) and deleted = 0";
        List<User> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(User.class), account, account);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public User findByNickname(String nickname) {
        User user = findByNicknameOrNull(nickname);
        if (user == null) {
            throw new org.springframework.dao.EmptyResultDataAccessException(1);
        }
        return user;
    }

    @Override
    public User findByNicknameOrNull(String nickname) {
        String sql = "select * from `user` where nickname = ? and deleted = 0";
        List<User> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(User.class), nickname);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public int insert(User user) {
        Map<String, Object> payload = buildPayload(user, true, Set.of());
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        MapSqlParameterSource params = new MapSqlParameterSource();
        int index = 0;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (index > 0) {
                columns.append(", ");
                values.append(", ");
            }
            columns.append("`").append(entry.getKey()).append("`");
            values.append(":" + entry.getKey());
            params.addValue(entry.getKey(), entry.getValue());
            index++;
        }
        String sql = "insert into `user` (" + columns + ") values (" + values + ")";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        return namedParameterJdbcTemplate.update(sql, params, keyHolder);
    }

    @Override
    public int update(User user) {
        return update(user, Set.of());
    }

    public int update(User user, Set<String> nullableColumns) {
        Map<String, Object> payload = buildPayload(user, false, nullableColumns);
        if (payload.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("update `user` set ");
        MapSqlParameterSource params = new MapSqlParameterSource();
        int index = 0;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (index > 0) {
                sql.append(", ");
            }
            sql.append("`").append(entry.getKey()).append("` = :" + entry.getKey());
            params.addValue(entry.getKey(), entry.getValue());
            index++;
        }
        sql.append(" where id = :id and deleted = 0");
        params.addValue("id", user.getId());
        return namedParameterJdbcTemplate.update(sql.toString(), params);
    }

    @Override
    public int assignRole(String userId, int roleId) {
        return jdbcTemplate.update(
                "insert into user_role (user_id, role_id) values (?, ?)",
                userId,
                roleId
        );
    }

    public int insertPointsLog(String userId, int type, int amount, String source, String orderId) {
        String sql = """
                insert into points_log (user_id, type, amount, source, order_id, create_time)
                values (?, ?, ?, ?, ?, ?)
                """;
        return jdbcTemplate.update(
                sql,
                userId,
                type,
                amount,
                source,
                orderId,
                Timestamp.valueOf(LocalDateTime.now())
        );
    }

    public int changePoints(String userId, int amount) {
        return jdbcTemplate.update("update `user` set points = points + ?, update_time = now() where id = ? and deleted = 0 and points + ? >= 0",
                amount, userId, amount);
    }

    public int countPointsLog(String userId, String orderId, String source) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from points_log where user_id = ? and order_id = ? and source = ?",
                Integer.class, userId, orderId, source);
        return count == null ? 0 : count;
    }

    @Override
    public int softDeleteById(String id) {
        if (hasColumn("update_time")) {
            String sql = "update `user` set deleted = 1, update_time = ? where id = ? and deleted = 0";
            return jdbcTemplate.update(sql, toTimestamp(LocalDateTime.now()), id);
        }
        String sql = "update `user` set deleted = 1 where id = ? and deleted = 0";
        return jdbcTemplate.update(sql, id);
    }

    @Override
    public UserIdentity findIdentityByUserId(String userId) {
        String sql = """
                select r.role_name
                from user_role ur
                join role r on ur.role_id = r.id
                where ur.user_id = ?
                """;
        List<String> roleNames = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("role_name"), userId);
        if (roleNames.isEmpty()) {
            return UserIdentity.USER;
        }
        for (String roleName : roleNames) {
            if (UserIdentity.ADMIN == UserIdentity.fromRoleName(roleName)) {
                return UserIdentity.ADMIN;
            }
        }
        return UserIdentity.fromRoleName(roleNames.get(0));
    }

    /**
     * 将 LocalDateTime 转换为 Timestamp
     * @param value LocalDateTime 值
     * @return Timestamp 值
     */
    private Timestamp toTimestamp(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return Timestamp.valueOf(value);
    }

    /**
     * 构建数据库操作的数据负载
     * @param user 用户实体
     * @param includeCreateTime 是否包含创建时间
     * @return 数据负载 Map
     */
    private Map<String, Object> buildPayload(User user, boolean includeCreateTime, Set<String> nullableColumns) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "phone", user.getPhone(), nullableColumns);
        putIfPresent(payload, "email", user.getEmail(), nullableColumns);
        putIfPresent(payload, "password", user.getPassword(), nullableColumns);
        putIfPresent(payload, "nickname", user.getNickname(), nullableColumns);
        putIfPresent(payload, "avatar", user.getAvatar(), nullableColumns);
        putIfPresent(payload, "gender", user.getGender(), nullableColumns);
        putIfPresent(payload, "birth_date", user.getBirth_date(), nullableColumns);
        putIfPresent(payload, "points", user.getPoints(), nullableColumns);
        putIfPresent(payload, "level", user.getLevel(), nullableColumns);
        putIfPresent(payload, "status", user.getStatus(), nullableColumns);
        putIfPresent(payload, "register_source", user.getRegister_source(), nullableColumns);
        putIfPresent(payload, "last_login_time", toTimestamp(user.getLast_login_time()), nullableColumns);
        putIfPresent(payload, "last_login_ip", user.getLast_login_ip(), nullableColumns);
        if (hasColumn("country_code")) {
            putIfPresent(payload, "country_code", user.getCountry_code(), nullableColumns);
        }
        if (includeCreateTime) {
            putIfPresent(payload, "create_time", toTimestamp(user.getCreate_time()), nullableColumns);
        }
        putIfPresent(payload, "update_time", toTimestamp(user.getUpdate_time()), nullableColumns);
        putIfPresent(payload, "deleted", user.isDeleted(), nullableColumns);
        return payload;
    }

    /**
     * 如果值存在且列存在，则添加到负载中
     * @param payload 数据负载
     * @param column 列名
     * @param value 值
     */
    private void putIfPresent(Map<String, Object> payload, String column, Object value, Set<String> nullableColumns) {
        if (!hasColumn(column)) {
            return;
        }
        if (value == null && !nullableColumns.contains(column)) {
            return;
        }
        payload.put(column, value);
    }

    /**
     * 检查表中是否存在指定列
     * @param column 列名
     * @return 是否存在
     */
    private boolean hasColumn(String column) {
        if (columnsCache.isEmpty()) {
            loadColumns();
        }
        return columnsCache.contains(column.toLowerCase(Locale.ROOT));
    }

    /**
     * 从数据库加载表的列信息到缓存
     */
    private void loadColumns() {
        try {
            if (dataSource == null) {
                return;
            }
            try (var connection = dataSource.getConnection();
                 var rs = connection.getMetaData().getColumns(null, null, "user", null)) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    if (name != null) {
                        columnsCache.add(name.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (Exception ex) {
            // leave cache empty to avoid blocking operations
        }
    }
}
