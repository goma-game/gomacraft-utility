package gomacraft.utility.chatimage.client;

import gomacraft.utility.Features;
import gomacraft.utility.chatimage.ChatImageFeatures;
import gomacraft.utility.chatimage.DiscordImageUrl;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 機能 {@code chat_image_viewer} のクライアントの中身(mixin から呼ぶ)。
 *
 * <p>当てるのはチャットの画面だけ: 押したとき({@code Screen#handleComponentClicked} で、画面がチャットのとき)と、
 * 描いた後({@code ChatScreen#render} の終わり)。ほかの URL と、Shift を押しながらのクリック、設定でチャットの
 * リンクを切っているときは、バニラのまま。
 */
public final class ChatImageClient {
    /** 小さく出すときの枠(この中に収まるよう縮める)。 */
    private static final int PREVIEW_W = 160;
    private static final int PREVIEW_H = 120;

    private ChatImageClient() {
    }

    public static void init() {
        // 止めたら作り置きの絵を全部放す(止めている間は描かないので、残すと GPU の記憶を持ったままになる)
        Features.registry().onActiveChange(ChatImageFeatures.VIEWER, active -> ChatImageCache.clear());
        // 抜けたら作り置きを捨てる(次のサーバでは使わない。途中の取りにいきも取り消す)。⚠ guard の外で呼ぶ: 機能が
        // FAILED になった後も、それまでに作った絵と取りにいきは残っている(markFailed は onActiveChange を呼ばない)。
        // この listener は機能を初期化したときだけ足すので、問わずに捨てる(作り置きが空なら何もしない)
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> releaseAll());
    }

    /** 作り置きを全部捨てる(機能の状態を問わない)。失敗はログに残し、抜ける処理を止めない。 */
    static void releaseAll() {
        try {
            ChatImageCache.clear();
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger(gomacraft.utility.GomacraftUtility.MOD_ID)
                    .warn("gomacraft_utility: 画像の作り置きを捨てられない: {}", t.toString());
        }
    }

    /** 押した部品が Discord の画像なら、その形。 */
    static Optional<DiscordImageUrl> imageOf(Style style) {
        if (style == null) {
            return Optional.empty();
        }
        ClickEvent click = style.getClickEvent();
        if (click == null || click.getAction() != ClickEvent.Action.OPEN_URL) {
            return Optional.empty();
        }
        return DiscordImageUrl.parse(click.getValue());
    }

    /** チャットの画面で押されたとき。画像の画面を開いたら true(バニラの「リンクを開きますか」を出さない)。 */
    public static boolean onClick(Screen screen, Style style) {
        Minecraft mc = Minecraft.getInstance();
        if (!(screen instanceof ChatScreen) || Screen.hasShiftDown() || !mc.options.chatLinks().get()) {
            return false;
        }
        Optional<DiscordImageUrl> url = imageOf(style);
        if (url.isEmpty()) {
            return false;
        }
        mc.setScreen(new ImageViewerScreen(screen, url.get()));
        return true;
    }

    /** チャットの画面を描いた後: マウスの下が Discord の画像なら、小さく出す。 */
    public static void renderPreview(ChatScreen screen, GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.chatLinks().get()) {
            return;
        }
        Optional<DiscordImageUrl> url = imageOf(mc.gui.getChat().getClickedComponentStyleAt(mouseX, mouseY));
        if (url.isEmpty()) {
            return;
        }
        ChatImageCache.Entry entry = ChatImageCache.get(url.get());
        Font font = mc.font;
        int w;
        int h;
        Component text = null;
        if (entry.state == ChatImageCache.State.READY) {
            float scale = Math.min(1f, Math.min((float) PREVIEW_W / entry.width, (float) PREVIEW_H / entry.height));
            w = Math.max(1, Math.round(entry.width * scale));
            h = Math.max(1, Math.round(entry.height * scale));
        } else {
            text = stateText(entry.state);
            w = font.width(text);
            h = font.lineHeight;
        }
        // ⚠ バニラの文字のツールチップ(マウスの右下、y - 12 から下)と重ねないよう、マウスの上に出す
        int x = Math.min(mouseX + 12, screen.width - w - 4);
        int y = mouseY - 16 - h;
        if (y < 4) {
            y = 4;
        }
        x = Math.max(4, x);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        TooltipRenderUtil.renderTooltipBackground(graphics, x, y, w, h, 0);
        if (text != null) {
            graphics.drawString(font, text, x, y, 0xFFFFFFFF, false);
        } else {
            graphics.blit(entry.texture, x, y, w, h, 0, 0, entry.width, entry.height, entry.width, entry.height);
        }
        graphics.pose().popPose();
    }

    static Component stateText(ChatImageCache.State state) {
        return switch (state) {
            case LOADING -> Component.translatableWithFallback("gomacraft_utility.chat_image.loading", "Loading...");
            case EXPIRED -> Component.translatableWithFallback("gomacraft_utility.chat_image.expired",
                    "This image link has expired.");
            case TOO_LARGE -> Component.translatableWithFallback("gomacraft_utility.chat_image.too_large",
                    "This image is too large to show.");
            case FAILED, READY -> Component.translatableWithFallback("gomacraft_utility.chat_image.failed",
                    "Could not load the image.");
        };
    }
}
