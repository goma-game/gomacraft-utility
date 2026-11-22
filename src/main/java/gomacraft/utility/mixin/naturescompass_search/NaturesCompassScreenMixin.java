package gomacraft.utility.mixin.naturescompass_search;

import com.chaosthedude.naturescompass.gui.NaturesCompassScreen;
import com.chaosthedude.naturescompass.util.BiomeUtils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import gomacraft.utility.Features;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.client.CompassEnglish;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * Nature's Compass の検索(印の無い検索語)で、バイオームの英語名でも当たるようにする。差し替えるのは検索の照合に
 * 使う名前だけで、一覧に出す名前は変えない。
 */
@Mixin(NaturesCompassScreen.class)
public abstract class NaturesCompassScreenMixin {
    @Group(name = "gomacraft_utility$naturescompass_search$biome_name", min = 1)
    @WrapOperation(
            method = "processSearchTerm()V",
            at = @At(value = "INVOKE",
                    target = "Lcom/chaosthedude/naturescompass/util/BiomeUtils;getBiomeNameForDisplay(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/biome/Biome;)Ljava/lang/String;"),
            require = 0)
    private String gomacraftUtility$addEnglish(Level level, Biome biome, Operation<String> original) {
        String shown = original.call(level, biome);
        return Features.guard(SearchFeatures.NATURESCOMPASS_SEARCH,
                () -> CompassEnglish.biome(shown, BiomeUtils.getKeyForBiome(level, biome).orElse(null)), shown);
    }
}
