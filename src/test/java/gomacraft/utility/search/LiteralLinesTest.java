package gomacraft.utility.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiteralLinesTest {
    private static final Map<String, String> DICT = Map.of(
            "Check the status of\nthe selected receiver", "選んだ受信機の\n状態を確かめる",
            "Redstone mode:", "レッドストーンモード:",
            "Ignored", "無視");

    private static List<String> translate(List<String> lines) {
        return LiteralLines.translate(lines, s -> DICT.getOrDefault(s, s));
    }

    @Test
    void blockFirstThenLineByLine() {
        assertEquals(List.of("選んだ受信機の", "状態を確かめる"),
                translate(List.of("Check the status of", "the selected receiver")));
        assertEquals(List.of("レッドストーンモード:", "無視"), translate(List.of("Redstone mode:", "Ignored")));
        assertEquals(List.of("Unknown", "無視"), translate(List.of("Unknown", "Ignored")));
        assertEquals(List.of("無視"), translate(List.of("Ignored")));
        assertEquals(List.of(), translate(List.of()));
    }
}
