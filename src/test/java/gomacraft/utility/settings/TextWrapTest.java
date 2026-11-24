package gomacraft.utility.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** ツールチップの折り返しが、日本語の文を空白の位置で折らないこと(goma-org/gomabase#2295 の 4 回目の確かめの 4)。 */
class TextWrapTest {
    private static int width(int c) {
        return c > 0x7F ? 9 : 6;
    }

    @Test
    void doesNotBreakRightAfterALeadingAsciiWord() {
        List<String> lines = TextWrap.wrap("AE2 のターミナルの検索で、英語名でも当たるようにします。", TextWrapTest::width, 100);
        assertTrue(lines.get(0).startsWith("AE2 の"), "「AE2」の後で折れた: " + lines);
        for (String line : lines) {
            int w = line.codePoints().map(TextWrapTest::width).sum();
            // 句読点を前の行へ寄せた分(1 字)だけは越えてよい
            assertTrue(w <= 100 + 9, "幅を越えた: " + line);
            assertFalse(line.startsWith("、") || line.startsWith("。"), "句読点で始まる行: " + lines);
        }
        assertEquals("AE2 のターミナルの検索で、英語名でも当たるようにします。", String.join("", lines).replace("AE2の", "AE2 の"));
    }

    @Test
    void consecutivePunctuationStaysOnThePreviousLine() {
        // 幅 36 = 全角 4 字。「あいうえ」の後に「。」」が続く
        List<String> lines = TextWrap.wrap("あいうえ。」かき", TextWrapTest::width, 36);
        assertEquals(List.of("あいうえ。」", "かき"), lines);
    }

    @Test
    void keepsAsciiWordsAndHonoursNewlines() {
        List<String> lines = TextWrap.wrap("Explorer's Compass の検索\n次の行", TextWrapTest::width, 60);
        assertTrue(lines.contains("次の行"), lines.toString());
        for (String line : lines) {
            assertFalse(line.contains("Explor") && !line.contains("Explorer's"), "英語の語を分けた: " + lines);
        }
    }
}
