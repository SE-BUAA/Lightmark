package top.ortus.lightmark.product.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import top.ortus.lightmark.product.dto.TrainTicketDTO;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 12306 火车票查询（MCP Streamable HTTP 客户端）。
 *
 * 与 mcp-12306-server 的交互必须遵循 MCP 2025-03-26 会话流程：
 * initialize -> 保存响应头 Mcp-Session-Id -> notifications/initialized ->
 * 之后每次 tools/call 都携带 Mcp-Session-Id 请求头（无会话会被服务器拒绝）。
 */
@Service
public class TrainProductService {

    private static final String SESSION_HEADER = "Mcp-Session-Id";

    /** station.csv 站名缓存（懒加载，线程不安全但只赋值一次同一内容，可接受） */
    private static volatile List<String> STATIONS;

    private final RestClient client;
    private final ObjectMapper mapper;
    private final AtomicReference<String> sessionId = new AtomicReference<>();
    private final AtomicLong requestId = new AtomicLong(10);

    public TrainProductService(RestClient.Builder builder, ObjectMapper mapper,
                               @Value("${train.mcp-url:http://150.230.223.11:9000/mcp}") String url) {
        this.client = builder.baseUrl(url).build();
        this.mapper = mapper;
    }

    public List<TrainTicketDTO> search(Map<String, Object> body, boolean transfer) {
        if (body == null || blank(body, "startStation") || blank(body, "endStation")) {
            return List.of();
        }
        String date = String.valueOf(body.getOrDefault("date", LocalDate.now()));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("from_station", body.get("startStation"));
        args.put("to_station", body.get("endStation"));
        args.put("train_date", date);
        if (transfer) {
            args.put("middle_station", "");
        }
        Map<String, Object> response = call(transfer ? "query-transfer" : "query-tickets", args);
        return tickets(response);
    }

