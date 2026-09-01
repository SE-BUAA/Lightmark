package top.ortus.lightmark.user.dao;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AuthVerificationCodeRepositoryImpl implements AuthVerificationCodeRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthVerificationCodeRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AuthVerificationCode findActiveByTargetAndChannel(String target, String channel) {
        String sql = """
                select *
                from auth_verification_code
                where target = ? and channel = ? and consumed_time is null
                order by update_time desc, create_time desc
                limit 1
                """;
        List<AuthVerificationCode> list = jdbcTemplate.query(
                sql,
                new BeanPropertyRowMapper<>(AuthVerificationCode.class),
                target,
                channel
        );
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public int upsert(AuthVerificationCode code) {
        AuthVerificationCode existing = findActiveByTargetAndChannel(code.getTarget(), code.getChannel());
        if (existing == null) {
            String sql = """
                    insert into auth_verification_code
                    (target, channel, code, expire_time, consumed_time, send_count, create_time, update_time)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            return jdbcTemplate.update(
                    sql,
                    code.getTarget(),
                    code.getChannel(),
                    code.getCode(),
                    toTimestamp(code.getExpire_time()),
                    toTimestamp(code.getConsumed_time()),
                    code.getSend_count(),
                    toTimestamp(code.getCreate_time()),
                    toTimestamp(code.getUpdate_time())
            );
        }
        String sql = """
                update auth_verification_code
                set code = ?, expire_time = ?, consumed_time = ?, send_count = ?, update_time = ?
                where id = ?
                """;
        return jdbcTemplate.update(
                sql,
                code.getCode(),
                toTimestamp(code.getExpire_time()),
                toTimestamp(code.getConsumed_time()),
                code.getSend_count(),
                toTimestamp(code.getUpdate_time()),
                existing.getId()
        );
    }

    @Override
    public int consume(String id) {
        String sql = "update auth_verification_code set consumed_time = ?, update_time = ? where id = ?";
        Timestamp now = toTimestamp(LocalDateTime.now());
        return jdbcTemplate.update(sql, now, now, id);
    }

    @Override
    public int countRecentByTargetAndChannel(String target, String channel, int withinMinutes) {
        String sql = """
                select count(*)
                from auth_verification_code
                where target = ? and channel = ? and create_time >= ?
                """;
        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                target,
                channel,
                toTimestamp(LocalDateTime.now().minusMinutes(withinMinutes))
        );
        return count == null ? 0 : count;
    }

    private Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }
}
