package gomacraft.utility.mixin.naturescompass_search;

import com.chaosthedude.naturescompass.gui.NaturesCompassScreen;
import com.chaosthedude.naturescompass.gui.TransparentTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Nature's Compass の検索欄を読む(コンパスの検索語の履歴 —— {@code CompassSearchHistory})。 */
@Mixin(NaturesCompassScreen.class)
public interface NaturesCompassScreenAccessor {
    @Accessor("searchTextField")
    TransparentTextField gomacraftUtility$searchField();
}
