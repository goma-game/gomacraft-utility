package gomacraft.utility.chatimage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文の中の http / https の URL の位置を探す(機能 {@code chat_link})。Minecraft に依らない。
 *
 * <p>バニラ 1.21.1 はチャットの URL をリンクにしない(NeoForge の {@code CommonHooks.newChatWithLinks} も、
 * Mod の一覧の画面でしか使われていない)。scheme の無いもの(example.com)は拾わない(ふつうの文を誤って拾うため)。
 * 終わりの句読点と閉じ括弧は URL に含めない。
 */
public final class ChatLinks {
    private static final Pattern URL = Pattern.compile("https?://[^\\s§<>\"]+", Pattern.CASE_INSENSITIVE);
    private static final String TRAILING = ".,;:!?)]}'。、」』）";

    /** URL の位置({@code [start, end)})。 */
    public record Span(int start, int end) {
    }

    private ChatLinks() {
    }

    public static List<Span> find(String text) {
        List<Span> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            int end = matcher.end();
            while (end > matcher.start() && TRAILING.indexOf(text.charAt(end - 1)) >= 0) {
                end--;
            }
            // scheme だけ(「https://」)はリンクにしない
            if (text.indexOf("://", matcher.start()) + 3 < end) {
                out.add(new Span(matcher.start(), end));
            }
        }
        return out;
    }
}
