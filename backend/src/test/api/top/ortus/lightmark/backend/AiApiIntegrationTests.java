package top.ortus.lightmark.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiApiIntegrationTests extends BaseIntegrationTest {

    private String userToken() {
        return bearerToken(2L, "普通用户", List.of("USER"));
    }

    @Test
    void aiHotelRecommendShouldReturnFallbackRecommendationAndHotels() throws Exception {
        mockMvc.perform(post("/api/ai/hotel/recommend")
                        .header("Authorization", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userInput": "推荐上海外滩附近带早餐的高星酒店"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.recommendText", not(blankOrNullString())))
                .andExpect(jsonPath("$.data.hotels.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.hotels[0].name").value("上海外滩酒店"))
                .andExpect(jsonPath("$.data.hotels[0].facilities.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    void aiHotelReviewSummaryShouldSupportStoredAndFallbackReviews() throws Exception {
        mockMvc.perform(get("/api/ai/hotel/review-summary/2")
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pros.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.overall", not(blankOrNullString())));

        mockMvc.perform(get("/api/ai/hotel/review-summary/999")
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.pros.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.overall", not(blankOrNullString())));
    }

    @Test
    void aiHotelEndpointsShouldRequireLogin() throws Exception {
        mockMvc.perform(post("/api/ai/hotel/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userInput": "推荐上海酒店"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("login required"));

        mockMvc.perform(get("/api/ai/hotel/review-summary/2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("login required"));
    }
}
