package top.ortus.lightmark.backend.controller;

import org.junit.jupiter.api.Test;
import top.ortus.lightmark.backend.common.ApiResponse;
import top.ortus.lightmark.backend.service.GenericCrudService;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrudControllerTest {

    @Test
    void deleteShouldUseParamsWhenPayloadIsNullOrEmpty() {
        GenericCrudService service = mock(GenericCrudService.class);
        when(service.delete(eq("user"), any())).thenReturn(true);

        CrudController controller = new CrudController(service);

        ApiResponse<Boolean> resp1 = controller.delete("user", null, Map.of("id", "2"));
        assertThat(resp1.getCode()).isEqualTo(0);
        assertThat(resp1.getData()).isTrue();
        verify(service).delete("user", Map.of("id", "2"));

        ApiResponse<Boolean> resp2 = controller.delete("user", Map.of(), Map.of("id", "2"));
        assertThat(resp2.getCode()).isEqualTo(0);
        assertThat(resp2.getData()).isTrue();
    }

    @Test
    void deleteShouldUsePayloadWhenPayloadProvided() {
        GenericCrudService service = mock(GenericCrudService.class);
        when(service.delete(eq("user"), any())).thenReturn(true);

        CrudController controller = new CrudController(service);

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", 2);
        ApiResponse<Boolean> resp = controller.delete("user", payload, Map.of("id", "999"));

        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData()).isTrue();
        verify(service).delete("user", payload);
    }

    @Test
    void deleteShouldReturnFalseWhenServiceReturnsFalseAndCallOnce() {
        GenericCrudService service = mock(GenericCrudService.class);
        when(service.delete(eq("user"), any())).thenReturn(false);
        CrudController controller = new CrudController(service);

        ApiResponse<Boolean> resp = controller.delete("user", null, Map.of("id", "2"));

        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData()).isFalse();
        verify(service, times(1)).delete("user", Map.of("id", "2"));
    }

    @Test
    void deleteShouldPropagateServiceException() {
        GenericCrudService service = mock(GenericCrudService.class);
        when(service.delete(eq("user"), any())).thenThrow(new IllegalArgumentException("bad request"));
        CrudController controller = new CrudController(service);

        assertThatThrownBy(() -> controller.delete("user", null, Map.of("id", "2")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("bad request");
    }

    @Test
    void listGetCreateAndUpdateShouldDelegateToService() {
        GenericCrudService service = mock(GenericCrudService.class);
        CrudController controller = new CrudController(service);
        Map<String, Object> row = Map.of("id", 1, "name", "测试");
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "测试");
        when(service.list("product", Map.of("status", "1"))).thenReturn(java.util.List.of(row));
        when(service.getById("product", "1")).thenReturn(row);
        when(service.create("product", payload)).thenReturn(row);
        when(service.update("product", payload)).thenReturn(row);

        assertThat(controller.list("product", Map.of("status", "1")).getData().getTotal()).isEqualTo(1);
        assertThat(controller.getById("product", "1").getData()).isEqualTo(row);
        assertThat(controller.create("product", payload).getData()).isEqualTo(row);
        assertThat(controller.update("product", payload).getData()).isEqualTo(row);

        verify(service).list("product", Map.of("status", "1"));
        verify(service).getById("product", "1");
        verify(service).create("product", payload);
        verify(service).update("product", payload);
    }
}

