package top.ortus.lightmark.user.utils;

public final class UserIdFormatter {

    private UserIdFormatter() {
    }

    public static String format16(String userId) {
        if (userId == null || userId.isBlank()) {
            return "";
        }
        if (!userId.chars().allMatch(Character::isDigit)) {
            return userId;
        }
        if (userId.length() >= 16) {
            return userId;
        }
        return "0".repeat(16 - userId.length()) + userId;
    }
}

