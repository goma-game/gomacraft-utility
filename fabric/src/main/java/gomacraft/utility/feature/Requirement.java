package gomacraft.utility.feature;

import java.util.Objects;

/**
 * 機能が当てる先の Mod と、その版の範囲。
 *
 * <p>fabric 版: forge 版の同じ名前のクラスから写した。版の範囲の書き方だけが違う(forge 版は Maven の書き方)。
 *
 * @param modId        対象の Mod の id
 * @param versionRange Fabric の版の条件の書き方(例: {@code >=19.0 <20}。読むのは {@link ModLookup#matches})。null は「版を問わない」
 */
public record Requirement(String modId, String versionRange) {
    public Requirement {
        Objects.requireNonNull(modId, "modId");
    }

    public static Requirement of(String modId) {
        return new Requirement(modId, null);
    }

    public static Requirement of(String modId, String versionRange) {
        return new Requirement(modId, versionRange);
    }
}
