package gomacraft.utility.mixin.explorerscompass_search;

import com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import gomacraft.utility.Features;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.client.CompassEnglish;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * Explorer's Compass の検索(印の無い検索語)で、構造物の英語名でも当たるようにする。差し替えるのは検索の照合に
 * 使う名前だけで、一覧に出す名前は変えない。
 */
@Mixin(ExplorersCompassScreen.class)
public abstract class ExplorersCompassScreenMixin {
    @Group(name = "gomacraft_utility$explorerscompass_search$structure_name", min = 1)
    @WrapOperation(
            method = "processSearchTerm()V",
            at = @At(value = "INVOKE",
                    target = "Lcom/chaosthedude/explorerscompass/util/StructureUtils;getPrettyStructureName(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;"),
            require = 0)
    private String gomacraftUtility$addEnglish(ResourceLocation key, Operation<String> original) {
        String shown = original.call(key);
        return Features.guard(SearchFeatures.EXPLORERSCOMPASS_SEARCH,
                () -> CompassEnglish.structure(shown, key), shown);
    }
}
