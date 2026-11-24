package gomacraft.utility.mixin.naturescompass_search;

import com.chaosthedude.naturescompass.gui.NaturesCompassScreen;
import com.chaosthedude.naturescompass.util.BiomeUtils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import gomacraft.utility.Features;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.client.CompassEnglish;
import gomacraft.utility.search.client.NaturesCompassFilters;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    /**
     * 印での絞り込み(@ ソース・# タグ・~ ディメンション・% グループ —— {@code CompassQuery})。この Mod が受け持つ形の
     * 検索語なら、結果の一覧を入れ替えて本来の照合を飛ばす。受け持たない形は本来の照合のまま(英語名は上の差し替えで当たる)。
     */
    @Group(name = "gomacraft_utility$naturescompass_search$filters", min = 1)
    @Inject(method = "processSearchTerm()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void gomacraftUtility$filters(CallbackInfo ci) {
        if (Features.guard(SearchFeatures.NATURESCOMPASS_SEARCH,
                () -> NaturesCompassFilters.apply((NaturesCompassScreen) (Object) this), false)) {
            ci.cancel();
        }
    }
}
