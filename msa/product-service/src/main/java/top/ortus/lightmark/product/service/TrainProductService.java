package top.ortus.lightmark.product.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import top.ortus.lightmark.product.dto.TrainTicketDTO;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class TrainProductService {
    private final RestClient client; private final ObjectMapper mapper;
    public TrainProductService(RestClient.Builder builder, ObjectMapper mapper, @Value("${train.mcp-url:http://150.230.223.11:9000/mcp}") String url) { this.client=builder.baseUrl(url).build(); this.mapper=mapper; }
    public List<TrainTicketDTO> search(Map<String,Object> body, boolean transfer) { if(body==null||blank(body,"startStation")||blank(body,"endStation")) return List.of(); String date=String.valueOf(body.getOrDefault("date", LocalDate.now())); Map<String,Object> args=new LinkedHashMap<>(); args.put("from_station",body.get("startStation"));args.put("to_station",body.get("endStation"));args.put("train_date",date); if(transfer)args.put("middle_station",""); Map<String,Object> response=call(transfer?"query-transfer":"query-tickets",args); return tickets(response); }
    public List<Map<String,Object>> calendar(Map<String,Object> body){ if(body==null||blank(body,"startStation")||blank(body,"endStation")||blank(body,"month"))return List.of(); YearMonth month=YearMonth.parse(String.valueOf(body.get("month"))); List<Map<String,Object>> out=new ArrayList<>(); for(int i=1;i<=month.lengthOfMonth();i++){Map<String,Object> req=new HashMap<>(body);req.put("date",month.atDay(i).toString());List<TrainTicketDTO> t=search(req,false);if(!t.isEmpty())out.add(Map.of("date",req.get("date"),"ticketCount",t.stream().mapToInt(x->x.stock()==null?0:x.stock()).sum(),"trainCount",t.size()));} return out; }
    public Map<String,Object> options(){return Map.of("stations",List.of("北京","上海","广州","深圳","杭州","南京","成都","武汉"),"trainTypes",List.of("高铁","动车","普速"),"seatTypes",List.of("商务座","一等座","二等座","软卧","硬卧","硬座"));}
    public TrainTicketDTO detail(String id){try{return mapper.readValue(new String(Base64.getUrlDecoder().decode(id.startsWith("MCP:")?id.substring(4):id)),TrainTicketDTO.class);}catch(Exception e){throw new IllegalArgumentException("invalid train ticket");}}
    private Map<String,Object> call(String name,Map<String,Object> args){try{Map<String,Object> body=Map.of("jsonrpc","2.0","id",System.nanoTime(),"method","tools/call","params",Map.of("name",name,"arguments",args));String raw=client.post().contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);JsonNode root=mapper.readTree(raw);String text=root.at("/result/content/0/text").asText("");return mapper.readValue(text,new TypeReference<>(){});}catch(Exception e){return Map.of("success",false);}}
    private List<TrainTicketDTO> tickets(Map<String,Object> response){Object rows=response.get("tickets");if(!(rows instanceof List<?>))rows=response.get("trains");if(!(rows instanceof List<?> list))return List.of();List<TrainTicketDTO> out=new ArrayList<>();for(Object row:list){if(row instanceof Map<?,?> m){Map<String,Object>x=new LinkedHashMap<>();m.forEach((k,v)->x.put(String.valueOf(k),v));out.add(new TrainTicketDTO(String.valueOf(x.getOrDefault("id",x.getOrDefault("train_no",""))),String.valueOf(x.getOrDefault("name",x.getOrDefault("train_no",""))),number(x.get("price")),integer(x.get("stock")),integer(x.get("soldCount")),List.of(),x,Map.of(),Map.of()));}}return out;}
    private boolean blank(Map<String,Object>b,String k){return b.get(k)==null||String.valueOf(b.get(k)).isBlank();} private Double number(Object x){try{return x==null?null:Double.valueOf(x.toString());}catch(Exception e){return null;}} private Integer integer(Object x){try{return x==null?0:Integer.valueOf(x.toString());}catch(Exception e){return 0;}}
}
