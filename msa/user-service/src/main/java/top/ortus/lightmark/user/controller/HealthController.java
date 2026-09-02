package top.ortus.lightmark.user.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import top.ortus.lightmark.common.ApiResponse;

import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final String version;

    public HealthController(JdbcTemplate jdbcTemplate,
                            @Value("${app.version:unknown}") String version) {
        this.jdbcTemplate = jdbcTemplate;
        this.version = version;
    }

    @GetMapping("/api/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("service", "user-service", "status", "UP"));
    }

    /** Readiness checks the dependency without changing any database state. */
    @GetMapping({"/api/ready", "/api/readiness"})
    public ResponseEntity<ApiResponse<Map<String, String>>> readiness() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "service", "user-service",
                    "status", "UP",
                    "database", "UP")));
        } catch (DataAccessException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(503, "database is not ready"));
        }
    }

    @GetMapping("/api/version")
    public ApiResponse<Map<String, String>> version() {
        return ApiResponse.ok(Map.of("service", "user-service", "version", version));
    }
}
