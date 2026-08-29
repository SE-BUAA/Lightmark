package top.ortus.lightmark.common;

import org.junit.jupiter.api.Test;
import top.ortus.lightmark.common.security.JwtTokenService;
import top.ortus.lightmark.common.security.UserIdentity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtTokenServiceTest {

    @Test
    void createAndResolveTokenShouldRoundTrip() {
        JwtTokenService service = new JwtTokenService(
                "lightmark-secret-key-please-change-123456",
                "lightmark",
                120
        );

        String token = service.createToken(2L, "普通用户", List.of("USER"));

        assertEquals(2L, service.resolveUserId(token));
        assertEquals(List.of("USER"), service.resolveRoles(token));
        assertEquals(UserIdentity.USER, service.resolveIdentity(token));
    }
}
