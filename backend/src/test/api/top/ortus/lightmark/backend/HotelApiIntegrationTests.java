package top.ortus.lightmark.backend;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HotelApiIntegrationTests extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userToken() {
        return bearerToken(2L, "普通用户", java.util.List.of("USER"));
    }

    @Test
    void hotelListDetailAndRoomsShouldReturnStructuredData() throws Exception {
        mockMvc.perform(get("/api/hotel/list")
                        .header("Authorization", userToken())
                        .param("keyword", "上海")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.records[0].id").value("2"))
                .andExpect(jsonPath("$.data.records[0].name").value("上海外滩酒店"));

        mockMvc.perform(get("/api/hotel/2")
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("2"))
                .andExpect(jsonPath("$.data.starLevel").value(5))
                .andExpect(jsonPath("$.data.address", containsString("黄浦区")));

        mockMvc.perform(get("/api/hotel/2/rooms")
                        .header("Authorization", userToken())
                        .param("checkIn", "2026-12-20")
                        .param("checkOut", "2026-12-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].roomId").value(21))
                .andExpect(jsonPath("$.data[0].roomName").value("标准大床房"));
    }

    @Test
    void hotelOrderShouldCreatePayAndApplyInvoice() throws Exception {
        String createResponse = mockMvc.perform(post("/api/hotel/order")
                        .header("Authorization", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomId": 21,
                                  "checkInDate": "2026-12-20",
                                  "checkOutDate": "2026-12-22",
                                  "roomNum": 1,
                                  "pointsDeduced": 0,
                                  "paymentMethod": "ALIPAY",
                                  "guestList": [
                                    {
                                      "name": "张三",
                                      "idCard": "110101199001011234",
                                      "phone": "13900000000"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.payAmount").value(1798.00))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createNode = objectMapper.readTree(createResponse).path("data");
        long orderId = createNode.path("orderId").asLong();
        String extraInfo = jdbcTemplate.queryForObject("select extra_info from orders where id = ?", String.class, orderId);
        assertThat(extraInfo).contains("\"hotelName\":\"上海外滩酒店\"");
        assertThat(extraInfo).contains("\"roomId\":21");

        mockMvc.perform(post("/api/hotel/order/{orderId}/pay", orderId)
                        .header("Authorization", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod": "ALIPAY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.paymentMethod").value("ALIPAY"));

        mockMvc.perform(post("/api/hotel/order/{orderId}/invoice", orderId)
                        .header("Authorization", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invoiceType": "electronic",
                                  "title": "拾光旅行",
                                  "taxNo": "91310000123456789A"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Integer invoiceCount = jdbcTemplate.queryForObject(
                "select count(*) from invoice_application where order_id = ? and title = ?",
                Integer.class,
                orderId,
                "拾光旅行"
        );
        assertThat(invoiceCount).isEqualTo(1);
    }

    @Test
    void hotelOrderShouldAllowReviewAfterCompletedStay() throws Exception {
        String createResponse = mockMvc.perform(post("/api/hotel/order")
                        .header("Authorization", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomId": 22,
                                  "checkInDate": "2026-08-27",
                                  "checkOutDate": "2026-08-28",
                                  "roomNum": 1,
                                  "pointsDeduced": 0,
                                  "paymentMethod": "WECHAT",
                                  "guestList": [
                                    {
                                      "name": "李四",
                                      "idCard": "110101199202021234",
                                      "phone": "13900000001"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long orderId = objectMapper.readTree(createResponse).path("data").path("orderId").asLong();

        mockMvc.perform(post("/api/hotel/order/{orderId}/pay", orderId)
                        .header("Authorization", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentMethod": "WECHAT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2));

        mockMvc.perform(post("/api/hotel/order/{orderId}/review", orderId)
                        .header("Authorization", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 5,
                                  "content": "房间整洁，出行方便，早餐也不错。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.content").value("房间整洁，出行方便，早餐也不错。"));

        mockMvc.perform(get("/api/hotel/2/reviews")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    void hotelOrderShouldRejectInvalidDatesAndMissingLogin() throws Exception {
        mockMvc.perform(get("/api/hotel/2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("login required"));

        mockMvc.perform(post("/api/hotel/order")
                        .header("Authorization", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomId": 21,
                                  "checkInDate": "2026-12-22",
                                  "checkOutDate": "2026-12-20",
                                  "roomNum": 1,
                                  "pointsDeduced": 0,
                                  "paymentMethod": "ALIPAY",
                                  "guestList": [
                                    {
                                      "name": "王五",
                                      "idCard": "110101199303031234",
                                      "phone": "13900000002"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("checkOutDate must be after checkInDate"));
    }
}
