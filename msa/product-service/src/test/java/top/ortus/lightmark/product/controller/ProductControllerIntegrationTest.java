package top.ortus.lightmark.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import top.ortus.lightmark.common.PageResponse;
import top.ortus.lightmark.common.security.JwtTokenService;
import top.ortus.lightmark.common.exception.GlobalExceptionHandler;
import top.ortus.lightmark.product.dto.HotelDTO;
import top.ortus.lightmark.product.dto.ProductDTO;
import top.ortus.lightmark.product.dto.RoomDTO;
import top.ortus.lightmark.product.dto.TrainTicketDTO;
import top.ortus.lightmark.product.dto.VacationDTO;
import top.ortus.lightmark.product.service.FlightProductService;
import top.ortus.lightmark.product.service.HotelProductService;
import top.ortus.lightmark.product.service.TrainProductService;
import top.ortus.lightmark.product.service.VacationProductService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import({GlobalExceptionHandler.class, top.ortus.lightmark.product.config.JwtConfig.class})
@org.springframework.test.context.TestPropertySource(properties = "lightmark.auth.jwt.secret=test-secret-key-at-least-32-bytes-long")
class ProductControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenService jwt;
    private String auth;
    @MockBean FlightProductService flights;
    @MockBean HotelProductService hotels;
    @MockBean VacationProductService vacations;
    @MockBean TrainProductService trains;

    @BeforeEach
    void setUp() { auth = "Bearer " + jwt.createToken(1L, "integration-test", java.util.List.of("USER")); }

    @Test
    void flightSearchAndDetailExposeCommonResponse() throws Exception {
        ProductDTO product = product("1", "CA100", "300.00");
        when(flights.search(anyMap())).thenReturn(new PageResponse<>(1, 1, 10, List.of(product)));
        when(flights.detail("1")).thenReturn(product);

        mvc.perform(get("/api/flights/search").param("departureCity", "BJS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.list", hasSize(1)))
                .andExpect(jsonPath("$.data.list[0].id").value("1"));
        mvc.perform(get("/api/flights/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("CA100"));
        verify(flights).search(argThat(p -> "BJS".equals(p.get("departureCity"))));
    }

    @Test
    void genericAndAdminProductEndpointsDelegate() throws Exception {
        ProductDTO product = product("2", "Hotel", "500.00");
        when(flights.products(anyMap())).thenReturn(new PageResponse<>(1, List.of(product)));
        when(flights.product("2")).thenReturn(product);
        when(flights.adminProducts(anyMap())).thenReturn(new PageResponse<>(0, List.of()));
        when(flights.createProduct(anyMap())).thenReturn(Map.of("id", 2));
        when(flights.updateProduct(2L, "price", 399)).thenReturn(true);
        when(flights.deleteProduct(2L)).thenReturn(true);

        mvc.perform(get("/api/products").param("productType", "HOTEL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.list[0].product_type").value("HOTEL"));
        mvc.perform(get("/api/products/2")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/products")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "Hotel", "price", 500))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(2));
        mvc.perform(put("/api/admin/products/2/price").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":399}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        mvc.perform(delete("/api/admin/products/2")).andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        verify(flights).updateProduct(2L, "price", 399);
    }

    @Test
    void hotelRoomAndVacationEndpointsSerializeDomainRecords() throws Exception {
        when(hotels.search(anyMap())).thenReturn(new PageResponse<>(1, List.of(new HotelDTO("7", "上海酒店", "黄浦区", 5, 4.5, new BigDecimal("300"), null, null, null, null, List.of("WiFi"), "REFUNDABLE"))));
        when(hotels.rooms(eq(7L), eq("2026-09-01"), eq("2026-09-03"))).thenReturn(List.of(new RoomDTO(8L, 7L, "大床房", "大床", "30㎡", 1, "REFUNDABLE", new BigDecimal("300"), new BigDecimal("600"), "2026-09-01", "2026-09-03", 2)));
        when(vacations.search(anyMap())).thenReturn(List.of(new VacationDTO("9", "三亚之旅", new BigDecimal("1000"), 2, 0, Map.of(), List.of("海岛"))));

        mvc.perform(get("/api/hotel/list")).andExpect(status().isOk()).andExpect(jsonPath("$.data.list[0].id").value("7"));
        mvc.perform(get("/api/hotel/7/rooms").param("checkIn", "2026-09-01").param("checkOut", "2026-09-03"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].roomId").value(8));
        mvc.perform(get("/api/vacations").param("destination", "三亚"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("三亚之旅"));
        verify(hotels).rooms(7L, "2026-09-01", "2026-09-03");
    }

    @Test
    void trainEndpointsAcceptJsonAndExposeOptions() throws Exception {
        TrainTicketDTO ticket = new TrainTicketDTO("T1", "G1", 150d, 5, 0, List.of(), Map.of(), Map.of(), Map.of());
        when(trains.search(anyMap(), eq(false))).thenReturn(List.of(ticket));
        when(trains.options()).thenReturn(Map.of("stations", List.of("北京南")));
        when(trains.calendar(anyMap())).thenReturn(List.of(Map.of("date", "2026-09-01", "ticketCount", 1)));

        mvc.perform(post("/api/trains/search").contentType(MediaType.APPLICATION_JSON).content("{\"startStation\":\"北京南\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value("T1"));
        mvc.perform(get("/api/trains/options")).andExpect(status().isOk()).andExpect(jsonPath("$.data.stations[0]").value("北京南"));
        mvc.perform(post("/api/trains/calendar").contentType(MediaType.APPLICATION_JSON).content("{\"month\":\"2026-09\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)));
        verify(trains).search(argThat(p -> "北京南".equals(p.get("startStation"))), eq(false));
    }

    @Test
    void browsingAndStockInternalEndpointsUseExpectedPayloads() throws Exception {
        when(flights.adjustInventory(42L, 2, true)).thenReturn(true);
        when(flights.adjustInventory(42L, 3, false)).thenReturn(true);
        when(flights.views(eq(42L), isNull(), anyMap())).thenReturn(new PageResponse<>(0, List.of()));

        mvc.perform(post("/api/internal/product/42/stock").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"delta\":-2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        mvc.perform(post("/api/internal/product/42/stock").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"delta\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        mvc.perform(post("/api/products/42/views").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":5,\"source\":\"WEB\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/products/42/views")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        verify(flights).adjustInventory(42L, 2, true);
        verify(flights).adjustInventory(42L, 3, false);
        verify(flights).recordView(42L, 5L, "WEB");
    }

    @Test
    void invalidInternalStockPayloadReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/internal/product/not-a-number/stock").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{\"delta\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(flights);
    }

    private ProductDTO product(String id, String name, String price) {
        ProductDTO p = new ProductDTO(); p.setId(id); p.setProduct_type("HOTEL"); p.setName(name);
        p.setPrice(new BigDecimal(price)); p.setStock(5); p.setStatus(1); return p;
    }
}
