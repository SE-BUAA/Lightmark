package top.ortus.lightmark.user.dao;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import top.ortus.lightmark.user.dto.module.TravelerDTO;

import java.util.List;

@Repository
public class TravelerRepository {
    private final JdbcTemplate jdbcTemplate;

    public TravelerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TravelerDTO> findByUserId(String userId) {
        return jdbcTemplate.query("select * from traveler where user_id = ? order by id desc",
                new BeanPropertyRowMapper<>(TravelerDTO.class), userId);
    }

    public TravelerDTO findOwned(String id, String userId) {
        List<TravelerDTO> list = jdbcTemplate.query("select * from traveler where id = ? and user_id = ?",
                new BeanPropertyRowMapper<>(TravelerDTO.class), id, userId);
        return list.isEmpty() ? null : list.get(0);
    }

    public int insert(TravelerDTO traveler) {
        return jdbcTemplate.update("insert into traveler (user_id, name, id_card, phone, id_type, create_time) values (?, ?, ?, ?, ?, now())",
                traveler.getUser_id(), traveler.getName(), traveler.getId_card(), traveler.getPhone(), traveler.getId_type());
    }

    public int update(TravelerDTO traveler) {
        return jdbcTemplate.update("update traveler set name = ?, id_card = ?, phone = ?, id_type = ? where id = ? and user_id = ?",
                traveler.getName(), traveler.getId_card(), traveler.getPhone(), traveler.getId_type(), traveler.getId(), traveler.getUser_id());
    }

    public int delete(String id, String userId) {
        return jdbcTemplate.update("delete from traveler where id = ? and user_id = ?", id, userId);
    }
}
