package top.ortus.lightmark.order.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.ortus.lightmark.order.tools.ApiResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台看板统计内部接口(供 user-service 聚合调用)。
 * 路径受 OrderServiceSecurityConfig 保护(/internal/** 需有效 JWT,服务令牌亦可)。
 */
@RestController
@RequestMapping("/internal/admin/stats")
public class InternalStatsController {

    private final JdbcTemplate jdbc;

    public InternalStatsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 订单总量与总销售额(口径与单体一致)。 */
    @GetMapping("/orders/summary")
    public ApiResponse<Map<String, Object>> orderSummary() {
        Long totalOrders = jdbc.queryForObject("select count(*) from orders", Long.class);
        BigDecimal totalRevenue = jdbc.queryForObject(
                "select coalesce(sum(pay_amount), 0) from orders", BigDecimal.class);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalOrders", totalOrders == null ? 0L : totalOrders);
        data.put("totalRevenue", totalRevenue == null ? BigDecimal.ZERO : totalRevenue);
        return ApiResponse.ok(data);
    }

    /** 近 days 天(默认 7)每日订单量与销售额,缺数据的日期补 0,升序返回。 */
    @GetMapping("/orders/trends")
    public ApiResponse<List<Map<String, Object>>> orderTrends(@RequestParam(defaultValue = "7") int days) {
        int n = Math.max(1, Math.min(days, 90));
        Map<String, Map<String, Object>> byDay = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = n - 1; i >= 0; i--) {
            String day = today.minusDays(i).toString();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day);
            row.put("orderCount", 0);
            row.put("revenue", BigDecimal.ZERO);
            byDay.put(day, row);
        }
        String since = today.minusDays(n - 1L).toString();
        jdbc.query(
                "select date(create_time) d, count(*) c, coalesce(sum(pay_amount), 0) r from orders"
                        + " where date(create_time) >= ? group by d",
                rs -> {
                    Map<String, Object> row = byDay.get(String.valueOf(rs.getDate("d")));
                    if (row != null) {
                        row.put("orderCount", rs.getLong("c"));
                        row.put("revenue", rs.getBigDecimal("r"));
                    }
                },
                since);
        return ApiResponse.ok(new ArrayList<>(byDay.values()));
    }
}
