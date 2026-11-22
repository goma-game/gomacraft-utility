package gomacraft.utility.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EnglishFormatTest {
    private static String format(String template, String... args) {
        return EnglishFormat.format(template, i -> args[i], args.length);
    }

    @Test
    void fillsSequentialAndIndexedArguments() {
        assertEquals("Potion of Healing", format("Potion of %s", "Healing"));
        assertEquals("b a", format("%2$s %1$s", "a", "b"));
        assertEquals("100% a", format("100%% %s", "a"));
    }

    @Test
    void leavesUnknownOrMissingAsIs() {
        assertEquals("x %d y", format("x %d y"));
        assertEquals("a ", format("a %s"));
        assertEquals("tail %", format("tail %"));
        assertEquals("%99999999999$s", format("%99999999999$s", "a"));
    }
}
