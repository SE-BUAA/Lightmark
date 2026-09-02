package top.ortus.lightmark.user.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserIdFormatterTest {
    @Test
    void formatsOnlyNumericIdsToSixteenCharacters() {
        assertEquals("0000000000000001", UserIdFormatter.format16("1"));
        assertEquals("1234567890123456", UserIdFormatter.format16("1234567890123456"));
        assertEquals("abc", UserIdFormatter.format16("abc"));
        assertEquals("", UserIdFormatter.format16(" "));
    }
}
