package gomacraft.utility.mixin.ae2_tooltip_search_cache;

import appeng.api.stacks.AEKey;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import gomacraft.utility.Features;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.client.TooltipSearchCache;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * AE2 の端末のツールチップの検索({@code $})が使う「品 → ツールチップの文」の作り置きを、端末ごとの入れ物から、
 * 端末をまたいで持つ入れ物({@link TooltipSearchCache})に差し替える。AE2 は端末を開くたびに作り置きを空から作り直し、
 * 最初の検索で全部の品のツールチップを 1 度に作るので、待たされてカクついていた。
 */
@Mixin(targets = "appeng.client.gui.me.search.RepoSearch")
public abstract class RepoSearchMixin {
    @Group(name = "gomacraft_utility$ae2_tooltip_search_cache$predicates", min = 1)
    @ModifyExpressionValue(
            method = "getPredicates(Ljava/lang/String;)Ljava/util/List;",
            at = @At(value = "FIELD",
                    target = "Lappeng/client/gui/me/search/RepoSearch;tooltipCache:Ljava/util/Map;"),
            require = 0)
    private Map<AEKey, String> gomacraftUtility$shared(Map<AEKey, String> own) {
        return Features.guard(SearchFeatures.AE2_TOOLTIP_SEARCH_CACHE, TooltipSearchCache::shared, own);
    }
}
