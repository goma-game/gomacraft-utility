package gomacraft.utility.mixin.emi_ae_stock;

import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import gomacraft.utility.Features;
import gomacraft.utility.emiae.EmiAeFeatures;
import gomacraft.utility.emiae.client.StockBadges;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EMI の一覧の 1 区画を描いた後に、AE の在庫数とクラフト可の印を重ねる。
 *
 * <p>⚠ 描くだけ。EMI の「手持ち」・「作れるレシピ」には触らない(goma-org/gomabase#2286)。
 */
@Mixin(EmiScreenManager.ScreenSpace.class)
public abstract class EmiScreenSpaceMixin {
    @Group(name = "gomacraft_utility$emi_ae_stock$render_badges", min = 1)
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void gomacraftUtility$renderBadges(
            EmiDrawContext context, int mouseX, int mouseY, float delta, int startIndex, CallbackInfo ci) {
        EmiScreenManager.ScreenSpace self = (EmiScreenManager.ScreenSpace) (Object) this;
        Features.guard(EmiAeFeatures.STOCK, () -> StockBadges.render(self, context, startIndex));
    }
}
