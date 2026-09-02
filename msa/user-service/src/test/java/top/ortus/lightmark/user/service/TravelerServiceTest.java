package top.ortus.lightmark.user.service;

import org.junit.jupiter.api.Test;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.user.dao.TravelerRepository;
import top.ortus.lightmark.user.dto.module.TravelerDTO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TravelerServiceTest {
    @Test
    void createsUpdatesAndDeletesOwnedTraveler() {
        TravelerRepository repository = mock(TravelerRepository.class);
        TravelerService service = new TravelerService(repository);
        TravelerDTO request = new TravelerDTO();
        request.setName("Alice");
        request.setId_card("110101199001010011");
        when(repository.findByUserId("1")).thenReturn(java.util.List.of(request));
        when(repository.findOwned("9", "1")).thenReturn(request);
        when(repository.delete("9", "1")).thenReturn(1);

        assertEquals(request, service.create("1", request));
        assertEquals(request, service.update("1", "9", request));
        assertEquals(true, service.delete("1", "9"));
        verify(repository).insert(request);
        verify(repository).update(request);
    }

    @Test
    void rejectsMissingTravelerIdentityAndUnknownOwner() {
        TravelerService service = new TravelerService(mock(TravelerRepository.class));
        assertEquals(400, assertThrows(ApiException.class,
                () -> service.create("1", new TravelerDTO())).getCode());
        TravelerRepository repository = mock(TravelerRepository.class);
        when(repository.findOwned("9", "1")).thenReturn(null);
        assertEquals(404, assertThrows(ApiException.class,
                () -> new TravelerService(repository).delete("1", "9")).getCode());
    }
}
