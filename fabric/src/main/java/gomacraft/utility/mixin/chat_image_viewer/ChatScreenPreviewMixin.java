package gomacraft.utility.mixin.chat_image_viewer;

import gomacraft.utility.Features;
import gomacraft.utility.chatimage.ChatImageFeatures;
import gomacraft.utility.chatimage.client.ChatImageClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * チャットの画面を描いた後に、マウスの下の Discord の画像を小さく出す。
 *
 * <p>⚠ バニラのツールチップの呼び出しには当てない。バニラは部品に hoverEvent があるときしか出さないので、そこに
 * 掛けると hoverEvent の無いリンク(chat_link が作るものなど)で出ない。
 *
 * <p>fabric 版: forge 版の同じ名前の mixin の対。26.x では描く口が {@code extractRenderState} になった。マウスの下の部品を
 * バニラのクリックと同じ形で探すため、画面の {@code displayMode} を読む。
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenPreviewMixin {
    @Shadow
    private ChatComponent.DisplayMode displayMode;

    @Group(name = "gomacraft_utility$chat_image_viewer$preview", min = 1)
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"),
            require = 0)
    private void gomacraftUtility$renderPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        ChatScreen self = (ChatScreen) (Object) this;
        ChatComponent.DisplayMode mode = displayMode;
        Features.guard(ChatImageFeatures.VIEWER,
                () -> ChatImageClient.renderPreview(self, mode, graphics, mouseX, mouseY));
    }
}
