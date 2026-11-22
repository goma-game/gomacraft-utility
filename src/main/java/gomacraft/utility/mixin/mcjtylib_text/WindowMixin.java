package gomacraft.utility.mixin.mcjtylib_text;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import gomacraft.utility.Features;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.client.HardcodedText;
import java.util.List;
import mcjty.lib.gui.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * mcjtylib の画面のツールチップ(部品が英語で持つ行)に訳を当てる。ツールチップは画面の窓が部品から集めて描くので、
 * 集めた一覧の 1 か所で読み替える(部品が持つ一覧は変えない)。
 */
@Mixin(Window.class)
public abstract class WindowMixin {
    @Group(name = "gomacraft_utility$mcjtylib_text$tooltips", min = 1)
    @ModifyReturnValue(method = "getTooltips()Ljava/util/List;", at = @At("RETURN"), require = 0)
    private List<String> gomacraftUtility$tooltips(List<String> lines) {
        return Features.guard(SearchFeatures.MCJTYLIB_TEXT, () -> HardcodedText.translateAll(lines), lines);
    }
}
