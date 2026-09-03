package top.ortus.lightmark.content.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;
import top.ortus.lightmark.common.ApiResponse;
import top.ortus.lightmark.common.PageResponse;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.common.security.JwtTokenService;
import top.ortus.lightmark.common.security.UserIdentity;
import top.ortus.lightmark.content.client.UserProfileClient;
import top.ortus.lightmark.content.service.ContentAiService;
import top.ortus.lightmark.content.service.CommunityImageStorageService;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内容域接口：社区、智能行程和 AI 能力全部收敛在 content-service。
 * 注意：这里不再使用单体的通用 CRUD，也不会查询 user/order 等其他服务的表。
 */
@RestController
@RequestMapping("/api")
public class ContentController {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final JwtTokenService jwt;
    private final UserProfileClient userProfileClient;
    private final ContentAiService aiService;
    private final CommunityImageStorageService imageStorage;
    /** 会话上下文暂存内存，避免把 AI 对话表引入 content 数据边界。 */
    private final Map<String, List<Map<String, String>>> chatContexts = new ConcurrentHashMap<>();

    @Autowired
    public ContentController(JdbcTemplate jdbc, ObjectMapper mapper, JwtTokenService jwt,
                             UserProfileClient userProfileClient, ContentAiService aiService,
                             CommunityImageStorageService imageStorage) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.jwt = jwt;
        this.userProfileClient = userProfileClient;
        this.aiService = aiService;
        this.imageStorage = imageStorage;
    }

    /** Backward-compatible constructor for unit tests and embedders. */
    public ContentController(JdbcTemplate jdbc, ObjectMapper mapper, JwtTokenService jwt,
                             UserProfileClient userProfileClient, ContentAiService aiService) {
        this(jdbc, mapper, jwt, userProfileClient, aiService, new CommunityImageStorageService());
    }

    @PostMapping(value = "/upload/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> uploadImage(@RequestHeader("Authorization") String authorization,
                                                         @RequestPart("file") MultipartFile file) {
        long userId = requiredUserId(authorization);
        return ApiResponse.ok(Map.of("objectName", imageStorage.upload(userId, file)));
    }

    // -------------------- 社区游记 --------------------

    @GetMapping({"/community/posts", "/posts"})
    public ApiResponse<PageResponse<Map<String, Object>>> listPosts(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam Map<String, String> params) {
        Long userId = optionalUserId(authorization);
        int page = positive(params.get("page"), 1);
        int size = Math.min(positive(params.get("size"), 20), 50);
        String keyword = params.get("keyword");
        StringBuilder where = new StringBuilder(" where p.status = 1 ");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            where.append(" and (p.title like ? or p.content like ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        Long total = jdbc.queryForObject("select count(*) from post p" + where, Long.class, args.toArray());
        String order = "hot".equalsIgnoreCase(params.get("sort"))
                ? " order by p.likes desc, p.comments_count desc, p.create_time desc"
                : " order by p.create_time desc";
        String sql = "select p.id,p.user_id,p.title,p.content,p.images,p.likes,p.comments_count,p.status,p.create_time,p.update_time"
                + " from post p" + where + order + " limit ? offset ?";
        args.add(size);
        args.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
        rows.forEach(row -> {
            long authorId = ((Number) row.get("user_id")).longValue();
            row.put("author", userProfileClient.getProfile(authorId));
            row.put("liked", userId != null && liked(userId, ((Number) row.get("id")).longValue()));
        });
        return ApiResponse.ok(new PageResponse<>(total == null ? 0 : total, page, size, rows));
    }

    @GetMapping({"/community/posts/{id}", "/posts/{id}"})
    public ApiResponse<Map<String, Object>> getPost(@PathVariable Long id) {
        return ApiResponse.ok(post(id));
    }

    @PostMapping({"/community/posts", "/posts"})
    public ApiResponse<Map<String, Object>> createPost(@RequestHeader("Authorization") String authorization,
                                                        @RequestBody Map<String, Object> body) {
        long userId = requiredUserId(authorization);
        String title = text(body, "title");
        if (!StringUtils.hasText(title)) throw new ApiException(400, "游记标题不能为空");
        String content = text(body, "content");
        String images = json(body.get("images"));
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement("insert into post(user_id,title,content,images,likes,comments_count,status) values(?,?,?, ?,0,0,1)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId); ps.setString(2, title.trim()); ps.setString(3, content); ps.setString(4, images); return ps;
        }, keys);
        return ApiResponse.ok(post(keys.getKey().longValue()));
    }

    @PutMapping({"/community/posts/{id}", "/posts/{id}"})
    public ApiResponse<Map<String, Object>> updatePost(@RequestHeader("Authorization") String authorization,
                                                        @PathVariable Long id, @RequestBody Map<String, Object> body) {
        long userId = requiredUserId(authorization);
        ensureOwner(id, userId, "post", "只能编辑自己的游记");
        jdbc.update("update post set title=?,content=?,images=?,update_time=current_timestamp where id=?", text(body, "title"), text(body, "content"), json(body.get("images")), id);
        return ApiResponse.ok(post(id));
    }

    @DeleteMapping({"/community/posts/{id}", "/posts/{id}"})
    public ApiResponse<Boolean> deletePost(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        long userId = requiredUserId(authorization);
        ensureOwnerOrAdmin(id, userId, "post", "只能删除自己的游记", authorization);
        return ApiResponse.ok(jdbc.update("update post set status=0 where id=?", id) > 0);
    }

    @PostMapping({"/community/posts/{id}/like", "/posts/{id}/like"})
    public ApiResponse<Map<String, Object>> toggleLike(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        long userId = requiredUserId(authorization); post(id);
        boolean liked = liked(userId, id);
        if (liked) { jdbc.update("delete from post_like where post_id=? and user_id=?", id, userId); jdbc.update("update post set likes=greatest(likes-1,0) where id=?", id); }
        else { jdbc.update("insert ignore into post_like(post_id,user_id) values(?,?)", id, userId); jdbc.update("update post set likes=likes+1 where id=?", id); }
        Integer count = jdbc.queryForObject("select likes from post where id=?", Integer.class, id);
        return ApiResponse.ok(Map.of("liked", !liked, "likes", count == null ? 0 : count));
    }

    @GetMapping({"/community/posts/{id}/comments", "/posts/{id}/comments"})
    public ApiResponse<PageResponse<Map<String, Object>>> comments(@PathVariable Long id, @RequestParam Map<String, String> params) {
        post(id); int page = positive(params.get("page"), 1); int size = Math.min(positive(params.get("size"), 20), 50);
        Long total = jdbc.queryForObject("select count(*) from comment where target_type='POST' and target_id=? and is_approved=1", Long.class, id);
        List<Map<String, Object>> rows = jdbc.queryForList("select id,target_type,target_id,user_id,parent_id,content,likes,create_time from comment where target_type='POST' and target_id=? and is_approved=1 order by create_time asc limit ? offset ?", id, size, (page - 1) * size);
        return ApiResponse.ok(new PageResponse<>(total == null ? 0 : total, page, size, rows));
    }

    @PostMapping({"/community/posts/{id}/comments", "/posts/{id}/comments"})
    public ApiResponse<Map<String, Object>> addComment(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        long userId = requiredUserId(authorization); post(id); String content = text(body, "content");
        if (!StringUtils.hasText(content)) throw new ApiException(400, "评论内容不能为空");
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(c -> { PreparedStatement ps = c.prepareStatement("insert into comment(target_type,target_id,user_id,parent_id,content,likes,is_approved) values('POST',?,?,?, ?,0,1)", Statement.RETURN_GENERATED_KEYS); ps.setLong(1,id); ps.setLong(2,userId); ps.setObject(3, body.get("parentId")); ps.setString(4,content.trim()); return ps; }, keys);
        jdbc.update("update post set comments_count=comments_count+1 where id=?", id);
        return ApiResponse.ok(jdbc.queryForMap("select id,target_type,target_id,user_id,parent_id,content,likes,create_time from comment where id=?", keys.getKey()));
    }

    // -------------------- 问答 --------------------

    @GetMapping({"/community/questions", "/questions"})
    public ApiResponse<PageResponse<Map<String, Object>>> questions(@RequestParam Map<String, String> params) {
        int page = positive(params.get("page"), 1); int size = Math.min(positive(params.get("size"), 20), 50);
        String keyword = params.get("keyword"); String where = ""; List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) { where = " where title like ? or content like ?"; args.add("%"+keyword.trim()+"%"); args.add("%"+keyword.trim()+"%"); }
        Long total = jdbc.queryForObject("select count(*) from question" + where, Long.class, args.toArray()); args.add(size); args.add((page-1)*size);
        return ApiResponse.ok(new PageResponse<>(total == null ? 0 : total, page, size, jdbc.queryForList("select * from question" + where + " order by create_time desc limit ? offset ?", args.toArray())));
    }

    @PostMapping({"/community/questions", "/questions"})
    public ApiResponse<Map<String, Object>> createQuestion(@RequestHeader("Authorization") String authorization, @RequestBody Map<String, Object> body) {
        long userId = requiredUserId(authorization); String title=text(body,"title"), content=text(body,"content");
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) throw new ApiException(400,"问题标题和内容不能为空");
        KeyHolder keys=new GeneratedKeyHolder(); jdbc.update(c->{PreparedStatement ps=c.prepareStatement("insert into question(user_id,title,content,status) values(?,?,?,0)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,userId);ps.setString(2,title.trim());ps.setString(3,content.trim());return ps;},keys);
        return ApiResponse.ok(jdbc.queryForMap("select * from question where id=?",keys.getKey()));
    }

    @GetMapping({"/community/questions/{id}", "/questions/{id}"})
    public ApiResponse<Map<String, Object>> getQuestion(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from question where id=?", id);
        if (rows.isEmpty()) throw new ApiException(404, "问题不存在");
        return ApiResponse.ok(rows.get(0));
    }

    @PostMapping({"/community/questions/{id}/answer", "/questions/{id}/answer"})
    public ApiResponse<Map<String, Object>> answer(@RequestHeader("Authorization") String authorization,@PathVariable Long id,@RequestBody Map<String,Object> body){
        long userId=requiredUserId(authorization); String answer=text(body,"answer"); if(!StringUtils.hasText(answer)) throw new ApiException(400,"回答内容不能为空");
        if(jdbc.queryForObject("select count(*) from question where id=?",Integer.class,id)==0) throw new ApiException(404,"问题不存在");
        jdbc.update("update question set answer=?,answer_user_id=?,status=1,answer_time=current_timestamp where id=?",answer.trim(),userId,id); return ApiResponse.ok(jdbc.queryForMap("select * from question where id=?",id));
    }

    @DeleteMapping({"/community/questions/{id}", "/questions/{id}"})
    public ApiResponse<Boolean> deleteQuestion(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        long userId = requiredUserId(authorization);
        ensureOwner(id, userId, "question", "只能删除自己的问题");
        return ApiResponse.ok(jdbc.update("delete from question where id=?", id) > 0);
    }

    @DeleteMapping({"/community/questions/{id}/answer", "/questions/{id}/answer"})
    public ApiResponse<Boolean> deleteAnswer(@RequestHeader("Authorization") String authorization, @PathVariable Long id) {
        long userId = requiredUserId(authorization);
        Map<String, Object> question = getQuestion(id).getData();
        Object answerUser = question.get("answer_user_id");
        if (answerUser == null || !String.valueOf(answerUser).equals(String.valueOf(userId))) {
            throw new ApiException(403, "只能删除自己的回答");
        }
        return ApiResponse.ok(jdbc.update("update question set answer=null,answer_user_id=null,status=0,answer_time=null where id=?", id) > 0);
    }

    // -------------------- 智能行程 --------------------

    @GetMapping("/itinerary/my-plans")
    public ApiResponse<PageResponse<Map<String,Object>>> myPlans(@RequestHeader("Authorization") String authorization,@RequestParam Map<String,String> params){
        long userId=requiredUserId(authorization); int page=positive(params.get("page"),1),size=Math.min(positive(params.get("size"),20),50); Long total=jdbc.queryForObject("select count(*) from travel_plan where user_id=?",Long.class,userId);
        List<Map<String,Object>> rows=jdbc.queryForList("select * from travel_plan where user_id=? order by create_time desc limit ? offset ?",userId,size,(page-1)*size); return ApiResponse.ok(new PageResponse<>(total==null?0:total,page,size,rows));
    }

    @PostMapping("/itinerary/plans")
    public ApiResponse<Map<String,Object>> createPlan(@RequestHeader("Authorization") String authorization,@RequestBody Map<String,Object> body){
        long userId=requiredUserId(authorization); String destination=text(body,"destination"); if(!StringUtils.hasText(destination)) throw new ApiException(400,"目的地不能为空");
        KeyHolder keys=new GeneratedKeyHolder(); jdbc.update(c->{PreparedStatement ps=c.prepareStatement("insert into travel_plan(user_id,title,destination,start_date,end_date,plan_data,is_public) values(?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);ps.setLong(1,userId);ps.setString(2,text(body,"title"));ps.setString(3,destination);ps.setObject(4,body.get("startDate"));ps.setObject(5,body.get("endDate"));ps.setString(6,json(body.get("planData")));ps.setInt(7,number(body.get("isPublic"),0));return ps;},keys); return ApiResponse.ok(jdbc.queryForMap("select * from travel_plan where id=?",keys.getKey()));
    }

    @PutMapping("/itinerary/plans/{id}")
    public ApiResponse<Map<String,Object>> updatePlan(@RequestHeader("Authorization") String authorization,@PathVariable Long id,@RequestBody Map<String,Object> body){long userId=requiredUserId(authorization);ensureOwner(id,userId,"travel_plan","只能编辑自己的行程");jdbc.update("update travel_plan set title=?,destination=?,start_date=?,end_date=?,plan_data=?,is_public=? where id=?",text(body,"title"),text(body,"destination"),body.get("startDate"),body.get("endDate"),json(body.get("planData")),number(body.get("isPublic"),0),id);return ApiResponse.ok(jdbc.queryForMap("select * from travel_plan where id=?",id));}

    @DeleteMapping("/itinerary/plans/{id}")
    public ApiResponse<Boolean> deletePlan(@RequestHeader("Authorization") String authorization,@PathVariable Long id){long userId=requiredUserId(authorization);ensureOwner(id,userId,"travel_plan","只能删除自己的行程");return ApiResponse.ok(jdbc.update("delete from travel_plan where id=?",id)>0);}

    @GetMapping("/itinerary/plans/{id}/share")
    public ApiResponse<Map<String,String>> share(@RequestHeader("Authorization") String authorization,@PathVariable Long id){long userId=requiredUserId(authorization);ensureOwner(id,userId,"travel_plan","只能分享自己的行程");jdbc.update("update travel_plan set is_public=1 where id=?",id);return ApiResponse.ok(Map.of("shareUrl","/api/itinerary/plans/"+id));}

    @GetMapping("/itinerary/plans/{id}/export")
    public ApiResponse<Map<String,String>> export(@RequestHeader("Authorization") String authorization,@PathVariable Long id){long userId=requiredUserId(authorization);ensureOwner(id,userId,"travel_plan","只能导出自己的行程");return ApiResponse.ok(Map.of("fileUrl","/api/itinerary/plans/"+id+"/export","format","json"));}

    @PostMapping("/itinerary/ai/generate")
    public ApiResponse<Map<String,Object>> generate(@RequestHeader("Authorization") String authorization,@RequestBody Map<String,Object> body){requiredUserId(authorization);Map<String,Object> result=new LinkedHashMap<>(aiService.chat("请为"+text(body,"destination")+"生成旅行行程建议", "只返回简洁、结构化的旅行建议。"));result.put("title",text(body,"title"));result.put("destination",text(body,"destination"));return ApiResponse.ok(result);}

    // -------------------- AI 对话 --------------------

    @PostMapping("/chat")
    public ApiResponse<Map<String,Object>> chat(@RequestHeader(value="Authorization",required=false) String authorization,@RequestBody Map<String,Object> body){requiredUserId(authorization);String message=text(body,"message");if(!StringUtils.hasText(message))throw new ApiException(400,"消息不能为空");String session=text(body,"sessionId");Map<String,Object> result=new LinkedHashMap<>(aiService.chat(message,text(body,"systemPrompt")));result.put("sessionId",session);result.put("role","assistant");chatContexts.computeIfAbsent(session,k->new ArrayList<>()).add(Map.of("role","user","content",message));chatContexts.get(session).add(Map.of("role","assistant","content",String.valueOf(result.get("content"))));return ApiResponse.ok(result);}

    @PostMapping("/chat/region/complete")
    public ApiResponse<Map<String,Object>> regionComplete(@RequestHeader("Authorization") String authorization,@RequestBody Map<String,Object> body){requiredUserId(authorization);return ApiResponse.ok(aiService.chat("请补全以下区域："+text(body,"regionText")+"。上下文："+text(body,"surroundingText"),"只返回补全文本。"));}

    @GetMapping("/chat/context/{sessionId}")
    public ApiResponse<Map<String,Object>> context(@RequestHeader("Authorization") String authorization,@PathVariable String sessionId){requiredUserId(authorization);return ApiResponse.ok(Map.of("sessionId",sessionId,"messages",chatContexts.getOrDefault(sessionId,List.of())));}

    @PostMapping("/chat/context/{sessionId}/reset")
    public ApiResponse<Boolean> resetContext(@RequestHeader("Authorization") String authorization,@PathVariable String sessionId){requiredUserId(authorization);chatContexts.remove(sessionId);return ApiResponse.ok(true);}

    @PostMapping(value="/chat/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader("Authorization") String authorization,@RequestBody Map<String,Object> body){requiredUserId(authorization);SseEmitter emitter=new SseEmitter(15000L);new Thread(()->{try{Map<String,Object> result=aiService.chat(text(body,"message"),text(body,"systemPrompt"));emitter.send(SseEmitter.event().name("partial").data(result.get("content")));emitter.send(SseEmitter.event().name("complete").data("__complete__"));emitter.complete();}catch(Exception ex){emitter.completeWithError(ex);}}).start();return emitter;}

    @PostMapping("/ai/hotel/recommend")
    public ApiResponse<Map<String,Object>> hotelRecommend(@RequestHeader("Authorization") String authorization,@RequestBody(required=false) Map<String,Object> body){requiredUserId(authorization);return ApiResponse.ok(Map.of("recommendations",List.of(),"message","暂无可用推荐"));}

    // -------------------- 辅助方法 --------------------

    private Map<String,Object> post(Long id){List<Map<String,Object>> rows=jdbc.queryForList("select * from post where id=? and status=1",id);if(rows.isEmpty())throw new ApiException(404,"游记不存在");return rows.get(0);}
    private boolean liked(long userId,long postId){Integer n=jdbc.queryForObject("select count(*) from post_like where post_id=? and user_id=?",Integer.class,postId,userId);return n!=null&&n>0;}
    private void ensureOwner(long id,long userId,String table,String message){Integer n=jdbc.queryForObject("select count(*) from "+table+" where id=? and user_id=?",Integer.class,id,userId);if(n==null||n==0)throw new ApiException(403,message);}
    private void ensureOwnerOrAdmin(long id,long userId,String table,String message,String authorization){if(!isAdmin(authorization))ensureOwner(id,userId,table,message);}
    private boolean isAdmin(String authorization){String token=token(authorization);return token!=null&&jwt.resolveIdentity(token)== UserIdentity.ADMIN;}
    private long requiredUserId(String authorization){Long id=optionalUserId(authorization);if(id==null)throw new ApiException(401,"unauthorized");return id;}
    private Long optionalUserId(String authorization){String token=token(authorization);return token==null?null:jwt.resolveUserId(token);}
    private String token(String authorization){if(authorization==null||!authorization.startsWith("Bearer "))return null;String value=authorization.substring(7).trim();return value.isEmpty()?null:value;}
    private int positive(String value,int fallback){try{return Math.max(1,Integer.parseInt(value));}catch(Exception e){return fallback;}}
    private int number(Object value,int fallback){try{return value==null?fallback:Integer.parseInt(String.valueOf(value));}catch(Exception e){return fallback;}}
    private String text(Map<String,Object> body,String key){return body==null?"":Objects.toString(body.get(key),"");}
    private String json(Object value){if(value==null)return null;if(value instanceof String)return (String)value;try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new ApiException(400,"JSON 参数格式错误");}}
}
