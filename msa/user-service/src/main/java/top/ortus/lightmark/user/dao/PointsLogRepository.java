package top.ortus.lightmark.user.dao;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import top.ortus.lightmark.user.dto.module.PointsLogDTO;

import java.util.List;

@Repository
public class PointsLogRepository {
    private final JdbcTemplate jdbcTemplate;

    public PointsLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PointsLogDTO> findByUserId(String userId) {
        return jdbcTemplate.query("select * from points_log where user_id = ? order by id desc",
                new BeanPropertyRowMapper<>(PointsLogDTO.class), userId);
    }
}
