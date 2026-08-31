package top.ortus.lightmark.product.dto;

import java.util.List;
import java.util.Map;

public record TrainTicketDTO(String id, String name, Double price, Integer stock,
                             Integer soldCount, List<String> categoryTags,
                             Map<String, Object> extra, Map<String, Integer> seats,
                             Map<String, Double> prices) { }
