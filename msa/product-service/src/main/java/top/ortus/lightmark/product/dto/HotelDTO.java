package top.ortus.lightmark.product.dto;

import java.math.BigDecimal;
import java.util.List;

public record HotelDTO(String id, String name, String address, Integer starLevel, Double rating,
                       BigDecimal priceMin, Double distance, Double lat, Double lng,
                       String coverImage, List<String> facilities, String cancelPolicy) { }
