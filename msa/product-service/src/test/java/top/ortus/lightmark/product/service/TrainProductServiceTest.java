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
}
