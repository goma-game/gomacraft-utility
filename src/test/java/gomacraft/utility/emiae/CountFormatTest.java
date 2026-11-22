package gomacraft.utility.emiae;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CountFormatTest {
    @Test
    void shortensWithoutRoundingUp() {
        assertEquals("0", CountFormat.shortCount(0));
        assertEquals("999", CountFormat.shortCount(999));
        assertEquals("1k", CountFormat.shortCount(1000));
        assertEquals("1.2k", CountFormat.shortCount(1234));
        assertEquals("1.9k", CountFormat.shortCount(1999));
        assertEquals("9.9k", CountFormat.shortCount(9999));
        assertEquals("12k", CountFormat.shortCount(12345));
        assertEquals("999k", CountFormat.shortCount(999_999));
        assertEquals("1M", CountFormat.shortCount(1_000_000));
        assertEquals("3.4G", CountFormat.shortCount(3_456_789_012L));
        assertEquals("9.2E", CountFormat.shortCount(Long.MAX_VALUE));
    }
}
