package gomacraft.utility.search;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 複数行の文言(ツールチップ)への訳の当て方。Minecraft に依らない(試験はここを見る)。
 *
 * <p>1 文を行に分けて書いたツールチップがあるので、行を改行でつないだまとまりを先に引く。まとまりの訳が無ければ、
 * 行ごとに引く。
 */
public final class LiteralLines {
    private LiteralLines() {
    }

    /**
     * @param lines     元の行(null・空ならそのまま返す)
     * @param translate 1 つの文言の訳(訳が無ければ同じ文字列を返す)
     */
    public static List<String> translate(List<String> lines, UnaryOperator<String> translate) {
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        if (lines.size() > 1 && lines.stream().noneMatch(line -> line == null)) {
            String block = String.join("\n", lines);
            String translated = translate.apply(block);
            if (translated != null && !translated.equals(block)) {
                return List.of(translated.split("\n", -1));
            }
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(translate.apply(line));
        }
        return out;
    }
}
