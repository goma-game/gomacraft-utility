package gomacraft.utility.mixin.naturescompass_search;

import com.chaosthedude.naturescompass.gui.BiomeSearchEntry;
import com.chaosthedude.naturescompass.util.BiomeUtils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import gomacraft.utility.Features;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.client.CompassEnglish;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * Nature's Compass の結果の一覧の行で、バイオームの名前を「日本語 / English」の 1 行にする(品と同じ併記 ——
 * goma-org/gomabase#2295 の 8)。検索の照合は {@link NaturesCompassScreenMixin} が持つ。
 */
@Mixin(BiomeSearchEntry.class)
public abstract class BiomeSearchEntryMixin {
    @Group(name = "gomacraft_utility$naturescompass_search$list_name", min = 1)
    @WrapOperation(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIZF)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/chaosthedude/naturescompass/util/BiomeUtils;getBiomeNameForDisplay(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/biome/Biome;)Ljava/lang/String;"),
            require = 0)
    private String gomacraftUtility$addEnglishToList(Level level, Biome biome, Operation<String> original,
            @Local(argsOnly = true, ordinal = 3) int rowWidth) {
        // render(GuiGraphics, index, top, left, width, height, mouseX, mouseY, hovered, partialTick) の width
        String shown = original.call(level, biome);
        return Features.guard(SearchFeatures.NATURESCOMPASS_SEARCH,
                () -> CompassEnglish.biomeDisplay(shown, BiomeUtils.getKeyForBiome(level, biome).orElse(null),
                        rowWidth), shown);
    }
}
