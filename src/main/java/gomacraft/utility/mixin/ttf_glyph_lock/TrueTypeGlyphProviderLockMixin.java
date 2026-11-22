package gomacraft.utility.mixin.ttf_glyph_lock;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import gomacraft.utility.Features;
import gomacraft.utility.features.ttf_glyph_lock.TtfGlyphLock;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * 文字の大きさを測る読み込み({@code FT_Load_Glyph})を、FreeType の lock の中で行う(理由は {@code TtfGlyphLock})。
 *
 * <p>⚠ 元の処理({@code original.call})を {@code Features.guard} の中に入れない。guard は例外を飲むので、元の処理の
 * 例外(読めない字など —— 今までどおり呼び手へ届くべきもの)まで隠してしまう。代わりに、機能が有効でない(切った・
 * FAILED)ときは lock を取らずに元の処理だけを呼ぶ(lock を取る・取らないの違いだけで、元の振る舞いは変えない)。
 */
@Mixin(TrueTypeGlyphProvider.class)
public abstract class TrueTypeGlyphProviderLockMixin {
    @Group(name = "gomacraft_utility$ttf_glyph_lock$get_glyph", min = 1)
    @WrapMethod(method = "getGlyph(I)Lcom/mojang/blaze3d/font/GlyphInfo;", require = 0)
    private GlyphInfo gomacraftUtility$lockGetGlyph(int character, Operation<GlyphInfo> original) {
        if (!Features.registry().isOn(TtfGlyphLock.ID)) {
            return original.call(character);
        }
        synchronized (FreeTypeUtil.LIBRARY_LOCK) {
            return original.call(character);
        }
    }
}
