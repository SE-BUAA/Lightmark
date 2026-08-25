package top.ortus.lightmark.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import top.ortus.lightmark.backend.common.PageResult;
import top.ortus.lightmark.backend.dto.AIRecommendResultVO;
import top.ortus.lightmark.backend.dto.AIHotelSearchIntent;
import top.ortus.lightmark.backend.dto.ReviewSummaryVO;
import top.ortus.lightmark.backend.dto.module.TravelPlanDTO;
import top.ortus.lightmark.backend.service.HotelService;
import top.ortus.lightmark.backend.utils.AIClient;
import top.ortus.lightmark.backend.vo.HotelVO;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AIServiceImplTest {

    private final HotelService hotelService = mock(HotelService.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AIClient aiClient = mock(AIClient.class);
    private final AIServiceImpl aiService = new AIServiceImpl(
        hotelService,
        jdbcTemplate,
        new ObjectMapper(),
        aiClient,
        resource("用户需求：%s。返回 JSON。"),
        resource("评论内容：%s。返回 JSON。")
    );

    @Test
    void parseHotelIntentFromModelJson() {
        String response = """
                {
                  "destination": "上海迪士尼",
                  "maxPrice": 500,
                  "roomNum": 1,
                  "adultNum": 2,
                  "facilities": ["安静", "亲子"],
                  "starLevel": 4,
                  "recommendText": "为您推荐安静亲子的酒店"
                }
                """;

        AIHotelSearchIntent intent = aiService.parseHotelIntent(response);

        assertThat(intent.getDestination()).isEqualTo("上海迪士尼");
        assertThat(intent.getMaxPrice()).isEqualByComparingTo("500");
        assertThat(intent.getAdultNum()).isEqualTo(2);
        assertThat(intent.getFacilities()).containsExactly("安静", "亲子");
    }

    @Test
    void parseHotelIntentFromMarkdownJsonAndStringPrice() {
        String response = """
                ```json
                {
                  "目的地": "成都",
                  "价格上限": "500元以内",
                  "房间数": "2间",
                  "入住人数": "3人",
                  "偏好设施": "地铁",
                  "星级要求": "4星"
                }
                ```
                """;

        AIHotelSearchIntent intent = aiService.parseHotelIntent(response);

        assertThat(intent.getDestination()).isEqualTo("成都");
        assertThat(intent.getMaxPrice()).isEqualByComparingTo("500");
        assertThat(intent.getRoomNum()).isEqualTo(2);
        assertThat(intent.getAdultNum()).isEqualTo(3);
        assertThat(intent.getFacilities()).containsExactly("地铁");
        assertThat(intent.getStarLevel()).isEqualTo(4);
    }

    @Test
    void parseHotelIntentShouldReturnEmptyIntentForBlankJson() {
        AIHotelSearchIntent intent = aiService.parseHotelIntent("");

        assertThat(intent.getDestination()).isNull();
        assertThat(intent.getFacilities()).isEmpty();
    }

    @Test
    void parseHotelIntentShouldRejectInvalidJson() {
        assertThatThrownBy(() -> aiService.parseHotelIntent("not-json"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("invalid_ai_hotel_json");
    }

    @Test
    void parseReviewSummaryFromModelJson() {
        String response = """
                {
                  "pros": ["位置好", "干净"],
                  "cons": ["噪音大"],
                  "overall": "总体推荐率80%"
                }
                """;

        ReviewSummaryVO summary = aiService.parseReviewSummary(response);

        assertThat(summary.getPros()).containsExactly("位置好", "干净");
        assertThat(summary.getCons()).containsExactly("噪音大");
        assertThat(summary.getOverall()).isEqualTo("总体推荐率80%");
    }

    @Test
    void parseReviewSummaryShouldHandleEmptyArraysAndWrappedContent() {
        String response = """
                {
                  "choices": [{
                    "message": {
                      "content": "```json\\n{\\\"pros\\\":[],\\\"cons\\\":[],\\\"overall\\\":\\\"暂无明显倾向\\\"}\\n```"
                    }
                  }]
                }
                """;

        ReviewSummaryVO summary = aiService.parseReviewSummary(response);

        assertThat(summary.getPros()).isEmpty();
        assertThat(summary.getCons()).isEmpty();
        assertThat(summary.getOverall()).isEqualTo("暂无明显倾向");
    }

    @Test
    void parseReviewSummaryShouldRejectInvalidJson() {
        assertThatThrownBy(() -> aiService.parseReviewSummary("not-json"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("invalid_ai_review_json");
    }

    @Test
    void recommendHotelShouldFallbackWhenAiClientThrows() {
        when(aiClient.chat(org.mockito.ArgumentMatchers.anyString())).thenThrow(new RuntimeException("ai down"));
        when(hotelService.searchHotels(org.mockito.ArgumentMatchers.eq(0L), org.mockito.ArgumentMatchers.any()))
            .thenReturn(PageResult.<HotelVO>builder()
                .total(1L)
                .records(List.of(HotelVO.builder()
                    .id("1")
                    .name("上海亲子酒店")
                    .priceMin(new BigDecimal("450"))
                    .rating(4.8)
                    .facilities(List.of("亲子", "地铁"))
                    .build()))
                .build());

        AIRecommendResultVO result = aiService.recommendHotel("上海 500元以内 亲子");

        assertThat(result.getRecommendText()).contains("AI 服务繁忙");
        assertThat(result.getHotels()).extracting(HotelVO::getName).containsExactly("上海亲子酒店");
    }

    @Test
    void generateReviewSummaryShouldFallbackWhenAiClientUnavailable() {
        when(aiClient.chat(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(hotelService.getHotel(1L)).thenReturn(HotelVO.builder().name("测试酒店").address("地铁旁").build());

        ReviewSummaryVO summary = aiService.generateReviewSummary(1L);

        assertThat(summary.getPros()).isNotEmpty();
        assertThat(summary.getOverall()).contains("AI 服务繁忙");
    }

    @Test
    void generateTravelPlanShouldFallbackWhenAiClientThrows() {
        when(aiClient.chat(org.mockito.ArgumentMatchers.anyString())).thenThrow(new RuntimeException("ai down"));

        TravelPlanDTO plan = aiService.generateTravelPlan(Map.of(
            "destination", "杭州",
            "days", 2,
            "startDate", "2026-07-01",
            "preferences", "美食"
        ));

        assertThat(plan.getTitle()).isEqualTo("杭州2日智能行程");
        assertThat(plan.getDestination()).isEqualTo("杭州");
        assertThat(plan.getStart_date()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(plan.getEnd_date()).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(plan.getPlan_data()).contains("西湖", "美食");
    }

    private static ByteArrayResource resource(String text) {
        return new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8));
    }
}
