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
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VacationApiIntegrationTests extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void vacationSearchShouldFilterByDestinationDateDaysAndTags() throws Exception {
        mockMvc.perform(post("/api/vacations/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "destination": "三亚",
                                  "departCity": "北京",
                                  "date": "2026-06-15",
                                  "minDays": 5,
                                  "maxDays": 5,
                                  "tags": ["海岛", "亲子"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("201"))
                .andExpect(jsonPath("$.data[0].name").value("三亚海岛五日自由行"));
    }

    @Test
    void vacationDetailAiShouldReturnLongEnoughContent() throws Exception {
        mockMvc.perform(get("/api/vacations/201/detail-ai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.productId").value("201"))
                .andExpect(jsonPath("$.data.content", matchesPattern("(?s).{21,}")));
    }

    @Test
    void vacationOrderShouldCreatePayGenerateAssistantAndRefundWithInsurance() throws Exception {
        Integer initialSoldCount = jdbcTemplate.queryForObject("select sold_count from product where id = 201", Integer.class);

        String createResponse = mockMvc.perform(post("/api/orders/vacation")
                        .header("Authorization", bearerToken(2L, "普通用户", java.util.List.of("USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "201",
                                  "travelerName": "张三",
                                  "travelerPhone": "13900000000",
                                  "travelerCount": 1,
                                  "cancellationInsurance": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.payAmount").value(3148.95))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createResponse).path("data");
        String orderNo = created.path("orderNo").asText();
        assertThat(orderNo).isNotBlank();

        String extraInfo = jdbcTemplate.queryForObject(
                "select extra_info from orders where order_no = ?",
                String.class,
                orderNo
        );
        Integer soldCountAfterCreate = jdbcTemplate.queryForObject("select sold_count from product where id = 201", Integer.class);
        assertThat(extraInfo).contains("\"cancellationInsurance\":true");
        assertThat(extraInfo).contains("\"insuranceAmount\":149.95");
        assertThat(soldCountAfterCreate).isEqualTo(initialSoldCount + 1);

        mockMvc.perform(post("/api/orders/{orderNo}/pay", orderNo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.pickupCode", matchesPattern("[A-Z0-9]{6}")));

        mockMvc.perform(get("/api/orders/vacation/{orderNo}/assistant", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.destination").value("三亚"))
                .andExpect(jsonPath("$.data.content", matchesPattern("(?s).{21,}")));

        mockMvc.perform(post("/api/orders/vacation/{orderNo}/refund", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.status").value(4))
                .andExpect(jsonPath("$.data.paidAmount").value(3148.95))
                .andExpect(jsonPath("$.data.refundAmount").value(3148.95))
                .andExpect(jsonPath("$.data.refundRule", containsString("全额退款")));

        Integer finalStatus = jdbcTemplate.queryForObject(
                "select status from orders where order_no = ?",
                Integer.class,
                orderNo
        );
        Integer soldCountAfterRefund = jdbcTemplate.queryForObject("select sold_count from product where id = 201", Integer.class);
        assertThat(finalStatus).isEqualTo(4);
        assertThat(soldCountAfterRefund).isEqualTo(initialSoldCount);
    }

    @Test
    void vacationOrderShouldRejectOfflineProductsAndInvalidPhone() throws Exception {
        jdbcTemplate.update("update product set status = 0 where id = 201");

        mockMvc.perform(post("/api/orders/vacation")
                        .header("Authorization", bearerToken(2L, "普通用户", java.util.List.of("USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "201",
                                  "travelerName": "张三",
                                  "travelerPhone": "13900000000",
                                  "travelerCount": 1,
                                  "cancellationInsurance": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("度假产品不存在或已下架"));

        mockMvc.perform(post("/api/orders/vacation")
                        .header("Authorization", bearerToken(2L, "普通用户", java.util.List.of("USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "202",
                                  "travelerName": "张三",
                                  "travelerPhone": "12345",
                                  "travelerCount": 1,
                                  "cancellationInsurance": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请输入正确的中国大陆手机号"));
    }
}
