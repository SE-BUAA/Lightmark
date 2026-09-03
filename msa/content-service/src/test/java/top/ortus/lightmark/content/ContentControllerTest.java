package top.ortus.lightmark.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import top.ortus.lightmark.common.ApiResponse;
import top.ortus.lightmark.common.security.JwtTokenService;
import top.ortus.lightmark.content.client.UserProfileClient;
import top.ortus.lightmark.content.controller.ContentController;
import top.ortus.lightmark.content.service.ContentAiService;
import top.ortus.lightmark.common.exception.ApiException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 内容服务控制器单元测试：使用 Mockito 隔离数据库、用户服务和外部 AI 调用。
 */
@ExtendWith(MockitoExtension.class)
class ContentControllerTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private JwtTokenService jwt;

    @Mock
    private UserProfileClient userProfileClient;

    @Mock
    private ContentAiService aiService;

    private ContentController controller;

    @BeforeEach
    void setUp() {
        controller = new ContentController(jdbc, new ObjectMapper(), jwt, userProfileClient, aiService);
    }

    @Test
    void healthShouldReportContentServiceUp() {
        ApiResponse<Map<String, String>> response = new top.ortus.lightmark.content.controller.HealthController().health();

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).containsEntry("service", "content-service")
                .containsEntry("status", "UP");
    }

    @Test
    void chatShouldRejectMissingAuthorization() {
        assertThatThrownBy(() -> controller.chat(null, Map.of("message", "你好")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("unauthorized");
    }

    @Test
    void chatShouldRejectBlankMessage() {
        when(jwt.resolveUserId("valid-token")).thenReturn(42L);

        assertThatThrownBy(() -> controller.chat("Bearer valid-token", Map.of("message", "  ")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("消息不能为空");
    }

    @Test
    void chatShouldReturnAiContentAndSessionContext() {
        when(jwt.resolveUserId("valid-token")).thenReturn(42L);
        when(aiService.chat(anyString(), anyString()))
                .thenReturn(Map.of("content", "建议安排两天行程"));

        ApiResponse<Map<String, Object>> response = controller.chat(
                "Bearer valid-token",
                Map.of("message", "帮我规划行程", "sessionId", "session-1"));

        assertThat(response.getCode()).isZero();
        assertThat(response.getData())
                .containsEntry("content", "建议安排两天行程")
                .containsEntry("sessionId", "session-1")
                .containsEntry("role", "assistant");

        ApiResponse<Map<String, Object>> context = controller.context("Bearer valid-token", "session-1");
        assertThat(context.getData().get("messages")).isInstanceOf(List.class);
        assertThat((List<?>) context.getData().get("messages")).hasSize(2);
    }

    @Test
    void hotelRecommendShouldReturnEmptyFallbackList() {
        when(jwt.resolveUserId("valid-token")).thenReturn(42L);

        ApiResponse<Map<String, Object>> response = controller.hotelRecommend(
                "Bearer valid-token", Map.of("city", "北京"));

        assertThat(response.getCode()).isZero();
        assertThat(response.getData().get("recommendations")).isEqualTo(List.of());
        assertThat(response.getData()).containsEntry("message", "暂无可用推荐");
    }
}
