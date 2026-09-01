package top.ortus.lightmark.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import top.ortus.lightmark.common.security.JwtTokenService;
import top.ortus.lightmark.common.security.UserIdentity;
import top.ortus.lightmark.user.config.LightmarkAuthProperties;
import top.ortus.lightmark.user.service.ObjectStorageService;
import top.ortus.lightmark.user.service.QqSmtpEmailService;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/db/test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UserServiceApiIntegrationTest {

    private static final String USER_PASSWORD = "Password!1";
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private LightmarkAuthProperties authProperties;

    @MockitoBean
    private QqSmtpEmailService qqSmtpEmailService;

    @MockitoBean
    private ObjectStorageService objectStorageService;

    @BeforeEach
    void seedUsers() {
        jdbcTemplate.update("delete from user_role");
        jdbcTemplate.update("delete from traveler");
        jdbcTemplate.update("delete from points_log");
        jdbcTemplate.update("delete from user_login_log");
        jdbcTemplate.update("delete from auth_verification_code");
        jdbcTemplate.update("delete from admin_log");
        jdbcTemplate.update("delete from `user`");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        insertUser(42L, "user@qq.com", "普通用户", encoder.encode(USER_PASSWORD), 20, 0);
        insertUser(7L, "admin@qq.com", "管理员", encoder.encode(USER_PASSWORD), 200, 2);
        jdbcTemplate.update("insert into user_role (user_id, role_id) values (42, 2), (7, 1)");
    }

    @Test
    void exposesHealthReadinessAndVersion() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.status", is("UP")));
        mockMvc.perform(get("/api/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.database", is("UP")));
        mockMvc.perform(get("/api/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.service", is("user-service")))
                .andExpect(jsonPath("$.data.version", is("test")));
    }

    @Test
    void exposesCaptchaEmailVerificationAndLogoutApis() throws Exception {
        MockHttpSession captchaSession = new MockHttpSession();
        mockMvc.perform(get("/api/auth/captcha").session(captchaSession))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(result -> assertTrue(result.getResponse().getContentAsByteArray().length > 0));

        MockHttpSession emailSession = sessionWithCaptcha("ABCD");
        mockMvc.perform(post("/api/auth/verification/email/send")
                        .session(emailSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"notify@qq.com\",\"captchaCode\":\"ABCD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", is(true)));
        org.mockito.Mockito.verify(qqSmtpEmailService).sendVerificationCode(org.mockito.ArgumentMatchers.eq("notify@qq.com"),
                org.mockito.ArgumentMatchers.anyString());

        mockMvc.perform(post("/api/auth/verification/email/send")
                        .session(sessionWithCaptcha("ABCD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"notify@qq.com\",\"captchaCode\":\"WRONG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.msg", is("captcha invalid")));

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", is(true)));
    }

    @Test
    void rejectsProtectedRequestWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/user/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is(401)))
                .andExpect(jsonPath("$.msg", is("unauthorized")));
    }

    @Test
    void readsAndUpdatesCurrentUserAndAvatar() throws Exception {
        String token = token(42L, UserIdentity.USER);
        mockMvc.perform(get("/api/user/current").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is("0000000000000042")))
                .andExpect(jsonPath("$.data.identity", is("USER")))
                .andExpect(jsonPath("$.data.email", is("user@qq.com")));

        mockMvc.perform(put("/api/user/current")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"新昵称\",\"gender\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname", is("新昵称")))
                .andExpect(jsonPath("$.data.gender", is(1)));

        mockMvc.perform(post("/api/user/avatar")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":\"https://img.example/avatar.jpg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl", is("https://img.example/avatar.jpg")));

        mockMvc.perform(post("/api/user/42/profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"路径更新\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname", is("路径更新")));

        org.mockito.Mockito.when(objectStorageService.uploadAvatar(org.mockito.ArgumentMatchers.eq("42"),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn("https://img.example/avatar-42.jpg");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3});
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/user/avatar/upload")
                        .file(file).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl", is("https://img.example/avatar-42.jpg")));
    }

    @Test
    void completesTravelerPointsAndPasswordFlows() throws Exception {
        String token = token(42L, UserIdentity.USER);
        String auth = bearer(token);
        mockMvc.perform(post("/api/user/travelers")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\",\"id_card\":\"110101199001010011\",\"phone\":\"13800138000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Alice")));
        mockMvc.perform(get("/api/user/travelers").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
        mockMvc.perform(get("/api/user/42/travelers").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)));
        mockMvc.perform(put("/api/user/travelers/1").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice Updated\",\"id_card\":\"110101199001010011\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name", is("Alice Updated")));
        mockMvc.perform(put("/api/user/42/travelers/1").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice Again\",\"id_card\":\"110101199001010011\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name", is("Alice Again")));

        mockMvc.perform(post("/internal/user/42/points")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":30,\"type\":1,\"source\":\"ORDER\",\"orderId\":\"1001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", is(true)));
        mockMvc.perform(get("/api/user/points/logs").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(1)))
                .andExpect(jsonPath("$.data.list[0].source", is("ORDER")));
        mockMvc.perform(get("/api/user/42/points").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.points", is(50)));
        mockMvc.perform(get("/api/user/level/upgrade-info").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pointsNeeded", is(50)));

        mockMvc.perform(put("/api/user/password")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrong\",\"newPassword\":\"NewPassword!1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg", containsString("old password")));
        mockMvc.perform(put("/api/user/password")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"Password!1\",\"newPassword\":\"NewPassword!1\"}"))
                .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data", is(true)));
        mockMvc.perform(post("/api/user/42/password").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"NewPassword!1\",\"newPassword\":\"Password!1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", is(true)));
        mockMvc.perform(delete("/api/user/42/travelers/1").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", is(true)));
        mockMvc.perform(delete("/api/user/travelers/1").header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void registersAndLogsInWithSessionCaptchaAndEmailCode() throws Exception {
        String email = "new" + System.nanoTime() + "@qq.com";
        insertVerificationCode(email, "EMAIL", "123456");
        MockHttpSession registerSession = sessionWithCaptcha("ABCD");
        mockMvc.perform(post("/api/auth/register")
                        .session(registerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"nickname\":\"new-user\",\"password\":\"Password!1\",\"verificationCode\":\"123456\",\"captchaCode\":\"ABCD\",\"privacyAccepted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(0)))
                .andExpect(jsonPath("$.data.email", is(email)))
                .andExpect(jsonPath("$.data.id", notNullValue()));

        MockHttpSession loginSession = sessionWithCaptcha("WXYZ");
        mockMvc.perform(post("/api/auth/login")
                        .session(loginSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"" + email + "\",\"password\":\"Password!1\",\"captchaCode\":\"WXYZ\",\"privacyAccepted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andExpect(jsonPath("$.data.identity", is("USER")));

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"admin@qq.com\",\"password\":\"Password!1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identity", is("ADMIN")));
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"admin@qq.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg", is("invalid credentials")));
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"user@qq.com\",\"password\":\"Password!1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
    }

    @Test
    void enforcesAdminRoleAndExposesInternalReadApis() throws Exception {
        String userAuth = bearer(token(42L, UserIdentity.USER));
        String adminAuth = bearer(token(7L, UserIdentity.ADMIN));
        mockMvc.perform(get("/api/admin/users").header("Authorization", userAuth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is(403)));
        mockMvc.perform(get("/api/admin/users").header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
        mockMvc.perform(put("/api/admin/users/42/status")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is(1)));
        mockMvc.perform(get("/api/admin/logs").header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].operation", is("update_user_status")));
        mockMvc.perform(get("/internal/user/42").header("Authorization", userAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname", is("普通用户")))
                .andExpect(content().string(containsString("level")));
        mockMvc.perform(get("/internal/user/42/travelers").header("Authorization", userAuth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(put("/api/admin/users/42/level")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.level", is(3)));
    }

    private String token(long id, UserIdentity identity) {
        return jwtTokenService.createToken(id, identity == UserIdentity.ADMIN ? "管理员" : "普通用户", identity);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private MockHttpSession sessionWithCaptcha(String captcha) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(authProperties.getCaptcha().getSessionKey(), captcha);
        return session;
    }

    private void insertVerificationCode(String target, String channel, String code) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("insert into auth_verification_code (target, channel, code, expire_time, consumed_time, send_count, create_time, update_time) values (?, ?, ?, ?, null, 1, ?, ?)",
                target, channel, code, now.plusMinutes(10), now, now);
    }

    private void insertUser(long id, String email, String nickname, String password, int points, int level) {
        jdbcTemplate.update("insert into `user` (id, email, password, nickname, avatar, points, level, status, register_source, create_time, update_time, deleted, gender) values (?, ?, ?, ?, '', ?, ?, 0, 'EMAIL', ?, ?, false, 0)",
                id, email, password, nickname, points, level, LocalDateTime.now(), LocalDateTime.now());
    }
}
