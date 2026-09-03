package top.ortus.lightmark.user.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.ortus.lightmark.common.ApiResponse;
import top.ortus.lightmark.user.client.AdminStatsClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台看板(admin 身份;经 AdminStatsClient 聚合 user/order/product 域数据)。
 * 用户数取本域库,订单统计与热门产品经 internal 接口获取,依赖故障时降级为空值。
 */
@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardController {

    private final JdbcTemplate jdbc;
    private final AdminStatsClient stats;

    public DashboardController(JdbcTemplate jdbc, AdminStatsClient stats) {
        this.jdbc = jdbc;
        this.stats = stats;
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        Long totalUsers = jdbc.queryForObject("select count(*) from `user` where deleted = 0", Long.class);
        Map<String, Object> order = stats.orderSummary();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUsers", totalUsers == null ? 0L : totalUsers);
        data.put("totalOrders", order.getOrDefault("totalOrders", 0L));
        data.put("totalRevenue", order.getOrDefault("totalRevenue", BigDecimal.ZERO));
        return ApiResponse.ok(data);
    }

    @GetMapping("/trends")
    public ApiResponse<List<Map<String, Object>>> trends() {
        return ApiResponse.ok(stats.orderTrends());
    }

    @GetMapping("/hot-products")
    public ApiResponse<List<Map<String, Object>>> hotProducts() {
        return ApiResponse.ok(stats.hotProducts());
    }
}
