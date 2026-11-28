package gomacraft.utility.screenshot.client;

import gomacraft.utility.screenshot.ScreenshotRules.Target;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 行き先を選ぶ小さな画面(チャット / スクショ / 両方 / やめる)。選んだら閉じて {@code chosen} を呼ぶ。
 *
 * <p>fabric 版: forge 版の同じ名前のクラスから写した(描き方だけ 26.x の {@code extractRenderState} に替えた)。
 */
final class TargetScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int HEIGHT = 20;
    private static final int GAP = 4;

    private final Consumer<Target> chosen;

    TargetScreen(Consumer<Target> chosen) {
        super(Component.translatableWithFallback("gomacraft_utility.screenshot.choose.title",
                "Where to post this screenshot?"));
        this.chosen = chosen;
    }

    @Override
    protected void init() {
        int x = (width - WIDTH) / 2;
        int y = height / 2 - (HEIGHT * 4 + GAP * 3) / 2;
        add(x, y, "gomacraft_utility.screenshot.choose.chat", "Chat channel", Target.CHAT);
        add(x, y + (HEIGHT + GAP), "gomacraft_utility.screenshot.choose.screenshot", "Screenshot channel",
                Target.SCREENSHOT);
        add(x, y + (HEIGHT + GAP) * 2, "gomacraft_utility.screenshot.choose.both", "Both", Target.BOTH);
        addRenderableWidget(Button.builder(
                        Component.translatableWithFallback("gomacraft_utility.screenshot.choose.cancel", "Cancel"),
                        button -> onClose())
                .bounds(x, y + (HEIGHT + GAP) * 3, WIDTH, HEIGHT)
                .build());
    }

    private void add(int x, int y, String key, String fallback, Target target) {
        addRenderableWidget(Button.builder(Component.translatableWithFallback(key, fallback), button -> {
                    onClose();
                    chosen.accept(target);
                })
                .bounds(x, y, WIDTH, HEIGHT)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, height / 2 - (HEIGHT * 4 + GAP * 3) / 2 - 16, 0xFFFFFFFF);
    }
}
