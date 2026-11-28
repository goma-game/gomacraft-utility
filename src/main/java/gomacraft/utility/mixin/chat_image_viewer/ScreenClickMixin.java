package gomacraft.utility.mixin.chat_image_viewer;

import gomacraft.utility.Features;
import gomacraft.utility.chatimage.ChatImageFeatures;
import gomacraft.utility.chatimage.client.ChatImageClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * チャットの画面で Discord の画像のリンクを押したら、ブラウザの確かめの代わりにゲームの中の画面で出す。
 * {@code handleComponentClicked} はチャットの画面に無く {@code Screen} が持つので、ここに当てて画面の種類を見る。
 */
@Mixin(Screen.class)
public abstract class ScreenClickMixin {
    @Group(name = "gomacraft_utility$chat_image_viewer$click", min = 1)
    @Inject(method = "handleComponentClicked(Lnet/minecraft/network/chat/Style;)Z", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void gomacraftUtility$openViewer(Style style, CallbackInfoReturnable<Boolean> cir) {
        Screen self = (Screen) (Object) this;
        if (Features.guard(ChatImageFeatures.VIEWER, () -> ChatImageClient.onClick(self, style), false)) {
            cir.setReturnValue(true);
        }
    }
}
