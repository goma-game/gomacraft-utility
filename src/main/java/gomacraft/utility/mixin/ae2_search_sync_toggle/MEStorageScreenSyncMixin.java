package gomacraft.utility.mixin.ae2_search_sync_toggle;

import appeng.client.gui.me.common.MEStorageScreen;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import gomacraft.utility.Features;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.client.Ae2SearchSyncToggle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * AE2 の端末が「EMI の検索と同期する」設定を読むところ({@code MEStorageScreen#updateSearch} の中の呼び出し)だけで、
 * 読んだ値をその場だけ反転する。反転の入り切りは {@link Ae2SearchSyncToggle}(検索欄のダブルクリック)。
 *
 * <p>⚠ {@code AEConfig#isSyncWithExternalSearch} そのものを反転しない: AE2 の端末の設定の画面がこの値を読んで切り替え、
 * {@code save()} で設定のファイルへ書くので、反転した値がファイルに残ってしまう。
 */
@Mixin(MEStorageScreen.class)
public abstract class MEStorageScreenSyncMixin {
    @Group(name = "gomacraft_utility$ae2_search_sync_toggle$sync", min = 1)
    @ModifyExpressionValue(
            method = "updateSearch()V",
            at = @At(value = "INVOKE", target = "Lappeng/core/AEConfig;isSyncWithExternalSearch()Z"),
            require = 0)
    private boolean gomacraftUtility$invert(boolean configured) {
        return Features.guard(SearchFeatures.AE2_SEARCH_SYNC_TOGGLE,
                () -> configured != Ae2SearchSyncToggle.inverted(), configured);
    }
}
