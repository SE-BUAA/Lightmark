package top.ortus.lightmark.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiResponseTest {

    @Test
    void okShouldCreateSuccessPayload() {
        ApiResponse<String> response = ApiResponse.ok("ok");

        assertEquals(0, response.getCode());
        assertEquals("success", response.getMsg());
        assertEquals("ok", response.getData());
    }

    @Test
    void errorShouldCreateErrorPayload() {
        ApiResponse<Void> response = ApiResponse.error(400, "bad request");

        assertEquals(400, response.getCode());
        assertEquals("bad request", response.getMsg());
        assertNull(response.getData());
    }
}
