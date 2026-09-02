package top.ortus.lightmark.common;

import org.junit.jupiter.api.Test;
import top.ortus.lightmark.common.security.JwtTokenService;
import top.ortus.lightmark.common.security.UserIdentity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void rejectsShortSecretAndTamperedOrWrongIssuerToken() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenService("too-short", "lightmark", 120));

        JwtTokenService service = new JwtTokenService(
                "lightmark-secret-key-please-change-123456",
                "lightmark",
                120
        );
        String token = service.createToken(2L, "user", List.of("USER"));
        assertFalse(service.isValid(token.substring(0, token.length() - 1) + "x"));
        JwtTokenService differentIssuer = new JwtTokenService(
                "lightmark-secret-key-please-change-123456",
                "other-issuer",
                120
        );
        assertFalse(differentIssuer.isValid(token));
        assertNull(service.resolveUserId("not-a-jwt"));
    }
}
