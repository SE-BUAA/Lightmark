package top.ortus.lightmark.product.controller;

import org.springframework.web.bind.annotation.*;
import top.ortus.lightmark.common.ApiResponse;
import top.ortus.lightmark.common.PageResponse;
import top.ortus.lightmark.product.dto.ProductDTO;
import top.ortus.lightmark.product.service.FlightProductService;
import top.ortus.lightmark.product.service.HotelProductService;
import top.ortus.lightmark.product.dto.HotelDTO;
import top.ortus.lightmark.product.dto.RoomDTO;
import top.ortus.lightmark.product.dto.VacationDTO;
import top.ortus.lightmark.product.service.VacationProductService;
import top.ortus.lightmark.product.service.TrainProductService;
import top.ortus.lightmark.product.dto.TrainTicketDTO;
import top.ortus.lightmark.common.exception.ApiException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final FlightProductService service;
    private final HotelProductService hotelService;
    private final VacationProductService vacationService;
    private final TrainProductService trainService;
    public ProductController(FlightProductService service, HotelProductService hotelService, VacationProductService vacationService, TrainProductService trainService) { this.service = service; this.hotelService = hotelService; this.vacationService = vacationService; this.trainService = trainService; }
    @GetMapping("/flights/search") public ApiResponse<PageResponse<ProductDTO>> search(@RequestParam Map<String,String> params) { return ApiResponse.ok(service.search(params)); }
    @GetMapping("/flights/{id}") public ApiResponse<ProductDTO> detail(@PathVariable String id) { return ApiResponse.ok(service.detail(id)); }
    @GetMapping("/flights/price-calendar") public ApiResponse<Map<String,Object>> calendar(@RequestParam Map<String,String> params) { return ApiResponse.ok(service.priceCalendar(params)); }
    @GetMapping("/products") public ApiResponse<PageResponse<ProductDTO>> products(@RequestParam Map<String,String> params) { return ApiResponse.ok(service.products(params)); }
    @GetMapping("/products/{id}") public ApiResponse<ProductDTO> product(@PathVariable String id) { return ApiResponse.ok(service.product(id)); }
    @PostMapping("/internal/product/{id}/stock") public ApiResponse<Boolean> stock(@PathVariable String id, @RequestBody Map<String,Object> body) {
        long productId;
        try { productId = Long.parseLong(id); }
        catch (NumberFormatException ex) { throw new ApiException(400, "productId is invalid"); }
        if (body == null) throw new ApiException(400, "stock adjustment is required");
        Object deltaValue = body.get("delta");
        if (deltaValue != null) {
            int delta;
            try { delta = Integer.parseInt(String.valueOf(deltaValue)); }
            catch (NumberFormatException ex) { throw new ApiException(400, "delta is invalid"); }
            if (delta == 0) throw new ApiException(400, "delta must not be zero");
            return ApiResponse.ok(service.adjustInventory(productId, Math.abs(delta), delta < 0));
        }
        int quantity;
        try { quantity = Integer.parseInt(String.valueOf(body.getOrDefault("quantity", 0))); }
        catch (NumberFormatException ex) { throw new ApiException(400, "quantity is invalid"); }
        boolean deduct = !"RELEASE".equalsIgnoreCase(String.valueOf(body.getOrDefault("operation", "DEDUCT")));
        return ApiResponse.ok(service.adjustInventory(productId, quantity, deduct));
    }
    @GetMapping("/internal/product/{id}") public ApiResponse<ProductDTO> internalProduct(@PathVariable String id) { return ApiResponse.ok(service.product(id)); }
    @GetMapping("/hotel/list") public ApiResponse<PageResponse<HotelDTO>> hotels(@RequestParam Map<String,String> params) { return ApiResponse.ok(hotelService.search(params)); }
    @GetMapping("/hotels/search") public ApiResponse<PageResponse<HotelDTO>> hotelsLegacy(@RequestParam Map<String,String> params) { return ApiResponse.ok(hotelService.search(params)); }
    @GetMapping("/hotel/{id}") public ApiResponse<HotelDTO> hotel(@PathVariable long id) { return ApiResponse.ok(hotelService.detail(id)); }
    @GetMapping("/hotel/room/{id}") public ApiResponse<RoomDTO> room(@PathVariable long id, @RequestParam String checkIn, @RequestParam String checkOut) { return ApiResponse.ok(hotelService.room(id, checkIn, checkOut)); }
    @GetMapping("/hotel/{id}/rooms") public ApiResponse<List<RoomDTO>> rooms(@PathVariable long id, @RequestParam String checkIn, @RequestParam String checkOut) { return ApiResponse.ok(hotelService.rooms(id, checkIn, checkOut)); }
    @GetMapping("/vacations") public ApiResponse<List<VacationDTO>> vacations(@RequestParam Map<String,String> params) { return ApiResponse.ok(vacationService.search(params)); }
    @GetMapping("/vacations/{id}") public ApiResponse<VacationDTO> vacation(@PathVariable long id) { return ApiResponse.ok(vacationService.detail(id)); }
    @GetMapping("/vacations/options") public ApiResponse<Map<String,List<String>>> vacationOptions() { return ApiResponse.ok(vacationService.options()); }
    @PostMapping("/trains/search") public ApiResponse<List<TrainTicketDTO>> trains(@RequestBody Map<String,Object> body) { return ApiResponse.ok(trainService.search(body, false)); }
    @GetMapping("/trains/search") public ApiResponse<List<TrainTicketDTO>> trainsGet(@RequestParam Map<String,String> params) { Map<String,Object> body=new java.util.HashMap<>(); body.putAll(params); return ApiResponse.ok(trainService.search(body, false)); }
    @PostMapping("/trains/transfer/search") public ApiResponse<List<TrainTicketDTO>> trainTransfers(@RequestBody Map<String,Object> body) { return ApiResponse.ok(trainService.search(body, true)); }
    @PostMapping("/trains/calendar") public ApiResponse<List<Map<String,Object>>> trainCalendar(@RequestBody Map<String,Object> body) { return ApiResponse.ok(trainService.calendar(body)); }
    @GetMapping("/trains/options") public ApiResponse<Map<String,Object>> trainOptions() { return ApiResponse.ok(trainService.options()); }
    @GetMapping("/trains/detail/{id}") public ApiResponse<TrainTicketDTO> trainDetail(@PathVariable String id) { return ApiResponse.ok(trainService.detail(id)); }
    @GetMapping("/trains/{id}") public ApiResponse<TrainTicketDTO> trainDetailLegacy(@PathVariable String id) { return ApiResponse.ok(trainService.detail(id)); }
    @PostMapping("/vacations/search") public ApiResponse<List<VacationDTO>> vacationSearch(@RequestBody(required=false) Map<String,String> body) { return ApiResponse.ok(vacationService.search(body == null ? Map.of() : body)); }
    @GetMapping("/legacy/vacations/search") public ApiResponse<List<VacationDTO>> vacationSearchLegacy(@RequestParam Map<String,String> params) { return ApiResponse.ok(vacationService.search(params)); }
    @PostMapping("/products/{id}/views") public ApiResponse<Void> view(@PathVariable long id, @RequestBody(required=false) Map<String,Object> body) { Long userId=null; if(body!=null&&body.get("userId")!=null) userId=Long.valueOf(String.valueOf(body.get("userId"))); service.recordView(id,userId,body==null?null:String.valueOf(body.get("source"))); return ApiResponse.ok(null); }
    @GetMapping("/products/{id}/views") public ApiResponse<PageResponse<Map<String,Object>>> views(@PathVariable long id, @RequestParam(required=false) Long userId, @RequestParam Map<String,String> params) { return ApiResponse.ok(service.views(id,userId,params)); }
    @GetMapping("/admin/products") public ApiResponse<PageResponse<Map<String,Object>>> adminProducts(@RequestParam Map<String,String> params){return ApiResponse.ok(service.adminProducts(params));}
    @PostMapping("/admin/products") public ApiResponse<Map<String,Object>> create(@RequestBody Map<String,Object> body){return ApiResponse.ok(service.createProduct(body));}
    @PutMapping("/admin/products/{id}/status") public ApiResponse<Boolean> status(@PathVariable long id,@RequestBody Map<String,Object> body){return ApiResponse.ok(service.updateProduct(id,"status",body.get("status")));}
    @PutMapping("/admin/products/{id}/price") public ApiResponse<Boolean> price(@PathVariable long id,@RequestBody Map<String,Object> body){return ApiResponse.ok(service.updateProduct(id,"price",body.get("price")));}
    @PutMapping("/admin/products/{id}/stock") public ApiResponse<Boolean> stockAdmin(@PathVariable long id,@RequestBody Map<String,Object> body){return ApiResponse.ok(service.updateProduct(id,"stock",body.get("stock")));}
    @DeleteMapping("/admin/products/{id}") public ApiResponse<Boolean> delete(@PathVariable long id){return ApiResponse.ok(service.deleteProduct(id));}
}
