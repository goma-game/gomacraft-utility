package gomacraft.utility.mixin.ttf_glyph_lock;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Group;

/** 文字の大きさを測る読み込み({@code FT_Load_Glyph})を、FreeType の lock の中で行う(理由は {@code TtfGlyphLock})。 */
@Mixin(TrueTypeGlyphProvider.class)
public abstract class TrueTypeGlyphProviderLockMixin {
    @Group(name = "gomacraft_utility$ttf_glyph_lock$get_glyph", min = 1)
    @WrapMethod(method = "getGlyph", require = 0)
    private GlyphInfo gomacraftUtility$lockGetGlyph(int character, Operation<GlyphInfo> original) {
        synchronized (FreeTypeUtil.LIBRARY_LOCK) {
            return original.call(character);
        }
    }
}
