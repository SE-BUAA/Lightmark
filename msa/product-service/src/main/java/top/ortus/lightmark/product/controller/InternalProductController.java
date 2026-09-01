package top.ortus.lightmark.product.controller;

import org.springframework.web.bind.annotation.*;
import top.ortus.lightmark.common.ApiResponse;
import top.ortus.lightmark.product.dto.ProductDTO;
import top.ortus.lightmark.product.service.FlightProductService;
import java.util.Map;

@RestController
@RequestMapping("/internal/product")
public class InternalProductController {
    private final FlightProductService service;
    public InternalProductController(FlightProductService service) { this.service = service; }
    @GetMapping("/{id}") public ApiResponse<ProductDTO> product(@PathVariable String id) { return ApiResponse.ok(service.product(id)); }
    @PostMapping("/{id}/stock") public ApiResponse<Boolean> stock(@PathVariable long id, @RequestBody Map<String,Object> body) {
        int quantity = Integer.parseInt(String.valueOf(body.getOrDefault("quantity", 0)));
        boolean deduct = !"RELEASE".equalsIgnoreCase(String.valueOf(body.getOrDefault("operation", "DEDUCT")));
        return ApiResponse.ok(service.adjustInventory(id, quantity, deduct));
    }
}