    public List<Map<String, Object>> calendar(Map<String, Object> body) {
        if (body == null || blank(body, "startStation") || blank(body, "endStation") || blank(body, "month")) {
            return List.of();
        }
        YearMonth month = YearMonth.parse(String.valueOf(body.get("month")));
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 1; i <= month.lengthOfMonth(); i++) {
            Map<String, Object> req = new HashMap<>(body);
            req.put("date", month.atDay(i).toString());
            List<TrainTicketDTO> t = search(req, false);
            if (!t.isEmpty()) {
                out.add(Map.of("date", req.get("date"),
                        "ticketCount", t.stream().mapToInt(x -> x.stock() == null ? 0 : x.stock()).sum(),
                        "trainCount", t.size()));
            }
        }
        return out;
    }

    public Map<String, Object> options() {
        // 与单体 /api/trains/options 对齐：startStations/endStations/dates
        // （前端 TrainsView 依赖这三个键名；站名来自 station.csv 第 2 列，dates 为今天起 180 天）
        List<String> stations = stations();
        List<String> dates = new ArrayList<>();
        for (int i = 0; i < 180; i++) {
            dates.add(LocalDate.now().plusDays(i).toString());
        }
        return Map.of("startStations", stations, "endStations", stations, "dates", dates);
    }

    /** 读取 classpath station.csv（格式: 简码,站名,...），返回第 2 列站名，保持文件顺序。 */
    private static List<String> stations() {
        if (STATIONS != null) {
            return STATIONS;
        }
        List<String> names = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                TrainProductService.class.getClassLoader().getResourceAsStream("station.csv"),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",");
                if (columns.length > 1 && !columns[1].isBlank()) {
                    names.add(columns[1].trim());
                }
            }
        } catch (Exception ignored) {
            // 站点文件缺失时返回空列表，页面下拉为空但不崩溃
        }
        STATIONS = names;
        return names;
    }

    public TrainTicketDTO detail(String id) {
        try {
            return mapper.readValue(
                    new String(Base64.getUrlDecoder().decode(id.startsWith("MCP:") ? id.substring(4) : id)),
                    TrainTicketDTO.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid train ticket");
        }
    }

    // ==================== MCP 会话与工具调用 ====================

    private Map<String, Object> call(String name, Map<String, Object> args) {
        try {
            return doCall(name, args);
        } catch (RuntimeException ex) {
            // 会话可能过期/失效：重置后重试一次
            sessionId.set(null);
            try {
                return doCall(name, args);
            } catch (RuntimeException ignored) {
                return Map.of("success", false);
            }
        }
    }

    private synchronized Map<String, Object> doCall(String name, Map<String, Object> args) {
        ensureSession();
        Map<String, Object> body = Map.of("jsonrpc", "2.0", "id", requestId.incrementAndGet(),
                "method", "tools/call",
                "params", Map.of("name", name, "arguments", args));
        try {
            String raw = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .header(SESSION_HEADER, sessionId.get())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = mapper.readTree(normalize(raw));
            String text = root.at("/result/content/0/text").asText("");
            if (text.isBlank()) {
                return Map.of("success", false);
            }
            return mapper.readValue(normalize(text), new TypeReference<>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("12306 MCP tools/call failed: " + ex.getMessage(), ex);
        }
    }

    /** 建立 MCP 会话：initialize 获取 Mcp-Session-Id 并发送 initialized 通知。 */
    private synchronized void ensureSession() {
        String sid = sessionId.get();
        if (sid != null && !sid.isBlank()) {
            return;
        }
        Map<String, Object> init = Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize",
                "params", Map.of("protocolVersion", "2025-03-26",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "lightmark-product-service", "version", "1.0")));
        String newSession = client.post()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .body(init)
                .exchange((request, response) -> response.getHeaders().getFirst(SESSION_HEADER));
        if (newSession == null || newSession.isBlank()) {
            throw new IllegalStateException("12306 MCP session initialize failed");
        }
        sessionId.set(newSession);
        try {
            client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(SESSION_HEADER, newSession)
                    .body(Map.of("jsonrpc", "2.0", "method", "notifications/initialized", "params", Map.of()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ignored) {
            // 部分 MCP 服务器对通知不返回 body，忽略
        }
    }

    /** 兼容 SSE（data: 行）与 markdown 代码块包裹的 JSON 响应。 */
    private String normalize(String text) {
        String json = text == null ? "" : text.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        if (json.startsWith("event:") || json.startsWith("data:")) {
            json = json.lines()
                    .map(String::trim)
                    .filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring("data:".length()).trim())
                    .filter(line -> !line.isBlank() && !"[DONE]".equals(line))
                    .findFirst()
                    .orElse("");
        }
        return json;
    }

    // ==================== 结果映射 ====================

    List<TrainTicketDTO> tickets(Map<String, Object> response) {
        Object rows = response.get("tickets");
        if (!(rows instanceof List<?>)) {
            rows = response.get("trains");
        }
        if (!(rows instanceof List<?> list)) {
            return List.of();
        }
        List<TrainTicketDTO> out = new ArrayList<>();
        for (Object row : list) {
            if (row instanceof Map<?, ?> m) {
                Map<String, Object> x = new LinkedHashMap<>();
                m.forEach((k, v) -> x.put(String.valueOf(k), v));
                Map<String, Integer> seats = normalizeSeats(x.get("seats"));
                if (seats.isEmpty()) {
                    seats = normalizeSeats(x.get("seat_info"));
                }
                int stock = integer(x.get("stock"));
                if (stock <= 0) {
                    stock = seats.values().stream().mapToInt(Integer::intValue).sum();
                }
                Double price = number(x.get("price"));
                out.add(new TrainTicketDTO(
                        String.valueOf(x.getOrDefault("id", x.getOrDefault("train_no", ""))),
                        String.valueOf(x.getOrDefault("name", x.getOrDefault("train_no", ""))),
                        price,
                        stock,
                        integer(x.get("soldCount")),
                        List.of(),
                        x,
                        seats,
                        Map.of()));
            }
        }
        return out;
    }

    private boolean blank(Map<String, Object> b, String k) {
        return b.get(k) == null || String.valueOf(b.get(k)).isBlank();
    }

    private Double number(Object x) {
        try {
            return x == null ? null : Double.valueOf(x.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer integer(Object x) {
        try {
            return x == null ? 0 : Integer.valueOf(x.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> normalizeSeats(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Integer> seats = new LinkedHashMap<>();
        Map<String, String> names = Map.of(
                "business", "商务座", "first_class", "一等座", "second_class", "二等座",
                "soft_sleeper", "软卧", "hard_sleeper", "硬卧", "hard_seat", "硬座",
                "no_seat", "无座");
        raw.forEach((key, item) -> {
            String seat = names.getOrDefault(String.valueOf(key), String.valueOf(key));
            int count = seatCount(item);
            if (count > 0) {
                seats.put(seat, count);
            }
        });
        return seats;
    }

    private int seatCount(Object value) {
        if (value == null) return 0;
        String text = String.valueOf(value).trim();
        if (text.isBlank() || "无".equals(text) || "--".equals(text) || "候补".equals(text) || "售完".equals(text)) return 0;
        if ("有".equals(text) || text.contains("充足")) return 20;
        try {
            return Math.max(0, Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
