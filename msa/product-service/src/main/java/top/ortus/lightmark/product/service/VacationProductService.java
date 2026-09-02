package top.ortus.lightmark.product.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.product.dto.VacationDTO;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VacationProductService {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper;
    public VacationProductService(JdbcTemplate jdbc, ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    public List<VacationDTO> search(Map<String,String> q){ return all().stream().filter(v->eq(v,"destination",q,"destination")).filter(v->eq(v,"depart_city",q,"departCity")).filter(v->eq(v,"date",q,"date")).filter(v->range(v,"days",q,"minDays",q,"maxDays")).filter(v->price(v,q)).filter(v->tags(v,q.get("tags"))).toList(); }
    public VacationDTO detail(long id){ List<VacationDTO> r=jdbc.queryForList("select * from product where id=? and product_type='VACATION' and status=1",id).stream().map(this::map).toList(); if(r.isEmpty())throw new ApiException(404,"vacation not found"); return r.get(0); }
    public Map<String,List<String>> options(){ List<VacationDTO> vs=all(); return Map.of("destinations",values(vs,"destination"),"departCities",values(vs,"depart_city"),"dates",values(vs,"date"),"tags",vs.stream().flatMap(v->tagsList(v).stream()).distinct().sorted().toList()); }
    private List<VacationDTO> all(){return jdbc.queryForList("select * from product where product_type='VACATION' and status=1 order by id limit 200").stream().map(this::map).toList();}
    private VacationDTO map(Map<String,Object> r){return new VacationDTO(String.valueOf(r.get("id")),String.valueOf(r.get("name")),new BigDecimal(String.valueOf(r.get("price"))),num(r,"stock"),num(r,"sold_count"),json(r.get("extra")),jsonValue(r.get("category_tags")));}
    private Map<String,Object> json(Object v){try{return v==null?Map.of():mapper.readValue(String.valueOf(v),new TypeReference<>(){});}catch(Exception e){return Map.of();}}
    private Object jsonValue(Object v){try{return v==null?List.of():mapper.readValue(String.valueOf(v),Object.class);}catch(Exception e){return v;}}
    private boolean eq(VacationDTO v,String key,Map<String,String> q,String param){String x=q.get(param);return x==null||x.isBlank()||x.equals(String.valueOf(v.extra().get(key)));}
    private boolean range(VacationDTO v,String key,Map<String,String> q,String min,Map<String,String> q2,String max){int n=number(v.extra().get(key));return (q.get(min)==null||n>=number(q.get(min)))&&(q2.get(max)==null||n<=number(q2.get(max)));}
    private boolean price(VacationDTO v,Map<String,String> q){try{return (q.get("minPrice")==null||v.price().doubleValue()>=Double.parseDouble(q.get("minPrice")))&&(q.get("maxPrice")==null||v.price().doubleValue()<=Double.parseDouble(q.get("maxPrice")));}catch(Exception e){throw new ApiException(400,"invalid price");}}
    private boolean tags(VacationDTO v,String raw){if(raw==null||raw.isBlank())return true;Set<String>want=Arrays.stream(raw.split(",")).map(String::trim).collect(Collectors.toSet());return want.stream().anyMatch(tagsList(v)::contains);}
    private List<String> tagsList(VacationDTO v){Object x=v.categoryTags();if(x instanceof List<?> l)return l.stream().map(String::valueOf).toList();return List.of();}
    private List<String> values(List<VacationDTO> vs,String key){return vs.stream().map(v->String.valueOf(v.extra().get(key))).filter(x->!"null".equals(x)).distinct().sorted().toList();}
    private int num(Map<String,Object> r,String k){return number(r.get(k));} private int number(Object x){try{return Integer.parseInt(String.valueOf(x).replaceAll("\\D.*", ""));}catch(Exception e){return 0;}}
}
