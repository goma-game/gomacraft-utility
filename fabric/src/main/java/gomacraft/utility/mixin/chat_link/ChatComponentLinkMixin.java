package gomacraft.utility.mixin.chat_link;

import gomacraft.utility.Features;
import gomacraft.utility.chatimage.ChatImageFeatures;
import gomacraft.utility.chatimage.client.ChatLinkClient;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * プレイヤーの発言をチャット欄に足すときに、画面に出す部品だけを URL のリンクにする(fabric 版だけ。forge 版は NeoForge の
 * {@code ClientChatReceivedEvent} がプレイヤーの発言も差し替えられる)。
 *
 * <p>Fabric API の 26.2 には、プレイヤーの発言の部品を差し替える口が無い({@code ClientReceiveMessageEvents} の
 * ALLOW_CHAT・CHAT は見るだけ)。署名({@code MessageSignature})は別の引数で、ここでは触らない。サーバの知らせの行は
 * {@code ClientReceiveMessageEvents.MODIFY_GAME} が差し替える({@code ChatLinkClient})。
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentLinkMixin {
    @Group(name = "gomacraft_utility$chat_link$player_message", min = 1)
    @ModifyVariable(
            method = "addPlayerMessage(Lnet/minecraft/network/chat/Component;"
                    + "Lnet/minecraft/network/chat/MessageSignature;"
                    + "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            argsOnly = true,
            require = 0)
    private Component gomacraftUtility$linkPlayerMessage(Component message) {
        return Features.guard(ChatImageFeatures.LINK, () -> ChatLinkClient.linkOrSame(message), message);
    }
}
