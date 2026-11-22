package gomacraft.utility.search.client;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEKey;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * AE2 の品(AEKey)の英語名。AE2 の名前の判定は検索語が変わるたびに全部の品を回すので、品ごとに作り置く
 * (索引を差し替えたら捨てる)。描画の thread からだけ呼ばれる。
 */
public final class Ae2English {
    /** 英語にできない品の印(作り置きに null を置かないため)。 */
    private static final String NONE = "";
    private static final Map<AEKey, String> CACHE = new WeakHashMap<>();
    private static int cachedGeneration = -1;

    private Ae2English() {
    }

    /** 品の英語名(小文字)が {@code term}(小文字)を含むか。 */
    public static boolean matches(AEKey key, String term) {
        if (key == null || term == null || term.isEmpty()) {
            return false;
        }
        String english = lowerCase(key);
        return english != null && english.contains(term);
    }

    /** 品の英語名(小文字。英語にできなければ null)。 */
    public static synchronized String lowerCase(AEKey key) {
        int generation = EnglishNames.generation();
        if (generation != cachedGeneration) {
            CACHE.clear();
            cachedGeneration = generation;
        }
        String cached = CACHE.get(key);
        if (cached == null) {
            String english = EnglishNames.lowerCase(key.getDisplayName());
            cached = english == null ? NONE : english;
            CACHE.put(key, cached);
        }
        return cached.isEmpty() ? null : cached;
    }

    /**
     * パターン端末(Access)の検索の文に、パターンの出力の英語名を足す(AE2 は出力の今の言語の名前だけを並べる)。
     */
    public static String withPatternOutputs(String original, ItemStack pattern) {
        Minecraft minecraft = Minecraft.getInstance();
        if (pattern == null || pattern.isEmpty() || minecraft.level == null) {
            return original;
        }
        var details = PatternDetailsHelper.decodePattern(pattern, minecraft.level);
        if (details == null) {
            return original;
        }
        StringBuilder text = new StringBuilder(original);
        for (var output : details.getOutputs()) {
            String english = lowerCase(output.what());
            if (english != null) {
                text.append(english).append('\n');
            }
        }
        return text.toString();
    }
}
