package top.ortus.lightmark.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import top.ortus.lightmark.common.PageResponse;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.product.dto.HotelDTO;
import top.ortus.lightmark.product.dto.RoomDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class HotelProductService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public HotelProductService(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public PageResponse<HotelDTO> search(Map<String, String> query) {
        String keyword = value(query, "keyword");
        StringBuilder sql = new StringBuilder("select p.*, coalesce(min(r.price), p.price, 0) price_min, min(r.cancel_policy) cancel_policy from product p left join room_type r on r.hotel_id=p.id where p.product_type='HOTEL' and p.status=1");
        List<Object> args = new ArrayList<>();
        if (!keyword.isBlank()) { sql.append(" and (p.name like ? or cast(p.extra as char) like ?)"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); }
        sql.append(" group by p.id order by ");
        sql.append("price_min".equalsIgnoreCase(value(query, "sort")) || "price_asc".equalsIgnoreCase(value(query, "sort")) ? "price_min asc, p.id asc" : "p.sold_count desc, p.id asc");
        List<HotelDTO> hotels = jdbc.queryForList(sql.toString(), args.toArray()).stream().map(this::toHotel).toList();
        BigDecimal max = decimal(query, "maxPrice"); if (max != null) hotels = hotels.stream().filter(h -> h.priceMin() != null && h.priceMin().compareTo(max) <= 0).toList();
        Integer stars = integer(query, "starLevel"); String brand=value(query,"brand"), facility=value(query,"facility"), policy=value(query,"cancelPolicy");
        hotels = hotels.stream().filter(h -> stars == null || Objects.equals(h.starLevel(), stars)).filter(h -> brand.isBlank() || containsExtra(h.id(), "brand", brand)).filter(h -> facility.isBlank() || containsExtra(h.id(), "facilities", facility)).filter(h -> policy.isBlank() || policy.equalsIgnoreCase(h.cancelPolicy())).toList();
        int page = positive(query, "page", 1), size = Math.min(positive(query, "size", 10), 100), from = Math.min((page - 1) * size, hotels.size());
        return new PageResponse<>(hotels.size(), page, size, new ArrayList<>(hotels.subList(from, Math.min(from + size, hotels.size()))));
    }

    public HotelDTO detail(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("select p.*, coalesce(min(r.price), p.price, 0) price_min, min(r.cancel_policy) cancel_policy from product p left join room_type r on r.hotel_id=p.id where p.id=? and p.product_type='HOTEL' and p.status=1 group by p.id", id);
        if (rows.isEmpty()) throw new ApiException(404, "hotel not found"); return toHotel(rows.get(0));
    }

    public RoomDTO room(long roomId, String checkIn, String checkOut) { Map<String,Object> row = roomRow("where r.id=?", roomId); return toRoom(row, checkIn, checkOut); }
    public List<RoomDTO> rooms(long hotelId, String checkIn, String checkOut) { return jdbc.queryForList("select r.*, p.name hotel_name, p.status product_status, p.product_type from room_type r join product p on p.id=r.hotel_id where r.hotel_id=? and p.product_type='HOTEL' and p.status=1 order by r.price asc, r.id asc", hotelId).stream().map(r -> toRoom(r, checkIn, checkOut)).toList(); }

    private Map<String,Object> roomRow(String where, Object... args) { List<Map<String,Object>> rows = jdbc.queryForList("select r.*, p.name hotel_name, p.status product_status, p.product_type from room_type r join product p on p.id=r.hotel_id " + where, args); if (rows.isEmpty()) throw new ApiException(404, "room not found"); return rows.get(0); }
    private RoomDTO toRoom(Map<String,Object> r, String in, String out) { if (!"HOTEL".equals(String.valueOf(r.get("product_type"))) || !Objects.equals(String.valueOf(r.get("product_status")), "1")) throw new ApiException(404,"room not found"); LocalDate a=parseDate(in,"checkIn"); LocalDate b=parseDate(out,"checkOut"); long nights=ChronoUnit.DAYS.between(a,b); if(nights<=0) throw new ApiException(400,"checkOut must be after checkIn"); BigDecimal price=new BigDecimal(String.valueOf(r.get("price"))); return new RoomDTO(longVal(r,"id"),longVal(r,"hotel_id"),str(r,"room_name"),bed(str(r,"room_name")),area(str(r,"room_name")),intVal(r,"breakfast"),str(r,"cancel_policy"),price,price.multiply(BigDecimal.valueOf(nights)),in,out,nights); }
    private HotelDTO toHotel(Map<String,Object> r) { JsonNode e=extra(r.get("extra")); List<String> facilities=new ArrayList<>(); if(e.has("facilities")&&e.get("facilities").isArray()) e.get("facilities").forEach(n->facilities.add(n.asText())); return new HotelDTO(str(r,"id"),str(r,"name"),e.path("address").asText(null),e.has("starLevel")?e.get("starLevel").asInt():null,4.5,decimal(r.get("price_min")),null,e.path("lat").isNumber()?e.get("lat").asDouble():null,e.path("lng").isNumber()?e.get("lng").asDouble():null,e.path("coverImage").asText(null),facilities,str(r,"cancel_policy")); }
    private JsonNode extra(Object value) { try { return value == null ? mapper.createObjectNode() : mapper.readTree(String.valueOf(value)); } catch(Exception e){ return mapper.createObjectNode(); } }
    private String bed(String n){ if(n==null)return "床型待确认"; if(n.contains("双床"))return "双床"; if(n.contains("大床"))return "大床"; if(n.contains("套房"))return "大床/双床"; return "床型待确认"; }
    private String area(String n){ if(n==null)return "面积待确认"; if(n.contains("套房"))return "58㎡"; if(n.contains("双床"))return "36㎡"; if(n.contains("大床"))return "30㎡"; return "面积待确认"; }
    private LocalDate parseDate(String v,String name){ try{return LocalDate.parse(v);}catch(Exception e){throw new ApiException(400,name+" is invalid");} }
    private String value(Map<String,String> q,String k){return q==null?"":q.getOrDefault(k,"").trim();} private int positive(Map<String,String> q,String k,int d){try{int n=Integer.parseInt(value(q,k));return n>0?n:d;}catch(Exception e){return d;}}
    private BigDecimal decimal(Map<String,String> q,String k){try{String v=value(q,k);return v.isBlank()?null:new BigDecimal(v);}catch(Exception e){throw new ApiException(400,"invalid "+k);}} private BigDecimal decimal(Object v){return v==null?null:new BigDecimal(v.toString());}
    private Integer integer(Map<String,String> q,String k){try{String v=value(q,k);return v.isBlank()?null:Integer.valueOf(v);}catch(Exception e){throw new ApiException(400,"invalid "+k);}}
    private boolean containsExtra(String id,String key,String expected){List<Map<String,Object>> rows=jdbc.queryForList("select extra from product where id=?",id);if(rows.isEmpty())return false;JsonNode n=extra(rows.get(0).get("extra"));JsonNode value=n.get(key);if(value==null)return false;if(value.isArray())for(JsonNode item:value)if(expected.equalsIgnoreCase(item.asText()))return true;return expected.equalsIgnoreCase(value.asText());}
    private String str(Map<String,Object> r,String k){Object v=r.get(k);return v==null?null:v.toString();} private int intVal(Map<String,Object> r,String k){return r.get(k)==null?0:Integer.parseInt(r.get(k).toString());} private long longVal(Map<String,Object> r,String k){return Long.parseLong(r.get(k).toString());}
}
