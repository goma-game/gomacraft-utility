package gomacraft.utility.mixin.mcjtylib_text;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import gomacraft.utility.Features;
import gomacraft.utility.search.SearchFeatures;
import gomacraft.utility.search.client.HardcodedText;
import mcjty.lib.gui.widgets.AbstractLabel;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

/**
 * mcjtylib の文字の部品(Label・Button・ToggleButton・ChoiceLabel など)の文言に、描くときだけ訳を当てる。
 *
 * <p>⚠ {@code text(String)} で差し替えない: 部品の値({@code getText}・{@code getGenericValue})と画面の保存
 * ({@code fillGuiCommand})が同じ文字列を読むので、値が変わってしまう。描く・幅を測る・寄せる 3 か所だけで読み替える。
 */
@Mixin(AbstractLabel.class)
public abstract class AbstractLabelMixin {
    private static final String TEXT = "Lmcjty/lib/gui/widgets/AbstractLabel;text:Ljava/lang/String;";

    @Group(name = "gomacraft_utility$mcjtylib_text$draw", min = 1)
    @ModifyExpressionValue(method = "drawOffset", at = @At(value = "FIELD", target = TEXT, opcode = Opcodes.GETFIELD),
            require = 0)
    private String gomacraftUtility$draw(String text) {
        return gomacraftUtility$translate(text);
    }

    @Group(name = "gomacraft_utility$mcjtylib_text$width", min = 1)
    @ModifyExpressionValue(method = "getDesiredWidth", at = @At(value = "FIELD", target = TEXT, opcode = Opcodes.GETFIELD),
            require = 0)
    private String gomacraftUtility$width(String text) {
        return gomacraftUtility$translate(text);
    }

    @Group(name = "gomacraft_utility$mcjtylib_text$align", min = 1)
    @ModifyExpressionValue(method = "calculateHorizontalOffset",
            at = @At(value = "FIELD", target = TEXT, opcode = Opcodes.GETFIELD), require = 0)
    private String gomacraftUtility$align(String text) {
        return gomacraftUtility$translate(text);
    }

    private static String gomacraftUtility$translate(String text) {
        return Features.guard(SearchFeatures.MCJTYLIB_TEXT, () -> HardcodedText.translate(text), text);
    }
}
