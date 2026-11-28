package gomacraft.utility.mixin.chat_image_viewer;

import gomacraft.utility.Features;
import gomacraft.utility.chatimage.ChatImageFeatures;
import gomacraft.utility.chatimage.client.ChatImageClient;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * チャットの画面で Discord の画像のリンクを押したら、ブラウザの確かめの代わりにゲームの中の画面で出す。
 *
 * <p>fabric 版: forge 版の ScreenClickMixin の対。26.2 では押した部品を扱うのがチャットの画面の
 * {@code handleComponentClicked(Style, boolean)}(2 つめの引数は挿入のクリックか)に移った(26.2 の実物で確かめた)。
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenClickMixin {
    @Group(name = "gomacraft_utility$chat_image_viewer$click", min = 1)
    @Inject(method = "handleComponentClicked(Lnet/minecraft/network/chat/Style;Z)Z", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void gomacraftUtility$openViewer(Style style, boolean insertion, CallbackInfoReturnable<Boolean> cir) {
        ChatScreen self = (ChatScreen) (Object) this;
        if (Features.guard(ChatImageFeatures.VIEWER, () -> ChatImageClient.onClick(self, style, insertion), false)) {
            cir.setReturnValue(true);
        }
    }
}
