package gomacraft.utility.mixin.explorerscompass_search;

import com.chaosthedude.explorerscompass.gui.ExplorersCompassScreen;
import com.chaosthedude.explorerscompass.gui.TransparentTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Explorer's Compass の検索欄を読み、検索をし直させる(コンパスの検索語の履歴 —— {@code CompassSearchHistory})。 */
@Mixin(ExplorersCompassScreen.class)
public interface ExplorersCompassScreenAccessor {
    @Accessor("searchTextField")
    TransparentTextField gomacraftUtility$searchField();

    @Invoker("processSearchTerm")
    void gomacraftUtility$processSearchTerm();
}
