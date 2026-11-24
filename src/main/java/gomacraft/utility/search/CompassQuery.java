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
 *
 * <p>⚠ Mod の名前・ディメンションの名前は空白を含む(Biomes O' Plenty・the nether)。語は空白で区切るので、{@code @}・
 * {@code ~} の語は、空白と記号({@code ' ’ _ - . :})を詰めた形どうしで比べる({@code @biomesoplenty}・
 * {@code @yungsbetterdungeons}・{@code ~thenether} が当たる。名前空間 —— Mod の id —— でも当たる)。EMI も空白を詰めて
 * 打てば当たる(EMI は Mod の id で当てる)ので、その打ち方に合わせた(ユーザの指定)。印だけの語({@code @ forest} の
 * {@code @})は捨てる(続く語は名前の語)。
 */
public final class CompassQuery {
    public enum Kind { NAME, SOURCE, TAG, DIMENSION, GROUP }

    public record Term(Kind kind, String text) {
    }

    private CompassQuery() {
    }

    private static Kind kindOf(char c) {
        return switch (c) {
            case '@' -> Kind.SOURCE;
            case '#', '$' -> Kind.TAG;
            case '~' -> Kind.DIMENSION;
            case '%' -> Kind.GROUP;
            default -> Kind.NAME;
        };
    }

    /**
     * 語に分ける(小文字にする)。このコンパスで見られない種類の印の語は捨てる(黙って 0 件にしない)。
     *
     * @param supported このコンパスで見られる印の種類(名前はいつも見る)
     */
    public static List<Term> parse(String input, Set<Kind> supported) {
        List<Term> out = new ArrayList<>();
        if (input == null) {
            return out;
        }
        for (String word : input.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            Kind kind = kindOf(word.charAt(0));
            String text = kind == Kind.NAME ? word : word.substring(1);
            if (text.isEmpty()) {
                // 印だけの語は捨てる(続く語は名前の語)
                continue;
            }
            add(out, kind, text, supported);
        }
        return out;
    }

    private static void add(List<Term> out, Kind kind, String text, Set<Kind> supported) {
        if (kind == Kind.NAME || supported.contains(kind)) {
            out.add(new Term(kind, text));
        }
    }

    /**
     * この Mod が照合を受け持つか。印の語が 1 つでも在れば受け持つ(印の語の当たり方を、組み合わせの有無で変えない)。
     * 印の無い語だけなら、コンパス本来の照合のまま(英語名は照合に使う名前の差し替えで当たる)。
     */
    public static boolean takesOver(String raw, Set<Kind> supported) {
        for (Term t : parse(raw, supported)) {
            if (t.kind() != Kind.NAME) {
                return true;
            }
        }
        return false;
    }

    /** 空白と記号({@code ' ’ _ - . :})を詰める(Mod の名前・ディメンションの名前を、空白を詰めて打った語と比べる)。 */
    static String compact(String text) {
        return text.replaceAll("[\\s'\u2019_\\-.:]", "");
    }

    /**
     * 全部の語を満たすか。
     *
     * @param values 種類ごとの、照合する文字列(小文字にしてから比べる。このコンパスで見られない種類は空)
     */
    public static boolean matches(List<Term> terms, Function<Kind, List<String>> values) {
        for (Term t : terms) {
            boolean hit = false;
            boolean compact = t.kind() == Kind.SOURCE || t.kind() == Kind.DIMENSION;
            String needle = compact ? compact(t.text()) : t.text();
            for (String v : values.apply(t.kind())) {
                if (v == null) {
                    continue;
                }
                String hay = v.toLowerCase(Locale.ROOT);
                if ((compact ? compact(hay) : hay).contains(needle)) {
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
