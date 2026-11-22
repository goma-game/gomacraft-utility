package gomacraft.utility.mixin.ae2_english_search;

import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import gomacraft.utility.Features;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.client.Ae2English;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/** パターン端末(Access)の検索で、パターンの出力の英語名でも当たるようにする。 */
@Mixin(PatternAccessTermScreen.class)
public abstract class PatternAccessTermScreenMixin {
    @Group(name = "gomacraft_utility$ae2_english_search$pattern_text", min = 1)
    @ModifyReturnValue(
            method = "getPatternSearchText(Lnet/minecraft/world/item/ItemStack;)Ljava/lang/String;",
            at = @At("RETURN"),
            require = 0)
    private String gomacraftUtility$addEnglish(String original, @Local(argsOnly = true) ItemStack stack) {
        return Features.guard(SearchFeatures.AE2_ENGLISH_SEARCH,
                () -> Ae2English.withPatternOutputs(original, stack), original);
    }
}
