package gomacraft.utility.mixin.emi_ae_quick_craft;

import dev.emi.emi.screen.EmiScreenManager;
import gomacraft.utility.Features;
import gomacraft.utility.emiae.EmiAeFeatures;
import gomacraft.utility.emiae.client.QuickCraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EMI がクリックを扱う前に、この Mod の操作(既定は Alt + 左クリック)を見る。扱ったら EMI には渡さない。
 *
 * <p>EMI は品のクリックを押したときに覚え、離したときに動かすので、押したときに止めれば EMI の動きは起きない。
 */
@Mixin(EmiScreenManager.class)
public abstract class EmiScreenManagerMixin {
    @Group(name = "gomacraft_utility$emi_ae_quick_craft$mouse_clicked", min = 1)
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gomacraftUtility$quickCraft(
            double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (Features.guard(EmiAeFeatures.QUICK_CRAFT, () -> QuickCraft.onMouseClicked(mouseX, mouseY, button), false)) {
            cir.setReturnValue(true);
        }
    }

    /** 操作をキーボードのキーに割り当て直したとき用(品の上でそのキーを押す)。 */
    @Group(name = "gomacraft_utility$emi_ae_quick_craft$key_pressed", min = 1)
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gomacraftUtility$quickCraftKey(
            int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (Features.guard(EmiAeFeatures.QUICK_CRAFT, () -> QuickCraft.onKeyPressed(keyCode, scanCode), false)) {
            cir.setReturnValue(true);
        }
    }
}
