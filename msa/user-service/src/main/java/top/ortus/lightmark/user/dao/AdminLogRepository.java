package top.ortus.lightmark.user.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import top.ortus.lightmark.user.dto.module.AdminLogDTO;

import java.util.List;

@Repository
public class AdminLogRepository {
    private final JdbcTemplate jdbcTemplate;

    public AdminLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int append(long adminId, String operation, String params, String result, String ip) {
        return jdbcTemplate.update("insert into admin_log (admin_id, operation, params, result, ip, create_time) values (?, ?, ?, ?, ?, now())",
                adminId, operation, params, result, ip);
    }

    public List<AdminLogDTO> findAll() {
        return jdbcTemplate.query("select * from admin_log order by id desc", new BeanPropertyRowMapper<>(AdminLogDTO.class));
    }
}
