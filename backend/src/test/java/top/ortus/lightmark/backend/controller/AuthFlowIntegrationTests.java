package top.ortus.lightmark.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import top.ortus.lightmark.backend.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowIntegrationTests extends BaseIntegrationTest {

    @Test
    void loginShouldRequireCaptcha() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account":"普通用户",
                                  "password":"Password!1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void registerWithWeakPasswordShouldFail() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("auth:captcha", "ABCD");

        mockMvc.perform(post("/api/auth/register")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"fresh@qq.com",
                                  "password":"weakpass",
                                  "nickname":"新用户A",
                                  "verificationCode":"123456",
                                  "captchaCode":"ABCD"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void registerWithUnsupportedEmailDomainShouldFail() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("auth:captcha", "ABCD");

        mockMvc.perform(post("/api/auth/register")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"fresh@example.org",
                                  "password":"Password!1",
                                  "nickname":"新用户B",
                                  "verificationCode":"123456",
                                  "captchaCode":"ABCD"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void emailVerificationSendShouldRequireConfiguredQqSmtp() throws Exception {
        MockHttpSession sendCodeSession = new MockHttpSession();
        sendCodeSession.setAttribute("auth:captcha", "QWER");

        mockMvc.perform(post("/api/auth/verification/email/send")
                        .session(sendCodeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"newuser@qq.com","captchaCode":"QWER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("qq smtp username is not configured"));
    }

    @Test
    void captchaEndpointShouldReturnImage() throws Exception {
        mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk());
    }

    @Test
    void adminLoginShouldWorkWithoutCaptchaForAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account":"admin@lightmark.com",
                                  "password":"password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.identity").value("ADMIN"));
    }

    @Test
    void adminLoginShouldRejectNormalUser() throws Exception {
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account":"user@lightmark.com",
                                  "password":"password"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
