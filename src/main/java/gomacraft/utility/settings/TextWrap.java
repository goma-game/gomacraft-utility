package gomacraft.utility.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * ツールチップの文を、表示の幅で折り返す。Minecraft に依らない(試験はここを見る)。
 *
 * <p>Minecraft の折り返しは空白で語を区切るので、日本語の文では、文の中のただ 1 つの空白(「AE2 のターミナル…」の
 * 「AE2」の後)で折れてしまう(goma-org/gomabase#2295 の 4 回目の確かめの 4)。ここでは:
 * <ul>
 *   <li>英数字の続き(空白を含まない ASCII の続き —— {@code AE2}・{@code Alt+Shift+クリック} の {@code Alt+Shift+})は
 *       1 語として分けない(1 行に入らない長さなら字で分ける)。</li>
 *   <li>それ以外の字(かな・漢字など)は、どの字の間でも折る。</li>
 *   <li>行の頭に句読点と閉じ括弧(、。,.)」』など)を置かない(前の行の終わりへ寄せる。幅を少し越えてよい)。</li>
 *   <li>{@code \n} は改行。行の頭と終わりの空白は落とす。</li>
 * </ul>
 */
public final class TextWrap {
    /** 行の頭に置かない字。 */
    private static final String NO_LINE_START = "、。，．,.!?！？)）]］」』】〉》ー々ぁぃぅぇぉっゃゅょァィゥェォッャュョ:：;；";

    private TextWrap() {
    }

    /**
     * @param width 1 字(code point)の幅
     * @param max   1 行の幅の上限
     */
    public static List<String> wrap(String text, IntUnaryOperator width, int max) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            wrapParagraph(paragraph, width, max, lines);
        }
        return lines;
    }

    private static void wrapParagraph(String text, IntUnaryOperator width, int max, List<String> lines) {
        int start = lines.size();
        StringBuilder line = new StringBuilder();
        int lineWidth = 0;
        int i = 0;
        while (i < text.length()) {
            int c = text.codePointAt(i);
            // 次の 1 単位(空白・英数字の続き・1 字)
            int end = i + Character.charCount(c);
            if (isWordChar(c)) {
                while (end < text.length() && isWordChar(text.codePointAt(end))) {
                    end += Character.charCount(text.codePointAt(end));
                }
            }
            String unit = text.substring(i, end);
            int unitWidth = measure(unit, width);
            i = end;
            if (c == ' ') {
                if (lineWidth > 0 && lineWidth + unitWidth <= max) {
                    line.append(unit);
                    lineWidth += unitWidth;
                } else if (lineWidth > 0) {
                    lines.add(strip(line));
                    line.setLength(0);
                    lineWidth = 0;
                }
                continue;
            }
            if (lineWidth + unitWidth <= max) {
                line.append(unit);
                lineWidth += unitWidth;
                continue;
            }
            if (lineWidth > 0 && NO_LINE_START.indexOf(c) >= 0 && unit.length() == Character.charCount(c)) {
                // 句読点は前の行に寄せる(少し越える)
                line.append(unit);
                lines.add(strip(line));
                line.setLength(0);
                lineWidth = 0;
                continue;
            }
            if (lineWidth > 0) {
                lines.add(strip(line));
                line.setLength(0);
                lineWidth = 0;
            }
            if (unitWidth <= max) {
                line.append(unit);
                lineWidth = unitWidth;
                continue;
            }
            // 1 行に入らない長い語: 字で分ける
            for (int j = 0; j < unit.length(); ) {
                int d = unit.codePointAt(j);
                int w = width.applyAsInt(d);
                if (lineWidth > 0 && lineWidth + w > max) {
                    lines.add(strip(line));
                    line.setLength(0);
                    lineWidth = 0;
                }
                line.appendCodePoint(d);
                lineWidth += w;
                j += Character.charCount(d);
            }
        }
        String last = strip(line);
        if (!last.isEmpty() || lines.size() == start) {
            lines.add(last);
        }
    }

    private static boolean isWordChar(int c) {
        return c > ' ' && c < 0x7F;
    }

    private static int measure(String s, IntUnaryOperator width) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int c = s.codePointAt(i);
            w += width.applyAsInt(c);
            i += Character.charCount(c);
        }
        return w;
    }

    private static String strip(StringBuilder sb) {
        return sb.toString().strip();
    }
}
