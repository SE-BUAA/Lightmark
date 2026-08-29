package top.ortus.lightmark.backend;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthPermissionIntegrationTests extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userToken() {
        return bearerToken(2L, "普通用户", List.of("USER"));
    }

    @Test
    void registerLoginAndCurrentUserShouldWork() throws Exception {
        String email = "auth" + System.currentTimeMillis() + "@qq.com";
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        insert into auth_verification_code
                        (target, channel, code, expire_time, consumed_time, send_count, create_time, update_time)
                        values (?, 'EMAIL', ?, ?, null, 1, ?, ?)
                        """,
                email,
                "123456",
                Timestamp.valueOf(now.plusMinutes(5)),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );

        MockHttpSession registerSession = new MockHttpSession();
        registerSession.setAttribute("auth:captcha", "ABCD");

        mockMvc.perform(post("/api/auth/register")
                        .session(registerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password!1","nickname":"用户%s","verificationCode":"123456","captchaCode":"ABCD","privacyAccepted":true}
                                """.formatted(email, System.currentTimeMillis())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value(email));

        MockHttpSession loginSession = new MockHttpSession();
        loginSession.setAttribute("auth:captcha", "QWER");

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .session(loginSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"%s","password":"Password!1","captchaCode":"QWER","privacyAccepted":true}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.identity").value("USER"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(loginResponse).path("data").path("token").asText();
        mockMvc.perform(get("/api/user/current")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.identity").value("USER"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"));
    }

    @Test
    void loginShouldUpdateLastLoginTimeAndIp() throws Exception {
        String email = "login" + System.currentTimeMillis() + "@qq.com";
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                        insert into auth_verification_code
                        (target, channel, code, expire_time, consumed_time, send_count, create_time, update_time)
                        values (?, 'EMAIL', ?, ?, null, 1, ?, ?)
                        """,
                email,
                "654321",
                Timestamp.valueOf(now.plusMinutes(5)),
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        );

        MockHttpSession registerSession = new MockHttpSession();
        registerSession.setAttribute("auth:captcha", "ZXCV");
        mockMvc.perform(post("/api/auth/register")
                        .session(registerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password!1","nickname":"回写%s","verificationCode":"654321","captchaCode":"ZXCV","privacyAccepted":true}
                                """.formatted(email, System.currentTimeMillis())))
                .andExpect(status().isOk());

        MockHttpSession loginSession = new MockHttpSession();
        loginSession.setAttribute("auth:captcha", "TYUI");
        mockMvc.perform(post("/api/auth/login")
                        .session(loginSession)
                        .header("X-Forwarded-For", "203.0.113.9, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"%s","password":"Password!1","captchaCode":"TYUI","privacyAccepted":true}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Timestamp lastLoginTime = jdbcTemplate.queryForObject(
                "select last_login_time from `user` where email = ?",
                Timestamp.class,
                email
        );
        String lastLoginIp = jdbcTemplate.queryForObject(
                "select last_login_ip from `user` where email = ?",
                String.class,
                email
        );
        assertThat(lastLoginTime).isNotNull();
        assertThat(lastLoginIp).isEqualTo("203.0.113.9");
    }

    @Test
    void adminEndpointsShouldRejectMissingOrNonAdminTokens() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("unauthorized"));

        mockMvc.perform(get("/api/admin/dashboard/summary")
                        .header("Authorization", userToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("forbidden"));
    }
}
