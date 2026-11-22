package gomacraft.utility.mixin.ae2_english_search;

import appeng.menu.me.common.GridInventoryEntry;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import gomacraft.utility.Features;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.client.Ae2English;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * AE2 の端末の、印の無い検索語(名前の検索)で、今の言語の名前に当たらなければ英語名でも見る。
 * パターン端末(Encoding)・ワイヤレスの端末も同じ判定を通る。
 */
@Mixin(targets = "appeng.client.gui.me.search.NameSearchPredicate")
public abstract class NameSearchPredicateMixin {
    @Shadow
    @Final
    private String term;

    @Group(name = "gomacraft_utility$ae2_english_search$name_test", min = 1)
    @ModifyReturnValue(method = "test(Lappeng/menu/me/common/GridInventoryEntry;)Z", at = @At("RETURN"), require = 0)
    private boolean gomacraftUtility$matchEnglish(boolean matched, @Local(argsOnly = true) GridInventoryEntry entry) {
        if (matched) {
            return true;
        }
        return Features.guard(SearchFeatures.AE2_ENGLISH_SEARCH,
                () -> Ae2English.matches(entry.getWhat(), term), false);
    }
}
