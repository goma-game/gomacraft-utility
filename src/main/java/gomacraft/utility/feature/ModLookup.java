package gomacraft.utility.feature;

import java.util.Optional;

/** 入っている Mod の版を引く口(実物は FML、試験は表)。 */
@FunctionalInterface
public interface ModLookup {
    /** 入っていれば版の文字列、無ければ空。 */
    Optional<String> version(String modId);
}
