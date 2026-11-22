package gomacraft.utility.feature;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 機能の中の細かい設定の 1 つ(設定のファイルの {@code [settings]} の 1 行)。
 *
 * @param key          設定の名前({@code <機能の名前>_<設定>}。{@code [a-z0-9_]+})
 * @param defaultValue 書かれていないとき・読めないときの値(ファイルに書く形)
 * @param comment      設定のファイルに添える説明(1 行)
 * @param min          整数の設定の下限(整数でなければ null)
 * @param max          整数の設定の上限(整数でなければ null)
 */
public record Setting(String key, String defaultValue, String comment, Integer min, Integer max) {
    private static final Pattern KEY = Pattern.compile("[a-z0-9_]+");

    public Setting {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(comment, "comment");
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("設定の名前が不正: " + key);
        }
    }

    /** 整数の設定(範囲の外・整数でない値は既定の値に戻す)。 */
    public static Setting ofInt(String key, int defaultValue, int min, int max, String comment) {
        if (defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("既定の値が範囲の外: " + key);
        }
        return new Setting(key, Integer.toString(defaultValue), comment + "(" + min + "〜" + max + ")", min, max);
    }

    /** 書かれた値を整数として読む。書かれていない・整数でない・範囲の外なら既定の値。 */
    public int readInt(FeatureConfig config) {
        if (min == null || max == null) {
            throw new IllegalStateException("整数の設定でない: " + key);
        }
        Optional<String> raw = config.setting(key);
        int fallback = Integer.parseInt(defaultValue);
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            int v = Integer.parseInt(raw.get().strip());
            return v < min || v > max ? fallback : v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
