package gomacraft.utility.mixin.explorerscompass_search;

import com.chaosthedude.explorerscompass.gui.StructureSearchEntry;
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
 * Explorer's Compass の結果の一覧の行で、構造物の名前を「日本語 / English」の 1 行にする(品と同じ併記 ——
 * goma-org/gomabase#2295 の 8)。⚠ 行は名前を 2 回引く(名前と、同じ群の行)。併記するのは 1 回目の名前だけ
 * ({@code ordinal = 0})。検索の照合は {@link ExplorersCompassScreenMixin} が持つ。
 */
@Mixin(StructureSearchEntry.class)
public abstract class StructureSearchEntryMixin {
    @Group(name = "gomacraft_utility$explorerscompass_search$list_name", min = 1)
    @WrapOperation(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIZF)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/chaosthedude/explorerscompass/util/StructureUtils;getPrettyStructureName(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;",
                    ordinal = 0),
            require = 0)
    private String gomacraftUtility$addEnglishToList(ResourceLocation key, Operation<String> original) {
        String shown = original.call(key);
        return Features.guard(SearchFeatures.EXPLORERSCOMPASS_SEARCH,
                () -> CompassEnglish.structureDisplay(shown, key), shown);
    }
}
