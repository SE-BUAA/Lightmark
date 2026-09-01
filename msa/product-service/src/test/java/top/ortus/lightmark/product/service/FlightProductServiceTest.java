package top.ortus.lightmark.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import top.ortus.lightmark.product.dto.ProductDTO;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FlightProductServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final FlightProductService service = new FlightProductService(jdbc, new ObjectMapper());

    private Map<String, Object> row(long id, String date, String from, String to, String price) {
        Map<String, Object> r = new LinkedHashMap<>(); r.put("id", id); r.put("product_type", "FLIGHT");
        r.put("name", "Test flight"); r.put("price", new BigDecimal(price)); r.put("stock", 5); r.put("sold_count", 0); r.put("status", 1);
        r.put("extra", "{\"departureDate\":\"" + date + "\",\"departureCity\":\"" + from + "\",\"arrivalCity\":\"" + to + "\"}"); return r;
    }

    @Test void searchFiltersAndPaginatesFlights() {
        when(jdbc.queryForList(anyString(), eq("FLIGHT"))).thenReturn(List.of(row(1,"2026-09-01","BJS","SHA","200"), row(2,"2026-09-01","BJS","SHA","100")));
        var result = service.search(Map.of("departureCity", "BJS", "page", "1", "size", "1"));
        assertThat(result.getTotal()).isEqualTo(2); assertThat(result.getList()).hasSize(1); assertThat(result.getList().get(0).getPrice()).isEqualByComparingTo("100");
    }

    @Test void priceCalendarReturnsAvailability() {
        when(jdbc.queryForList(anyString(), eq("FLIGHT"))).thenReturn(List.of(row(1,"2026-09-01","BJS","SHA","200")));
        var result = service.priceCalendar(Map.of("startDate", "2026-09-01", "days", "2"));
        assertThat((List<?>) result.get("days")).hasSize(2); assertThat(result.toString()).contains("available=true");
    }

    @Test void deductsStockAtomically() {
        when(jdbc.update(anyString(), eq(2), eq(2), eq(1L), eq(2))).thenReturn(1);
        assertThat(service.adjustStock(1, 2, true)).isTrue(); verify(jdbc).update(contains("stock >="), eq(2), eq(2), eq(1L), eq(2));
    }
}
