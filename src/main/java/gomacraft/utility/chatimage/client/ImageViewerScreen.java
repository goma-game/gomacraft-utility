package gomacraft.utility.chatimage.client;

import gomacraft.utility.Features;
import gomacraft.utility.chatimage.ChatImageFeatures;
import gomacraft.utility.chatimage.DiscordImageUrl;
import java.net.URI;
import java.net.URISyntaxException;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * チャットの Discord の画像を、ゲームの中で画面に合わせて大きく出す。下に「ブラウザで開く」と「閉じる」。
 * 閉じると、開く前の画面(チャット)へ戻る。
 */
final class ImageViewerScreen extends Screen {
    private static final int MARGIN = 20;
    private static final int BUTTON_W = 120;
    private static final int BUTTON_H = 20;

    private final Screen previous;
    private final DiscordImageUrl url;

    ImageViewerScreen(Screen previous, DiscordImageUrl url) {
        super(Component.translatableWithFallback("gomacraft_utility.chat_image.title", "Image"));
        this.previous = previous;
        this.url = url;
    }

    @Override
    protected void init() {
        int y = this.height - MARGIN - BUTTON_H + 8;
        int left = this.width / 2 - BUTTON_W - 4;
        addRenderableWidget(Button.builder(
                Component.translatableWithFallback("gomacraft_utility.chat_image.open_browser", "Open in browser"),
                button -> openInBrowser()).bounds(left, y, BUTTON_W, BUTTON_H).build());
        addRenderableWidget(Button.builder(
                Component.translatableWithFallback("gomacraft_utility.chat_image.close", "Close"),
                button -> onClose()).bounds(this.width / 2 + 4, y, BUTTON_W, BUTTON_H).build());
    }

    /** バニラのリンクと同じく、設定で確かめを出すなら「リンクを開きますか」を挟む。 */
    private void openInBrowser() {
        URI uri;
        try {
            uri = Util.parseAndValidateUntrustedUri(url.original());
        } catch (URISyntaxException e) {
            return;
        }
        if (this.minecraft.options.chatLinksPrompt().get()) {
            ConfirmLinkScreen.confirmLinkNow(this, uri, false);
        } else {
            Util.getPlatform().openUri(uri);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(previous);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int areaTop = MARGIN;
        int areaBottom = this.height - MARGIN - BUTTON_H;
        int areaW = this.width - MARGIN * 2;
        int areaH = areaBottom - areaTop;
        // 止めた後も開いたままなら、絵は放してあるので描かない
        if (!Features.registry().isOn(ChatImageFeatures.VIEWER)) {
            return;
        }
        Features.guard(ChatImageFeatures.VIEWER, () -> {
            ChatImageCache.Entry entry = ChatImageCache.get(url);
            if (entry.state != ChatImageCache.State.READY || areaW <= 0 || areaH <= 0) {
                graphics.drawCenteredString(this.font, ChatImageClient.stateText(entry.state), this.width / 2,
                        areaTop + areaH / 2 - this.font.lineHeight / 2, 0xFFFFFFFF);
                return;
            }
            // 画面に合わせる(縦横の比は保つ)
            float scale = Math.min((float) areaW / entry.width, (float) areaH / entry.height);
            int w = Math.max(1, Math.round(entry.width * scale));
            int h = Math.max(1, Math.round(entry.height * scale));
            int x = (this.width - w) / 2;
            int y = areaTop + (areaH - h) / 2;
            graphics.blit(entry.texture, x, y, w, h, 0, 0, entry.width, entry.height, entry.width, entry.height);
        });
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
