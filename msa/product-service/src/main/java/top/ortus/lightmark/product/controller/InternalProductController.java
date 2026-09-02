package top.ortus.lightmark.product.controller;

import org.springframework.web.bind.annotation.*;
import top.ortus.lightmark.common.ApiResponse;
import top.ortus.lightmark.product.dto.ProductDTO;
import top.ortus.lightmark.product.service.FlightProductService;
import top.ortus.lightmark.common.exception.ApiException;
import java.util.Map;

@RestController
@RequestMapping("/internal/product")
public class InternalProductController {
    private final FlightProductService service;
    public InternalProductController(FlightProductService service) { this.service = service; }
    @GetMapping("/{id}") public ApiResponse<ProductDTO> product(@PathVariable String id) { return ApiResponse.ok(service.product(id)); }
    @PostMapping("/{id}/stock") public ApiResponse<Boolean> stock(@PathVariable String id, @RequestBody Map<String,Object> body) {
        long productId;
        try {
            productId = Long.parseLong(id);
        } catch (NumberFormatException ex) {
            throw new ApiException(400, "productId is invalid");
        }
        if (body == null) {
            throw new ApiException(400, "stock adjustment is required");
        }
        Object deltaValue = body.get("delta");
        if (deltaValue != null) {
            int delta;
            try {
                delta = Integer.parseInt(String.valueOf(deltaValue));
            } catch (NumberFormatException ex) {
                throw new ApiException(400, "delta is invalid");
            }
            if (delta == 0) throw new ApiException(400, "delta must not be zero");
            return ApiResponse.ok(service.adjustInventory(productId, Math.abs(delta), delta < 0));
        }
        int quantity;
        try {
            quantity = Integer.parseInt(String.valueOf(body.getOrDefault("quantity", 0)));
        } catch (NumberFormatException ex) {
            throw new ApiException(400, "quantity is invalid");
        }
        boolean deduct = !"RELEASE".equalsIgnoreCase(String.valueOf(body.getOrDefault("operation", "DEDUCT")));
        return ApiResponse.ok(service.adjustInventory(productId, quantity, deduct));
    }
}
