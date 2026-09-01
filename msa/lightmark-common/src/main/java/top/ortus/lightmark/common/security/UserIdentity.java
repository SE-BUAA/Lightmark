package top.ortus.lightmark.common.security;

public enum UserIdentity {
    ADMIN,
    USER;

    public static UserIdentity fromRoleName(String roleName) {
        if (roleName == null) {
            return USER;
        }
        String normalized = roleName.trim().toUpperCase();
        if ("ADMIN".equals(normalized)) {
            return ADMIN;
        }
        return USER;
    }
}
