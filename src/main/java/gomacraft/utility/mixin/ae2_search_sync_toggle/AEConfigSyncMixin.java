package gomacraft.utility.mixin.ae2_search_sync_toggle;

import appeng.core.AEConfig;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import gomacraft.utility.Features;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.client.Ae2SearchSyncToggle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * AE2 の「EMI の検索と同期する」設定の読みを、その場だけ反転する(AE2 の設定のファイルは変えない)。
 * 反転の入り切りは {@link Ae2SearchSyncToggle}(検索欄のダブルクリック)。
 */
@Mixin(AEConfig.class)
public abstract class AEConfigSyncMixin {
    @Group(name = "gomacraft_utility$ae2_search_sync_toggle$sync", min = 1)
    @ModifyReturnValue(method = "isSyncWithExternalSearch()Z", at = @At("RETURN"), require = 0)
    private boolean gomacraftUtility$invert(boolean configured) {
        return Features.guard(SearchFeatures.AE2_SEARCH_SYNC_TOGGLE,
                () -> configured != Ae2SearchSyncToggle.inverted(), configured);
    }
}
