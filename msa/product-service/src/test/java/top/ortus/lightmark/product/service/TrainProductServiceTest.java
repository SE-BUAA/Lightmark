package top.ortus.lightmark.product.service;
import com.fasterxml.jackson.databind.ObjectMapper; import org.junit.jupiter.api.Test; import org.springframework.web.client.RestClient; import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
class TrainProductServiceTest { @Test void returnsOptionsAndRejectsMissingStations(){ var s=new TrainProductService(RestClient.builder(),new ObjectMapper(),"http://127.0.0.1:1"); assertThat(s.options()).containsKey("stations"); assertThat(s.search(Map.of(),false)).isEmpty(); } }
