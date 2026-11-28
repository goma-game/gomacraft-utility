package gomacraft.utility.feature;

import java.util.Optional;

/**
 * 入っている Mod の版を引く口(実物は Fabric Loader、試験は表)。
 *
 * <p>fabric 版: forge 版の同じ名前のクラスから写し、版の範囲の読み方({@link #matches})をここへ移した
 * (forge 版は Maven の書き方を {@link FeatureRegistry} が読む。Fabric Loader は Maven の版の部品を持たないので、
 * Fabric の書き方で、Loader の部品に読ませる)。
 */
public interface ModLookup {
    /** 入っていれば版の文字列、無ければ空。 */
    Optional<String> version(String modId);

    /**
     * 版が範囲に入るか。範囲の書き方は Fabric の {@code fabric.mod.json} の版の条件(例: {@code >=1.2 <2})。
     *
     * @throws IllegalArgumentException 範囲か版を読めない(機能の定義の誤り。呼ぶ側が FAILED にする)
     */
    boolean matches(String version, String range);
}
