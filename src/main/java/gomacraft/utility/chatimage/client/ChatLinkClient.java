package gomacraft.utility.chatimage.client;

import gomacraft.utility.Features;
import gomacraft.utility.chatimage.ChatImageFeatures;
import gomacraft.utility.chatimage.ChatLinks;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 機能 {@code chat_link}: 受け取ったチャットの行の http / https の URL を、押せるリンク(下線)にする。
 *
 * <p>NeoForge の {@code ClientChatReceivedEvent} で、画面に出す部品だけを差し替える(署名の確かめは済んでおり、
 * 署名した中身・送り主の印はそのまま)。もうクリックの動きを持つ部品(Bot の作ったリンク・名前のクリックなど)は
 * 触らない。URL が無い行は元の部品をそのまま出す。画面の上の帯(overlay)は押せないので触らない。
 */
public final class ChatLinkClient {
    private ChatLinkClient() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientChatReceivedEvent.class,
                event -> Features.guard(ChatImageFeatures.LINK, () -> onChat(event)));
    }

    private static void onChat(ClientChatReceivedEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (event instanceof ClientChatReceivedEvent.System system && system.isOverlay()) {
            return;
        }
        Component linked = linkify(event.getMessage());
        if (linked != null) {
            event.setMessage(linked);
        }
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
            changed[0] = true;
            int last = 0;
            for (ChatLinks.Span span : spans) {
                if (span.start() > last) {
                    out.append(Component.literal(text.substring(last, span.start())).withStyle(style));
                }
                String url = text.substring(span.start(), span.end());
                Style link = style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)).withUnderlined(true);
                out.append(Component.literal(url).withStyle(link));
                last = span.end();
            }
            if (last < text.length()) {
                out.append(Component.literal(text.substring(last)).withStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? out : null;
    }
}
