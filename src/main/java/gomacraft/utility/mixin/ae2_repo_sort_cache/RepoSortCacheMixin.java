package gomacraft.utility.mixin.ae2_repo_sort_cache;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.Repo;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import gomacraft.utility.Features;
import gomacraft.utility.features.ae2_repo_sort_cache.Ae2RepoSortCache;
import gomacraft.utility.features.ae2_repo_sort_cache.client.Ae2SortKeys;
import java.util.Comparator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * AE2 の端末の手元の一覧({@code Repo})の並べ替えの比べ方を、鍵(Mod の id・表示名)を作り置く同じ順序の比べ方に
 * 置き換える(理由は {@code Ae2RepoSortCache})。並べ直しのまとめは別の機能({@code ae2_repo_update_batch})。
 */
@Mixin(Repo.class)
public abstract class RepoSortCacheMixin {
    @Group(name = "gomacraft_utility$ae2_repo_sort_cache$key_comparator", min = 1)
    @ModifyReturnValue(
            method = "getKeyComparator(Lappeng/api/config/SortOrder;Lappeng/api/config/SortDir;)Ljava/util/Comparator;",
            at = @At("RETURN"), require = 0)
    private Comparator<AEKey> gomacraftUtility$cacheSortKeys(Comparator<AEKey> original,
            @Local(argsOnly = true) SortOrder order, @Local(argsOnly = true) SortDir dir) {
        return Features.guard(Ae2RepoSortCache.ID, () -> Ae2SortKeys.comparator(order, dir, original), original);
    }
}
