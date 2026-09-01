package top.ortus.lightmark.user.service;

import org.junit.jupiter.api.Test;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.user.dao.PointsLogRepository;
import top.ortus.lightmark.user.dao.UserRepositoryImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PointsServiceTest {
    @Test
    void changesPointsAndIsIdempotentForSameOrder() {
        UserRepositoryImpl users = mock(UserRepositoryImpl.class);
        PointsLogRepository logs = mock(PointsLogRepository.class);
        PointsService service = new PointsService(users, logs);
        when(users.changePoints("1", 20)).thenReturn(1);
        when(users.countPointsLog("1", "order-1", "ORDER")).thenReturn(0);

        assertEquals(true, service.change("1", 20, 1, "ORDER", "order-1"));
        verify(users).insertPointsLog("1", 1, 20, "ORDER", "order-1");

        when(users.countPointsLog("1", "order-1", "ORDER")).thenReturn(1);
        assertEquals(true, service.change("1", 20, 1, "ORDER", "order-1"));
        verify(users, times(1)).changePoints("1", 20);
    }

    @Test
    void rejectsUnauthorizedAndInvalidChanges() {
        PointsService service = new PointsService(mock(UserRepositoryImpl.class), mock(PointsLogRepository.class));
        assertEquals(401, assertThrows(ApiException.class, () -> service.logs("0")).getCode());
        assertEquals(400, assertThrows(ApiException.class, () -> service.change("1", 0, 1, "ORDER", null)).getCode());
    }
}
