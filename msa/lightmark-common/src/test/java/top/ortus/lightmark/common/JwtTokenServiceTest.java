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
        // 篡改 payload 文本任意一字符（HMAC 是对 header.payload 原文计算的，必定验签失败）。
        // 注意：不能只改签名段最后一个字符——base64 解码会忽略末尾填充位，
        // 若替换字符与原字符的有效位相同，解码出的签名不变，token 仍会被判定有效。
        String[] jwtParts = token.split("\\.");
        char lastPayloadChar = jwtParts[1].charAt(jwtParts[1].length() - 1);
        char replacement = lastPayloadChar == 'A' ? 'B' : 'A';
        String tamperedPayload = jwtParts[1].substring(0, jwtParts[1].length() - 1) + replacement;
        String tampered = jwtParts[0] + "." + tamperedPayload + "." + jwtParts[2];
        assertFalse(service.isValid(tampered));
        JwtTokenService differentIssuer = new JwtTokenService(
                "lightmark-secret-key-please-change-123456",
                "other-issuer",
                120
        );
        assertFalse(differentIssuer.isValid(token));
        assertNull(service.resolveUserId("not-a-jwt"));
    }
}
