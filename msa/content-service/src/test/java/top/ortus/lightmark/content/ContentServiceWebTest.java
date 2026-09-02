package top.ortus.lightmark.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import top.ortus.lightmark.common.security.JwtTokenService;
import top.ortus.lightmark.content.client.UserProfileClient;
import top.ortus.lightmark.content.service.ContentAiService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 内容服务最小 Web 层门禁，验证服务可以被独立加载并返回统一响应格式。 */
@WebMvcTest
class ContentServiceWebTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;
    @MockBean
    private JwtTokenService jwtTokenService;
    @MockBean
    private UserProfileClient userProfileClient;
    @MockBean
    private ContentAiService contentAiService;

    @Test
    void healthEndpointShouldReportContentServiceUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.service").value("content-service"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
