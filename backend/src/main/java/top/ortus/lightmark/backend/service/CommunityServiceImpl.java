package top.ortus.lightmark.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.ortus.lightmark.backend.common.PageResponse;
import top.ortus.lightmark.backend.dto.module.CommentDTO;
import top.ortus.lightmark.backend.dto.module.PostDTO;
import top.ortus.lightmark.backend.dto.module.QuestionDTO;
import top.ortus.lightmark.backend.exception.ApiException;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CommunityServiceImpl implements CommunityService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CommunityServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostDTO> listPosts(Long userId, Map<String, String> params) {
        int page = positiveInt(params.get("page"), 1);
        int size = Math.min(positiveInt(params.get("size"), 20), 50);
        String keyword = trim(params.get("keyword"));
        String sort = trim(params.get("sort"));
        String orderBy = "hot".equalsIgnoreCase(sort)
                ? " order by likes desc, comments_count desc, create_time desc"
                : " order by create_time desc";
        String where = " where status = 1";
        Object[] countArgs;
        Object[] listArgs;
        if (StringUtils.hasText(keyword)) {
            where += " and (title like ? or content like ?)";
            String like = "%" + keyword + "%";
            countArgs = new Object[]{like, like};
            listArgs = new Object[]{like, like, size, (page - 1) * size};
        } else {
            countArgs = new Object[]{};
            listArgs = new Object[]{size, (page - 1) * size};
        }
        Long total = jdbcTemplate.queryForObject("select count(1) from post" + where, Long.class, countArgs);
        List<PostDTO> list = jdbcTemplate.query(
                """
                select p.*, u.nickname as user_nickname,
                  case when pl.user_id is null then 0 else 1 end as liked
                from post p
                left join `user` u on u.id = p.user_id
                left join post_like pl on pl.post_id = p.id and pl.user_id = ?
                """ + where.replace("status", "p.status")
                        .replace("title like", "p.title like")
                        .replace("content like", "p.content like")
                        + orderBy.replace("likes", "p.likes")
                        .replace("comments_count", "p.comments_count")
                        .replace("create_time", "p.create_time")
                        + " limit ? offset ?",
                this::mapPost,
                prependUserId(userId, listArgs)
        );
        return new PageResponse<>(total == null ? 0 : total, page, size, list);
    }

    @Override
    @Transactional(readOnly = true)
    public PostDTO getPost(Long userId, Long id) {
        return getExistingPost(userId, id);
    }

    @Override
    @Transactional
    public PostDTO createPost(Long userId, PostDTO payload) {
        ensureUserExists(userId);
        PostDTO post = payload == null ? new PostDTO() : payload;
        validatePost(post);
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into post (user_id, title, content, images, likes, comments_count, status)
                    values (?, ?, ?, ?, 0, 0, 1)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, post.getTitle().trim());
            ps.setString(3, defaultText(post.getContent(), ""));
            ps.setString(4, normalizePostImages(post.getImages()));
            return ps;
        });
        return getExistingPost(userId, lastInsertedId());
    }

    @Override
    @Transactional
    public PostDTO updatePost(Long userId, Long id, PostDTO payload) {
        ensureUserExists(userId);
        PostDTO existing = getExistingPost(id);
        ensureOwner(userId, existing.getUser_id(), "只能编辑自己的游记");
        PostDTO post = payload == null ? new PostDTO() : payload;
        validatePost(post);
        jdbcTemplate.update("""
                update post
                set title = ?, content = ?, images = ?, update_time = current_timestamp
                where id = ? and user_id = ?
                """,
                post.getTitle().trim(),
                defaultText(post.getContent(), ""),
                normalizePostImages(post.getImages()),
                id,
                userId
        );
        return getExistingPost(userId, id);
    }

    @Override
    @Transactional
    public boolean deletePost(Long userId, boolean admin, Long id) {
        PostDTO existing = getExistingPost(id);
        ensureOwnerOrAdmin(userId, admin, existing.getUser_id(), "只能删除自己的游记");
        return jdbcTemplate.update("update post set status = 0 where id = ?", id) > 0;
    }

    @Override
    @Transactional
    public Map<String, Object> togglePostLike(Long userId, Long id) {
        ensureUserExists(userId);
        getExistingPost(id);
        Integer liked = jdbcTemplate.queryForObject(
                "select count(1) from post_like where post_id = ? and user_id = ?",
                Integer.class,
                id,
                userId
        );
        boolean nowLiked;
        if (liked != null && liked > 0) {
            jdbcTemplate.update("delete from post_like where post_id = ? and user_id = ?", id, userId);
            jdbcTemplate.update("update post set likes = greatest(likes - 1, 0) where id = ?", id);
            nowLiked = false;
        } else {
            try {
                jdbcTemplate.update("insert into post_like (post_id, user_id) values (?, ?)", id, userId);
                jdbcTemplate.update("update post set likes = likes + 1 where id = ?", id);
            } catch (DuplicateKeyException ignored) {
                // Another request may have inserted the same like; keep the final state as liked.
            }
            nowLiked = true;
        }
        Integer likes = jdbcTemplate.queryForObject("select likes from post where id = ?", Integer.class, id);
        return Map.of("liked", nowLiked, "likes", likes == null ? 0 : likes);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentDTO> listPostComments(Long postId, Map<String, String> params) {
        getExistingPost(postId);
        int page = positiveInt(params.get("page"), 1);
        int size = Math.min(positiveInt(params.get("size"), 20), 50);
        Long total = jdbcTemplate.queryForObject(
                "select count(1) from comment where target_type = 'POST' and target_id = ? and is_approved = 1",
                Long.class,
                postId
        );
        List<CommentDTO> list = jdbcTemplate.query("""
                select c.*, u.nickname as user_nickname
                from comment c
                left join `user` u on u.id = c.user_id
                where c.target_type = 'POST' and c.target_id = ? and c.is_approved = 1
                order by c.create_time asc
                limit ? offset ?
                """, this::mapComment, postId, size, (page - 1) * size);
        return new PageResponse<>(total == null ? 0 : total, page, size, list);
    }

    @Override
    @Transactional
    public CommentDTO createPostComment(Long userId, Long postId, CommentDTO payload, String ip) {
        ensureUserExists(userId);
        getExistingPost(postId);
        CommentDTO comment = payload == null ? new CommentDTO() : payload;
        if (!StringUtils.hasText(comment.getContent())) {
            throw new ApiException(400, "评论内容不能为空");
        }
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into comment (target_type, target_id, user_id, parent_id, content, likes, is_approved, ip)
                    values ('POST', ?, ?, ?, ?, 0, 1, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, postId);
            ps.setLong(2, userId);
            if (StringUtils.hasText(comment.getParent_id())) {
                ps.setLong(3, Long.parseLong(comment.getParent_id()));
            } else {
                ps.setObject(3, null);
            }
            ps.setString(4, comment.getContent().trim());
            ps.setString(5, ip);
            return ps;
        }, keyHolder);
        jdbcTemplate.update("update post set comments_count = comments_count + 1 where id = ?", postId);
        return getComment(generatedId(keyHolder));
    }

    @Override
    @Transactional
    public boolean deletePostComment(Long userId, boolean admin, Long postId, Long commentId) {
        PostDTO post = getExistingPost(postId);
        CommentDTO comment = getExistingPostComment(postId, commentId);
        if (!admin && !String.valueOf(userId).equals(comment.getUser_id()) && !String.valueOf(userId).equals(post.getUser_id())) {
            throw new ApiException(403, "只能删除自己的评论回复");
        }
        int affected = jdbcTemplate.update(
                "update comment set is_approved = 0 where id = ? and target_type = 'POST' and target_id = ? and is_approved = 1",
                commentId,
                postId
        );
        if (affected > 0) {
            jdbcTemplate.update("update post set comments_count = greatest(comments_count - 1, 0) where id = ?", postId);
        }
        return affected > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuestionDTO> listQuestions(Map<String, String> params) {
        int page = positiveInt(params.get("page"), 1);
        int size = Math.min(positiveInt(params.get("size"), 20), 50);
        String keyword = trim(params.get("keyword"));
        String where = "";
        Object[] countArgs;
        Object[] listArgs;
        if (StringUtils.hasText(keyword)) {
            where = " where title like ? or content like ?";
            String like = "%" + keyword + "%";
            countArgs = new Object[]{like, like};
            listArgs = new Object[]{like, like, size, (page - 1) * size};
        } else {
            countArgs = new Object[]{};
            listArgs = new Object[]{size, (page - 1) * size};
        }
        Long total = jdbcTemplate.queryForObject("select count(1) from question" + where, Long.class, countArgs);
        List<QuestionDTO> list = jdbcTemplate.query(
                """
                select q.*, u.nickname as user_nickname, au.nickname as answer_user_nickname
                from question q
                left join `user` u on u.id = q.user_id
                left join `user` au on au.id = q.answer_user_id
                """ + where.replace("title like", "q.title like")
                        .replace("content like", "q.content like")
                        + " order by q.create_time desc limit ? offset ?",
                this::mapQuestion,
                listArgs
        );
        return new PageResponse<>(total == null ? 0 : total, page, size, list);
    }

    @Override
    @Transactional
    public QuestionDTO createQuestion(Long userId, QuestionDTO payload) {
        ensureUserExists(userId);
        QuestionDTO question = payload == null ? new QuestionDTO() : payload;
        if (!StringUtils.hasText(question.getTitle()) || !StringUtils.hasText(question.getContent())) {
            throw new ApiException(400, "问题标题和内容不能为空");
        }
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    insert into question (user_id, title, content, status)
                    values (?, ?, ?, 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, question.getTitle().trim());
            ps.setString(3, question.getContent().trim());
            return ps;
        }, keyHolder);
        return getQuestion(generatedId(keyHolder));
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionDTO getQuestion(Long id) {
        List<QuestionDTO> questions = jdbcTemplate.query("""
                select q.*, u.nickname as user_nickname, au.nickname as answer_user_nickname
                from question q
                left join `user` u on u.id = q.user_id
                left join `user` au on au.id = q.answer_user_id
                where q.id = ?
                """, this::mapQuestion, id);
        if (questions.isEmpty()) {
            throw new ApiException(404, "问题不存在");
        }
        return questions.get(0);
    }

    @Override
    @Transactional
    public QuestionDTO answerQuestion(Long userId, boolean admin, Long id, Map<String, Object> payload) {
        ensureUserExists(userId);
        QuestionDTO existing = getQuestion(id);
        boolean answered = existing.getStatus() == 1
                || StringUtils.hasText(existing.getAnswer())
                || StringUtils.hasText(existing.getAnswer_user_id());
        if (answered
                && !admin
                && !String.valueOf(userId).equals(existing.getAnswer_user_id())) {
            throw new ApiException(403, "只能修改自己的回答");
        }
        String answer = Objects.toString(payload == null ? "" : payload.get("answer"), "").trim();
        if (!StringUtils.hasText(answer)) {
            throw new ApiException(400, "回答内容不能为空");
        }
        jdbcTemplate.update("""
                update question
                set answer = ?, answer_user_id = ?, status = 1, answer_time = current_timestamp
                where id = ?
                """, answer, userId, id);
        return getQuestion(id);
    }

    @Override
    @Transactional
    public boolean deleteQuestion(Long userId, boolean admin, Long id) {
        QuestionDTO question = getQuestion(id);
        ensureOwnerOrAdmin(userId, admin, question.getUser_id(), "只能删除自己的问题");
        return jdbcTemplate.update("delete from question where id = ?", id) > 0;
    }

    @Override
    @Transactional
    public boolean deleteAnswer(Long userId, boolean admin, Long id) {
        QuestionDTO question = getQuestion(id);
        if (!StringUtils.hasText(question.getAnswer()) || !StringUtils.hasText(question.getAnswer_user_id())) {
            throw new ApiException(404, "回答不存在");
        }
        ensureOwnerOrAdmin(userId, admin, question.getAnswer_user_id(), "只能删除自己的回答");
        return jdbcTemplate.update("""
                update question
                set answer = null, answer_user_id = null, status = 0, answer_time = null
                where id = ?
                """, id) > 0;
    }

    private PostDTO getExistingPost(Long id) {
        return getExistingPost(null, id);
    }

    private PostDTO getExistingPost(Long userId, Long id) {
        List<PostDTO> posts = jdbcTemplate.query("""
                select p.*, u.nickname as user_nickname,
                  case when pl.user_id is null then 0 else 1 end as liked
                from post p
                left join `user` u on u.id = p.user_id
                left join post_like pl on pl.post_id = p.id and pl.user_id = ?
                where p.id = ? and p.status = 1
                """, this::mapPost, userId, id);
        if (posts.isEmpty()) {
            throw new ApiException(404, "游记不存在");
        }
        return posts.get(0);
    }

    private CommentDTO getComment(Long id) {
        List<CommentDTO> comments = jdbcTemplate.query("""
                select c.*, u.nickname as user_nickname
                from comment c
                left join `user` u on u.id = c.user_id
                where c.id = ?
                """, this::mapComment, id);
        if (comments.isEmpty()) {
            throw new ApiException(404, "评论不存在");
        }
        return comments.get(0);
    }

    private CommentDTO getExistingPostComment(Long postId, Long commentId) {
        List<CommentDTO> comments = jdbcTemplate.query("""
                select c.*, u.nickname as user_nickname
                from comment c
                left join `user` u on u.id = c.user_id
                where c.id = ? and c.target_type = 'POST' and c.target_id = ? and c.is_approved = 1
                """, this::mapComment, commentId, postId);
        if (comments.isEmpty()) {
            throw new ApiException(404, "评论不存在");
        }
        return comments.get(0);
    }

    private PostDTO mapPost(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        PostDTO dto = new PostDTO();
        dto.setId(String.valueOf(rs.getLong("id")));
        dto.setUser_id(String.valueOf(rs.getLong("user_id")));
        dto.setUser_nickname(readString(rs, "user_nickname"));
        dto.setTitle(rs.getString("title"));
        dto.setContent(rs.getString("content"));
        dto.setImages(rs.getString("images"));
        dto.setLikes(rs.getInt("likes"));
        dto.setLiked(readBoolean(rs, "liked"));
        dto.setComments_count(rs.getInt("comments_count"));
        dto.setStatus(rs.getInt("status"));
        var createTime = rs.getTimestamp("create_time");
        var updateTime = rs.getTimestamp("update_time");
        dto.setCreate_time(createTime == null ? null : createTime.toLocalDateTime());
        dto.setUpdate_time(updateTime == null ? null : updateTime.toLocalDateTime());
        return dto;
    }

    private CommentDTO mapComment(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        CommentDTO dto = new CommentDTO();
        dto.setId(String.valueOf(rs.getLong("id")));
        dto.setTarget_type(rs.getString("target_type"));
        dto.setTarget_id(String.valueOf(rs.getLong("target_id")));
        dto.setUser_id(String.valueOf(rs.getLong("user_id")));
        dto.setUser_nickname(readString(rs, "user_nickname"));
        long parentId = rs.getLong("parent_id");
        dto.setParent_id(rs.wasNull() ? null : String.valueOf(parentId));
        dto.setContent(rs.getString("content"));
        dto.setLikes(rs.getInt("likes"));
        dto.setIs_approved(rs.getInt("is_approved"));
        dto.setIp(rs.getString("ip"));
        var createTime = rs.getTimestamp("create_time");
        dto.setCreate_time(createTime == null ? LocalDateTime.now() : createTime.toLocalDateTime());
        return dto;
    }

    private QuestionDTO mapQuestion(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        QuestionDTO dto = new QuestionDTO();
        dto.setId(String.valueOf(rs.getLong("id")));
        dto.setUser_id(String.valueOf(rs.getLong("user_id")));
        dto.setUser_nickname(readString(rs, "user_nickname"));
        dto.setTitle(rs.getString("title"));
        dto.setContent(rs.getString("content"));
        dto.setAnswer(rs.getString("answer"));
        long answerUserId = rs.getLong("answer_user_id");
        dto.setAnswer_user_id(rs.wasNull() ? null : String.valueOf(answerUserId));
        dto.setAnswer_user_nickname(readString(rs, "answer_user_nickname"));
        dto.setStatus(rs.getInt("status"));
        var createTime = rs.getTimestamp("create_time");
        var answerTime = rs.getTimestamp("answer_time");
        dto.setCreate_time(createTime == null ? null : createTime.toLocalDateTime());
        dto.setAnswer_time(answerTime == null ? null : answerTime.toLocalDateTime());
        return dto;
    }

    private void validatePost(PostDTO post) {
        if (!StringUtils.hasText(post.getTitle())) {
            throw new ApiException(400, "游记标题不能为空");
        }
        if (!StringUtils.hasText(post.getContent())) {
            throw new ApiException(400, "游记内容不能为空");
        }
    }

    private String normalizePostImages(String images) {
        String value = defaultText(images, "[]");
        try {
            JsonNode root = objectMapper.readTree(value);
            if (!root.isArray()) {
                throw new ApiException(400, "images must be a JSON array");
            }
            List<String> objectNames = new ArrayList<>();
            for (JsonNode item : root) {
                if (!item.isTextual()) {
                    continue;
                }
                String objectName = communityImageObjectName(item.asText());
                if (StringUtils.hasText(objectName)) {
                    objectNames.add(objectName);
                }
                if (objectNames.size() == 9) {
                    break;
                }
            }
            return objectMapper.writeValueAsString(objectNames);
        } catch (JsonProcessingException ex) {
            throw new ApiException(400, "images must be a valid JSON array");
        }
    }

    private String communityImageObjectName(String value) {
        String normalized = trim(value).replace('\\', '/');
        int suffixStart = normalized.length();
        int queryIndex = normalized.indexOf('?');
        int fragmentIndex = normalized.indexOf('#');
        if (queryIndex >= 0) {
            suffixStart = Math.min(suffixStart, queryIndex);
        }
        if (fragmentIndex >= 0) {
            suffixStart = Math.min(suffixStart, fragmentIndex);
        }
        normalized = normalized.substring(0, suffixStart);
        if ((normalized.startsWith("http://") || normalized.startsWith("https://"))
                && !normalized.contains("objectstorage.ap-tokyo-1.oraclecloud.com/")) {
            return normalized;
        }
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private void ensureOwner(Long userId, String ownerId, String message) {
        if (!String.valueOf(userId).equals(ownerId)) {
            throw new ApiException(403, message);
        }
    }

    private void ensureOwnerOrAdmin(Long userId, boolean admin, String ownerId, String message) {
        if (!admin && !String.valueOf(userId).equals(ownerId)) {
            throw new ApiException(403, message);
        }
    }

    private void ensureUserExists(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApiException(401, "请先登录");
        }
        Integer count = jdbcTemplate.queryForObject(
                "select count(1) from `user` where id = ? and deleted = 0",
                Integer.class,
                userId
        );
        if (count == null || count == 0) {
            throw new ApiException(401, "登录状态已失效，请重新登录");
        }
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private Long lastInsertedId() {
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        if (id == null || id <= 0) {
            throw new ApiException(500, "游记发布失败");
        }
        return id;
    }

    private String readString(java.sql.ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (java.sql.SQLException ignored) {
            return null;
        }
    }

    private boolean readBoolean(java.sql.ResultSet rs, String column) {
        try {
            return rs.getBoolean(column);
        } catch (java.sql.SQLException ignored) {
            return false;
        }
    }

    private Object[] prependUserId(Long userId, Object[] args) {
        Object[] merged = new Object[args.length + 1];
        merged[0] = userId;
        System.arraycopy(args, 0, merged, 1, args.length);
        return merged;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private int positiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(Objects.toString(value, ""));
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private long generatedId(GeneratedKeyHolder keyHolder) {
        Number key = keyHolder.getKeyList().stream()
                .map(keys -> keys.get("id"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .findFirst()
                .orElse(null);
        if (key == null && keyHolder.getKeyList().size() == 1) {
            key = keyHolder.getKeyList().get(0).values().stream()
                    .filter(Number.class::isInstance)
                    .map(Number.class::cast)
                    .findFirst()
                    .orElse(null);
        }
        if (key == null) {
            throw new ApiException(500, "failed to read generated id");
        }
        return key.longValue();
    }
}
