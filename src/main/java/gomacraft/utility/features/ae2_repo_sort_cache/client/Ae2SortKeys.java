package gomacraft.utility.features.ae2_repo_sort_cache.client;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.stacks.AEKey;
import gomacraft.utility.features.ae2_repo_sort_cache.SortKeys;
import java.util.Comparator;

/** AE2 の並べ方(名前順・Mod 順 × 昇順・降順)を、鍵を作り置く比べ方({@link SortKeys})に置き換える。 */
public final class Ae2SortKeys {
    private Ae2SortKeys() {
    }

    /**
     * AE2 の比べ方の代わり。知らない並べ方・向き(AE2 が後で足したもの)は、AE2 の比べ方をそのまま返す。
     *
     * @param original AE2 の比べ方({@code Repo.getKeyComparator} が返したもの)
     */
    public static Comparator<AEKey> comparator(SortOrder order, SortDir dir, Comparator<AEKey> original) {
        boolean byMod;
        if (order == SortOrder.NAME) {
            byMod = false;
        } else if (order == SortOrder.MOD) {
            byMod = true;
        } else {
            return original;
        }
        boolean ascending;
        if (dir == SortDir.ASCENDING) {
            ascending = true;
        } else if (dir == SortDir.DESCENDING) {
            ascending = false;
        } else {
            return original;
        }
        return SortKeys.comparator(byMod, ascending, AEKey::getModId, key -> key.getDisplayName().getString());
    }
}
