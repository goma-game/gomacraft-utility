package gomacraft.utility.mixin.ttf_glyph_lock;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.NativeImage;
import gomacraft.utility.Features;
import gomacraft.utility.features.ttf_glyph_lock.TtfGlyphLock;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * グリフの焼き込み(描いて、同じ置き場から読み出す)を、FreeType の lock の中で行う(理由は {@code TtfGlyphLock})。
 *
 * <p>⚠ 元の処理を {@code Features.guard} の中に入れない理由は {@code TrueTypeGlyphProviderLockMixin} と同じ
 * (guard が元の処理の例外を飲む)。機能が有効でないときは lock を取らずに元の処理だけを呼ぶ。
 */
@Mixin(NativeImage.class)
public abstract class NativeImageFontLockMixin {
    @Group(name = "gomacraft_utility$ttf_glyph_lock$copy_from_font", min = 1)
    @WrapMethod(method = "copyFromFont(Lorg/lwjgl/util/freetype/FT_Face;I)Z", require = 0)
    private boolean gomacraftUtility$lockCopyFromFont(FT_Face face, int index, Operation<Boolean> original) {
        if (!Features.registry().isOn(TtfGlyphLock.ID)) {
            return original.call(face, index);
        }
        synchronized (FreeTypeUtil.LIBRARY_LOCK) {
            return original.call(face, index);
        }
    }
}
