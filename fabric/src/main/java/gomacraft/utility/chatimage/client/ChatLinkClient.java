package gomacraft.utility.chatimage.client;

import gomacraft.utility.Features;
import gomacraft.utility.chatimage.ChatImageFeatures;
import gomacraft.utility.chatimage.ChatLinks;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;

/**
 * 機能 {@code chat_link}: 受け取ったチャットの行の http / https の URL を、押せるリンク(下線)にする。
 *
 * <p>画面に出す部品だけを差し替える(署名の確かめは済んでおり、署名した中身・送り主の印はそのまま)。もうクリックの
 * 動きを持つ部品(Bot の作ったリンク・名前のクリックなど)は触らない。URL が無い行は元の部品をそのまま出す。画面の
 * 上の帯(overlay)は押せないので触らない。
 *
 * <p>fabric 版: forge 版の同じ名前のクラスの対。forge 版は NeoForge の {@code ClientChatReceivedEvent} 1 つで差し替える。
 * fabric 版は 2 つの口で差し替える:
 * <ul>
 *   <li>サーバの知らせ(Discord から来た行の {@code tellraw} など): Fabric API の {@code ClientReceiveMessageEvents.MODIFY_GAME}。</li>
 *   <li>プレイヤーの発言: Fabric API 0.161 の 26.2 には差し替えの口が無い(ALLOW_CHAT・CHAT は見るだけ)ので、
 *       チャット欄に足す口({@code ChatComponent#addPlayerMessage})の部品を mixin で差し替える
 *       ({@code mixin.chat_link.ChatComponentLinkMixin})。署名({@code MessageSignature})は別の引数で、触らない。</li>
 * </ul>
 * 26.2 の URL の部品は {@code ClickEvent.OpenUrl}(URI を持つ)。URI として読めない文はリンクにしない(バニラの
 * {@code Util.parseAndValidateUntrustedUri} で確かめる —— http / https だけを通す)。
 *
 * <p>バニラの 26.2 は、プレイヤーの打った URL をリンクにしない(26.2 の jar の中で {@code ClickEvent.OpenUrl} を作るのは
 * 画面・死の知らせなどだけで、チャットの受け取り・サーバの発言の処理には無い)。
 */
public final class ChatLinkClient {
    private ChatLinkClient() {
    }

    public static void init() {
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay ? message
                : Features.guard(ChatImageFeatures.LINK, () -> linkOrSame(message), message));
    }

    /** URL を含む行ならリンクにした部品、含まなければ元の部品(mixin からも呼ぶ)。 */
    public static Component linkOrSame(Component message) {
        Component linked = linkify(message);
        return linked != null ? linked : message;
    }

    /** URL を含む行なら、リンクにした部品。含まなければ null(元のまま使う)。 */
    static Component linkify(Component message) {
        if (message == null) {
            return null;
        }
        MutableComponent out = Component.empty();
        boolean[] changed = {false};
        message.<Object>visit((style, text) -> {
            // ⚠ § の書式の符号を含む部品は分けない(分けると、符号より後ろの色と書式が消える)
            List<ChatLinks.Span> spans = style.getClickEvent() == null && text.indexOf('§') < 0
                    ? ChatLinks.find(text) : List.of();
            if (spans.isEmpty()) {
                out.append(Component.literal(text).withStyle(style));
                return Optional.empty();
            }
            int last = 0;
            for (ChatLinks.Span span : spans) {
                String url = text.substring(span.start(), span.end());
                URI uri;
                try {
                    uri = Util.parseAndValidateUntrustedUri(url);
                } catch (URISyntaxException | RuntimeException e) {
                    // 読めない URL は文のまま(次の文と一緒に出す)
                    continue;
                }
                if (span.start() > last) {
                    out.append(Component.literal(text.substring(last, span.start())).withStyle(style));
                }
                Style link = style.withClickEvent(new ClickEvent.OpenUrl(uri)).withUnderlined(true);
                out.append(Component.literal(url).withStyle(link));
                last = span.end();
                changed[0] = true;
            }
            if (last < text.length()) {
                out.append(Component.literal(text.substring(last)).withStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? out : null;
    }
}
