package top.ortus.lightmark.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TrainProductServiceTest {

    @Test
    void returnsOptionsAndRejectsMissingStations() {
        var s = new TrainProductService(RestClient.builder(), new ObjectMapper(), "http://127.0.0.1:1");
        Map<String, Object> options = s.options();
        // 前端 TrainsView 依赖 startStations/endStations/dates（与单体 /api/trains/options 对齐）
        assertThat(options).containsKeys("startStations", "endStations", "dates");
        assertThat((List<?>) options.get("startStations")).isNotEmpty();
        assertThat((List<?>) options.get("endStations")).isNotEmpty();
        assertThat((List<?>) options.get("dates")).hasSize(180);
        assertThat(s.search(Map.of(), false)).isEmpty();
    }

    @Test
    void mapsMcpSeatDataToAvailableTickets() {
        var s = new TrainProductService(RestClient.builder(), new ObjectMapper(), "http://127.0.0.1:1");
        Map<String, Object> row = Map.of(
                "train_no", "G1",
                "from_station", "北京南",
                "to_station", "上海虹桥",
                "seats", Map.of(
                        "business", "无",
                        "first_class", "7",
                        "second_class", "有",
                        "no_seat", "候补"));

        List<?> tickets = s.tickets(Map.of("trains", List.of(row)));

        assertThat(tickets).hasSize(1);
        var ticket = (top.ortus.lightmark.product.dto.TrainTicketDTO) tickets.get(0);
        assertThat(ticket.seats()).containsEntry("一等座", 7).containsEntry("二等座", 20);
        assertThat(ticket.seats()).doesNotContainKeys("商务座", "无座");
        assertThat(ticket.stock()).isEqualTo(27);
    }
}
