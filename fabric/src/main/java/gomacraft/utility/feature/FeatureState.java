package gomacraft.utility.feature;

/** 機能の状態。起動時の 1 行に出す語は {@link #word()}(gomabase の起動の検証が読む閉じた集合)。 */
public enum FeatureState {
    /** 有効。 */
    ON("on"),
    /** 切ってある(設定・対象の Mod が無い・版が合わない)。壊れてはいない。 */
    OFF("off"),
    /** 有効にしようとして壊れた(mixin の当て損ね・初期化の例外)。この機能だけが止まり、起動は続く。 */
    FAILED("FAILED");

    private final String word;

    FeatureState(String word) {
        this.word = word;
    }

    public String word() {
        return word;
    }
}
