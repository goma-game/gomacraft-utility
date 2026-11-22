package gomacraft.utility.search.client;

import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;

/**
 * コンパス(Nature's / Explorer's)の検索で、一覧の名前に英語名を足す。コンパスは
 * {@code 名前.toLowerCase().contains(検索語)} で照合するので、改行で区切って足せば、どちらの名前でも当たる
 * (区切りをまたいでは当たらない)。
 */
public final class CompassEnglish {
    private CompassEnglish() {
    }

    /** バイオームの名前({@code biome.<名前空間>.<名前>})に英語名を足す。 */
    public static String biome(String shown, ResourceLocation key) {
        return withEnglish(shown, key == null ? null : Util.makeDescriptionId("biome", key));
    }

    /** 構造物の名前({@code structure.<名前空間>.<名前>})に英語名を足す。 */
    public static String structure(String shown, ResourceLocation key) {
        return withEnglish(shown, key == null ? null : Util.makeDescriptionId("structure", key));
    }

    /**
     * 一覧の行で名前に使える幅を引く分。名前は行の左から 5 の所に描かれ、右端には印(XP など)と枠がある。
     * 併記がこの幅に収まらないときは、日本語だけを出す(印と枠にかけない —— #16 のレビュー)。
     */
    static final int ROW_RESERVED = 5 + 32;

    /**
     * 一覧に出すバイオームの名前を「日本語 / English」の 1 行にする(品と同じ併記 —— goma-org/gomabase#2295 の 8)。
     * 行の幅 {@code rowWidth} に収まらなければ日本語だけ。
     */
    public static String biomeDisplay(String shown, ResourceLocation key, int rowWidth) {
        return fit(shown, joined(shown, key == null ? null : Util.makeDescriptionId("biome", key), " / "), rowWidth);
    }

    /** 一覧に出す構造物の名前を「日本語 / English」の 1 行にする。収まらなければ日本語だけ。 */
    public static String structureDisplay(String shown, ResourceLocation key, int rowWidth) {
        return fit(shown, joined(shown, key == null ? null : Util.makeDescriptionId("structure", key), " / "),
                rowWidth);
    }

    private static String fit(String shown, String both, int rowWidth) {
        if (both == null || both.equals(shown)) {
            return shown;
        }
        int available = rowWidth - ROW_RESERVED;
        return net.minecraft.client.Minecraft.getInstance().font.width(both) <= available ? both : shown;
    }

    static String withEnglish(String shown, String translationKey) {
        return joined(shown, translationKey, "\n");
    }

    /** 英語名が引けて、見えている名前と違うときだけ {@code separator} でつなぐ(英語で遊んでいるときは足さない)。 */
    static String joined(String shown, String translationKey, String separator) {
        if (shown == null || translationKey == null) {
            return shown;
        }
        return EnglishNames.lookup(translationKey)
                .filter(english -> !english.toLowerCase(Locale.ROOT).equals(shown.toLowerCase(Locale.ROOT)))
                .map(english -> shown + separator + english)
                .orElse(shown);
    }
}
