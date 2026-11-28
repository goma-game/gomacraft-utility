package gomacraft.utility.mixin.screenshot_share;

import gomacraft.utility.Features;
import gomacraft.utility.screenshot.ScreenshotFeatures;
import gomacraft.utility.screenshot.client.ShareClient;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 撮ったときの知らせの受け手を包む(fabric 版だけ。forge 版は NeoForge の {@code ScreenshotEvent} を使う)。
 *
 * <p>当て先は、ほかの {@code grab} がどれも最後に呼ぶ 5 つの引数の {@code grab}(26.2 の実物で確かめた)。受け手は
 * 画像を書き終えてから「保存しました」の知らせを受けるので、その時点でファイルは在る。
 */
@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {
    @Group(name = "gomacraft_utility$screenshot_share$grab", min = 1)
    @ModifyVariable(
            method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;"
                    + "ILjava/util/function/Consumer;)V",
            at = @At("HEAD"),
            argsOnly = true,
            require = 0)
    private static Consumer<Component> gomacraftUtility$wrapMessage(Consumer<Component> messageConsumer) {
        return Features.guard(ScreenshotFeatures.SHARE, () -> ShareClient.wrap(messageConsumer), messageConsumer);
    }
}
