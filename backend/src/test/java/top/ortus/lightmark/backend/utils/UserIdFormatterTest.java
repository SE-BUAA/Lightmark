package top.ortus.lightmark.backend.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UserIdFormatterTest {

    @Test
    public void testFormat16() {
        Assertions.assertEquals("0000000000000123", UserIdFormatter.format16("123"));
        Assertions.assertEquals("0000000000000000", UserIdFormatter.format16("0"));
        Assertions.assertEquals("1234567890123456", UserIdFormatter.format16("1234567890123456"));
        Assertions.assertEquals("abc", UserIdFormatter.format16("abc"));
    }

    @Test
    public void testFormat16BoundaryInputs() {
        Assertions.assertEquals("", UserIdFormatter.format16(null));
        Assertions.assertEquals("", UserIdFormatter.format16(""));
        Assertions.assertEquals("", UserIdFormatter.format16("   "));
        Assertions.assertEquals("-123", UserIdFormatter.format16("-123"));
        Assertions.assertEquals("12345678901234567", UserIdFormatter.format16("12345678901234567"));
        Assertions.assertEquals(" 123 ", UserIdFormatter.format16(" 123 "));
        Assertions.assertEquals("12ab34", UserIdFormatter.format16("12ab34"));
    }
}

