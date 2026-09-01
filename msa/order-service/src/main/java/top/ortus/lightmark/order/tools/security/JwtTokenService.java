package top.ortus.lightmark.order.tools.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

public class JwtTokenService {

    private final SecretKey secretKey;
    private final String issuer;
    private final long expireMinutes;

    public JwtTokenService(String secret, String issuer, long expireMinutes) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.expireMinutes = expireMinutes;
    }

    public String createToken(Long userId, String nickname, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expireMinutes, ChronoUnit.MINUTES)))
                .claim("nickname", nickname)
                .claim("roles", roles)
                .claim("identity", resolveIdentityFromRoles(roles).name())
                .signWith(secretKey)
                .compact();
    }

    public String createToken(Long userId, String nickname, UserIdentity identity) {
        UserIdentity safeIdentity = identity == null ? UserIdentity.USER : identity;
        return createToken(userId, nickname, List.of(safeIdentity.name()));
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long resolveUserId(String token) {
        try {
            Claims claims = parseToken(token);
            return Long.valueOf(claims.getSubject());
        } catch (Exception ex) {
            return null;
        }
    }

    public List<String> resolveRoles(String token) {
        try {
            Claims claims = parseToken(token);
            Object roles = claims.get("roles");
            if (roles instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
            return List.of();
        } catch (Exception ex) {
            return List.of();
        }
    }

    public UserIdentity resolveIdentity(String token) {
        try {
            Claims claims = parseToken(token);
            Object identity = claims.get("identity");
            if (identity != null) {
                return UserIdentity.fromRoleName(String.valueOf(identity));
            }
            return resolveIdentityFromRoles(resolveRoles(token));
        } catch (Exception ex) {
            return UserIdentity.USER;
        }
    }

    private UserIdentity resolveIdentityFromRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return UserIdentity.USER;
        }
        for (String role : roles) {
            if (UserIdentity.fromRoleName(role) == UserIdentity.ADMIN) {
                return UserIdentity.ADMIN;
            }
        }
        return UserIdentity.fromRoleName(roles.get(0));
    }
}
