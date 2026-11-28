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
 * 終わりの句読点と、対になる開き括弧の無い閉じ括弧は URL に含めない。
 *
 * <p>⚠ 全角の空白と日本語の句読点・括弧で切る(「…/a.png。次」「…/a、https://…/b」を 1 つにつなげない)。
 * ASCII だけには絞らない(「…/wiki/日本」のような日本語の path を壊すため)。
 */
public final class ChatLinks {
    /** URL を切る全角の文字(空白・句読点・括弧)。 */
    private static final String FULLWIDTH_BREAKS = "　。、，．・：；！？「」『』（）［］｛｝【】〈〉《》〔〕…‥";
    private static final Pattern URL = Pattern.compile(
            "https?://[^\\s§<>\"" + FULLWIDTH_BREAKS + "]+", Pattern.CASE_INSENSITIVE);
    private static final String TRAILING = ".,;:!?]}'";
    /** この直後なら ASCII でない文字(日本語の path・query)を URL に含める。 */
    private static final String BEFORE_NON_ASCII = "/?&=#-_~+(";

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
            int end = cutAtTextAfterUrl(text, matcher.start(), matcher.end());
            while (end > matcher.start()) {
                char last = text.charAt(end - 1);
                // 閉じ括弧は、URL の中に対になる開き括弧が無いときだけ外す(「Foo_(bar)」は残す)
                if (TRAILING.indexOf(last) >= 0 || (last == ')' && !balanced(text, matcher.start(), end))) {
                    end--;
                } else {
                    break;
                }
            }
            // scheme だけ(「https://」)はリンクにしない
            if (text.indexOf("://", matcher.start()) + 3 < end) {
                out.add(new Span(matcher.start(), end));
            }
        }
        return out;
    }

    /**
     * URL のすぐ後に続けて書いた日本語(「…/a.pngこれ」)を切る。ASCII の文字の直後に ASCII でない文字が来たら、
     * 前の文字が区切り({@link #BEFORE_NON_ASCII})でなければ、そこで終える(「…/wiki/日本」「?q=日本」は残す)。
     */
    private static int cutAtTextAfterUrl(String text, int start, int end) {
        for (int i = text.indexOf("://", start) + 3; i < end; i++) {
            char prev = text.charAt(i - 1);
            if (text.charAt(i) > 0x7F && prev <= 0x7F && BEFORE_NON_ASCII.indexOf(prev) < 0) {
                return i;
            }
        }
        return end;
    }

    /** {@code [start, end)} の中で「(」と「)」の数が合っているか。 */
    private static boolean balanced(String text, int start, int end) {
        int depth = 0;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
        }
        return depth == 0;
    }
}
