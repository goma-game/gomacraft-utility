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

    static String withEnglish(String shown, String translationKey) {
        if (shown == null || translationKey == null) {
            return shown;
        }
        return EnglishNames.lookup(translationKey)
                .filter(english -> !english.toLowerCase(Locale.ROOT).equals(shown.toLowerCase(Locale.ROOT)))
                .map(english -> shown + "\n" + english)
                .orElse(shown);
    }
}
