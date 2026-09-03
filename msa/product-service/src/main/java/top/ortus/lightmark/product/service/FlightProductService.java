package top.ortus.lightmark.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import top.ortus.lightmark.common.PageResponse;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.product.dto.ProductDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class FlightProductService {
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FlightProductService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public PageResponse<ProductDTO> search(Map<String, String> params) {
        List<ProductDTO> products = jdbcTemplate.queryForList(
                "select * from product where product_type = ? and status = 1 and stock > 0", "FLIGHT")
                .stream().map(this::toProduct).filter(p -> matches(p, params)).sorted(comparator(params)).toList();
        int page = positive(params, "page", 1);
        int size = Math.min(positive(params, "size", DEFAULT_SIZE), MAX_SIZE);
        int from = Math.min((page - 1) * size, products.size());
        return new PageResponse<>(products.size(), page, size,
                new ArrayList<>(products.subList(from, Math.min(from + size, products.size()))));
    }

    public ProductDTO detail(String id) {
        if (id == null || id.isBlank()) throw new ApiException(400, "productId is required");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select * from product where id = ? and product_type = ? and status = 1", id, "FLIGHT");
        if (rows.isEmpty()) throw new ApiException(404, "flight not found");
        return toProduct(rows.get(0));
    }

    public Map<String, Object> priceCalendar(Map<String, String> params) {
        LocalDate start = parseDate(params == null ? null : params.get("startDate"));
        int days = Math.min(positive(params, "days", 30), 365);
        Map<String, BigDecimal> lowest = new HashMap<>();
        for (ProductDTO product : jdbcTemplate.queryForList(
                "select * from product where product_type = ? and status = 1 and stock > 0", "FLIGHT")
                .stream().map(this::toProduct).filter(p -> matches(p, params)).toList()) {
            String date = text(product, "departureDate", "departure_date", "date");
            if (date != null && product.getPrice() != null) lowest.merge(date, product.getPrice(), BigDecimal::min);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = start.plusDays(i); BigDecimal price = lowest.get(date.toString());
            result.add(Map.of("date", date.toString(), "lowestPrice", price == null ? BigDecimal.ZERO : price,
                    "available", price != null));
        }
        return Map.of("route", Map.of("departureCity", value(params, "departureCity"),
                        "arrivalCity", value(params, "arrivalCity")), "days", result);
    }

    public boolean adjustStock(long id, int quantity, boolean deduct) {
        if (quantity <= 0) throw new ApiException(400, "quantity must be positive");
        String sql = deduct
                ? "update product set stock = stock - ?, sold_count = sold_count + ? where id = ? and product_type = 'FLIGHT' and status = 1 and stock >= ?"
                : "update product set stock = stock + ?, sold_count = greatest(0, sold_count - ?) where id = ? and product_type = 'FLIGHT'";
        int changed = deduct
                ? jdbcTemplate.update(sql, quantity, quantity, id, quantity)
                : jdbcTemplate.update(sql, quantity, quantity, id);
        if (changed == 0) throw new ApiException(409, deduct ? "product stock is insufficient" : "product not found");
        return true;
    }

    public void recordView(long productId, Long userId, String source) {
        if (jdbcTemplate.queryForObject("select count(*) from product where id=? and status=1", Integer.class, productId) == 0) {
            throw new ApiException(404, "product not found");
        }
        jdbcTemplate.update("insert into product_view_log(product_id,user_id,view_source) values (?,?,?)", productId, userId, source == null || source.isBlank() ? "WEB" : source);
    }

    public boolean adjustInventory(long id, int quantity, boolean deduct) {
        if (quantity <= 0) throw new ApiException(400, "quantity must be positive");
        int changed = deduct
                ? jdbcTemplate.update("update product set stock=stock-?, sold_count=coalesce(sold_count,0)+? where id=? and status=1 and stock>=?", quantity, quantity, id, quantity)
                : jdbcTemplate.update("update product set stock=stock+?, sold_count=greatest(0,coalesce(sold_count,0)-?) where id=?", quantity, quantity, id);
        if (changed == 0) throw new ApiException(deduct ? 409 : 404, deduct ? "product stock is insufficient" : "product not found");
        return true;
    }

    public PageResponse<ProductDTO> products(Map<String, String> query) {
        String type = value(query, "productType");
        String name = value(query, "name");
        List<Object> args = new ArrayList<>();
        String sql = "select * from product where status=1";
        if (!type.isBlank()) { sql += " and product_type=?"; args.add(type); }
        if (!name.isBlank()) { sql += " and name like ?"; args.add("%" + name + "%"); }
        sql += " order by id desc";
        List<ProductDTO> rows = jdbcTemplate.queryForList(sql, args.toArray()).stream().map(this::toProduct).toList();
        int page=positive(query,"page",1), size=Math.min(positive(query,"size",10),100), from=Math.min((page-1)*size,rows.size());
        return new PageResponse<>(rows.size(), page, size, new ArrayList<>(rows.subList(from, Math.min(from+size, rows.size()))));
    }

    public ProductDTO product(String id) {
        if (id == null || id.isBlank()) throw new ApiException(400, "productId is required");
        List<Map<String,Object>> rows=jdbcTemplate.queryForList("select * from product where id=? and status=1",id);
        if(rows.isEmpty()) throw new ApiException(404,"product not found"); return toProduct(rows.get(0));
    }

    public PageResponse<Map<String,Object>> views(long productId, Long userId, Map<String,String> query) {
        int page=positive(query,"page",1), size=Math.min(positive(query,"size",20),100);
        List<Map<String,Object>> rows = userId == null
                ? jdbcTemplate.queryForList("select id,product_id,user_id,view_source,create_time from product_view_log where product_id=? order by id desc", productId)
                : jdbcTemplate.queryForList("select id,product_id,user_id,view_source,create_time from product_view_log where product_id=? and user_id=? order by id desc", productId,userId);
        int from=Math.min((page-1)*size,rows.size()); return new PageResponse<>(rows.size(),page,size,new ArrayList<>(rows.subList(from,Math.min(from+size,rows.size()))));
    }

    public PageResponse<Map<String,Object>> adminProducts(Map<String,String> query) {
        List<Map<String,Object>> rows = jdbcTemplate.queryForList("select id,product_type,name,price,stock,sold_count,status,update_time from product where (?='' or product_type=?) and (?='' or name like ?) and (?='' or status=?) order by id desc", value(query,"productType"), value(query,"productType"), value(query,"name"), "%"+value(query,"name")+"%", value(query,"status"), value(query,"status"));
        int page=positive(query,"page",1), size=Math.min(positive(query,"size",10),100), from=Math.min((page-1)*size,rows.size());
        return new PageResponse<>(rows.size(),page,size,new ArrayList<>(rows.subList(from,Math.min(from+size,rows.size()))));
    }
    public Map<String,Object> createProduct(Map<String,Object> body) { if(body==null||String.valueOf(body.getOrDefault("name","")).isBlank()) throw new ApiException(400,"name is required"); jdbcTemplate.update("insert into product(product_type,name,price,stock,sold_count,status,category_tags,extra) values (?,?,?,?,?,?,?,?)", body.getOrDefault("productType","OTHER"),body.get("name"),new BigDecimal(String.valueOf(body.getOrDefault("price",0))),Integer.parseInt(String.valueOf(body.getOrDefault("stock",0))),Integer.parseInt(String.valueOf(body.getOrDefault("soldCount",0))),Integer.parseInt(String.valueOf(body.getOrDefault("status",1))),body.get("categoryTags"),body.get("extra")); return jdbcTemplate.queryForMap("select * from product where id=last_insert_id()"); }
    public boolean updateProduct(long id,String field,Object value){String sql=switch(field){case "status"->"update product set status=? where id=?";case "price"->"update product set price=? where id=?";case "stock"->"update product set stock=? where id=?";default->throw new ApiException(400,"unsupported product field");}; if(jdbcTemplate.update(sql,value,id)==0)throw new ApiException(404,"product not found");return true;}
    public boolean deleteProduct(long id){if(jdbcTemplate.update("delete from product where id=?",id)==0)throw new ApiException(404,"product not found");return true;}

    private ProductDTO toProduct(Map<String, Object> row) {
        ProductDTO p = new ProductDTO(); p.setId(String.valueOf(first(row, "id", "ID")));
        p.setProduct_type(text(row, "product_type", "PRODUCT_TYPE")); p.setName(text(row, "name", "NAME"));
        Object price = first(row, "price", "PRICE"); if (price != null) p.setPrice(new BigDecimal(price.toString()));
        p.setStock(number(row, "stock", "STOCK")); p.setSold_count(number(row, "sold_count", "SOLD_COUNT"));
        p.setStatus(number(row, "status", "STATUS")); p.setExtra(text(row, "extra", "EXTRA"));
        p.setCategory_tags(text(row, "category_tags", "CATEGORY_TAGS")); return p;
    }
    // 多机场城市统一城市码（与单体 FlightSearchService.AIRPORT_CITY_CODES 一致）：
    // 前端按机场选项发送机场码(PEK/PKX/NAY)，数据里存城市码(BJS)，必须归一化后比较。
    private static final Map<String, String> AIRPORT_CITY_CODES = Map.ofEntries(
            Map.entry("PEK", "BJS"),
            Map.entry("PKX", "BJS"),
            Map.entry("NAY", "BJS"),
            Map.entry("SHA", "SHA"),
            Map.entry("PVG", "SHA"),
            Map.entry("CTU", "CTU"),
            Map.entry("TFU", "CTU")
    );

    private boolean matches(ProductDTO p, Map<String, String> params) {
        // 路由字段：多 key 兼容(机场码/城市码/城市名/机场名)，城市码归一化 + 文本包含匹配
        return matchesRoute(p, params, "departureCity", "departureCity", "departure_city", "departureAirport",
                "departure_airport", "departureAirportCode", "departure_airport_code", "departure")
                && matchesRoute(p, params, "arrivalCity", "arrivalCity", "arrival_city", "arrivalAirport",
                "arrival_airport", "arrivalAirportCode", "arrival_airport_code", "arrival");
    }

    /** 路由匹配：期望值(机场码/城市码/中文名)与 extra 中任一字段按城市码归一化或包含关系命中即匹配。 */
    private boolean matchesRoute(ProductDTO p, Map<String, String> params, String paramKey, String... extraKeys) {
        String wanted = value(params, paramKey);
        if (wanted == null || wanted.isBlank()) {
            return true;
        }
        String normalized = normalize(wanted);
        List<String> wantedCityCodes = cityCodes(normalized);
        for (String extraKey : extraKeys) {
            for (String actual : textValues(p, extraKey)) {
                if (actual == null || actual.isBlank()) {
                    continue;
                }
                // 1) 归一化后城市码相等：BJS == BJS / PEK(→BJS) == BJS
                List<String> actualCityCodes = cityCodes(normalize(actual));
                if (actualCityCodes.stream().anyMatch(wantedCityCodes::contains)) {
                    return true;
                }
                // 2) 文本包含：北京 vs 北京首都/北京大兴
                if (actual.contains(normalized) || normalized.contains(actual)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 把「城市码/机场码/中文名」都换算成候选城市码集合。 */
    private List<String> cityCodes(String token) {
        List<String> codes = new ArrayList<>();
        if (token.isBlank()) {
            return codes;
        }
        String upper = token.toUpperCase(Locale.ROOT);
        if (upper.matches("[A-Z]{2,4}")) {
            codes.add(AIRPORT_CITY_CODES.getOrDefault(upper, upper));
        } else {
            codes.add(token);
        }
        return codes;
    }

    /** 取 extra JSON 中某 key 的字符串值(兼容字符串/数组元素/嵌套 map 的 name)。 */
    private List<String> textValues(ProductDTO p, String key) {
        List<String> values = new ArrayList<>();
        try {
            JsonNode n = objectMapper.readTree(p.getExtra());
            JsonNode node = n == null ? null : n.get(key);
            if (node == null) {
                // 兼容带下划线别名（调用方已传全，此处兜底大小写变体）
                return values;
            }
            if (node.isTextual()) {
                values.add(node.asText());
            } else if (node.isArray()) {
                node.forEach(item -> values.add(item.isTextual() ? item.asText() : item.toString()));
            } else if (node.isObject()) {
                if (node.hasNonNull("name")) values.add(node.get("name").asText());
                if (node.hasNonNull("type")) values.add(node.get("type").asText());
                values.add(node.toString());
            } else {
                values.add(node.asText());
            }
        } catch (Exception ignored) {
            // 忽略无法解析的 extra
        }
        return values;
    }

    private String normalize(String token) {
        return token == null ? "" : token.trim();
    }

    private boolean equalsField(ProductDTO p, Map<String, String> params, String... keys) {
        String wanted = value(params, keys[0]);
        if (wanted == null || wanted.isBlank()) {
            return true;
        }
        String actual = text(p, keys);
        return wanted.equalsIgnoreCase(actual == null ? "" : actual);
    }
    private Comparator<ProductDTO> comparator(Map<String, String> params) { return "desc".equalsIgnoreCase(value(params, "sortOrder")) ? Comparator.comparing(ProductDTO::getPrice, Comparator.nullsLast(Comparator.reverseOrder())) : Comparator.comparing(ProductDTO::getPrice, Comparator.nullsLast(Comparator.naturalOrder())); }
    private String text(ProductDTO p, String... keys) { try { JsonNode n = objectMapper.readTree(p.getExtra()); for (String k : keys) if (n != null && n.hasNonNull(k)) return n.get(k).asText(); } catch (Exception ignored) { } return null; }
    private String text(Map<String, Object> row, String... keys) { Object value = first(row, keys); return value == null ? null : value.toString(); }
    private Object first(Map<String, Object> row, String... keys) { for (String key : keys) if (row.containsKey(key)) return row.get(key); return null; }
    private int number(Map<String, Object> row, String... keys) { Object value = first(row, keys); return value == null ? 0 : Integer.parseInt(value.toString()); }
    private int positive(Map<String, String> p, String key, int fallback) { try { int n = Integer.parseInt(value(p, key)); return n > 0 ? n : fallback; } catch (Exception e) { return fallback; } }
    private String value(Map<String, String> p, String key) { return p == null ? "" : p.getOrDefault(key, ""); }
    private LocalDate parseDate(String value) { try { return value == null || value.isBlank() ? LocalDate.now() : LocalDate.parse(value); } catch (Exception e) { throw new ApiException(400, "invalid startDate"); } }
}
