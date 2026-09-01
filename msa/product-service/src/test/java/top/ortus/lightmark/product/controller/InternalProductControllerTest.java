package top.ortus.lightmark.product.controller;

import org.junit.jupiter.api.Test;
import top.ortus.lightmark.product.service.FlightProductService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InternalProductControllerTest {
    private final FlightProductService service = mock(FlightProductService.class);
    private final InternalProductController controller = new InternalProductController(service);

    @Test
    void mapsNegativeDeltaToStockDeduction() {
        when(service.adjustInventory(42L, 2, true)).thenReturn(true);

        assertThat(controller.stock("42", Map.of("delta", -2)).getData()).isTrue();

        verify(service).adjustInventory(42L, 2, true);
    }

    @Test
    void mapsPositiveDeltaToStockRelease() {
        when(service.adjustInventory(42L, 3, false)).thenReturn(true);

        assertThat(controller.stock("42", Map.of("delta", 3)).getData()).isTrue();

        verify(service).adjustInventory(42L, 3, false);
    }
}
