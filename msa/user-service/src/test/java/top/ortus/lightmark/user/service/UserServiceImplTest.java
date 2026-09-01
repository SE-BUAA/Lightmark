package top.ortus.lightmark.user.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.user.dao.User;
import top.ortus.lightmark.user.dao.UserRepositoryImpl;
import top.ortus.lightmark.user.dto.UserDTO;
import top.ortus.lightmark.user.dto.user.UserCreateRequest;
import top.ortus.lightmark.user.dto.user.UserUpdateRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceImplTest {
    @Test
    void createsUserWithEmailDefaults() {
        UserRepositoryImpl repository = mock(UserRepositoryImpl.class);
        User stored = user("9", "a@qq.com", "Alice", "plain");
        when(repository.findByEmail("a@qq.com")).thenReturn(stored);
        UserCreateRequest request = new UserCreateRequest();
        request.setEmail("a@qq.com");
        request.setPassword("plain");
        request.setNickname("Alice");

        UserDTO result = new UserServiceImpl(repository).create(request);

        assertEquals("9", result.getId());
        assertEquals("EMAIL", result.getRegister_source());
        verify(repository).insert(any(User.class));
    }

    @Test
    void updatesPasswordOnlyAfterOldPasswordMatches() {
        UserRepositoryImpl repository = mock(UserRepositoryImpl.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User stored = user("9", "a@qq.com", "Alice", encoder.encode("OldPassword!1"));
        when(repository.findById("9")).thenReturn(stored);
        when(repository.update(any(User.class), anySet())).thenReturn(1);
        UserServiceImpl service = new UserServiceImpl(repository);

        assertEquals(400, assertThrows(ApiException.class,
                () -> service.updatePassword("9", "wrong", "NewPassword!1")).getCode());
        assertEquals(true, service.updatePassword("9", "OldPassword!1", "NewPassword!1"));
        verify(repository).update(any(User.class), anySet());
    }

    @Test
    void rejectsInvalidCreateAndUnauthorizedPasswordRequests() {
        UserServiceImpl service = new UserServiceImpl(mock(UserRepositoryImpl.class));
        assertThrows(IllegalArgumentException.class, () -> service.create(new UserCreateRequest()));
        assertEquals(401, assertThrows(ApiException.class,
                () -> service.updatePassword("0", "OldPassword!1", "NewPassword!1")).getCode());
    }

    private User user(String id, String email, String nickname, String password) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setNickname(nickname);
        user.setPassword(password);
        user.setAvatar("");
        user.setPoints(0);
        user.setLevel((short) 0);
        user.setStatus(0);
        user.setRegister_source("EMAIL");
        user.setDeleted(false);
        assertNotNull(user.getId());
        return user;
    }
}
