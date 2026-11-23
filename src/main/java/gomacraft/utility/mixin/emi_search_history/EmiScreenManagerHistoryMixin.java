package gomacraft.utility.mixin.emi_search_history;

import dev.emi.emi.screen.EmiScreenManager;
import gomacraft.utility.Features;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.client.EmiSearchHistory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EMI が押す・キーを扱う前に、EMI の検索欄の履歴のウィンドウに渡す。扱ったら EMI にも画面にも渡さない。
 *
 * <p>⚠ EMI は画面の出来事(ScreenEvent)より先に押すのを受け、欄の外を押すと欄の focus を外す。先に受けないと、
 * ウィンドウを押した時点でウィンドウが消える。↑↓も EMI 自身の履歴より先に受ける。
 */
@Mixin(EmiScreenManager.class)
public abstract class EmiScreenManagerHistoryMixin {
    @Group(name = "gomacraft_utility$emi_search_history$mouse_clicked", min = 1)
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gomacraftUtility$historyClick(
            double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (Features.guard(SearchFeatures.EMI_SEARCH_HISTORY,
                () -> EmiSearchHistory.onMouseClicked(mouseX, mouseY, button), false)) {
            cir.setReturnValue(true);
        }
    }

    @Group(name = "gomacraft_utility$emi_search_history$key_pressed", min = 1)
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gomacraftUtility$historyKey(
            int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (Features.guard(SearchFeatures.EMI_SEARCH_HISTORY, () -> EmiSearchHistory.onKeyPressed(keyCode), false)) {
            cir.setReturnValue(true);
        }
    }
}
