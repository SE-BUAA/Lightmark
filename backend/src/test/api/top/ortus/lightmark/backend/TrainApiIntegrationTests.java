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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrainApiIntegrationTests extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userToken() {
        return bearerToken(2L, "普通用户", List.of("USER"));
    }

    @Test
    void trainOptionsShouldReturnStationsAndDateChoices() throws Exception {
        mockMvc.perform(get("/api/trains/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.startStations.length()", greaterThan(5)))
                .andExpect(jsonPath("$.data.endStations.length()", greaterThan(5)))
                .andExpect(jsonPath("$.data.dates.length()", greaterThan(2)));
    }

    @Test
    void trainOrderShouldCreatePayAndRefundStudentTicket() throws Exception {
        Integer initialSoldCount = jdbcTemplate.queryForObject("select sold_count from product where id = 3", Integer.class);

        String createResponse = mockMvc.perform(post("/api/orders/train")
                        .header("Authorization", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "3",
                                  "passengerName": "张三",
                                  "passengerPhone": "13900000000",
                                  "passengerAge": 20,
                                  "seatType": "二等座",
                                  "isStudent": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.payAmount").value(331.80))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderNo = objectMapper.readTree(createResponse).path("data").path("orderNo").asText();
        String extraInfo = jdbcTemplate.queryForObject(
                "select extra_info from orders where order_no = ?",
                String.class,
                orderNo
        );
        Integer soldCountAfterCreate = jdbcTemplate.queryForObject("select sold_count from product where id = 3", Integer.class);
        assertThat(extraInfo)
                .contains("\"seatType\":\"二等座\"")
                .contains("\"ticketType\":\"STUDENT\"")
                .contains("\"discountRate\":0.6")
                .contains("\"startStation\":\"北京南\"")
                .contains("\"endStation\":\"上海虹桥\"");
        assertThat(soldCountAfterCreate).isEqualTo(initialSoldCount + 1);

        mockMvc.perform(post("/api/orders/train/{orderNo}/pay", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.pickupCode", matchesPattern("[A-Z0-9]{6}")));

        mockMvc.perform(post("/api/orders/train/{orderNo}/refund", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.status").value(4))
                .andExpect(jsonPath("$.data.paidAmount").value(331.80))
                .andExpect(jsonPath("$.data.refundAmount").value(331.80))
                .andExpect(jsonPath("$.data.refundRule", containsString("全额退还")));

        Integer finalStatus = jdbcTemplate.queryForObject(
                "select status from orders where order_no = ?",
                Integer.class,
                orderNo
        );
        Integer soldCountAfterRefund = jdbcTemplate.queryForObject("select sold_count from product where id = 3", Integer.class);
        Integer pointLogCount = jdbcTemplate.queryForObject(
                "select count(*) from points_log where order_id = (select id from orders where order_no = ?) and source in ('TRAIN_PAY', 'TRAIN_REFUND')",
                Integer.class,
                orderNo
        );
        assertThat(finalStatus).isEqualTo(4);
        assertThat(soldCountAfterRefund).isEqualTo(initialSoldCount);
        assertThat(pointLogCount).isEqualTo(2);
    }

    @Test
    void trainOrderShouldCancelPendingOrderAndRestoreSoldCount() throws Exception {
        Integer initialSoldCount = jdbcTemplate.queryForObject("select sold_count from product where id = 3", Integer.class);

        String createResponse = mockMvc.perform(post("/api/orders/train")
                        .header("Authorization", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "3",
                                  "passengerName": "李四",
                                  "passengerPhone": "13900000001",
                                  "passengerAge": 16,
                                  "seatType": "一等座",
                                  "isStudent": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.payAmount").value(442.40))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderNo = objectMapper.readTree(createResponse).path("data").path("orderNo").asText();
        Integer soldCountAfterCreate = jdbcTemplate.queryForObject("select sold_count from product where id = 3", Integer.class);
        assertThat(soldCountAfterCreate).isEqualTo(initialSoldCount + 1);

        mockMvc.perform(post("/api/orders/train/{orderNo}/cancel", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Integer finalStatus = jdbcTemplate.queryForObject(
                "select status from orders where order_no = ?",
                Integer.class,
                orderNo
        );
        Integer soldCountAfterCancel = jdbcTemplate.queryForObject("select sold_count from product where id = 3", Integer.class);
        assertThat(finalStatus).isEqualTo(2);
        assertThat(soldCountAfterCancel).isEqualTo(initialSoldCount);
    }

    @Test
    void trainOrderShouldRejectInvalidPassengerPhone() throws Exception {
        mockMvc.perform(post("/api/orders/train")
                        .header("Authorization", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": "3",
                                  "passengerName": "王五",
                                  "passengerPhone": "12345",
                                  "passengerAge": 28,
                                  "seatType": "二等座"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请输入正确的中国大陆手机号"));
    }
}
