package top.ortus.lightmark.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import top.ortus.lightmark.backend.service.FlightSearchService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FlightSearchApiIntegrationTests extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FlightSearchService flightSearchService;

    /**
     * 测试数据（data-h2.sql）中航班起飞时间为 2026-06-20 08:30（Asia/Shanghai）。
     * 把时钟固定到起飞前 24 小时以上，保证退款手续费率分支（10%）稳定可断言，
     * 避免测试结果随真实时间漂移（例如日期过后手续费率翻到 30% 导致断言失败）。
     */
    @BeforeEach
    void fixFlightClock() {
        flightSearchService.setClock(Clock.fixed(
                Instant.parse("2026-06-19T00:00:00Z"),
                ZoneId.of("Asia/Shanghai")));
    }

    private String userToken() {
        return bearerToken(2L, "普通用户", List.of("USER"));
    }

    // 覆盖前端机票模块主链路：搜索、价格日历、预览下单、支付、取消/退款和库存回补。
    @Test
    void flightSearchShouldFilterByRouteDateCabinAndSortByPrice() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .param("departureCity", "BJS")
                        .param("arrivalCity", "SHA")
                        .param("departureDate", "2026-06-20")
                        .param("adultCount", "2")
                        .param("cabin", "ECONOMY")
                        .param("sort", "price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].name").value("MU5678 北京-上海"))
                .andExpect(jsonPath("$.data.list[1].name").value("CA1234 北京-上海"));
    }

    @Test
    void flightSearchShouldAcceptAirportCodesThroughExistingCityParams() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .param("departureCity", "PKX")
                        .param("arrivalCity", "PVG")
                        .param("departureDate", "2026-06-20")
                        .param("adultCount", "2")
                        .param("cabin", "ECONOMY")
                        .param("sort", "price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].name").value("MU5678 北京-上海"))
                .andExpect(jsonPath("$.data.list[1].name").value("CA1234 北京-上海"));
    }

    @Test
    void flightSearchShouldAcceptProvinceAirportCodeLists() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .param("departureCity", "PEK,PKX,BJS")
                        .param("arrivalCity", "SHA,PVG")
                        .param("departureDate", "2026-07-01")
                        .param("adultCount", "1")
                        .param("cabin", "ECONOMY")
                        .param("sort", "price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].name").value("DBG1001 北京大兴-上海浦东"))
                .andExpect(jsonPath("$.data.list[1].name").value("DBG1002 北京首都-上海虹桥"));
    }

    @Test
    void flightSearchShouldRespectCabinAndPassengerStock() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .param("departureCity", "BJS")
                        .param("arrivalCity", "SHA")
                        .param("departureDate", "2026-06-20")
                        .param("adultCount", "40")
                        .param("cabin", "BUSINESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].name").value("CA1234 北京-上海"));
    }

    @Test
    void flightSearchShouldNotFilterCabinWhenCabinIsBlank() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .param("departureCity", "BJS")
                        .param("arrivalCity", "SHA")
                        .param("departureDate", "2026-06-20")
                        .param("adultCount", "1")
                        .param("sort", "price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void flightSearchShouldPaginateResults() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .param("departureCity", "BJS")
                        .param("departureDate", "2026-06-20")
                        .param("page", "2")
                        .param("size", "1")
                        .param("sort", "departureTime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.list.length()").value(1))
                .andExpect(jsonPath("$.data.list[0].name").value("CZ9001 北京-广州"));
    }

    @Test
    void flightDetailShouldReturnFlightProductWithExtra() throws Exception {
        mockMvc.perform(get("/api/flights/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.product_type").value("FLIGHT"))
                .andExpect(jsonPath("$.data.name").value("CA1234 北京-上海"))
                .andExpect(jsonPath("$.data.extra").value(org.hamcrest.Matchers.containsString("departureCity")));
    }

    @Test
    void flightDetailShouldRejectNonFlightProduct() throws Exception {
        mockMvc.perform(get("/api/flights/2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("flight not found"));
    }

    @Test
    void flightDetailShouldReturnNotFoundForMissingProduct() throws Exception {
        mockMvc.perform(get("/api/flights/99999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("flight not found"));
    }

    @Test
    void priceCalendarShouldReturnThirtyDaysWithLowestPrice() throws Exception {
        mockMvc.perform(get("/api/flights/price-calendar")
                        .param("departureCity", "BJS")
                        .param("arrivalCity", "SHA")
                        .param("startDate", "2026-06-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.days", hasSize(30)))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-06-20"))
                .andExpect(jsonPath("$.data.days[0].lowestPrice").value(520.00))
                .andExpect(jsonPath("$.data.days[0].available").value(true));
    }

    @Test
    void priceCalendarShouldReturnUpToOneYearWhenRequested() throws Exception {
        mockMvc.perform(get("/api/flights/price-calendar")
                        .param("departureCity", "BJS")
                        .param("arrivalCity", "SHA")
                        .param("startDate", "2026-05-28")
                        .param("days", "365"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.days", hasSize(365)))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-05-28"))
                .andExpect(jsonPath("$.data.days[34].date").value("2026-07-01"))
                .andExpect(jsonPath("$.data.days[34].lowestPrice").value(560.00))
                .andExpect(jsonPath("$.data.days[79].date").value("2026-08-15"))
                .andExpect(jsonPath("$.data.days[79].lowestPrice").value(610.00))
                .andExpect(jsonPath("$.data.days[364].date").value("2027-05-27"));
    }

    @Test
    void priceCalendarShouldAcceptProvinceAirportCodeLists() throws Exception {
        mockMvc.perform(get("/api/flights/price-calendar")
                        .param("departureCity", "PEK,PKX,BJS")
                        .param("arrivalCity", "SHA,PVG")
                        .param("startDate", "2026-07-01")
                        .param("days", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.days", hasSize(5)))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-07-01"))
                .andExpect(jsonPath("$.data.days[0].lowestPrice").value(560.00))
                .andExpect(jsonPath("$.data.days[4].date").value("2026-07-05"))
                .andExpect(jsonPath("$.data.days[4].lowestPrice").value(420.00));
    }

    @Test
    void priceCalendarShouldLimitRangeToOneYear() throws Exception {
        mockMvc.perform(get("/api/flights/price-calendar")
                        .param("departureCity", "BJS")
                        .param("arrivalCity", "SHA")
                        .param("startDate", "2026-05-28")
                        .param("days", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.days", hasSize(365)));
    }

    @Test
    void priceCalendarShouldRespectDirectOnlyFilter() throws Exception {
        mockMvc.perform(get("/api/flights/price-calendar")
                        .param("departureCity", "PKX")
                        .param("arrivalCity", "PVG")
                        .param("startDate", "2026-07-05")
                        .param("directOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-07-05"))
                .andExpect(jsonPath("$.data.days[0].lowestPrice").value(0))
                .andExpect(jsonPath("$.data.days[0].available").value(false));

        mockMvc.perform(get("/api/flights/price-calendar")
                        .param("departureCity", "PKX")
                        .param("arrivalCity", "PVG")
                        .param("startDate", "2026-07-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.days[0].lowestPrice").value(420.00))
                .andExpect(jsonPath("$.data.days[0].available").value(true));
    }

    @Test
    void flightSearchShouldFilterDirectOnlyOnServer() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .param("departureCity", "PKX")
                        .param("arrivalCity", "PVG")
                        .param("departureDate", "2026-07-05")
                        .param("adultCount", "1")
                        .param("cabin", "ECONOMY")
                        .param("directOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/flights/search")
                        .param("departureCity", "PKX")
                        .param("arrivalCity", "PVG")
                        .param("departureDate", "2026-07-05")
                        .param("adultCount", "1")
                        .param("cabin", "ECONOMY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].name").value("DBG1010 北京大兴-上海浦东 经停济南"));
    }

    @Test
    void flightOrderPreviewShouldCalculatePayAmount() throws Exception {
        mockMvc.perform(post("/api/flights/order/preview")
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": "1",
                                  "cabin": "ECONOMY",
                                  "adultCount": 2,
                                  "insurance": true,
                                  "extraBaggage": true,
                                  "seatSelection": true,
                                  "pointsDeduct": 1000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.passengerCount").value(2))
                .andExpect(jsonPath("$.data.ticketAmount").value(1360.00))
                .andExpect(jsonPath("$.data.taxAmount").value(260.00))
                .andExpect(jsonPath("$.data.serviceAmount").value(260.00))
                .andExpect(jsonPath("$.data.pointsAmount").value(10.00))
                .andExpect(jsonPath("$.data.payAmount").value(1870.00));
    }

    @Test
    void flightOrderShouldPersistPassengerAndAddonDetail() throws Exception {
        String createResponse = mockMvc.perform(post("/api/flights/order")
                        .header("Authorization", userToken())
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": "1",
                                  "cabin": "BUSINESS",
                                  "adultCount": 1,
                                  "passengers": [
                                    {"name": "赵六", "idType": "ID_CARD", "idNo": "110101199303031234", "phone": "13800000003"}
                                  ],
                                  "insurance": true,
                                  "extraBaggage": true,
                                  "seatSelection": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderNo = createResponse.replaceAll("(?s).*\\\"order_no\\\":\\\"([^\\\"]+)\\\".*", "$1");
        String detailJson = jdbcTemplate.queryForObject(
                "select cast(fod.passenger_list as varchar) from flight_order_detail fod join orders o on o.id = fod.order_id where o.order_no = ?",
                String.class,
                orderNo
        );

        org.assertj.core.api.Assertions.assertThat(detailJson)
                .contains("\"passengerCount\":1")
                .contains("\"cabin\":\"BUSINESS\"")
                .contains("\"insurance\":true")
                .contains("\"extraBaggage\":true")
                .contains("\"seatSelection\":true")
                .contains("赵六");
    }

    @Test
    void flightOrderPreviewShouldRejectPassengerListCountMismatch() throws Exception {
        mockMvc.perform(post("/api/flights/order/preview")
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": "1",
                                  "cabin": "ECONOMY",
                                  "adultCount": 2,
                                  "childCount": 1,
                                  "passengers": [
                                    {"name": "张三", "idType": "ID_CARD", "idNo": "110101199001011234", "phone": "13800000000"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("passenger list size does not match adultCount and childCount"));
    }

    @Test
    void flightOrderShouldUseDeclaredPassengerCountForStockAndRestore() throws Exception {
        Integer initialStock = jdbcTemplate.queryForObject("select stock from product where id = 4", Integer.class);
        String createResponse = mockMvc.perform(post("/api/flights/order")
                        .header("Authorization", userToken())
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": "4",
                                  "cabin": "ECONOMY",
                                  "adultCount": 2,
                                  "childCount": 1,
                                  "passengers": [
                                    {"name": "张三", "idType": "ID_CARD", "idNo": "110101199001011234", "phone": "13800000000"},
                                    {"name": "李四", "idType": "ID_CARD", "idNo": "110101199202021234", "phone": "13800000001"},
                                    {"name": "王小明", "idType": "ID_CARD", "idNo": "110101201805051234", "phone": "13800000002"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pay_amount").value(1950.00))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Integer afterCreateStock = jdbcTemplate.queryForObject("select stock from product where id = 4", Integer.class);
        org.assertj.core.api.Assertions.assertThat(afterCreateStock).isEqualTo(initialStock - 3);

        String orderNo = createResponse.replaceAll("(?s).*\\\"order_no\\\":\\\"([^\\\"]+)\\\".*", "$1");
        mockMvc.perform(post("/api/orders/{orderNo}/cancel", orderNo)
                        .contentType("application/json")
                        .content("{\"reason\":\"调试取消\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        Integer afterCancelStock = jdbcTemplate.queryForObject("select stock from product where id = 4", Integer.class);
        org.assertj.core.api.Assertions.assertThat(afterCancelStock).isEqualTo(initialStock);
    }

    @Test
    void flightOrderShouldRejectMissingAuthorization() throws Exception {
        mockMvc.perform(post("/api/flights/order")
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": "4",
                                  "adultCount": 1,
                                  "cabin": "ECONOMY"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("unauthorized"));
    }

    @Test
    void flightOrderShouldCreatePendingOrderAndPayIt() throws Exception {
        String createResponse = mockMvc.perform(post("/api/flights/order")
                        .header("Authorization", userToken())
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": "4",
                                  "cabin": "ECONOMY",
                                  "passengers": [
                                    {"name": "张三", "idType": "ID_CARD", "idNo": "110101199001011234", "phone": "13800000000"}
                                  ],
                                  "insurance": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.order_no", notNullValue()))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.pay_amount").value(680.00))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderNo = createResponse.replaceAll("(?s).*\\\"order_no\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/orders/{orderNo}/pay", orderNo)
                        .contentType("application/json")
                        .content("{\"paymentMethod\":\"ALIPAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.ticketNo", notNullValue()))
                .andExpect(jsonPath("$.data.pointsAdded", greaterThan(0)));

        mockMvc.perform(post("/api/orders/{orderNo}/pay", orderNo)
                        .contentType("application/json")
                        .content("{\"paymentMethod\":\"ALIPAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.paymentMethod").value("ALIPAY"))
                .andExpect(jsonPath("$.data.pointsAdded").value(0))
                .andExpect(jsonPath("$.data.ticketNo", notNullValue()));

        mockMvc.perform(post("/api/payment/callback")
                        .contentType("application/json")
                        .content("{\"orderNo\":\"" + orderNo + "\",\"paymentMethod\":\"ALIPAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/orders/{orderNo}/status", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statusText").value("已出票"))
                .andExpect(jsonPath("$.data.paymentMethod").value("ALIPAY"));
    }

    @Test
    void flightOrderPaymentShouldAcceptPointsMethod() throws Exception {
        String createResponse = mockMvc.perform(post("/api/flights/order")
                        .header("Authorization", userToken())
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": "4",
                                  "adultCount": 1,
                                  "cabin": "ECONOMY"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderNo = createResponse.replaceAll("(?s).*\\\"order_no\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/orders/{orderNo}/pay", orderNo)
                        .contentType("application/json")
                        .content("{\"paymentMethod\":\"POINTS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.paymentMethod").value("POINTS"))
                .andExpect(jsonPath("$.data.ticketNo", notNullValue()));
    }

    @Test
    void flightOrderPaymentShouldRejectUnsupportedMethod() throws Exception {
        String createResponse = mockMvc.perform(post("/api/flights/order")
                        .header("Authorization", userToken())
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": "4",
                                  "adultCount": 1,
                                  "cabin": "ECONOMY"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderNo = createResponse.replaceAll("(?s).*\\\"order_no\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/orders/{orderNo}/pay", orderNo)
                        .contentType("application/json")
                        .content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("unsupported payment method"));
    }

    @Test
    void pendingOrderShouldBeCancelable() throws Exception {
        String createResponse = mockMvc.perform(post("/api/flights/order")
                        .header("Authorization", userToken())
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": "4",
                                  "adultCount": 1,
                                  "cabin": "ECONOMY"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderNo = createResponse.replaceAll("(?s).*\\\"order_no\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/orders/{orderNo}/cancel", orderNo)
                        .contentType("application/json")
                        .content("{\"reason\":\"行程变化\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/orders/{orderNo}/status", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.cancelReason").value("行程变化"));
    }

    @Test
    void ticketedOrderShouldCalculateRefund() throws Exception {
        String createResponse = mockMvc.perform(post("/api/flights/order")
                        .header("Authorization", userToken())
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": "1",
                                  "adultCount": 1,
                                  "cabin": "ECONOMY"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String orderNo = createResponse.replaceAll("(?s).*\\\"order_no\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/orders/{orderNo}/pay", orderNo)
                        .contentType("application/json")
                        .content("{\"paymentMethod\":\"WECHAT\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/{orderNo}/refund", orderNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(4))
                .andExpect(jsonPath("$.data.refundAmount").value(729.00))
                .andExpect(jsonPath("$.data.serviceFee").value(81.00));
    }

    @Test
    void aiFlightSearchShouldReuseFlightSearch() throws Exception {
        mockMvc.perform(post("/ai/search/flight")
                        .contentType("application/json")
                        .content("""
                                {
                                  "query": "帮我找 2026-06-20 从北京到上海的经济舱直飞机票"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("MU5678 北京-上海")));
    }

    @Test
    void aiRefundExplainShouldReturnRuleForOrder() throws Exception {
        String createResponse = mockMvc.perform(post("/api/flights/order")
                        .header("Authorization", userToken())
                        .contentType("application/json")
                        .content("""
                                {
                                  "productId": "1",
                                  "adultCount": 1,
                                  "cabin": "ECONOMY"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String orderNo = createResponse.replaceAll("(?s).*\\\"order_no\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/ai/explain/refund")
                        .contentType("application/json")
                        .content("{\"orderNo\":\"" + orderNo + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").value(org.hamcrest.Matchers.containsString("预计退款金额")));
    }
}
