package top.ortus.lightmark.product.dto;

import java.math.BigDecimal;
import java.util.Map;

public record VacationDTO(String id, String name, BigDecimal price, int stock,
                          int soldCount, Map<String, Object> extra, Object categoryTags) { }
