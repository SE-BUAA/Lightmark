package top.ortus.lightmark.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class M6ItineraryCommunityIntegrationTests extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String userToken() {
        return bearerToken(2L, "普通用户", List.of("USER"));
    }

    private String otherUserToken() {
        return bearerToken(99L, "其他用户", List.of("USER"));
    }

    private String secondUserToken() {
        ensureSecondUser();
        return bearerToken(3L, "second-user", List.of("USER"));
    }

    private String adminToken() {
        return bearerToken(1L, "管理员", List.of("ADMIN"));
    }

    private void ensureSecondUser() {
        jdbcTemplate.update("""
                merge into `user` (id, phone, email, password, nickname, points, level, status, deleted)
                key(id) values (3, '13900000003', 'second@lightmark.com', 'test-password', 'second-user', 0, 1, 0, 0)
                """);
        jdbcTemplate.update("merge into user_role (user_id, role_id) key(user_id, role_id) values (3, 2)");
    }

    @Test
    void itineraryShouldGenerateSaveAndListPlans() throws Exception {
        mockMvc.perform(post("/api/itinerary/ai/generate")
                        .header("Authorization", userToken())
                        .contentType("application/json")
                        .content("""
                                {
                                  "destination": "杭州",
                                  "days": 2,
                                  "startDate": "2026-07-01",
                                  "budget": "人均3000元",
                                  "preferences": "慢节奏和美食"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.destination").value("杭州"))
                .andExpect(jsonPath("$.data.plan_data").isNotEmpty());

        mockMvc.perform(post("/api/itinerary/plans")
                        .header("Authorization", userToken())
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "杭州周末行程",
                                  "destination": "杭州",
                                  "start_date": "2026-07-01",
                                  "end_date": "2026-07-02",
                                  "plan_data": "[{\\"day\\":1,\\"theme\\":\\"西湖\\",\\"items\\":[\\"白堤\\"]}]",
                                  "is_public": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.title").value("杭州周末行程"));

        mockMvc.perform(get("/api/itinerary/my-plans")
                        .header("Authorization", userToken())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.list[0].destination", notNullValue()));
    }

    @Test
    void communityShouldLikeCommentAndAnswerQuestion() throws Exception {
        mockMvc.perform(get("/api/posts")
                        .header("Authorization", userToken())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(1)));

        mockMvc.perform(post("/api/posts/1/like")
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.liked").isBoolean())
                .andExpect(jsonPath("$.data.likes", greaterThanOrEqualTo(0)));

        mockMvc.perform(post("/api/posts/1/comments")
                        .header("Authorization", userToken())
                        .contentType("application/json")
                        .content("{\"content\":\"路线很实用，准备照着走。\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").value("路线很实用，准备照着走。"));

        String questionId = createQuestionAndReturnId(userToken(), "where to stay in Hangzhou", "near subway and lake");

        mockMvc.perform(post("/api/questions/" + questionId + "/answer")
                        .header("Authorization", userToken())
                        .contentType("application/json")
                        .content("{\"answer\":\"try Hubin or Longxiangqiao first\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.answer").value("try Hubin or Longxiangqiao first"));
    }

    @Test
    void communityShouldStoreOnlyImageObjectNames() throws Exception {
        String oldUrl = "https://objectstorage.ap-tokyo-1.oraclecloud.com/p/expired/n/example/b/ortus-bucket/o/post-0000000000000002-example.jpg?token=expired";
        String response = mockMvc.perform(post("/api/posts")
                        .header("Authorization", userToken())
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "image object name storage",
                                  "content": "community post image storage test",
                                  "images": "[\\"%s\\"]"
                                }
                                """.formatted(oldUrl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images").value("[\"post-0000000000000002-example.jpg\"]"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String postId = JsonPath.read(response, "$.data.id");
        String storedImages = jdbcTemplate.queryForObject(
                "select images from post where id = ?",
                String.class,
                Long.valueOf(postId)
        );
        org.junit.jupiter.api.Assertions.assertEquals("[\"post-0000000000000002-example.jpg\"]", storedImages);
    }

    @Test
    void communityShouldLimitDeleteToOwnerOrAdmin() throws Exception {
        String postId = createPostAndReturnId(userToken(), "权限测试游记");

        mockMvc.perform(delete("/api/posts/" + postId)
                        .header("Authorization", otherUserToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/posts/" + postId)
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        String ownerPostId = createPostAndReturnId(userToken(), "本人删除游记");
        mockMvc.perform(delete("/api/posts/" + ownerPostId)
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void communityShouldLimitCommentDeleteToCommentOwnerOrAdmin() throws Exception {
        String postId = createPostAndReturnId(userToken(), "评论删除权限游记");
        String commentId = createCommentAndReturnId(userToken(), postId, "这条评论只能本人或管理员删除");

        mockMvc.perform(delete("/api/posts/" + postId + "/comments/" + commentId)
                        .header("Authorization", otherUserToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/posts/" + postId + "/comments/" + commentId)
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        String adminCommentId = createCommentAndReturnId(userToken(), postId, "管理员可以删除这条评论");
        mockMvc.perform(delete("/api/posts/" + postId + "/comments/" + adminCommentId)
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void communityPostOwnerShouldDeleteCommentsUnderOwnPost() throws Exception {
        String postId = createPostAndReturnId(userToken(), "post owner can moderate comments");
        String commentId = createCommentAndReturnId(secondUserToken(), postId, "comment by second user");

        mockMvc.perform(delete("/api/posts/" + postId + "/comments/" + commentId)
                        .header("Authorization", otherUserToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/posts/" + postId + "/comments/" + commentId)
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void communityShouldLimitQuestionAndAnswerDeleteToOwnerOrAdmin() throws Exception {
        String questionId = createQuestionAndReturnId(userToken(), "question delete permission", "question content");

        mockMvc.perform(delete("/api/questions/" + questionId)
                        .header("Authorization", otherUserToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/questions/" + questionId)
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        String adminQuestionId = createQuestionAndReturnId(userToken(), "admin question delete permission", "question content");
        mockMvc.perform(delete("/api/questions/" + adminQuestionId)
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        String answeredQuestionId = createQuestionAndReturnId(userToken(), "answer delete permission", "question content");
        answerQuestion(answeredQuestionId, secondUserToken(), "answer by second user");

        mockMvc.perform(post("/api/questions/" + answeredQuestionId + "/answer")
                        .header("Authorization", userToken())
                        .contentType("application/json")
                        .content("{\"answer\":\"overwrite by question owner\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/questions/" + answeredQuestionId + "/answer")
                        .header("Authorization", userToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/questions/" + answeredQuestionId + "/answer")
                        .header("Authorization", secondUserToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        answerQuestion(answeredQuestionId, secondUserToken(), "answer by second user again");
        mockMvc.perform(delete("/api/questions/" + answeredQuestionId + "/answer")
                        .header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
    }

    private String createPostAndReturnId(String token, String title) throws Exception {
        String response = mockMvc.perform(post("/api/posts")
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "%s",
                                  "content": "用于权限测试的游记正文。",
                                  "images": "[]"
                                }
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    private String createCommentAndReturnId(String token, String postId, String content) throws Exception {
        String response = mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content("{\"content\":\"%s\"}".formatted(content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    private String createQuestionAndReturnId(String token, String title, String content) throws Exception {
        String response = mockMvc.perform(post("/api/questions")
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content("{\"title\":\"%s\",\"content\":\"%s\"}".formatted(title, content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    private void answerQuestion(String questionId, String token, String answer) throws Exception {
        mockMvc.perform(post("/api/questions/" + questionId + "/answer")
                        .header("Authorization", token)
                        .contentType("application/json")
                        .content("{\"answer\":\"%s\"}".formatted(answer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
