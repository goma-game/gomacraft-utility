package gomacraft.utility.mixin.explorerscompass_search;

import com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import gomacraft.utility.Features;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.client.CompassEnglish;
import gomacraft.utility.search.client.ExplorersCompassFilters;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    /**
     * 印での絞り込み(@ ソース・# タグ・~ ディメンション・% グループ —— {@code CompassQuery})。この Mod が受け持つ形の
     * 検索語なら、結果の一覧を入れ替えて本来の照合を飛ばす。受け持たない形は本来の照合のまま(英語名は上の差し替えで当たる)。
     */
    @Group(name = "gomacraft_utility$explorerscompass_search$filters", min = 1)
    @Inject(method = "processSearchTerm()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void gomacraftUtility$filters(CallbackInfo ci) {
        if (Features.guard(SearchFeatures.EXPLORERSCOMPASS_SEARCH,
                () -> ExplorersCompassFilters.apply((ExplorersCompassScreen) (Object) this), false)) {
            ci.cancel();
        }
    }
}
