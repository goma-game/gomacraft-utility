package gomacraft.utility.emiae;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 在庫数バッジ描画の位置計算とフォーマット済み数量文字列のキャッシュ(Minecraft / OpenGL 非依存)。
 *
 * <p>座標式は AE2 の {@code StackSizeRenderer} と同一に保つ。
 *
 * <p>⚠ 写したもの: 座標の式・倍率(0.85 / 0.666)・z の 200・影と字の色は、AE2 の
 * {@code appeng.client.gui.me.common.StackSizeRenderer#renderSizeLabel} を写した(写した元の版は AE2 19.2.18 ——
 * {@code gradle.properties} の {@code ae2_version}。ライセンスは AE2 のコードの LGPL-3.0)。AE2 を上げて描き方が
 * 変われば、ここも見直す(AE2 とずれても何も赤くならない)。写してよいことは、ユーザの決定(2026-11-28、
 * goma-org/gomabase#2295)。
 */
public final class BadgeLayout {
    public static final float SCALE_LARGE = 0.85f;
    public static final float SCALE_SMALL = 0.666f;
    public static final float Z_OFFSET = 200.0f;
    public static final int COLOR_SHADOW = 0x413F54;
    public static final int COLOR_TEXT = 0xFFFFFF;
    public static final int FULL_BRIGHT = 0xF000F0;

    private BadgeLayout() {
    }

    public static float scale(boolean largeFonts) {
        return largeFonts ? SCALE_LARGE : SCALE_SMALL;
    }

    public static int labelX(float xPos, int textWidth, boolean largeFonts) {
        float s = scale(largeFonts);
        int offset = largeFonts ? 0 : -1;
        return (int) ((xPos + offset + 16.0f + 2.0f - textWidth * s) * (1.0f / s));
    }

    public static int labelY(float yPos, boolean largeFonts) {
        float s = scale(largeFonts);
        int offset = largeFonts ? 0 : -1;
        return (int) ((yPos + offset + 16.0f - 5.0f * s) * (1.0f / s));
    }

    /** 描画フレーム間で {@code formatAmount} の文字列生成を使い回す上限付きキャッシュ。 */
    public static final class AmountLabelCache<K> {
        private record CachedEntry(long stored, boolean largeFonts, String label) {
        }

        private final int limit;
        private final Map<K, CachedEntry> entries = new HashMap<>();

        public AmountLabelCache(int limit) {
            if (limit < 1) {
                throw new IllegalArgumentException("limit: " + limit);
            }
            this.limit = limit;
        }

        public String getOrFormat(K key, long stored, boolean largeFonts, Supplier<String> formatter) {
            CachedEntry cached = entries.get(key);
            if (cached != null && cached.stored() == stored && cached.largeFonts() == largeFonts) {
                return cached.label();
            }
            String formatted = formatter.get();
            if (entries.size() >= limit) {
                entries.clear();
            }
            entries.put(key, new CachedEntry(stored, largeFonts, formatted));
            return formatted;
        }

        public void clear() {
            entries.clear();
        }

        public int size() {
            return entries.size();
        }
    }
}
