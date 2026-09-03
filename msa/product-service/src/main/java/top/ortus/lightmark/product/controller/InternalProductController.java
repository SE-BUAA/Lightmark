package top.ortus.lightmark.product.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.ortus.lightmark.common.ApiResponse;
import top.ortus.lightmark.product.dto.ProductDTO;
import top.ortus.lightmark.product.service.FlightProductService;
import top.ortus.lightmark.common.exception.ApiException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/product")
public class InternalProductController {
    private final FlightProductService service;
    private final JdbcTemplate jdbc;

    public InternalProductController(FlightProductService service, JdbcTemplate jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    @GetMapping("/{id}") public ApiResponse<ProductDTO> product(@PathVariable String id) { return ApiResponse.ok(service.product(id)); }

    /** 后台看板:销量 Top N 产品(口径同单体 hotProducts;字面路径优先于 /{id})。 */
    @GetMapping("/hot")
    public ApiResponse<List<Map<String, Object>>> hotProducts(@RequestParam(defaultValue = "10") int limit) {
        int n = Math.max(1, Math.min(limit, 50));
        List<Map<String, Object>> rows = new ArrayList<>();
        jdbc.query(
                "select id, name, product_type, sold_count from product order by sold_count desc, id asc limit ?",
                rs -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("id"));
                    m.put("name", rs.getString("name"));
                    m.put("productType", rs.getString("product_type"));
                    m.put("soldCount", rs.getLong("sold_count"));
                    rows.add(m);
                },
                n);
        return ApiResponse.ok(rows);
    }

    @PostMapping("/{id}/stock") public ApiResponse<Boolean> stock(@PathVariable String id, @RequestBody Map<String,Object> body) {
        long productId;
        try {
            productId = Long.parseLong(id);
        } catch (NumberFormatException ex) {
            throw new ApiException(400, "productId is invalid");
        }
        if (body == null) {
            throw new ApiException(400, "stock adjustment is required");
        }
        Object deltaValue = body.get("delta");
        if (deltaValue != null) {
            int delta;
            try {
                delta = Integer.parseInt(String.valueOf(deltaValue));
            } catch (NumberFormatException ex) {
                throw new ApiException(400, "delta is invalid");
            }
            if (delta == 0) throw new ApiException(400, "delta must not be zero");
            return ApiResponse.ok(service.adjustInventory(productId, Math.abs(delta), delta < 0));
        }
        int quantity;
        try {
            quantity = Integer.parseInt(String.valueOf(body.getOrDefault("quantity", 0)));
        } catch (NumberFormatException ex) {
            throw new ApiException(400, "quantity is invalid");
        }
        boolean deduct = !"RELEASE".equalsIgnoreCase(String.valueOf(body.getOrDefault("operation", "DEDUCT")));
        return ApiResponse.ok(service.adjustInventory(productId, quantity, deduct));
    }
}
