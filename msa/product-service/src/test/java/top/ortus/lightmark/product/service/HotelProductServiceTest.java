package top.ortus.lightmark.product.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test; import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal; import java.util.*;
import static org.assertj.core.api.Assertions.assertThat; import static org.mockito.ArgumentMatchers.*; import static org.mockito.Mockito.*;
class HotelProductServiceTest {
 @Test void mapsHotelSearchAndRooms(){ JdbcTemplate j=mock(JdbcTemplate.class); when(j.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("id",2L,"name","上海酒店","price_min",new BigDecimal("300"),"cancel_policy","REFUNDABLE","extra","{\"address\":\"黄浦区\",\"starLevel\":5}"))); HotelProductService s=new HotelProductService(j,new ObjectMapper()); assertThat(s.search(Map.of()).getList().get(0).name()).isEqualTo("上海酒店"); }
}
