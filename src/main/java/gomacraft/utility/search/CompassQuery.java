package gomacraft.utility.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * コンパス(Nature's / Explorer's)の検索語を、印の付いた語に分けて照合する(goma-org/gomabase#2295 の 5 回目の確かめの B。
 * Minecraft に依らない)。
 *
 * <p>空白で区切った語を全部満たすものが当たる。語の頭の印で、何を見るかを決める。印は AE2・EMI の慣れた形に揃える。
 * <ul>
 *   <li>印なし: 名前(日本語と英語 —— 英語名の検索と組み合わさる)</li>
 *   <li>{@code @}: ソース(Mod)。AE2・EMI・コンパスと同じ</li>
 *   <li>{@code #}: タグ。AE2・EMI と同じ。Nature's Compass がタグに使う {@code $} も同じに読む</li>
 *   <li>{@code ~}: ディメンション。AE2・EMI・JEI のどれも使っていない印から選んだ(慣例が無い)</li>
 *   <li>{@code %}: グループ(Explorer's Compass の構造物の群)。JEI がクリエイティブのタブ(まとまり)に使う印に合わせた
 *       (AE2・EMI には群の印が無い)</li>
 * </ul>
 */
public final class CompassQuery {
    public enum Kind { NAME, SOURCE, TAG, DIMENSION, GROUP }

    public record Term(Kind kind, String text) {
    }

    private CompassQuery() {
    }

    /** 語に分ける(小文字にする。印だけの語は捨てる)。 */
    public static List<Term> parse(String input) {
        List<Term> out = new ArrayList<>();
        if (input == null) {
            return out;
        }
        for (String word : input.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            Kind kind = switch (word.charAt(0)) {
                case '@' -> Kind.SOURCE;
                case '#', '$' -> Kind.TAG;
                case '~' -> Kind.DIMENSION;
                case '%' -> Kind.GROUP;
                default -> Kind.NAME;
            };
            String text = kind == Kind.NAME ? word : word.substring(1);
            if (!text.isEmpty()) {
                out.add(new Term(kind, text));
            }
        }
        return out;
    }

    /**
     * この Mod が照合を受け持つか。コンパス本来の照合で足りる形(印の無い語・{@code @} か {@code $} 1 つだけの語)は
     * 本来のままにする(空白を含む名前の検索も今までどおり)。
     *
     * @param raw       欄の語(印を見分けるため、分ける前のもの)
     * @param supported このコンパスで見られる印の種類
     */
    public static boolean takesOver(String raw, Set<Kind> supported) {
        List<Term> terms = parse(raw);
        boolean prefixed = false;
        for (Term t : terms) {
            if (t.kind() == Kind.NAME) {
                continue;
            }
            prefixed = true;
            // コンパスが自分で読まない印(# ~ %)は、1 つでも在れば受け持つ
            if (t.kind() != Kind.SOURCE && !(t.kind() == Kind.TAG && raw.trim().startsWith("$") && terms.size() == 1)
                    && supported.contains(t.kind())) {
                return true;
            }
        }
        // 印の付いた語を、ほかの語と組み合わせた
        return prefixed && terms.size() > 1;
    }

    /**
     * 全部の語を満たすか。
     *
     * @param values 種類ごとの、照合する文字列(小文字にしてから比べる。このコンパスで見られない種類は空)
     */
    public static boolean matches(List<Term> terms, Function<Kind, List<String>> values) {
        for (Term t : terms) {
            boolean hit = false;
            for (String v : values.apply(t.kind())) {
                if (v != null && v.toLowerCase(Locale.ROOT).contains(t.text())) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                return false;
            }
        }
        return true;
    }
}
