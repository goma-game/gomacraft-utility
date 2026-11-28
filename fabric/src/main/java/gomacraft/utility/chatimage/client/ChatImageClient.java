package gomacraft.utility.chatimage.client;

import gomacraft.utility.Features;
import gomacraft.utility.chatimage.ChatImageFeatures;
import gomacraft.utility.chatimage.DiscordImageUrl;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * 機能 {@code chat_image_viewer} のクライアントの中身(mixin から呼ぶ)。
 *
 * <p>当てるのはチャットの画面だけ: 押したとき({@code ChatScreen#handleComponentClicked})と、描いた後
 * ({@code ChatScreen#extractRenderState} の終わり)。ほかの URL と、Shift を押しながらのクリック(挿入のクリック)、
 * 設定でチャットのリンクを切っているときは、バニラのまま。
 *
 * <p>fabric 版: forge 版の同じ名前のクラスの対。26.2 では押す口がチャットの画面の {@code handleComponentClicked(Style,
 * boolean)} に移り(2 つめの引数が挿入のクリックか)、URL の部品は {@code ClickEvent.OpenUrl}(URI を持つ)になった。
 * マウスの下の部品は、バニラのクリックと同じく {@code captureClickableText} に {@code ClickableStyleFinder} を渡して探す。
 * 描き方は 26.x の {@code GuiGraphicsExtractor}(前に出すのは {@code nextStratum})。ログアウトの後始末は Fabric API の
 * {@code ClientPlayConnectionEvents.DISCONNECT}。
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
        // 抜けたら作り置きを捨てる(次のサーバでは使わない。途中の取りにいきも取り消す)。作り置きは主スレッドだけが
        // 触るので、主スレッドへ回す(主スレッドで呼ばれたら、その場で走る)
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(
                () -> Features.guard(ChatImageFeatures.VIEWER, ChatImageCache::clear)));
    }

    /** 押した部品が Discord の画像なら、その形。 */
    static Optional<DiscordImageUrl> imageOf(Style style) {
        if (style == null || !(style.getClickEvent() instanceof ClickEvent.OpenUrl open) || open.uri() == null) {
            return Optional.empty();
        }
        return DiscordImageUrl.parse(open.uri().toString());
    }

    /**
     * チャットの画面で押されたとき。画像の画面を開いたら true(バニラの「リンクを開きますか」を出さない)。
     *
     * @param insertion 挿入のクリック(Shift を押しながら)か
     */
    public static boolean onClick(ChatScreen screen, Style style, boolean insertion) {
        Minecraft mc = Minecraft.getInstance();
        if (insertion || !mc.options.chatLinks().get()) {
            return false;
        }
        Optional<DiscordImageUrl> url = imageOf(style);
        if (url.isEmpty()) {
            return false;
        }
        mc.gui.setScreen(new ImageViewerScreen(screen, url.get()));
        return true;
    }

    /** チャットの画面を描いた後: マウスの下が Discord の画像なら、小さく出す。 */
    public static void renderPreview(ChatScreen screen, ChatComponent.DisplayMode displayMode,
            GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.chatLinks().get()) {
            return;
        }
        Font font = mc.font;
        // バニラのクリック(ChatScreen#mouseClicked)と同じ探し方
        ActiveTextCollector.ClickableStyleFinder finder = new ActiveTextCollector.ClickableStyleFinder(font, mouseX, mouseY);
        mc.gui.hud.getChat().captureClickableText(finder, mc.getWindow().getGuiScaledHeight(), mc.gui.hud.getGuiTicks(),
                displayMode);
        Optional<DiscordImageUrl> url = imageOf(finder.result());
        if (url.isEmpty()) {
            return;
        }
        ChatImageCache.Entry entry = ChatImageCache.get(url.get());
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
        // 前に出す(forge 版の z = 400 の代わり)
        graphics.nextStratum();
        TooltipRenderUtil.extractTooltipBackground(graphics, x, y, w, h, null);
        if (text != null) {
            graphics.text(font, text, x, y, 0xFFFFFFFF, false);
        } else {
            graphics.blit(RenderPipelines.GUI_TEXTURED, entry.texture, x, y, 0, 0, w, h, entry.width, entry.height,
                    entry.width, entry.height);
        }
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
